"""
기상청 단기예보 조회서비스 (15084084) 실제 API 연동 서비스

[주요 기능]
1. WGS84 위경도(lat, lng) -> 기상청 5km 격자 좌표(nx, ny) 변환 (람베르트 정각원추도법)
2. 현재 한국 시각(KST) 기준 최신 발표 일시(base_date, base_time) 자동 산출
3. 기상청 getVilageFcst API 호출 -> 익일 출발 시각 예상 기온(TMP) 추출 (Timeout 3.0s)
4. 2차 백업(Open-Meteo) & 최종 Default Safety Fallback(-5.0℃) 3중 안전망 보장
"""

import logging
import math
import re
from datetime import datetime, timedelta, timezone
from typing import Dict, Any, Tuple
import httpx

from backend.app.core.config import KMA_API_KEY, API_TIMEOUT_SECONDS

logger = logging.getLogger("uvicorn.error")


KMA_VILAGE_FCST_URL = "http://apis.data.go.kr/1360000/VilageFcstInfoService_2.0/getVilageFcst"
OPEN_METEO_URL = "https://api.open-meteo.com/v1/forecast"
DEFAULT_FALLBACK_TEMP = -5.0  # 동절기 안전 가중치 기본 기온 (℃)
KST = timezone(timedelta(hours=9))


def latlon_to_kma_grid(lat: float, lng: float) -> Tuple[int, int]:
    """
    위도/경도(WGS84)를 기상청 단기예보 5km 격자 좌표(nx, ny)로 변환합니다.
    (기상청 공식 LCC 람베르트 정각원추도법 변환 파라미터 적용)
    """
    RE = 6371.00877  # 지구 반경(km)
    GRID = 5.0       # 격자 간격(km)
    SLAT1 = 30.0     # 투영 위도1(degree)
    SLAT2 = 60.0     # 투영 위도2(degree)
    OLON = 126.0     # 기준점 경도(degree)
    OLAT = 38.0      # 기준점 위도(degree)
    XO = 43          # 기준점 X좌표(GRID)
    YO = 136         # 기준점 Y좌표(GRID)

    DEGRAD = math.pi / 180.0
    re = RE / GRID
    slat1 = SLAT1 * DEGRAD
    slat2 = SLAT2 * DEGRAD
    olon = OLON * DEGRAD
    olat = OLAT * DEGRAD

    sn = math.tan(math.pi * 0.25 + slat2 * 0.5) / math.tan(math.pi * 0.25 + slat1 * 0.5)
    sn = math.log(math.cos(slat1) / math.cos(slat2)) / math.log(sn)
    sf = math.tan(math.pi * 0.25 + slat1 * 0.5)
    sf = math.pow(sf, sn) * math.cos(slat1) / sn
    ro = math.tan(math.pi * 0.25 + olat * 0.5)
    ro = re * sf / math.pow(ro, sn)

    ra = math.tan(math.pi * 0.25 + lat * DEGRAD * 0.5)
    ra = re * sf / math.pow(ra, sn)
    theta = lng * DEGRAD - olon
    if theta > math.pi:
        theta -= 2.0 * math.pi
    if theta < -math.pi:
        theta += 2.0 * math.pi
    theta *= sn

    nx = math.floor(ra * math.sin(theta) + XO + 0.5)
    ny = math.floor(ro - ra * math.cos(theta) + YO + 0.5)
    return int(nx), int(ny)


def get_latest_kma_base_datetime(now_kst: datetime | None = None) -> Tuple[str, str]:
    """
    기상청 단기예보 발표 주기(02, 05, 08, 11, 14, 17, 20, 23시 + API 생성 15분 고려)에 맞춰
    가장 최근의 유효한 (base_date, base_time)을 반환합니다.
    """
    if now_kst is None:
        now_kst = datetime.now(KST)

    # 발표 후 데이터 생성 여유 15분 고려해 15분 전 시점 기준으로 계산
    effective_time = now_kst - timedelta(minutes=15)
    base_hours = [2, 5, 8, 11, 14, 17, 20, 23]

    selected_hour = None
    for h in reversed(base_hours):
        if effective_time.hour >= h:
            selected_hour = h
            break

    if selected_hour is None:
        # 자정 ~ 02:15 사이는 전날 23:00 발표 데이터 사용
        prev_day = effective_time - timedelta(days=1)
        return prev_day.strftime("%Y%m%d"), "2300"

    return effective_time.strftime("%Y%m%d"), f"{selected_hour:02d}00"


def _parse_target_hour(departure_time: str) -> str:
    """출발 시각 문자열(예: '내일 오전 10:00', '오후 2시', '14:00')에서 목표 시간 HH00을 추출"""
    match = re.search(r"(오전|오후)?\s*(\d{1,2})\s*(?:[:시])", departure_time)
    if match:
        meridiem = match.group(1)
        hour = int(match.group(2))
        if meridiem == "오후" and 1 <= hour <= 11:
            hour += 12
        elif meridiem == "오전" and hour == 12:
            hour = 0
        if 0 <= hour <= 23:
            return f"{hour:02d}00"
    return "1000"  # 기본값 오전 10시


def _parse_target_day_offset(departure_time: str) -> int:
    """출발 일자 문자열('오늘', '내일', '모레') 기준 일수 오프셋 반환"""
    if "오늘" in departure_time:
        return 0
    if "모레" in departure_time:
        return 2
    return 1


async def fetch_forecast_temperature(
    destination_name: str = "우리 집",
    departure_time: str = "내일 오전 10:00",
    lat: float = 37.6242,   # 기본 차박지/목적지 대표 위도 (대관령 부근 예시)
    lng: float = 128.7420,
    force_fallback: bool = False,
) -> Dict[str, Any]:
    """
    기상청 단기예보 API를 호출하여 목적지(또는 차박지)의 내일 출발 시각 예상 기온을 조회합니다.
    - 1순위: 기상청 단기예보 API (15084084)
    - 2순위: Open-Meteo 실시간 한국 기온 API (기상청 키 반영 지연 등 비상 백업)
    - 3순위: Default Safety Fallback (-5.0℃)
    """
    if force_fallback:
        return {
            "temperature": DEFAULT_FALLBACK_TEMP,
            "is_fallback": True,
            "source": "Default Safety Fallback",
            "description": "네트워크 통신 지연으로 동절기 기본 안전 기온(-5.0℃)이 자동 적용되었습니다.",
        }

    now_kst = datetime.now(KST)
    day_offset = _parse_target_day_offset(departure_time)
    target_date_dt = now_kst + timedelta(days=day_offset)
    tomorrow_date_str = target_date_dt.strftime("%Y%m%d")
    target_time_str = _parse_target_hour(departure_time)

    # 1순위: 기상청 단기예보 API 호출
    if KMA_API_KEY:
        try:
            nx, ny = latlon_to_kma_grid(lat, lng)
            base_date, base_time = get_latest_kma_base_datetime(now_kst)

            params = {
                "serviceKey": KMA_API_KEY,
                "pageNo": 1,
                "numOfRows": 1000,
                "dataType": "JSON",
                "base_date": base_date,
                "base_time": base_time,
                "nx": nx,
                "ny": ny,
            }

            async with httpx.AsyncClient(timeout=API_TIMEOUT_SECONDS) as client:
                resp = await client.get(KMA_VILAGE_FCST_URL, params=params)
                resp.raise_for_status()
                payload = resp.json()

            header = payload.get("response", {}).get("header", {})
            if header.get("resultCode") == "00":
                items = payload.get("response", {}).get("body", {}).get("items", {}).get("item", [])
                # 내일 출발 시각의 TMP(1시간 기온) 우선 탐색
                found_temp = None
                sky_code = None
                for item in items:
                    if item.get("fcstDate") == tomorrow_date_str and item.get("fcstTime") == target_time_str:
                        if item.get("category") == "TMP":
                            found_temp = float(item.get("fcstValue"))
                        elif item.get("category") == "SKY":
                            sky_code = item.get("fcstValue")

                # 만약 해당 정확한 시간이 없으면 내일 가장 가까운 TMP 추출
                if found_temp is None:
                    for item in items:
                        if item.get("fcstDate") == tomorrow_date_str and item.get("category") == "TMP":
                            found_temp = float(item.get("fcstValue"))
                            break

                if found_temp is not None:
                    return {
                        "temperature": found_temp,
                        "grid_nx": nx,
                        "grid_ny": ny,
                        "base_date": base_date,
                        "base_time": base_time,
                        "target_date": tomorrow_date_str,
                        "target_time": target_time_str,
                        "sky_code": sky_code,
                        "is_fallback": False,
                        "source": "KMA Short-term Forecast API (15084084)",
                        "description": f"기상청 단기예보 연동 성공 (격자 {nx},{ny} / {departure_time} 예상 {found_temp}℃)",
                    }
        except Exception as e:
            logger.warning(f"[Weather/KMA 1차 실패 -> Open-Meteo 전환] err={repr(e)}")

    # 2순위: Open-Meteo 실시간 예보 (API 키 없이 안정적 작동)
    try:
        params = {
            "latitude": lat,
            "longitude": lng,
            "hourly": "temperature_2m",
            "timezone": "Asia/Seoul",
            "forecast_days": 2,
        }
        async with httpx.AsyncClient(timeout=API_TIMEOUT_SECONDS) as client:
            resp = await client.get(OPEN_METEO_URL, params=params)
            resp.raise_for_status()
            data = resp.json()

        times = data.get("hourly", {}).get("time", [])
        temps = data.get("hourly", {}).get("temperature_2m", [])

        tomorrow_iso = target_date_dt.strftime("%Y-%m-%d")
        target_hour_int = int(target_time_str[:2])
        target_prefix = f"{tomorrow_iso}T{target_hour_int:02d}:00"

        matched_temp = None
        for idx, t_str in enumerate(times):
            if t_str == target_prefix:
                matched_temp = float(temps[idx])
                break

        if matched_temp is not None:
            return {
                "temperature": matched_temp,
                "is_fallback": False,
                "source": "Open-Meteo Backup Live API",
                "description": f"실시간 기상 예보 연동 성공 ({departure_time} 예상 {matched_temp}℃)",
            }
    except Exception as e:
        logger.warning(f"[Weather/Open-Meteo 2차 실패 -> Safety Fallback] err={repr(e)}")

    # 3순위: 통신 장애 / 타임아웃 시 Default Safety Fallback (-5.0℃)
    return {
        "temperature": DEFAULT_FALLBACK_TEMP,
        "is_fallback": True,
        "source": "Default Safety Fallback",
        "description": "기상 API 통신 실패(3.0s 초과 또는 오프라인)로 동절기 기본 안전 기온(-5.0℃)을 적용합니다.",
    }

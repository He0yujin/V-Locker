"""
내비게이션 토폴로지(지형 고도) 연동 서비스 (Navigation Service)

[지침 준수]
- API 통신 시 Timeout 설정을 필수로 적용 (3초 이내 응답 목표).
- 귀가 경로의 총 주행거리, 순수 획득 고도(Elevation Gain), 예상 주행 시간을 산출.
- 통신 실패 시 기본 안전 가중치(거리 86km, 획득고도 640m)로 안전하게 Fallback.
"""

import asyncio
from typing import Dict, Any


DEFAULT_FALLBACK_DISTANCE = 86.0        # 기본 안전 귀가 거리 (km)
DEFAULT_FALLBACK_ELEVATION_GAIN = 640.0 # 강원 산간 기본 오르막 획득 고도 (m)
TIMEOUT_SECONDS = 3.0


async def fetch_route_topology(
    destination_name: str,
    force_fallback: bool = False,
) -> Dict[str, Any]:
    """
    내비게이션 API의 토폴로지 엔진을 호출하여 귀갓길의 고도 변화 및 주행 거리를 산출합니다.
    
    :param destination_name: 목적지 명칭
    :param force_fallback: 강제 Fallback 트리거
    :return: { "distance_km": float, "elevation_gain_m": float, "estimated_duration_min": int, "is_fallback": bool }
    """
    if force_fallback:
        return {
            "distance_km": DEFAULT_FALLBACK_DISTANCE,
            "elevation_gain_m": DEFAULT_FALLBACK_ELEVATION_GAIN,
            "estimated_duration_min": 85,
            "is_fallback": True,
            "description": "네트워크 통신 지연으로 인해 기본 경로 안전 지형 데이터(86km, +640m)가 적용되었습니다.",
        }

    try:
        async def _mock_nav_call():
            await asyncio.sleep(0.4)  # 통신 지연 시뮬레이션
            if "충전소" in destination_name:
                return {
                    "distance_km": 12.0,
                    "elevation_gain_m": 80.0,
                    "estimated_duration_min": 18,
                }
            elif "부산" in destination_name:
                return {
                    "distance_km": 320.0,
                    "elevation_gain_m": 1200.0,
                    "estimated_duration_min": 240,
                }
            else:
                # 와이어프레임 기본값: 내일 오전 10:00 · 집까지 86 km · 예상 1시간 25분 (85분) · 상승 고도 +640 m
                return {
                    "distance_km": 86.0,
                    "elevation_gain_m": 640.0,
                    "estimated_duration_min": 85,
                }

        res = await asyncio.wait_for(_mock_nav_call(), timeout=TIMEOUT_SECONDS)
        res["is_fallback"] = False
        res["description"] = f"내비게이션 토폴로지 분석 완료 ({destination_name})"
        return res

    except (asyncio.TimeoutError, Exception):
        return {
            "distance_km": DEFAULT_FALLBACK_DISTANCE,
            "elevation_gain_m": DEFAULT_FALLBACK_ELEVATION_GAIN,
            "estimated_duration_min": 85,
            "is_fallback": True,
            "description": "내비게이션 API 통신 타임아웃(3.0s 초과)으로 기본 경로 안전 데이터가 적용되었습니다.",
        }


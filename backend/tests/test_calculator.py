import asyncio
from datetime import datetime, timedelta, timezone
from unittest.mock import MagicMock, patch
import httpx

from backend.app.core.calculator import (
    calc_temperature_efficiency,
    calc_elevation_energy,
    calc_cold_start_hvac_spike,
    predict_v2l_lockout,
)
from backend.app.services.weather import (
    fetch_forecast_temperature,
    latlon_to_kma_grid,
    get_latest_kma_base_datetime,
    _parse_target_hour,
    _parse_target_day_offset,
)
from backend.app.services.navigation import fetch_route_topology
from backend.app.services.places import search_places, search_nearby_ev_chargers

KST = timezone(timedelta(hours=9))


def _make_mock_response(json_data, status_code=200):
    resp = MagicMock()
    resp.status_code = status_code
    resp.json.return_value = json_data
    resp.raise_for_status = MagicMock()
    if status_code >= 400:
        resp.raise_for_status.side_effect = httpx.HTTPStatusError(
            "HTTP error", request=MagicMock(), response=resp
        )
    return resp


def test_temperature_efficiency_curve():
    """
    [논문 정합성 검증]
    - 20도 이상 상온: 1.0 (감소 없음)
    - 0도 저온: 0.86
    - -20도 혹한: 0.67 (33% 주행거리 감소 정합성 확인)
    """
    assert calc_temperature_efficiency(25.0) == 1.0
    assert calc_temperature_efficiency(20.0) == 1.0
    assert round(calc_temperature_efficiency(0.0), 2) == 0.86
    assert round(calc_temperature_efficiency(-20.0), 2) == 0.67
    assert calc_temperature_efficiency(-25.0) == 0.65


def test_elevation_energy_calculation():
    """[물리학 위치에너지 및 회생 상쇄 검증]"""
    gross_kwh = calc_elevation_energy(
        elevation_gain_m=640.0,
        curb_weight_kg=2010.0,
        passengers=2,
        apply_downhill_regen=False,
    )
    assert 4.0 <= gross_kwh <= 4.5

    net_kwh = calc_elevation_energy(
        elevation_gain_m=640.0,
        curb_weight_kg=2010.0,
        passengers=2,
        apply_downhill_regen=True,
    )
    assert 0.9 <= net_kwh <= 1.3
    assert calc_elevation_energy(0.0, 2010.0) == 0.0


def test_cold_start_hvac_spike():
    """[초기 공조/승온 스파이크 검증]"""
    assert calc_cold_start_hvac_spike(-5.0) == 0.8
    assert calc_cold_start_hvac_spike(20.0) == 0.3


def test_proportional_margin_prediction():
    """
    [비례 가중치 기반 3단계 마진 검증]
    - MAX_USE (최대 사용, x1.00): 순수 귀가 필요치(약 28.1%)만 남김
    - NORMAL  (균형, x1.15): +15% 비례 여유 -> 약 32.3%
    - SAFE    (안심, x1.35): +35% 비례 여유 -> 약 37.9%
    """
    res = predict_v2l_lockout(
        current_soc_percent=61.0,
        battery_capacity_kwh=77.4,
        distance_km=86.0,
        elevation_gain_m=640.0,
        expected_temp_celsius=-5.0,
        avg_efficiency_km_kwh=5.4,
        curb_weight_kg=2010.0,
        passengers=2,
    )

    recs = res["recommendations"]
    pure_soc = res["breakdown"]["pure_return_soc_percent"]

    assert "SAFE" in recs
    assert "NORMAL" in recs
    assert "MAX_USE" in recs

    assert recs["MAX_USE"]["reserve_soc_percent"] == pure_soc
    assert recs["NORMAL"]["reserve_soc_percent"] > recs["MAX_USE"]["reserve_soc_percent"]
    assert recs["SAFE"]["reserve_soc_percent"] > recs["NORMAL"]["reserve_soc_percent"]

    short_res = predict_v2l_lockout(
        current_soc_percent=80.0,
        battery_capacity_kwh=58.8,
        distance_km=25.0,
        elevation_gain_m=100.0,
        expected_temp_celsius=5.0,
        avg_efficiency_km_kwh=3.1,
        curb_weight_kg=1950.0,
        passengers=1,
    )
    short_recs = short_res["recommendations"]
    assert 15.0 <= short_recs["MAX_USE"]["reserve_soc_percent"] <= 20.0
    assert short_recs["SAFE"]["reserve_soc_percent"] < 30.0


def test_kma_grid_and_datetime_helpers():
    """기상청 격자 변환(서울시청 60,127 / 안반데기 90,129) 및 발표 시각 계산 검증"""
    assert latlon_to_kma_grid(37.5665, 126.9780) == (60, 127)
    assert latlon_to_kma_grid(37.6242, 128.7420) == (90, 129)

    assert get_latest_kma_base_datetime(datetime(2026, 11, 9, 1, 30, tzinfo=KST)) == ("20261108", "2300")
    assert get_latest_kma_base_datetime(datetime(2026, 11, 9, 14, 5, tzinfo=KST)) == ("20261109", "1100")
    assert get_latest_kma_base_datetime(datetime(2026, 11, 9, 14, 20, tzinfo=KST)) == ("20261109", "1400")

    assert _parse_target_hour("내일 오전 10:00") == "1000"
    assert _parse_target_hour("내일 오후 2:00") == "1400"
    assert _parse_target_hour("오늘 오전 7시") == "0700"
    assert _parse_target_day_offset("오늘 오후 5:00") == 0
    assert _parse_target_day_offset("모레 오전 9:00") == 2
    assert _parse_target_day_offset("내일 오전 10:00") == 1


def test_weather_service_live_mock_and_fallback():
    """기상청 API 파싱 -> Open-Meteo 백업 -> Default Safety Fallback (-5.0℃) 3중 방어 검증"""
    tomorrow_str = (datetime.now(KST) + timedelta(days=1)).strftime("%Y%m%d")
    kma_payload = {
        "response": {
            "header": {"resultCode": "00", "resultMsg": "NORMAL_SERVICE"},
            "body": {
                "items": {
                    "item": [
                        {"fcstDate": tomorrow_str, "fcstTime": "1000", "category": "TMP", "fcstValue": "-3"},
                        {"fcstDate": tomorrow_str, "fcstTime": "1000", "category": "SKY", "fcstValue": "1"},
                    ]
                }
            },
        }
    }

    async def fake_kma_get(url, params=None, **kwargs):
        return _make_mock_response(kma_payload)

    with patch("backend.app.services.weather.KMA_API_KEY", "dummy-kma-key"):
        with patch("httpx.AsyncClient.get", side_effect=fake_kma_get):
            res = asyncio.run(fetch_forecast_temperature("안반데기", "내일 오전 10:00"))
            assert res["temperature"] == -3.0
            assert res["sky_code"] == "1"
            assert res["is_fallback"] is False
            assert "KMA" in res["source"]

    # 1순위 실패 -> 2순위 Open-Meteo 백업 전환 검증
    tomorrow_iso = (datetime.now(KST) + timedelta(days=1)).strftime("%Y-%m-%d")
    open_meteo_payload = {
        "hourly": {
            "time": [f"{tomorrow_iso}T09:00", f"{tomorrow_iso}T10:00"],
            "temperature_2m": [2.5, 4.0],
        }
    }

    async def fake_kma_fail_meteo_ok(url, params=None, **kwargs):
        if "data.go.kr" in url:
            raise httpx.ConnectTimeout("KMA timeout")
        return _make_mock_response(open_meteo_payload)

    with patch("backend.app.services.weather.KMA_API_KEY", "dummy-kma-key"):
        with patch("httpx.AsyncClient.get", side_effect=fake_kma_fail_meteo_ok):
            res_backup = asyncio.run(fetch_forecast_temperature("안반데기", "내일 오전 10:00"))
            assert res_backup["temperature"] == 4.0
            assert res_backup["is_fallback"] is False
            assert "Open-Meteo" in res_backup["source"]

    # 전체 네트워크 차단 또는 force_fallback=True 시 -5.0℃ 안전 기본값 보장 검증
    with patch("httpx.AsyncClient.get", side_effect=httpx.ConnectError("offline")):
        res_offline = asyncio.run(fetch_forecast_temperature("안반데기", "내일 오전 10:00"))
        assert res_offline["temperature"] == -5.0
        assert res_offline["is_fallback"] is True

    fallback_weather = asyncio.run(fetch_forecast_temperature("우리 집", "내일 오전 10:00", force_fallback=True))
    assert fallback_weather["temperature"] == -5.0
    assert fallback_weather["is_fallback"] is True


def test_navigation_service_live_mock_and_fallback():
    """카카오 길찾기 + 고도 상승(Δh) 산출 및 OSRM 백업 / Safety Fallback 검증"""
    kakao_nav_payload = {
        "routes": [
            {
                "result_code": 0,
                "summary": {"distance": 190600, "duration": 12180},
                "sections": [
                    {
                        "roads": [
                            {"vertexes": [128.74, 37.62, 128.00, 37.55, 127.02, 37.49]}
                        ]
                    }
                ],
            }
        ]
    }
    elevation_payload = {"elevation": [950.0, 500.0, 919.0]}  # 내리막(-450) 무시, 오르막(+419)만 합산

    async def fake_nav_get(url, headers=None, params=None, **kwargs):
        if "kakaomobility" in url:
            return _make_mock_response(kakao_nav_payload)
        return _make_mock_response(elevation_payload)

    with patch("backend.app.services.navigation.KAKAO_REST_API_KEY", "dummy-kakao-key"):
        with patch("httpx.AsyncClient.get", side_effect=fake_nav_get):
            res = asyncio.run(fetch_route_topology("강남역"))
            assert res["distance_km"] == 190.6
            assert res["elevation_gain_m"] == 419.0
            assert res["estimated_duration_min"] == 203
            assert res["is_fallback"] is False

    # 전체 네트워크 차단 시 86km / +640m 안전 기본값 보장 검증
    with patch("httpx.AsyncClient.get", side_effect=httpx.ConnectError("offline")):
        res_offline = asyncio.run(fetch_route_topology("우리 집"))
        assert res_offline["distance_km"] == 86.0
        assert res_offline["elevation_gain_m"] == 640.0
        assert res_offline["is_fallback"] is True


def test_places_and_ev_charger_search_mock():
    """카카오 로컬 목적지 검색 및 주변 전기차 충전소 탐색 검증"""
    places_payload = {
        "documents": [
            {
                "place_name": "강남역 2호선",
                "road_address_name": "서울 강남구 강남대로 지하 396",
                "category_group_name": "지하철역",
                "x": "127.0280",
                "y": "37.4981",
            }
        ]
    }
    chargers_payload = {
        "documents": [
            {
                "place_name": "강원강릉 감자원종장 전기차충전소",
                "road_address_name": "강원 강릉시 왕산면 안반데기길 11",
                "distance": "3100",
                "x": "128.7725",
                "y": "37.6105",
            }
        ]
    }

    async def fake_kakao_local(url, headers=None, params=None, **kwargs):
        if params and params.get("query") == "전기차충전소":
            return _make_mock_response(chargers_payload)
        return _make_mock_response(places_payload)

    with patch("backend.app.services.places.KAKAO_REST_API_KEY", "dummy-kakao-key"):
        with patch("httpx.AsyncClient.get", side_effect=fake_kakao_local):
            p_res = asyncio.run(search_places("강남역"))
            assert p_res["is_fallback"] is False
            assert p_res["places"][0]["name"] == "강남역 2호선"

            c_res = asyncio.run(search_nearby_ev_chargers(37.6242, 128.7420))
            assert c_res["is_fallback"] is False
            assert c_res["chargers"][0]["distance_km"] == 3.1

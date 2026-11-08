import asyncio
from backend.app.core.calculator import (
    calc_temperature_efficiency,
    calc_elevation_energy,
    calc_cold_start_hvac_spike,
    predict_v2l_lockout,
)
from backend.app.services.weather import fetch_forecast_temperature
from backend.app.services.navigation import fetch_route_topology


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
    assert calc_temperature_efficiency(-25.0) == 0.65  # 하한선 방어


def test_elevation_energy_calculation():
    """
    [물리학 위치에너지 검증]
    - E = mgh / (3.6e6 * eta)
    - 중량 2010kg + (2 * 70kg) + 40kg = 2190kg
    - 고도 640m 상승 시: 순수 상승 약 4.33 kWh, 내리막 회생 상쇄 후 약 1.08 kWh 소모
    """
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
    """
    [초기 공조/승온 스파이크 검증]
    - 영하 5도일 때: 0.8 kWh
    - 상온 20도일 때: 0.3 kWh
    """
    assert calc_cold_start_hvac_spike(-5.0) == 0.8
    assert calc_cold_start_hvac_spike(20.0) == 0.3


def test_full_prediction_matches_wireframe():
    """
    [와이어프레임 화면 4번 일치성 검증]
    - 거리 86km, 고도 640m, 기온 -5℃, 전비 5.4 km/kWh, 현재 SoC 61%
    - 안심: 정확히 34%
    - 균형: 정확히 28%
    - 최대 사용: 정확히 24%
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
    
    # 3가지 모드 존재 확인
    assert "MAX" in recs
    assert "NORMAL" in recs
    assert "SAFE" in recs

    # 와이어프레임 화면 4번 수치와 100% 일치 검증
    assert recs["MAX"]["reserve_soc_percent"] == 34.0
    assert recs["NORMAL"]["reserve_soc_percent"] == 28.0
    assert recs["SAFE"]["reserve_soc_percent"] == 24.0

    # 잔여 V2L 가용 시간 정상 산출 확인
    assert recs["NORMAL"]["v2l_usable_hours"] >= 20.0


def test_weather_service_and_fallback():
    """기상청 API 정상 조회 및 타임아웃/Fallback 테스트"""
    normal_weather = asyncio.run(fetch_forecast_temperature("우리 집", "내일 오전 10:00"))
    assert normal_weather["temperature"] == -5.0
    assert normal_weather["is_fallback"] is False

    fallback_weather = asyncio.run(fetch_forecast_temperature("우리 집", "내일 오전 10:00", force_fallback=True))
    assert fallback_weather["temperature"] == -5.0
    assert fallback_weather["is_fallback"] is True


def test_navigation_service_and_fallback():
    """내비게이션 토폴로지 API 정상 조회 및 Fallback 테스트"""
    normal_nav = asyncio.run(fetch_route_topology("우리 집"))
    assert normal_nav["distance_km"] == 86.0
    assert normal_nav["elevation_gain_m"] == 640.0
    assert normal_nav["is_fallback"] is False

    charger_nav = asyncio.run(fetch_route_topology("대관령 급속충전소"))
    assert charger_nav["distance_km"] == 12.0
    assert charger_nav["elevation_gain_m"] == 80.0

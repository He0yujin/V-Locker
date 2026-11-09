"""
V2L Safe Locker - 배터리 소모량 예측 코어 엔진 (Prediction Calculator)

[연구 및 이론적 배경]
1. 저온 환경 리튬이온 배터리 성능 저하:
   - 학술 논문: <리튬이온 배터리의 열관리가 전기자동차 주행거리에 미치는 영향> (한국자동차공학회 등)
   - 기준 온도 25℃ 대비 -20℃ 저온에서는 전해질의 점도가 급증하여 리튬 이온의 이동도가 저하되고
     내부 저항이 급증하여 배터리 가용 용량 및 주행거리가 최대 33% 감소함.
2. 고도 변화 및 회생제동(Regeneration) 위치에너지 상쇄:
   - 중력가속도 g = 9.80665 m/s^2, 획득 고도(Elevation Gain) h, 총 차량 질량 m
   - 차박지(고지대)에서 귀가 시 오르막 구간 소모 후 내리막 구간 회생 제동으로 약 75% 에너지가 회수됨.
   - 순 위치에너지 소모 E_elev_net = E_potential * (1 - regen_ratio) [kWh]
3. 콜드 스타트(Cold Start) & HVAC 초기 스파이크:
   - 영하 환경에서 출발 시 결빙된 배터리를 적정 작동 온도로 예열하는 프리컨디셔닝
     및 실내 캐빈 룸 초기 승온(PTC/히트펌프)에 소비되는 에너지 상수 반영.
4. 비례 가중치 기반 3단계 안전 마진 (Proportional Safety Factor):
   - 모든 환경 변수를 결합해 산출한 순수 귀가 필요 전력(E_pure)을 절대 최소 마지노선으로 삼음.
   - MAX_USE (최대 사용): E_pure * 1.00 (비례 여유 0%, 귀가 필수치만 남기고 V2L 극대화)
   - NORMAL  (균형 추천): E_pure * 1.15 (+15% 비례 가중치, 정체 및 일반 변수 대비)
   - SAFE    (안심 모드): E_pure * 1.35 (+35% 비례 가중치, 폭설/혹한/우회로 완벽 방어)
"""

from typing import Dict, Any


GRAVITY_ACCELERATION = 9.80665  # m/s^2
JOULES_TO_KWH = 3.6e6           # 1 kWh = 3.6 * 10^6 J
DEFAULT_PASSENGER_WEIGHT = 70.0 # 1인당 평균 체중 (kg)
DEFAULT_LUGGAGE_WEIGHT = 40.0   # 차박 기본 캠핑 장비 중량 (kg)
DEFAULT_V2L_LOAD_KW = 1.2       # 차박 시 일반적 V2L 소비 전력 (kW)
REGEN_BRAKING_RECOVERY = 0.75   # 산간 귀갓길 내리막 회생제동 에너지 회수율 (75%)
MIN_BMS_PROTECT_SOC = 10.0      # 거북이 모드(출력제한) 진입 및 배터리 수명 보호 하한선 (%)


def calc_temperature_efficiency(temp_celsius: float) -> float:
    """
    익일 아침 예상 기온에 따른 배터리 화학적 효율 저하율(가중치 계수)을 산출합니다.

    [산출 근거]
    - 20℃ 이상: 상온 최적 환경, 효율 100% (1.0)
    - 0℃ ~ 20℃: 완만한 효율 저하 (20℃에서 1.0, 0℃에서 0.86)
    - -20℃ ~ 0℃: 급격한 내부 저항 증가 구간 (0℃에서 0.86, -20℃에서 0.67 -> 33% 감소 반영)
    - -20℃ 미만: 극한 저온 안전 하한선 0.65 적용
    """
    if temp_celsius >= 20.0:
        return 1.0
    elif 0.0 <= temp_celsius < 20.0:
        return 1.0 - 0.007 * (20.0 - temp_celsius)
    elif -20.0 <= temp_celsius < 0.0:
        return 0.86 - 0.0095 * abs(temp_celsius)
    else:
        return 0.65


def calc_elevation_energy(
    elevation_gain_m: float,
    curb_weight_kg: float,
    passengers: int = 2,
    luggage_kg: float = DEFAULT_LUGGAGE_WEIGHT,
    powertrain_efficiency: float = 0.88,
    apply_downhill_regen: bool = True,
) -> float:
    """
    지형 토폴로지(순수 획득 고도) 및 내리막 회생제동 상쇄를 고려한 순수 위치에너지 소모량을 계산합니다.
    """
    if elevation_gain_m <= 0:
        return 0.0

    total_mass = curb_weight_kg + (passengers * DEFAULT_PASSENGER_WEIGHT) + luggage_kg
    potential_joules = total_mass * GRAVITY_ACCELERATION * elevation_gain_m
    gross_kwh = potential_joules / (JOULES_TO_KWH * powertrain_efficiency)

    # 차박지 귀갓길 특성: 고지대에서 평지로 하강하며 발생하는 내리막 회생 제동 상쇄 반영
    net_kwh = gross_kwh * (1.0 - REGEN_BRAKING_RECOVERY) if apply_downhill_regen else gross_kwh
    return round(net_kwh, 2)


def calc_cold_start_hvac_spike(temp_celsius: float) -> float:
    """
    출발 시점의 배터리 승온 컨디셔닝 및 실내 급속 난방 초기 공조 에너지 스파이크를 산출합니다.
    """
    if temp_celsius >= 15.0:
        return 0.3
    elif 0.0 <= temp_celsius < 15.0:
        return 0.5
    elif -10.0 <= temp_celsius < 0.0:
        return 0.8
    else:
        return 1.8


def predict_v2l_lockout(
    current_soc_percent: float,
    battery_capacity_kwh: float,
    distance_km: float,
    elevation_gain_m: float,
    expected_temp_celsius: float,
    avg_efficiency_km_kwh: float,
    curb_weight_kg: float,
    passengers: int = 2,
    v2l_current_load_kw: float = DEFAULT_V2L_LOAD_KW,
) -> Dict[str, Any]:
    """
    모든 입력 변수(거리, 고도, 기온, 차량 전비, 공차중량, 배터리 용량, 탑승인원)를 동적으로 결합해
    순수 귀가 최소 전력량(E_pure)을 구하고, 비례 가중치 기반 3단계 안전 마진 모드를 도출합니다.
    """
    # 1. 기온 효율 계수
    eta_temp = calc_temperature_efficiency(expected_temp_celsius)

    # 2. 평지 기준 기본 주행 에너지 (kWh)
    if avg_efficiency_km_kwh <= 0:
        raise ValueError("평균 전비(avg_efficiency_km_kwh)는 0보다 커야 합니다.")
    base_driving_kwh = distance_km / avg_efficiency_km_kwh

    # 3. 고도 위치에너지 (내리막 회생제동 상쇄 반영)
    elevation_kwh = calc_elevation_energy(
        elevation_gain_m=elevation_gain_m,
        curb_weight_kg=curb_weight_kg,
        passengers=passengers,
        apply_downhill_regen=True,
    )

    # 4. 저온 효율 저하 보정
    compensated_driving_kwh = (base_driving_kwh + elevation_kwh) / eta_temp

    # 5. 콜드 스타트 및 초기 HVAC 에너지 스파이크
    spike_kwh = calc_cold_start_hvac_spike(expected_temp_celsius)

    # 6. 순수 귀가 필수 전력 (kWh) -> 이것이 곧 '최대 사용(MAX_USE)'의 바닥선!
    pure_return_kwh = compensated_driving_kwh + spike_kwh
    pure_return_soc = (pure_return_kwh / battery_capacity_kwh) * 100.0

    # 비례 가중치(Proportional Safety Factor) 기반 3단계 마진 구성:
    # - MAX_USE (최대 사용): x 1.00 (비례 마진 +0% -> 순수 필요치만 남김)
    # - NORMAL  (균형 추천): x 1.15 (비례 마진 +15% -> 일반 정체/변수 대비)
    # - SAFE    (안심 모드): x 1.35 (비례 마진 +35% -> 혹한/폭설/우회로 완벽 방어)
    margin_configs = [
        {
            "mode": "SAFE",
            "name_kr": "안심",
            "multiplier": 1.35,
            "desc": "예측 전력의 +35% 여유 확보 · 폭설·정체·추가 난방까지 완벽 방어",
        },
        {
            "mode": "NORMAL",
            "name_kr": "균형",
            "multiplier": 1.15,
            "desc": "예측 전력의 +15% 여유 확보 · 주행 및 환경 변수를 고려한 표준 추천",
        },
        {
            "mode": "MAX_USE",
            "name_kr": "최대 사용",
            "multiplier": 1.00,
            "desc": "귀가 최소 필수 전력만 보존 · V2L 가용 시간을 최대로 확보",
        },
    ]

    recommendations = {}
    current_kwh = (current_soc_percent / 100.0) * battery_capacity_kwh
    load_kw = v2l_current_load_kw if v2l_current_load_kw > 0.1 else DEFAULT_V2L_LOAD_KW

    for cfg in margin_configs:
        target_kwh_raw = pure_return_kwh * cfg["multiplier"]
        raw_soc = (target_kwh_raw / battery_capacity_kwh) * 100.0

        # 배터리 심방전 및 거북이 모드 진입 방지를 위한 최소 10% 하한선 방어
        target_soc = round(min(95.0, max(MIN_BMS_PROTECT_SOC, raw_soc)), 1)
        final_target_kwh = round((target_soc / 100.0) * battery_capacity_kwh, 2)

        v2l_usable_kwh = max(0.0, current_kwh - final_target_kwh)
        usable_hours = round(v2l_usable_kwh / load_kw, 1)

        recommendations[cfg["mode"]] = {
            "mode": cfg["mode"],
            "mode_name_kr": cfg["name_kr"],
            "safety_multiplier": cfg["multiplier"],
            "reserve_soc_percent": target_soc,
            "required_kwh": final_target_kwh,
            "v2l_usable_kwh": round(v2l_usable_kwh, 2),
            "v2l_usable_hours": usable_hours,
            "is_sufficient": current_soc_percent > target_soc,
            "description": cfg["desc"],
        }

    return {
        "input_summary": {
            "distance_km": distance_km,
            "elevation_gain_m": elevation_gain_m,
            "expected_temp_celsius": expected_temp_celsius,
            "avg_efficiency_km_kwh": avg_efficiency_km_kwh,
            "curb_weight_kg": curb_weight_kg,
            "current_soc_percent": current_soc_percent,
            "battery_capacity_kwh": battery_capacity_kwh,
            "passengers": passengers,
            "v2l_current_load_kw": load_kw,
        },
        "breakdown": {
            "base_driving_kwh": round(base_driving_kwh, 2),
            "elevation_kwh": round(elevation_kwh, 2),
            "temperature_efficiency_factor": round(eta_temp, 3),
            "efficiency_loss_percent": round((1.0 - eta_temp) * 100.0, 1),
            "cold_start_hvac_spike_kwh": round(spike_kwh, 2),
            "pure_return_kwh": round(pure_return_kwh, 2),
            "pure_return_soc_percent": round(pure_return_soc, 1),
        },
        "recommendations": recommendations,
    }

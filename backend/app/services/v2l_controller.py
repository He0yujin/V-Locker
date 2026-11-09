"""
V2L 및 차량 하드웨어 제어 시뮬레이션 서비스 (V2L Controller Service)

[핵심 역할]
1. 배터리 SoC 추적 및 차단 기준점 도달 여부 모니터링
2. 사전 단계별 예고 알림 (차단 1시간 전: WARN_60M, 30분 전: WARN_30M)
3. 목표 SoC 도달 시 V2L 물리 릴레이 차단 (LOCKED_OUT)
4. 차단 즉시 동사 방지용 차량 무시동 히터(유틸리티 모드 21℃) 자동 가동 트리거
"""

from typing import Dict, Any, Optional
from sqlalchemy.orm import Session
from backend.app.db.models import V2LSession
from backend.app.core.calculator import predict_v2l_lockout

EMERGENCY_CHARGER_RESERVE_MIN_SOC = 15.0  # 거북이 모드(10%) + 비상 충전소 대기/우회 여유(5%)


def update_session_soc(
    db: Session,
    session: V2LSession,
    new_soc: float,
    current_load_kw: Optional[float] = None,
) -> Dict[str, Any]:
    """
    실시간 배터리 잔량을 업데이트하고 상태 전이(State Transition)를 평가합니다.

    - 1시간 전 경고: (current_soc - target_soc) * 배터리용량 / load_kw <= 1.0 hr (약 1.5%p ~ 2.0%p 남음)
    - 30분 전 경고: (current_soc - target_soc) * 배터리용량 / load_kw <= 0.5 hr (약 0.8%p ~ 1.0%p 남음)
    - 락아웃 차단: current_soc <= target_soc
    """
    if current_load_kw is not None and current_load_kw > 0.1:
        session.current_v2l_load_kw = current_load_kw

    session.current_soc = round(max(0.0, new_soc), 1)
    battery_cap = session.user.battery_capacity if session.user else 77.4
    load_kw = session.current_v2l_load_kw or 1.2

    # 잔여 전력 및 가용 시간 산출
    remaining_kwh = max(0.0, (session.current_soc - session.target_soc) / 100.0 * battery_cap)
    hours_left = round(remaining_kwh / load_kw, 1)

    warning_level = None

    # 상태 전이 로직
    if session.current_soc <= session.target_soc:
        # 배터리 임계치 도달 -> 물리 릴레이 즉시 차단 & 무시동 히터 자동 전환
        session.v2l_status = "LOCKED_OUT"
        session.relay_connected = False
        session.utility_mode_active = True
    elif hours_left <= 0.5 or (session.current_soc - session.target_soc) <= 1.0:
        session.v2l_status = "WARN_30M"
        warning_level = 30
    elif hours_left <= 1.0 or (session.current_soc - session.target_soc) <= 2.0:
        session.v2l_status = "WARN_60M"
        warning_level = 60
    else:
        session.v2l_status = "ACTIVE"

    db.commit()
    db.refresh(session)

    return {
        "session_id": session.id,
        "current_soc": session.current_soc,
        "target_soc": session.target_soc,
        "remaining_margin_pct": round(max(0.0, session.current_soc - session.target_soc), 1),
        "current_v2l_load_kw": session.current_v2l_load_kw,
        "estimated_hours_left": hours_left,
        "v2l_status": session.v2l_status,
        "warning_level": warning_level,
        "relay_connected": session.relay_connected,
        "utility_mode_active": session.utility_mode_active,
    }


def execute_v2l_lockout(db: Session, session: V2LSession, reason: str = "MANUAL") -> Dict[str, Any]:
    """
    V2L 전력 공급을 물리적으로 차단하고 차량 내부 유틸리티 모드(21℃ 무시동 히터)를 자동 작동합니다.
    """
    session.v2l_status = "LOCKED_OUT"
    session.relay_connected = False
    session.utility_mode_active = True
    db.commit()
    db.refresh(session)

    return {
        "session_id": session.id,
        "v2l_status": "LOCKED_OUT",
        "relay_connected": False,
        "utility_mode_active": True,
        "cabin_target_temp_celsius": 21.0,
        "bms_preserved_soc": session.current_soc,
        "return_distance_guaranteed_km": round(session.distance_km * 1.1, 1),
    }


def extend_v2l_session(
    db: Session,
    session: V2LSession,
    charger_name: str,
    distance_km: float,
    elevation_gain_m: float,
) -> Dict[str, Any]:
    """
    30분 전 알림에서 [사용 연장] 요청 시, 인근 급속 충전소 경로(실제 거리/고도/기온/차량 스펙)를
    코어 예측 엔진(predict_v2l_lockout)으로 재계산해 안전하게 추가 가용 시간을 확보합니다.
    """
    prev_target = session.target_soc
    battery_cap = session.user.battery_capacity if session.user else 77.4
    avg_eff = session.user.avg_efficiency if session.user else 5.4
    curb_wt = float(session.user.curb_weight) if session.user else 2010.0
    load_kw = session.current_v2l_load_kw or 1.2

    charger_pred = predict_v2l_lockout(
        current_soc_percent=session.current_soc,
        battery_capacity_kwh=battery_cap,
        distance_km=distance_km,
        elevation_gain_m=elevation_gain_m,
        expected_temp_celsius=session.expected_temp,
        avg_efficiency_km_kwh=avg_eff,
        curb_weight_kg=curb_wt,
        passengers=session.passengers,
        v2l_current_load_kw=load_kw,
    )
    dynamic_charger_soc = charger_pred["recommendations"]["NORMAL"]["reserve_soc_percent"]
    new_target = round(min(prev_target, max(EMERGENCY_CHARGER_RESERVE_MIN_SOC, dynamic_charger_soc)), 1)
    gained_pct = max(0.0, round(prev_target - new_target, 1))

    added_hours = round((gained_pct / 100.0 * battery_cap) / load_kw, 1)

    session.destination_name = f"{charger_name} (비상 충전소 경유)"
    session.distance_km = distance_km
    session.elevation_gain_m = elevation_gain_m
    session.target_soc = new_target
    session.v2l_status = "ACTIVE" # 경고 상태 해제
    session.extended_count += 1

    db.commit()
    db.refresh(session)

    return {
        "session_id": session.id,
        "new_destination": session.destination_name,
        "previous_target_soc": prev_target,
        "new_target_soc": new_target,
        "gained_soc_pct": gained_pct,
        "additional_hours": added_hours,
        "v2l_status": session.v2l_status,
    }


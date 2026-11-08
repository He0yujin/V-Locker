"""
V2L Safe Locker - RESTful API 라우터 (Routes)

[지침 준수]
- 통일된 JSON 응답 규격: {"status": 200, "data": {...}, "message": "..."}
- RESTful API 컨벤션 준수
"""

from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session

from backend.app.db.database import get_db
from backend.app.db.models import User, V2LSession
from backend.app.schemas.session import (
    APIResponse,
    PredictRequest,
    CreateSessionRequest,
    TickRequest,
    ExtendRequest,
)
from backend.app.services.weather import fetch_forecast_temperature
from backend.app.services.navigation import fetch_route_topology
from backend.app.services.v2l_controller import (
    update_session_soc,
    execute_v2l_lockout,
    extend_v2l_session,
)
from backend.app.core.calculator import predict_v2l_lockout

router = APIRouter(prefix="/api", tags=["V2L Safe Locker"])


def get_default_user(db: Session) -> User:
    user = db.query(User).first()
    if not user:
        user = User(
            name="캠퍼 드라이버",
            vehicle_model="IONIQ 5 Long Range",
            battery_capacity=77.4,
            curb_weight=2010,
            avg_efficiency=5.4,
        )
        db.add(user)
        db.commit()
        db.refresh(user)
    return user


@router.post("/predict", response_model=APIResponse)
async def predict_battery_consumption(
    req: PredictRequest,
    db: Session = Depends(get_db),
):
    """
    [핵심 기능 1] 다중 데이터 융합 배터리 소모량 예측 API
    기상청 API(기온) + 내비게이션 API(고도) + 차량 전비/중량을 결합하여
    안심/균형/최대사용 3단계 모드 및 V2L 잔여 가용 시간을 산출합니다.
    """
    user = get_default_user(db)

    # 1. 기상청 API 연동 (3초 타임아웃 / Fallback 방어)
    if req.expected_temp_celsius is not None:
        weather_data = {
            "temperature": req.expected_temp_celsius,
            "is_fallback": False,
            "description": f"수동 지정 기온 ({req.expected_temp_celsius}℃)",
        }
    else:
        weather_data = await fetch_forecast_temperature(
            destination_name=req.destination_name,
            departure_time=req.departure_time,
            force_fallback=req.force_fallback,
        )

    # 2. 내비게이션 토폴로지 API 연동 (3초 타임아웃 / Fallback 방어)
    nav_data = await fetch_route_topology(
        destination_name=req.destination_name,
        force_fallback=req.force_fallback,
    )

    # 3. 배터리 소모량 예측 코어 엔진 연산
    curr_soc = req.current_soc_percent if req.current_soc_percent is not None else 61.0
    dist_km = nav_data["distance_km"] if req.distance_km == 86.0 else req.distance_km
    elev_m = nav_data["elevation_gain_m"] if req.elevation_gain_m == 640.0 else req.elevation_gain_m

    calc_result = predict_v2l_lockout(
        current_soc_percent=curr_soc,
        battery_capacity_kwh=user.battery_capacity,
        distance_km=dist_km,
        elevation_gain_m=elev_m,
        expected_temp_celsius=weather_data["temperature"],
        avg_efficiency_km_kwh=user.avg_efficiency,
        curb_weight_kg=float(user.curb_weight),
        passengers=req.passengers,
    )

    is_any_fallback = weather_data.get("is_fallback", False) or nav_data.get("is_fallback", False)

    return APIResponse(
        status=200,
        data={
            "weather": weather_data,
            "navigation": nav_data,
            "is_fallback": is_any_fallback,
            "breakdown": calc_result["breakdown"],
            "recommendations": calc_result["recommendations"],
        },
        message="AI 귀가 전력 분석 및 3단계 안전 마진 추천이 완료되었습니다.",
    )


@router.post("/sessions", response_model=APIResponse)
def create_session(
    req: CreateSessionRequest,
    db: Session = Depends(get_db),
):
    """
    [핵심 기능 2] 모니터링 세션 시작 API
    선택된 안전 마진(예: 균형 28%)으로 V2L 안전 모니터링을 활성화합니다.
    """
    user = get_default_user(db)

    session = V2LSession(
        user_id=user.id,
        destination_name=req.destination_name,
        distance_km=req.distance_km,
        elevation_gain_m=req.elevation_gain_m,
        expected_temp=req.expected_temp,
        departure_time=req.departure_time,
        passengers=req.passengers,
        margin_mode=req.margin_mode,
        target_soc=req.target_soc,
        current_soc=req.current_soc,
        current_v2l_load_kw=req.current_v2l_load_kw,
        is_fallback=req.is_fallback,
        v2l_status="ACTIVE",
        relay_connected=True,
        utility_mode_active=False,
    )
    db.add(session)
    db.commit()
    db.refresh(session)

    # 남은 가용 시간 계산
    remain_kwh = max(0.0, (session.current_soc - session.target_soc) / 100.0 * user.battery_capacity)
    hours_left = round(remain_kwh / session.current_v2l_load_kw, 1)

    return APIResponse(
        status=200,
        data={
            "session_id": session.id,
            "destination_name": session.destination_name,
            "current_soc": session.current_soc,
            "target_soc": session.target_soc,
            "remaining_margin_pct": round(session.current_soc - session.target_soc, 1),
            "current_v2l_load_kw": session.current_v2l_load_kw,
            "estimated_hours_left": hours_left,
            "v2l_status": session.v2l_status,
            "relay_connected": session.relay_connected,
            "utility_mode_active": session.utility_mode_active,
            "is_fallback": session.is_fallback,
        },
        message="V2L 안전 모니터링 세션이 성공적으로 활성화되었습니다.",
    )


@router.get("/sessions/{session_id}", response_model=APIResponse)
def get_session_status(
    session_id: int,
    db: Session = Depends(get_db),
):
    """
    실시간 세션 상태 조회 API
    현재 SoC, V2L 전원 상태, 단계별 알림 레벨, 무시동 히터 가동 여부를 반환합니다.
    """
    session = db.query(V2LSession).filter(V2LSession.id == session_id).first()
    if not session:
        raise HTTPException(status_code=404, detail="세션을 찾을 수 없습니다.")

    battery_cap = session.user.battery_capacity if session.user else 77.4
    remain_kwh = max(0.0, (session.current_soc - session.target_soc) / 100.0 * battery_cap)
    hours_left = round(remain_kwh / (session.current_v2l_load_kw or 1.2), 1)

    warning_level = None
    if session.v2l_status == "WARN_30M":
        warning_level = 30
    elif session.v2l_status == "WARN_60M":
        warning_level = 60

    return APIResponse(
        status=200,
        data={
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
            "is_fallback": session.is_fallback,
            "destination_summary": {
                "destination": session.destination_name,
                "departure": session.departure_time,
                "distance_km": session.distance_km,
                "elevation_gain_m": session.elevation_gain_m,
                "temp_celsius": session.expected_temp,
                "margin_mode": session.margin_mode,
            },
        },
        message="세션 상태 조회 성공",
    )


@router.post("/sessions/{session_id}/tick", response_model=APIResponse)
def simulate_session_tick(
    session_id: int,
    req: TickRequest,
    db: Session = Depends(get_db),
):
    """
    [시연용 가속 시뮬레이터 API]
    배터리를 인위적으로 감소시켜 1시간 전/30분 전 알림 및 락아웃 전 과정을 테스트합니다.
    """
    session = db.query(V2LSession).filter(V2LSession.id == session_id).first()
    if not session:
        raise HTTPException(status_code=404, detail="세션을 찾을 수 없습니다.")

    new_soc = session.current_soc - req.soc_decrement
    status_data = update_session_soc(db, session, new_soc, req.load_kw)

    msg = "시뮬레이션 배터리 감소 완료"
    if status_data["v2l_status"] == "LOCKED_OUT":
        msg = "목표 배터리에 도달하여 V2L이 안전 차단되었으며 무시동 히터가 가동되었습니다."
    elif status_data["v2l_status"] == "WARN_30M":
        msg = "차단 30분 전 긴급 예고 알림 발생!"
    elif status_data["v2l_status"] == "WARN_60M":
        msg = "차단 1시간 전 예고 알림 발생"

    return APIResponse(
        status=200,
        data=status_data,
        message=msg,
    )


@router.post("/sessions/{session_id}/extend", response_model=APIResponse)
def extend_session(
    session_id: int,
    req: ExtendRequest,
    db: Session = Depends(get_db),
):
    """
    [30분 전 알림 사용 연장 API]
    인근 급속 충전소 경로를 기반으로 목표 SoC를 안전하게 재계산하여 가용 시간을 연장합니다.
    """
    session = db.query(V2LSession).filter(V2LSession.id == session_id).first()
    if not session:
        raise HTTPException(status_code=404, detail="세션을 찾을 수 없습니다.")

    result = extend_v2l_session(
        db=db,
        session=session,
        charger_name=req.nearest_charger_name,
        distance_km=req.distance_km,
        elevation_gain_m=req.elevation_gain_m,
    )

    return APIResponse(
        status=200,
        data=result,
        message=f"{req.nearest_charger_name} 기준 안전 마진이 재계산되어 V2L 사용 시간이 {result['additional_hours']}시간 연장되었습니다.",
    )


@router.post("/sessions/{session_id}/lockout", response_model=APIResponse)
def manual_or_forced_lockout(
    session_id: int,
    db: Session = Depends(get_db),
):
    """
    [V2L 물리 차단 & 무시동 히터 페일세이프 API]
    수동 또는 비상 시 V2L 릴레이를 차단하고 실내 무시동 히터(21℃)를 가동합니다.
    """
    session = db.query(V2LSession).filter(V2LSession.id == session_id).first()
    if not session:
        raise HTTPException(status_code=404, detail="세션을 찾을 수 없습니다.")

    result = execute_v2l_lockout(db, session, reason="USER_REQUEST")

    return APIResponse(
        status=200,
        data=result,
        message="안전을 위해 V2L이 정상 차단되었으며, 동사 방지를 위해 차량 무시동 히터가 21℃로 가동되었습니다.",
    )


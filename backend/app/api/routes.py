"""
V2L Safe Locker - RESTful API 라우터 (Routes)

[지침 준수]
- 통일된 JSON 응답 규격: {"status": 200, "data": {...}, "message": "..."}
- RESTful API 컨벤션 준수
"""

from fastapi import APIRouter, Depends, HTTPException, Query
from sqlalchemy.orm import Session

from backend.app.db.database import get_db
from backend.app.db.models import User, V2LSession
from backend.app.schemas.session import (
    APIResponse,
    UserVehicleUpdateRequest,
    PredictRequest,
    CreateSessionRequest,
    TickRequest,
    ExtendRequest,
)
from backend.app.services.places import search_places, search_nearby_ev_chargers
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


@router.get("/places/search", response_model=APIResponse)
async def api_search_places(
    q: str = Query(..., min_length=1, description="목적지 검색 키워드 (예: 강남역, 안반데기)"),
    size: int = Query(5, ge=1, le=15),
):
    """[신규] 카카오 로컬 키워드 검색 API를 통해 목적지 후보 및 위경도를 조회합니다."""
    res = await search_places(query=q, size=size)
    return APIResponse(
        status=200,
        data=res,
        message=f"'{q}' 장소 검색 완료 ({len(res.get('places', []))}건)",
    )


@router.get("/chargers/nearby", response_model=APIResponse)
async def api_search_nearby_chargers(
    lat: float = Query(37.6242, description="현재 위치 위도"),
    lng: float = Query(128.7420, description="현재 위치 경도"),
    radius_m: int = Query(20000, description="탐색 반경 (m)"),
):
    """[신규] 현재 위치 기준 반경 내 가장 가까운 실제 전기차 충전소를 검색합니다."""
    res = await search_nearby_ev_chargers(lat=lat, lng=lng, radius_m=radius_m)
    return APIResponse(
        status=200,
        data=res,
        message=f"주변 전기차 충전소 탐색 완료 ({len(res.get('chargers', []))}건)",
    )


@router.get("/user", response_model=APIResponse)
def get_user_vehicle(db: Session = Depends(get_db)):
    """현재 등록된 차량 스펙 및 운전자 누적 평균 전비 조회"""
    user = get_default_user(db)
    return APIResponse(
        status=200,
        data={
            "id": user.id,
            "name": user.name,
            "vehicle_model": user.vehicle_model,
            "battery_capacity": user.battery_capacity,
            "curb_weight": user.curb_weight,
            "avg_efficiency": user.avg_efficiency,
        },
        message="차량 및 운전자 전비 정보 조회 성공",
    )


@router.put("/user", response_model=APIResponse)
def update_user_vehicle(req: UserVehicleUpdateRequest, db: Session = Depends(get_db)):
    """차량 모델, 배터리 용량, 공차중량, 평소 전비 변수 동적 변경 및 DB 저장"""
    user = get_default_user(db)
    if req.name:
        user.name = req.name
    user.vehicle_model = req.vehicle_model
    user.battery_capacity = req.battery_capacity
    user.curb_weight = req.curb_weight
    user.avg_efficiency = req.avg_efficiency
    db.commit()
    db.refresh(user)

    return APIResponse(
        status=200,
        data={
            "id": user.id,
            "name": user.name,
            "vehicle_model": user.vehicle_model,
            "battery_capacity": user.battery_capacity,
            "curb_weight": user.curb_weight,
            "avg_efficiency": user.avg_efficiency,
        },
        message="차량 및 운전자 전비 정보가 성공적으로 업데이트되었습니다.",
    )


@router.post("/predict", response_model=APIResponse)
async def predict_battery_consumption(
    req: PredictRequest,
    db: Session = Depends(get_db),
):
    """
    [핵심 기능 1] 실시간 다중 데이터 융합 배터리 소모량 예측 API
    1) 목적지 좌표 미지정 시 카카오 로컬 검색으로 자동 좌표 변환
    2) 카카오모빌리티 자동차 길찾기 + 지형 고도 API 연동
    3) 기상청 단기예보 API (출발지/목적지 격자 변환) 연동
    4) 비례 가중치 기반 3단계 마진(최대사용 / 균형 / 안심) 산출
    """
    user = get_default_user(db)

    # 0. 목적지 좌표 확인 (없거나 Swagger 기본값 0인 경우 카카오 로컬 검색으로 자동 매핑)
    d_lat = None if req.dest_lat == 0 else req.dest_lat
    d_lng = None if req.dest_lng == 0 else req.dest_lng
    resolved_dest_name = req.destination_name

    if (d_lat is None or d_lng is None) and not req.force_fallback:
        # "우리 집" 같은 별칭이면 기본 서울 강남 좌표 사용, 실제 지명이면 카카오 검색
        if "우리 집" in req.destination_name or "우리집" in req.destination_name:
            d_lat, d_lng = 37.4979, 127.0276
        else:
            place_res = await search_places(req.destination_name, size=1)
            places = place_res.get("places", [])
            if places:
                d_lat = places[0]["lat"]
                d_lng = places[0]["lng"]
                resolved_dest_name = places[0]["name"]

    if d_lat is None or d_lng is None:
        d_lat, d_lng = 37.4979, 127.0276  # 기본 서울 강남

    # 1. 기상청 단기예보 API 연동 (직접 지정 시 오버라이드, 없으면 API 호출 + Fallback)
    if req.expected_temp_celsius is not None:
        weather_data = {
            "temperature": req.expected_temp_celsius,
            "is_fallback": False,
            "source": "User Override",
            "description": f"사용자 지정 기온 ({req.expected_temp_celsius}℃)",
        }
    else:
        # 차박지(출발지)의 아침 최저 기온이 콜드 스타트 및 초기 저온에 직접 영향을 미침
        weather_data = await fetch_forecast_temperature(
            destination_name=resolved_dest_name,
            departure_time=req.departure_time,
            lat=req.origin_lat,
            lng=req.origin_lng,
            force_fallback=req.force_fallback,
        )

    # 2. 내비게이션 토폴로지 API 연동 (직접 지정 시 오버라이드, 없으면 카카오 길찾기 + 고도 API)
    if req.distance_km is not None and req.elevation_gain_m is not None:
        nav_data = {
            "distance_km": req.distance_km,
            "elevation_gain_m": req.elevation_gain_m,
            "estimated_duration_min": round(req.distance_km * 1.1),
            "is_fallback": False,
            "source": "User Override",
            "description": f"사용자 지정 경로 ({req.distance_km}km, +{req.elevation_gain_m}m)",
        }
    else:
        nav_data = await fetch_route_topology(
            destination_name=resolved_dest_name,
            origin_lat=req.origin_lat,
            origin_lng=req.origin_lng,
            dest_lat=d_lat,
            dest_lng=d_lng,
            force_fallback=req.force_fallback,
        )

    dist_km = req.distance_km if req.distance_km is not None else nav_data["distance_km"]
    elev_m = req.elevation_gain_m if req.elevation_gain_m is not None else nav_data["elevation_gain_m"]

    # 3. 차량 변수 결정 (요청 오버라이드 > DB 저장된 유저/차량 정보)
    bat_cap = req.battery_capacity_kwh if req.battery_capacity_kwh is not None else user.battery_capacity
    avg_eff = req.avg_efficiency_km_kwh if req.avg_efficiency_km_kwh is not None else user.avg_efficiency
    curb_wt = req.curb_weight_kg if req.curb_weight_kg is not None else float(user.curb_weight)

    # 4. 배터리 소모량 예측 코어 엔진 연산 (비례 가중치 모델)
    calc_result = predict_v2l_lockout(
        current_soc_percent=req.current_soc_percent,
        battery_capacity_kwh=bat_cap,
        distance_km=dist_km,
        elevation_gain_m=elev_m,
        expected_temp_celsius=weather_data["temperature"],
        avg_efficiency_km_kwh=avg_eff,
        curb_weight_kg=curb_wt,
        passengers=req.passengers,
        v2l_current_load_kw=req.v2l_current_load_kw,
    )

    is_any_fallback = weather_data.get("is_fallback", False) or nav_data.get("is_fallback", False)

    return APIResponse(
        status=200,
        data={
            "destination": {
                "name": resolved_dest_name,
                "origin_lat": req.origin_lat,
                "origin_lng": req.origin_lng,
                "dest_lat": d_lat,
                "dest_lng": d_lng,
            },
            "vehicle": {
                "vehicle_model": user.vehicle_model,
                "battery_capacity_kwh": bat_cap,
                "avg_efficiency_km_kwh": avg_eff,
                "curb_weight_kg": curb_wt,
            },
            "weather": weather_data,
            "navigation": {
                **nav_data,
                "distance_km": dist_km,
                "elevation_gain_m": elev_m,
            },
            "is_fallback": is_any_fallback,
            "breakdown": calc_result["breakdown"],
            "recommendations": calc_result["recommendations"],
        },
        message="AI 귀가 전력 분석 및 비례 가중치 기반 3단계 안전 마진 추천이 완료되었습니다.",
    )


@router.post("/sessions", response_model=APIResponse)
def create_session(
    req: CreateSessionRequest,
    db: Session = Depends(get_db),
):
    """[핵심 기능 2] 모니터링 세션 시작 API"""
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

    remain_kwh = max(0.0, (session.current_soc - session.target_soc) / 100.0 * user.battery_capacity)
    hours_left = round(remain_kwh / session.current_v2l_load_kw, 1)

    return APIResponse(
        status=200,
        data={
            "session_id": session.id,
            "destination_name": session.destination_name,
            "current_soc": session.current_soc,
            "target_soc": session.target_soc,
            "remaining_margin_pct": round(max(0.0, session.current_soc - session.target_soc), 1),
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
    """실시간 세션 상태 조회 API"""
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
    """[시연용 가속 시뮬레이터 API]"""
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
async def extend_session(
    session_id: int,
    req: ExtendRequest,
    db: Session = Depends(get_db),
):
    """[30분 전 알림 사용 연장 API] 실제 인근 급속 충전소를 검색해 안전 마진을 재계산합니다."""
    session = db.query(V2LSession).filter(V2LSession.id == session_id).first()
    if not session:
        raise HTTPException(status_code=404, detail="세션을 찾을 수 없습니다.")

    c_name = None if req.nearest_charger_name in (None, "", "string") else req.nearest_charger_name
    c_dist = None if (req.distance_km is None or req.distance_km <= 0) else req.distance_km
    c_elev = req.elevation_gain_m

    # 충전소가 명시되지 않은 경우 실제 카카오 로컬에서 가까운 충전소 탐색 + 경로 토폴로지 반영
    if c_name is None or c_dist is None:
        charger_res = await search_nearby_ev_chargers(lat=req.origin_lat, lng=req.origin_lng, size=1)
        chargers = charger_res.get("chargers", [])
        if chargers:
            c_name = chargers[0]["name"]
            c_dist = chargers[0]["distance_km"]
            if not charger_res.get("is_fallback"):
                nav_res = await fetch_route_topology(
                    destination_name=c_name,
                    origin_lat=req.origin_lat,
                    origin_lng=req.origin_lng,
                    dest_lat=chargers[0]["lat"],
                    dest_lng=chargers[0]["lng"],
                )
                if not nav_res.get("is_fallback"):
                    c_dist = nav_res["distance_km"]
                    c_elev = nav_res["elevation_gain_m"]
        else:
            c_name = "인근 급속충전소"
            c_dist = 12.0

    result = extend_v2l_session(
        db=db,
        session=session,
        charger_name=c_name,
        distance_km=c_dist,
        elevation_gain_m=c_elev,
    )

    return APIResponse(
        status=200,
        data=result,
        message=f"{c_name} ({c_dist}km) 기준 안전 마진이 재계산되어 V2L 사용 시간이 {result['additional_hours']}시간 연장되었습니다.",
    )


@router.post("/sessions/{session_id}/lockout", response_model=APIResponse)
def manual_or_forced_lockout(
    session_id: int,
    db: Session = Depends(get_db),
):
    """[V2L 물리 차단 & 무시동 히터 페일세이프 API]"""
    session = db.query(V2LSession).filter(V2LSession.id == session_id).first()
    if not session:
        raise HTTPException(status_code=404, detail="세션을 찾을 수 없습니다.")

    result = execute_v2l_lockout(db, session, reason="USER_REQUEST")

    return APIResponse(
        status=200,
        data=result,
        message="안전을 위해 V2L이 정상 차단되었으며, 동사 방지를 위해 차량 무시동 히터가 21℃로 가동되었습니다.",
    )

import pytest
from unittest.mock import patch
from fastapi.testclient import TestClient
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker

from backend.app.db.database import Base, get_db
from backend.app.main import app


@pytest.fixture()
def client(tmp_path):
    """테스트마다 독립된 임시 SQLite DB를 사용해 로컬 개발 DB(v2l_safe_locker.db)를 오염시키지 않음"""
    test_db_path = tmp_path / "test_v2l.db"
    engine = create_engine(
        f"sqlite:///{test_db_path}",
        connect_args={"check_same_thread": False},
    )
    TestingSessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)
    Base.metadata.create_all(bind=engine)

    def override_get_db():
        db = TestingSessionLocal()
        try:
            yield db
        finally:
            db.close()

    app.dependency_overrides[get_db] = override_get_db
    with TestClient(app) as test_client:
        yield test_client
    app.dependency_overrides.clear()


def test_root_health(client):
    res = client.get("/")
    assert res.status_code == 200
    assert res.json()["status"] == 200


def test_user_vehicle_update_and_predict_api(client):
    """차량 스펙/전비 동적 변경 및 POST /api/predict 엔드포인트 검증"""
    # 1. 기본 차량 정보 조회
    u_res = client.get("/api/user")
    assert u_res.status_code == 200
    assert u_res.json()["data"]["vehicle_model"] == "IONIQ 5 Long Range"

    # 2. 유저 차량 정보 변경 (EV6 롱레인지)
    put_res = client.put(
        "/api/user",
        json={
            "name": "테스트 드라이버",
            "vehicle_model": "Kia EV6 Long Range",
            "battery_capacity": 77.4,
            "curb_weight": 2030,
            "avg_efficiency": 4.8,
        },
    )
    assert put_res.status_code == 200
    assert put_res.json()["data"]["vehicle_model"] == "Kia EV6 Long Range"
    assert put_res.json()["data"]["avg_efficiency"] == 4.8

    # 3. 거리/고도/기온 지정 예측 호출
    res = client.post(
        "/api/predict",
        json={
            "destination_name": "부산 해운대",
            "distance_km": 120.0,
            "elevation_gain_m": 400.0,
            "expected_temp_celsius": -10.0,
            "departure_time": "내일 오전 09:00",
            "passengers": 3,
            "current_soc_percent": 75.0,
        },
    )
    assert res.status_code == 200
    body = res.json()
    assert body["status"] == 200
    data = body["data"]
    assert data["vehicle"]["avg_efficiency_km_kwh"] == 4.8
    assert data["navigation"]["distance_km"] == 120.0
    assert data["weather"]["temperature"] == -10.0

    recs = data["recommendations"]
    assert "SAFE" in recs and "NORMAL" in recs and "MAX_USE" in recs
    assert recs["MAX_USE"]["reserve_soc_percent"] < recs["NORMAL"]["reserve_soc_percent"] < recs["SAFE"]["reserve_soc_percent"]


def test_places_and_chargers_endpoints(client):
    """신규 장소 검색(GET /api/places/search) 및 충전소 탐색(GET /api/chargers/nearby) 엔드포인트 검증"""
    mock_places = {
        "places": [
            {"name": "강남역 2호선", "address": "서울 강남구", "category": "지하철역", "lat": 37.4981, "lng": 127.0280}
        ],
        "is_fallback": False,
        "source": "Kakao Local Keyword API",
    }
    mock_chargers = {
        "chargers": [
            {"name": "감자원종장 충전소", "address": "강원 강릉시", "distance_km": 3.1, "lat": 37.6105, "lng": 128.7725}
        ],
        "is_fallback": False,
        "source": "Kakao Local EV Charger Search",
    }

    with patch("backend.app.api.routes.search_places", return_value=mock_places):
        res_p = client.get("/api/places/search?q=강남역")
        assert res_p.status_code == 200
        assert res_p.json()["data"]["places"][0]["name"] == "강남역 2호선"

    with patch("backend.app.api.routes.search_nearby_ev_chargers", return_value=mock_chargers):
        res_c = client.get("/api/chargers/nearby?lat=37.6242&lng=128.7420")
        assert res_c.status_code == 200
        assert res_c.json()["data"]["chargers"][0]["distance_km"] == 3.1


def test_predict_treats_zero_coords_as_unset(client):
    """Swagger/클라이언트가 dest_lat=0, dest_lng=0을 전송해도 (0,0)으로 길찾기하지 않고 카카오 검색으로 좌표 자동 매핑"""
    mock_places = {
        "places": [
            {"name": "강남역 2호선", "address": "서울 강남구", "category": "지하철역", "lat": 37.4981, "lng": 127.0280}
        ],
        "is_fallback": False,
    }
    mock_weather = {"temperature": 6.0, "is_fallback": False, "source": "KMA", "description": "ok"}
    mock_nav = {"distance_km": 190.6, "elevation_gain_m": 419.0, "estimated_duration_min": 203, "is_fallback": False}

    with (
        patch("backend.app.api.routes.search_places", return_value=mock_places) as m_places,
        patch("backend.app.api.routes.fetch_forecast_temperature", return_value=mock_weather),
        patch("backend.app.api.routes.fetch_route_topology", return_value=mock_nav) as m_nav,
    ):
        res = client.post(
            "/api/predict",
            json={
                "destination_name": "강남역",
                "origin_lat": 37.6242,
                "origin_lng": 128.7420,
                "dest_lat": 0,
                "dest_lng": 0,
            },
        )
        assert res.status_code == 200
        data = res.json()["data"]
        m_places.assert_called_once()
        m_nav.assert_called_once()
        assert data["destination"]["name"] == "강남역 2호선"
        assert data["destination"]["dest_lat"] == 37.4981
        assert data["destination"]["dest_lng"] == 127.0280
        assert data["navigation"]["distance_km"] == 190.6
        assert data["is_fallback"] is False


def test_session_lifecycle_and_soft_lockout(client):
    """
    세션 생성 -> 조회 -> 틱 진행 -> 1시간/30분 전 알림 -> 사용 연장 -> 락아웃 및 무시동 히터 가동
    """
    # 1. 세션 생성 (균형 모드 목표 32.3%)
    create_res = client.post(
        "/api/sessions",
        json={
            "destination_name": "우리 집",
            "distance_km": 86.0,
            "elevation_gain_m": 640.0,
            "expected_temp": -5.0,
            "departure_time": "내일 오전 10:00",
            "passengers": 2,
            "margin_mode": "NORMAL",
            "target_soc": 32.3,
            "current_soc": 61.0,
            "current_v2l_load_kw": 1.2,
        },
    )
    assert create_res.status_code == 200
    session_id = create_res.json()["data"]["session_id"]

    # GET /api/sessions/{id} 조회 확인
    get_res = client.get(f"/api/sessions/{session_id}")
    assert get_res.status_code == 200
    assert get_res.json()["data"]["v2l_status"] == "ACTIVE"

    # 존재하지 않는 세션 404 확인
    assert client.get("/api/sessions/99999").status_code == 404

    # 2. 1시간 전 알림 도달 (34.0%로 감소 -> 남은 마진 1.7%p)
    tick1 = client.post(f"/api/sessions/{session_id}/tick", json={"soc_decrement": 27.0})
    assert tick1.json()["data"]["v2l_status"] == "WARN_60M"

    # 3. 30분 전 긴급 알림 도달 (33.0%로 감소 -> 남은 마진 0.7%p)
    tick2 = client.post(f"/api/sessions/{session_id}/tick", json={"soc_decrement": 1.0})
    assert tick2.json()["data"]["v2l_status"] == "WARN_30M"

    # 4. 사용 연장 (비상 충전소로 물리 엔진 기반 동적 재계산)
    extend_res = client.post(
        f"/api/sessions/{session_id}/extend",
        json={
            "nearest_charger_name": "대관령 급속충전소",
            "distance_km": 12.0,
            "elevation_gain_m": 80.0,
        },
    )
    assert extend_res.status_code == 200
    extend_data = extend_res.json()["data"]
    assert extend_data["new_target_soc"] < 32.3
    assert extend_data["additional_hours"] > 0
    assert extend_data["v2l_status"] == "ACTIVE"

    # 5. 최종 락아웃 (새로운 목표치 15.0% 도달 -> 33.0 - 18.0 = 15.0)
    tick3 = client.post(f"/api/sessions/{session_id}/tick", json={"soc_decrement": 18.0})
    final_data = tick3.json()["data"]
    assert final_data["v2l_status"] == "LOCKED_OUT"
    assert final_data["relay_connected"] is False
    assert final_data["utility_mode_active"] is True

    # 6. 수동/강제 Lockout 엔드포인트 확인
    manual_res = client.post(f"/api/sessions/{session_id}/lockout")
    assert manual_res.status_code == 200
    assert manual_res.json()["data"]["cabin_target_temp_celsius"] == 21.0

    # 7. 세션 이벤트 감사 로그(SESSION_STARTED -> SOC_UPDATED -> SESSION_EXTENDED -> LOCKED_OUT) 확인
    events_res = client.get(f"/api/sessions/{session_id}/events")
    assert events_res.status_code == 200
    event_types = [ev["event_type"] for ev in events_res.json()["data"]["events"]]
    assert event_types[0] == "SESSION_STARTED"
    assert "SOC_UPDATED" in event_types
    assert "SESSION_EXTENDED" in event_types
    assert "LOCKED_OUT" in event_types


def test_favorites_crud_and_predict_home_lookup(client):
    """즐겨찾기 CRUD(/api/favorites) 및 '우리 집' 예측 시 DB 좌표 자동 조회 검증"""
    list_res = client.get("/api/favorites")
    assert list_res.status_code == 200
    assert list_res.json()["data"]["favorites"] == []

    # 1. '우리 집' 등록
    create_res = client.post(
        "/api/favorites",
        json={
            "name": "우리 집",
            "address": "경기 성남시 분당구 판교역로 160",
            "latitude": 37.3947,
            "longitude": 127.1112,
        },
    )
    assert create_res.status_code == 200
    fav_id = create_res.json()["data"]["id"]

    # 2. 동일 이름 재등록 시 중복 생성 대신 좌표 갱신(Upsert)
    upsert_res = client.post(
        "/api/favorites",
        json={
            "name": "우리 집",
            "address": "서울 강남구 학동로 343",
            "latitude": 37.5172,
            "longitude": 127.0473,
        },
    )
    assert upsert_res.status_code == 200
    assert upsert_res.json()["data"]["id"] == fav_id
    assert len(client.get("/api/favorites").json()["data"]["favorites"]) == 1

    # 3. '우리 집'으로 /api/predict 호출 시 등록된 DB 좌표가 반영되는지 확인
    mock_weather = {"temperature": 2.0, "is_fallback": False, "source": "KMA", "description": "ok"}
    mock_nav = {"distance_km": 175.0, "elevation_gain_m": 320.0, "estimated_duration_min": 180, "is_fallback": False}

    with (
        patch("backend.app.api.routes.fetch_forecast_temperature", return_value=mock_weather),
        patch("backend.app.api.routes.fetch_route_topology", return_value=mock_nav) as m_nav,
    ):
        pred_res = client.post(
            "/api/predict",
            json={
                "destination_name": "우리 집",
                "origin_lat": 37.6242,
                "origin_lng": 128.7420,
            },
        )
        assert pred_res.status_code == 200
        dest = pred_res.json()["data"]["destination"]
        assert dest["dest_lat"] == 37.5172
        assert dest["dest_lng"] == 127.0473
        assert m_nav.call_args.kwargs["dest_lat"] == 37.5172
        assert m_nav.call_args.kwargs["dest_lng"] == 127.0473

    # 4. 삭제 및 미존재 삭제 404 검증
    del_res = client.delete(f"/api/favorites/{fav_id}")
    assert del_res.status_code == 200
    assert client.get("/api/favorites").json()["data"]["favorites"] == []
    assert client.delete(f"/api/favorites/{fav_id}").status_code == 404


def test_realtime_soc_sync_sessions_list_and_lockout_guard(client):
    """실시간 절대 SoC 동기화(PATCH /soc), 세션 목록 조회, 락아웃 후 상태 보호 검증"""
    create_res = client.post(
        "/api/sessions",
        json={
            "destination_name": "강남역 2호선",
            "distance_km": 86.0,
            "elevation_gain_m": 640.0,
            "expected_temp": -5.0,
            "departure_time": "내일 오전 10:00",
            "passengers": 2,
            "margin_mode": "NORMAL",
            "target_soc": 32.3,
            "current_soc": 61.0,
            "current_v2l_load_kw": 1.2,
        },
    )
    assert create_res.status_code == 200
    session_id = create_res.json()["data"]["session_id"]

    # 세션 목록 조회
    list_res = client.get("/api/sessions")
    assert list_res.status_code == 200
    assert len(list_res.json()["data"]["sessions"]) == 1
    assert list_res.json()["data"]["sessions"][0]["session_id"] == session_id

    # 절대 SoC 동기화 -> WARN_60M -> WARN_30M
    sync_60 = client.patch(f"/api/sessions/{session_id}/soc", json={"current_soc": 34.0, "load_kw": 1.2})
    assert sync_60.status_code == 200
    assert sync_60.json()["data"]["v2l_status"] == "WARN_60M"
    assert sync_60.json()["data"]["warning_level"] == 60

    sync_30 = client.patch(f"/api/sessions/{session_id}/soc", json={"current_soc": 33.0})
    assert sync_30.status_code == 200
    assert sync_30.json()["data"]["v2l_status"] == "WARN_30M"
    assert sync_30.json()["data"]["warning_level"] == 30

    # 수동 락아웃 실행 (SoC가 target보다 큰 33.0% 상태에서도 LOCKED_OUT)
    lock_res = client.post(f"/api/sessions/{session_id}/lockout")
    assert lock_res.status_code == 200
    assert lock_res.json()["data"]["v2l_status"] == "LOCKED_OUT"
    assert lock_res.json()["data"]["return_distance_guaranteed_km"] > 0

    # 락아웃 이후 높은 SoC 동기화나 tick(0%)이 들어와도 ACTIVE로 풀리지 않고 LOCKED_OUT 유지
    guard_sync = client.patch(f"/api/sessions/{session_id}/soc", json={"current_soc": 50.0})
    assert guard_sync.status_code == 200
    assert guard_sync.json()["data"]["v2l_status"] == "LOCKED_OUT"
    assert guard_sync.json()["data"]["relay_connected"] is False
    assert guard_sync.json()["data"]["utility_mode_active"] is True

    # 존재하지 않는 세션 404 검증
    assert client.patch("/api/sessions/99999/soc", json={"current_soc": 50.0}).status_code == 404
    assert client.get("/api/sessions/99999/events").status_code == 404

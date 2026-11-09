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

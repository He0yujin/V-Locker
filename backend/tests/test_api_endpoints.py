from fastapi.testclient import TestClient
from backend.app.main import app

client = TestClient(app)


def test_predict_api():
    """POST /api/predict 엔드포인트 검증"""
    res = client.post(
        "/api/predict",
        json={
            "destination_name": "우리 집 (서울 강남)",
            "distance_km": 86.0,
            "elevation_gain_m": 640.0,
            "departure_time": "내일 오전 10:00",
            "passengers": 2,
            "current_soc_percent": 61.0,
        },
    )
    assert res.status_code == 200
    body = res.json()
    assert body["status"] == 200
    data = body["data"]
    assert "recommendations" in data
    assert data["recommendations"]["NORMAL"]["reserve_soc_percent"] == 28.0
    assert data["recommendations"]["MAX"]["reserve_soc_percent"] == 34.0
    assert data["recommendations"]["SAFE"]["reserve_soc_percent"] == 24.0


def test_session_lifecycle_and_soft_lockout():
    """
    세션 생성 -> 틱 진행 (61% -> 29.5% -> 28.0%) -> 30분 전 알림 -> 사용 연장 -> 락아웃 및 무시동 히터 가동
    엔드투엔드 시나리오 검증
    """
    # 1. 세션 생성
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
            "target_soc": 28.0,
            "current_soc": 61.0,
            "current_v2l_load_kw": 1.2,
        },
    )
    assert create_res.status_code == 200
    session_id = create_res.json()["data"]["session_id"]

    # 2. 1시간 전 알림 도달 (30.0%로 감소)
    tick1 = client.post(f"/api/sessions/{session_id}/tick", json={"soc_decrement": 31.0})
    assert tick1.json()["data"]["v2l_status"] == "WARN_60M"

    # 3. 30분 전 긴급 알림 도달 (28.8%로 감소)
    tick2 = client.post(f"/api/sessions/{session_id}/tick", json={"soc_decrement": 1.2})
    assert tick2.json()["data"]["v2l_status"] == "WARN_30M"

    # 4. 사용 연장 (비상 충전소로 스마트 재계산)
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
    assert extend_data["new_target_soc"] < 28.0  # 목표치가 16% 수준으로 완화
    assert extend_data["additional_hours"] > 0
    assert extend_data["v2l_status"] == "ACTIVE" # 경고 해제

    # 5. 최종 락아웃 (새로운 목표치 이하로 감소)
    tick3 = client.post(f"/api/sessions/{session_id}/tick", json={"soc_decrement": 15.0})
    final_data = tick3.json()["data"]
    assert final_data["v2l_status"] == "LOCKED_OUT"
    assert final_data["relay_connected"] is False       # V2L 릴레이 물리 차단 완료
    assert final_data["utility_mode_active"] is True    # 무시동 히터 자동 가동 완료!



import pytest
from fastapi.testclient import TestClient
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker
from sqlalchemy.pool import StaticPool

from backend.app.main import app
from backend.app.db.database import Base, get_db


@pytest.fixture
def client():
    # 테스트 전용 메모리 DB
    engine = create_engine(
        "sqlite://",
        connect_args={"check_same_thread": False},
        poolclass=StaticPool,
    )

    Base.metadata.create_all(bind=engine)

    TestingSession = sessionmaker(
        bind=engine,
        autoflush=False,
        autocommit=False,
    )

    def override_get_db():
        db = TestingSession()
        try:
            yield db
        finally:
            db.close()

    app.dependency_overrides[get_db] = override_get_db

    try:
        with TestClient(app) as test_client:
            yield test_client
    finally:
        app.dependency_overrides.pop(get_db, None)
        engine.dispose()


def test_favorite_crud(client):
    # 1. 즐겨찾기 등록
    response = client.post(
        "/api/favorites",
        json={
            "name": "우리 집",
            "address": "서울특별시 강남구",
            "latitude": 37.4979,
            "longitude": 127.0276,
        },
    )

    assert response.status_code == 201
    created = response.json()["data"]
    favorite_id = created["id"]
    assert created["name"] == "우리 집"

    # 2. 즐겨찾기 목록 조회
    response = client.get("/api/favorites")

    assert response.status_code == 200
    favorites = response.json()["data"]
    assert len(favorites) == 1
    assert favorites[0]["id"] == favorite_id

    # 3. 즐겨찾기 수정
    response = client.put(
        f"/api/favorites/{favorite_id}",
        json={"name": "우리 집 (수정)"},
    )

    assert response.status_code == 200
    assert response.json()["data"]["name"] == "우리 집 (수정)"

    # 수정한 값이 DB에 유지되는지 확인
    response = client.get("/api/favorites")
    assert response.json()["data"][0]["name"] == "우리 집 (수정)"

    # 4. 즐겨찾기 삭제
    response = client.delete(f"/api/favorites/{favorite_id}")

    assert response.status_code == 200
    assert response.json()["data"]["deleted"] is True

    # 5. 삭제 후 목록 확인
    response = client.get("/api/favorites")

    assert response.status_code == 200
    assert response.json()["data"] == []

    # 6. 삭제한 즐겨찾기를 다시 삭제하면 404
    response = client.delete(f"/api/favorites/{favorite_id}")

    assert response.status_code == 404


def test_favorite_validation(client):
    # 위도 범위 초과
    response = client.post(
        "/api/favorites",
        json={
            "name": "잘못된 좌표",
            "latitude": 100.0,
            "longitude": 127.0,
        },
    )

    assert response.status_code == 422

    # 빈 이름은 허용하지 않음
    response = client.post(
        "/api/favorites",
        json={
            "name": "",
            "latitude": 37.5,
            "longitude": 127.0,
        },
    )

    assert response.status_code == 422

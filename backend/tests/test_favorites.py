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
    # 1. 즐겨찾기 등록 (Upsert 200 규격)
    response = client.post(
        "/api/favorites",
        json={
            "name": "우리 집",
            "address": "서울특별시 강남구",
            "latitude": 37.4979,
            "longitude": 127.0276,
        },
    )

    assert response.status_code == 200
    created = response.json()["data"]
    favorite_id = created["id"]
    assert created["name"] == "우리 집"
    assert created["user_id"] >= 1

    # 2. 즐겨찾기 목록 조회 (android-wide FavoritesListData: data.favorites 규격)
    response = client.get("/api/favorites")

    assert response.status_code == 200
    favorites = response.json()["data"]["favorites"]
    assert len(favorites) == 1
    assert favorites[0]["id"] == favorite_id
    assert favorites[0]["user_id"] >= 1

    # 3. 즐겨찾기 수정 (PUT /api/favorites/{id})
    response = client.put(
        f"/api/favorites/{favorite_id}",
        json={"name": "우리 집 (수정)"},
    )

    assert response.status_code == 200
    assert response.json()["data"]["name"] == "우리 집 (수정)"

    # 필수 필드 null 수정 차단
    null_update = client.put(
        f"/api/favorites/{favorite_id}",
        json={"name": None},
    )
    assert null_update.status_code == 422

    # 수정한 값이 DB에 유지되는지 확인
    response = client.get("/api/favorites")
    assert response.json()["data"]["favorites"][0]["name"] == "우리 집 (수정)"

    # 4. 즐겨찾기 삭제 (android-wide Map<String, Int>: {"deleted_id": id} 규격)
    response = client.delete(f"/api/favorites/{favorite_id}")

    assert response.status_code == 200
    assert response.json()["data"]["deleted_id"] == favorite_id

    # 5. 삭제 후 목록 확인
    response = client.get("/api/favorites")

    assert response.status_code == 200
    assert response.json()["data"]["favorites"] == []

    # 6. 삭제한 즐겨찾기를 다시 삭제/수정하면 404
    assert client.delete(f"/api/favorites/{favorite_id}").status_code == 404
    assert client.put(f"/api/favorites/{favorite_id}", json={"name": "없음"}).status_code == 404


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

    # 공백만 있는 이름도 허용하지 않음
    response = client.post(
        "/api/favorites",
        json={
            "name": "   ",
            "latitude": 37.5,
            "longitude": 127.0,
        },
    )

    assert response.status_code == 422


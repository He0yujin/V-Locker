
import pytest

from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker
from sqlalchemy.pool import StaticPool

from backend.app.db.database import Base
from backend.app.db.models import User, FavoriteDestination


def test_transaction_rollback():
    """DB 저장 도중 오류 발생 시 변경사항이 취소되는지 확인"""

    # 실제 PostgreSQL이 아닌 테스트 전용 SQLite DB
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

    try:
        with TestingSession() as db:

            # 1. 기본 사용자 생성 후 정상 저장
            user = User(
                name="롤백 테스트 사용자",
                vehicle_model="IONIQ 5",
                battery_capacity=77.4,
                curb_weight=2010,
                avg_efficiency=5.4,
            )

            db.add(user)
            db.commit()
            db.refresh(user)

            user_id = user.id

            # 2. 즐겨찾기 저장 중 의도적으로 오류 발생
            with pytest.raises(RuntimeError):
                try:
                    favorite = FavoriteDestination(
                        user_id=user_id,
                        name="롤백 테스트 목적지",
                        address="서울특별시 강남구",
                        latitude=37.4979,
                        longitude=127.0276,
                    )

                    db.add(favorite)
                    db.flush()

                    # 저장 도중 오류를 강제로 재현
                    raise RuntimeError("DB 저장 실패 시뮬레이션")

                except RuntimeError:
                    db.rollback()
                    raise

            # 3. 롤백 후 즐겨찾기가 남아 있는지 확인
            favorites = db.query(FavoriteDestination).all()

            assert len(favorites) == 0

            # 4. 이전에 정상 저장된 사용자는 유지되어야 함
            saved_user = db.get(User, user_id)

            assert saved_user is not None
            assert saved_user.name == "롤백 테스트 사용자"

    finally:
        engine.dispose()

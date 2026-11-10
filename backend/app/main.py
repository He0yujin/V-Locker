from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from backend.app.db.database import Base, engine, SessionLocal
from backend.app.db.models import User
from backend.app.db import session_events
from backend.app.api.routes import router

# DB 테이블 생성
Base.metadata.create_all(bind=engine)

# 초기 기본 유저 생성
def init_db():
    db = SessionLocal()
    try:
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
    finally:
        db.close()

init_db()

app = FastAPI(
    title="V2L Safe Locker API",
    description="현대자동차 Pleos App Market용 AI 기반 V2L 전력 자동 제어 백엔드 엔진",
    version="1.0.0",
)

# CORS 미들웨어 설정 (프론트엔드 통신 허용)
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(router)


@app.get("/")
def root():
    return {
        "status": 200,
        "message": "V2L Safe Locker Engine is running online.",
        "platform": "Hyundai Pleos App Market (SDV)",
    }


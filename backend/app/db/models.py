from datetime import datetime
from sqlalchemy import Column, Integer, String, Float, Boolean, DateTime, ForeignKey, CheckConstraint
from sqlalchemy.orm import relationship

from backend.app.db.database import Base


class User(Base):
    """
    차량 및 운전자 프로필 모델
    - 차량 모델, 배터리 총 용량, 공차 중량, 평소 누적 전비 저장
    """
    __tablename__ = "users"
    
    __table_args__ = (
        CheckConstraint("battery_capacity > 0", name="ck_user_battery_capacity"),
        CheckConstraint("curb_weight > 0", name="ck_user_curb_weight"),
        CheckConstraint("avg_efficiency > 0", name="ck_user_avg_efficiency"),
    )


    id = Column(Integer, primary_key=True, index=True, autoincrement=True)
    name = Column(String(50), default="캠퍼 드라이버")
    vehicle_model = Column(String(50), default="IONIQ 5 Long Range")
    battery_capacity = Column(Float, default=77.4)   # kWh
    curb_weight = Column(Integer, default=2010)       # kg
    avg_efficiency = Column(Float, default=5.4)       # km/kWh
    created_at = Column(DateTime, default=datetime.utcnow)

    sessions = relationship("V2LSession", back_populates="user")


class V2LSession(Base):
    """
    V2L 세이프 락커 활성화 세션 모델
    - 목적지, 출발시각, 예상 기온, 상승 고도, 탑승자 수, 안전 마진 모드
    - 실시간 배터리 SoC, 목표 차단 배터리 SoC, V2L 상태, 유틸리티 모드(무시동 히터) 상태
    """
    __tablename__ = "v2l_sessions"
    
    __table_args__ = (
        CheckConstraint("current_soc BETWEEN 0 AND 100", name="ck_session_current_soc"),
        CheckConstraint("target_soc BETWEEN 10 AND 95", name="ck_session_target_soc"),
        CheckConstraint("distance_km > 0", name="ck_session_distance"),
        CheckConstraint("elevation_gain_m >= 0", name="ck_session_elevation"),
        CheckConstraint(
            "margin_mode IN ('SAFE', 'NORMAL', 'MAX_USE')",
            name="ck_session_margin_mode"
        ),
    )


    id = Column(Integer, primary_key=True, index=True, autoincrement=True)
    user_id = Column(Integer, ForeignKey("users.id"), default=1)

    # 귀가 경로 및 환경 변수
    destination_name = Column(String(100), default="우리 집 (서울 강남)")
    distance_km = Column(Float, default=86.0)
    elevation_gain_m = Column(Float, default=640.0)
    expected_temp = Column(Float, default=-5.0)       # ℃
    departure_time = Column(String(50), default="내일 오전 10:00")
    passengers = Column(Integer, default=2)

    # AI 예측 및 마진 설정
    margin_mode = Column(String(20), default="NORMAL") # SAFE, NORMAL, MAX
    target_soc = Column(Float, default=28.0)           # V2L 차단 목표 배터리 (%)
    current_soc = Column(Float, default=61.0)          # 실시간 배터리 잔량 (%)
    current_v2l_load_kw = Column(Float, default=1.2)   # 실시간 V2L 소비 전력 (kW)

    # 락아웃 및 안전 제어 상태
    # ACTIVE: 정상 V2L 전원 공급 중
    # WARN_60M: 1시간 전 알림 발생
    # WARN_30M: 30분 전 긴급 알림 발생
    # LOCKED_OUT: V2L 하드웨어 릴레이 차단 완료
    v2l_status = Column(String(20), default="ACTIVE")
    relay_connected = Column(Boolean, default=True)      # V2L 하드웨어 릴레이 물리적 연결 여부
    utility_mode_active = Column(Boolean, default=False) # 무시동 히터(유틸리티 모드 21℃) 작동 여부
    is_fallback = Column(Boolean, default=False)         # 통신 장애 Fallback 사용 여부
    extended_count = Column(Integer, default=0)          # 비상 충전소 재계산으로 사용 연장된 횟수

    created_at = Column(DateTime, default=datetime.utcnow)
    updated_at = Column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)

    user = relationship("User", back_populates="sessions")

class FavoriteDestination(Base):
    """사용자별 즐겨찾기 목적지"""
    __tablename__ = "favorite_destinations"

    id = Column(Integer, primary_key=True, autoincrement=True)
    user_id = Column(
        Integer,
        ForeignKey("users.id", ondelete="CASCADE"),
        nullable=False,
        index=True,
    )
    name = Column(String(100), nullable=False)
    address = Column(String(255), nullable=True)
    latitude = Column(Float, nullable=False)
    longitude = Column(Float, nullable=False)
    created_at = Column(DateTime, default=datetime.utcnow, nullable=False)

    user = relationship("User")


class V2LSessionEvent(Base):
    """V2L 세션의 상태 및 설정 변경 이력"""
    __tablename__ = "v2l_session_events"

    id = Column(Integer, primary_key=True, autoincrement=True)
    session_id = Column(
        Integer,
        ForeignKey("v2l_sessions.id", ondelete="CASCADE"),
        nullable=False,
        index=True,
    )
    event_type = Column(String(30), nullable=False)
    previous_soc = Column(Float, nullable=True)
    current_soc = Column(Float, nullable=True)
    previous_target_soc = Column(Float, nullable=True)
    new_target_soc = Column(Float, nullable=True)
    description = Column(String(500), nullable=True)
    created_at = Column(DateTime, default=datetime.utcnow, nullable=False)

    session = relationship("V2LSession")

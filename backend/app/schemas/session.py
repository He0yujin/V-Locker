from typing import Generic, TypeVar, Optional, Any, Dict
from pydantic import BaseModel, Field

T = TypeVar("T")


class APIResponse(BaseModel, Generic[T]):
    """
    통일된 JSON 응답 규격
    {"status": 200, "data": {...}, "message": "..."}
    """
    status: int = 200
    data: Optional[T] = None
    message: str = "성공"


class PredictRequest(BaseModel):
    destination_name: str = Field(default="우리 집 (서울 강남)", description="목적지 명칭")
    distance_km: float = Field(default=86.0, description="귀가 주행 거리 (km)")
    elevation_gain_m: float = Field(default=640.0, description="총 상승 고도 (m)")
    departure_time: str = Field(default="내일 오전 10:00", description="출발 예정 시각")
    passengers: int = Field(default=2, ge=1, le=5, description="탑승 인원 수")
    current_soc_percent: Optional[float] = Field(default=61.0, description="현재 배터리 잔량 (%)")
    expected_temp_celsius: Optional[float] = Field(default=None, description="수동 기온 입력(없을 시 기상청 API 자동 연동)")
    force_fallback: bool = Field(default=False, description="테스트용 통신 오류 시뮬레이션 플래그")


class CreateSessionRequest(BaseModel):
    destination_name: str = "우리 집 (서울 강남)"
    distance_km: float = 86.0
    elevation_gain_m: float = 640.0
    expected_temp: float = -5.0
    departure_time: str = "내일 오전 10:00"
    passengers: int = 2
    margin_mode: str = Field(default="NORMAL", description="SAFE | NORMAL | MAX")
    target_soc: float = Field(default=28.0, description="예약 잔량 (%)")
    current_soc: float = Field(default=61.0, description="현재 배터리 (%)")
    current_v2l_load_kw: float = Field(default=1.2, description="현재 V2L 부하 (kW)")
    is_fallback: bool = False


class TickRequest(BaseModel):
    soc_decrement: float = Field(default=2.0, description="1회 시뮬레이션 시 차감할 배터리 (%)")
    load_kw: Optional[float] = Field(default=None, description="현재 V2L 소비 전력 갱신치 (kW)")


class ExtendRequest(BaseModel):
    nearest_charger_name: str = "대관령 급속충전소"
    distance_km: float = 12.0
    elevation_gain_m: float = 80.0


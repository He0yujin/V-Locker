from typing import Generic, TypeVar, Optional, Literal
from pydantic import BaseModel, ConfigDict, Field

T = TypeVar("T")


class APIResponse(BaseModel, Generic[T]):
    """
    통일된 JSON 응답 규격
    {"status": 200, "data": {...}, "message": "..."}
    """
    status: int = 200
    data: Optional[T] = None
    message: str = "성공"


class UserVehicleUpdateRequest(BaseModel):
    """사용자 차량 정보 및 평소 전비 등록/수정 요청"""
    name: Optional[str] = None
    vehicle_model: str = Field(..., description="차량 모델명 (예: IONIQ 5, EV6, 포터 EV)")
    battery_capacity: float = Field(..., gt=0, description="배터리 총 용량 (kWh)")
    curb_weight: int = Field(..., gt=500, description="차량 공차 중량 (kg)")
    avg_efficiency: float = Field(..., gt=0.5, description="사용자 평소 전비 (km/kWh)")

    model_config = ConfigDict(
        json_schema_extra={
            "example": {
                "name": "캠퍼 드라이버",
                "vehicle_model": "IONIQ 5 Long Range",
                "battery_capacity": 77.4,
                "curb_weight": 2010,
                "avg_efficiency": 5.4,
            }
        }
    )


class PredictRequest(BaseModel):
    destination_name: str = Field(..., description="목적지 명칭 또는 검색어 (예: 강남역, 우리 집)")
    origin_lat: float = Field(default=37.6242, description="출발지(현재 차박지) 위도 (기본: 강릉 안반데기)")
    origin_lng: float = Field(default=128.7420, description="출발지(현재 차박지) 경도")
    dest_lat: Optional[float] = Field(default=None, description="목적지 위도 (null이면 카카오 로컬로 자동 검색)")
    dest_lng: Optional[float] = Field(default=None, description="목적지 경도 (null이면 카카오 로컬로 자동 검색)")
    distance_km: Optional[float] = Field(default=None, gt=0, description="직접 지정 거리 (null이면 카카오 길찾기 자동 조회)")
    elevation_gain_m: Optional[float] = Field(default=None, ge=0, description="직접 지정 상승고도 (null이면 지형 API 자동 조회)")
    departure_time: str = Field(default="내일 오전 10:00", description="출발 예정 시각")
    passengers: int = Field(default=2, ge=1, le=6, description="탑승 인원 수")
    current_soc_percent: float = Field(default=61.0, ge=0, le=100, description="현재 배터리 잔량 (%)")
    expected_temp_celsius: Optional[float] = Field(default=None, description="수동 기온 입력 (null이면 기상청 API 자동 조회)")
    avg_efficiency_km_kwh: Optional[float] = Field(default=None, gt=0.5, description="차량 전비 덮어쓰기 (null이면 유저 DB 값 5.4 사용)")
    battery_capacity_kwh: Optional[float] = Field(default=None, gt=0, description="배터리 용량 덮어쓰기 (null이면 유저 DB 값 77.4 사용)")
    curb_weight_kg: Optional[float] = Field(default=None, gt=500, description="공차중량 덮어쓰기 (null이면 유저 DB 값 2010 사용)")
    v2l_current_load_kw: float = Field(default=1.2, gt=0.1, description="실시간 V2L 소비 전력 (kW)")
    force_fallback: bool = Field(default=False, description="테스트용 통신 오류 시뮬레이션 플래그")

    model_config = ConfigDict(
        json_schema_extra={
            "example": {
                "destination_name": "강남역",
                "origin_lat": 37.6242,
                "origin_lng": 128.7420,
                "dest_lat": None,
                "dest_lng": None,
                "distance_km": None,
                "elevation_gain_m": None,
                "departure_time": "내일 오전 10:00",
                "passengers": 2,
                "current_soc_percent": 61.0,
                "expected_temp_celsius": None,
                "avg_efficiency_km_kwh": None,
                "battery_capacity_kwh": None,
                "curb_weight_kg": None,
                "v2l_current_load_kw": 1.2,
                "force_fallback": False,
            }
        }
    )


class CreateSessionRequest(BaseModel):
    destination_name: str
    distance_km: float = Field(..., gt=0)
    elevation_gain_m: float = Field(..., ge=0)
    expected_temp: float
    departure_time: str
    passengers: int = 2
    margin_mode: Literal["SAFE", "NORMAL", "MAX_USE"] = Field(
        default="NORMAL",
        description="SAFE | NORMAL | MAX_USE"
    )
    target_soc: float = Field(..., ge=10.0, le=95.0, description="예약 잔량 (%)")
    current_soc: float = Field(..., ge=0.0, le=100.0, description="현재 배터리 (%)")
    current_v2l_load_kw: float = Field(default=1.2, gt=0.1, description="현재 V2L 부하 (kW)")
    is_fallback: bool = False

    model_config = ConfigDict(
        json_schema_extra={
            "example": {
                "destination_name": "강남역 2호선",
                "distance_km": 190.6,
                "elevation_gain_m": 419.0,
                "expected_temp": 6.0,
                "departure_time": "내일 오전 10:00",
                "passengers": 2,
                "margin_mode": "NORMAL",
                "target_soc": 60.1,
                "current_soc": 61.0,
                "current_v2l_load_kw": 1.2,
                "is_fallback": False,
            }
        }
    )


class TickRequest(BaseModel):
    soc_decrement: float = Field(default=2.0, ge=0.0, le=100.0, description="1회 시뮬레이션 시 차감할 배터리 (%)")
    load_kw: Optional[float] = Field(default=None, gt=0.1, description="현재 V2L 소비 전력 갱신치 (kW)")

    model_config = ConfigDict(
        json_schema_extra={
            "example": {
                "soc_decrement": 2.0,
                "load_kw": 1.2,
            }
        }
    )


class SocSyncRequest(BaseModel):
    """Pleos EvBattery SDK 실시간 절대 SoC 동기화 요청"""
    current_soc: float = Field(..., ge=0.0, le=100.0, description="차량 BMS에서 수신한 현재 배터리 잔량 (%)")
    load_kw: Optional[float] = Field(default=None, gt=0.1, description="현재 V2L 소비 전력 갱신치 (kW)")

    model_config = ConfigDict(
        json_schema_extra={
            "example": {
                "current_soc": 34.0,
                "load_kw": 1.2,
            }
        }
    )


class FavoriteDestinationCreateRequest(BaseModel):
    """즐겨찾기 목적지(우리 집 등) 등록/갱신 요청"""
    name: str = Field(..., min_length=1, max_length=100, description="즐겨찾기 이름 (예: 우리 집)")
    address: Optional[str] = Field(default=None, max_length=255, description="도로명/지번 주소")
    latitude: float = Field(..., ge=-90.0, le=90.0, description="위도")
    longitude: float = Field(..., ge=-180.0, le=180.0, description="경도")

    model_config = ConfigDict(
        json_schema_extra={
            "example": {
                "name": "우리 집",
                "address": "서울 강남구 강남대로 396",
                "latitude": 37.4979,
                "longitude": 127.0276,
            }
        }
    )


FavoriteCreateRequest = FavoriteDestinationCreateRequest


class FavoriteUpdateRequest(BaseModel):
    """즐겨찾기 목적지 수정 요청"""
    name: Optional[str] = Field(default=None, min_length=1, max_length=100, description="수정할 즐겨찾기 이름")
    address: Optional[str] = Field(default=None, max_length=255, description="수정할 도로명/지번 주소")
    latitude: Optional[float] = Field(default=None, ge=-90.0, le=90.0, description="수정할 위도")
    longitude: Optional[float] = Field(default=None, ge=-180.0, le=180.0, description="수정할 경도")


class ExtendRequest(BaseModel):
    origin_lat: float = Field(default=37.6242, description="현재 위치 위도")
    origin_lng: float = Field(default=128.7420, description="현재 위치 경도")
    nearest_charger_name: Optional[str] = Field(default=None, description="직접 지정 시 충전소 명칭 (null이면 카카오 로컬 자동 탐색)")
    distance_km: Optional[float] = Field(default=None, description="직접 지정 거리 (null이면 자동 탐색)")
    elevation_gain_m: float = Field(default=80.0, description="추정 상승고도")
    dest_lat: Optional[float] = Field(default=None, description="직접 지정 충전소 위도")
    dest_lng: Optional[float] = Field(default=None, description="직접 지정 충전소 경도")

    model_config = ConfigDict(
        json_schema_extra={
            "example": {
                "origin_lat": 37.6242,
                "origin_lng": 128.7420,
                "nearest_charger_name": None,
                "distance_km": None,
                "elevation_gain_m": 80.0,
            }
        }
    )

class FavoriteCreateRequest(BaseModel):
    """즐겨찾기 목적지 등록 요청"""
    name: str = Field(..., min_length=1, max_length=100)
    address: Optional[str] = Field(default=None, max_length=255)
    latitude: float = Field(..., ge=-90, le=90)
    longitude: float = Field(..., ge=-180, le=180)


class FavoriteUpdateRequest(BaseModel):
    """즐겨찾기 목적지 수정 요청"""
    name: Optional[str] = Field(default=None, min_length=1, max_length=100)
    address: Optional[str] = Field(default=None, max_length=255)
    latitude: Optional[float] = Field(default=None, ge=-90, le=90)
    longitude: Optional[float] = Field(default=None, ge=-180, le=180)

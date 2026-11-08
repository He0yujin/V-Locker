# [V2L 세이프 락커] API 연동 및 엔드포인트 명세서 (API Specification)

* **버전:** v1.0
* **작성일자:** 2026-11-08
* **적용 환경:** 현대자동차 Pleos App Market (SDV) & V2L Safe Locker 온보드 백엔드

---

## 1. 연동 API 전체 개요 (Architecture Overview)

V2L 세이프 락커는 **외부 클라우드 API (기상/지형)**, **차량 내부 SDV 제어 API (BMS/공조/릴레이)**, 그리고 **프론트엔드-백엔드 간 RESTful API**의 3개 레이어로 구성됩니다.

```
┌────────────────────────────────────────────────────────────────────────┐
│                        1. 외부 클라우드 API                             │
│  - [외부] 기상청 단기예보 API : 익일 아침 최저기온 및 기상 상태 조회          │
│  - [외부] 내비게이션 토폴로지 API : 목적지 주행거리, 획득고도, 오르막 비율 조회│
└───────────────────────────────────┬────────────────────────────────────┘
                                    │ (Timeout 3.0s / Fallback 보장)
┌───────────────────────────────────▼────────────────────────────────────┐
│                  2. V2L Safe Locker Backend REST API                   │
│  - POST /api/predict : 다중 데이터 융합 배터리 소모량 & 3단계 모드 계산      │
│  - POST /api/sessions : 모니터링 세션 등록 및 활성화                     │
│  - GET  /api/sessions/{id} : 실시간 SoC, kW, 잔여 시간, 경고 상태 조회    │
│  - POST /api/sessions/{id}/tick : 시뮬레이션 가속 (배터리/시간 소모)     │
│  - POST /api/sessions/{id}/extend : 비상 충전소 기반 스마트 사용 연장     │
│  - POST /api/sessions/{id}/lockout : V2L 차단 & 무시동 히터 페일세이프     │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │ (차량 하드웨어 제어 명령)
┌───────────────────────────────────▼────────────────────────────────────┐
│                    3. 현대차 SDV (Pleos In-Car) API                    │
│  - Pleos BMS API : 실시간 배터리 SoC(%), 팩 온도(℃), 평균 전비 조회       │
│  - Pleos V2L Hardware Relay API : 외부 220V V2L 물리 전원 ON/OFF 차단   │
│  - Pleos HVAC Utility API : 동사 방지 무시동 히터(유틸리티 모드 21℃) 가동  │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 2. 외부 및 차량 내부 연동 API 상세

### 2.1 [외부] 기상청 단기예보 API (공공데이터포털)
* **제공처:** 기상청 (Korea Meteorological Administration)
* **목적:** 익일 출발 예정 시각(예: 오전 07:00 ~ 10:00)의 야외 최저 기온 및 한파 특보 데이터 수집.
* **프로토콜:** HTTP GET (REST JSON)
* **호출 주기:** 세션 생성 시 1회 및 15분 간격 갱신 (캐싱 적용)
* **타임아웃 & Fallback:** **3.0초** 초과 시 기본 안전값인 **-5.0℃** 자동 적용.

### 2.2 [외부] 내비게이션 토폴로지 API (카카오/티맵/Pleos Map SDK)
* **제공처:** 내비게이션 라우팅 엔진
* **목적:** 출발지(캠핑장)부터 목적지(집/충전소)까지의 **단순 평면 거리가 아닌 '순수 획득 고도(Elevation Gain, $\Delta h$)'와 경사도 프로파일** 수집.
* **파라미터:** `origin` (출발지 위경도), `destination` (목적지 위경도)
* **응답 데이터:**
  * `distance_km`: 총 주행거리 (km)
  * `elevation_gain_m`: 총 획득 고도 (오르막 상승 고도 합계, m)
  * `estimated_duration_min`: 예상 소요 시간 (분)
* **타임아웃 & Fallback:** **3.0초** 초과 시 기본 안전 경로값(**거리 86km, 고도 640m**) 자동 적용.

### 2.3 [SDV] 현대자동차 Pleos In-Car 하드웨어 제어 API
* **제공처:** 현대차 SDV 통합 OS (Pleos Vehicle HAL / SDK)
* **목적:** 스마트폰 앱으로는 불가능한 차량 하드웨어 직접 제어.
  1. **`pleos.bms.get_state()`**: 현재 배터리 SoC (0~100%), 전압, 배터리 모듈 온도, 누적 평균 전비(km/kWh) 수신.
  2. **`pleos.v2l.set_relay(enabled: bool)`**: V2L 메인 릴레이 물리적 개폐 명령 (목표치 도달 시 전원 차단).
  3. **`pleos.climate.set_utility_mode(temp: 21, fan: 2)`**: V2L 차단 즉시 동사 방지용 무시동 히터(유틸리티 모드) 최소 난방 자동 가동.

---

## 3. 백엔드 RESTful API 상세 명세

모든 백엔드 응답은 통일된 JSON 구조를 준수합니다:
```json
{
  "status": 200,
  "data": { ... },
  "message": "성공 안내 메시지"
}
```

---

### 3.1 배터리 소모량 예측 및 3단계 모드 산출
* **엔드포인트:** `POST /api/predict`
* **설명:** 목적지, 출발시각, 탑승객 수, 현재 SoC를 기반으로 기상/지형/전비 데이터를 융합 분석하여 3가지 모드(안심/균형/최대사용)의 예약 잔량(%)과 가용 시간(Time)을 반환합니다.
* **Request Body:**
```json
{
  "destination_name": "우리 집 (서울 강남)",
  "distance_km": 86.0,
  "elevation_gain_m": 640.0,
  "departure_time": "내일 오전 10:00",
  "passengers": 2,
  "current_soc_percent": 61.0,
  "expected_temp_celsius": null,
  "force_fallback": false
}
```
* **Response Body (200 OK):**
```json
{
  "status": 200,
  "data": {
    "weather": {
      "temperature": -5.0,
      "is_fallback": false,
      "description": "기상청 단기예보 연동 성공 (출발 시각 내일 오전 10:00 기준)"
    },
    "navigation": {
      "distance_km": 86.0,
      "elevation_gain_m": 640.0,
      "estimated_duration_min": 85,
      "is_fallback": false
    },
    "breakdown": {
      "base_driving_kwh": 15.93,
      "elevation_kwh": 4.29,
      "temperature_efficiency_factor": 0.812,
      "efficiency_loss_percent": 18.8,
      "cold_start_hvac_spike_kwh": 3.2,
      "pure_return_kwh": 28.1
    },
    "recommendations": {
      "MAX": {
        "mode": "MAX",
        "mode_name_kr": "안심",
        "reserve_soc_percent": 34.0,
        "required_kwh": 37.39,
        "v2l_usable_kwh": 20.9,
        "v2l_usable_hours": 17.4,
        "description": "여유를 넉넉히 확보 · 예측 오차와 추가 난방까지 완벽 방어"
      },
      "NORMAL": {
        "mode": "NORMAL",
        "mode_name_kr": "균형",
        "reserve_soc_percent": 28.0,
        "required_kwh": 33.52,
        "v2l_usable_kwh": 25.54,
        "v2l_usable_hours": 21.3,
        "description": "주행 데이터 기준 설정 · 일반적인 귀가 환경에 최적화"
      },
      "SAFE": {
        "mode": "SAFE",
        "mode_name_kr": "최대 사용",
        "reserve_soc_percent": 24.0,
        "required_kwh": 30.42,
        "v2l_usable_kwh": 28.64,
        "v2l_usable_hours": 23.9,
        "description": "V2L 사용을 우선 · 귀가 필수치만 남기고 전력 최대 활용"
      }
    }
  },
  "message": "AI 귀가 전력 분석 및 3단계 안전 마진 추천이 완료되었습니다."
}
```

---

### 3.2 V2L 안전 모니터링 세션 생성
* **엔드포인트:** `POST /api/sessions`
* **설명:** 사용자가 선택한 마진 모드(예: 균형 28%)를 기반으로 실시간 V2L 안전 모니터링 세션을 시작합니다.
* **Request Body:**
```json
{
  "destination_name": "우리 집 (서울 강남)",
  "distance_km": 86.0,
  "elevation_gain_m": 640.0,
  "expected_temp": -5.0,
  "departure_time": "내일 오전 10:00",
  "passengers": 2,
  "margin_mode": "NORMAL",
  "target_soc": 28.0,
  "current_soc": 61.0,
  "current_v2l_load_kw": 1.2,
  "is_fallback": false
}
```
* **Response Body (200 OK):**
```json
{
  "status": 200,
  "data": {
    "session_id": 1,
    "destination_name": "우리 집 (서울 강남)",
    "current_soc": 61.0,
    "target_soc": 28.0,
    "remaining_margin_pct": 33.0,
    "current_v2l_load_kw": 1.2,
    "estimated_hours_left": 21.3,
    "v2l_status": "ACTIVE",
    "relay_connected": true,
    "utility_mode_active": false
  },
  "message": "V2L 안전 모니터링 세션이 활성화되었습니다."
}
```

---

### 3.3 실시간 세션 상태 조회
* **엔드포인트:** `GET /api/sessions/{session_id}`
* **설명:** 인포테인먼트 대시보드에서 2초 간격(또는 웹소켓/폴링)으로 현재 SoC, 잔여 시간, 차단까지 남은 %p, 경고 알림 상태를 조회합니다.
* **Response Body (200 OK):**
```json
{
  "status": 200,
  "data": {
    "session_id": 1,
    "current_soc": 61.0,
    "target_soc": 28.0,
    "remaining_margin_pct": 33.0,
    "current_v2l_load_kw": 1.2,
    "estimated_hours_left": 21.3,
    "v2l_status": "ACTIVE",
    "warning_level": null,
    "relay_connected": true,
    "utility_mode_active": false,
    "destination_summary": {
      "destination": "우리 집 (서울 강남)",
      "departure": "내일 오전 10:00",
      "distance_km": 86.0,
      "elevation_gain_m": 640.0,
      "temp_celsius": -5.0,
      "margin_mode": "NORMAL"
    }
  },
  "message": "세션 상태 조회 성공"
}
```

---

### 3.4 시뮬레이션 가속 틱 (Simulation Tick)
* **엔드포인트:** `POST /api/sessions/{session_id}/tick`
* **설명:** 심사위원 시연 및 데모 시 배터리를 인위적으로 감소시켜 **[정상 사용] -> [1시간 전 알림] -> [30분 전 알림] -> [락아웃 & 무시동 히터 가동]** 전 과정을 수초 내에 체감할 수 있도록 지원합니다.
* **Request Body:**
```json
{
  "soc_decrement": 2.0,
  "load_kw": 1.5
}
```
* **Response Body (200 OK):**
```json
{
  "status": 200,
  "data": {
    "session_id": 1,
    "current_soc": 29.5,
    "target_soc": 28.0,
    "remaining_margin_pct": 1.5,
    "v2l_status": "WARN_30M",
    "warning_level": 30,
    "relay_connected": true,
    "utility_mode_active": false
  },
  "message": "시뮬레이션 틱이 반영되었습니다. (30분 전 긴급 경고 도달)"
}
```

---

### 3.5 [사용 연장] 비상 충전소 기반 스마트 재계산
* **엔드포인트:** `POST /api/sessions/{session_id}/extend`
* **설명:** 30분 전 알림에서 [사용 연장] 클릭 시 무제한 연장 대신 **인근 급속 충전소(12km)로 목적지를 자동 재설정하여 목표 배터리를 28%에서 16%로 하향 조정**, 안전하게 추가 가용 시간을 확보합니다.
* **Request Body:**
```json
{
  "nearest_charger_name": "대관령 급속충전소",
  "distance_km": 12.0,
  "elevation_gain_m": 80.0
}
```
* **Response Body (200 OK):**
```json
{
  "status": 200,
  "data": {
    "session_id": 1,
    "new_destination": "대관령 급속충전소 (비상 귀가)",
    "previous_target_soc": 28.0,
    "new_target_soc": 16.0,
    "gained_soc_pct": 12.0,
    "additional_hours": 7.7,
    "v2l_status": "ACTIVE"
  },
  "message": "인근 급속충전소 기반으로 안전 마진이 재계산되어 V2L 사용 시간이 7.7시간 추가 연장되었습니다."
}
```

---

### 3.6 V2L 하드웨어 차단 및 무시동 히터 자동 전환 (Lock-out)
* **엔드포인트:** `POST /api/sessions/{session_id}/lockout`
* **설명:** 배터리 목표치 도달(자동) 또는 사용자의 수동 차단 버튼 클릭 시, V2L 물리 릴레이를 차단하고 캠퍼 동사 방지를 위해 차량 내 무시동 히터(유틸리티 모드, 21℃)를 즉각 가동합니다.
* **Request Body:**
```json
{
  "reason": "TARGET_SOC_REACHED"
}
```
* **Response Body (200 OK):**
```json
{
  "status": 200,
  "data": {
    "session_id": 1,
    "v2l_status": "LOCKED_OUT",
    "relay_connected": false,
    "utility_mode_active": true,
    "cabin_target_temp_celsius": 21.0,
    "bms_preserved_soc": 28.0,
    "return_distance_guaranteed_km": 92.0
  },
  "message": "안전을 위해 V2L이 정상 종료되었으며, 저체온증 방지를 위해 차량 무시동 히터가 21℃로 자동 가동되었습니다."
}
```


# V-Locker (브이-락커)

> **"내일의 날씨, 지형, 주행 데이터를 분석하여 안전한 귀가를 보장하는 AI 기반 V2L 전력 자동 제어 인카 앱"**  
> *현대자동차 Pleos App Market (SDV In-Car Infotainment) 최적화 서비스*

---

## 📌 제출 정보 및 핵심 요약 (Submission Info)

| 항목 | 내용 |
| :--- | :--- |
| **팀명** | **호호이** |
| **앱 이름** | **V-Locker (브이-락커)** |
| **한 줄 소개** | 내일 아침의 기온·지형 고도·운전자 주행 전비를 AI로 융합 분석해 귀가 필수 배터리를 자동 보존하고, V2L 차단 즉시 차량 무시동 히터(21℃)를 자동 가동해 캠퍼의 안전까지 지키는 SDV 인카 전력 안전 제어 솔루션 |
| **제출 APK 경로** | [`apk/호호이_V-Locker_release.apk`](file:///Users/heoyujin/Desktop/V-Locker/apk/호호이_V-Locker_release.apk) *(실행 가능한 정식 Release 빌드본, 약 6.7 MB)* |
| **최종 인카 앱 소스** | [`android-finalversion/`](file:///Users/heoyujin/Desktop/V-Locker/android-finalversion) *(아이오닉 5 / Pleos Connect `2560x1440` 와이드 디스플레이 최적화)* |
| **1차 프로토타입 소스** | [`android-prototype/`](file:///Users/heoyujin/Desktop/V-Locker/android-prototype) *(모바일 뷰 1차 프로토타입 보존본)* |
| **백엔드 AI 엔진 소스** | [`backend/`](file:///Users/heoyujin/Desktop/V-Locker/backend) *(FastAPI + PostgreSQL 17 + 기상청/카카오/고도 연동)* |
| **타깃 플랫폼** | 현대자동차그룹 Pleos Connect (Android Automotive OS 14 / API 34) |

---

## 1. 기획 배경 (Background)

전기차 보급과 함께 차박/캠핑 레저 수요가 폭발적으로 증가(캠핑카 튜닝 전년 대비 267.4% 폭등)하며, 전기차의 220V 외부 전력 공급 기능인 **V2L(Vehicle to Load)**이 필수 기능으로 자리 잡았다.

그러나 차박 유저들은 밤새 전기장판과 전자제품을 사용하면서 항상 심리적 불안을 겪는다.
> *"밤새 V2L 쓰다가 내일 아침 집에 갈 배터리가 방전되면 어떡하지?"*

### 기존 시스템의 한계점
* 현재 현대/기아 전기차의 내장 V2L 시스템은 사용자가 20%~80% 범위에서 **단순 감에 의존하여 차단 기준을 수동 설정**해야 한다.
* 하지만 야외 차박 환경에는 운전자가 예측하기 어려운 3대 환경 변수가 존재한다.:
  1. **저온 환경 배터리 열화:** 리튬이온 배터리는 최적 온도(25℃) 대비 **-20℃ 저온에서 주행거리가 최대 33% 감소** (한국자동차공학회 논문 근거).
  2. **산간 고도 변화:** 강원도/대관령 등 고지대 차박지에서 오르막길을 통과할 때 위치에너지($mgh$) 소모 급증.
  3. **콜드 스타트(Cold Start) & HVAC 초기 스파이크:** 영하의 아침 시동 시 배터리 컨디셔닝 승온 및 실내 난방에 순간적 전력 급감 발생.

**V2L 세이프 락커**는 외부 기상청, 내비게이션 토폴로지, 차량 BMS 센서 데이터를 AI로 융합 분석하여 익일 귀가에 필요한 최적의 배터리 잔량을 자동 계산하고 V2L을 스마트 제어한다.

---

## 2. 핵심 기능 및 차별점 (Core Features)

### 1) 다중 데이터 융합 배터리 소모량 예측 알고리즘 (AI Core)
* **기상청 단기예보 연동:** 익일 출발 시각의 최저 기온을 수집해 저온 효율 저하 계수($\eta_{temp}$) 적용.
* **내비게이션 토폴로지 연동:** 목적지까지의 순수 획득 고도($\Delta h$)와 내리막 회생제동(75% 회수)을 반영한 순 위치에너지 산출.
* **콜드 스타트 & HVAC 스파이크 차감:** 영하 날씨 출발 시 배터리 승온 및 실내 난방 상수를 사전 확보.
* **3단계 비례 가중치 안전 마진 모드 (Proportional Safety Factor):**
  * 환경·차량 변수를 결합해 순수 귀가 필요 전력($E_{pure}$)을 먼저 산출한 뒤, 목적에 맞게 비례 여유를 부여한다.
  * **안심 (`SAFE`, x1.35):** 순수 필요 전력 +35% 여유 · 폭설·정체·추가 난방까지 완벽 방어
  * **균형 (`NORMAL`, x1.15, 추천):** 순수 필요 전력 +15% 여유 · 주행 및 환경 변수를 고려한 표준 추천
  * **최대 사용 (`MAX_USE`, x1.00):** 귀가 최소 필수 전력만 보존 (하한선 BMS 10% 보호) · V2L 가용 시간 극대화
* **가용 시간(Time) 실시간 환산:** 배터리 %뿐만 아니라 현재 소비 전력(kW) 기준 **"앞으로 약 N.N시간 사용 가능"**을 동시 표기.

### 2) 엣지 케이스 방어 로직 (Soft Lock-out & Fail-Safe)
* **단계별 사전 예고 알림:** V2L 차단 **1시간 전(1차)**, **30분 전(2차 긴급)** 인포테인먼트 HUD 및 사운드 알림.
* **동사 방지 무시동 히터(유틸리티 모드) 자동 전환:** 
  * 야간 수면 중 갑작스러운 V2L 차단으로 전기장판이 꺼져 저체온증/동사 사고가 발생하는 것을 방지하기 위해, **V2L 차단 즉시 차량 내부 공조(유틸리티 모드, 21℃)를 최소 난방 온도로 자동 가동**.
* **스마트 [사용 연장] 안전 장치:** 30분 전 알림 시 무제한 연장이 아닌, **카카오 로컬/길찾기로 탐색한 최단거리 비상 급속 충전소 경로를 물리 엔진으로 재계산**해 안전하게 추가 가용 시간 확보.
* **3중 통신 장애 Fallback 방어:** 기상청/카카오 API 장애 또는 3.0초 타임아웃 발생 시 백업 API(Open-Meteo / OSRM) → 최종 안전 기본값(-5℃, 86km, +640m) 순으로 자동 방어.

---

## 3. 배터리 소모량 예측 수식 모델

$$E_{pure} = \frac{E_{driving}(d, \eta_{base}) + E_{elevation}(\Delta h, m)}{\eta_{temp}(T)} + E_{spike}(T), \quad E_{target} = E_{pure} \times \text{Multiplier}_{Mode}$$

```text
예시 (IONIQ 5 Long Range 77.4kWh · 전비 5.4km/kWh · 86km · +640m · -5℃ 기준):
1. 기본 주행 에너지 (E_base) = 거리(86km) / 평소 전비(5.4) = 15.93 kWh
2. 지형 위치에너지 (E_elev)  = (총질량 2190kg * g * 640m) / (3.6e6 * 0.88) * (1 - 0.75 회생) = 1.08 kWh
3. 저온 효율 계수 (η_temp)   = -5℃ 기준 0.812 (약 18.8% 효율 저하 보정)
4. 저온 보정 주행 에너지     = (15.93 + 1.08) / 0.812 = 20.93 kWh
5. 콜드스타트 & HVAC 스파이크 = -5℃ 기준 0.8 kWh 사전 확보
6. 순수 귀가 소모 전력(MAX_USE x1.00) = 21.73 kWh (28.1%) → NORMAL(x1.15) 32.3% / SAFE(x1.35) 37.9%
```

---

## 4. 인카 인포테인먼트(IVI) 화면 플로우

현대자동차 아이오닉 5 / Pleos Connect 와이드 디스플레이(`2560x1440 @ 160dpi` 기준 `16:9` / `21:9` 반응형 스케일링)에 최적화된 4대 상단 탭(`안전귀가 설정` · `에너지 분석` · `모니터링` · `유틸리티`) 및 핵심 화면으로 구성됩니다:

```
[Screen 01] 귀가 경로 & 출발 설정
  ├─ 목적지 검색 및 [우리 집], [인근 급속 충전소] 원터치 숏컷
  ├─ 한국 실제 GPS 위치 자동 반영(LocationHelper) + 출발 시각 · 탑승 인원 선택
  └─ 실도로 폴리라인 지도 미리보기(RoutePreviewView, 확대/축소 지원) & 상승 고도(+640m)
        │
        ▼ [AI 귀가 경로 & 배터리 분석하기]
[Screen 02] AI 융합 분석 & 3단계 마진 선택 (+ [에너지 분석] 상세 탭)
  ├─ 기상/고도/전비/콜드스타트 융합 분석 요약
  ├─ 안심(37.9%) / 균형 추천(32.3%) / 최대 사용(28.1%) 카드 선택 (가용 시간 동시 표기)
  └─ [이 기준으로 V2L 모니터링 시작] 클릭
        │
        ▼
[Screen 03] 실시간 V2L 세이프 모니터링 대시보드
  ├─ 현재 배터리 61% 분할 게이지 바 & 예약 잔량 32.3% 표시 (가용 여유 28.7%p)
  ├─ 실시간 소비 전력(1.2 kW) 추이 그래프(PowerTrendView) & 차단 카운트다운
  ├─ 1시간 전 / 30분 전 사전 경고 팝업 (급속충전소 스마트 [사용 연장] 연동)
  └─ [시연용] 10% 소모 · 1시간 전 · 30분 전 · 통신장애 Fallback 시뮬레이션 지원
        │ (서버 미연결 시에도 OfflineSafetyEngine으로 동일 안전 로직 무중단 동작)
        ▼ (예약 잔량 도달 또는 수동 차단)
[Screen 04] V2L 안전 락아웃 & 무시동 히터 자동 전환
  ├─ V2L 물리 릴레이 차단 (V2lHardwareController 어댑터)
  ├─ 귀가 전력 안전 보존 완료 & 귀가 가능 거리 안내
  └─ [안전 페일세이프] Pleos HVAC SDK로 차량 내부 무시동 히터(유틸리티 모드 21℃) 자동 가동
```

---

## 5. 기술 스택 및 테스트 환경 (Tech Stack & Environment)

* **Backend:** Python, **FastAPI**, **SQLAlchemy**, **Pydantic v2**, **HTTPX**
* **External Live APIs:** 기상청 단기예보(`15084084`), 카카오 로컬, 카카오모빌리티 길찾기, Open-Meteo Forecast/Elevation, OSRM (3중 안전망 자동 Fallback)
* **Database:** **PostgreSQL 17** (`users`, `v2l_sessions`, `favorite_destinations`, `v2l_session_events` 4개 테이블)
* **In-Car Client (`android-finalversion`):**
  * Android Automotive OS (Kotlin, `minSdk 28`, `compileSdk / targetSdk 37`, **Pleos Vehicle SDK `v2.3.2`**, Retrofit2)
  * **와이드 반응형 스케일링:** `values`(폰) · `values-sw600dp-land` · `values-sw720dp-land` · `values-sw1050dp-land`(`2560x1440 @ 160dpi` 아이오닉 5 기준 ~2.0배 스케일)
  * **로컬 안전/위치 엔진:** `OfflineSafetyEngine`, `RouteGeometry`, `LocationHelper`, `LocalStateStore`, `PleosPermissionHelper`
* **테스트 환경 및 필요 설정:**
  * **에뮬레이터:** Android Studio `Pleos Connect v2.1.0` (Android 14 / API 34, Cold boot, RAM 4096MB 권장)
  * **Virtual Console:** `http://127.0.0.1:8088/` (차량 배터리 SoC 및 공조 VHAL 제어)
  * **검증 완료 현황:**
    * Android (`android-finalversion`): JVM 단위 테스트 **28종 100% 통과**
    * Backend (`backend/tests`): Pytest **5개 테스트 스위트(21개 케이스)** 구성
    * Pleos Connect 에뮬레이터 실차 센서/HVAC 연동 실화면 시연 완료

---

## 6. 설치 및 실행 가이드

### 1) 저장소 클론 및 가상환경 설정
```bash
git clone https://github.com/He0yujin/V-Locker.git
cd V-Locker

# Python 가상환경 생성 및 활성화
python3 -m venv venv
source venv/bin/activate  # Windows: venv\Scripts\activate

# 의존성 설치
pip install -r backend/requirements.txt

# 환경변수 설정 (.env.example 복사 후 DATABASE_URL 및 API 키 입력)
cp .env.example .env
```

### 2) 백엔드 서버 실행
```bash
PYTHONPATH=. uvicorn backend.app.main:app --host 0.0.0.0 --port 8000 --reload
```
* API 명세서(Swagger UI): `http://localhost:8000/docs`

### 3) 인카 앱(APK) 설치 및 실행
```bash
# 1. PC 에뮬레이터에 포트 포워딩 연결 (Virtual Console & 백엔드 통신)
adb forward tcp:8088 tcp:8088
adb reverse tcp:8000 tcp:8000

# 2. 제출용 정식 Release APK 직접 설치
adb install -r apk/호호이_V-Locker_release.apk

# 3. 앱 실행
adb shell am start -n com.example.Vlocker.wide/com.example.Vlocker.MainActivity
```
*(소스코드 직접 빌드 시: `./android-finalversion/gradlew -p android-finalversion assembleRelease`)*

### 4) 테스트 실행
```bash
# 1) Android (android-finalversion) 단위 테스트 (28종)
./android-finalversion/gradlew -p android-finalversion testDebugUnitTest

# 2) Backend Pytest (PostgreSQL DATABASE_URL 설정 필요)
PYTHONPATH=. pytest backend/tests/ -v
```

---

## 7. 프로젝트 디렉토리 구조

```
V-Locker/
├── apk/                                 # 📦 최종 제출용 실행 가능한 Release APK
│   └── 호호이_V-Locker_release.apk       # 호호이 팀 V-Locker 릴리스 APK (약 6.7 MB)
├── android-finalversion/                # 🚘 [최종 제출] Pleos Connect 와이드 디스플레이 최적화 인카 앱
│   └── app/src/
│       ├── main/
│       │   ├── AndroidManifest.xml      # Pleos 권한(VEHICLE_BINDING/CAR_ENERGY/CLIMATE) & uses-feature 선언
│       │   ├── java/com/example/Vlocker/
│       │   │   ├── MainActivity.kt      # 4대 탭·모달 스케일링·상태 연동 메인 액티비티
│       │   │   ├── data/LocalStateStore.kt
│       │   │   ├── location/LocationHelper.kt
│       │   │   ├── network/             # ApiClient, ApiModels, VlockerApiService
│       │   │   ├── safety/OfflineSafetyEngine.kt
│       │   │   ├── ui/                  # RoutePreviewView, RouteGeometry, PowerTrendView, SegmentedEnergyBarView, UiScaleHelper
│       │   │   └── vehicle/PleosVehicleManager.kt  # Vehicle SDK v2.3.2 & PleosPermissionHelper
│       │   └── res/
│       │       ├── layout-land/         # 차량 와이드 가로형 레이아웃
│       │       └── values-sw{600,720,1050}dp-land/  # 해상도별 반응형 dimens.xml
│       └── test/                        # JVM 단위 테스트 7개 클래스 (28종)
├── android-prototype/                   # 📱 [보존본] 1차 모바일 프로토타입 앱 (스마트폰 뷰)
├── backend/
│   ├── app/
│   │   ├── api/routes.py                # RESTful API 라우터 (/predict, /sessions, /favorites 등)
│   │   ├── core/
│   │   │   ├── config.py                # .env 환경변수 및 타임아웃(3.0s) 로드
│   │   │   └── calculator.py            # 비례 가중치 배터리 예측 코어 엔진
│   │   ├── db/
│   │   │   ├── database.py              # PostgreSQL SQLAlchemy 연결 설정
│   │   │   ├── models.py                # User, V2LSession, FavoriteDestination, V2LSessionEvent
│   │   │   └── session_events.py        # 세션 상태 전이 자동 감사 이벤트 리스너
│   │   ├── schemas/session.py           # Pydantic v2 입출력 스키마
│   │   └── services/
│   │       ├── places.py                # 카카오 로컬 목적지 & 충전소 검색
│   │       ├── weather.py               # 기상청 단기예보 연동 (격자변환 / Fallback)
│   │       ├── navigation.py            # 카카오모빌리티 길찾기 + 지형 고도 API + 폴리라인
│   │       └── v2l_controller.py        # 릴레이 차단, Soft Lock-out, 무시동 히터 제어
│   ├── requirements.txt
│   └── tests/                           # test_calculator, test_api_endpoints, test_favorites, test_db_*
├── docs/
│   └── ERD.md                           # PostgreSQL 4테이블 ERD 명세서
├── .env.example                         # DB 및 외부 API 키 환경변수 템플릿
├── V-Locker_기획서.md                   # 서비스 종합 기획서
├── 요구사항_정의서.md                    # 시스템 요구사항 정의서 (v2.0)
├── API_명세서.md                        # RESTful API 연동 명세서 (v2.1)
├── Pleos_개발_가이드.md                 # Pleos Playground (Kotlin + SDK 2.3.2) 개발 가이드
└── README.md                            # 프로젝트 소개 문서
```


# V-Locker

> **"내일의 날씨, 지형, 주행 데이터를 분석하여 안전한 귀가를 보장하는 AI 기반 V2L 전력 자동 제어 인카 앱"**  
> *현대자동차 Pleos App Market (SDV In-Car Infotainment) 최적화 서비스*

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

현대자동차 Pleos 와이드 디스플레이(16:9 / 21:9) 환경에 최적화된 4대 핵심 화면으로 구성됩니다:

```
[Screen 01] 귀가 경로 & 출발 설정
  ├─ 목적지 검색 및 [우리 집], [가까운 급속 충전소] 원터치 숏컷
  ├─ 예상 출발 시각 및 탑승 인원(중량 계산 연동) 선택
  └─ 경로 지도 및 상승 고도(+640m) 미리보기
        │
        ▼ [경로 분석하기]
[Screen 02] AI 융합 분석 & 3단계 마진 선택
  ├─ 기상/고도/전비 융합 분석 요약 바
  ├─ 안심(34%) / 균형(28%) / 최대사용(24%) 카드 선택 (가용 시간 동시 표기)
  └─ [이 기준으로 모니터링] 클릭
        │
        ▼
[Screen 03] 실시간 V2L 세이프 모니터링 대시보드
  ├─ 현재 배터리 61% 프로그레스 바 & 예약 잔량 28% 표시 (차단까지 33%p)
  ├─ 실시간 소비 전력(1.2 kW) 및 차단 카운트다운 (약 21.3시간 남음)
  ├─ 1시간 전 알림 / 30분 전 긴급 알림 팝업 (사용 연장 연동)
  └─ [데모 시연용] 배터리 가속 소모 시뮬레이션 버튼 지원
        │
        ▼ (예약 잔량 28% 도달)
[Screen 04] V2L 안전 락아웃 & 무시동 히터 자동 전환
  ├─ V2L 물리 릴레이 차단 (누설 전력 0W)
  ├─ 배터리 28% 안전 보존 완료 (귀가 가능 거리 92km)
  └─ [안전 페일세이프] 차량 내부 무시동 히터(유틸리티 모드 21℃) 자동 가동
```

---

## 5. 기술 스택 (Tech Stack)

* **Backend:** Python 3.13, **FastAPI**, **SQLAlchemy**, **Pydantic v2**, **HTTPX**
* **External Live APIs:** 기상청 단기예보(`15084084`), 카카오 로컬, 카카오모빌리티 길찾기, Open-Meteo Elevation
* **Database:** SQLite (온보드 임베디드 DB / PostgreSQL 마이그레이션 가능)
* **In-Car Client (Target):** Android Automotive OS (Kotlin, Pleos Vehicle SDK `2.3.1`, Retrofit2)
* **Testing:** Pytest (단위/통합 테스트 13종 100% 통과)
* **Target Environment:** 현대자동차 Pleos App Market (SDV)

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

# 환경변수 설정 (.env.example 복사 후 실제 키 입력)
cp .env.example .env
```

### 2) 백엔드 서버 실행
```bash
PYTHONPATH=. uvicorn backend.app.main:app --host 0.0.0.0 --port 8000 --reload
```
* API 명세서(Swagger UI): `http://localhost:8000/docs`

### 3) 단위 및 통합 테스트 실행 (13종 100% 통과 검증)
```bash
PYTHONPATH=. pytest backend/tests/ -v
```

---

## 7. 프로젝트 디렉토리 구조

```
V2L Safe Locker/
├── backend/
│   ├── app/
│   │   ├── api/
│   │   │   └── routes.py         # 통일된 JSON RESTful API 라우터
│   │   ├── core/
│   │   │   ├── config.py         # .env 환경변수 및 타임아웃(3.0s) 로드
│   │   │   └── calculator.py     # 비례 가중치 배터리 예측 코어 엔진
│   │   ├── db/
│   │   │   ├── database.py       # SQLAlchemy SQLite 연결 설정
│   │   │   └── models.py         # User, V2LSession DB 모델
│   │   ├── schemas/
│   │   │   └── session.py        # Pydantic v2 입출력 스키마
│   │   ├── services/
│   │   │   ├── places.py         # 카카오 로컬 목적지 & 충전소 검색
│   │   │   ├── weather.py        # 기상청 단기예보 연동 (격자변환 / Fallback)
│   │   │   ├── navigation.py     # 카카오모빌리티 길찾기 + 지형 고도 API
│   │   │   └── v2l_controller.py # 릴레이 차단, Soft Lock-out, 무시동 히터 제어
│   │   └── main.py               # FastAPI 애플리케이션 진입점
│   ├── requirements.txt
│   └── tests/
│       ├── test_calculator.py    # 수식/비례가중치/외부API 파싱 단위 테스트
│       └── test_api_endpoints.py # 엔드포인트 수명주기 통합 테스트
├── .env.example                  # API 키 환경변수 템플릿
├── 와이어프레임/                  # IVI 와이드 인포테인먼트 화면 스크린샷
├── 요구사항_정의서.md              # 상세 시스템 요구사항 정의서 (v1.2)
├── API_명세서.md                  # RESTful API 엔드포인트 명세서 (v2.0)
├── Pleos_개발_가이드.md           # Pleos Playground (Kotlin + SDK 2.3.1) 개발 가이드
└── README.md                     # 프로젝트 소개 문서
```


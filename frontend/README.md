# V-Locker Frontend

Figma 기반 V2L 귀가 배터리 보호 웹 UI입니다. 귀가 조건 입력 → AI 배터리 분석 → 안전 마진 선택 → 실시간 모니터링 → 차단 완료의 4단계 흐름을 제공합니다.

## 프론트엔드 실행

저장소 루트에서 실행합니다.

```powershell
cd frontend
py -m http.server 4173
```

브라우저에서 `http://localhost:4173`을 엽니다. Python이 없으면 `python -m http.server 4173`을 사용하세요.

## 백엔드 연결

프론트엔드는 기본으로 `http://localhost:8000`의 FastAPI를 사용합니다. 저장소 루트에서 백엔드 실행:

```powershell
python -m venv venv
.\venv\Scripts\Activate.ps1
pip install -r backend/requirements.txt
$env:PYTHONPATH = "."
uvicorn backend.app.main:app --reload --port 8000
```

API 키가 없거나 백엔드에 연결할 수 없어도 UI 데모 흐름을 볼 수 있습니다. 이때 앱은 기본 안전값(−5°C, 오르막 640m)을 사용하고 화면에 기본 안전 모드 배너를 표시합니다.

백엔드 주소를 바꿔야 할 때는 브라우저 개발자 도구 콘솔에서 `localStorage.setItem('vlocker-api-base', 'http://localhost:8000')`처럼 설정한 뒤 새로고침하세요.

## 연결 API

- `GET /api/places/search` 목적지 검색
- `GET /api/chargers/nearby` 가까운 급속 충전소
- `POST /api/predict` 귀가 배터리 예측
- `POST /api/sessions`, `GET /api/sessions/{id}` 모니터링 세션
- `POST /api/sessions/{id}/tick` 시연용 배터리 소모
- `POST /api/sessions/{id}/extend` 충전소 경로 재계산 및 사용 연장
- `POST /api/sessions/{id}/lockout` V2L 차단

차량 센서와 Pleos HVAC 제어는 실제 차량 SDK 연동 전까지 시뮬레이션 값입니다.

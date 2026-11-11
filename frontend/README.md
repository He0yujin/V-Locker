# Pleos Connect · V2L 세이프 락커

Figma 확정 디자인을 바탕으로 만든 반응형 웹 앱 프로토타입입니다. 안전 마진 선택부터 활성화, 실시간 모니터링, V2L 차단 확인 및 완료 화면까지 이어집니다.

## 실행

Python 3가 설치되어 있으면 이 폴더에서 아래 명령을 실행한 뒤 브라우저에서 `http://localhost:4173`을 여세요.

```powershell
python -m http.server 4173
```

또는 `index.html`을 브라우저에서 직접 열어도 됩니다. 별도 패키지 설치나 빌드 과정은 필요하지 않습니다.

## 구성

- `index.html` 앱 진입점
- `styles.css` 반응형 화면 스타일
- `app.js` 화면 상태 및 상호작용
- `assets/` Figma 디자인의 SVG 에셋

현재 차량 배터리·주행 데이터는 화면 흐름 확인을 위한 예시 값입니다.

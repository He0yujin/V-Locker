const asset = (name) => `assets/${name}`;

const plans = {
  safe: {
    key: "safe",
    label: "안심",
    description: "여유를 넉넉히 확보",
    reserve: 34,
    hours: "5시간 10분",
    gap: 27,
    gauge: "gauge-safe.svg",
    headline: "귀가 에너지를 넉넉히 남겨둡니다.",
    confirmation: "약 5시간 10분 사용 가능 · 귀가 잔량 34%",
  },
  balanced: {
    key: "balanced",
    label: "균형",
    description: "주행 데이터 기준으로 설정",
    reserve: 28,
    hours: "6시간 40분",
    gap: 33,
    gauge: "gauge-balanced.svg",
    headline: "돌아갈 에너지를 남겨둡니다.",
    confirmation: "귀가 주행 예상량과 출발 전 공조 여유분을 확보했습니다.",
  },
  maximum: {
    key: "maximum",
    label: "최대 사용",
    description: "V2L 사용을 우선",
    reserve: 24,
    hours: "7시간 50분",
    gap: 37,
    gauge: "gauge-max.svg",
    headline: "V2L을 더 오래 사용할 수 있어요.",
    confirmation: "약 7시간 50분 사용 가능 · 귀가 잔량 24%",
  },
};

const state = {
  screen: "selection",
  margin: "balanced",
  v2lEnabled: true,
  modalOpen: false,
};

const app = document.querySelector("#app");
const toast = document.querySelector("#toast");
let toastTimer;

function currentPlan() {
  return plans[state.margin] || plans.balanced;
}

function showToast(message) {
  toast.textContent = message;
  toast.classList.add("visible");
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => toast.classList.remove("visible"), 2600);
}

function header(active = "safety") {
  const nav = [
    ["safety", "안전 계획"],
    ["energy", "에너지 분석"],
    ["monitor", "실시간 모니터링"],
    ["utility", "차량 및 유틸리티"],
  ];
  return `
    <header class="topbar">
      <div class="brand">
        <span class="brand-mark"><img src="${asset("brand-shield.svg")}" alt="" /></span>
        <span class="brand-name">V2L 세이프 락커</span>
      </div>
      <div class="vehicle-meta">
        <span>내 차량 · 전기차</span>
        <span class="connection-pill"><img src="${asset("status-dot.svg")}" alt="" />차량 연결됨</span>
        <span class="top-divider" aria-hidden="true"></span>
        <img class="top-icon" src="${asset("bell.svg")}" alt="알림" />
        <img class="top-icon" src="${asset("settings.svg")}" alt="설정" />
      </div>
    </header>
    <nav class="nav-bar" aria-label="주요 메뉴">
      ${nav.map(([key, label]) => `<button type="button" class="nav-item ${active === key ? "active" : ""}" data-nav="${key}">${label}</button>`).join("")}
    </nav>`;
}

function intro(title, subtitle = "", className = "") {
  return `
    <section class="page-intro ${subtitle ? "has-subtitle" : ""} ${className}">
      <div class="intro-stack">
        <h1>${title}</h1>
        ${subtitle ? `<p>${subtitle}</p>` : ""}
      </div>
    </section>`;
}

function footerButton(label, action, secondary = false, disabled = false) {
  const klass = secondary ? "secondary-button" : "primary-button";
  return `<button type="button" class="${klass} ${disabled ? "button-disabled" : ""}" data-action="${action}" ${disabled ? "disabled" : ""}>${label}${secondary ? "" : `<img src="${asset("arrow-right.svg")}" alt="" />`}</button>`;
}

function renderSelection() {
  const plan = currentPlan();
  const options = Object.values(plans).map((item) => `
    <button type="button" class="option-card ${state.margin === item.key ? "selected" : ""}" data-margin="${item.key}" aria-pressed="${state.margin === item.key}">
      <span class="option-title-row">
        <span class="option-title">${item.label}</span>
        <img class="option-radio" src="${asset(state.margin === item.key ? "radio-selected.svg" : "radio-empty.svg")}" alt="${state.margin === item.key ? "선택됨" : "선택 안 됨"}" />
      </span>
      <span class="option-description">${item.description}</span>
      <span class="reserve-line"><span class="reserve-number">${item.reserve}%</span><span class="reserve-caption">귀가 예약 잔량</span></span>
      <span class="option-rule"></span>
      <span class="time-line"><span>앞으로 약</span><strong>${item.hours}</strong></span>
    </button>`).join("");

  return `
    <div class="app-shell screen-enter">
      ${header("safety")}
      ${intro("안전 귀가에 필요한 전력량")}
      <section class="content selection-content" aria-label="귀가 전력 예측 및 안전 마진 선택">
        <div class="trip-summary">
          <div class="trip-topline">
            <div class="destination-line">
              <img src="${asset("house.svg")}" alt="" />
              <strong class="destination-name">우리 집까지 86 km</strong>
              <span class="trip-duration">예상 1시간 25분</span>
            </div>
            <span class="trip-badge"><img src="${asset("route-dot.svg")}" alt="" />내일 오전 10:00 출발</span>
          </div>
          <div class="summary-rule"></div>
          <div class="weather-grid">
            <div class="weather-item"><span>예상 기온</span><strong>−5°C</strong></div>
            <div class="weather-item"><span>총 상승 고도</span><strong>+640 m</strong></div>
            <div class="weather-item"><span>평소 전비</span><strong>5.4 km/kWh</strong></div>
            <div class="weather-item"><span>공조 여유분</span><strong>출발 전 반영</strong></div>
          </div>
        </div>
        <div class="margin-heading"><strong>귀가 예약 잔량을 선택하세요</strong><span>현재 배터리 61% 기준</span></div>
        <div class="option-grid">${options}</div>
        <div class="disclaimer"><img src="${asset("info.svg")}" alt="" /><span>예측값은 참고치입니다. 실제 소모량은 기온, 적재량, 주행 조건에 따라 달라질 수 있습니다.</span></div>
      </section>
      <footer class="bottom-actions no-rule">${footerButton("이 기준으로 모니터링", "activate")}</footer>
    </div>`;
}

function renderActivation() {
  const plan = currentPlan();
  const statusSteps = [
    ["bell-small.svg", "1시간 전", "사전 알림"],
    ["bell-small.svg", "30분 전", "긴급 알림"],
    ["shield-small.svg", "예약 잔량 도달", "V2L 자동 차단"],
  ];
  return `
    <div class="app-shell screen-enter">
      ${header("safety")}
      ${intro("안전 계획이 활성화됐어요")}
      <section class="content activation-content" aria-label="안전 계획 활성화 확인">
        <div class="activation-grid">
          <article class="protection-card">
            <span class="state-pill"><img src="${asset("success-dot.svg")}" alt="" />안전 계획 활성</span>
            <div class="reserve-gauge" aria-label="귀가 예약 잔량 ${plan.reserve}%">
              <img class="gauge-background" src="${asset("gauge-background.svg")}" alt="" />
              <div class="gauge-value-clip" data-mode="${plan.key}"><img src="${asset(plan.gauge)}" alt="" /></div>
              <div class="gauge-number">${plan.reserve}%</div>
              <div class="gauge-caption">귀가 예약 잔량</div>
            </div>
            <h2 class="confirmation-title">${plan.headline}</h2>
            <p class="confirmation-copy">${plan.confirmation}</p>
            <div class="card-divider"></div>
            <div class="choice-summary"><span>선택한 안전 마진</span><strong>${plan.label}</strong></div>
            <p class="small-note">언제든 설정을 변경하거나 모니터링을 종료할 수 있습니다.</p>
          </article>
          <div class="activation-side">
            <article class="data-card">
              <div class="section-title-row">
                <h2 class="card-heading">데이터 상태</h2>
                <span class="state-pill muted-pill"><img src="${asset("status-muted.svg")}" alt="" />연결 확인 완료</span>
              </div>
              <div class="data-row">
                <span class="data-icon"><img src="${asset("cloud-sun.svg")}" alt="" /></span>
                <span class="data-copy"><strong>기상·경로 데이터 연결됨</strong><span>귀가 조건에 맞는 예측 데이터 반영</span></span>
                <img class="data-check" src="${asset("check.svg")}" alt="확인" />
              </div>
              <div class="data-row">
                <span class="data-icon"><img src="${asset("battery-charging.svg")}" alt="" /></span>
                <span class="data-copy"><strong>차량 배터리 상태 실시간 수신 중</strong><span>현재 차량 배터리 61%</span></span>
                <img class="data-check" src="${asset("check.svg")}" alt="확인" />
              </div>
              <div class="thin-rule"></div>
              <div class="update-row"><span>예측 데이터 업데이트</span><strong>15분마다</strong></div>
            </article>
            <article class="alerts-card">
              <h2 class="card-heading">차단 전에 미리 알려드려요</h2>
              <div class="alert-steps">${statusSteps.map(([icon, title, caption]) => `<div class="alert-step"><img src="${asset(icon)}" alt="" /><strong>${title}</strong><span>${caption}</span></div>`).join("")}</div>
            </article>
          </div>
        </div>
      </section>
      <footer class="bottom-actions">${footerButton("모니터링 시작", "monitor")}</footer>
    </div>`;
}

function renderRouteMap() {
  return `
    <div class="route-map" aria-label="현재 캠핑장에서 우리 집까지의 귀가 경로">
      <img class="map-asset map-water" src="${asset("route-water.svg")}" alt="" />
      <img class="map-asset map-connector one" src="${asset("route-connector.svg")}" alt="" />
      <img class="map-asset map-connector two" src="${asset("route-connector.svg")}" alt="" />
      <img class="map-asset map-connector three" src="${asset("route-connector.svg")}" alt="" />
      <img class="map-asset map-road row-one" src="${asset("route-road.svg")}" alt="" />
      <img class="map-asset map-road row-two" src="${asset("route-road.svg")}" alt="" />
      <img class="map-asset map-road row-three" src="${asset("route-road.svg")}" alt="" />
      <img class="map-asset map-road row-four" src="${asset("route-road.svg")}" alt="" />
      <img class="map-asset map-outline" src="${asset("route-outline.svg")}" alt="" />
      <img class="map-asset map-route" src="${asset("route-line.svg")}" alt="" />
      <img class="map-asset map-destination" src="${asset("destination-dot.svg")}" alt="" />
      <img class="map-asset map-origin" src="${asset("origin-dot.svg")}" alt="" />
      <span class="map-badge">귀가 경로 미리보기</span>
      <span class="map-label destination">우리 집</span><span class="map-label origin">현재 캠핑장</span>
      <span class="map-caption">경로 개요 · 실제 지도 축척과 다름</span>
      <span class="map-zoom" aria-hidden="true">＋<br />−</span>
    </div>`;
}

function renderMonitor() {
  const plan = currentPlan();
  const protectedWidth = plan.reserve;
  const usedWidth = Math.max(0, 61 - plan.reserve);
  const planLine = `귀가 예약 잔량 ${plan.reserve}% · ${plan.label} 마진 · 예약 잔량 도달 시 전원 공급 종료`;
  const chargeStatus = state.v2lEnabled ? "현재 V2L 사용 중" : "V2L 공급 차단 완료";
  const availableTime = state.v2lEnabled ? plan.hours : "사용 종료";
  return `
    <div class="app-shell screen-enter">
      ${header("monitor")}
      ${intro("V2L 안전 모니터링", "캠핑에 쓰는 에너지와 귀가를 위한 에너지를 한눈에 확인하세요.", "monitor-intro")}
      <section class="content monitor-content" aria-label="V2L 안전 모니터링">
        <div class="monitor-grid">
          <div class="monitor-left">
            <article class="battery-card">
              <div class="supply-row">
                <span class="state-pill dark-pill"><img src="${asset("status-dot.svg")}" alt="" />${chargeStatus}</span>
                <span class="live-label">차량 배터리 실시간 수신</span>
              </div>
              <div class="energy-metrics">
                <div><span class="metric-label">배터리 잔량</span><strong class="battery-number">61%</strong></div>
                <div><span class="metric-label">앞으로 약 사용 가능</span><strong class="time-value">${availableTime}</strong></div>
              </div>
              <div class="energy-bar" role="img" aria-label="귀가 예약 ${plan.reserve}%, V2L 사용 가능 ${usedWidth}%, 남은 전력 39%">
                <span class="energy-segment energy-protected" style="width:${protectedWidth}%"></span>
                <span class="energy-segment energy-used" style="width:${usedWidth}%"></span>
                <span class="energy-segment energy-unused"></span>
              </div>
              <div class="energy-labels">
                <span>귀가 예약 ${plan.reserve}%</span><span class="threshold">차단 기준까지 ${plan.gap}%p</span><span>100%</span>
              </div>
              <div class="energy-explanation">${state.v2lEnabled ? planLine : `V2L 공급이 중지됐습니다 · 귀가 예약 잔량 ${plan.reserve}%를 보호하고 있습니다.`}</div>
            </article>
            <article class="consumption-card">
              <div>
                <p class="consumption-label">실시간 소비 전력</p>
                <p class="consumption-value">${state.v2lEnabled ? "1.2 kW" : "0.0 kW"}</p>
                <p class="consumption-foot">연결된 기기 전체 사용량</p>
              </div>
              <div class="trend-wrap">
                <img class="trend-img" src="${asset("power-trend.svg")}" alt="최근 소비 전력 추세" />
                <div class="trend-caption"><span>전력 흐름 · 표시 예시</span><span>현재</span></div>
              </div>
            </article>
          </div>
          <aside class="conditions-card">
            <div class="conditions-heading"><h2>귀가 조건</h2><button class="text-link" type="button" data-action="change-settings">설정 변경 →</button></div>
            ${renderRouteMap()}
            <div class="condition-list">
              <div class="condition-row"><span>목적지</span><strong>우리 집</strong></div>
              <div class="condition-row"><span>예상 출발</span><strong>내일 오전 10:00</strong></div>
              <div class="condition-row"><span>거리 / 예상 시간</span><strong>86 km / 1시간 25분</strong></div>
              <div class="condition-row"><span>예상 기온 / 상승 고도</span><strong>−5°C / +640 m</strong></div>
              <div class="condition-row"><span>안전 마진</span><strong class="teal-value">${plan.label} · ${plan.reserve}%</strong></div>
            </div>
            <p class="condition-note">기상·경로 예측은 15분마다 업데이트됩니다. 실제 소모량은 주행 조건에 따라 달라집니다.</p>
          </aside>
        </div>
        <div class="monitor-alert"><img src="${asset("monitoring-shield.svg")}" alt="" /><span>임계치 도달 1시간·30분 전에 알려드립니다. 차단 시 난방 유지 가능 여부를 확인합니다.</span><strong>안전 계획 활성</strong></div>
      </section>
      <footer class="bottom-actions">${state.v2lEnabled ? footerButton("V2L 차단", "open-cutoff", true) : `<button type="button" class="secondary-button button-disabled" disabled>차단 완료</button>`}</footer>
      ${state.modalOpen ? renderCutoffModal() : ""}
    </div>`;
}

function renderCutoffModal() {
  return `
    <div class="modal-backdrop" data-action="backdrop" role="presentation">
      <section class="confirm-dialog" role="dialog" aria-modal="true" aria-labelledby="cutoff-title" aria-describedby="cutoff-copy">
        <span class="warning-icon"><img src="${asset("power.svg")}" alt="" /></span>
        <h2 id="cutoff-title">V2L 전원 공급을 차단할까요?</h2>
        <p id="cutoff-copy">차단하면 연결된 기기의 전원이 꺼집니다.<br />계속하려면 ‘V2L 차단’을 선택하세요.</p>
        <div class="dialog-actions"><button type="button" class="dialog-cancel" data-action="cancel-cutoff">취소</button><button type="button" class="dialog-confirm" data-action="confirm-cutoff">V2L 차단</button></div>
      </section>
    </div>`;
}

function renderCompletion() {
  return `
    <div class="app-shell screen-enter">
      ${header("utility")}
      ${intro("귀가 전력은 지키고, 실내는 따뜻하게")}
      <section class="content complete-content" aria-label="V2L 차단 완료">
        <article class="completion-banner">
          <img src="${asset("complete-shield.svg")}" alt="" />
          <div class="completion-copy"><h2>안전 귀가를 위해 V2L이 자동 종료되었습니다.</h2><p>차박 체온 유지를 위해 차량 내부 무시동 히터(유틸리티 모드 21℃)가 자동 작동 중입니다.</p></div>
          <span class="state-pill muted-pill"><img src="${asset("complete-dot.svg")}" alt="" />귀가 전력 보호</span>
        </article>
        <div class="completion-checklist"><img src="${asset("complete-check.svg")}" alt="" /><span>모든 도어와 트렁크가 잠겨 있습니다. 실내 온도와 전원 상태를 확인한 뒤 차단을 완료하세요.</span></div>
        <div class="utility-grid">
          <article class="utility-card"><img src="${asset("complete-power.svg")}" alt="" /><h3>V2L 자동 종료 완료</h3></article>
          <article class="utility-card"><img src="${asset("thermometer.svg")}" alt="" /><h3>유틸리티 모드 21℃</h3></article>
          <article class="utility-card"><span class="lock-icon"><img src="${asset("lock.svg")}" alt="" /></span><h3>도어 잠금 완료</h3></article>
        </div>
      </section>
      <footer class="bottom-actions no-rule complete-actions">${footerButton("취소", "cancel-completion", true)}${footerButton("차단 완료", "finish-cutoff")}</footer>
    </div>`;
}

function render() {
  if (state.screen === "activation") app.innerHTML = renderActivation();
  else if (state.screen === "monitor") app.innerHTML = renderMonitor();
  else if (state.screen === "completion") app.innerHTML = renderCompletion();
  else app.innerHTML = renderSelection();
}

app.addEventListener("click", (event) => {
  const marginButton = event.target.closest("[data-margin]");
  if (marginButton) {
    state.margin = marginButton.dataset.margin;
    render();
    return;
  }

  const navButton = event.target.closest("[data-nav]");
  if (navButton) {
    if (navButton.dataset.nav === "safety") state.screen = "selection";
    else if (navButton.dataset.nav === "monitor") state.screen = "monitor";
    else if (navButton.dataset.nav === "utility") state.screen = "completion";
    else showToast("에너지 분석 화면은 다음 개발 단계에서 연결할게요.");
    render();
    return;
  }

  const action = event.target.closest("[data-action]")?.dataset.action;
  if (!action) return;
  if (action === "activate") state.screen = "activation";
  else if (action === "monitor") { state.screen = "monitor"; state.v2lEnabled = true; }
  else if (action === "change-settings") state.screen = "selection";
  else if (action === "open-cutoff") state.modalOpen = true;
  else if (action === "cancel-cutoff" || action === "backdrop") {
    if (action === "backdrop" && event.target.closest(".confirm-dialog")) return;
    state.modalOpen = false;
  } else if (action === "confirm-cutoff") {
    state.modalOpen = false;
    state.screen = "completion";
  } else if (action === "cancel-completion") state.screen = "monitor";
  else if (action === "finish-cutoff") {
    state.v2lEnabled = false;
    state.screen = "monitor";
    showToast("V2L 전원이 차단됐어요. 귀가 예약 잔량을 보호하고 있습니다.");
  }
  render();
});

document.addEventListener("keydown", (event) => {
  if (event.key === "Escape" && state.modalOpen) {
    state.modalOpen = false;
    render();
  }
});

render();

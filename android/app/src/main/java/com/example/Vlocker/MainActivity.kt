package com.example.Vlocker

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.example.Vlocker.network.BreakdownInfo
import com.example.Vlocker.network.DestinationInfo
import com.example.Vlocker.network.DestinationSummary
import com.example.Vlocker.network.LockoutData
import com.example.Vlocker.network.MarginRecommendation
import com.example.Vlocker.network.NavigationInfo
import com.example.Vlocker.network.PlaceItem
import com.example.Vlocker.network.PredictData
import com.example.Vlocker.network.SessionStatusData
import com.example.Vlocker.network.VehicleInfo
import com.example.Vlocker.network.WeatherInfo
import com.example.Vlocker.vehicle.PleosVehicleManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var vehicleManager: PleosVehicleManager

    // 상단 바 & 배너
    private lateinit var tvConnectionBadge: TextView
    private lateinit var btnVehicleSettings: MaterialButton
    private lateinit var cardFallbackBanner: MaterialCardView
    private lateinit var tvFallbackMessage: TextView

    // 화면 컨테이너 (Screen 01 ~ 04)
    private lateinit var layoutScreen01: LinearLayout
    private lateinit var layoutScreen00: LinearLayout
    private lateinit var tvDashboardSoc: TextView
    private lateinit var tvDashboardReserve: TextView
    private lateinit var btnDashboardDestination: MaterialButton
    private lateinit var btnHeaderSafety: MaterialButton
    private lateinit var btnHeaderEnergy: MaterialButton
    private lateinit var btnHeaderMonitor: MaterialButton
    private lateinit var btnHeaderUtility: MaterialButton
    private lateinit var layoutScreen05: LinearLayout
    private lateinit var tvEnergyAnalysisSummary: TextView
    private lateinit var layoutScreen02: LinearLayout
    private lateinit var layoutScreen03: LinearLayout
    private lateinit var layoutScreen04: LinearLayout

    // Screen 01 뷰
    private lateinit var etDestination: EditText
    private lateinit var btnSearchPlaces: MaterialButton
    private lateinit var layoutPlaceResults: LinearLayout
    private lateinit var btnShortcutHome: MaterialButton
    private lateinit var btnShortcutCharger: MaterialButton
    private lateinit var tvSelectedDestCoords: TextView
    private lateinit var btnDepMorning07: MaterialButton
    private lateinit var btnDepMorning10: MaterialButton
    private lateinit var btnDepNoon12: MaterialButton
    private lateinit var btnPass1: MaterialButton
    private lateinit var btnPass2: MaterialButton
    private lateinit var btnPass4: MaterialButton
    private lateinit var etV2lLoadKw: EditText
    private lateinit var cbForceFallback: CheckBox
    private lateinit var tvScreen01SocInfo: TextView
    private lateinit var progressPredict: ProgressBar
    private lateinit var btnAnalyzeRoute: MaterialButton

    // Screen 02 뷰
    private lateinit var btnBackToScreen01: MaterialButton
    private lateinit var tvPredictRouteSummary: TextView
    private lateinit var tvPredictEnvBadges: TextView
    private lateinit var tvPredictBreakdown: TextView
    private lateinit var cardModeSafe: MaterialCardView
    private lateinit var tvSafeReserve: TextView
    private lateinit var tvSafeHours: TextView
    private lateinit var tvSafeDesc: TextView
    private lateinit var cardModeNormal: MaterialCardView
    private lateinit var tvNormalReserve: TextView
    private lateinit var tvNormalHours: TextView
    private lateinit var tvNormalDesc: TextView
    private lateinit var cardModeMax: MaterialCardView
    private lateinit var tvMaxReserve: TextView
    private lateinit var tvMaxHours: TextView
    private lateinit var tvMaxDesc: TextView
    private lateinit var tvInsufficientWarning: TextView
    private lateinit var btnStartMonitoring: MaterialButton

    // Screen 03 뷰
    private lateinit var tvMonitorStatusBadge: TextView
    private lateinit var tvMonitorCurrentSoc: TextView
    private lateinit var tvMonitorTargetSoc: TextView
    private lateinit var progressMonitorSoc: ProgressBar
    private lateinit var tvMonitorMarginRemain: TextView
    private lateinit var tvMonitorHoursLeft: TextView
    private lateinit var tvMonitorLoadKw: TextView
    private lateinit var tvMonitorRouteDetails: TextView
    private lateinit var btnSimulateTick2: MaterialButton
    private lateinit var btnSimulateTick10: MaterialButton
    private lateinit var btnRefreshSession: MaterialButton
    private lateinit var btnOpenExtend: MaterialButton
    private lateinit var btnManualLockout: MaterialButton
    private lateinit var btnDemoWarn60: MaterialButton
    private lateinit var btnDemoWarn30: MaterialButton
    private lateinit var btnDemoFallback: MaterialButton

    // Screen 04 뷰
    private lateinit var tvLockoutBmsInfo: TextView
    private lateinit var tvLockoutRelayInfo: TextView
    private lateinit var tvLockoutHvacInfo: TextView
    private lateinit var tvLockoutSdkLog: TextView
    private lateinit var btnResetToStart: MaterialButton

    // 앱 상태 변수
    private var currentScreenIndex: Int = 0
    private var isPleosConnected: Boolean = false

    // 차량 / 센서 상태 (기본 IONIQ 5 Long Range)
    private var currentSocPercent: Double = 61.0
    private var userVehicleModel: String = "IONIQ 5 Long Range"
    private var userBatteryCapacity: Double = 77.4
    private var userCurbWeight: Int = 2010
    private var userAvgEfficiency: Double = 5.4

    // 출발/경로 선택 상태 (기본 출발지: 강릉 안반데기 차박지)
    private val originLat: Double = 37.6242
    private val originLng: Double = 128.7420
    private var selectedDestLat: Double? = null
    private var selectedDestLng: Double? = null
    private var selectedDepartureTime: String = "내일 오전 10:00"
    private var selectedPassengers: Int = 2

    // 예측 & 세션 상태
    private var lastPredictData: PredictData? = null
    private var selectedMarginMode: String = "NORMAL"
    private var activeSessionId: Int? = null
    private var activeSessionStatus: SessionStatusData? = null
    private var lastShownWarningLevel: Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Pleos App Market 규격: Back 버튼으로 앱을 finish()하지 않고 백그라운드로 이동
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (currentScreenIndex != 0) {
                    showScreen(0)
                } else {
                    moveTaskToBack(true)
                }
            }
        })

        bindViews()
        installDashboardScreen()
        setupListeners()
        initVehicleAndBackend()
        showScreen(0)
    }

    private fun bindViews() {
        tvConnectionBadge = findViewById(R.id.tvConnectionBadge)
        btnVehicleSettings = findViewById(R.id.btnVehicleSettings)
        btnHeaderSafety = findViewById(R.id.btnHeaderSafety)
        btnHeaderEnergy = findViewById(R.id.btnHeaderEnergy)
        btnHeaderMonitor = findViewById(R.id.btnHeaderMonitor)
        btnHeaderUtility = findViewById(R.id.btnHeaderUtility)
        cardFallbackBanner = findViewById(R.id.cardFallbackBanner)
        tvFallbackMessage = findViewById(R.id.tvFallbackMessage)

        layoutScreen01 = findViewById(R.id.layoutScreen01)
        layoutScreen02 = findViewById(R.id.layoutScreen02)
        layoutScreen03 = findViewById(R.id.layoutScreen03)
        layoutScreen04 = findViewById(R.id.layoutScreen04)

        etDestination = findViewById(R.id.etDestination)
        btnSearchPlaces = findViewById(R.id.btnSearchPlaces)
        layoutPlaceResults = findViewById(R.id.layoutPlaceResults)
        btnShortcutHome = findViewById(R.id.btnShortcutHome)
        btnShortcutCharger = findViewById(R.id.btnShortcutCharger)
        tvSelectedDestCoords = findViewById(R.id.tvSelectedDestCoords)
        btnDepMorning07 = findViewById(R.id.btnDepMorning07)
        btnDepMorning10 = findViewById(R.id.btnDepMorning10)
        btnDepNoon12 = findViewById(R.id.btnDepNoon12)
        btnPass1 = findViewById(R.id.btnPass1)
        btnPass2 = findViewById(R.id.btnPass2)
        btnPass4 = findViewById(R.id.btnPass4)
        etV2lLoadKw = findViewById(R.id.etV2lLoadKw)
        cbForceFallback = findViewById(R.id.cbForceFallback)
        tvScreen01SocInfo = findViewById(R.id.tvScreen01SocInfo)
        progressPredict = findViewById(R.id.progressPredict)
        btnAnalyzeRoute = findViewById(R.id.btnAnalyzeRoute)

        btnBackToScreen01 = findViewById(R.id.btnBackToScreen01)
        tvPredictRouteSummary = findViewById(R.id.tvPredictRouteSummary)
        tvPredictEnvBadges = findViewById(R.id.tvPredictEnvBadges)
        tvPredictBreakdown = findViewById(R.id.tvPredictBreakdown)
        cardModeSafe = findViewById(R.id.cardModeSafe)
        tvSafeReserve = findViewById(R.id.tvSafeReserve)
        tvSafeHours = findViewById(R.id.tvSafeHours)
        tvSafeDesc = findViewById(R.id.tvSafeDesc)
        cardModeNormal = findViewById(R.id.cardModeNormal)
        tvNormalReserve = findViewById(R.id.tvNormalReserve)
        tvNormalHours = findViewById(R.id.tvNormalHours)
        tvNormalDesc = findViewById(R.id.tvNormalDesc)
        cardModeMax = findViewById(R.id.cardModeMax)
        tvMaxReserve = findViewById(R.id.tvMaxReserve)
        tvMaxHours = findViewById(R.id.tvMaxHours)
        tvMaxDesc = findViewById(R.id.tvMaxDesc)
        tvInsufficientWarning = findViewById(R.id.tvInsufficientWarning)
        btnStartMonitoring = findViewById(R.id.btnStartMonitoring)

        tvMonitorStatusBadge = findViewById(R.id.tvMonitorStatusBadge)
        tvMonitorCurrentSoc = findViewById(R.id.tvMonitorCurrentSoc)
        tvMonitorTargetSoc = findViewById(R.id.tvMonitorTargetSoc)
        progressMonitorSoc = findViewById(R.id.progressMonitorSoc)
        tvMonitorMarginRemain = findViewById(R.id.tvMonitorMarginRemain)
        tvMonitorHoursLeft = findViewById(R.id.tvMonitorHoursLeft)
        tvMonitorLoadKw = findViewById(R.id.tvMonitorLoadKw)
        tvMonitorRouteDetails = findViewById(R.id.tvMonitorRouteDetails)
        btnSimulateTick2 = findViewById(R.id.btnSimulateTick2)
        btnSimulateTick10 = findViewById(R.id.btnSimulateTick10)
        btnRefreshSession = findViewById(R.id.btnRefreshSession)
        btnOpenExtend = findViewById(R.id.btnOpenExtend)
        btnManualLockout = findViewById(R.id.btnManualLockout)
        btnDemoWarn60 = findViewById(R.id.btnDemoWarn60)
        btnDemoWarn30 = findViewById(R.id.btnDemoWarn30)
        btnDemoFallback = findViewById(R.id.btnDemoFallback)

        tvLockoutBmsInfo = findViewById(R.id.tvLockoutBmsInfo)
        tvLockoutRelayInfo = findViewById(R.id.tvLockoutRelayInfo)
        tvLockoutHvacInfo = findViewById(R.id.tvLockoutHvacInfo)
        tvLockoutSdkLog = findViewById(R.id.tvLockoutSdkLog)
        btnResetToStart = findViewById(R.id.btnResetToStart)
    }

    private fun setupListeners() {
        btnVehicleSettings.setOnClickListener { showVehicleSettingsDialog() }
        btnHeaderSafety.setOnClickListener {
            if (lastPredictData == null) {
                lastPredictData = makeDemoPrediction(etDestination.text.toString().ifBlank { "우리 집" }, false)
                renderPredictResult(lastPredictData!!)
            }
            showScreen(2)
        }
        btnHeaderEnergy.setOnClickListener { showEnergyAnalysisDialog() }
        btnHeaderMonitor.setOnClickListener {
            if (activeSessionStatus != null) showScreen(3) else {
                if (lastPredictData == null) lastPredictData = makeDemoPrediction(etDestination.text.toString().ifBlank { "우리 집" }, false)
                if (lastPredictData?.recommendations?.get(selectedMarginMode) != null) startMonitoringSession()
            }
        }
        btnHeaderUtility.setOnClickListener { showScreen(4) }

        btnDemoWarn60.setOnClickListener { showWarn60mDialog() }
        btnDemoWarn30.setOnClickListener { showWarn30mDialog() }
        btnDemoFallback.setOnClickListener {
            renderFallbackBanner(true)
            AlertDialog.Builder(this)
                .setTitle("기본 안전 모드 보호 중")
                .setMessage("네트워크 연결이 불안정해도 현재 설정된 안전 예약 잔량으로 배터리를 보호합니다.")
                .setPositiveButton("확인", null)
                .show()
        }

        // Screen 01: 목적지 검색 & 숏컷
        btnSearchPlaces.setOnClickListener { searchPlacesFromBackend() }

        btnShortcutHome.setOnClickListener {
            etDestination.setText("우리 집")
            selectedDestLat = 37.4979
            selectedDestLng = 127.0276
            layoutPlaceResults.visibility = View.GONE
            tvSelectedDestCoords.text = "선택: 우리 집 (기본 등록 서울 강남 좌표)"
        }

        btnShortcutCharger.setOnClickListener { findNearestChargerShortcut() }

        // 출발 시각 선택
        btnDepMorning07.setOnClickListener { selectDepartureTime("내일 오전 07:00") }
        btnDepMorning10.setOnClickListener { selectDepartureTime("내일 오전 10:00") }
        btnDepNoon12.setOnClickListener { selectDepartureTime("내일 오후 12:00") }

        // 탑승 인원 선택
        btnPass1.setOnClickListener { selectPassengers(1) }
        btnPass2.setOnClickListener { selectPassengers(2) }
        btnPass4.setOnClickListener { selectPassengers(4) }

        // AI 예측 호출
        btnAnalyzeRoute.setOnClickListener { analyzeReturnRoute() }

        // Screen 02: 마진 선택 & 모니터링 시작
        btnBackToScreen01.setOnClickListener { showScreen(1) }
        cardModeSafe.setOnClickListener { updateSelectedMargin("SAFE") }
        cardModeNormal.setOnClickListener { updateSelectedMargin("NORMAL") }
        cardModeMax.setOnClickListener { updateSelectedMargin("MAX_USE") }
        btnStartMonitoring.setOnClickListener { startMonitoringSession() }

        // Screen 03: 시연 틱 & 연장 & 수동 차단
        btnSimulateTick2.setOnClickListener { simulateSessionTick(2.0) }
        btnSimulateTick10.setOnClickListener { simulateSessionTick(10.0) }
        btnRefreshSession.setOnClickListener { refreshActiveSession() }
        btnOpenExtend.setOnClickListener { showExtendConfirmDialog() }
        btnManualLockout.setOnClickListener { showManualLockoutConfirmDialog() }

        // Screen 04: 초기화
        btnResetToStart.setOnClickListener {
            activeSessionId = null
            lastShownWarningLevel = null
            cardFallbackBanner.visibility = View.GONE
            showScreen(0)
        }
    }

    /** Keeps the route form as the existing setup step while opening the app on a compact dashboard. */
    private fun installDashboardScreen() {
        val content = layoutScreen01.parent as? LinearLayout ?: return
        val dashboard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(4, 4, 4, 18)
            setBackgroundColor(Color.parseColor("#0B111E"))
        }
        layoutScreen00 = dashboard

        fun label(value: String, size: Float, color: String, bold: Boolean = false) = TextView(this).apply {
            text = value
            textSize = size
            setTextColor(Color.parseColor(color))
            if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        fun action(text: String, color: String, onClick: () -> Unit) = MaterialButton(this).apply {
            this.text = text
            setTextColor(Color.WHITE)
            backgroundTintList = ColorStateList.valueOf(Color.parseColor(color))
            cornerRadius = 14
            setOnClickListener { onClick() }
        }
        fun card(title: String, value: String): MaterialCardView {
            val inner = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(18, 16, 18, 16)
                addView(label(title, 13f, "#A9BBB3"))
                val valueView = label(value, 24f, "#F1F6F2", true)
                if (title == "현재 배터리") tvDashboardSoc = valueView else tvDashboardReserve = valueView
                addView(valueView)
            }
            return MaterialCardView(this).apply {
                radius = 18f
                setCardBackgroundColor(Color.parseColor("#182422"))
                strokeColor = Color.parseColor("#33433D")
                strokeWidth = 1
                addView(inner)
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = 8 }
            }
        }

        dashboard.addView(label("V-Locker", 13f, "#6EDAC1", true))
        dashboard.addView(label("오늘의 캠핑, 안전하게 준비하세요", 25f, "#F1F6F2", true).apply {
            setPadding(0, 10, 0, 4)
        })
        dashboard.addView(label("현재 배터리와 귀가 계획을 한눈에 확인하세요.", 14f, "#A9BBB3").apply {
            setPadding(0, 0, 0, 18)
        })

        val metrics = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        metrics.addView(card("현재 배터리", String.format(Locale.US, "%.0f%%", currentSocPercent)))
        metrics.addView(card("귀가 예약 잔량", "계획 없음").apply { layoutParams = LinearLayout.LayoutParams(0, -2, 1f) })
        dashboard.addView(metrics)

        btnDashboardDestination = action("⌕   목적지 검색   ·   ${etDestination.text}", "#22322F") {
            showDestinationSearchDialog()
        }.apply { setPadding(8, 0, 8, 0) }
        dashboard.addView(btnDashboardDestination)
        dashboard.addView(action("귀가 예약 잔량 선택하기", "#087F70") {
            if (lastPredictData != null) showScreen(2) else analyzeReturnRoute()
        }.apply { layoutParams = LinearLayout.LayoutParams(-1, 58).apply { topMargin = 10; bottomMargin = 12 } })

        content.addView(dashboard, content.indexOfChild(layoutScreen01))

        layoutScreen05 = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(4, 8, 4, 18)
            addView(label("에너지 분석", 25f, "#F1F6F2", true))
            addView(label("귀가 주행과 V2L 사용에 필요한 전력을 확인하세요.", 14f, "#A9BBB3").apply {
                setPadding(0, 6, 0, 16)
            })
            tvEnergyAnalysisSummary = label("경로 분석 후 에너지 사용량을 표시합니다.", 16f, "#E2E8F0")
            setPadding(4, 8, 4, 18)
            addView(tvEnergyAnalysisSummary)
            addView(action("메인 대시보드", "#087F70") { showScreen(0) })
        }
        content.addView(layoutScreen05)
    }

    private fun showDestinationSearchDialog() {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 12, 28, 8)
        }
        val query = EditText(this).apply {
            setSingleLine(true)
            hint = "목적지를 검색하세요"
            setText(etDestination.text)
        }
        val results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val shortcuts = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val home = MaterialButton(this).apply { text = "우리 집" }
        val charger = MaterialButton(this).apply { text = "가까운 급속 충전소" }
        shortcuts.addView(home, LinearLayout.LayoutParams(0, -2, 1f))
        shortcuts.addView(charger, LinearLayout.LayoutParams(0, -2, 1f))
        panel.addView(query)
        panel.addView(shortcuts)
        panel.addView(results)
        val dialog = AlertDialog.Builder(this)
            .setTitle("목적지 검색")
            .setView(panel)
            .setNegativeButton("닫기", null)
            .create()

        fun choose(name: String, lat: Double?, lng: Double?) {
            etDestination.setText(name)
            selectedDestLat = lat
            selectedDestLng = lng
            lastPredictData = null
            tvSelectedDestCoords.text = "선택: $name"
            if (::tvDashboardReserve.isInitialized) tvDashboardReserve.text = "계획 없음"
            if (::btnDashboardDestination.isInitialized) btnDashboardDestination.text = "⌕   목적지 검색   ·   $name"
            dialog.dismiss()
        }
        home.setOnClickListener { choose("우리 집", 37.4979, 127.0276) }
        charger.setOnClickListener {
            choose("가까운 급속 충전소", 37.5665, 126.9780)
        }
        dialog.setButton(AlertDialog.BUTTON_POSITIVE, "검색", null)
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val keyword = query.text.toString().trim()
            if (keyword.isEmpty()) return@setOnClickListener
            results.removeAllViews()
            listOf(
                PlaceItem(keyword, "검색한 목적지 · 시연 데이터", null, 37.5665, 126.9780),
                PlaceItem("${keyword} 근처", "추천 목적지 · 시연 데이터", null, 37.5700, 126.9820)
            ).forEach { place ->
                val row = TextView(this@MainActivity).apply {
                    text = "📍 ${place.name}\n${place.address}"
                    textSize = 14f
                    setTextColor(Color.WHITE)
                    setPadding(12, 14, 12, 14)
                    setOnClickListener { choose(place.name, place.lat, place.lng) }
                }
                results.addView(row)
            }
        }
    }

    private fun showEnergyAnalysisDialog() {
        val breakdown = lastPredictData?.breakdown
        tvEnergyAnalysisSummary.text = if (breakdown == null) "경로 분석 후 에너지 사용량을 표시합니다." else String.format(
            Locale.US,
            "목적지: %s\n\n귀가 주행 전력  %.2f kWh\n고도 회생 전력  %.2f kWh\n난방 예상 전력  %.2f kWh\n귀가 필수 잔량  %.1f%%",
            lastPredictData?.destination?.name.orEmpty(),
            breakdown.baseDrivingKwh, breakdown.elevationKwh, breakdown.coldStartHvacSpikeKwh,
            breakdown.pureReturnSocPercent
        )
        showScreen(5)
    }

    private fun initVehicleAndBackend() {
        vehicleManager = PleosVehicleManager(this)
        vehicleManager.onLiveSocChanged = { liveSoc ->
            currentSocPercent = liveSoc
            updateVehicleSummaryUI()
        }

        vehicleManager.initialize { connected, _ ->
            isPleosConnected = connected
            vehicleManager.readVehicleTelemetry(
                fallbackSoc = currentSocPercent,
                fallbackEfficiency = userAvgEfficiency
            ) { soc, eff, _ ->
                currentSocPercent = soc
                userAvgEfficiency = eff
                updateVehicleSummaryUI()
                updateVehicleSummaryUI("로컬 시연 데이터")
            }
        }
    }

    private fun updateVehicleSummaryUI(backendNote: String? = null) {
        val modeLabel = if (isPleosConnected) "Pleos SDK" else "시뮬 모드"
        val socFmt = String.format(Locale.US, "%.1f", currentSocPercent)
        val extra = backendNote?.let { " · $it" } ?: ""
        tvConnectionBadge.text = "$modeLabel · SoC $socFmt%$extra"
        tvScreen01SocInfo.text = String.format(
            Locale.US,
            "차량(%s): 배터리 %.1f%% · 용량 %.1f kWh · 전비 %.1f km/kWh",
            userVehicleModel, currentSocPercent, userBatteryCapacity, userAvgEfficiency
        )
        if (::tvDashboardSoc.isInitialized) {
            tvDashboardSoc.text = String.format(Locale.US, "%.0f%%", currentSocPercent)
        }
        if (::tvDashboardReserve.isInitialized) {
            tvDashboardReserve.text = lastPredictData?.recommendations?.get(selectedMarginMode)?.let {
                String.format(Locale.US, "%.1f%%", it.reserveSocPercent)
            } ?: "계획 없음"
        }
        if (::btnDashboardDestination.isInitialized) {
            btnDashboardDestination.text = "⌕   목적지 검색   ·   ${etDestination.text}"
        }
    }

    // =========================================================
    // Screen 01 동작
    // =========================================================
    private fun searchPlacesFromBackend() {
        val keyword = etDestination.text.toString().trim()
        if (keyword.isEmpty()) {
            toast("검색할 목적지를 입력해 주세요.")
            return
        }

        renderPlaceResults(listOf(
            PlaceItem(keyword, "검색한 목적지 · 시연 데이터", null, 37.5665, 126.9780),
            PlaceItem("${keyword} 근처", "추천 목적지 · 시연 데이터", null, 37.5700, 126.9820)
        ))
    }

    private fun renderPlaceResults(places: List<PlaceItem>) {
        layoutPlaceResults.removeAllViews()
        if (places.isEmpty()) {
            layoutPlaceResults.visibility = View.GONE
            return
        }

        layoutPlaceResults.visibility = View.VISIBLE
        for (place in places) {
            val row = TextView(this).apply {
                text = "📍 ${place.name}\n     ${place.address}"
                setTextColor(Color.parseColor("#E2E8F0"))
                textSize = 13f
                setPadding(20, 18, 20, 18)
                setOnClickListener {
                    etDestination.setText(place.name)
                    selectedDestLat = place.lat
                    selectedDestLng = place.lng
                    layoutPlaceResults.visibility = View.GONE
                    tvSelectedDestCoords.text = String.format(
                        Locale.US,
                        "선택: %s (%.4f, %.4f)",
                        place.name, place.lat, place.lng
                    )
                }
            }
            layoutPlaceResults.addView(row)
        }
    }

    private fun findNearestChargerShortcut() {
        etDestination.setText("가까운 급속 충전소")
        selectedDestLat = 37.5665
        selectedDestLng = 126.9780
        layoutPlaceResults.visibility = View.GONE
        tvSelectedDestCoords.text = "선택: 가까운 급속 충전소 · 시연용 위치"
    }

    private fun selectDepartureTime(label: String) {
        selectedDepartureTime = label
        val active = ColorStateList.valueOf(Color.parseColor("#0284C7"))
        val inactive = ColorStateList.valueOf(Color.parseColor("#1E293B"))
        btnDepMorning07.backgroundTintList = if (label == "내일 오전 07:00") active else inactive
        btnDepMorning10.backgroundTintList = if (label == "내일 오전 10:00") active else inactive
        btnDepNoon12.backgroundTintList = if (label == "내일 오후 12:00") active else inactive
    }

    private fun selectPassengers(count: Int) {
        selectedPassengers = count
        val active = ColorStateList.valueOf(Color.parseColor("#0284C7"))
        val inactive = ColorStateList.valueOf(Color.parseColor("#1E293B"))
        btnPass1.backgroundTintList = if (count == 1) active else inactive
        btnPass2.backgroundTintList = if (count == 2) active else inactive
        btnPass4.backgroundTintList = if (count == 4) active else inactive
    }

    private fun readLoadKw(): Double {
        return etV2lLoadKw.text.toString().toDoubleOrNull()?.coerceIn(0.2, 3.6) ?: 1.2
    }

    private fun analyzeReturnRoute() {
        val destName = etDestination.text.toString().trim().ifEmpty { "강남역" }
        val data = makeDemoPrediction(destName, cbForceFallback.isChecked)
        lastPredictData = data
        renderFallbackBanner(data.isFallback)
        renderPredictResult(data)
        showScreen(2)
    }

    private fun makeDemoPrediction(destination: String, fallback: Boolean): PredictData {
        val distanceKm = when {
            destination.contains("충전소") -> 18.0
            destination.contains("집") -> 86.0
            else -> 42.0
        }
        val routeKwh = distanceKm / userAvgEfficiency
        val requiredKwh = routeKwh + 1.5
        val requiredSoc = requiredKwh / userBatteryCapacity * 100.0
        fun option(mode: String, label: String, reserve: Double, multiplier: Double, hours: Double, description: String) =
            MarginRecommendation(
                mode = mode, modeNameKr = label, safetyMultiplier = multiplier,
                reserveSocPercent = reserve, requiredKwh = requiredKwh,
                v2lUsableKwh = ((currentSocPercent - reserve).coerceAtLeast(0.0) * userBatteryCapacity / 100.0),
                v2lUsableHours = hours, isSufficient = currentSocPercent >= reserve,
                description = description
            )
        val recommendations = mapOf(
            "SAFE" to option("SAFE", "안심", 34.0, 1.5, 5.1, "배터리 잔여량을 넉넉히 남겨 귀가 여유를 확보합니다."),
            "NORMAL" to option("NORMAL", "균형", 28.0, 1.25, 6.7, "귀가 여유와 V2L 사용 시간을 균형 있게 맞춥니다."),
            "MAX_USE" to option("MAX_USE", "최대 사용", 24.0, 1.1, 7.8, "귀가에 필요한 잔량을 남기고 V2L을 더 오래 사용합니다.")
        )
        return PredictData(
            destination = DestinationInfo(destination, originLat, originLng,
                selectedDestLat ?: 37.5665, selectedDestLng ?: 126.9780),
            vehicle = VehicleInfo(userVehicleModel, userBatteryCapacity, userAvgEfficiency, userCurbWeight.toDouble()),
            weather = WeatherInfo(-5.0, isFallback = fallback, source = "시연 데이터", description = "겨울철 예시"),
            navigation = NavigationInfo(distanceKm, 640.0, (distanceKm / 45.0 * 60).toInt(), fallback,
                "시연 데이터", "경로 분석 예시"),
            isFallback = fallback,
            breakdown = BreakdownInfo(routeKwh, -2.4, 1.15, 10.0, 1.5, requiredKwh, requiredSoc),
            recommendations = recommendations
        )
    }

    // =========================================================
    // Screen 02 동작
    // =========================================================
    private fun renderFallbackBanner(isFallback: Boolean) {
        if (isFallback) {
            cardFallbackBanner.visibility = View.VISIBLE
            tvFallbackMessage.text =
                "⚠️ 네트워크 연결이 불안정하여 기본 안전 모드(-5.0℃, +640m 기준)로 자동 전환되어 귀가 배터리를 안전하게 보호 중입니다."
        } else {
            cardFallbackBanner.visibility = View.GONE
        }
    }

    private fun renderPredictResult(data: PredictData) {
        val nav = data.navigation
        val weather = data.weather
        val bd = data.breakdown
        val durText = nav.estimatedDurationMin?.let { "약 ${it}분" } ?: ""

        tvPredictRouteSummary.text = String.format(
            Locale.US,
            "%s · %.1f km %s",
            data.destination.name, nav.distanceKm, durText
        )
        tvPredictEnvBadges.text = String.format(
            Locale.US,
            "%s · 예상 기온 %.1f℃ (효율 저하 -%.1f%%) · 상승고도 +%.0fm",
            selectedDepartureTime, weather.temperature, bd.efficiencyLossPercent, nav.elevationGainM
        )
        tvPredictBreakdown.text = String.format(
            Locale.US,
            "기본 주행 %.2f kWh + 회생고도 %.2f kWh + 콜드스타트 %.2f kWh\n→ 순수 귀가 필수 전력: %.2f kWh (약 %.1f%%)",
            bd.baseDrivingKwh, bd.elevationKwh, bd.coldStartHvacSpikeKwh,
            bd.pureReturnKwh, bd.pureReturnSocPercent
        )

        bindMarginCard(data.recommendations["SAFE"], tvSafeReserve, tvSafeHours, tvSafeDesc)
        bindMarginCard(data.recommendations["NORMAL"], tvNormalReserve, tvNormalHours, tvNormalDesc)
        bindMarginCard(data.recommendations["MAX_USE"], tvMaxReserve, tvMaxHours, tvMaxDesc)

        updateSelectedMargin("NORMAL")
    }

    private fun bindMarginCard(
        rec: MarginRecommendation?,
        tvReserve: TextView,
        tvHours: TextView,
        tvDesc: TextView
    ) {
        if (rec == null) return
        tvReserve.text = String.format(Locale.US, "예약 %.1f%%", rec.reserveSocPercent)
        if (rec.isSufficient) {
            tvHours.text = String.format(
                Locale.US,
                "앞으로 약 %.1f시간 사용 가능 (여유 %.2f kWh)",
                rec.v2lUsableHours, rec.v2lUsableKwh
            )
            tvHours.setTextColor(Color.parseColor("#34D399"))
        } else {
            tvHours.text = "현재 배터리 부족 · 즉시 충전 또는 최대사용 선택 필요"
            tvHours.setTextColor(Color.parseColor("#F87171"))
        }
        tvDesc.text = rec.description
    }

    private fun updateSelectedMargin(mode: String) {
        selectedMarginMode = mode
        highlightMarginCard(cardModeSafe, mode == "SAFE")
        highlightMarginCard(cardModeNormal, mode == "NORMAL")
        highlightMarginCard(cardModeMax, mode == "MAX_USE")

        val selectedRec = lastPredictData?.recommendations?.get(mode)
        tvInsufficientWarning.visibility =
            if (selectedRec != null && !selectedRec.isSufficient) View.VISIBLE else View.GONE
        if (::tvDashboardReserve.isInitialized) {
            tvDashboardReserve.text = selectedRec?.let { String.format(Locale.US, "%.1f%%", it.reserveSocPercent) } ?: "계획 없음"
        }
    }

    private fun highlightMarginCard(card: MaterialCardView, selected: Boolean) {
        if (selected) {
            card.setCardBackgroundColor(Color.parseColor("#162844"))
            card.strokeColor = Color.parseColor("#38BDF8")
        } else {
            card.setCardBackgroundColor(Color.parseColor("#141D2F"))
            card.strokeColor = Color.parseColor("#26334D")
        }
    }

    private fun startMonitoringSession() {
        val predict = lastPredictData ?: return
        val rec = predict.recommendations[selectedMarginMode] ?: return
        activeSessionId = (activeSessionId ?: 0) + 1
        lastShownWarningLevel = null
        activeSessionStatus = SessionStatusData(
            sessionId = activeSessionId!!,
            destinationName = predict.destination.name,
            currentSoc = currentSocPercent,
            targetSoc = rec.reserveSocPercent,
            remainingMarginPct = currentSocPercent - rec.reserveSocPercent,
            currentV2lLoadKw = readLoadKw(),
            estimatedHoursLeft = rec.v2lUsableHours,
            v2lStatus = "ACTIVE",
            warningLevel = null,
            relayConnected = true,
            utilityModeActive = false,
            isFallback = predict.isFallback,
            destinationSummary = DestinationSummary(
                predict.destination.name, selectedDepartureTime, predict.navigation.distanceKm,
                predict.navigation.elevationGainM, predict.weather.temperature,
                when (selectedMarginMode) { "SAFE" -> "안심"; "MAX_USE" -> "최대 사용"; else -> "균형" }
            )
        )
        renderSessionDashboard(activeSessionStatus!!)
        showScreen(3)
    }

    // =========================================================
    // Screen 03 모니터링 & 경고 & 연장
    // =========================================================
    private fun simulateSessionTick(decrementPct: Double) {
        val session = activeSessionStatus ?: return
        currentSocPercent = (session.currentSoc - decrementPct).coerceAtLeast(session.targetSoc)
        val margin = currentSocPercent - session.targetSoc
        val status = when {
            margin <= 0.5 -> "LOCKED_OUT"
            margin <= 2.0 -> "WARN_30M"
            margin <= 5.0 -> "WARN_60M"
            else -> "ACTIVE"
        }
        handleSessionTransition(session.copy(
            currentSoc = currentSocPercent,
            remainingMarginPct = margin,
            estimatedHoursLeft = (margin / 5.0).coerceAtLeast(0.0),
            currentV2lLoadKw = readLoadKw(),
            v2lStatus = status,
            warningLevel = when (status) { "WARN_30M" -> 30; "WARN_60M" -> 60; else -> null }
        ))
    }

    private fun refreshActiveSession() {
        activeSessionStatus?.let { renderSessionDashboard(it) }
    }

    private fun handleSessionTransition(session: SessionStatusData) {
        activeSessionStatus = session
        currentSocPercent = session.currentSoc
        updateVehicleSummaryUI()
        renderSessionDashboard(session)

        when (session.v2lStatus) {
            "LOCKED_OUT" -> triggerFinalLockoutSequence(session.sessionId)
            "WARN_30M" -> {
                if (lastShownWarningLevel != 30) {
                    lastShownWarningLevel = 30
                    showWarn30mDialog()
                }
            }
            "WARN_60M" -> {
                if (lastShownWarningLevel == null) {
                    lastShownWarningLevel = 60
                    showWarn60mDialog()
                }
            }
        }
    }

    private fun renderSessionDashboard(session: SessionStatusData) {
        renderFallbackBanner(session.isFallback || (lastPredictData?.isFallback == true))

        when (session.v2lStatus) {
            "WARN_30M" -> {
                tvMonitorStatusBadge.text = "🚨 차단 30분 전 긴급 예고 (WARN_30M)"
                tvMonitorStatusBadge.setBackgroundColor(Color.parseColor("#7F1D1D"))
                tvMonitorStatusBadge.setTextColor(Color.parseColor("#FCA5A5"))
                progressMonitorSoc.progressTintList = ColorStateList.valueOf(Color.parseColor("#EF4444"))
            }
            "WARN_60M" -> {
                tvMonitorStatusBadge.text = "⚠️ 차단 1시간 전 안내 (WARN_60M)"
                tvMonitorStatusBadge.setBackgroundColor(Color.parseColor("#78350F"))
                tvMonitorStatusBadge.setTextColor(Color.parseColor("#FDE68A"))
                progressMonitorSoc.progressTintList = ColorStateList.valueOf(Color.parseColor("#F59E0B"))
            }
            else -> {
                tvMonitorStatusBadge.text = "● 현재 V2L 안전 사용 중 (ACTIVE)"
                tvMonitorStatusBadge.setBackgroundColor(Color.parseColor("#064E3B"))
                tvMonitorStatusBadge.setTextColor(Color.parseColor("#6EE7B7"))
                progressMonitorSoc.progressTintList = ColorStateList.valueOf(Color.parseColor("#10B981"))
            }
        }

        tvMonitorCurrentSoc.text = String.format(Locale.US, "%.1f%%", session.currentSoc)
        tvMonitorTargetSoc.text = String.format(Locale.US, "/ 안전 예약 잔량 %.1f%%", session.targetSoc)
        progressMonitorSoc.progress = session.currentSoc.toInt().coerceIn(0, 100)
        tvMonitorMarginRemain.text = String.format(Locale.US, "차단까지 남은 여유: %.1f%%p", session.remainingMarginPct)
        tvMonitorHoursLeft.text = String.format(Locale.US, "남은 가용 시간: 약 %.1f시간", session.estimatedHoursLeft)
        tvMonitorLoadKw.text = String.format(Locale.US, "현재 V2L 소비 전력: %.1f kW (최대 3.6 kW)", session.currentV2lLoadKw)

        val summary = session.destinationSummary
        val pred = lastPredictData
        tvMonitorRouteDetails.text = if (summary != null) {
            String.format(
                Locale.US,
                "목적지: %s · 출발: %s\n거리 %.1f km · 상승고도 +%.0fm · 기온 %.1f℃ · 모드: %s",
                summary.destination, summary.departure,
                summary.distanceKm, summary.elevationGainM, summary.tempCelsius, summary.marginMode
            )
        } else if (pred != null) {
            String.format(
                Locale.US,
                "목적지: %s · 출발: %s\n거리 %.1f km · 상승고도 +%.0fm · 기온 %.1f℃ · 모드: %s",
                session.destinationName ?: pred.destination.name, selectedDepartureTime,
                pred.navigation.distanceKm, pred.navigation.elevationGainM,
                pred.weather.temperature, selectedMarginMode
            )
        } else {
            "목적지: ${session.destinationName ?: "귀가 경로"} · 모드: $selectedMarginMode"
        }
    }

    private fun showWarn60mDialog() {
        AlertDialog.Builder(this)
            .setTitle("⚠️ 차단 1시간 전 안내")
            .setMessage("귀가 예약 잔량에 도달하기까지 약 1시간 남았습니다.\n차량 배터리와 남은 사용 시간을 확인해 주세요.")
            .setPositiveButton("확인", null)
            .show()
    }

    private fun showWarn30mDialog() {
        AlertDialog.Builder(this)
            .setTitle("🚨 차단 30분 전 긴급 알림")
            .setMessage("곧 목표 예약 잔량에 도달하여 V2L 전원이 안전 차단됩니다.\n인근 급속 충전소 경유로 마진을 재계산해 사용을 연장하시겠습니까?")
            .setPositiveButton("사용 연장 (충전소 탐색)") { _, _ -> showExtendConfirmDialog() }
            .setNegativeButton("확인", null)
            .show()
    }

    private fun showExtendConfirmDialog() {
        val session = activeSessionStatus ?: return
        AlertDialog.Builder(this)
            .setTitle("🔌 V2L 사용 연장 (인근 급속충전소 재계산)")
            .setMessage("인근 급속 충전소 경로를 기준으로 안전 마진을 다시 계산하여 V2L 가용 시간을 연장할까요?")
            .setPositiveButton("연장 적용") { _, _ ->
                val newTarget = (session.targetSoc - 4.0).coerceAtLeast(18.0)
                activeSessionStatus = session.copy(
                    destinationName = "가까운 급속 충전소 경유",
                    destinationSummary = session.destinationSummary?.copy(
                        destination = "가까운 급속 충전소 경유",
                        distanceKm = 18.0,
                        marginMode = "충전소 재계산"
                    ),
                    targetSoc = newTarget,
                    remainingMarginPct = session.currentSoc - newTarget,
                    estimatedHoursLeft = session.estimatedHoursLeft + 2.0
                )
                lastShownWarningLevel = null
                activeSessionStatus?.let { renderSessionDashboard(it) }
                toast("시연 데이터 기준으로 목표 잔량을 ${newTarget}%로 조정했습니다.")
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun showManualLockoutConfirmDialog() {
        val sessionId = activeSessionId ?: return
        AlertDialog.Builder(this)
            .setTitle("V2L 차단 확인 팝업 - 수동")
            .setMessage("V2L 전원을 차단하고 귀가 예약 잔량을 보존할까요?\n차단 후에는 차량 유틸리티 화면에서 BMS 잔량과 난방 상태를 확인할 수 있습니다.")
            .setNegativeButton("취소 (계속 사용)", null)
            .setPositiveButton("즉시 전원 차단") { _, _ -> triggerFinalLockoutSequence(sessionId) }
            .show()
    }

    // =========================================================
    // Screen 04 락아웃 & Pleos HVAC 21℃ 가동
    // =========================================================
    private fun triggerFinalLockoutSequence(sessionId: Int) {
        val session = activeSessionStatus ?: return
        val lockout = LockoutData(
            sessionId = sessionId,
            v2lStatus = "LOCKED_OUT",
            relayConnected = false,
            utilityModeActive = true,
            cabinTargetTempCelsius = 21.0,
            bmsPreservedSoc = session.targetSoc,
            returnDistanceGuaranteedKm = session.targetSoc * userAvgEfficiency
        )
        activeSessionStatus = session.copy(v2lStatus = "LOCKED_OUT", utilityModeActive = true)
        executeHardwareAndShowScreen04(lockout)
    }

    private fun executeHardwareAndShowScreen04(lockout: LockoutData) {
        tvLockoutBmsInfo.text = String.format(
            Locale.US,
            "시연 잔량 %.1f%% 보존 · 귀가 가능 거리 예시: 약 %.1f km",
            lockout.bmsPreservedSoc, lockout.returnDistanceGuaranteedKm
        )
        tvLockoutRelayInfo.text = "시연 상태: V2L 전원 차단 완료"
        tvLockoutHvacInfo.text = String.format(
            Locale.US, "유틸리티 모드 시연 · 목표 실내 온도 %.1f℃", lockout.cabinTargetTempCelsius
        )
        tvLockoutSdkLog.text = "로컬 시연 데이터입니다. 차량 제어 API 연결 후 실제 상태로 교체합니다."
        showScreen(4)
    }

    // =========================================================
    // 차량 & 서버 URL 설정 다이얼로그
    // =========================================================
    private fun showVehicleSettingsDialog() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 8)
        }

        fun addField(label: String, initialValue: String, numeric: Boolean = false): EditText {
            container.addView(TextView(this).apply {
                text = label
                textSize = 12f
                setPadding(0, 12, 0, 4)
            })
            return EditText(this).apply {
                setText(initialValue)
                if (numeric) {
                    inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                }
                container.addView(this)
            }
        }

        val etModel = addField("차량 모델", userVehicleModel)
        val etCap = addField("배터리 용량 (kWh)", userBatteryCapacity.toString(), numeric = true)
        val etWeight = addField("공차중량 (kg)", userCurbWeight.toString(), numeric = true)
        val etEff = addField("평소 전비 (km/kWh)", userAvgEfficiency.toString(), numeric = true)

        AlertDialog.Builder(this)
            .setTitle("⚙️ 차량 시연 정보 설정")
            .setView(container)
            .setNegativeButton("취소", null)
            .setPositiveButton("저장") { _, _ ->
                userVehicleModel = etModel.text.toString().trim().ifEmpty { userVehicleModel }
                userBatteryCapacity = etCap.text.toString().toDoubleOrNull() ?: userBatteryCapacity
                userCurbWeight = etWeight.text.toString().toDoubleOrNull()?.toInt() ?: userCurbWeight
                userAvgEfficiency = etEff.text.toString().toDoubleOrNull() ?: userAvgEfficiency
                updateVehicleSummaryUI("로컬 시연 데이터")
                toast("차량 시연 정보가 저장되었습니다.")
            }
            .show()
    }

    private fun showScreen(step: Int) {
        currentScreenIndex = step
        layoutScreen00.visibility = if (step == 0) View.VISIBLE else View.GONE
        layoutScreen01.visibility = if (step == 1) View.VISIBLE else View.GONE
        layoutScreen02.visibility = if (step == 2) View.VISIBLE else View.GONE
        layoutScreen03.visibility = if (step == 3) View.VISIBLE else View.GONE
        layoutScreen04.visibility = if (step == 4) View.VISIBLE else View.GONE
        layoutScreen05.visibility = if (step == 5) View.VISIBLE else View.GONE
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        vehicleManager.release()
        super.onDestroy()
    }
}

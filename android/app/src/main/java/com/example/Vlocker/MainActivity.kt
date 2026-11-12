package com.example.Vlocker

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.speech.tts.TextToSpeech
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
import androidx.lifecycle.lifecycleScope
import com.example.Vlocker.network.ApiClient
import com.example.Vlocker.network.CreateSessionRequest
import com.example.Vlocker.network.ExtendRequest
import com.example.Vlocker.network.FavoriteCreateRequest
import com.example.Vlocker.network.FavoriteItem
import com.example.Vlocker.network.LockoutData
import com.example.Vlocker.network.MarginRecommendation
import com.example.Vlocker.network.PlaceItem
import com.example.Vlocker.network.PredictData
import com.example.Vlocker.network.PredictRequest
import com.example.Vlocker.network.SessionStatusData
import com.example.Vlocker.network.SocSyncRequest
import com.example.Vlocker.network.TickRequest
import com.example.Vlocker.network.UserVehicleData
import com.example.Vlocker.network.UserVehicleUpdateRequest
import com.example.Vlocker.vehicle.PleosVehicleManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs

class MainActivity : AppCompatActivity() {

    private lateinit var vehicleManager: PleosVehicleManager

    // 상단 바 & 배너
    private lateinit var tvConnectionBadge: TextView
    private lateinit var btnVehicleSettings: MaterialButton
    private lateinit var btnHeaderSafety: MaterialButton
    private lateinit var btnHeaderEnergy: MaterialButton
    private lateinit var btnHeaderMonitor: MaterialButton
    private lateinit var btnHeaderUtility: MaterialButton
    private lateinit var cardFallbackBanner: MaterialCardView
    private lateinit var tvFallbackMessage: TextView

    // 화면 컨테이너 (Screen 00 ~ 05)
    private lateinit var layoutScreen00: LinearLayout
    private lateinit var tvDashboardSoc: TextView
    private lateinit var tvDashboardReserve: TextView
    private lateinit var btnDashboardDestination: MaterialButton
    private lateinit var btnDashboardAnalyze: MaterialButton
    private lateinit var layoutScreen01: LinearLayout
    private lateinit var layoutScreen02: LinearLayout
    private lateinit var layoutScreen03: LinearLayout
    private lateinit var layoutScreen04: LinearLayout
    private lateinit var layoutScreen05: LinearLayout
    private lateinit var tvEnergyAnalysisSummary: TextView

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
    private var currentServerUrl: String = "http://10.0.2.2:8000/"
    private var backendStatusNote: String = "백엔드 연결 확인 중"

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
    private var selectedDestAddress: String? = null
    private var selectedDestLabel: String? = null
    private var selectedDepartureTime: String = "내일 오전 10:00"
    private var selectedPassengers: Int = 2

    // 예측 & 세션 상태
    private var lastPredictData: PredictData? = null
    private var lastPredictQueryName: String? = null
    private var lastPredictLoadKw: Double? = null
    private var selectedMarginMode: String = "NORMAL"
    private var activeSessionId: Int? = null
    private var activeSessionStatus: SessionStatusData? = null
    private var lastShownWarningLevel: Int? = null
    private var isLockoutTriggered: Boolean = false
    private var isSyncingLiveSoc: Boolean = false
    private var lastSyncedLiveSoc: Double? = null
    private var savedHomeFavorite: FavoriteItem? = null
    private var isPredicting: Boolean = false

    // 차량 스피커 TTS 안내
    private var textToSpeech: TextToSpeech? = null
    private var isTtsReady: Boolean = false

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
        initTextToSpeech()
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
        tvConnectionBadge.setOnClickListener { fetchUserProfileFromBackend(retryOnStartup = false) }
        btnVehicleSettings.setOnClickListener { showVehicleSettingsDialog() }

        btnHeaderSafety.setOnClickListener {
            if (hasFreshPrediction()) {
                showScreen(2)
            } else {
                analyzeReturnRoute(targetScreen = 2)
            }
        }
        btnHeaderEnergy.setOnClickListener {
            if (hasFreshPrediction()) {
                showEnergyAnalysisDialog()
            } else {
                analyzeReturnRoute(targetScreen = 5)
            }
        }
        btnHeaderMonitor.setOnClickListener {
            if (activeSessionId != null && !isLockoutTriggered) {
                showScreen(3)
                refreshActiveSession()
            } else if (hasFreshPrediction() && lastPredictData?.recommendations?.get(selectedMarginMode) != null) {
                startMonitoringSession()
            } else {
                analyzeReturnRoute(targetScreen = 3, autoStartMonitoring = true)
            }
        }
        btnHeaderUtility.setOnClickListener {
            if (!isLockoutTriggered) {
                renderUtilityStandbyState()
            }
            showScreen(4)
        }

        btnDemoWarn60.setOnClickListener {
            showWarn60mDialog()
            speakSafetyNotice("배터리 보호를 위해 약 1시간 후 V2L 전원이 종료됩니다.")
        }
        btnDemoWarn30.setOnClickListener {
            showWarn30mDialog()
            speakSafetyNotice("긴급 알림. 약 30분 후 V2L 전원이 차단됩니다. 필요하면 사용 연장을 선택해 주세요.")
        }
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

        btnShortcutHome.setOnClickListener { applyHomeFavoriteSelection() }
        btnShortcutHome.setOnLongClickListener {
            saveCurrentDestinationAsHome()
            true
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
        cbForceFallback.setOnCheckedChangeListener { _, _ -> invalidatePredictCache() }

        // AI 예측 호출
        btnAnalyzeRoute.setOnClickListener { analyzeReturnRoute(targetScreen = 2) }

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
            activeSessionStatus = null
            invalidatePredictCache()
            lastShownWarningLevel = null
            isLockoutTriggered = false
            isSyncingLiveSoc = false
            lastSyncedLiveSoc = null
            cardFallbackBanner.visibility = View.GONE
            updateVehicleSummaryUI()
            showScreen(0)
        }
    }

    private fun initTextToSpeech() {
        runCatching {
            textToSpeech = TextToSpeech(this) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    val langResult = textToSpeech?.setLanguage(Locale.KOREAN)
                    isTtsReady = langResult != TextToSpeech.LANG_MISSING_DATA &&
                        langResult != TextToSpeech.LANG_NOT_SUPPORTED
                }
            }
        }
    }

    private fun speakSafetyNotice(message: String) {
        if (!isTtsReady) return
        runCatching {
            textToSpeech?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "vlocker_alert")
        }
    }

    /** Keeps the route form as the existing setup step while opening the app on a compact dashboard. */
    private fun installDashboardScreen() {
        val content = layoutScreen01.parent as? LinearLayout ?: return
        fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
        val dashboard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(2), dp(2), dp(2), dp(12))
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
            cornerRadius = dp(12)
            insetTop = 0
            insetBottom = 0
            setOnClickListener { onClick() }
        }
        fun card(title: String, value: String): MaterialCardView {
            val inner = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), dp(12), dp(14), dp(12))
                addView(label(title, 13f, "#A9BBB3"))
                val valueView = label(value, 24f, "#F1F6F2", true)
                if (title == "현재 배터리") tvDashboardSoc = valueView else tvDashboardReserve = valueView
                addView(valueView)
            }
            return MaterialCardView(this).apply {
                radius = dp(14).toFloat()
                setCardBackgroundColor(Color.parseColor("#182422"))
                strokeColor = Color.parseColor("#33433D")
                strokeWidth = 1
                addView(inner)
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(8) }
            }
        }

        dashboard.addView(label("V-Locker", 13f, "#6EDAC1", true))
        dashboard.addView(label("오늘의 캠핑, 안전하게 준비하세요", 24f, "#F1F6F2", true).apply {
            setPadding(0, dp(8), 0, dp(4))
        })
        dashboard.addView(label("현재 배터리와 귀가 계획을 한눈에 확인하세요.", 14f, "#A9BBB3").apply {
            setPadding(0, 0, 0, dp(14))
        })

        val metrics = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        metrics.addView(card("현재 배터리", String.format(Locale.US, "%.0f%%", currentSocPercent)))
        metrics.addView(card("귀가 예약 잔량", "계획 없음").apply { layoutParams = LinearLayout.LayoutParams(0, -2, 1f) })
        dashboard.addView(metrics)

        btnDashboardDestination = action("⌕   목적지 검색   ·   ${etDestination.text}", "#22322F") {
            showDestinationSearchDialog()
        }.apply {
            minHeight = dp(48)
            setPadding(dp(12), dp(8), dp(12), dp(8))
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = dp(12)
            }
        }
        dashboard.addView(btnDashboardDestination)

        btnDashboardAnalyze = action("귀가 예약 잔량 선택하기", "#087F70") {
            if (hasFreshPrediction()) {
                showScreen(2)
            } else {
                analyzeReturnRoute(targetScreen = 2)
            }
        }.apply {
            minHeight = dp(52)
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = dp(10)
                bottomMargin = dp(8)
            }
        }
        dashboard.addView(btnDashboardAnalyze)

        dashboard.addView(
            action("출발 시각 · 탑승 인원 · 소비 전력 상세 설정", "#182422") {
                showScreen(1)
            }.apply {
                minHeight = dp(46)
                setTextColor(Color.parseColor("#A9BBB3"))
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) }
            }
        )

        content.addView(dashboard, content.indexOfChild(layoutScreen01))

        layoutScreen05 = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(dp(4), dp(8), dp(4), dp(14))
            addView(label("에너지 분석", 24f, "#F1F6F2", true))
            addView(label("귀가 주행과 V2L 사용에 필요한 전력을 확인하세요.", 14f, "#A9BBB3").apply {
                setPadding(0, dp(6), 0, dp(12))
            })
            tvEnergyAnalysisSummary = label("경로 분석 후 에너지 사용량을 표시합니다.", 16f, "#E2E8F0").apply {
                setPadding(dp(4), dp(8), dp(4), dp(16))
            }
            addView(tvEnergyAnalysisSummary)
            addView(action("메인 대시보드", "#087F70") { showScreen(0) }.apply {
                minHeight = dp(48)
                layoutParams = LinearLayout.LayoutParams(-1, -2)
            })
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
            .setPositiveButton("검색", null)
            .setNegativeButton("닫기", null)
            .create()

        fun choose(name: String, lat: Double?, lng: Double?, address: String? = null, note: String? = null) {
            etDestination.setText(name)
            selectedDestLat = lat
            selectedDestLng = lng
            selectedDestAddress = address
            selectedDestLabel = name
            invalidatePredictCache()
            tvSelectedDestCoords.text = note ?: if (lat != null && lng != null) {
                String.format(Locale.US, "선택: %s (%.4f, %.4f)", name, lat, lng)
            } else {
                "선택: $name"
            }
            updateVehicleSummaryUI()
            dialog.dismiss()
        }

        home.setOnClickListener {
            val fav = savedHomeFavorite
            if (fav != null) {
                choose(
                    fav.name,
                    fav.latitude,
                    fav.longitude,
                    fav.address,
                    String.format(Locale.US, "선택: %s (DB 저장 즐겨찾기 %.4f, %.4f)", fav.name, fav.latitude, fav.longitude)
                )
            } else {
                choose("우리 집", 37.4979, 127.0276, null, "선택: 우리 집 (기본 서울 강남 좌표)")
            }
        }
        home.setOnLongClickListener {
            saveCurrentDestinationAsHome()
            true
        }

        charger.setOnClickListener {
            charger.isEnabled = false
            lifecycleScope.launch {
                runCatching { ApiClient.service.searchNearbyChargers(originLat, originLng) }
                    .onSuccess { res ->
                        val nearest = res.data?.chargers?.firstOrNull()
                        if (nearest != null) {
                            choose(
                                nearest.name,
                                nearest.lat,
                                nearest.lng,
                                nearest.address,
                                String.format(Locale.US, "최근접 충전소: %s (직선 %.1f km)", nearest.name, nearest.distanceKm)
                            )
                        } else {
                            toast("주변 충전소 정보를 불러오지 못했습니다.")
                        }
                    }
                    .onFailure { e ->
                        toast("충전소 검색 실패: ${e.message}")
                    }
                if (dialog.isShowing) {
                    charger.isEnabled = true
                }
            }
        }

        dialog.show()

        val searchButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        searchButton.setOnClickListener {
            val keyword = query.text.toString().trim()
            if (keyword.isEmpty()) {
                toast("검색할 목적지를 입력해 주세요.")
                return@setOnClickListener
            }

            searchButton.isEnabled = false
            results.removeAllViews()
            results.addView(TextView(this@MainActivity).apply {
                text = "장소 검색 중..."
                textSize = 13f
                setTextColor(Color.parseColor("#94A3B8"))
                setPadding(12, 14, 12, 14)
            })

            lifecycleScope.launch {
                runCatching { ApiClient.service.searchPlaces(keyword, 5) }
                    .onSuccess { res ->
                        if (!dialog.isShowing) return@onSuccess
                        val places = res.data?.places.orEmpty()
                        results.removeAllViews()
                        if (places.isEmpty()) {
                            results.addView(TextView(this@MainActivity).apply {
                                text = "검색 결과가 없습니다."
                                textSize = 13f
                                setTextColor(Color.parseColor("#94A3B8"))
                                setPadding(12, 14, 12, 14)
                            })
                        } else {
                            places.forEach { place ->
                                val row = TextView(this@MainActivity).apply {
                                    text = "📍 ${place.name}\n     ${place.address}"
                                    textSize = 14f
                                    setTextColor(Color.WHITE)
                                    setPadding(12, 14, 12, 14)
                                    setOnClickListener {
                                        choose(place.name, place.lat, place.lng, place.address)
                                    }
                                }
                                results.addView(row)
                            }
                        }
                    }
                    .onFailure { e ->
                        if (!dialog.isShowing) return@onFailure
                        results.removeAllViews()
                        results.addView(TextView(this@MainActivity).apply {
                            text = "장소 검색 실패 (서버 연결 확인): ${e.message}"
                            textSize = 13f
                            setTextColor(Color.parseColor("#FCA5A5"))
                            setPadding(12, 14, 12, 14)
                        })
                    }
                if (dialog.isShowing) {
                    searchButton.isEnabled = true
                }
            }
        }
    }

    private fun showEnergyAnalysisDialog() {
        val predict = lastPredictData
        val breakdown = predict?.breakdown
        val rec = predict?.recommendations?.get(selectedMarginMode)
        tvEnergyAnalysisSummary.text = if (predict == null || breakdown == null) {
            "경로 분석 후 에너지 사용량을 표시합니다."
        } else {
            val modeLine = if (rec != null) {
                String.format(
                    Locale.US,
                    "\n선택 마진(%s): 예약 %.1f%% · V2L 약 %.1f시간 (여유 %.2f kWh)",
                    rec.modeNameKr, rec.reserveSocPercent, rec.v2lUsableHours, rec.v2lUsableKwh
                )
            } else {
                ""
            }
            String.format(
                Locale.US,
                "목적지: %s (%.1f km · 예상 기온 %.1f℃)\n저온 효율 저하: -%.1f%%\n\n" +
                    "귀가 주행 전력: %.2f kWh\n" +
                    "고도 회생 전력: %.2f kWh\n" +
                    "콜드스타트 난방 전력: %.2f kWh\n" +
                    "순수 귀가 필수 전력: %.2f kWh (약 %.1f%%)%s",
                predict.destination.name,
                predict.navigation.distanceKm,
                predict.weather.temperature,
                breakdown.efficiencyLossPercent,
                breakdown.baseDrivingKwh,
                breakdown.elevationKwh,
                breakdown.coldStartHvacSpikeKwh,
                breakdown.pureReturnKwh,
                breakdown.pureReturnSocPercent,
                modeLine
            )
        }
        if (predict != null) {
            renderFallbackBanner(predict.isFallback)
        }
        showScreen(5)
    }

    private fun renderUtilityStandbyState() {
        val reservedText = activeSessionStatus?.let {
            String.format(Locale.US, "목표 예약 %.1f%%", it.targetSoc)
        } ?: lastPredictData?.recommendations?.get(selectedMarginMode)?.let {
            String.format(Locale.US, "목표 예약 %.1f%%", it.reserveSocPercent)
        } ?: "예약 계획 미설정"

        tvLockoutBmsInfo.text = String.format(
            Locale.US,
            "현재 배터리 %.1f%% · %s (귀가 주행 보호 대기)",
            currentSocPercent, reservedText
        )
        tvLockoutRelayInfo.text = if (activeSessionStatus != null) {
            "V2L 외부 전원 릴레이 공급 중 (차단 전)"
        } else {
            "V2L 모니터링 시작 전 대기 상태"
        }
        tvLockoutHvacInfo.text = if (isPleosConnected) {
            "Pleos HVAC 연동 대기 (차단 시 21.0℃ 무시동 히터 자동 가동)"
        } else {
            "무시동 히터 대기 중 (차단 시 21.0℃ 유틸리티 모드 자동 전환)"
        }
        tvLockoutSdkLog.text = "실시간 모니터링에서 목표 잔량에 도달하거나 수동 차단 시 물리 제어 및 DB 감사 로그가 기록됩니다."
    }

    private fun initVehicleAndBackend() {
        vehicleManager = PleosVehicleManager(this)
        vehicleManager.onLiveSocChanged = { liveSoc ->
            currentSocPercent = liveSoc
            updateVehicleSummaryUI()

            val sessionId = activeSessionId
            if (currentScreenIndex == 3 && sessionId != null && !isLockoutTriggered) {
                val prevSynced = lastSyncedLiveSoc
                if (!isSyncingLiveSoc && (prevSynced == null || abs(liveSoc - prevSynced) >= 0.1)) {
                    syncLiveSocToBackend(sessionId, liveSoc)
                }
            }
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
                fetchUserProfileFromBackend()
            }
        }
    }

    private fun fetchUserProfileFromBackend(retryOnStartup: Boolean = true) {
        lifecycleScope.launch {
            var lastError: Throwable? = null
            var result = runCatching { ApiClient.service.getUserVehicle() }
                .onFailure { lastError = it }

            if (result.isFailure && currentServerUrl.startsWith("http://10.0.2.2:")) {
                val reverseUrl = currentServerUrl.replace("10.0.2.2", "127.0.0.1")
                ApiClient.updateBaseUrl(reverseUrl)
                val reverseResult = runCatching { ApiClient.service.getUserVehicle() }
                if (reverseResult.isSuccess) {
                    currentServerUrl = reverseUrl
                    result = reverseResult
                } else {
                    ApiClient.updateBaseUrl(currentServerUrl)
                    lastError = reverseResult.exceptionOrNull() ?: lastError
                }
            }

            result
                .onSuccess { res ->
                    res.data?.let { applyUserVehicle(it) }
                    updateVehicleSummaryUI("백엔드 연결됨")
                    fetchSavedHomeFavorite()
                }
                .onFailure {
                    android.util.Log.w("VlockerNet", "GET /api/user failed: ${lastError?.message}", lastError)
                    if (retryOnStartup) {
                        updateVehicleSummaryUI("백엔드 연결 재시도 중...")
                        delay(2500)
                        fetchUserProfileFromBackend(retryOnStartup = false)
                    } else {
                        updateVehicleSummaryUI("백엔드 미연결 (차량 설정 확인)")
                    }
                }
        }
    }

    private fun fetchSavedHomeFavorite() {
        lifecycleScope.launch {
            runCatching { ApiClient.service.listFavorites() }
                .onSuccess { res ->
                    val favorites = res.data?.favorites.orEmpty()
                    savedHomeFavorite = favorites.firstOrNull {
                        it.name.contains("우리 집") || it.name.contains("우리집")
                    } ?: favorites.firstOrNull()
                }
        }
    }

    private fun applyHomeFavoriteSelection() {
        val home = savedHomeFavorite
        if (home != null) {
            etDestination.setText(home.name)
            selectedDestLat = home.latitude
            selectedDestLng = home.longitude
            selectedDestAddress = home.address
            selectedDestLabel = home.name
            layoutPlaceResults.visibility = View.GONE
            tvSelectedDestCoords.text = String.format(
                Locale.US,
                "선택: %s (DB 저장 즐겨찾기 %.4f, %.4f)",
                home.name, home.latitude, home.longitude
            )
        } else {
            etDestination.setText("우리 집")
            selectedDestLat = 37.4979
            selectedDestLng = 127.0276
            selectedDestAddress = null
            selectedDestLabel = "우리 집"
            layoutPlaceResults.visibility = View.GONE
            tvSelectedDestCoords.text = "선택: 우리 집 (기본 등록 서울 강남 좌표 · 길게 눌러 현재 목적지 저장)"
        }
        invalidatePredictCache()
        updateVehicleSummaryUI()
    }

    private fun saveCurrentDestinationAsHome() {
        val lat = selectedDestLat
        val lng = selectedDestLng
        if (lat == null || lng == null) {
            toast("먼저 목적지를 검색해 선택한 뒤 [우리 집] 버튼을 길게 눌러 저장해 주세요.")
            return
        }
        val req = FavoriteCreateRequest(
            name = "우리 집",
            address = selectedDestAddress ?: etDestination.text.toString().trim(),
            latitude = lat,
            longitude = lng
        )
        lifecycleScope.launch {
            runCatching { ApiClient.service.upsertFavorite(req) }
                .onSuccess { res ->
                    savedHomeFavorite = res.data
                    toast("'우리 집' 좌표가 DB에 저장되었습니다.")
                }
                .onFailure { e ->
                    toast("즐겨찾기 저장 실패: ${e.message}")
                }
        }
    }

    private fun applyUserVehicle(data: UserVehicleData) {
        userVehicleModel = data.vehicleModel
        userBatteryCapacity = data.batteryCapacity
        userCurbWeight = data.curbWeight
        userAvgEfficiency = data.avgEfficiency
        updateVehicleSummaryUI()
    }

    private fun updateVehicleSummaryUI(backendNote: String? = null) {
        if (backendNote != null) {
            backendStatusNote = backendNote
        }
        val modeLabel = if (isPleosConnected) "Pleos SDK" else "시뮬 모드"
        val socFmt = String.format(Locale.US, "%.1f", currentSocPercent)
        tvConnectionBadge.text = "$modeLabel · SoC $socFmt% · $backendStatusNote"
        tvScreen01SocInfo.text = String.format(
            Locale.US,
            "차량(%s): 배터리 %.1f%% · 용량 %.1f kWh · 전비 %.1f km/kWh",
            userVehicleModel, currentSocPercent, userBatteryCapacity, userAvgEfficiency
        )
        if (::tvDashboardSoc.isInitialized) {
            tvDashboardSoc.text = String.format(Locale.US, "%.0f%%", currentSocPercent)
        }
        if (::tvDashboardReserve.isInitialized) {
            val activeReserve = activeSessionStatus?.takeIf { !isLockoutTriggered }?.targetSoc
            val plannedReserve = lastPredictData?.recommendations?.get(selectedMarginMode)?.reserveSocPercent
            val displayReserve = activeReserve ?: plannedReserve
            tvDashboardReserve.text = displayReserve?.let {
                String.format(Locale.US, "%.1f%%", it)
            } ?: "계획 없음"
        }
        if (::btnDashboardDestination.isInitialized) {
            val destLabel = etDestination.text.toString().trim().ifEmpty { "목적지 선택" }
            btnDashboardDestination.text = "⌕   목적지 검색   ·   $destLabel"
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

        lifecycleScope.launch {
            btnSearchPlaces.isEnabled = false
            runCatching { ApiClient.service.searchPlaces(keyword, 5) }
                .onSuccess { res ->
                    val places = res.data?.places.orEmpty()
                    renderPlaceResults(places)
                    updateVehicleSummaryUI("백엔드 연결됨")
                    if (places.isEmpty()) {
                        toast("검색 결과가 없습니다. 직접 [분석하기]를 눌러 기본 매핑을 사용할 수 있습니다.")
                    }
                }
                .onFailure { e ->
                    updateVehicleSummaryUI("백엔드 미연결 (서버 확인)")
                    toast("장소 검색 실패: ${e.message}")
                }
            btnSearchPlaces.isEnabled = true
        }
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
                    selectedDestAddress = place.address
                    selectedDestLabel = place.name
                    invalidatePredictCache()
                    layoutPlaceResults.visibility = View.GONE
                    tvSelectedDestCoords.text = String.format(
                        Locale.US,
                        "선택: %s (%.4f, %.4f)",
                        place.name, place.lat, place.lng
                    )
                    updateVehicleSummaryUI()
                }
            }
            layoutPlaceResults.addView(row)
        }
    }

    private fun findNearestChargerShortcut() {
        lifecycleScope.launch {
            btnShortcutCharger.isEnabled = false
            runCatching { ApiClient.service.searchNearbyChargers(originLat, originLng) }
                .onSuccess { res ->
                    val charger = res.data?.chargers?.firstOrNull()
                    if (charger != null) {
                        etDestination.setText(charger.name)
                        selectedDestLat = charger.lat
                        selectedDestLng = charger.lng
                        selectedDestAddress = charger.address
                        selectedDestLabel = charger.name
                        invalidatePredictCache()
                        layoutPlaceResults.visibility = View.GONE
                        tvSelectedDestCoords.text = String.format(
                            Locale.US,
                            "최근접 충전소: %s (직선 %.1f km)",
                            charger.name, charger.distanceKm
                        )
                        updateVehicleSummaryUI("백엔드 연결됨")
                    } else {
                        toast("주변 충전소 정보를 불러오지 못했습니다.")
                    }
                }
                .onFailure { e ->
                    updateVehicleSummaryUI("백엔드 미연결 (서버 확인)")
                    toast("충전소 검색 실패: ${e.message}")
                }
            btnShortcutCharger.isEnabled = true
        }
    }

    private fun invalidatePredictCache() {
        lastPredictData = null
        lastPredictQueryName = null
        lastPredictLoadKw = null
    }

    private fun hasFreshPrediction(): Boolean {
        val currentDestInput = etDestination.text.toString().trim().ifEmpty { "강남역" }
        return lastPredictData != null &&
            lastPredictQueryName == currentDestInput &&
            lastPredictLoadKw == readLoadKw()
    }

    private fun selectDepartureTime(label: String) {
        selectedDepartureTime = label
        invalidatePredictCache()
        val active = ColorStateList.valueOf(Color.parseColor("#0284C7"))
        val inactive = ColorStateList.valueOf(Color.parseColor("#1E293B"))
        btnDepMorning07.backgroundTintList = if (label == "내일 오전 07:00") active else inactive
        btnDepMorning10.backgroundTintList = if (label == "내일 오전 10:00") active else inactive
        btnDepNoon12.backgroundTintList = if (label == "내일 오후 12:00") active else inactive
    }

    private fun selectPassengers(count: Int) {
        selectedPassengers = count
        invalidatePredictCache()
        val active = ColorStateList.valueOf(Color.parseColor("#0284C7"))
        val inactive = ColorStateList.valueOf(Color.parseColor("#1E293B"))
        btnPass1.backgroundTintList = if (count == 1) active else inactive
        btnPass2.backgroundTintList = if (count == 2) active else inactive
        btnPass4.backgroundTintList = if (count == 4) active else inactive
    }

    private fun readLoadKw(): Double {
        return etV2lLoadKw.text.toString().toDoubleOrNull()?.coerceIn(0.2, 3.6) ?: 1.2
    }

    private fun analyzeReturnRoute(targetScreen: Int = 2, autoStartMonitoring: Boolean = false) {
        if (isPredicting) return

        val destName = etDestination.text.toString().trim().ifEmpty { "강남역" }
        val loadKw = readLoadKw()
        val forceFallback = cbForceFallback.isChecked
        val coordsMatchTypedDest = destName == selectedDestLabel

        val request = PredictRequest(
            destinationName = destName,
            originLat = originLat,
            originLng = originLng,
            destLat = if (coordsMatchTypedDest) selectedDestLat else null,
            destLng = if (coordsMatchTypedDest) selectedDestLng else null,
            departureTime = selectedDepartureTime,
            passengers = selectedPassengers,
            currentSocPercent = currentSocPercent,
            avgEfficiencyKmKwh = userAvgEfficiency,
            batteryCapacityKwh = userBatteryCapacity,
            curbWeightKg = userCurbWeight.toDouble(),
            v2lCurrentLoadKw = loadKw,
            forceFallback = forceFallback
        )

        lifecycleScope.launch {
            isPredicting = true
            progressPredict.visibility = View.VISIBLE
            btnAnalyzeRoute.isEnabled = false
            if (::btnDashboardAnalyze.isInitialized) {
                btnDashboardAnalyze.isEnabled = false
                btnDashboardAnalyze.text = "AI 귀가 경로 및 기상 분석 중..."
            }

            runCatching { ApiClient.service.predictConsumption(request) }
                .onSuccess { res ->
                    val data = res.data
                    if (data != null) {
                        lastPredictData = data
                        lastPredictQueryName = destName
                        lastPredictLoadKw = loadKw
                        updateVehicleSummaryUI("백엔드 연결됨")
                        renderFallbackBanner(data.isFallback)
                        renderPredictResult(data)
                        if (autoStartMonitoring) {
                            startMonitoringSession()
                        } else if (targetScreen == 5) {
                            showEnergyAnalysisDialog()
                        } else {
                            showScreen(targetScreen)
                        }
                    } else {
                        toast("예측 데이터가 비어 있습니다.")
                    }
                }
                .onFailure { e ->
                    updateVehicleSummaryUI("백엔드 미연결 (서버 확인)")
                    toast("서버 통신 실패 (FastAPI 실행 여부 확인): ${e.message}")
                }

            isPredicting = false
            progressPredict.visibility = View.GONE
            btnAnalyzeRoute.isEnabled = true
            if (::btnDashboardAnalyze.isInitialized) {
                btnDashboardAnalyze.isEnabled = true
                btnDashboardAnalyze.text = "귀가 예약 잔량 선택하기"
            }
        }
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

        updateSelectedMargin(selectedMarginMode)
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
        updateVehicleSummaryUI()
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

        val req = CreateSessionRequest(
            destinationName = predict.destination.name,
            distanceKm = predict.navigation.distanceKm,
            elevationGainM = predict.navigation.elevationGainM,
            expectedTemp = predict.weather.temperature,
            departureTime = selectedDepartureTime,
            passengers = selectedPassengers,
            marginMode = selectedMarginMode,
            targetSoc = rec.reserveSocPercent,
            currentSoc = currentSocPercent,
            currentV2lLoadKw = readLoadKw(),
            isFallback = predict.isFallback
        )

        lifecycleScope.launch {
            btnStartMonitoring.isEnabled = false
            runCatching { ApiClient.service.createSession(req) }
                .onSuccess { res ->
                    val session = res.data
                    if (session != null) {
                        activeSessionId = session.sessionId
                        activeSessionStatus = session
                        lastShownWarningLevel = null
                        isLockoutTriggered = false
                        isSyncingLiveSoc = false
                        lastSyncedLiveSoc = session.currentSoc
                        updateVehicleSummaryUI("백엔드 연결됨")
                        renderSessionDashboard(session)
                        showScreen(3)
                    } else {
                        toast("모니터링 세션 응답이 비어 있습니다.")
                    }
                }
                .onFailure { e ->
                    updateVehicleSummaryUI("백엔드 미연결 (서버 확인)")
                    toast("모니터링 세션 시작 실패: ${e.message}")
                }
            btnStartMonitoring.isEnabled = true
        }
    }

    // =========================================================
    // Screen 03 모니터링 & 경고 & 연장
    // =========================================================
    private fun simulateSessionTick(decrementPct: Double) {
        val sessionId = activeSessionId
        if (sessionId == null) {
            toast("활성화된 V2L 세션이 없습니다.")
            return
        }
        lifecycleScope.launch {
            runCatching {
                ApiClient.service.tickSession(
                    sessionId,
                    TickRequest(socDecrement = decrementPct, loadKw = readLoadKw())
                )
            }.onSuccess { res ->
                val session = res.data ?: return@onSuccess
                handleSessionTransition(session)
            }.onFailure { e ->
                toast("시뮬레이션 틱 실패: ${e.message}")
            }
        }
    }

    private fun syncLiveSocToBackend(sessionId: Int, liveSoc: Double) {
        isSyncingLiveSoc = true
        lifecycleScope.launch {
            runCatching {
                ApiClient.service.syncSessionSoc(
                    sessionId,
                    SocSyncRequest(currentSoc = liveSoc, loadKw = readLoadKw())
                )
            }.onSuccess { res ->
                val session = res.data
                if (session != null) {
                    lastSyncedLiveSoc = session.currentSoc
                    handleSessionTransition(session)
                }
            }
            isSyncingLiveSoc = false
        }
    }

    private fun refreshActiveSession() {
        val sessionId = activeSessionId ?: return
        lifecycleScope.launch {
            runCatching { ApiClient.service.getSessionStatus(sessionId) }
                .onSuccess { res ->
                    val session = res.data ?: return@onSuccess
                    handleSessionTransition(session)
                }
                .onFailure { e ->
                    toast("상태 새로고침 실패: ${e.message}")
                }
        }
    }

    private fun handleSessionTransition(session: SessionStatusData) {
        activeSessionStatus = session
        currentSocPercent = session.currentSoc
        lastSyncedLiveSoc = session.currentSoc
        updateVehicleSummaryUI("백엔드 연결됨")
        renderSessionDashboard(session)

        when (session.v2lStatus) {
            "LOCKED_OUT" -> {
                if (!isLockoutTriggered) {
                    triggerFinalLockoutSequence(session.sessionId)
                }
            }
            "WARN_30M" -> {
                if (lastShownWarningLevel != 30) {
                    lastShownWarningLevel = 30
                    showWarn30mDialog()
                    speakSafetyNotice("긴급 알림. 약 30분 후 V2L 전원이 차단됩니다. 필요하면 사용 연장을 선택해 주세요.")
                }
            }
            "WARN_60M" -> {
                if (lastShownWarningLevel == null) {
                    lastShownWarningLevel = 60
                    showWarn60mDialog()
                    speakSafetyNotice("배터리 보호를 위해 약 1시간 후 V2L 전원이 종료됩니다.")
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
            .setMessage("귀가 예약 잔량에 도달하기까지 약 1시간 남았습니다.\n차단 즉시 차량 내부 무시동 히터(21℃)가 자동으로 가동됩니다.")
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
        val sessionId = activeSessionId
        if (sessionId == null) {
            toast("먼저 V2L 모니터링 세션을 시작한 뒤 사용 연장을 실행해 주세요.")
            return
        }
        AlertDialog.Builder(this)
            .setTitle("🔌 V2L 사용 연장 (인근 급속충전소 재계산)")
            .setMessage("인근 급속 충전소 경로를 기준으로 안전 마진을 다시 계산하여 V2L 가용 시간을 연장할까요?")
            .setPositiveButton("연장 적용") { _, _ ->
                lifecycleScope.launch {
                    runCatching {
                        ApiClient.service.extendSession(
                            sessionId,
                            ExtendRequest(originLat = originLat, originLng = originLng)
                        )
                    }.onSuccess { res ->
                        val ext = res.data
                        if (ext != null) {
                            lastShownWarningLevel = null
                            toast("${ext.newDestination}: 목표 ${ext.newTargetSoc}%로 변경 (+${ext.additionalHours}시간 연장)")
                            refreshActiveSession()
                        }
                    }.onFailure { e ->
                        toast("사용 연장 실패: ${e.message}")
                    }
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun showManualLockoutConfirmDialog() {
        val sessionId = activeSessionId
        if (sessionId == null) {
            toast("진행 중인 V2L 모니터링 세션이 없습니다.")
            return
        }
        AlertDialog.Builder(this)
            .setTitle("V2L 차단 확인 팝업 - 수동")
            .setMessage("V2L 전원을 차단하고 귀가 예약 잔량을 보존할까요?\n차단 후에는 차량 유틸리티 화면에서 BMS 잔량과 난방(21℃) 상태를 확인할 수 있습니다.")
            .setNegativeButton("취소 (계속 사용)", null)
            .setPositiveButton("즉시 전원 차단") { _, _ -> triggerFinalLockoutSequence(sessionId) }
            .show()
    }

    // =========================================================
    // Screen 04 락아웃 & Pleos HVAC 21℃ 가동
    // =========================================================
    private fun triggerFinalLockoutSequence(sessionId: Int) {
        if (isLockoutTriggered) return
        isLockoutTriggered = true
        lifecycleScope.launch {
            runCatching { ApiClient.service.lockoutSession(sessionId) }
                .onSuccess { res ->
                    val lockout = res.data
                    if (lockout != null) {
                        activeSessionStatus = activeSessionStatus?.copy(
                            v2lStatus = lockout.v2lStatus,
                            relayConnected = lockout.relayConnected,
                            utilityModeActive = lockout.utilityModeActive
                        )
                        executeHardwareAndShowScreen04(lockout)
                    } else {
                        isLockoutTriggered = false
                    }
                }
                .onFailure { e ->
                    isLockoutTriggered = false
                    toast("차단 API 호출 실패: ${e.message}")
                }
        }
    }

    private fun executeHardwareAndShowScreen04(lockout: LockoutData) {
        vehicleManager.triggerEmergencyLockoutAndHeater(lockout.cabinTargetTempCelsius.toFloat()) { relayOk, hvacOk, detail ->
            tvLockoutBmsInfo.text = String.format(
                Locale.US,
                "배터리 %.1f%% 안전 보존 완료 · 귀가 보장 거리: 약 %.1f km",
                lockout.bmsPreservedSoc, lockout.returnDistanceGuaranteedKm
            )
            tvLockoutRelayInfo.text =
                if (relayOk) "물리적 전원 공급 차단됨 (대기 누수 0W)" else "릴레이 차단 상태 확인 중"
            tvLockoutHvacInfo.text = String.format(
                Locale.US,
                "목표 실내 온도 %.1f℃ 유지 중 (유틸리티 모드 %s)",
                lockout.cabinTargetTempCelsius,
                if (hvacOk) "작동 중" else "대체 전환"
            )
            tvLockoutSdkLog.text = "하드웨어 제어 로그: $detail"
            speakSafetyNotice("V2L 전원을 안전하게 차단하고 차량 무시동 히터를 21도로 가동했습니다.")
            updateVehicleSummaryUI()
            showScreen(4)

            lifecycleScope.launch {
                runCatching { ApiClient.service.getSessionEvents(lockout.sessionId) }
                    .onSuccess { res ->
                        val count = res.data?.events?.size ?: 0
                        if (count > 0) {
                            tvLockoutSdkLog.text = "하드웨어 제어 로그: $detail\nDB 감사 로그 ${count}건 기록 완료"
                        }
                    }
            }
        }
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

        val etUrl = addField("FastAPI 서버 주소", currentServerUrl)
        val etModel = addField("차량 모델", userVehicleModel)
        val etCap = addField("배터리 용량 (kWh)", userBatteryCapacity.toString(), numeric = true)
        val etWeight = addField("공차중량 (kg)", userCurbWeight.toString(), numeric = true)
        val etEff = addField("평소 전비 (km/kWh)", userAvgEfficiency.toString(), numeric = true)

        AlertDialog.Builder(this)
            .setTitle("⚙️ 차량 스펙 & 서버 연결 설정")
            .setView(container)
            .setNegativeButton("취소", null)
            .setPositiveButton("저장") { _, _ ->
                val newUrl = etUrl.text.toString().trim().ifEmpty { currentServerUrl }
                currentServerUrl = newUrl
                ApiClient.updateBaseUrl(newUrl)

                val req = UserVehicleUpdateRequest(
                    vehicleModel = etModel.text.toString().trim().ifEmpty { userVehicleModel },
                    batteryCapacity = etCap.text.toString().toDoubleOrNull() ?: userBatteryCapacity,
                    curbWeight = etWeight.text.toString().toDoubleOrNull()?.toInt() ?: userCurbWeight,
                    avgEfficiency = etEff.text.toString().toDoubleOrNull() ?: userAvgEfficiency
                )

                lifecycleScope.launch {
                    runCatching { ApiClient.service.updateUserVehicle(req) }
                        .onSuccess { res ->
                            invalidatePredictCache()
                            res.data?.let { applyUserVehicle(it) }
                            updateVehicleSummaryUI("백엔드 연결됨")
                            fetchSavedHomeFavorite()
                            toast("차량 스펙이 DB에 저장되었습니다.")
                        }
                        .onFailure { e ->
                            updateVehicleSummaryUI("백엔드 미연결 (주소 확인)")
                            toast("서버 저장 실패 (주소 확인 필요): ${e.message}")
                        }
                }
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

        val activeTab = ColorStateList.valueOf(Color.parseColor("#087F70"))
        val inactiveTab = ColorStateList.valueOf(Color.parseColor("#182422"))
        btnHeaderSafety.backgroundTintList = if (step == 2) activeTab else inactiveTab
        btnHeaderEnergy.backgroundTintList = if (step == 5) activeTab else inactiveTab
        btnHeaderMonitor.backgroundTintList = if (step == 3) activeTab else inactiveTab
        btnHeaderUtility.backgroundTintList = if (step == 4) activeTab else inactiveTab

        if (step == 0) {
            updateVehicleSummaryUI()
        }
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        runCatching {
            textToSpeech?.stop()
            textToSpeech?.shutdown()
        }
        textToSpeech = null
        vehicleManager.release()
        super.onDestroy()
    }
}

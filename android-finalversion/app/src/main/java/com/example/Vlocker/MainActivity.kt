package com.example.Vlocker

import android.Manifest
import android.content.Context
import android.content.DialogInterface
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.text.InputType
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DimenRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.Vlocker.data.LocalStateStore
import com.example.Vlocker.location.ActiveLocationResult
import com.example.Vlocker.location.LocationHelper
import com.example.Vlocker.location.UnavailableReason
import com.example.Vlocker.network.ApiClient
import com.example.Vlocker.network.ChargerItem
import com.example.Vlocker.network.CreateSessionRequest
import com.example.Vlocker.network.ExtendRequest
import com.example.Vlocker.network.FavoriteCreateRequest
import com.example.Vlocker.network.FavoriteItem
import com.example.Vlocker.network.LockoutData
import com.example.Vlocker.network.MarginRecommendation
import com.example.Vlocker.network.PlaceItem
import com.example.Vlocker.network.PredictData
import com.example.Vlocker.network.PredictRequest
import com.example.Vlocker.network.RoutePoint
import com.example.Vlocker.network.SessionStatusData
import com.example.Vlocker.network.SocSyncRequest
import com.example.Vlocker.network.TickRequest
import com.example.Vlocker.network.UserVehicleData
import com.example.Vlocker.network.UserVehicleUpdateRequest
import com.example.Vlocker.safety.OfflineSafetyEngine
import com.example.Vlocker.ui.PowerTrendTracker
import com.example.Vlocker.ui.PowerTrendView
import com.example.Vlocker.ui.RouteGeometry
import com.example.Vlocker.ui.RoutePreviewView
import com.example.Vlocker.ui.SegmentedEnergyBarView
import com.example.Vlocker.ui.UiFormatters
import com.example.Vlocker.ui.UiScaleHelper
import com.example.Vlocker.vehicle.PleosVehicleManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {

    companion object {
        private const val MONITOR_POLL_INTERVAL_MS = 4_000L
    }

    private object SessionStore {
        var initialized: Boolean = false
        var currentScreenIndex: Int = 1 // 1: 경로설정, 2: 마진선택, 3: 모니터링, 4: 유틸리티, 5: 에너지분석

        var isPleosConnected: Boolean = false
        var isBackendOnline: Boolean = false
        var hasCompletedStartupConnectivityCheck: Boolean = false
        var currentServerUrl: String = "http://10.0.2.2:8000/"
        var backendStatusNote: String = "차량 연결 확인 중"

        // 차량 상태 (IONIQ 5 Long Range 기본값)
        var currentSocPercent: Double = 61.0
        var userVehicleModel: String = "IONIQ 5 Long Range"
        var userBatteryCapacity: Double = 77.4
        var userCurbWeight: Int = 2010
        var userAvgEfficiency: Double = 5.4

        // 출발 위치 (실제 GPS 우선, 한국 좌표 미검출 시 차박 기본 좌표)
        var originLat: Double = 37.6242
        var originLng: Double = 128.7420
        var originName: String = "강릉 안반데기"
        var originModeNote: String = "차박 기본 위치"
        var gpsAttempted: Boolean = false
        var originManuallySelected: Boolean = false

        // 목적지 상태
        var destName: String = "우리 집"
        var destSub: String = "저장된 목적지로 경로 미리보기"
        var destLat: Double? = null
        var destLng: Double? = null
        var destAddress: String? = null
        var destIsHome: Boolean = true
        var savedHomeFavorite: FavoriteItem? = null

        // 일정 · 인원 · 소비 전력 · 안전 기준
        var departureTime: String = "내일 오전 10:00"
        var passengers: Int = 2
        var v2lLoadKw: Double = 1.2
        var forceFallbackDemo: Boolean = false

        // 예측 & 모니터링 세션
        var lastPredictData: PredictData? = null
        var lastPredictKey: String? = null
        var selectedMarginMode: String = "NORMAL"

        var activeSessionId: Int? = null
        var activeSessionStatus: SessionStatusData? = null
        var extendedDestLat: Double? = null
        var extendedDestLng: Double? = null
        var extendedPolylinePoints: List<RoutePoint> = emptyList()
        var extendedGeometryType: String? = null
        var lastShownWarningLevel: Int? = null
        var isLockoutTriggered: Boolean = false
        var lastLockoutData: LockoutData? = null
        var lastHardwareDetail: String = ""

        val alertLogs: MutableList<String> = mutableListOf()
        val powerTrendTracker: PowerTrendTracker = PowerTrendTracker()
        var lastMonitorSyncTimeMs: Long? = null
        var isMonitorFeedDelayed: Boolean = false
        var hasUnsyncedLocalState: Boolean = false
        var hasLoggedOfflineMonitorSwitch: Boolean = false
    }

    private lateinit var localStateStore: LocalStateStore
    private lateinit var vehicleManager: PleosVehicleManager

    // 상단 헤더 & 탭
    private lateinit var tvTopVehicleMeta: TextView
    private lateinit var pillTopConnection: LinearLayout
    private lateinit var dotTopConnection: View
    private lateinit var tvTopConnection: TextView
    private lateinit var btnTopBell: ImageView
    private lateinit var btnTopSettings: ImageView

    private lateinit var tabSafety: LinearLayout
    private lateinit var tvTabSafety: TextView
    private lateinit var indTabSafety: View
    private lateinit var tabEnergy: LinearLayout
    private lateinit var tvTabEnergy: TextView
    private lateinit var indTabEnergy: View
    private lateinit var tabMonitor: LinearLayout
    private lateinit var tvTabMonitor: TextView
    private lateinit var indTabMonitor: View
    private lateinit var tabUtility: LinearLayout
    private lateinit var tvTabUtility: TextView
    private lateinit var indTabUtility: View

    private lateinit var tvPageTitle: TextView
    private lateinit var tvPageSubtitle: TextView
    private lateinit var cardFallbackNotice: LinearLayout
    private lateinit var tvFallbackNotice: TextView
    private lateinit var mainScrollView: ScrollView

    // 화면 컨테이너
    private lateinit var screenRouteSetup: LinearLayout
    private lateinit var screenMarginPlan: LinearLayout
    private lateinit var screenEnergyAnalysis: LinearLayout
    private lateinit var screenMonitor: LinearLayout
    private lateinit var screenUtility: LinearLayout

    // Screen 1 뷰
    private lateinit var pillRouteMode: TextView
    private lateinit var rowSearchBox: LinearLayout
    private lateinit var etSearchQuery: EditText
    private lateinit var btnSearchSubmit: TextView
    private lateinit var layoutPlaceDropdown: LinearLayout
    private lateinit var cardSelectedDest: LinearLayout
    private lateinit var tvSelectedDestName: TextView
    private lateinit var tvSelectedDestSub: TextView
    private lateinit var ivSelectedDestCheck: ImageView
    private lateinit var btnSelectHome: MaterialButton
    private lateinit var btnSelectCharger: TextView
    private lateinit var cardDeparturePicker: LinearLayout
    private lateinit var tvDepartureSummary: TextView
    private lateinit var tvOriginDestBreadcrumb: TextView
    private lateinit var routePreviewMap: RoutePreviewView
    private lateinit var btnMapZoomIn: TextView
    private lateinit var btnMapZoomOut: TextView
    private lateinit var tvMetricDistanceLabel: TextView
    private lateinit var tvMetricDistanceValue: TextView
    private lateinit var tvMetricTimeLabel: TextView
    private lateinit var tvMetricTimeValue: TextView
    private lateinit var tvMetricElevationLabel: TextView
    private lateinit var tvMetricElevationValue: TextView
    private lateinit var progressRouteLoading: ProgressBar
    private lateinit var btnGoToMargin: MaterialButton

    // Screen 2 뷰
    private lateinit var tvPlanDestTitle: TextView
    private lateinit var tvPlanDuration: TextView
    private lateinit var tvPlanDepartureBadge: TextView
    private lateinit var tvPlanTempValue: TextView
    private lateinit var tvPlanElevValue: TextView
    private lateinit var tvPlanEffValue: TextView
    private lateinit var tvPlanHvacValue: TextView
    private lateinit var tvPlanCurrentSocMeta: TextView
    private lateinit var cardPlanSafe: MaterialCardView
    private lateinit var ivRadioSafe: ImageView
    private lateinit var tvSafeReserveNum: TextView
    private lateinit var tvSafeUsableHours: TextView
    private lateinit var cardPlanNormal: MaterialCardView
    private lateinit var ivRadioNormal: ImageView
    private lateinit var tvNormalReserveNum: TextView
    private lateinit var tvNormalUsableHours: TextView
    private lateinit var cardPlanMax: MaterialCardView
    private lateinit var ivRadioMax: ImageView
    private lateinit var tvMaxReserveNum: TextView
    private lateinit var tvMaxUsableHours: TextView
    private lateinit var tvPlanInsufficientNotice: TextView
    private lateinit var btnStartMonitor: MaterialButton

    // 에너지 분석 뷰
    private lateinit var tvEnergyTotalNeed: TextView
    private lateinit var tvEnergyReserveTarget: TextView
    private lateinit var tvEnergyUsableNow: TextView
    private lateinit var tvEnergyBaseDrive: TextView
    private lateinit var tvEnergyHillClimb: TextView
    private lateinit var tvEnergyColdAdjust: TextView
    private lateinit var tvEnergyPreHeat: TextView
    private lateinit var tvEnergySafetyMargin: TextView
    private lateinit var btnEnergyBackPlan: MaterialButton
    private lateinit var btnEnergyGoMonitor: MaterialButton

    // Screen 3 뷰
    private lateinit var pillMonitorState: LinearLayout
    private lateinit var dotMonitorState: View
    private lateinit var tvMonitorStateLabel: TextView
    private lateinit var tvMonitorLiveTag: TextView
    private lateinit var tvMonitorCurrentSoc: TextView
    private lateinit var tvMonitorHoursLeft: TextView
    private lateinit var energyBarMonitor: SegmentedEnergyBarView
    private lateinit var tvMonitorReserveLabel: TextView
    private lateinit var tvMonitorGapLabel: TextView
    private lateinit var tvMonitorSummaryLine: TextView
    private lateinit var cardMonitorLoad: MaterialCardView
    private lateinit var tvMonitorLoadValue: TextView
    private lateinit var powerTrendChart: PowerTrendView
    private lateinit var tvPowerTrendLeftLabel: TextView
    private lateinit var tvPowerTrendRightLabel: TextView
    private lateinit var btnMonitorEditConditions: TextView
    private lateinit var routePreviewMonitor: RoutePreviewView
    private lateinit var btnMonitorMapZoomIn: TextView
    private lateinit var btnMonitorMapZoomOut: TextView
    private lateinit var tvCondDest: TextView
    private lateinit var tvCondDeparture: TextView
    private lateinit var tvCondDistTime: TextView
    private lateinit var tvCondWeatherElev: TextView
    private lateinit var tvCondMargin: TextView
    private lateinit var tvMonitorSafetyStatus: TextView
    private lateinit var btnSimDrain: TextView
    private lateinit var btnSimWarn60: TextView
    private lateinit var btnSimWarn30: TextView
    private lateinit var btnSimFallback: TextView
    private lateinit var btnStopV2l: MaterialButton

    // Screen 4 뷰
    private lateinit var tvUtilityHeadline: TextView
    private lateinit var tvUtilityDesc: TextView
    private lateinit var tvUtilityBadge: TextView
    private lateinit var tvUtilityCheckSummary: TextView
    private lateinit var tvCardPowerTitle: TextView
    private lateinit var tvCardPowerSub: TextView
    private lateinit var tvCardHeaterTitle: TextView
    private lateinit var tvCardHeaterSub: TextView
    private lateinit var tvCardRangeTitle: TextView
    private lateinit var tvCardRangeSub: TextView
    private lateinit var btnUtilityBack: MaterialButton
    private lateinit var btnUtilityComplete: MaterialButton

    private var predictJob: Job? = null
    private var predictRequestSeq: Long = 0L
    private var inFlightPredictKey: String? = null
    private var inFlightPredictServerUrl: String? = null
    private var pendingPredictTargetScreen: Int? = null
    private var pendingAutoStartMonitoring: Boolean = false
    private var pendingPredictIsExplicitNav: Boolean = false

    private var isSyncingLiveSoc: Boolean = false
    private var lastSyncedLiveSoc: Double? = null
    private var isStartingMonitoringSession: Boolean = false
    private var monitorPollingJob: Job? = null
    private var monitorMutationEpoch: Long = 0L
    private var isManualMonitorCallInFlight: Boolean = false
    private var isActivityStarted: Boolean = false
    private var textToSpeech: TextToSpeech? = null
    private var isTtsReady: Boolean = false

    private var activeLocationJob: Job? = null
    private var activeLocationRequestSeq: Long = 0L
    private var isFetchingDeviceLocation: Boolean = false
    private var pendingLocationUserInitiated: Boolean = false

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        val userInitiated = pendingLocationUserInitiated
        pendingLocationUserInitiated = false
        if (granted) {
            requestActiveDeviceLocation(userInitiated = userInitiated)
        } else if (userInitiated) {
            toast("위치 권한이 허용되지 않아 현재 출발 위치(${SessionStore.originName})를 유지합니다.")
        } else {
            Log.i("VlockerGps", "Startup location permission denied; keeping ${SessionStore.originName}")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false
        setupSystemBarInsets()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when (SessionStore.currentScreenIndex) {
                    2 -> showScreen(1)
                    5 -> showScreen(2)
                    3 -> showScreen(2)
                    4 -> if (SessionStore.activeSessionId != null) showScreen(3) else showScreen(1)
                    else -> moveTaskToBack(true)
                }
            }
        })

        bindViews()
        setupListeners()
        initTextToSpeech()
        initVehicleAndBackend()

        syncAllUiFromStore()
        showScreen(SessionStore.currentScreenIndex)
    }

    override fun onStart() {
        super.onStart()
        isActivityStarted = true
        if (SessionStore.initialized && !SessionStore.isBackendOnline && SessionStore.hasCompletedStartupConnectivityCheck) {
            probeBackendConnectionQuietly()
        }
        if (SessionStore.currentScreenIndex == 3) {
            startMonitorPollingIfEligible(immediateFirstPoll = true)
        }
    }

    override fun onStop() {
        isActivityStarted = false
        stopMonitorPolling()
        super.onStop()
    }

    private fun dimenPx(@DimenRes resId: Int): Int = resources.getDimensionPixelSize(resId)

    private fun dimenFloat(@DimenRes resId: Int): Float = resources.getDimension(resId)

    private fun AlertDialog.Builder.showScaled(): AlertDialog {
        val dialog = create()
        dialog.setOnShowListener {
            val metrics = resources.displayMetrics
            val isLand = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            val targetWidth = UiScaleHelper.computeDialogWidthPx(
                screenWidthPx = metrics.widthPixels,
                maxDialogWidthPx = dimenPx(R.dimen.vl_dialog_max_width),
                isLandscape = isLand
            )
            if (targetWidth > 0) {
                dialog.window?.setLayout(targetWidth, WindowManager.LayoutParams.WRAP_CONTENT)
            }

            val titlePx = dimenFloat(R.dimen.vl_dialog_title_text)
            val bodyPx = dimenFloat(R.dimen.vl_dialog_body_text)
            val btnPx = dimenFloat(R.dimen.vl_dialog_button_text)
            val btnMinH = dimenPx(R.dimen.vl_dialog_button_min_h)
            val btnPadH = dimenPx(R.dimen.vl_dialog_pad_h) / 2
            val btnPadV = dimenPx(R.dimen.vl_dialog_item_spacing) / 2
            val rowMinH = dimenPx(R.dimen.vl_touch_min_height)
            val rowPadH = dimenPx(R.dimen.vl_dialog_pad_h)
            val rowPadV = dimenPx(R.dimen.vl_dialog_item_spacing)

            dialog.findViewById<TextView>(androidx.appcompat.R.id.alertTitle)?.apply {
                setTextSize(TypedValue.COMPLEX_UNIT_PX, titlePx)
            }
            dialog.findViewById<TextView>(android.R.id.message)?.apply {
                setTextSize(TypedValue.COMPLEX_UNIT_PX, bodyPx)
                setLineSpacing(0f, 1.2f)
            }
            dialog.listView?.let { lv ->
                fun styleListRow(row: View?) {
                    row ?: return
                    row.minimumHeight = rowMinH
                    if (row is TextView) {
                        row.setTextSize(TypedValue.COMPLEX_UNIT_PX, bodyPx)
                        row.setPadding(rowPadH, rowPadV, rowPadH, rowPadV)
                    } else {
                        row.findViewById<TextView>(android.R.id.text1)?.apply {
                            setTextSize(TypedValue.COMPLEX_UNIT_PX, bodyPx)
                        }
                    }
                }
                lv.setOnHierarchyChangeListener(object : ViewGroup.OnHierarchyChangeListener {
                    override fun onChildViewAdded(parent: View?, child: View?) {
                        styleListRow(child)
                    }

                    override fun onChildViewRemoved(parent: View?, child: View?) = Unit
                })
                for (i in 0 until lv.childCount) {
                    styleListRow(lv.getChildAt(i))
                }
            }
            listOf(
                DialogInterface.BUTTON_POSITIVE,
                DialogInterface.BUTTON_NEGATIVE,
                DialogInterface.BUTTON_NEUTRAL
            ).forEach { which ->
                dialog.getButton(which)?.apply {
                    if (visibility == View.VISIBLE) {
                        setTextSize(TypedValue.COMPLEX_UNIT_PX, btnPx)
                        minHeight = btnMinH
                        minimumHeight = btnMinH
                        setPadding(btnPadH, btnPadV, btnPadH, btnPadV)
                    }
                }
            }
        }
        dialog.show()
        return dialog
    }

    private fun setupSystemBarInsets() {
        val root = findViewById<LinearLayout>(R.id.main)
        val topHeader = root.getChildAt(0)
        val topHeaderBaseHeight = topHeader?.layoutParams?.height?.takeIf { it > 0 }
            ?: dimenPx(R.dimen.vl_topbar_height)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            v.setPadding(bars.left, 0, bars.right, bars.bottom)
            topHeader?.let { header ->
                val lp = header.layoutParams
                val targetHeight = topHeaderBaseHeight + bars.top
                if (lp.height != targetHeight) {
                    lp.height = targetHeight
                    header.layoutParams = lp
                }
                if (header.paddingTop != bars.top) {
                    header.setPadding(
                        header.paddingLeft,
                        bars.top,
                        header.paddingRight,
                        header.paddingBottom
                    )
                }
            }
            insets
        }
    }

    private fun bindViews() {
        tvTopVehicleMeta = findViewById(R.id.tvTopVehicleMeta)
        pillTopConnection = findViewById(R.id.pillTopConnection)
        dotTopConnection = findViewById(R.id.dotTopConnection)
        tvTopConnection = findViewById(R.id.tvTopConnection)
        btnTopBell = findViewById(R.id.btnTopBell)
        btnTopSettings = findViewById(R.id.btnTopSettings)

        tabSafety = findViewById(R.id.tabSafety)
        tvTabSafety = findViewById(R.id.tvTabSafety)
        indTabSafety = findViewById(R.id.indTabSafety)
        tabEnergy = findViewById(R.id.tabEnergy)
        tvTabEnergy = findViewById(R.id.tvTabEnergy)
        indTabEnergy = findViewById(R.id.indTabEnergy)
        tabMonitor = findViewById(R.id.tabMonitor)
        tvTabMonitor = findViewById(R.id.tvTabMonitor)
        indTabMonitor = findViewById(R.id.indTabMonitor)
        tabUtility = findViewById(R.id.tabUtility)
        tvTabUtility = findViewById(R.id.tvTabUtility)
        indTabUtility = findViewById(R.id.indTabUtility)

        tvPageTitle = findViewById(R.id.tvPageTitle)
        tvPageSubtitle = findViewById(R.id.tvPageSubtitle)
        cardFallbackNotice = findViewById(R.id.cardFallbackNotice)
        tvFallbackNotice = findViewById(R.id.tvFallbackNotice)
        mainScrollView = findViewById(R.id.mainScrollView)

        screenRouteSetup = findViewById(R.id.screenRouteSetup)
        screenMarginPlan = findViewById(R.id.screenMarginPlan)
        screenEnergyAnalysis = findViewById(R.id.screenEnergyAnalysis)
        screenMonitor = findViewById(R.id.screenMonitor)
        screenUtility = findViewById(R.id.screenUtility)

        pillRouteMode = findViewById(R.id.pillRouteMode)
        rowSearchBox = findViewById(R.id.rowSearchBox)
        etSearchQuery = findViewById(R.id.etSearchQuery)
        btnSearchSubmit = findViewById(R.id.btnSearchSubmit)
        layoutPlaceDropdown = findViewById(R.id.layoutPlaceDropdown)
        cardSelectedDest = findViewById(R.id.cardSelectedDest)
        tvSelectedDestName = findViewById(R.id.tvSelectedDestName)
        tvSelectedDestSub = findViewById(R.id.tvSelectedDestSub)
        ivSelectedDestCheck = findViewById(R.id.ivSelectedDestCheck)
        btnSelectHome = findViewById(R.id.btnSelectHome)
        btnSelectCharger = findViewById(R.id.btnSelectCharger)
        cardDeparturePicker = findViewById(R.id.cardDeparturePicker)
        tvDepartureSummary = findViewById(R.id.tvDepartureSummary)
        tvOriginDestBreadcrumb = findViewById(R.id.tvOriginDestBreadcrumb)
        routePreviewMap = findViewById(R.id.routePreviewMap)
        btnMapZoomIn = findViewById(R.id.btnMapZoomIn)
        btnMapZoomOut = findViewById(R.id.btnMapZoomOut)
        tvMetricDistanceLabel = findViewById(R.id.tvMetricDistanceLabel)
        tvMetricDistanceValue = findViewById(R.id.tvMetricDistanceValue)
        tvMetricTimeLabel = findViewById(R.id.tvMetricTimeLabel)
        tvMetricTimeValue = findViewById(R.id.tvMetricTimeValue)
        tvMetricElevationLabel = findViewById(R.id.tvMetricElevationLabel)
        tvMetricElevationValue = findViewById(R.id.tvMetricElevationValue)
        progressRouteLoading = findViewById(R.id.progressRouteLoading)
        btnGoToMargin = findViewById(R.id.btnGoToMargin)

        tvPlanDestTitle = findViewById(R.id.tvPlanDestTitle)
        tvPlanDuration = findViewById(R.id.tvPlanDuration)
        tvPlanDepartureBadge = findViewById(R.id.tvPlanDepartureBadge)
        tvPlanTempValue = findViewById(R.id.tvPlanTempValue)
        tvPlanElevValue = findViewById(R.id.tvPlanElevValue)
        tvPlanEffValue = findViewById(R.id.tvPlanEffValue)
        tvPlanHvacValue = findViewById(R.id.tvPlanHvacValue)
        tvPlanCurrentSocMeta = findViewById(R.id.tvPlanCurrentSocMeta)
        cardPlanSafe = findViewById(R.id.cardPlanSafe)
        ivRadioSafe = findViewById(R.id.ivRadioSafe)
        tvSafeReserveNum = findViewById(R.id.tvSafeReserveNum)
        tvSafeUsableHours = findViewById(R.id.tvSafeUsableHours)
        cardPlanNormal = findViewById(R.id.cardPlanNormal)
        ivRadioNormal = findViewById(R.id.ivRadioNormal)
        tvNormalReserveNum = findViewById(R.id.tvNormalReserveNum)
        tvNormalUsableHours = findViewById(R.id.tvNormalUsableHours)
        cardPlanMax = findViewById(R.id.cardPlanMax)
        ivRadioMax = findViewById(R.id.ivRadioMax)
        tvMaxReserveNum = findViewById(R.id.tvMaxReserveNum)
        tvMaxUsableHours = findViewById(R.id.tvMaxUsableHours)
        tvPlanInsufficientNotice = findViewById(R.id.tvPlanInsufficientNotice)
        btnStartMonitor = findViewById(R.id.btnStartMonitor)

        tvEnergyTotalNeed = findViewById(R.id.tvEnergyTotalNeed)
        tvEnergyReserveTarget = findViewById(R.id.tvEnergyReserveTarget)
        tvEnergyUsableNow = findViewById(R.id.tvEnergyUsableNow)
        tvEnergyBaseDrive = findViewById(R.id.tvEnergyBaseDrive)
        tvEnergyHillClimb = findViewById(R.id.tvEnergyHillClimb)
        tvEnergyColdAdjust = findViewById(R.id.tvEnergyColdAdjust)
        tvEnergyPreHeat = findViewById(R.id.tvEnergyPreHeat)
        tvEnergySafetyMargin = findViewById(R.id.tvEnergySafetyMargin)
        btnEnergyBackPlan = findViewById(R.id.btnEnergyBackPlan)
        btnEnergyGoMonitor = findViewById(R.id.btnEnergyGoMonitor)

        pillMonitorState = findViewById(R.id.pillMonitorState)
        dotMonitorState = findViewById(R.id.dotMonitorState)
        tvMonitorStateLabel = findViewById(R.id.tvMonitorStateLabel)
        tvMonitorLiveTag = findViewById(R.id.tvMonitorLiveTag)
        tvMonitorCurrentSoc = findViewById(R.id.tvMonitorCurrentSoc)
        tvMonitorHoursLeft = findViewById(R.id.tvMonitorHoursLeft)
        energyBarMonitor = findViewById(R.id.energyBarMonitor)
        tvMonitorReserveLabel = findViewById(R.id.tvMonitorReserveLabel)
        tvMonitorGapLabel = findViewById(R.id.tvMonitorGapLabel)
        tvMonitorSummaryLine = findViewById(R.id.tvMonitorSummaryLine)
        cardMonitorLoad = findViewById(R.id.cardMonitorLoad)
        tvMonitorLoadValue = findViewById(R.id.tvMonitorLoadValue)
        powerTrendChart = findViewById(R.id.powerTrendChart)
        tvPowerTrendLeftLabel = findViewById(R.id.tvPowerTrendLeftLabel)
        tvPowerTrendRightLabel = findViewById(R.id.tvPowerTrendRightLabel)
        btnMonitorEditConditions = findViewById(R.id.btnMonitorEditConditions)
        routePreviewMonitor = findViewById(R.id.routePreviewMonitor)
        btnMonitorMapZoomIn = findViewById(R.id.btnMonitorMapZoomIn)
        btnMonitorMapZoomOut = findViewById(R.id.btnMonitorMapZoomOut)
        tvCondDest = findViewById(R.id.tvCondDest)
        tvCondDeparture = findViewById(R.id.tvCondDeparture)
        tvCondDistTime = findViewById(R.id.tvCondDistTime)
        tvCondWeatherElev = findViewById(R.id.tvCondWeatherElev)
        tvCondMargin = findViewById(R.id.tvCondMargin)
        tvMonitorSafetyStatus = findViewById(R.id.tvMonitorSafetyStatus)
        btnSimDrain = findViewById(R.id.btnSimDrain)
        btnSimWarn60 = findViewById(R.id.btnSimWarn60)
        btnSimWarn30 = findViewById(R.id.btnSimWarn30)
        btnSimFallback = findViewById(R.id.btnSimFallback)
        btnStopV2l = findViewById(R.id.btnStopV2l)

        tvUtilityHeadline = findViewById(R.id.tvUtilityHeadline)
        tvUtilityDesc = findViewById(R.id.tvUtilityDesc)
        tvUtilityBadge = findViewById(R.id.tvUtilityBadge)
        tvUtilityCheckSummary = findViewById(R.id.tvUtilityCheckSummary)
        tvCardPowerTitle = findViewById(R.id.tvCardPowerTitle)
        tvCardPowerSub = findViewById(R.id.tvCardPowerSub)
        tvCardHeaterTitle = findViewById(R.id.tvCardHeaterTitle)
        tvCardHeaterSub = findViewById(R.id.tvCardHeaterSub)
        tvCardRangeTitle = findViewById(R.id.tvCardRangeTitle)
        tvCardRangeSub = findViewById(R.id.tvCardRangeSub)
        btnUtilityBack = findViewById(R.id.btnUtilityBack)
        btnUtilityComplete = findViewById(R.id.btnUtilityComplete)
    }

    private fun setupListeners() {
        pillTopConnection.setOnClickListener { fetchUserProfileFromBackend(retryOnStartup = false) }
        btnTopBell.setOnClickListener { showAlertHistoryDialog() }
        btnTopSettings.setOnClickListener { showVehicleAndOriginSettingsDialog() }

        tabSafety.setOnClickListener {
            if (SessionStore.currentScreenIndex == 1 && hasFreshPrediction()) {
                showScreen(2)
            } else if (SessionStore.currentScreenIndex == 2) {
                showScreen(1)
            } else {
                showScreen(if (hasFreshPrediction()) 2 else 1)
            }
        }
        tabEnergy.setOnClickListener {
            if (hasFreshPrediction()) {
                showScreen(5)
            } else {
                predictRoute(targetScreen = 5)
            }
        }
        tabMonitor.setOnClickListener {
            val sid = SessionStore.activeSessionId
            if (sid != null && !SessionStore.isLockoutTriggered) {
                showScreen(3, immediateMonitorRefresh = true)
            } else if (hasFreshPrediction()) {
                startMonitoringSession()
            } else {
                predictRoute(targetScreen = 3, autoStartMonitoring = true)
            }
        }
        tabUtility.setOnClickListener {
            showScreen(4)
        }

        // Screen 1 리스너
        btnSearchSubmit.setOnClickListener { searchPlacesFromBackend() }
        etSearchQuery.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                searchPlacesFromBackend()
                true
            } else {
                false
            }
        }
        cardSelectedDest.setOnClickListener {
            if (SessionStore.destIsHome) {
                showOriginPickerDialog()
            } else {
                AlertDialog.Builder(this)
                    .setTitle("선택된 목적지")
                    .setMessage("${SessionStore.destName}\n이 장소를 '우리 집' 기본 목적지로 저장할까요?")
                    .setPositiveButton("우리 집으로 저장") { _, _ -> saveCurrentDestinationAsHome() }
                    .setNegativeButton("닫기", null)
                    .showScaled()
            }
        }
        btnSelectHome.setOnClickListener { applyHomeFavoriteSelection() }
        btnSelectHome.setOnLongClickListener {
            saveCurrentDestinationAsHome()
            true
        }
        btnSelectCharger.setOnClickListener { findNearestChargerShortcut() }
        cardDeparturePicker.setOnClickListener { showTripConditionsDialog() }
        tvOriginDestBreadcrumb.setOnClickListener { showOriginPickerDialog() }

        btnMapZoomIn.setOnClickListener { routePreviewMap.zoomIn() }
        btnMapZoomOut.setOnClickListener { routePreviewMap.zoomOut() }
        btnGoToMargin.setOnClickListener {
            if (hasFreshPrediction()) {
                showScreen(2)
            } else {
                predictRoute(targetScreen = 2)
            }
        }

        // Screen 2 & 에너지 분석 리스너
        cardPlanSafe.setOnClickListener { updateSelectedMargin("SAFE") }
        cardPlanNormal.setOnClickListener { updateSelectedMargin("NORMAL") }
        cardPlanMax.setOnClickListener { updateSelectedMargin("MAX_USE") }
        btnStartMonitor.setOnClickListener { startMonitoringSession() }

        btnEnergyBackPlan.setOnClickListener { showScreen(2) }
        btnEnergyGoMonitor.setOnClickListener { startMonitoringSession() }

        // Screen 3 리스너
        cardMonitorLoad.setOnClickListener { showLiveLoadAdjustDialog() }
        btnMonitorEditConditions.setOnClickListener { showScreen(1) }
        btnMonitorMapZoomIn.setOnClickListener { routePreviewMonitor.zoomIn() }
        btnMonitorMapZoomOut.setOnClickListener { routePreviewMonitor.zoomOut() }

        btnSimDrain.setOnClickListener { handleDemoDrainTick() }
        btnSimWarn60.setOnClickListener { handleDemoWarn60() }
        btnSimWarn30.setOnClickListener { handleDemoWarn30() }
        btnSimFallback.setOnClickListener { handleDemoFallbackToggle() }
        btnStopV2l.setOnClickListener { showManualLockoutConfirmDialog() }

        // Screen 4 리스너
        btnUtilityBack.setOnClickListener { showScreen(3) }
        btnUtilityComplete.setOnClickListener { resetAndReturnHome() }
    }

    private fun initTextToSpeech() {
        runCatching {
            textToSpeech = TextToSpeech(this) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    val res = textToSpeech?.setLanguage(Locale.KOREAN)
                    isTtsReady = res != TextToSpeech.LANG_MISSING_DATA &&
                        res != TextToSpeech.LANG_NOT_SUPPORTED
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

    private fun addAlertLog(title: String, detail: String) {
        val ts = SimpleDateFormat("HH:mm", Locale.KOREA).format(Date())
        SessionStore.alertLogs.add(0, "[$ts] $title - $detail")
        if (SessionStore.alertLogs.size > 20) {
            SessionStore.alertLogs.removeAt(SessionStore.alertLogs.lastIndex)
        }
    }

    // =========================================================
    // 초기화: 차량 SDK, 위치(GPS), 백엔드 동기화
    // =========================================================
    private fun onlineStatusNote(): String =
        if (SessionStore.isPleosConnected) "차량 연결됨" else "서버 연결됨"

    private fun offlineStatusNote(): String =
        if (SessionStore.isPleosConnected) "오프라인 · 차량 연결" else "오프라인 안전 모드"

    private fun ensureLocalStateRestored() {
        if (!::localStateStore.isInitialized) {
            localStateStore = LocalStateStore(this)
        }
        if (SessionStore.initialized) return

        localStateStore.loadServerUrl()?.let { cachedUrl ->
            val normalized = ApiClient.normalizeBaseUrl(cachedUrl)
            if (ApiClient.updateBaseUrl(normalized)) {
                SessionStore.currentServerUrl = normalized
            }
        }
        localStateStore.loadUserVehicle()?.let { cachedVehicle ->
            SessionStore.userVehicleModel = cachedVehicle.vehicleModel
            SessionStore.userBatteryCapacity = cachedVehicle.batteryCapacity
            SessionStore.userCurbWeight = cachedVehicle.curbWeight
            SessionStore.userAvgEfficiency = cachedVehicle.avgEfficiency
        }
        localStateStore.loadHomeFavorite()?.let { cachedHome ->
            SessionStore.savedHomeFavorite = cachedHome
            if (SessionStore.destIsHome && SessionStore.destLat == null) {
                SessionStore.destName = cachedHome.name
                SessionStore.destLat = cachedHome.latitude
                SessionStore.destLng = cachedHome.longitude
                SessionStore.destAddress = cachedHome.address
                SessionStore.destSub = cachedHome.address?.takeIf { it.isNotBlank() } ?: "저장된 우리 집 위치"
            }
        }
    }

    private fun initVehicleAndBackend() {
        ensureLocalStateRestored()
        vehicleManager = PleosVehicleManager(this)
        vehicleManager.onLiveSocChanged = { liveSoc ->
            SessionStore.currentSocPercent = liveSoc
            updateHeaderAndMetaUI()

            val sessionId = SessionStore.activeSessionId
            if (SessionStore.currentScreenIndex == 3 && sessionId != null && !SessionStore.isLockoutTriggered) {
                val prevSynced = lastSyncedLiveSoc
                if (!isSyncingLiveSoc && (prevSynced == null || abs(liveSoc - prevSynced) >= 0.1)) {
                    syncLiveSocToBackend(sessionId, liveSoc)
                }
            }
        }

        if (SessionStore.initialized) {
            fetchUserProfileFromBackend(retryOnStartup = false)
            return
        }

        if (!SessionStore.gpsAttempted) {
            SessionStore.gpsAttempted = true
            requestActiveDeviceLocation(userInitiated = false)
        }

        vehicleManager.initialize { connected, _ ->
            SessionStore.isPleosConnected = connected
            vehicleManager.readVehicleTelemetry(
                fallbackSoc = SessionStore.currentSocPercent,
                fallbackEfficiency = SessionStore.userAvgEfficiency
            ) { soc, eff, fromSdk ->
                if (fromSdk) {
                    SessionStore.currentSocPercent = soc
                    SessionStore.userAvgEfficiency = eff
                }
                SessionStore.initialized = true
                updateHeaderAndMetaUI()
                fetchUserProfileFromBackend(retryOnStartup = true)
            }
        }
    }

    private fun requestActiveDeviceLocation(userInitiated: Boolean) {
        val fineGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!fineGranted && !coarseGranted) {
            pendingLocationUserInitiated = userInitiated
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
            return
        }

        if (!userInitiated && (SessionStore.originManuallySelected || SessionStore.activeSessionId != null || isStartingMonitoringSession)) {
            return
        }

        activeLocationRequestSeq++
        val mySeq = activeLocationRequestSeq
        activeLocationJob?.cancel()

        isFetchingDeviceLocation = true
        syncScreen1UI()
        if (userInitiated) {
            toast("실시간 GPS 위치를 확인하고 있습니다.")
        }

        activeLocationJob = lifecycleScope.launch {
            var completedCleanly = false
            try {
                val result = LocationHelper.fetchSingleKoreanOrBestLocation(
                    context = this@MainActivity,
                    fineGranted = fineGranted,
                    coarseGranted = coarseGranted,
                    forceActiveRefresh = userInitiated
                )
                if (mySeq != activeLocationRequestSeq || isFinishing || isDestroyed) return@launch
                completedCleanly = true
                isFetchingDeviceLocation = false
                handleActiveLocationResult(result, userInitiated)
            } finally {
                if (!completedCleanly && mySeq == activeLocationRequestSeq && !isDestroyed) {
                    isFetchingDeviceLocation = false
                    syncScreen1UI()
                }
            }
        }
    }

    private fun handleActiveLocationResult(result: ActiveLocationResult, userInitiated: Boolean) {
        Log.i("VlockerGps", "handleActiveLocationResult userInitiated=$userInitiated result=$result")
        when (result) {
            is ActiveLocationResult.KoreanLocation -> {
                if (!userInitiated && (SessionStore.originManuallySelected || SessionStore.activeSessionId != null || isStartingMonitoringSession)) {
                    syncScreen1UI()
                    return
                }
                SessionStore.originManuallySelected = false
                SessionStore.originLat = result.candidate.latitude
                SessionStore.originLng = result.candidate.longitude
                SessionStore.originName = "현재 내 위치"
                SessionStore.originModeNote = result.sourceNote
                invalidatePredictCache()
                syncAllUiFromStore()
                if (userInitiated) {
                    toast("현재 GPS 위치(${result.sourceNote}) 기준으로 경로와 충전소를 다시 계산합니다.")
                }
                predictRoute(targetScreen = SessionStore.currentScreenIndex, silent = true)
            }
            is ActiveLocationResult.OverseasLocation -> {
                syncScreen1UI()
                if (userInitiated) {
                    val coordsText = String.format(
                        Locale.US,
                        "%.2f, %.2f",
                        result.candidate.latitude,
                        result.candidate.longitude
                    )
                    toast("현재 기기 위치($coordsText)가 한국 외 지역입니다. 서울 또는 강릉을 선택할 수 있습니다.")
                }
            }
            is ActiveLocationResult.Unavailable -> {
                syncScreen1UI()
                if (userInitiated) {
                    val msg = when (result.reason) {
                        UnavailableReason.PERMISSION_MISSING ->
                            "위치 권한이 필요합니다."
                        UnavailableReason.LOCATION_SERVICE_OFF ->
                            "기기 위치(GPS) 설정이 꺼져 있습니다. 설정을 켜거나 서울 또는 강릉을 선택해 주세요."
                        UnavailableReason.NO_PROVIDER_ENABLED ->
                            "사용 가능한 위치 수신 장치가 없습니다. 서울 또는 강릉을 선택해 주세요."
                        UnavailableReason.TIMEOUT_OR_EMPTY ->
                            "GPS 위치를 시간 내 수신하지 못했습니다. 잠시 후 다시 시도하거나 서울·강릉을 선택해 주세요."
                    }
                    toast(msg)
                }
            }
        }
    }

    private fun setOriginPreset(lat: Double, lng: Double, name: String, note: String) {
        activeLocationRequestSeq++
        activeLocationJob?.cancel()
        isFetchingDeviceLocation = false
        SessionStore.originManuallySelected = true
        SessionStore.originLat = lat
        SessionStore.originLng = lng
        SessionStore.originName = name
        SessionStore.originModeNote = note
        invalidatePredictCache()
        syncAllUiFromStore()
        predictRoute(targetScreen = SessionStore.currentScreenIndex, silent = true)
    }

    private fun shouldUseInstantOfflinePath(): Boolean =
        SessionStore.hasCompletedStartupConnectivityCheck && !SessionStore.isBackendOnline

    private fun probeBackendConnectionQuietly() {
        lifecycleScope.launch {
            val userRes = runCatching { ApiClient.service.getUserVehicle() }.getOrNull()?.data
            if (userRes != null && !isFinishing && !isDestroyed) {
                applyUserVehicle(userRes)
                SessionStore.isBackendOnline = true
                SessionStore.hasCompletedStartupConnectivityCheck = true
                SessionStore.backendStatusNote = onlineStatusNote()
                if (SessionStore.lastPredictData?.weather?.source == "Offline Local Safety Engine" && !SessionStore.forceFallbackDemo) {
                    invalidatePredictCache()
                }
                updateHeaderAndMetaUI()
                fetchSavedHomeFavoriteAndPreview()
            }
        }
    }

    private fun fetchUserProfileFromBackend(retryOnStartup: Boolean) {
        lifecycleScope.launch {
            var result = runCatching { ApiClient.service.getUserVehicle() }
            if (result.isFailure && SessionStore.currentServerUrl.startsWith("http://10.0.2.2:")) {
                val reverseUrl = SessionStore.currentServerUrl.replace("10.0.2.2", "127.0.0.1")
                ApiClient.updateBaseUrl(reverseUrl)
                val reverseResult = runCatching { ApiClient.service.getUserVehicle() }
                if (reverseResult.isSuccess) {
                    SessionStore.currentServerUrl = reverseUrl
                    localStateStore.saveServerUrl(reverseUrl)
                    result = reverseResult
                } else {
                    ApiClient.updateBaseUrl(SessionStore.currentServerUrl)
                }
            }

            result
                .onSuccess { res ->
                    res.data?.let { applyUserVehicle(it) }
                    SessionStore.isBackendOnline = true
                    SessionStore.hasCompletedStartupConnectivityCheck = true
                    SessionStore.backendStatusNote = onlineStatusNote()
                    if (SessionStore.lastPredictData?.weather?.source == "Offline Local Safety Engine" && !SessionStore.forceFallbackDemo) {
                        invalidatePredictCache()
                    }
                    updateHeaderAndMetaUI()
                    fetchSavedHomeFavoriteAndPreview()
                }
                .onFailure {
                    Log.w("VlockerNet", "GET /api/user failed: ${it.message}")
                    if (!hasFreshPrediction()) {
                        SessionStore.lastPredictData = buildOfflinePredictData()
                        SessionStore.lastPredictKey = currentPredictCacheKey()
                    }
                    if (retryOnStartup) {
                        SessionStore.isBackendOnline = false
                        SessionStore.backendStatusNote = "연결 확인 중"
                        syncAllUiFromStore()
                        delay(2200)
                        fetchUserProfileFromBackend(retryOnStartup = false)
                    } else {
                        SessionStore.isBackendOnline = false
                        SessionStore.hasCompletedStartupConnectivityCheck = true
                        SessionStore.backendStatusNote = offlineStatusNote()
                        syncAllUiFromStore()
                    }
                }
        }
    }

    private fun fetchSavedHomeFavoriteAndPreview() {
        lifecycleScope.launch {
            runCatching { ApiClient.service.listFavorites() }
                .onSuccess { res ->
                    val favorites = res.data?.favorites.orEmpty()
                    val home = favorites.firstOrNull {
                        it.name.contains("우리 집") || it.name.contains("우리집")
                    } ?: favorites.firstOrNull()
                    if (home != null) {
                        SessionStore.savedHomeFavorite = home
                        localStateStore.saveHomeFavorite(home)
                    }
                    if (SessionStore.destIsHome && home != null && SessionStore.destLat == null) {
                        SessionStore.destName = home.name
                        SessionStore.destLat = home.latitude
                        SessionStore.destLng = home.longitude
                        SessionStore.destAddress = home.address
                        SessionStore.destSub = home.address?.takeIf { it.isNotBlank() } ?: "저장된 우리 집 위치"
                        syncAllUiFromStore()
                    }
                }
            if (!hasFreshPrediction()) {
                predictRoute(targetScreen = SessionStore.currentScreenIndex, silent = true)
            }
        }
    }

    private fun applyUserVehicle(data: UserVehicleData) {
        SessionStore.userVehicleModel = data.vehicleModel
        SessionStore.userBatteryCapacity = data.batteryCapacity
        SessionStore.userCurbWeight = data.curbWeight
        SessionStore.userAvgEfficiency = data.avgEfficiency
        if (::localStateStore.isInitialized) {
            localStateStore.saveUserVehicle(data)
        }
        updateHeaderAndMetaUI()
    }

    // =========================================================
    // Screen 1 동작: 목적지 검색, 우리 집, 충전소, 출발 위치/시간 변경
    // =========================================================
    private fun searchPlacesFromBackend() {
        val keyword = etSearchQuery.text.toString().trim()
        if (keyword.isEmpty()) {
            toast("집 주소 또는 충전소 이름을 입력해 주세요.")
            return
        }
        hideKeyboard()

        fun showOfflinePlaceResults() {
            SessionStore.isBackendOnline = false
            SessionStore.hasCompletedStartupConnectivityCheck = true
            SessionStore.backendStatusNote = offlineStatusNote()
            syncAllUiFromStore()
            val offlinePlaces = OfflineSafetyEngine.searchOfflinePlaces(
                query = keyword,
                originLat = SessionStore.originLat,
                originLng = SessionStore.originLng,
                limit = 5
            )
            renderPlaceDropdown(offlinePlaces)
            if (offlinePlaces.isEmpty()) {
                toast("오프라인 검색: 우리 집, 강릉, 서울, 가평, 대전, 부산 등을 입력하거나 '가까운 급속 충전소'를 누르세요.")
            } else {
                toast("오프라인 저장 목록에서 '${keyword}' 검색 결과 ${offlinePlaces.size}건을 불러왔습니다.")
            }
        }

        if (shouldUseInstantOfflinePath()) {
            showOfflinePlaceResults()
            return
        }

        lifecycleScope.launch {
            btnSearchSubmit.isEnabled = false
            runCatching { ApiClient.service.searchPlaces(keyword, 5) }
                .onSuccess { res ->
                    val places = res.data?.places.orEmpty()
                    renderPlaceDropdown(places)
                    SessionStore.isBackendOnline = true
                    SessionStore.hasCompletedStartupConnectivityCheck = true
                    SessionStore.backendStatusNote = onlineStatusNote()
                    updateHeaderAndMetaUI()
                    if (places.isEmpty()) {
                        toast("검색 결과가 없습니다. 다른 이름이나 주소를 입력해 주세요.")
                    }
                }
                .onFailure { e ->
                    Log.w("VlockerNet", "searchPlaces offline fallback: ${e.message}")
                    showOfflinePlaceResults()
                }
            btnSearchSubmit.isEnabled = true
        }
    }

    private fun renderPlaceDropdown(places: List<PlaceItem>) {
        layoutPlaceDropdown.removeAllViews()
        if (places.isEmpty()) {
            layoutPlaceDropdown.visibility = View.GONE
            return
        }
        layoutPlaceDropdown.visibility = View.VISIBLE
        val padH = dimenPx(R.dimen.vl_dropdown_row_pad_h)
        val padV = dimenPx(R.dimen.vl_dropdown_row_pad_v)
        val rowMinH = dimenPx(R.dimen.vl_dropdown_row_min_h)
        val titlePx = dimenFloat(R.dimen.vl_dropdown_title_text)
        val subPx = dimenFloat(R.dimen.vl_dropdown_sub_text)
        val dividerPx = dimenPx(R.dimen.vl_stroke_thin).coerceAtLeast(1)

        places.forEachIndexed { idx, place ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                minimumHeight = rowMinH
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(padH, padV, padH, padV)
                setOnClickListener {
                    selectDestination(
                        name = place.name,
                        sub = place.address.ifBlank { "검색된 목적지" },
                        lat = place.lat,
                        lng = place.lng,
                        address = place.address,
                        isHome = false
                    )
                    layoutPlaceDropdown.visibility = View.GONE
                }
            }
            val title = TextView(this).apply {
                text = place.name
                setTextSize(TypedValue.COMPLEX_UNIT_PX, titlePx)
                setTextColor(Color.parseColor("#182725"))
                setTypeface(null, Typeface.BOLD)
            }
            val addr = TextView(this).apply {
                text = place.address
                setTextSize(TypedValue.COMPLEX_UNIT_PX, subPx)
                setTextColor(Color.parseColor("#667672"))
            }
            row.addView(title)
            row.addView(addr)
            layoutPlaceDropdown.addView(row)

            if (idx < places.lastIndex) {
                layoutPlaceDropdown.addView(View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dividerPx)
                    setBackgroundColor(Color.parseColor("#E3E8E4"))
                })
            }
        }
    }

    private fun applyHomeFavoriteSelection() {
        val home = SessionStore.savedHomeFavorite
        if (home != null) {
            selectDestination(
                name = home.name,
                sub = home.address?.takeIf { it.isNotBlank() } ?: "저장된 우리 집 목적지",
                lat = home.latitude,
                lng = home.longitude,
                address = home.address,
                isHome = true
            )
        } else {
            selectDestination(
                name = "우리 집",
                sub = "저장된 기본 목적지 (길게 눌러 현재 검색지 저장)",
                lat = 37.4979,
                lng = 127.0276,
                address = "서울 강남구 기본 등록지",
                isHome = true
            )
        }
        layoutPlaceDropdown.visibility = View.GONE
    }

    private fun saveCurrentDestinationAsHome() {
        val lat = SessionStore.destLat
        val lng = SessionStore.destLng
        if (lat == null || lng == null) {
            toast("먼저 목적지를 검색해 선택한 뒤 길게 눌러 우리 집으로 저장하세요.")
            return
        }
        val req = FavoriteCreateRequest(
            name = "우리 집",
            address = SessionStore.destAddress ?: SessionStore.destName,
            latitude = lat,
            longitude = lng
        )
        lifecycleScope.launch {
            runCatching { ApiClient.service.upsertFavorite(req) }
                .onSuccess { res ->
                    val saved = res.data ?: FavoriteItem(
                        id = 0,
                        name = "우리 집",
                        address = req.address,
                        latitude = lat,
                        longitude = lng
                    )
                    SessionStore.savedHomeFavorite = saved
                    localStateStore.saveHomeFavorite(saved)
                    SessionStore.destIsHome = true
                    SessionStore.destName = "우리 집"
                    SessionStore.destSub = req.address ?: "우리 집 저장 완료"
                    syncAllUiFromStore()
                    toast("현재 목적지가 '우리 집'으로 저장되었습니다.")
                }
                .onFailure { e ->
                    Log.w("VlockerNet", "upsertFavorite offline save: ${e.message}")
                    val offlineSaved = FavoriteItem(
                        id = 0,
                        name = "우리 집",
                        address = req.address,
                        latitude = lat,
                        longitude = lng
                    )
                    SessionStore.savedHomeFavorite = offlineSaved
                    localStateStore.saveHomeFavorite(offlineSaved)
                    SessionStore.isBackendOnline = false
                    SessionStore.backendStatusNote = offlineStatusNote()
                    SessionStore.destIsHome = true
                    SessionStore.destName = "우리 집"
                    SessionStore.destSub = req.address ?: "오프라인 기기 저장 완료"
                    syncAllUiFromStore()
                    toast("서버 미연결 상태: '우리 집' 목적지를 현재 기기에 저장했습니다.")
                }
        }
    }

    private fun findNearestChargerShortcut() {
        fun showOfflineChargers() {
            val chargers = OfflineSafetyEngine.searchOfflineNearbyChargers(
                lat = SessionStore.originLat,
                lng = SessionStore.originLng,
                limit = 5
            )
            SessionStore.isBackendOnline = false
            SessionStore.hasCompletedStartupConnectivityCheck = true
            SessionStore.backendStatusNote = offlineStatusNote()
            syncAllUiFromStore()
            val nearest = chargers.firstOrNull()
            if (nearest == null) {
                toast("주변 급속 충전소를 찾지 못했습니다.")
            } else {
                selectDestination(
                    name = nearest.name,
                    sub = String.format(Locale.US, "%s · 약 %.1f km", nearest.address, nearest.distanceKm),
                    lat = nearest.lat,
                    lng = nearest.lng,
                    address = nearest.address,
                    isHome = false
                )
                layoutPlaceDropdown.visibility = View.GONE
                toast("오프라인 기준 가까운 국내 급속 충전소를 불러왔습니다.")
                if (chargers.size > 1) {
                    showChargerChooserDialog(chargers)
                }
            }
        }

        if (shouldUseInstantOfflinePath()) {
            showOfflineChargers()
            return
        }

        lifecycleScope.launch {
            btnSelectCharger.isEnabled = false
            runCatching {
                ApiClient.service.searchNearbyChargers(SessionStore.originLat, SessionStore.originLng)
            }.onSuccess { res ->
                val chargers = res.data?.chargers.orEmpty()
                SessionStore.isBackendOnline = true
                SessionStore.hasCompletedStartupConnectivityCheck = true
                if (chargers.isEmpty()) {
                    toast("주변에서 충전소를 찾지 못했습니다.")
                } else {
                    val nearest = chargers.first()
                    selectDestination(
                        name = nearest.name,
                        sub = String.format(Locale.US, "%s · 직선 %.1f km", nearest.address, nearest.distanceKm),
                        lat = nearest.lat,
                        lng = nearest.lng,
                        address = nearest.address,
                        isHome = false
                    )
                    layoutPlaceDropdown.visibility = View.GONE
                    if (chargers.size > 1) {
                        showChargerChooserDialog(chargers)
                    }
                }
            }.onFailure { e ->
                Log.w("VlockerNet", "searchNearbyChargers offline fallback: ${e.message}")
                showOfflineChargers()
            }
            btnSelectCharger.isEnabled = true
        }
    }

    private fun showChargerChooserDialog(chargers: List<ChargerItem>) {
        val items = chargers.map { c ->
            String.format(Locale.US, "%s (%.1f km)", c.name, c.distanceKm)
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("${SessionStore.originName} 기준 가까운 충전소")
            .setItems(items) { _, which ->
                val picked = chargers[which]
                selectDestination(
                    name = picked.name,
                    sub = String.format(Locale.US, "%s · 직선 %.1f km", picked.address, picked.distanceKm),
                    lat = picked.lat,
                    lng = picked.lng,
                    address = picked.address,
                    isHome = false
                )
            }
            .setPositiveButton("닫기", null)
            .showScaled()
    }

    private fun selectDestination(
        name: String,
        sub: String,
        lat: Double?,
        lng: Double?,
        address: String?,
        isHome: Boolean
    ) {
        SessionStore.destName = name
        SessionStore.destSub = sub
        SessionStore.destLat = lat
        SessionStore.destLng = lng
        SessionStore.destAddress = address
        SessionStore.destIsHome = isHome
        invalidatePredictCache()
        syncAllUiFromStore()
        predictRoute(targetScreen = 1, silent = true)
    }

    private fun showOriginPickerDialog() {
        val options = arrayOf(
            "내 현재 위치 (GPS 실시간 확인)",
            "서울 한강 난지캠핑장 (서울 테스트)",
            "강릉 안반데기 (겨울 산간 차박 기본)"
        )
        val preselected = when {
            SessionStore.originName.contains("현재") -> 0
            SessionStore.originName.contains("서울") -> 1
            else -> 2
        }
        var pickedIdx = preselected

        AlertDialog.Builder(this)
            .setTitle("출발 위치 선택 (${SessionStore.originModeNote})")
            .setSingleChoiceItems(options, preselected) { _, which ->
                pickedIdx = which
            }
            .setPositiveButton("적용") { _, _ ->
                when (pickedIdx) {
                    0 -> requestActiveDeviceLocation(userInitiated = true)
                    1 -> setOriginPreset(37.5699, 126.8744, "서울 한강 캠핑장", "서울 테스트")
                    else -> setOriginPreset(37.6242, 128.7420, "강릉 안반데기", "차박 기본")
                }
            }
            .setNegativeButton("취소", null)
            .showScaled()
    }

    private fun showTripConditionsDialog() {
        val padH = dimenPx(R.dimen.vl_dialog_pad_h)
        val padV = dimenPx(R.dimen.vl_dialog_pad_v)
        val gap = dimenPx(R.dimen.vl_dialog_item_spacing)
        val labelPx = dimenFloat(R.dimen.vl_dialog_field_label_text)
        val bodyPx = dimenFloat(R.dimen.vl_dialog_body_text)
        val inputPx = dimenFloat(R.dimen.vl_dialog_input_text)
        val minTouch = dimenPx(R.dimen.vl_touch_min_height)

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padH, padV, padH, padV / 2)
        }

        fun header(text: String) = TextView(this).apply {
            this.text = text
            setTextSize(TypedValue.COMPLEX_UNIT_PX, labelPx)
            setTextColor(Color.parseColor("#667672"))
            setPadding(0, gap, 0, gap / 2)
        }

        val depOptions = listOf("내일 오전 07:00", "내일 오전 10:00", "내일 오후 12:00")
        val depGroup = RadioGroup(this).apply { orientation = RadioGroup.HORIZONTAL }
        depOptions.forEachIndexed { i, label ->
            val rb = RadioButton(this).apply {
                id = 100 + i
                text = label.replace("내일 ", "")
                setTextSize(TypedValue.COMPLEX_UNIT_PX, bodyPx)
                minHeight = minTouch
                setPadding(gap / 2, 0, gap * 2, 0)
                isChecked = label == SessionStore.departureTime
            }
            depGroup.addView(rb)
        }

        val passOptions = listOf(1, 2, 4)
        val passGroup = RadioGroup(this).apply { orientation = RadioGroup.HORIZONTAL }
        passOptions.forEachIndexed { i, count ->
            val rb = RadioButton(this).apply {
                id = 200 + i
                text = "${count}명"
                setTextSize(TypedValue.COMPLEX_UNIT_PX, bodyPx)
                minHeight = minTouch
                setPadding(gap / 2, 0, gap * 2, 0)
                isChecked = count == SessionStore.passengers
            }
            passGroup.addView(rb)
        }

        val etLoad = EditText(this).apply {
            setText(String.format(Locale.US, "%.1f", SessionStore.v2lLoadKw))
            setTextSize(TypedValue.COMPLEX_UNIT_PX, inputPx)
            minHeight = minTouch
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            imeOptions = EditorInfo.IME_ACTION_DONE or EditorInfo.IME_FLAG_NO_EXTRACT_UI
            hint = "예: 1.2 (0.2 ~ 3.6)"
        }

        val cbFallback = CheckBox(this).apply {
            text = "통신 장애 시 기본 안전 기준(영하 5도, 오르막 640m) 사용 테스트"
            setTextSize(TypedValue.COMPLEX_UNIT_PX, bodyPx)
            minHeight = minTouch
            setPadding(gap / 2, gap, 0, 0)
            isChecked = SessionStore.forceFallbackDemo
        }

        container.addView(header("내일 예상 출발 시각"))
        container.addView(depGroup)
        container.addView(header("함께 귀가하는 인원"))
        container.addView(passGroup)
        container.addView(header("현재 캠핑 전력 사용량 (kW, 최대 3.6)"))
        container.addView(etLoad)
        container.addView(cbFallback)

        val scrollWrapper = ScrollView(this).apply { addView(container) }

        AlertDialog.Builder(this)
            .setTitle("귀가 일정 및 전력 설정")
            .setView(scrollWrapper)
            .setPositiveButton("적용") { _, _ ->
                val depIdx = (depGroup.checkedRadioButtonId - 100).coerceIn(0, depOptions.lastIndex)
                val passIdx = (passGroup.checkedRadioButtonId - 200).coerceIn(0, passOptions.lastIndex)
                val parsedLoad = etLoad.text.toString().toDoubleOrNull()?.coerceIn(0.2, 3.6) ?: SessionStore.v2lLoadKw

                val previousLoad = SessionStore.v2lLoadKw
                SessionStore.departureTime = depOptions[depIdx]
                SessionStore.passengers = passOptions[passIdx]
                SessionStore.v2lLoadKw = parsedLoad
                SessionStore.forceFallbackDemo = cbFallback.isChecked
                invalidatePredictCache()
                syncAllUiFromStore()
                predictRoute(targetScreen = 1, silent = true)

                val activeSid = SessionStore.activeSessionId
                if (activeSid != null && !SessionStore.isLockoutTriggered && abs(parsedLoad - previousLoad) >= 0.05) {
                    syncLiveLoadToBackend(activeSid, parsedLoad)
                }
            }
            .setNegativeButton("취소", null)
            .showScaled()
    }

    // =========================================================
    // AI 배터리 소모 예측 (/api/predict)
    // =========================================================
    private fun currentPredictCacheKey(): String {
        return "${SessionStore.originLat},${SessionStore.originLng}->${SessionStore.destName}:${SessionStore.destLat}:${SessionStore.departureTime}:${SessionStore.passengers}:${SessionStore.v2lLoadKw}:${SessionStore.currentSocPercent.roundToInt()}:${SessionStore.userBatteryCapacity}:${SessionStore.userAvgEfficiency}:${SessionStore.forceFallbackDemo}"
    }

    private fun invalidatePredictCache() {
        SessionStore.lastPredictData = null
        SessionStore.lastPredictKey = null
    }

    private fun hasFreshPrediction(): Boolean {
        return SessionStore.lastPredictData != null && SessionStore.lastPredictKey == currentPredictCacheKey()
    }

    private fun buildOfflinePredictData(): PredictData {
        val basePred = OfflineSafetyEngine.buildOfflinePrediction(
            destinationName = SessionStore.destName.ifBlank { "우리 집" },
            originLat = SessionStore.originLat,
            originLng = SessionStore.originLng,
            destLat = SessionStore.destLat,
            destLng = SessionStore.destLng,
            passengers = SessionStore.passengers,
            currentSocPercent = SessionStore.currentSocPercent,
            batteryCapacityKwh = SessionStore.userBatteryCapacity,
            avgEfficiencyKmKwh = SessionStore.userAvgEfficiency,
            curbWeightKg = SessionStore.userCurbWeight.toDouble(),
            v2lCurrentLoadKw = SessionStore.v2lLoadKw,
            vehicleModel = SessionStore.userVehicleModel
        )
        val cachedPolyline = if (!SessionStore.forceFallbackDemo && ::localStateStore.isInitialized) {
            localStateStore.loadMatchingRoutePolyline(
                originLat = basePred.destination.originLat,
                originLng = basePred.destination.originLng,
                destLat = basePred.destination.destLat,
                destLng = basePred.destination.destLng
            )
        } else {
            null
        }
        return if (cachedPolyline != null) {
            basePred.copy(
                navigation = basePred.navigation.copy(
                    geometryType = "CACHED_ROAD",
                    polylinePoints = cachedPolyline
                )
            )
        } else {
            basePred
        }
    }

    private fun predictRoute(
        targetScreen: Int,
        autoStartMonitoring: Boolean = false,
        silent: Boolean = false,
        forceNetworkRetry: Boolean = false
    ) {
        val requestKey = currentPredictCacheKey()
        if (!forceNetworkRetry && shouldUseInstantOfflinePath()) {
            predictRequestSeq++
            predictJob?.cancel()
            inFlightPredictKey = null
            inFlightPredictServerUrl = null
            pendingPredictTargetScreen = null
            pendingAutoStartMonitoring = false
            pendingPredictIsExplicitNav = false
            progressRouteLoading.visibility = View.GONE
            btnGoToMargin.isEnabled = true

            SessionStore.isBackendOnline = false
            SessionStore.backendStatusNote = offlineStatusNote()
            SessionStore.lastPredictData = buildOfflinePredictData()
            SessionStore.lastPredictKey = requestKey
            syncAllUiFromStore()
            if (autoStartMonitoring) {
                startMonitoringSession()
            } else if (!silent && targetScreen != SessionStore.currentScreenIndex) {
                showScreen(targetScreen)
            }
            return
        }
        val requestServerUrl = SessionStore.currentServerUrl
        if (predictJob?.isActive == true &&
            inFlightPredictKey == requestKey &&
            inFlightPredictServerUrl == requestServerUrl
        ) {
            if (!silent || targetScreen != SessionStore.currentScreenIndex) {
                pendingPredictTargetScreen = targetScreen
            }
            if (autoStartMonitoring) {
                pendingAutoStartMonitoring = true
            }
            if (!silent || autoStartMonitoring) {
                pendingPredictIsExplicitNav = true
            }
            return
        }

        val preservePendingNav = silent && !autoStartMonitoring &&
            predictJob?.isActive == true && pendingPredictIsExplicitNav
        val prevTargetScreen = pendingPredictTargetScreen
        val prevAutoStart = pendingAutoStartMonitoring

        predictRequestSeq++
        val mySeq = predictRequestSeq
        predictJob?.cancel()
        inFlightPredictKey = requestKey
        inFlightPredictServerUrl = requestServerUrl
        if (preservePendingNav) {
            pendingPredictTargetScreen = prevTargetScreen
            pendingAutoStartMonitoring = prevAutoStart
            pendingPredictIsExplicitNav = true
        } else {
            pendingPredictTargetScreen = targetScreen
            pendingAutoStartMonitoring = autoStartMonitoring
            pendingPredictIsExplicitNav = !silent || autoStartMonitoring
        }

        val request = PredictRequest(
            destinationName = SessionStore.destName.ifBlank { "우리 집" },
            originLat = SessionStore.originLat,
            originLng = SessionStore.originLng,
            destLat = SessionStore.destLat,
            destLng = SessionStore.destLng,
            departureTime = SessionStore.departureTime,
            passengers = SessionStore.passengers,
            currentSocPercent = SessionStore.currentSocPercent,
            avgEfficiencyKmKwh = SessionStore.userAvgEfficiency,
            batteryCapacityKwh = SessionStore.userBatteryCapacity,
            curbWeightKg = SessionStore.userCurbWeight.toDouble(),
            v2lCurrentLoadKw = SessionStore.v2lLoadKw,
            forceFallback = SessionStore.forceFallbackDemo
        )

        predictJob = lifecycleScope.launch {
            progressRouteLoading.visibility = View.VISIBLE
            btnGoToMargin.isEnabled = false
            try {
                val res = ApiClient.service.predictConsumption(request)
                if (mySeq != predictRequestSeq || isFinishing || isDestroyed) return@launch
                val data = res.data
                val destScreen = pendingPredictTargetScreen ?: targetScreen
                val shouldStart = pendingAutoStartMonitoring
                val wasExplicitNav = pendingPredictIsExplicitNav
                pendingPredictTargetScreen = null
                pendingAutoStartMonitoring = false
                pendingPredictIsExplicitNav = false

                if (data != null) {
                    SessionStore.lastPredictData = data
                    SessionStore.lastPredictKey = requestKey
                    val pts = data.navigation.polylinePoints.orEmpty()
                    if (data.navigation.geometryType == "REAL_ROAD" && pts.size >= 2 && ::localStateStore.isInitialized) {
                        localStateStore.saveCachedRoutePolyline(
                            originLat = data.destination.originLat,
                            originLng = data.destination.originLng,
                            destLat = data.destination.destLat,
                            destLng = data.destination.destLng,
                            points = pts
                        )
                    }
                    SessionStore.isBackendOnline = true
                    SessionStore.hasCompletedStartupConnectivityCheck = true
                    SessionStore.backendStatusNote = onlineStatusNote()
                    syncAllUiFromStore()
                } else {
                    SessionStore.isBackendOnline = false
                    SessionStore.hasCompletedStartupConnectivityCheck = true
                    SessionStore.backendStatusNote = offlineStatusNote()
                    SessionStore.lastPredictData = buildOfflinePredictData()
                    SessionStore.lastPredictKey = requestKey
                    syncAllUiFromStore()
                    if (!silent) {
                        toast("서버 응답 이상: 로컬 안전 엔진(영하 5°C, 오르막 640m 기준)으로 계산했습니다.")
                    }
                }

                if (shouldStart) {
                    startMonitoringSession()
                } else if (wasExplicitNav && destScreen != SessionStore.currentScreenIndex) {
                    showScreen(destScreen)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (mySeq != predictRequestSeq || isFinishing || isDestroyed) return@launch
                Log.w("VlockerNet", "predictRoute offline fallback: ${e.message}")
                SessionStore.isBackendOnline = false
                SessionStore.hasCompletedStartupConnectivityCheck = true
                SessionStore.backendStatusNote = offlineStatusNote()
                SessionStore.lastPredictData = buildOfflinePredictData()
                SessionStore.lastPredictKey = requestKey

                val destScreen = pendingPredictTargetScreen ?: targetScreen
                val shouldStart = pendingAutoStartMonitoring
                val wasExplicitNav = pendingPredictIsExplicitNav
                pendingPredictTargetScreen = null
                pendingAutoStartMonitoring = false
                pendingPredictIsExplicitNav = false

                syncAllUiFromStore()
                if (!silent) {
                    toast("서버 미연결 상태: 로컬 안전 엔진(영하 5°C, 오르막 640m 기준)으로 계산했습니다.")
                }
                if (shouldStart) {
                    startMonitoringSession()
                } else if (wasExplicitNav && destScreen != SessionStore.currentScreenIndex) {
                    showScreen(destScreen)
                }
            } finally {
                if (mySeq == predictRequestSeq && !isDestroyed) {
                    inFlightPredictKey = null
                    inFlightPredictServerUrl = null
                    pendingPredictTargetScreen = null
                    pendingAutoStartMonitoring = false
                    pendingPredictIsExplicitNav = false
                    progressRouteLoading.visibility = View.GONE
                    btnGoToMargin.isEnabled = true
                }
            }
        }
    }

    private fun updateSelectedMargin(mode: String) {
        SessionStore.selectedMarginMode = mode
        syncMarginSelectionUI()
        syncEnergyAnalysisUI()
    }

    private fun activateStartedSession(
        session: SessionStatusData,
        predict: PredictData,
        isOnline: Boolean
    ) {
        val nowMs = System.currentTimeMillis()
        SessionStore.activeSessionId = session.sessionId
        SessionStore.activeSessionStatus = session
        SessionStore.extendedDestLat = null
        SessionStore.extendedDestLng = null
        SessionStore.extendedPolylinePoints = emptyList()
        SessionStore.extendedGeometryType = null
        SessionStore.lastShownWarningLevel = null
        SessionStore.isLockoutTriggered = false
        SessionStore.lastLockoutData = null
        SessionStore.isBackendOnline = isOnline
        SessionStore.backendStatusNote = if (isOnline) onlineStatusNote() else offlineStatusNote()
        SessionStore.isMonitorFeedDelayed = false
        SessionStore.hasUnsyncedLocalState = false
        SessionStore.hasLoggedOfflineMonitorSwitch = !isOnline
        SessionStore.lastMonitorSyncTimeMs = nowMs
        if (session.v2lStatus != "LOCKED_OUT") {
            SessionStore.v2lLoadKw = session.currentV2lLoadKw
        }
        isSyncingLiveSoc = false
        lastSyncedLiveSoc = if (isOnline) session.currentSoc else null
        val initialPowerSample = if (session.v2lStatus == "LOCKED_OUT") 0.0 else session.currentV2lLoadKw
        SessionStore.powerTrendTracker.resetWithInitial(initialPowerSample, nowMs)
        addAlertLog(
            if (isOnline) "안전 모니터링 시작" else "오프라인 안전 모니터링 시작",
            "${predict.destination.name} · 예약 잔량 ${UiFormatters.formatPercentWholeOrDecimal(session.targetSoc)}"
        )
        syncAllUiFromStore()
        showScreen(3, immediateMonitorRefresh = false)
        if (session.v2lStatus != "ACTIVE") {
            handleSessionTransition(session, recordPowerSample = false, syncedFromBackend = isOnline)
        }
    }

    private fun startMonitoringSession() {
        if (isStartingMonitoringSession) return
        val predict = SessionStore.lastPredictData
        if (predict == null) {
            predictRoute(targetScreen = 3, autoStartMonitoring = true)
            return
        }
        val rec = predict.recommendations[SessionStore.selectedMarginMode] ?: return

        fun buildOfflineStartSession(): SessionStatusData = OfflineSafetyEngine.createLocalSession(
            destinationName = predict.destination.name,
            distanceKm = predict.navigation.distanceKm,
            elevationGainM = predict.navigation.elevationGainM,
            expectedTemp = predict.weather.temperature,
            departureTime = SessionStore.departureTime,
            marginMode = SessionStore.selectedMarginMode,
            targetSoc = rec.reserveSocPercent,
            currentSoc = SessionStore.currentSocPercent,
            currentV2lLoadKw = SessionStore.v2lLoadKw,
            batteryCapacityKwh = SessionStore.userBatteryCapacity,
            isFallback = true
        )

        if (shouldUseInstantOfflinePath()) {
            stopMonitorPolling()
            monitorMutationEpoch++
            activateStartedSession(session = buildOfflineStartSession(), predict = predict, isOnline = false)
            toast("서버 미연결: 기기 내부 안전 엔진으로 모니터링을 시작합니다.")
            return
        }

        val req = CreateSessionRequest(
            destinationName = predict.destination.name,
            distanceKm = predict.navigation.distanceKm,
            elevationGainM = predict.navigation.elevationGainM,
            expectedTemp = predict.weather.temperature,
            departureTime = SessionStore.departureTime,
            passengers = SessionStore.passengers,
            marginMode = SessionStore.selectedMarginMode,
            targetSoc = rec.reserveSocPercent,
            currentSoc = SessionStore.currentSocPercent,
            currentV2lLoadKw = SessionStore.v2lLoadKw,
            isFallback = predict.isFallback || SessionStore.forceFallbackDemo
        )

        isStartingMonitoringSession = true
        stopMonitorPolling()
        monitorMutationEpoch++

        lifecycleScope.launch {
            btnStartMonitor.isEnabled = false
            btnEnergyGoMonitor.isEnabled = false
            try {
                val res = ApiClient.service.createSession(req)
                val session = res.data
                if (session != null) {
                    SessionStore.hasCompletedStartupConnectivityCheck = true
                    activateStartedSession(session = session, predict = predict, isOnline = true)
                } else {
                    SessionStore.hasCompletedStartupConnectivityCheck = true
                    activateStartedSession(session = buildOfflineStartSession(), predict = predict, isOnline = false)
                    toast("서버 미연결: 기기 내부 안전 엔진으로 모니터링을 시작합니다.")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("VlockerNet", "createSession offline fallback: ${e.message}")
                SessionStore.hasCompletedStartupConnectivityCheck = true
                activateStartedSession(session = buildOfflineStartSession(), predict = predict, isOnline = false)
                toast("서버 미연결: 기기 내부 안전 엔진으로 모니터링을 시작합니다.")
            } finally {
                isStartingMonitoringSession = false
                if (!isDestroyed) {
                    btnStartMonitor.isEnabled = true
                    btnEnergyGoMonitor.isEnabled = true
                }
            }
        }
    }

    // =========================================================
    // Screen 3 동작: 실시간 자동 갱신 루프, 시연 컨트롤, 충전소 연장, 차단
    // =========================================================
    private fun startMonitorPollingIfEligible(immediateFirstPoll: Boolean) {
        val sid = SessionStore.activeSessionId
        if (!isActivityStarted ||
            SessionStore.currentScreenIndex != 3 ||
            sid == null ||
            SessionStore.isLockoutTriggered ||
            SessionStore.activeSessionStatus?.v2lStatus == "LOCKED_OUT"
        ) {
            stopMonitorPolling()
            return
        }

        if (monitorPollingJob?.isActive == true && !immediateFirstPoll) {
            return
        }

        monitorPollingJob?.cancel()
        monitorPollingJob = lifecycleScope.launch {
            if (!immediateFirstPoll) {
                delay(MONITOR_POLL_INTERVAL_MS)
            }
            while (isActive &&
                isActivityStarted &&
                SessionStore.currentScreenIndex == 3 &&
                SessionStore.activeSessionId != null &&
                !SessionStore.isLockoutTriggered &&
                SessionStore.activeSessionStatus?.v2lStatus != "LOCKED_OUT"
            ) {
                performSingleMonitorPoll()
                delay(MONITOR_POLL_INTERVAL_MS)
            }
        }
    }

    private fun stopMonitorPolling() {
        monitorPollingJob?.cancel()
        monitorPollingJob = null
    }

    private suspend fun readVehicleTelemetrySuspend(
        fallbackSoc: Double,
        fallbackEfficiency: Double
    ): Triple<Double, Double, Boolean> = suspendCancellableCoroutine { cont ->
        vehicleManager.readVehicleTelemetry(
            fallbackSoc = fallbackSoc,
            fallbackEfficiency = fallbackEfficiency
        ) { soc, eff, fromSdk ->
            if (cont.isActive) {
                cont.resume(Triple(soc, eff, fromSdk))
            }
        }
    }

    private suspend fun performSingleMonitorPoll() {
        val sid = SessionStore.activeSessionId ?: return
        if (!isActivityStarted || SessionStore.currentScreenIndex != 3 || SessionStore.isLockoutTriggered) {
            return
        }
        if (isManualMonitorCallInFlight || isSyncingLiveSoc) {
            return
        }

        val capturedEpoch = monitorMutationEpoch
        var liveSdkSoc: Double? = null
        if (SessionStore.isPleosConnected) {
            val (sdkSoc, sdkEff, fromSdk) = readVehicleTelemetrySuspend(
                fallbackSoc = SessionStore.currentSocPercent,
                fallbackEfficiency = SessionStore.userAvgEfficiency
            )
            if (fromSdk) {
                liveSdkSoc = sdkSoc
                if (sdkEff > 0.5) {
                    SessionStore.userAvgEfficiency = sdkEff
                }
            }
        }

        if (isFinishing || isDestroyed ||
            SessionStore.activeSessionId != sid ||
            SessionStore.currentScreenIndex != 3 ||
            SessionStore.isLockoutTriggered ||
            capturedEpoch != monitorMutationEpoch
        ) {
            return
        }

        if (sid == OfflineSafetyEngine.LOCAL_OFFLINE_SESSION_ID) {
            val currentSession = SessionStore.activeSessionStatus ?: return
            val nextSoc = liveSdkSoc ?: SessionStore.currentSocPercent
            val updatedLocal = OfflineSafetyEngine.updateSessionLocally(
                session = currentSession,
                newSoc = nextSoc,
                newLoadKw = SessionStore.v2lLoadKw,
                batteryCapacityKwh = SessionStore.userBatteryCapacity
            )
            SessionStore.isBackendOnline = false
            SessionStore.backendStatusNote = offlineStatusNote()
            SessionStore.isMonitorFeedDelayed = false
            handleSessionTransition(updatedLocal, recordPowerSample = true, syncedFromBackend = false)
            return
        }

        val prevSoc = lastSyncedLiveSoc
        val socToPush = liveSdkSoc ?: SessionStore.currentSocPercent
        val shouldPushSoc = SessionStore.hasUnsyncedLocalState ||
            (liveSdkSoc != null && (prevSoc == null || abs(liveSdkSoc - prevSoc) >= 0.1))

        try {
            val res = if (shouldPushSoc) {
                ApiClient.service.syncSessionSoc(
                    sid,
                    SocSyncRequest(currentSoc = socToPush, loadKw = SessionStore.v2lLoadKw)
                )
            } else {
                ApiClient.service.getSessionStatus(sid)
            }

            if (isFinishing || isDestroyed ||
                SessionStore.activeSessionId != sid ||
                SessionStore.isLockoutTriggered ||
                capturedEpoch != monitorMutationEpoch
            ) {
                return
            }

            val session = res.data ?: return
            SessionStore.isBackendOnline = true
            SessionStore.backendStatusNote = onlineStatusNote()
            SessionStore.isMonitorFeedDelayed = false
            SessionStore.hasUnsyncedLocalState = false
            SessionStore.hasLoggedOfflineMonitorSwitch = false
            handleSessionTransition(session, recordPowerSample = true, syncedFromBackend = true)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (isFinishing || isDestroyed || capturedEpoch != monitorMutationEpoch) return
            Log.w("VlockerMonitor", "Monitor poll offline fallback: ${e.message}")
            SessionStore.isBackendOnline = false
            SessionStore.backendStatusNote = offlineStatusNote()
            SessionStore.isMonitorFeedDelayed = true
            if (!SessionStore.hasLoggedOfflineMonitorSwitch) {
                SessionStore.hasLoggedOfflineMonitorSwitch = true
                addAlertLog("통신 장애 · 로컬 안전 감시 전환", "서버 미연결 상태에서도 기기 내부에서 잔량을 계속 보호합니다.")
            }
            val currentSession = SessionStore.activeSessionStatus
            if (currentSession != null) {
                val evaluatedSoc = liveSdkSoc ?: SessionStore.currentSocPercent
                if (liveSdkSoc != null) {
                    SessionStore.hasUnsyncedLocalState = true
                }
                val updatedLocal = OfflineSafetyEngine.updateSessionLocally(
                    session = currentSession,
                    newSoc = evaluatedSoc,
                    newLoadKw = SessionStore.v2lLoadKw,
                    batteryCapacityKwh = SessionStore.userBatteryCapacity
                )
                handleSessionTransition(updatedLocal, recordPowerSample = true, syncedFromBackend = false)
            } else {
                updateHeaderAndMetaUI()
                syncScreen3MonitorUI()
            }
        }
    }

    private fun refreshActiveSession(recordPowerSample: Boolean = true) {
        val sid = SessionStore.activeSessionId ?: return
        if (sid == OfflineSafetyEngine.LOCAL_OFFLINE_SESSION_ID) {
            SessionStore.activeSessionStatus?.let {
                handleSessionTransition(it, recordPowerSample = recordPowerSample, syncedFromBackend = false)
            }
            return
        }
        val capturedEpoch = monitorMutationEpoch
        lifecycleScope.launch {
            try {
                val res = ApiClient.service.getSessionStatus(sid)
                if (isFinishing || isDestroyed ||
                    SessionStore.activeSessionId != sid ||
                    capturedEpoch != monitorMutationEpoch
                ) {
                    return@launch
                }
                val session = res.data ?: return@launch
                SessionStore.isBackendOnline = true
                SessionStore.backendStatusNote = onlineStatusNote()
                SessionStore.isMonitorFeedDelayed = false
                SessionStore.hasUnsyncedLocalState = false
                handleSessionTransition(session, recordPowerSample = recordPowerSample, syncedFromBackend = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("VlockerMonitor", "refreshActiveSession failed: ${e.message}")
            }
        }
    }

    private fun syncLiveSocToBackend(sessionId: Int, liveSoc: Double) {
        if (isSyncingLiveSoc) return
        if (sessionId == OfflineSafetyEngine.LOCAL_OFFLINE_SESSION_ID) {
            val current = SessionStore.activeSessionStatus ?: return
            val updated = OfflineSafetyEngine.updateSessionLocally(
                session = current,
                newSoc = liveSoc,
                newLoadKw = SessionStore.v2lLoadKw,
                batteryCapacityKwh = SessionStore.userBatteryCapacity
            )
            handleSessionTransition(updated, recordPowerSample = true, syncedFromBackend = false)
            return
        }
        isSyncingLiveSoc = true
        monitorMutationEpoch++
        lifecycleScope.launch {
            try {
                val res = ApiClient.service.syncSessionSoc(
                    sessionId,
                    SocSyncRequest(currentSoc = liveSoc, loadKw = SessionStore.v2lLoadKw)
                )
                val session = res.data
                if (session != null && SessionStore.activeSessionId == sessionId) {
                    SessionStore.isBackendOnline = true
                    SessionStore.backendStatusNote = onlineStatusNote()
                    SessionStore.isMonitorFeedDelayed = false
                    SessionStore.hasUnsyncedLocalState = false
                    handleSessionTransition(session, recordPowerSample = true, syncedFromBackend = true)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("VlockerMonitor", "syncLiveSocToBackend offline fallback: ${e.message}")
                if (SessionStore.activeSessionId == sessionId) {
                    SessionStore.isBackendOnline = false
                    SessionStore.backendStatusNote = offlineStatusNote()
                    SessionStore.isMonitorFeedDelayed = true
                    SessionStore.hasUnsyncedLocalState = true
                    SessionStore.activeSessionStatus?.let { current ->
                        val updated = OfflineSafetyEngine.updateSessionLocally(
                            session = current,
                            newSoc = liveSoc,
                            newLoadKw = SessionStore.v2lLoadKw,
                            batteryCapacityKwh = SessionStore.userBatteryCapacity
                        )
                        handleSessionTransition(updated, recordPowerSample = true, syncedFromBackend = false)
                    }
                }
            } finally {
                isSyncingLiveSoc = false
            }
        }
    }

    private fun applyLocalLoadChange(newLoadKw: Double) {
        val current = SessionStore.activeSessionStatus ?: return
        SessionStore.v2lLoadKw = newLoadKw
        val updated = OfflineSafetyEngine.updateSessionLocally(
            session = current,
            newSoc = current.currentSoc,
            newLoadKw = newLoadKw,
            batteryCapacityKwh = SessionStore.userBatteryCapacity
        )
        handleSessionTransition(updated, recordPowerSample = true, syncedFromBackend = false)
    }

    private fun syncLiveLoadToBackend(sessionId: Int, newLoadKw: Double) {
        if (sessionId == OfflineSafetyEngine.LOCAL_OFFLINE_SESSION_ID) {
            monitorMutationEpoch++
            applyLocalLoadChange(newLoadKw)
            return
        }
        val targetSoc = SessionStore.activeSessionStatus?.currentSoc ?: SessionStore.currentSocPercent
        monitorMutationEpoch++
        isManualMonitorCallInFlight = true
        lifecycleScope.launch {
            try {
                val res = ApiClient.service.syncSessionSoc(
                    sessionId,
                    SocSyncRequest(currentSoc = targetSoc, loadKw = newLoadKw)
                )
                val session = res.data
                if (session != null && SessionStore.activeSessionId == sessionId) {
                    SessionStore.v2lLoadKw = session.currentV2lLoadKw
                    SessionStore.isBackendOnline = true
                    SessionStore.backendStatusNote = onlineStatusNote()
                    SessionStore.isMonitorFeedDelayed = false
                    SessionStore.hasUnsyncedLocalState = false
                    handleSessionTransition(session, recordPowerSample = true, syncedFromBackend = true)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("VlockerMonitor", "syncLiveLoadToBackend offline fallback: ${e.message}")
                if (SessionStore.activeSessionId == sessionId) {
                    SessionStore.isBackendOnline = false
                    SessionStore.backendStatusNote = offlineStatusNote()
                    SessionStore.isMonitorFeedDelayed = true
                    SessionStore.hasUnsyncedLocalState = true
                    applyLocalLoadChange(newLoadKw)
                    toast(String.format(Locale.US, "서버 미연결: 소비 전력(%.1f kW)을 로컬 감시에 즉시 반영했습니다.", newLoadKw))
                }
            } finally {
                isManualMonitorCallInFlight = false
            }
        }
    }

    private fun showLiveLoadAdjustDialog() {
        val sid = SessionStore.activeSessionId
        if (sid == null || SessionStore.isLockoutTriggered || SessionStore.activeSessionStatus?.v2lStatus == "LOCKED_OUT") {
            toast("V2L 모니터링이 실행 중일 때만 사용 전력을 조절할 수 있습니다.")
            return
        }

        val padH = dimenPx(R.dimen.vl_dialog_pad_h)
        val padV = dimenPx(R.dimen.vl_dialog_pad_v)
        val gap = dimenPx(R.dimen.vl_dialog_item_spacing)
        val bodyPx = dimenFloat(R.dimen.vl_dialog_body_text)
        val inputPx = dimenFloat(R.dimen.vl_dialog_input_text)
        val minTouch = dimenPx(R.dimen.vl_touch_min_height)

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padH, padV, padH, padV / 2)
        }

        val hintText = TextView(this).apply {
            text = "현재 연결된 캠핑 가전의 전체 사용 전력(0.2 ~ 3.6 kW)을 입력하면 실시간 그래프와 남은 시간이 바로 반영됩니다."
            setTextSize(TypedValue.COMPLEX_UNIT_PX, bodyPx)
            setLineSpacing(0f, 1.2f)
            setTextColor(Color.parseColor("#667672"))
            setPadding(0, 0, 0, gap)
        }
        val etLoad = EditText(this).apply {
            setText(String.format(Locale.US, "%.1f", SessionStore.v2lLoadKw))
            setTextSize(TypedValue.COMPLEX_UNIT_PX, inputPx)
            minHeight = minTouch
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            imeOptions = EditorInfo.IME_ACTION_DONE or EditorInfo.IME_FLAG_NO_EXTRACT_UI
            hint = "예: 1.2 (0.2 ~ 3.6)"
        }
        container.addView(hintText)
        container.addView(etLoad)

        AlertDialog.Builder(this)
            .setTitle("실시간 V2L 소비 전력 변경")
            .setView(container)
            .setPositiveButton("적용") { _, _ ->
                val parsed = etLoad.text.toString().toDoubleOrNull()?.coerceIn(0.2, 3.6)
                if (parsed != null) {
                    SessionStore.v2lLoadKw = parsed
                    syncLiveLoadToBackend(sid, parsed)
                } else {
                    toast("올바른 전력 값(0.2 ~ 3.6 kW)을 입력해 주세요.")
                }
            }
            .setNegativeButton("취소", null)
            .showScaled()
    }

    private fun handleDemoDrainTick() {
        val sid = SessionStore.activeSessionId
        if (sid == null) {
            startMonitoringSession()
            return
        }
        val active = SessionStore.activeSessionStatus
        val drop = if (active != null && (active.currentSoc - active.targetSoc) in 2.5..8.0) {
            2.0
        } else {
            5.0
        }
        callTickSession(sid, drop)
    }

    private fun handleDemoWarn60() {
        val sid = SessionStore.activeSessionId
        val active = SessionStore.activeSessionStatus
        if (sid != null && active != null && !SessionStore.isLockoutTriggered && active.currentSoc > active.targetSoc + 1.4) {
            val desiredSoc = active.targetSoc + 1.3
            val drop = (active.currentSoc - desiredSoc).coerceAtLeast(0.5)
            callTickSession(sid, drop)
        } else {
            showWarn60mDialog()
            speakSafetyNotice("배터리 보호를 위해 약 1시간 후 외부 전원 공급이 종료됩니다.")
            addAlertLog("차단 1시간 전 안내", "곧 예약 잔량에 도달할 예정입니다.")
        }
    }

    private fun handleDemoWarn30() {
        val sid = SessionStore.activeSessionId
        val active = SessionStore.activeSessionStatus
        if (sid != null && active != null && !SessionStore.isLockoutTriggered && active.currentSoc > active.targetSoc + 0.6) {
            val desiredSoc = active.targetSoc + 0.6
            val drop = (active.currentSoc - desiredSoc).coerceAtLeast(0.4)
            callTickSession(sid, drop)
        } else {
            showWarn30mDialog()
            speakSafetyNotice("긴급 안내입니다. 약 30분 후 외부 전원이 차단됩니다. 필요하면 가까운 충전소로 사용을 연장할 수 있습니다.")
            addAlertLog("차단 30분 전 긴급 안내", "가까운 충전소 경유로 사용 연장이 가능합니다.")
        }
    }

    private fun handleDemoFallbackToggle() {
        SessionStore.forceFallbackDemo = !SessionStore.forceFallbackDemo
        invalidatePredictCache()
        syncFallbackBannerUI()
        if (SessionStore.forceFallbackDemo) {
            addAlertLog("안전 기본 기준 전환", "통신 장애 대비 영하 5도, 오르막 640m 기준 보호 적용")
            toast("네트워크 장애 시 안전 기본 기준(영하 5도, 오르막 640m) 전환 시연을 켰습니다.")
        } else {
            toast("실시간 기상 및 경로 연결 모드로 복구했습니다.")
        }
        predictRoute(
            targetScreen = SessionStore.currentScreenIndex,
            silent = true,
            forceNetworkRetry = !SessionStore.forceFallbackDemo
        )
    }

    private fun applyLocalTick(decrementPct: Double) {
        val existing = SessionStore.activeSessionStatus ?: return
        val nextSoc = (existing.currentSoc - decrementPct).coerceAtLeast(0.0)
        val localSession = existing.copy(sessionId = OfflineSafetyEngine.LOCAL_OFFLINE_SESSION_ID)
        val updated = OfflineSafetyEngine.updateSessionLocally(
            session = localSession,
            newSoc = nextSoc,
            newLoadKw = SessionStore.v2lLoadKw,
            batteryCapacityKwh = SessionStore.userBatteryCapacity
        )
        SessionStore.activeSessionId = OfflineSafetyEngine.LOCAL_OFFLINE_SESSION_ID
        SessionStore.isBackendOnline = false
        SessionStore.backendStatusNote = offlineStatusNote()
        SessionStore.isMonitorFeedDelayed = false
        SessionStore.hasUnsyncedLocalState = false
        handleSessionTransition(updated, recordPowerSample = true, syncedFromBackend = false)
    }

    private fun callTickSession(sessionId: Int, decrementPct: Double) {
        monitorMutationEpoch++
        if (sessionId == OfflineSafetyEngine.LOCAL_OFFLINE_SESSION_ID) {
            applyLocalTick(decrementPct)
            return
        }
        isManualMonitorCallInFlight = true
        lifecycleScope.launch {
            try {
                val res = ApiClient.service.tickSession(
                    sessionId,
                    TickRequest(socDecrement = decrementPct, loadKw = SessionStore.v2lLoadKw)
                )
                val updated = res.data
                if (updated != null) {
                    SessionStore.isBackendOnline = true
                    SessionStore.backendStatusNote = onlineStatusNote()
                    SessionStore.isMonitorFeedDelayed = false
                    SessionStore.hasUnsyncedLocalState = false
                    handleSessionTransition(updated, recordPowerSample = true, syncedFromBackend = true)
                } else {
                    applyLocalTick(decrementPct)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("VlockerMonitor", "tickSession offline fallback: ${e.message}")
                applyLocalTick(decrementPct)
            } finally {
                isManualMonitorCallInFlight = false
            }
        }
    }

    private fun handleSessionTransition(
        session: SessionStatusData,
        recordPowerSample: Boolean = true,
        syncedFromBackend: Boolean = true
    ) {
        val nowMs = System.currentTimeMillis()
        SessionStore.activeSessionStatus = session
        SessionStore.currentSocPercent = session.currentSoc
        if (session.v2lStatus != "LOCKED_OUT") {
            SessionStore.v2lLoadKw = session.currentV2lLoadKw
        }
        if (syncedFromBackend) {
            lastSyncedLiveSoc = session.currentSoc
        }
        SessionStore.lastMonitorSyncTimeMs = nowMs

        if (recordPowerSample) {
            val sampleKw = if (session.v2lStatus == "LOCKED_OUT") 0.0 else session.currentV2lLoadKw
            SessionStore.powerTrendTracker.recordSample(sampleKw, nowMs)
        }
        syncAllUiFromStore()

        when (session.v2lStatus) {
            "LOCKED_OUT" -> {
                stopMonitorPolling()
                if (!SessionStore.isLockoutTriggered) {
                    addAlertLog("V2L 자동 차단 실행", "예약 잔량 ${UiFormatters.formatPercentWholeOrDecimal(session.targetSoc)} 도달 · 난방 21도 전환")
                    triggerFinalLockoutSequence(session.sessionId)
                }
            }
            "WARN_30M" -> {
                if (SessionStore.lastShownWarningLevel != 30) {
                    SessionStore.lastShownWarningLevel = 30
                    addAlertLog("차단 30분 전 긴급 안내", "잔량 ${UiFormatters.formatPercentWholeOrDecimal(session.currentSoc)} · 연장 또는 준비 필요")
                    showWarn30mDialog()
                    speakSafetyNotice("긴급 안내입니다. 약 30분 후 외부 전원이 차단됩니다. 필요하면 가까운 충전소로 사용을 연장할 수 있습니다.")
                }
            }
            "WARN_60M" -> {
                if (SessionStore.lastShownWarningLevel == null) {
                    SessionStore.lastShownWarningLevel = 60
                    addAlertLog("차단 1시간 전 안내", "잔량 ${UiFormatters.formatPercentWholeOrDecimal(session.currentSoc)} · 1시간 내 종료 예정")
                    showWarn60mDialog()
                    speakSafetyNotice("배터리 보호를 위해 약 1시간 후 외부 전원 공급이 종료됩니다.")
                }
            }
            else -> {
                SessionStore.lastShownWarningLevel = null
            }
        }
    }

    private fun showWarn60mDialog() {
        AlertDialog.Builder(this)
            .setTitle("차단 1시간 전 안내")
            .setMessage("귀가 예약 잔량에 도달하기까지 약 1시간 남았습니다.\n전원이 차단되면 차 안이 춥지 않도록 실내 난방(21도)이 자동으로 켜집니다.")
            .setPositiveButton("확인", null)
            .showScaled()
    }

    private fun showWarn30mDialog() {
        AlertDialog.Builder(this)
            .setTitle("차단 30분 전 긴급 안내")
            .setMessage("약 30분 뒤 귀가 배터리를 지키기 위해 외부 전원이 자동으로 꺼집니다.\n집 대신 가까운 급속 충전소로 경로를 바꿔 V2L 사용 시간을 늘릴까요?")
            .setPositiveButton("가까운 충전소로 연장") { _, _ ->
                showExtendConfirmDialog()
            }
            .setNegativeButton("유지하고 확인", null)
            .showScaled()
    }

    private fun showExtendConfirmDialog() {
        val sid = SessionStore.activeSessionId
        if (sid == null) {
            toast("실시간 모니터링을 시작한 뒤 사용 연장을 진행할 수 있습니다.")
            return
        }
        lifecycleScope.launch {
            var onlineCharger: ChargerItem? = null
            if (sid != OfflineSafetyEngine.LOCAL_OFFLINE_SESSION_ID && SessionStore.isBackendOnline) {
                onlineCharger = runCatching {
                    ApiClient.service.searchNearbyChargers(SessionStore.originLat, SessionStore.originLng)
                }.getOrNull()?.data?.chargers?.firstOrNull()
            }
            val charger = onlineCharger ?: OfflineSafetyEngine.searchOfflineNearbyChargers(
                lat = SessionStore.originLat,
                lng = SessionStore.originLng,
                limit = 1
            ).firstOrNull()

            if (isFinishing || isDestroyed) return@launch

            val chargerLabel = if (charger != null) {
                val suffix = if (onlineCharger == null) " · 오프라인 기준" else ""
                String.format(Locale.US, "%s (약 %.1f km%s)", charger.name, charger.distanceKm, suffix)
            } else {
                "인근 급속 충전소"
            }

            AlertDialog.Builder(this@MainActivity)
                .setTitle("가까운 충전소로 V2L 사용 연장")
                .setMessage("목적지를 [$chargerLabel] 기준으로 다시 계산해 캠핑 전력을 더 오래 쓰도록 안전 기준을 낮출까요?")
                .setPositiveButton("연장 적용") { _, _ ->
                    executeExtendSession(sid, charger)
                }
                .setNegativeButton("취소", null)
                .showScaled()
        }
    }

    private fun applyLocalExtension(charger: ChargerItem?) {
        val currentSession = SessionStore.activeSessionStatus ?: return
        val fallbackCharger = charger ?: OfflineSafetyEngine.searchOfflineNearbyChargers(
            lat = SessionStore.originLat,
            lng = SessionStore.originLng,
            limit = 1
        ).first()
        val localSource = currentSession.copy(sessionId = OfflineSafetyEngine.LOCAL_OFFLINE_SESSION_ID)
        val (updatedSession, extData) = OfflineSafetyEngine.extendSessionLocally(
            session = localSource,
            chargerName = fallbackCharger.name,
            distanceKm = fallbackCharger.distanceKm,
            batteryCapacityKwh = SessionStore.userBatteryCapacity,
            avgEfficiencyKmKwh = SessionStore.userAvgEfficiency,
            curbWeightKg = SessionStore.userCurbWeight.toDouble(),
            passengers = SessionStore.passengers
        )
        SessionStore.activeSessionId = OfflineSafetyEngine.LOCAL_OFFLINE_SESSION_ID
        SessionStore.isBackendOnline = false
        SessionStore.backendStatusNote = offlineStatusNote()
        SessionStore.isMonitorFeedDelayed = false
        SessionStore.hasUnsyncedLocalState = false
        SessionStore.lastShownWarningLevel = null
        SessionStore.extendedDestLat = fallbackCharger.lat
        SessionStore.extendedDestLng = fallbackCharger.lng
        SessionStore.extendedPolylinePoints = RouteGeometry.buildEstimatedPolyline(
            originLat = SessionStore.originLat,
            originLng = SessionStore.originLng,
            destLat = fallbackCharger.lat,
            destLng = fallbackCharger.lng
        )
        SessionStore.extendedGeometryType = "ESTIMATED"
        val addedText = UiFormatters.formatDurationHours(extData.additionalHours)
        addAlertLog("충전소 경유 사용 연장 (오프라인)", "${extData.newDestination} · +$addedText 연장")
        toast("${extData.newDestination} 기준으로 $addedText 더 사용할 수 있습니다.")
        handleSessionTransition(updatedSession, recordPowerSample = true, syncedFromBackend = false)
    }

    private fun executeExtendSession(sessionId: Int, charger: ChargerItem?) {
        monitorMutationEpoch++
        if (sessionId == OfflineSafetyEngine.LOCAL_OFFLINE_SESSION_ID) {
            applyLocalExtension(charger)
            return
        }
        isManualMonitorCallInFlight = true
        lifecycleScope.launch {
            try {
                val res = ApiClient.service.extendSession(
                    sessionId,
                    ExtendRequest(
                        originLat = SessionStore.originLat,
                        originLng = SessionStore.originLng,
                        nearestChargerName = charger?.name,
                        distanceKm = charger?.distanceKm,
                        destLat = charger?.lat,
                        destLng = charger?.lng
                    )
                )
                val ext = res.data
                if (ext != null) {
                    SessionStore.lastShownWarningLevel = null
                    val extLat = ext.destLat ?: charger?.lat
                    val extLng = ext.destLng ?: charger?.lng
                    SessionStore.extendedDestLat = extLat
                    SessionStore.extendedDestLng = extLng
                    val poly = ext.polylinePoints.orEmpty()
                    if (poly.size >= 2) {
                        SessionStore.extendedPolylinePoints = poly
                        SessionStore.extendedGeometryType = ext.geometryType ?: "REAL_ROAD"
                    } else if (extLat != null && extLng != null) {
                        SessionStore.extendedPolylinePoints = RouteGeometry.buildEstimatedPolyline(
                            originLat = SessionStore.originLat,
                            originLng = SessionStore.originLng,
                            destLat = extLat,
                            destLng = extLng
                        )
                        SessionStore.extendedGeometryType = ext.geometryType ?: "ESTIMATED"
                    }
                    val addedText = UiFormatters.formatDurationHours(ext.additionalHours)
                    addAlertLog("충전소 경유 사용 연장", "${ext.newDestination} · +$addedText 연장")
                    toast("${ext.newDestination} 기준으로 $addedText 더 사용할 수 있습니다.")
                    refreshActiveSession(recordPowerSample = true)
                } else {
                    applyLocalExtension(charger)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("VlockerMonitor", "extendSession offline fallback: ${e.message}")
                applyLocalExtension(charger)
            } finally {
                isManualMonitorCallInFlight = false
            }
        }
    }

    private fun showManualLockoutConfirmDialog() {
        val sid = SessionStore.activeSessionId
        if (sid == null) {
            toast("진행 중인 모니터링이 없습니다.")
            return
        }
        AlertDialog.Builder(this)
            .setTitle("V2L 전원 차단 확인")
            .setMessage("지금 외부 전원을 차단하고 귀가 배터리를 안전하게 보존할까요?\n차단 직후 실내 난방(21도)이 자동으로 유지됩니다.")
            .setNegativeButton("계속 사용", null)
            .setPositiveButton("V2L 차단하기") { _, _ ->
                triggerFinalLockoutSequence(sid)
            }
            .showScaled()
    }

    private fun buildLocalFallbackLockout(sessionId: Int): LockoutData {
        val soc = SessionStore.activeSessionStatus?.currentSoc ?: SessionStore.currentSocPercent
        val tempC = SessionStore.activeSessionStatus?.destinationSummary?.tempCelsius
            ?: SessionStore.lastPredictData?.weather?.temperature
            ?: OfflineSafetyEngine.DEFAULT_FALLBACK_TEMP_C
        return OfflineSafetyEngine.executeLocalLockout(
            sessionId = sessionId,
            currentSoc = soc,
            batteryCapacityKwh = SessionStore.userBatteryCapacity,
            avgEfficiencyKmKwh = SessionStore.userAvgEfficiency,
            expectedTempCelsius = tempC
        )
    }

    private fun finalizeLockoutWithData(lockout: LockoutData) {
        val nowMs = System.currentTimeMillis()
        SessionStore.lastLockoutData = lockout
        SessionStore.lastMonitorSyncTimeMs = nowMs
        SessionStore.isMonitorFeedDelayed = false
        SessionStore.hasUnsyncedLocalState = false
        SessionStore.activeSessionStatus = SessionStore.activeSessionStatus?.copy(
            v2lStatus = lockout.v2lStatus,
            relayConnected = lockout.relayConnected,
            utilityModeActive = lockout.utilityModeActive,
            currentV2lLoadKw = 0.0,
            estimatedHoursLeft = 0.0
        )
        SessionStore.powerTrendTracker.recordSample(0.0, nowMs)
        executeHardwareAndShowUtility(lockout)
    }

    private fun triggerFinalLockoutSequence(sessionId: Int) {
        if (SessionStore.isLockoutTriggered) return
        SessionStore.isLockoutTriggered = true
        stopMonitorPolling()
        monitorMutationEpoch++

        if (sessionId == OfflineSafetyEngine.LOCAL_OFFLINE_SESSION_ID || !SessionStore.isBackendOnline) {
            if (sessionId > 0) {
                addAlertLog("오프라인 긴급 V2L 차단 실행", "서버 미연결 · 로컬 안전 엔진으로 즉시 차단")
                lifecycleScope.launch {
                    runCatching { ApiClient.service.lockoutSession(sessionId) }
                }
            }
            finalizeLockoutWithData(buildLocalFallbackLockout(sessionId))
            return
        }

        isManualMonitorCallInFlight = true
        lifecycleScope.launch {
            try {
                val res = ApiClient.service.lockoutSession(sessionId)
                val lockout = res.data
                if (lockout != null) {
                    finalizeLockoutWithData(lockout)
                } else {
                    SessionStore.isBackendOnline = false
                    SessionStore.backendStatusNote = offlineStatusNote()
                    addAlertLog("오프라인 긴급 V2L 차단 실행", "서버 응답 미수신 · 로컬 안전 엔진으로 즉시 차단")
                    finalizeLockoutWithData(buildLocalFallbackLockout(sessionId))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("VlockerMonitor", "lockoutSession offline fail-safe: ${e.message}")
                SessionStore.isBackendOnline = false
                SessionStore.backendStatusNote = offlineStatusNote()
                addAlertLog("오프라인 긴급 V2L 차단 실행", "서버 미연결 · 로컬 안전 엔진으로 즉시 차단")
                toast("서버 미연결 상태: 로컬 안전 엔진으로 V2L 전원을 즉시 차단합니다.")
                finalizeLockoutWithData(buildLocalFallbackLockout(sessionId))
            } finally {
                isManualMonitorCallInFlight = false
            }
        }
    }

    private fun executeHardwareAndShowUtility(lockout: LockoutData) {
        vehicleManager.triggerEmergencyLockoutAndHeater(lockout.cabinTargetTempCelsius.toFloat()) { relayOk, hvacOk, detail ->
            SessionStore.lastHardwareDetail = detail
            addAlertLog(
                "V2L 안전 차단 및 난방 전환",
                "남은 배터리 ${UiFormatters.formatPercentWholeOrDecimal(lockout.bmsPreservedSoc)} · 약 ${lockout.returnDistanceGuaranteedKm.toInt()} km 주행 가능"
            )
            speakSafetyNotice("안전을 위해 외부 전원을 차단하고 실내 난방을 21도로 켰습니다.")
            syncUtilityUI(relayOk = relayOk, hvacOk = hvacOk)
            updateHeaderAndMetaUI()
            showScreen(4)
        }
    }

    private fun resetAndReturnHome() {
        stopMonitorPolling()
        monitorMutationEpoch++
        SessionStore.activeSessionId = null
        SessionStore.activeSessionStatus = null
        SessionStore.lastShownWarningLevel = null
        SessionStore.isLockoutTriggered = false
        SessionStore.lastLockoutData = null
        SessionStore.forceFallbackDemo = false
        SessionStore.lastMonitorSyncTimeMs = null
        SessionStore.isMonitorFeedDelayed = false
        SessionStore.hasUnsyncedLocalState = false
        SessionStore.hasLoggedOfflineMonitorSwitch = false
        SessionStore.powerTrendTracker.clear()
        invalidatePredictCache()
        syncAllUiFromStore()
        showScreen(1)
        predictRoute(targetScreen = 1, silent = true)
    }

    // =========================================================
    // 설정 & 알림 모달 (이모티콘 없음, 쉬운 한국어)
    // =========================================================
    private fun showAlertHistoryDialog() {
        val message = if (SessionStore.alertLogs.isEmpty()) {
            "아직 발생한 안전 알림 기록이 없습니다.\n모니터링 중 1시간 전·30분 전 알림과 차단 내역이 여기에 기록됩니다."
        } else {
            SessionStore.alertLogs.joinToString("\n\n")
        }

        val builder = AlertDialog.Builder(this)
            .setTitle("안전 알림 기록")
            .setMessage(message)
            .setPositiveButton("닫기", null)

        if (SessionStore.activeSessionId != null && !SessionStore.isLockoutTriggered) {
            builder.setNeutralButton("가까운 충전소로 연장") { _, _ -> showExtendConfirmDialog() }
        }
        builder.showScaled()
    }

    private fun showVehicleAndOriginSettingsDialog() {
        val padH = dimenPx(R.dimen.vl_dialog_pad_h)
        val padV = dimenPx(R.dimen.vl_dialog_pad_v)
        val gap = dimenPx(R.dimen.vl_dialog_item_spacing)
        val labelPx = dimenFloat(R.dimen.vl_dialog_field_label_text)
        val inputPx = dimenFloat(R.dimen.vl_dialog_input_text)
        val minTouch = dimenPx(R.dimen.vl_touch_min_height)

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padH, padV, padH, padV / 2)
        }

        fun addField(label: String, initialValue: String, numeric: Boolean = false): EditText {
            container.addView(TextView(this).apply {
                text = label
                setTextSize(TypedValue.COMPLEX_UNIT_PX, labelPx)
                setTextColor(Color.parseColor("#667672"))
                setPadding(0, gap, 0, gap / 2)
            })
            return EditText(this).apply {
                setText(initialValue)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, inputPx)
                minHeight = minTouch
                imeOptions = EditorInfo.IME_ACTION_DONE or EditorInfo.IME_FLAG_NO_EXTRACT_UI
                if (numeric) {
                    inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                }
                container.addView(this)
            }
        }

        val etUrl = addField("백엔드 서버 주소", SessionStore.currentServerUrl)
        val etModel = addField("내 차량 이름", SessionStore.userVehicleModel)
        val etCap = addField("배터리 전체 용량 (kWh)", SessionStore.userBatteryCapacity.toString(), numeric = true)
        val etWeight = addField("차량 무게 (kg)", SessionStore.userCurbWeight.toString(), numeric = true)
        val etEff = addField("평소 전비 (km/kWh)", SessionStore.userAvgEfficiency.toString(), numeric = true)

        val scrollWrapper = ScrollView(this).apply { addView(container) }

        AlertDialog.Builder(this)
            .setTitle("차량 정보 및 연결 설정")
            .setView(scrollWrapper)
            .setNeutralButton("출발 위치 변경") { _, _ -> showOriginPickerDialog() }
            .setNegativeButton("취소", null)
            .setPositiveButton("저장") { _, _ ->
                val rawUrl = etUrl.text.toString().trim().ifEmpty { SessionStore.currentServerUrl }
                val normalizedUrl = ApiClient.normalizeBaseUrl(rawUrl)
                if (ApiClient.updateBaseUrl(normalizedUrl)) {
                    SessionStore.currentServerUrl = normalizedUrl
                    localStateStore.saveServerUrl(normalizedUrl)
                } else {
                    toast("서버 주소 형식이 올바르지 않아 기존 주소를 유지합니다.")
                }

                val req = UserVehicleUpdateRequest(
                    vehicleModel = etModel.text.toString().trim().ifEmpty { SessionStore.userVehicleModel },
                    batteryCapacity = etCap.text.toString().toDoubleOrNull() ?: SessionStore.userBatteryCapacity,
                    curbWeight = etWeight.text.toString().toDoubleOrNull()?.toInt() ?: SessionStore.userCurbWeight,
                    avgEfficiency = etEff.text.toString().toDoubleOrNull() ?: SessionStore.userAvgEfficiency
                )

                lifecycleScope.launch {
                    runCatching { ApiClient.service.updateUserVehicle(req) }
                        .onSuccess { res ->
                            invalidatePredictCache()
                            res.data?.let { applyUserVehicle(it) }
                            SessionStore.isBackendOnline = true
                            SessionStore.hasCompletedStartupConnectivityCheck = true
                            SessionStore.backendStatusNote = onlineStatusNote()
                            syncAllUiFromStore()
                            toast("차량 정보가 저장되었습니다.")
                            predictRoute(
                                targetScreen = SessionStore.currentScreenIndex,
                                silent = true,
                                forceNetworkRetry = true
                            )
                        }
                        .onFailure { e ->
                            Log.w("VlockerNet", "updateUserVehicle offline save: ${e.message}")
                            invalidatePredictCache()
                            val localVehicle = UserVehicleData(
                                id = 1,
                                name = "캠퍼 드라이버",
                                vehicleModel = req.vehicleModel,
                                batteryCapacity = req.batteryCapacity,
                                curbWeight = req.curbWeight,
                                avgEfficiency = req.avgEfficiency
                            )
                            applyUserVehicle(localVehicle)
                            SessionStore.isBackendOnline = false
                            SessionStore.hasCompletedStartupConnectivityCheck = true
                            SessionStore.backendStatusNote = offlineStatusNote()
                            syncAllUiFromStore()
                            toast("서버 미연결 상태: 차량 정보를 기기에 저장하고 로컬 안전 계산에 반영했습니다.")
                            predictRoute(targetScreen = SessionStore.currentScreenIndex, silent = true)
                        }
                }
            }
            .showScaled()
    }

    // =========================================================
    // UI 동기화 (4개 레퍼런스 디자인 + 쉬운 한국어 + 큰 숫자)
    // =========================================================
    private fun syncAllUiFromStore() {
        updateHeaderAndMetaUI()
        syncFallbackBannerUI()
        syncScreen1UI()
        syncRoutePreviewWidget()
        syncScreen2MarginUI()
        syncEnergyAnalysisUI()
        syncScreen3MonitorUI()
        syncUtilityUI(
            relayOk = SessionStore.lastLockoutData?.relayConnected == false,
            hvacOk = SessionStore.lastLockoutData?.utilityModeActive == true
        )
    }

    private fun updateHeaderAndMetaUI() {
        val socFmt = UiFormatters.formatPercentWholeOrDecimal(SessionStore.currentSocPercent)
        tvTopVehicleMeta.text = "${SessionStore.userVehicleModel} · 배터리 $socFmt"

        tvTopConnection.text = SessionStore.backendStatusNote
        if (SessionStore.isBackendOnline || SessionStore.isPleosConnected) {
            dotTopConnection.setBackgroundResource(R.drawable.ic_dot_mint)
            tvTopConnection.setTextColor(ContextCompat.getColor(this, R.color.vlocker_mint))
        } else {
            dotTopConnection.setBackgroundResource(R.drawable.ic_dot_teal)
            tvTopConnection.setTextColor(ContextCompat.getColor(this, R.color.vlocker_subtle))
        }
    }

    private fun syncFallbackBannerUI() {
        val checkingStartup = !SessionStore.isBackendOnline &&
            (SessionStore.backendStatusNote == "차량 연결 확인 중" || SessionStore.backendStatusNote == "연결 확인 중")
        val showFallback = SessionStore.forceFallbackDemo ||
            (SessionStore.lastPredictData?.isFallback == true) ||
            (!SessionStore.isBackendOnline && !checkingStartup)
        cardFallbackNotice.visibility = if (showFallback) View.VISIBLE else View.GONE
        tvFallbackNotice.text = if (!SessionStore.isBackendOnline && !checkingStartup) {
            "서버에 연결되지 않아 기기 내부 안전 엔진(영하 5°C, 오르막 640m 기준)으로 배터리를 보호하고 있습니다."
        } else {
            "네트워크 연결이 원활하지 않아 안전 기본 기준(영하 5°C, 오르막 640m)으로 배터리를 보호하고 있습니다."
        }
        btnSimFallback.text = if (SessionStore.forceFallbackDemo) "실시간 복귀" else "네트워크 장애"
    }

    private fun syncScreen1UI() {
        tvSelectedDestName.text = SessionStore.destName
        tvSelectedDestSub.text = SessionStore.destSub
        ivSelectedDestCheck.visibility = View.VISIBLE
        btnSelectHome.text = if (SessionStore.destIsHome) "우리 집으로 설정" else "우리 집으로 전환"

        tvDepartureSummary.text = String.format(
            Locale.US,
            "%s · %d명 · %.1f kW",
            SessionStore.departureTime, SessionStore.passengers, SessionStore.v2lLoadKw
        )
        tvOriginDestBreadcrumb.text = if (isFetchingDeviceLocation) {
            "출발: GPS 위치 확인 중..."
        } else {
            "출발: ${SessionStore.originName} (변경)"
        }

        val distLabel = if (SessionStore.destIsHome) "우리 집까지 거리" else "목적지까지 거리"
        val pred = SessionStore.lastPredictData
        if (pred != null) {
            pillRouteMode.text = if (pred.isFallback) "기본 안전 기준" else "실시간 계산"
            tvMetricDistanceLabel.text = distLabel
            tvMetricDistanceValue.text = UiFormatters.formatDistanceKm(pred.navigation.distanceKm)
            tvMetricTimeValue.text = UiFormatters.formatDurationMinutes(pred.navigation.estimatedDurationMin)
            tvMetricElevationValue.text = String.format(Locale.US, "+%.0f m", pred.navigation.elevationGainM)
        } else {
            pillRouteMode.text = "경로 선택"
            tvMetricDistanceLabel.text = distLabel
        }
    }

    private fun syncRoutePreviewWidget() {
        val pred = SessionStore.lastPredictData
        val planOriginLat = pred?.destination?.originLat ?: SessionStore.originLat
        val planOriginLng = pred?.destination?.originLng ?: SessionStore.originLng
        val planDestLat = pred?.destination?.destLat ?: SessionStore.destLat ?: OfflineSafetyEngine.DEFAULT_HOME_LAT
        val planDestLng = pred?.destination?.destLng ?: SessionStore.destLng ?: OfflineSafetyEngine.DEFAULT_HOME_LNG
        val planDist = pred?.navigation?.distanceKm ?: 86.0
        val planElev = pred?.navigation?.elevationGainM ?: 640.0
        val planPolyline = pred?.navigation?.polylinePoints.orEmpty()
        val planGeomType = pred?.navigation?.geometryType ?: "ESTIMATED"

        routePreviewMap.setRouteInfo(
            origin = SessionStore.originName,
            dest = SessionStore.destName,
            distance = planDist,
            elevation = planElev,
            polylinePoints = planPolyline,
            originLat = planOriginLat,
            originLng = planOriginLng,
            destLat = planDestLat,
            destLng = planDestLng,
            geometryType = planGeomType,
            tealAccent = false
        )

        val activeSession = SessionStore.activeSessionStatus
        val hasExtendedRoute = activeSession != null && (
            SessionStore.extendedPolylinePoints.size >= 2 ||
                (SessionStore.extendedDestLat != null && SessionStore.extendedDestLng != null)
            )
        val monDestName = activeSession?.destinationSummary?.destination
            ?: activeSession?.destinationName
            ?: SessionStore.destName
        val monDist = activeSession?.destinationSummary?.distanceKm ?: planDist
        val monElev = activeSession?.destinationSummary?.elevationGainM ?: planElev
        val monDestLat = if (hasExtendedRoute) SessionStore.extendedDestLat ?: planDestLat else planDestLat
        val monDestLng = if (hasExtendedRoute) SessionStore.extendedDestLng ?: planDestLng else planDestLng
        val monPolyline = if (hasExtendedRoute) SessionStore.extendedPolylinePoints else planPolyline
        val monGeomType = if (hasExtendedRoute) (SessionStore.extendedGeometryType ?: "ESTIMATED") else planGeomType

        routePreviewMonitor.setRouteInfo(
            origin = SessionStore.originName,
            dest = monDestName,
            distance = monDist,
            elevation = monElev,
            polylinePoints = monPolyline,
            originLat = planOriginLat,
            originLng = planOriginLng,
            destLat = monDestLat,
            destLng = monDestLng,
            geometryType = monGeomType,
            tealAccent = true
        )
    }

    private fun syncScreen2MarginUI() {
        val pred = SessionStore.lastPredictData ?: return
        val distFmt = UiFormatters.formatDistanceKm(pred.navigation.distanceKm)
        tvPlanDestTitle.text = "${pred.destination.name}까지 $distFmt"
        tvPlanDuration.text = "예상 ${UiFormatters.formatDurationMinutes(pred.navigation.estimatedDurationMin)}"
        tvPlanDepartureBadge.text = "${SessionStore.departureTime} 출발"

        tvPlanTempValue.text = UiFormatters.formatTemperature(pred.weather.temperature)
        tvPlanElevValue.text = String.format(Locale.US, "+%.0f m", pred.navigation.elevationGainM)
        tvPlanEffValue.text = String.format(Locale.US, "%.1f km/kWh", pred.vehicle.avgEfficiencyKmKwh)
        tvPlanHvacValue.text = if (pred.breakdown.coldStartHvacSpikeKwh > 0.0) "출발 전 반영" else "최적 상태"
        tvPlanCurrentSocMeta.text = "현재 배터리 ${UiFormatters.formatPercentWholeOrDecimal(SessionStore.currentSocPercent)} 기준"

        bindPlanCard(pred.recommendations["SAFE"], tvSafeReserveNum, tvSafeUsableHours)
        bindPlanCard(pred.recommendations["NORMAL"], tvNormalReserveNum, tvNormalUsableHours)
        bindPlanCard(pred.recommendations["MAX_USE"], tvMaxReserveNum, tvMaxUsableHours)

        syncMarginSelectionUI()
    }

    private fun bindPlanCard(
        rec: MarginRecommendation?,
        tvReserve: TextView,
        tvHours: TextView
    ) {
        if (rec == null) return
        tvReserve.text = UiFormatters.formatPercentWholeOrDecimal(rec.reserveSocPercent)
        if (rec.isSufficient) {
            tvHours.text = UiFormatters.formatDurationHours(rec.v2lUsableHours)
            tvHours.setTextColor(ContextCompat.getColor(this, R.color.vlocker_teal))
        } else {
            tvHours.text = "배터리 부족"
            tvHours.setTextColor(ContextCompat.getColor(this, R.color.vlocker_alert_red))
        }
    }

    private fun syncMarginSelectionUI() {
        val mode = SessionStore.selectedMarginMode
        applyCardSelection(cardPlanSafe, ivRadioSafe, mode == "SAFE")
        applyCardSelection(cardPlanNormal, ivRadioNormal, mode == "NORMAL")
        applyCardSelection(cardPlanMax, ivRadioMax, mode == "MAX_USE")

        val selectedRec = SessionStore.lastPredictData?.recommendations?.get(mode)
        tvPlanInsufficientNotice.visibility =
            if (selectedRec != null && !selectedRec.isSufficient) View.VISIBLE else View.GONE
    }

    private fun applyCardSelection(card: MaterialCardView, radio: ImageView, selected: Boolean) {
        if (selected) {
            card.strokeColor = ContextCompat.getColor(this, R.color.vlocker_ink)
            card.strokeWidth = dimenPx(R.dimen.vl_stroke_card_selected)
            radio.setImageResource(R.drawable.ic_radio_selected)
        } else {
            card.strokeColor = ContextCompat.getColor(this, R.color.vlocker_border)
            card.strokeWidth = dimenPx(R.dimen.vl_stroke_card)
            radio.setImageResource(R.drawable.ic_radio_empty)
        }
    }

    private fun syncEnergyAnalysisUI() {
        val pred = SessionStore.lastPredictData ?: return
        val bd = pred.breakdown
        val rec = pred.recommendations[SessionStore.selectedMarginMode]

        tvEnergyTotalNeed.text = String.format(Locale.US, "%.1f kWh", bd.pureReturnKwh)
        if (rec != null) {
            tvEnergyReserveTarget.text = String.format(
                Locale.US,
                "%s (%.1f kWh)",
                UiFormatters.formatPercentWholeOrDecimal(rec.reserveSocPercent),
                rec.requiredKwh
            )
            tvEnergyUsableNow.text = String.format(
                Locale.US,
                "%.1f kWh · %s",
                rec.v2lUsableKwh,
                UiFormatters.formatDurationHours(rec.v2lUsableHours)
            )
            tvEnergySafetyMargin.text =
                UiFormatters.formatSafetyMarginBonus(rec.mode, rec.safetyMultiplier)
        }
        tvEnergyBaseDrive.text = String.format(Locale.US, "%.2f kWh", bd.baseDrivingKwh)
        tvEnergyHillClimb.text = String.format(Locale.US, "+%.2f kWh", bd.elevationKwh)
        tvEnergyColdAdjust.text = if (bd.efficiencyLossPercent > 0.0) {
            String.format(Locale.US, "-%.1f%% 반영", bd.efficiencyLossPercent)
        } else {
            "상온 최적 (손실 없음)"
        }
        tvEnergyPreHeat.text = String.format(Locale.US, "%.2f kWh", bd.coldStartHvacSpikeKwh)
    }

    private fun syncScreen3MonitorUI() {
        val session = SessionStore.activeSessionStatus
        val pred = SessionStore.lastPredictData
        val rec = pred?.recommendations?.get(SessionStore.selectedMarginMode)

        val status = session?.v2lStatus ?: if (SessionStore.isLockoutTriggered) "LOCKED_OUT" else "ACTIVE"
        val isLockedOut = status == "LOCKED_OUT"
        val currentSoc = session?.currentSoc ?: SessionStore.currentSocPercent
        val targetSoc = session?.targetSoc ?: rec?.reserveSocPercent ?: 28.0
        val loadKw = if (isLockedOut) 0.0 else (session?.currentV2lLoadKw ?: SessionStore.v2lLoadKw)
        val hoursLeft = session?.estimatedHoursLeft ?: rec?.v2lUsableHours ?: 6.6
        val remainPct = maxOf(0.0, currentSoc - targetSoc)

        when (status) {
            "LOCKED_OUT" -> {
                tvMonitorStateLabel.text = "V2L 차단 완료"
                dotMonitorState.setBackgroundResource(R.drawable.ic_dot_teal)
                tvMonitorStateLabel.setTextColor(ContextCompat.getColor(this, R.color.vlocker_subtle))
                tvMonitorSafetyStatus.text = "차단이 완료되어 실내 난방 21°C로 전환되었습니다."
            }
            "WARN_30M" -> {
                tvMonitorStateLabel.text = "차단 30분 전 긴급 안내"
                dotMonitorState.setBackgroundResource(R.drawable.ic_dot_mint)
                tvMonitorStateLabel.setTextColor(Color.parseColor("#FCA5A5"))
                tvMonitorSafetyStatus.text = "약 30분 후 전원 공급 종료 · 연장 필요 시 충전소 탐색"
            }
            "WARN_60M" -> {
                tvMonitorStateLabel.text = "차단 1시간 전 안내"
                dotMonitorState.setBackgroundResource(R.drawable.ic_dot_mint)
                tvMonitorStateLabel.setTextColor(Color.parseColor("#FDE68A"))
                tvMonitorSafetyStatus.text = "약 1시간 후 전원 공급 종료 · 차단 시 실내 난방 자동 전환"
            }
            else -> {
                tvMonitorStateLabel.text = "현재 V2L 사용 중"
                dotMonitorState.setBackgroundResource(R.drawable.ic_dot_mint)
                tvMonitorStateLabel.setTextColor(ContextCompat.getColor(this, R.color.vlocker_mint))
                tvMonitorSafetyStatus.text = "안전 계획 활성"
            }
        }

        val syncSuffix = SessionStore.lastMonitorSyncTimeMs
            ?.let { " · ${PowerTrendTracker.formatClockTime(it)}" }
            .orEmpty()
        tvMonitorLiveTag.text = when {
            isLockedOut -> "전원 차단 완료$syncSuffix"
            SessionStore.activeSessionId == OfflineSafetyEngine.LOCAL_OFFLINE_SESSION_ID ->
                if (SessionStore.isPleosConnected) "차량 연동 · 오프라인 안전 감시$syncSuffix" else "오프라인 로컬 안전 감시 중$syncSuffix"
            SessionStore.isMonitorFeedDelayed -> "서버 응답 지연 · 로컬 안전 감시 중$syncSuffix"
            SessionStore.isPleosConnected -> "차량·서버 실시간 수신$syncSuffix"
            else -> "실시간 감시 중$syncSuffix"
        }
        tvMonitorCurrentSoc.text = UiFormatters.formatPercentWholeOrDecimal(currentSoc)
        tvMonitorHoursLeft.text = if (isLockedOut) "사용 종료됨" else UiFormatters.formatDurationHours(hoursLeft)

        energyBarMonitor.setLevels(currentSoc, targetSoc)
        tvMonitorReserveLabel.text = "귀가 예약 ${UiFormatters.formatPercentWholeOrDecimal(targetSoc)}"
        tvMonitorGapLabel.text = "차단 기준까지 ${UiFormatters.formatPercentWholeOrDecimal(remainPct).replace("%", "")}%p"

        val modeTitle = UiFormatters.marginModeTitle(
            session?.destinationSummary?.marginMode ?: SessionStore.selectedMarginMode
        )
        tvMonitorSummaryLine.text =
            "귀가 예약 잔량 ${UiFormatters.formatPercentWholeOrDecimal(targetSoc)} · $modeTitle · 예약 잔량 도달 시 전원 공급 종료"
        tvMonitorLoadValue.text = String.format(Locale.US, "%.1f kW", loadKw)

        val powerSamples = SessionStore.powerTrendTracker.samples()
        powerTrendChart.setSamples(powerSamples.map { it.loadKw })
        tvPowerTrendLeftLabel.text = PowerTrendTracker.formatTrendLeftLabel(powerSamples, isLockedOut)
        tvPowerTrendRightLabel.text = PowerTrendTracker.formatTrendRightLabel(powerSamples, isLockedOut)

        val summary = session?.destinationSummary
        val destName = summary?.destination ?: session?.destinationName ?: pred?.destination?.name ?: SessionStore.destName
        val depName = summary?.departure ?: SessionStore.departureTime
        val distKm = summary?.distanceKm ?: pred?.navigation?.distanceKm ?: 86.0
        val durMin = if (SessionStore.extendedDestLat != null && summary != null) {
            maxOf(8, (distKm * 1.15).roundToInt())
        } else {
            pred?.navigation?.estimatedDurationMin ?: maxOf(8, (distKm * 1.15).roundToInt())
        }
        val elevM = summary?.elevationGainM ?: pred?.navigation?.elevationGainM ?: 640.0
        val tempC = summary?.tempCelsius ?: pred?.weather?.temperature ?: -5.0

        tvCondDest.text = destName
        tvCondDeparture.text = depName
        tvCondDistTime.text = "${UiFormatters.formatDistanceKm(distKm)} / ${UiFormatters.formatDurationMinutes(durMin)}"
        tvCondWeatherElev.text = String.format(
            Locale.US,
            "%s / +%.0f m",
            UiFormatters.formatTemperature(tempC), elevM
        )
        tvCondMargin.text = "$modeTitle · 예약 잔량 ${UiFormatters.formatPercentWholeOrDecimal(targetSoc)}"
    }

    private fun syncUtilityUI(relayOk: Boolean, hvacOk: Boolean) {
        val lockout = SessionStore.lastLockoutData
        val active = SessionStore.activeSessionStatus
        if (lockout != null || SessionStore.isLockoutTriggered) {
            val soc = lockout?.bmsPreservedSoc ?: active?.currentSoc ?: SessionStore.currentSocPercent
            val km = lockout?.returnDistanceGuaranteedKm?.toInt()
                ?: ((soc / 100.0 * SessionStore.userBatteryCapacity) * SessionStore.userAvgEfficiency).toInt()
            val temp = lockout?.cabinTargetTempCelsius?.toInt() ?: 21

            tvUtilityHeadline.text = "안전 귀가를 위해 V2L이 자동 종료되었습니다."
            tvUtilityDesc.text =
                "차박 체온 유지를 위해 차량 내부 무시동 히터(유틸리티 모드 ${temp}°C)가 자동 작동 중입니다."
            tvUtilityBadge.text = "귀가 전력 보호"
            tvUtilityCheckSummary.text =
                "보존 잔량 ${UiFormatters.formatPercentWholeOrDecimal(soc)}와 귀가 가능 거리(약 $km km)를 확인했습니다. 실내 온도와 전원 상태를 확인해 주세요."
            tvCardPowerTitle.text = if (relayOk) "V2L 자동 종료 완료" else "V2L 전원 차단됨"
            tvCardPowerSub.text = "외부 전원 차단됨"
            tvCardHeaterTitle.text = "실내 난방 ${temp}°C"
            tvCardHeaterSub.text = if (hvacOk) "무시동 히터 가동 중" else "실내 난방 대기 중"
            tvCardRangeTitle.text = "귀가 전력 보존 완료"
            tvCardRangeSub.text = "남은 배터리 ${UiFormatters.formatPercentWholeOrDecimal(soc)} · 약 $km km"
        } else {
            val reserveSoc = active?.targetSoc
                ?: SessionStore.lastPredictData?.recommendations?.get(SessionStore.selectedMarginMode)?.reserveSocPercent
                ?: 28.0
            val estKm = ((SessionStore.currentSocPercent / 100.0 * SessionStore.userBatteryCapacity) * SessionStore.userAvgEfficiency).toInt()

            tvUtilityHeadline.text = if (active != null) {
                "V2L 전원을 안전 감시하며 귀가 전력을 보호 중입니다."
            } else {
                "차단 시 실내 난방(21°C)으로 자동 전환됩니다."
            }
            tvUtilityDesc.text =
                "예약 잔량(${UiFormatters.formatPercentWholeOrDecimal(reserveSoc)})에 도달하거나 차단 버튼을 누르면 외부 전원을 끄고 실내 난방을 켭니다."
            tvUtilityBadge.text = if (active != null) "모니터링 감시 중" else "차단 대기 상태"
            tvUtilityCheckSummary.text =
                "현재 배터리 ${UiFormatters.formatPercentWholeOrDecimal(SessionStore.currentSocPercent)} 기준 귀가 가능 거리는 약 $estKm km입니다."
            tvCardPowerTitle.text = if (active != null) "V2L 외부 공급 중" else "V2L 대기 상태"
            tvCardPowerSub.text = if (active != null) "예약 잔량 도달 시 자동 차단" else "모니터링 시작 전"
            tvCardHeaterTitle.text = "실내 난방 21°C 예약"
            tvCardHeaterSub.text = if (SessionStore.isPleosConnected) "차량 공조 연동 준비 완료" else "차단 시 자동 가동"
            tvCardRangeTitle.text = "귀가 전력 보호 대기"
            tvCardRangeSub.text = "현재 ${UiFormatters.formatPercentWholeOrDecimal(SessionStore.currentSocPercent)} · 약 $estKm km"
        }
    }

    // =========================================================
    // 화면 및 상단 탭 전환
    // =========================================================
    private fun showScreen(step: Int, immediateMonitorRefresh: Boolean = false) {
        SessionStore.currentScreenIndex = step
        screenRouteSetup.visibility = if (step == 1) View.VISIBLE else View.GONE
        screenMarginPlan.visibility = if (step == 2) View.VISIBLE else View.GONE
        screenEnergyAnalysis.visibility = if (step == 5) View.VISIBLE else View.GONE
        screenMonitor.visibility = if (step == 3) View.VISIBLE else View.GONE
        screenUtility.visibility = if (step == 4) View.VISIBLE else View.GONE

        when (step) {
            1 -> {
                tvPageTitle.text = "귀가 경로 설정"
                tvPageSubtitle.visibility = View.GONE
            }
            2 -> {
                tvPageTitle.text = "안전 귀가에 필요한 전력량"
                tvPageSubtitle.visibility = View.GONE
            }
            5 -> {
                tvPageTitle.text = "귀가 전력 상세 분석"
                tvPageSubtitle.text = "캠핑에 쓰는 에너지와 귀가를 위한 에너지를 한눈에 확인하세요."
                tvPageSubtitle.visibility = View.VISIBLE
            }
            3 -> {
                tvPageTitle.text = "V2L 안전 모니터링"
                tvPageSubtitle.visibility = View.GONE
            }
            4 -> {
                tvPageTitle.text = "귀가 전력은 지키고, 실내는 따뜻하게"
                tvPageSubtitle.visibility = View.GONE
            }
        }

        val activeTabKey = when (step) {
            1, 2 -> 0
            5 -> 1
            3 -> 2
            else -> 3
        }
        setTabVisual(tvTabSafety, indTabSafety, activeTabKey == 0)
        setTabVisual(tvTabEnergy, indTabEnergy, activeTabKey == 1)
        setTabVisual(tvTabMonitor, indTabMonitor, activeTabKey == 2)
        setTabVisual(tvTabUtility, indTabUtility, activeTabKey == 3)

        syncAllUiFromStore()
        if (step == 3) {
            startMonitorPollingIfEligible(immediateFirstPoll = immediateMonitorRefresh)
        } else {
            stopMonitorPolling()
        }
        mainScrollView.scrollTo(0, 0)
        mainScrollView.post { mainScrollView.scrollTo(0, 0) }
    }

    private fun setTabVisual(label: TextView, indicator: View, active: Boolean) {
        if (active) {
            label.setTextColor(ContextCompat.getColor(this, R.color.vlocker_ink))
            label.setTypeface(null, Typeface.BOLD)
            indicator.setBackgroundColor(ContextCompat.getColor(this, R.color.vlocker_ink))
        } else {
            label.setTextColor(ContextCompat.getColor(this, R.color.vlocker_muted))
            label.setTypeface(null, Typeface.NORMAL)
            indicator.setBackgroundColor(Color.TRANSPARENT)
        }
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        currentFocus?.let { imm?.hideSoftInputFromWindow(it.windowToken, 0) }
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        isActivityStarted = false
        stopMonitorPolling()
        monitorMutationEpoch++
        activeLocationRequestSeq++
        activeLocationJob?.cancel()
        predictRequestSeq++
        predictJob?.cancel()
        runCatching {
            textToSpeech?.stop()
            textToSpeech?.shutdown()
        }
        if (::vehicleManager.isInitialized) {
            vehicleManager.release()
        }
        super.onDestroy()
    }
}

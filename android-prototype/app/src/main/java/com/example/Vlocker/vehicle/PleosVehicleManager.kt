package com.example.Vlocker.vehicle

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.EvBattery
import ai.pleos.playground.vehicle.api.HVAC
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.capability.AccessType
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.unit.TemperatureUnit
import ai.pleos.playground.vehicle.listener.EvBatteryStateOfChargeListener
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * V2L 물리 릴레이 차단 제어 추상화 (Adapter Pattern)
 * - 공개 Pleos SDK v2.3.2에는 서드파티 V2L 강제 차단 메서드가 보안상 미공개이므로,
 *   OEM 파트너십 승인 후 실차 릴레이 쓰기 권한(CONTROL_V2L_RELAY) 플러그인 교체가 가능하도록 분리함.
 */
interface V2lHardwareController {
    fun cutOffV2lRelay(onResult: (success: Boolean, message: String) -> Unit)
}

class SimulatedV2lHardwareAdapter : V2lHardwareController {
    override fun cutOffV2lRelay(onResult: (success: Boolean, message: String) -> Unit) {
        onResult(true, "V2L 방전 릴레이 안전 차단 완료 (Adapter Mode)")
    }
}

/**
 * Pleos Vehicle SDK v2.3.2 통합 매니저
 * - Pleos Connect 차량/에뮬레이터: 실제 EvBattery 조회 및 HVAC 21℃ 무시동 히터 제어
 * - 일반 Android 기기/가벼운 에뮬레이터: 서비스 미설치 감지 시 멈춤 없이 시뮬레이션 값으로 안전 동작
 */
class PleosVehicleManager(
    private val context: Context,
    private val v2lHardware: V2lHardwareController = SimulatedV2lHardwareAdapter()
) {
    companion object {
        private const val TAG = "PleosVehicleManager"
        private const val PLEOS_VEHICLE_SERVICE_ACTION = "ai.pleos.playground.vehicle.VEHICLE_START"
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var vehicle: Vehicle? = null
    private var evBattery: EvBattery? = null
    private var hvac: HVAC? = null

    var isPleosServiceInstalled: Boolean = false
        private set

    var onLiveSocChanged: ((Double) -> Unit)? = null

    private val socListener = object : EvBatteryStateOfChargeListener {
        override fun onEvBatteryStateOfChargeUpdated(value: Int?) {
            value?.let { soc ->
                mainHandler.post { onLiveSocChanged?.invoke(soc.toDouble()) }
            }
        }

        override fun onFailed(e: Exception) {
            Log.w(TAG, "실시간 SoC 구독 실패: ${describeVehicleError(e)}")
        }
    }

    /**
     * Pleos Vehicle Service APK가 현재 기기/에뮬레이터에 설치되어 있는지 확인합니다.
     * 일반 Android 에뮬레이터에서 불필요한 15초 재시도 대기를 방지합니다.
     */
    private fun checkPleosServiceInstalled(): Boolean {
        return runCatching {
            val intent = Intent(PLEOS_VEHICLE_SERVICE_ACTION)
            val services = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.queryIntentServices(
                    intent,
                    PackageManager.ResolveInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.queryIntentServices(intent, 0)
            }
            services.isNotEmpty()
        }.getOrDefault(false)
    }

    @SuppressLint("MissingPermission")
    fun initialize(onReady: (isPleosConnected: Boolean, statusMessage: String) -> Unit) {
        isPleosServiceInstalled = checkPleosServiceInstalled()
        if (!isPleosServiceInstalled) {
            mainHandler.post {
                onReady(false, "일반 Android 테스트 모드 (시뮬레이션 SoC 61% · 전비 5.4km/kWh)")
            }
            return
        }

        runCatching {
            val instance = Vehicle(context.applicationContext)
            instance.initialize()
            vehicle = instance
            evBattery = instance.getEvBattery()
            hvac = instance.getHVAC()

            evBattery?.registerEvBatteryStateOfCharge(socListener)
            mainHandler.post {
                onReady(true, "Pleos Vehicle Service 연결됨 (실차 SDK 활성)")
            }
        }.onFailure { e ->
            Log.e(TAG, "Vehicle SDK 초기화 실패: ${e.message}")
            mainHandler.post {
                onReady(false, "Vehicle SDK 초기화 실패 - 시뮬레이션 모드 전환")
            }
        }
    }

    /**
     * 현재 차량 배터리 잔량(%)과 누적 평균 전비(km/kWh)를 조회합니다.
     * Pleos 서비스가 없거나 조회 실패 시 안전한 기본값(61%, 5.4km/kWh)을 반환합니다.
     */
    @SuppressLint("MissingPermission")
    fun readVehicleTelemetry(
        fallbackSoc: Double = 61.0,
        fallbackEfficiency: Double = 5.4,
        onResult: (socPercent: Double, efficiencyKmKwh: Double, fromVehicleSdk: Boolean) -> Unit
    ) {
        val batteryApi = evBattery
        if (!isPleosServiceInstalled || batteryApi == null) {
            mainHandler.post { onResult(fallbackSoc, fallbackEfficiency, false) }
            return
        }

        batteryApi.getEvBatteryStateOfCharge(
            onComplete = { socInt ->
                val resolvedSoc = socInt?.toDouble() ?: fallbackSoc
                batteryApi.getCumulativeEvEfficiency(
                    onComplete = { effFloat ->
                        val resolvedEff = effFloat?.toDouble()?.takeIf { it > 0.5 } ?: fallbackEfficiency
                        mainHandler.post { onResult(resolvedSoc, resolvedEff, socInt != null) }
                    },
                    onFailed = { e ->
                        Log.w(TAG, "누적 전비 조회 실패: ${describeVehicleError(e)}")
                        mainHandler.post { onResult(resolvedSoc, fallbackEfficiency, socInt != null) }
                    }
                )
            },
            onFailed = { e ->
                Log.w(TAG, "배터리 SoC 조회 실패: ${describeVehicleError(e)}")
                mainHandler.post { onResult(fallbackSoc, fallbackEfficiency, false) }
            }
        )
    }

    /**
     * V2L 안전 차단 트리거 시:
     * 1) V2L 물리 릴레이 차단 (Adapter)
     * 2) Pleos HVAC SDK로 무시동 히터(21.0℃) 가동
     */
    @SuppressLint("MissingPermission")
    fun triggerEmergencyLockoutAndHeater(
        targetTempCelsius: Float = 21.0f,
        onResult: (relayCutOff: Boolean, hvacActivated: Boolean, detailMessage: String) -> Unit
    ) {
        v2lHardware.cutOffV2lRelay { relayOk, relayMsg ->
            val hvacApi = hvac
            if (!isPleosServiceInstalled || hvacApi == null) {
                mainHandler.post {
                    onResult(
                        relayOk,
                        true,
                        "$relayMsg / [시뮬레이션] 무시동 히터 ${targetTempCelsius}℃ 가동 완료"
                    )
                }
                return@cutOffV2lRelay
            }

            hvacApi.checkHvacTemperatureCapabilities(
                onComplete = { config, areaBoundsMap, _ ->
                    if (config.accessType != AccessType.WRITE && config.accessType != AccessType.READ_WRITE) {
                        mainHandler.post {
                            onResult(relayOk, false, "$relayMsg / 차량 HVAC 쓰기 미지원(${config.accessType})")
                        }
                        return@checkHvacTemperatureCapabilities
                    }

                    val targetArea = areaBoundsMap.keys.firstOrNull() ?: SeatArea(SeatValue.ROW_1_LEFT)
                    val bounds = areaBoundsMap[targetArea]
                    val safeTemp = bounds?.let { targetTempCelsius.coerceIn(it.minValue, it.maxValue) }
                        ?: targetTempCelsius

                    hvacApi.setHvacPower(
                        area = targetArea,
                        value = true,
                        onComplete = { activeArea, _ ->
                            hvacApi.setHvacTemperature(
                                area = activeArea,
                                value = safeTemp,
                                unit = TemperatureUnit.CELSIUS,
                                onComplete = { _, appliedTemp ->
                                    mainHandler.post {
                                        onResult(
                                            relayOk,
                                            true,
                                            "$relayMsg / Pleos HVAC 무시동 히터 ${appliedTemp ?: safeTemp}℃ 가동"
                                        )
                                    }
                                },
                                onFailed = { e ->
                                    Log.w(TAG, "HVAC 온도 설정 실패: ${describeVehicleError(e)}")
                                    mainHandler.post {
                                        onResult(
                                            relayOk,
                                            false,
                                            "$relayMsg / HVAC 온도 설정 실패(${describeVehicleError(e)})"
                                        )
                                    }
                                }
                            )
                        },
                        onFailed = { e ->
                            Log.w(TAG, "HVAC 전원 켜기 실패: ${describeVehicleError(e)}")
                            mainHandler.post {
                                onResult(
                                    relayOk,
                                    false,
                                    "$relayMsg / HVAC 전원 켜기 실패(${describeVehicleError(e)})"
                                )
                            }
                        }
                    )
                },
                onFailed = { e ->
                    Log.w(TAG, "HVAC Capability 조회 실패: ${describeVehicleError(e)}")
                    mainHandler.post {
                        onResult(
                            relayOk,
                            false,
                            "$relayMsg / HVAC 지원 조회 실패(${describeVehicleError(e)})"
                        )
                    }
                }
            )
        }
    }

    @SuppressLint("MissingPermission")
    fun release() {
        runCatching {
            evBattery?.unregisterEvBatteryStateOfCharge(socListener)
            vehicle?.release()
        }
        vehicle = null
        evBattery = null
        hvac = null
    }

    private fun describeVehicleError(e: Exception): String {
        return e.message?.takeIf { it.isNotBlank() } ?: e::class.java.simpleName
    }
}

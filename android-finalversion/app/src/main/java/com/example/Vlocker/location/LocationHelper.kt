package com.example.Vlocker.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.location.LocationCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.os.CancellationSignal
import androidx.core.util.Consumer
import com.example.Vlocker.ui.UiFormatters
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.math.abs

data class LocationCandidate(
    val provider: String,
    val latitude: Double,
    val longitude: Double,
    val timeMs: Long,
    val accuracyMeters: Float? = null,
    val elapsedRealtimeMs: Long? = null
)

enum class UnavailableReason {
    PERMISSION_MISSING,
    LOCATION_SERVICE_OFF,
    NO_PROVIDER_ENABLED,
    TIMEOUT_OR_EMPTY
}

sealed class ActiveLocationResult {
    data class KoreanLocation(
        val candidate: LocationCandidate,
        val sourceNote: String,
        val fromActiveFix: Boolean
    ) : ActiveLocationResult()

    data class OverseasLocation(
        val candidate: LocationCandidate
    ) : ActiveLocationResult()

    data class Unavailable(
        val reason: UnavailableReason
    ) : ActiveLocationResult()
}

object LocationHelper {

    const val MAX_STARTUP_CACHE_AGE_MS: Long = 30 * 1000L
    const val MAX_FALLBACK_CACHE_AGE_MS: Long = 30 * 60 * 1000L
    const val MAX_CACHE_ACCURACY_METERS: Float = 300f
    const val MAX_FALLBACK_ACCURACY_METERS: Float = 500f
    const val ACTIVE_TIMEOUT_MS: Long = 12_000L
    const val GPS_REFINEMENT_GRACE_MS: Long = 1_500L
    const val GPS_LOW_ACC_REFINEMENT_GRACE_MS: Long = 5_500L
    const val HIGH_ACCURACY_THRESHOLD_METERS: Float = 30f

    private const val TAG = "VlockerGps"
    private const val FUSED_PROVIDER_NAME = "fused"
    private const val COMPARABLE_TIME_WINDOW_MS = 30_000L

    private fun logGps(message: String) {
        runCatching { Log.i(TAG, message) }
    }

    fun toCandidate(location: Location, providerFallback: String): LocationCandidate {
        val elapsedMs = runCatching { LocationCompat.getElapsedRealtimeMillis(location) }
            .getOrNull()
            ?.takeIf { it > 0L }
        return LocationCandidate(
            provider = location.provider?.takeIf { it.isNotBlank() } ?: providerFallback,
            latitude = location.latitude,
            longitude = location.longitude,
            timeMs = location.time,
            accuracyMeters = if (location.hasAccuracy()) location.accuracy else null,
            elapsedRealtimeMs = elapsedMs
        )
    }

    fun computeAgeMs(
        candidate: LocationCandidate,
        nowEpochMs: Long,
        nowElapsedMs: Long?
    ): Long? {
        val candElapsed = candidate.elapsedRealtimeMs
        if (candElapsed != null && candElapsed > 0L && nowElapsedMs != null && nowElapsedMs >= 0L) {
            val delta = nowElapsedMs - candElapsed
            return if (delta < -60_000L) null else delta.coerceAtLeast(0L)
        }
        if (candidate.timeMs <= 0L) return null
        val delta = nowEpochMs - candidate.timeMs
        return if (delta < -60_000L) null else delta.coerceAtLeast(0L)
    }

    fun isFreshKoreanCandidate(
        candidate: LocationCandidate,
        nowEpochMs: Long,
        nowElapsedMs: Long?,
        maxAgeMs: Long,
        maxAccuracyMeters: Float?
    ): Boolean {
        if (!UiFormatters.isKoreanCoordinate(candidate.latitude, candidate.longitude)) return false
        val ageMs = computeAgeMs(candidate, nowEpochMs, nowElapsedMs) ?: return false
        if (ageMs > maxAgeMs) return false
        if (maxAccuracyMeters != null && candidate.accuracyMeters != null && candidate.accuracyMeters > maxAccuracyMeters) {
            return false
        }
        return true
    }

    fun providerRank(provider: String): Int {
        return when (provider) {
            LocationManager.GPS_PROVIDER -> 3
            FUSED_PROVIDER_NAME -> 2
            LocationManager.NETWORK_PROVIDER -> 1
            else -> 0
        }
    }

    fun candidateProviders(
        fineGranted: Boolean,
        coarseGranted: Boolean,
        availableProviders: Collection<String>,
        isProviderEnabled: (String) -> Boolean,
        sdkInt: Int
    ): List<String> {
        val ordered = when {
            fineGranted -> listOf(LocationManager.GPS_PROVIDER, FUSED_PROVIDER_NAME, LocationManager.NETWORK_PROVIDER)
            coarseGranted -> listOf(FUSED_PROVIDER_NAME, LocationManager.NETWORK_PROVIDER)
            else -> emptyList()
        }
        return ordered.filter { provider ->
            val supportedOnApi = provider != FUSED_PROVIDER_NAME || sdkInt >= Build.VERSION_CODES.S
            supportedOnApi && availableProviders.contains(provider) && runCatching { isProviderEnabled(provider) }.getOrDefault(false)
        }
    }

    fun pickBestCandidate(
        candidates: List<LocationCandidate>,
        nowEpochMs: Long,
        nowElapsedMs: Long?,
        preferKorean: Boolean
    ): LocationCandidate? {
        if (candidates.isEmpty()) return null
        val pool = if (preferKorean) {
            val koreanOnly = candidates.filter { UiFormatters.isKoreanCoordinate(it.latitude, it.longitude) }
            if (koreanOnly.isNotEmpty()) koreanOnly else candidates
        } else {
            candidates
        }

        return pool.minWithOrNull { a, b ->
            val ageA = computeAgeMs(a, nowEpochMs, nowElapsedMs) ?: (Long.MAX_VALUE / 4)
            val ageB = computeAgeMs(b, nowEpochMs, nowElapsedMs) ?: (Long.MAX_VALUE / 4)

            if (abs(ageA - ageB) > COMPARABLE_TIME_WINDOW_MS) {
                ageA.compareTo(ageB)
            } else {
                val rankDiff = providerRank(b.provider).compareTo(providerRank(a.provider))
                if (rankDiff != 0) {
                    rankDiff
                } else {
                    val accA = a.accuracyMeters ?: Float.MAX_VALUE
                    val accB = b.accuracyMeters ?: Float.MAX_VALUE
                    val accDiff = accA.compareTo(accB)
                    if (accDiff != 0) accDiff else ageA.compareTo(ageB)
                }
            }
        }
    }

    fun selectFreshStartupCache(
        cachedCandidates: List<LocationCandidate>,
        nowEpochMs: Long,
        nowElapsedMs: Long?
    ): ActiveLocationResult.KoreanLocation? {
        val freshCached = cachedCandidates.filter { cand ->
            isFreshKoreanCandidate(
                candidate = cand,
                nowEpochMs = nowEpochMs,
                nowElapsedMs = nowElapsedMs,
                maxAgeMs = MAX_STARTUP_CACHE_AGE_MS,
                maxAccuracyMeters = MAX_CACHE_ACCURACY_METERS
            )
        }
        val best = pickBestCandidate(freshCached, nowEpochMs, nowElapsedMs, preferKorean = true) ?: return null
        return ActiveLocationResult.KoreanLocation(
            candidate = best,
            sourceNote = "최근 GPS 확인",
            fromActiveFix = false
        )
    }

    fun resolveAfterActiveFetch(
        activeCandidates: List<LocationCandidate>,
        cachedCandidates: List<LocationCandidate>,
        nowEpochMs: Long,
        nowElapsedMs: Long?
    ): ActiveLocationResult {
        val koreanActive = activeCandidates.filter {
            UiFormatters.isKoreanCoordinate(it.latitude, it.longitude)
        }
        pickBestCandidate(koreanActive, nowEpochMs, nowElapsedMs, preferKorean = true)?.let { best ->
            return ActiveLocationResult.KoreanLocation(
                candidate = best,
                sourceNote = "GPS 실시간 확인",
                fromActiveFix = true
            )
        }

        if (activeCandidates.isNotEmpty()) {
            pickBestCandidate(activeCandidates, nowEpochMs, nowElapsedMs, preferKorean = false)?.let { overseas ->
                return ActiveLocationResult.OverseasLocation(overseas)
            }
        }

        pickFallbackCachedKorean(cachedCandidates, nowEpochMs, nowElapsedMs)?.let { return it }

        return ActiveLocationResult.Unavailable(UnavailableReason.TIMEOUT_OR_EMPTY)
    }

    @SuppressLint("MissingPermission")
    suspend fun fetchSingleKoreanOrBestLocation(
        context: Context,
        fineGranted: Boolean,
        coarseGranted: Boolean,
        forceActiveRefresh: Boolean
    ): ActiveLocationResult {
        if (!fineGranted && !coarseGranted) {
            return ActiveLocationResult.Unavailable(UnavailableReason.PERMISSION_MISSING)
        }

        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return ActiveLocationResult.Unavailable(UnavailableReason.NO_PROVIDER_ENABLED)

        val nowEpochMs = System.currentTimeMillis()
        val nowElapsedMs = SystemClock.elapsedRealtime()
        val cachedCandidates = collectCachedCandidates(lm, fineGranted)
        logGps("fetch start force=$forceActiveRefresh fine=$fineGranted coarse=$coarseGranted cached=${cachedCandidates.size}")

        if (!forceActiveRefresh) {
            selectFreshStartupCache(cachedCandidates, nowEpochMs, nowElapsedMs)?.let { fresh ->
                logGps("using fresh startup cache provider=${fresh.candidate.provider} (${fresh.candidate.latitude}, ${fresh.candidate.longitude})")
                return fresh
            }
        }

        if (!LocationManagerCompat.isLocationEnabled(lm)) {
            if (!forceActiveRefresh) {
                pickFallbackCachedKorean(cachedCandidates, nowEpochMs, nowElapsedMs)?.let { return it }
            }
            logGps("location service disabled")
            return ActiveLocationResult.Unavailable(UnavailableReason.LOCATION_SERVICE_OFF)
        }

        val knownProviders = listOf(
            LocationManager.GPS_PROVIDER,
            FUSED_PROVIDER_NAME,
            LocationManager.NETWORK_PROVIDER
        ).filter { provider ->
            runCatching { LocationManagerCompat.hasProvider(lm, provider) }.getOrDefault(false)
        }

        val activeProviders = candidateProviders(
            fineGranted = fineGranted,
            coarseGranted = coarseGranted,
            availableProviders = knownProviders,
            isProviderEnabled = { p -> lm.isProviderEnabled(p) },
            sdkInt = Build.VERSION.SDK_INT
        )
        logGps("activeProviders=$activeProviders known=$knownProviders")

        if (activeProviders.isEmpty()) {
            if (!forceActiveRefresh) {
                pickFallbackCachedKorean(cachedCandidates, nowEpochMs, nowElapsedMs)?.let { return it }
            }
            return ActiveLocationResult.Unavailable(UnavailableReason.NO_PROVIDER_ENABLED)
        }

        val activeCandidates = requestActiveFromProviders(context, lm, activeProviders)
        val postEpochMs = System.currentTimeMillis()
        val postElapsedMs = SystemClock.elapsedRealtime()
        return resolveAfterActiveFetch(
            activeCandidates = activeCandidates,
            cachedCandidates = cachedCandidates,
            nowEpochMs = postEpochMs,
            nowElapsedMs = postElapsedMs
        ).also { resolved ->
            logGps("resolved activeCount=${activeCandidates.size} result=$resolved")
        }
    }

    private fun pickFallbackCachedKorean(
        cachedCandidates: List<LocationCandidate>,
        nowEpochMs: Long,
        nowElapsedMs: Long?
    ): ActiveLocationResult.KoreanLocation? {
        val fallbackCached = cachedCandidates.filter { cand ->
            isFreshKoreanCandidate(
                candidate = cand,
                nowEpochMs = nowEpochMs,
                nowElapsedMs = nowElapsedMs,
                maxAgeMs = MAX_FALLBACK_CACHE_AGE_MS,
                maxAccuracyMeters = MAX_FALLBACK_ACCURACY_METERS
            )
        }
        val best = pickBestCandidate(fallbackCached, nowEpochMs, nowElapsedMs, preferKorean = true) ?: return null
        return ActiveLocationResult.KoreanLocation(
            candidate = best,
            sourceNote = "최근 GPS 확인",
            fromActiveFix = false
        )
    }

    @SuppressLint("MissingPermission")
    private fun collectCachedCandidates(
        lm: LocationManager,
        fineGranted: Boolean
    ): List<LocationCandidate> {
        val providers = if (fineGranted) {
            listOf(
                LocationManager.GPS_PROVIDER,
                FUSED_PROVIDER_NAME,
                LocationManager.NETWORK_PROVIDER,
                LocationManager.PASSIVE_PROVIDER
            )
        } else {
            listOf(
                FUSED_PROVIDER_NAME,
                LocationManager.NETWORK_PROVIDER,
                LocationManager.PASSIVE_PROVIDER
            )
        }
        val results = mutableListOf<LocationCandidate>()
        for (provider in providers) {
            val loc = runCatching { lm.getLastKnownLocation(provider) }.getOrNull() ?: continue
            results.add(toCandidate(loc, provider))
        }
        return results
    }

    @SuppressLint("MissingPermission")
    private suspend fun requestActiveFromProviders(
        context: Context,
        lm: LocationManager,
        providers: List<String>
    ): List<LocationCandidate> = suspendCancellableCoroutine { cont ->
        val mainExecutor = ContextCompat.getMainExecutor(context)
        val mainHandler = Handler(Looper.getMainLooper())
        val signals = mutableListOf<CancellationSignal>()
        val pending = providers.toMutableSet()
        val received = mutableListOf<LocationCandidate>()

        var finished = false
        var timeoutRunnable: Runnable? = null
        var graceRunnable: Runnable? = null

        fun cleanup() {
            graceRunnable?.let { mainHandler.removeCallbacks(it) }
            timeoutRunnable?.let { mainHandler.removeCallbacks(it) }
            signals.forEach { runCatching { it.cancel() } }
        }

        fun completeOnce() {
            if (finished) return
            finished = true
            cleanup()
            if (cont.isActive) {
                cont.resume(received.toList())
            }
        }

        cont.invokeOnCancellation {
            finished = true
            cleanup()
        }

        val timeoutTask = Runnable {
            logGps("active fetch timeout after ${ACTIVE_TIMEOUT_MS}ms; pending=$pending received=${received.size}")
            completeOnce()
        }
        timeoutRunnable = timeoutTask
        mainHandler.postDelayed(timeoutTask, ACTIVE_TIMEOUT_MS)

        for (provider in providers) {
            val signal = CancellationSignal()
            signals.add(signal)
            val started = try {
                LocationManagerCompat.getCurrentLocation(
                    lm,
                    provider,
                    signal,
                    mainExecutor,
                    Consumer { location: Location? ->
                        if (finished) return@Consumer
                        pending.remove(provider)

                        var isKoreanFix = false
                        var accuracyMeters: Float? = null
                        if (location != null) {
                            val cand = toCandidate(location, provider)
                            received.add(cand)
                            accuracyMeters = cand.accuracyMeters
                            isKoreanFix = UiFormatters.isKoreanCoordinate(cand.latitude, cand.longitude)
                            logGps("callback provider=$provider lat=${cand.latitude} lng=${cand.longitude} acc=${cand.accuracyMeters} korean=$isKoreanFix")
                        } else {
                            logGps("callback provider=$provider null location")
                        }

                        when {
                            isKoreanFix && provider == LocationManager.GPS_PROVIDER -> {
                                completeOnce()
                            }
                            isKoreanFix && !pending.contains(LocationManager.GPS_PROVIDER) -> {
                                completeOnce()
                            }
                            isKoreanFix && graceRunnable == null -> {
                                val graceMs = if (accuracyMeters != null && accuracyMeters <= HIGH_ACCURACY_THRESHOLD_METERS) {
                                    GPS_REFINEMENT_GRACE_MS
                                } else {
                                    GPS_LOW_ACC_REFINEMENT_GRACE_MS
                                }
                                val graceTask = Runnable { completeOnce() }
                                graceRunnable = graceTask
                                mainHandler.postDelayed(graceTask, graceMs)
                            }
                            pending.isEmpty() -> {
                                completeOnce()
                            }
                        }
                    }
                )
                true
            } catch (_: SecurityException) {
                false
            } catch (_: IllegalArgumentException) {
                false
            } catch (_: Exception) {
                false
            }

            if (!started) {
                pending.remove(provider)
            }
        }

        if (pending.isEmpty()) {
            completeOnce()
        }
    }
}

package com.example.motion

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Window
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.lang.ref.WeakReference

/**
 * Centralized Adaptive Motion Engine coordinating display refresh capability, live FrameMetrics
 * pacing, thermal throttling, battery saver, and reduced-motion preferences.
 */
class AdaptiveMotionEngine(context: Context) {

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val profileManager = MotionProfileManager()

    private var windowRef: WeakReference<Window>? = null
    private var latestFrameHealth = FrameHealthSnapshot()
    private var modePreference = MotionModePreference.AUTO
    private var configuredStiffness = 1100f
    private var configuredDamping = 0.85f

    private val refreshRateMonitor = RefreshRateMonitor(appContext) {
        recomputeOnMain()
    }
    private val thermalAndBatteryMonitor = ThermalAndBatteryMonitor(appContext) {
        recomputeOnMain()
    }
    private val performanceMonitor = PerformanceMonitor { snapshot ->
        latestFrameHealth = snapshot
        recomputeOnMain()
    }

    private val _telemetry = MutableStateFlow(MotionTelemetry())
    val telemetry: StateFlow<MotionTelemetry> = _telemetry.asStateFlow()

    private val _policy = MutableStateFlow(
        MotionPolicy.forProfile(
            profile = PerformanceProfile.BALANCED,
            targetRefreshRateHz = 60f,
            reducedMotion = false,
        ),
    )
    val policy: StateFlow<MotionPolicy> = _policy.asStateFlow()

    fun attach(activity: Activity) {
        val window = activity.window
        windowRef = WeakReference(window)
        refreshRateMonitor.start()
        thermalAndBatteryMonitor.start()
        performanceMonitor.attach(window)
        recompute()
    }

    fun detach() {
        performanceMonitor.detach()
        refreshRateMonitor.stop()
        thermalAndBatteryMonitor.stop()
        windowRef = null
    }

    fun setModePreference(preference: MotionModePreference) {
        if (modePreference != preference) {
            modePreference = preference
            recomputeOnMain()
        }
    }

    fun setSpringTuning(stiffness: Float, dampingRatio: Float) {
        val clampedStiffness = stiffness.coerceIn(200f, 3500f)
        val clampedDamping = dampingRatio.coerceIn(0.45f, 1.0f)
        if (configuredStiffness != clampedStiffness || configuredDamping != clampedDamping) {
            configuredStiffness = clampedStiffness
            configuredDamping = clampedDamping
            recomputeOnMain()
        }
    }

    private fun recomputeOnMain() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            recompute()
        } else {
            mainHandler.post { recompute() }
        }
    }

    fun recompute() {
        val window = windowRef?.get()
        val displayCap = refreshRateMonitor.inspectDisplay(window)
        val reducedMotion = runCatching {
            Settings.Global.getFloat(
                appContext.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            ) == 0f
        }.getOrDefault(false)
        val batterySaver = thermalAndBatteryMonitor.isBatterySaverActive()
        val thermal = thermalAndBatteryMonitor.currentThermalLevel()

        performanceMonitor.targetRefreshRateHz = displayCap.activeHz

        val decision = profileManager.evaluate(
            MotionProfileManager.MotionSignals(
                supportedMaxHz = displayCap.maxSupportedHz,
                activeHz = displayCap.activeHz,
                jankPercent = latestFrameHealth.jankPercent,
                sampleCount = latestFrameHealth.sampleCount,
                thermalLevel = thermal,
                batterySaverActive = batterySaver,
                reducedMotionActive = reducedMotion,
                modePreference = modePreference,
                timestampMs = System.currentTimeMillis(),
            ),
        )

        refreshRateMonitor.applyWindowRefreshPreference(window, decision.profile)

        val nextPolicy = MotionPolicy.forProfile(
            profile = decision.profile,
            targetRefreshRateHz = displayCap.activeHz,
            reducedMotion = reducedMotion,
            configuredStiffness = configuredStiffness,
            configuredDamping = configuredDamping,
        )

        _policy.value = nextPolicy
        _telemetry.value = MotionTelemetry(
            supportedRefreshRatesHz = displayCap.supportedRatesHz,
            activeRefreshRateHz = displayCap.activeHz,
            refreshTier = displayCap.tier,
            activeProfile = decision.profile,
            thermalLevel = thermal,
            batterySaverActive = batterySaver,
            reducedMotionActive = reducedMotion,
            modePreference = modePreference,
            configuredStiffness = configuredStiffness,
            configuredDamping = configuredDamping,
            frameHealth = latestFrameHealth,
            adaptationReason = decision.reason,
        )
    }
}

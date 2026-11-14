package com.example.motion

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.Window
import kotlin.math.roundToInt

/**
 * Detects device-supported refresh rates (60/90/120/144Hz), tracks live display mode changes,
 * and updates Window frame-rate preferences safely within the display's actual capabilities.
 */
class RefreshRateMonitor(
    context: Context,
    private val onDisplayChanged: () -> Unit,
) {
    private val displayManager = context.getSystemService(DisplayManager::class.java)
    private var registered = false

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = onDisplayChanged()
        override fun onDisplayRemoved(displayId: Int) = onDisplayChanged()
        override fun onDisplayChanged(displayId: Int) {
            if (displayId == Display.DEFAULT_DISPLAY) onDisplayChanged()
        }
    }

    data class DisplayCapability(
        val supportedRatesHz: List<Int>,
        val maxSupportedHz: Float,
        val activeHz: Float,
        val tier: RefreshTier,
    )

    fun start() {
        if (!registered) {
            displayManager?.registerDisplayListener(displayListener, Handler(Looper.getMainLooper()))
            registered = true
        }
    }

    fun stop() {
        if (registered) {
            runCatching { displayManager?.unregisterDisplayListener(displayListener) }
            registered = false
        }
    }

    fun inspectDisplay(window: Window?): DisplayCapability {
        val display = currentDisplay(window) ?: return DisplayCapability(
            supportedRatesHz = listOf(60),
            maxSupportedHz = 60f,
            activeHz = 60f,
            tier = RefreshTier.HZ_60,
        )
        val rawModes = display.supportedModes.orEmpty()
        val rates = rawModes
            .map { it.refreshRate.roundToInt() }
            .filter { it in 45..240 }
            .distinct()
            .sorted()
            .ifEmpty { listOf(display.refreshRate.roundToInt().coerceIn(60, 240)) }

        val maxHz = rawModes.maxOfOrNull { it.refreshRate } ?: display.refreshRate
        val activeHz = display.refreshRate.takeIf { it > 0f } ?: 60f

        return DisplayCapability(
            supportedRatesHz = rates,
            maxSupportedHz = maxHz.coerceAtLeast(60f),
            activeHz = activeHz,
            tier = RefreshTier.fromHz(maxHz),
        )
    }

    /**
     * Requests a suitable display mode matching the active [PerformanceProfile] without ever forcing
     * refresh rates higher than what the panel natively supports.
     */
    fun applyWindowRefreshPreference(window: Window?, profile: PerformanceProfile) {
        window ?: return
        val display = currentDisplay(window) ?: return
        val currentMode = display.mode ?: return
        val matchingSizeModes = display.supportedModes
            .filter { it.physicalWidth == currentMode.physicalWidth && it.physicalHeight == currentMode.physicalHeight }
            .ifEmpty { display.supportedModes.toList() }
        if (matchingSizeModes.isEmpty()) return

        val maxAllowedHz = when (profile) {
            PerformanceProfile.LOW -> 60.5f
            PerformanceProfile.BALANCED -> 120.5f
            PerformanceProfile.HIGH -> 240f
        }

        val targetMode = matchingSizeModes
            .filter { it.refreshRate <= maxAllowedHz }
            .maxByOrNull { it.refreshRate }
            ?: matchingSizeModes.minByOrNull { it.refreshRate }
            ?: return

        val params = window.attributes
        if (params.preferredDisplayModeId != targetMode.modeId) {
            params.preferredDisplayModeId = targetMode.modeId
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                params.preferredRefreshRate = targetMode.refreshRate
            }
            window.attributes = params
        }
    }

    private fun currentDisplay(window: Window?): Display? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window?.context?.display?.let { return it }
        }
        return displayManager?.getDisplay(Display.DEFAULT_DISPLAY)
    }
}

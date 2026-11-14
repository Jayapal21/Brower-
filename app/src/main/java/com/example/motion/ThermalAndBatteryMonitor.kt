package com.example.motion

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat

/**
 * Monitors Battery Saver mode and Android Thermal Status (API 29+) to feed adaptive fallback signals.
 */
class ThermalAndBatteryMonitor(
    private val context: Context,
    private val onEnvironmentChanged: () -> Unit,
) {
    private val appContext = context.applicationContext
    private val powerManager = appContext.getSystemService(PowerManager::class.java)
    private var started = false

    private val powerSaveReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            onEnvironmentChanged()
        }
    }

    private val thermalListener: PowerManager.OnThermalStatusChangedListener? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            PowerManager.OnThermalStatusChangedListener { _ ->
                onEnvironmentChanged()
            }
        } else {
            null
        }

    fun start() {
        if (started) return
        started = true
        val filter = IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        ContextCompat.registerReceiver(
            appContext,
            powerSaveReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        val listener = thermalListener
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && listener != null) {
            runCatching { powerManager?.addThermalStatusListener(listener) }
        }
    }

    fun stop() {
        if (!started) return
        started = false
        runCatching { appContext.unregisterReceiver(powerSaveReceiver) }
        val listener = thermalListener
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && listener != null) {
            runCatching { powerManager?.removeThermalStatusListener(listener) }
        }
    }

    fun isBatterySaverActive(): Boolean = powerManager?.isPowerSaveMode == true

    fun currentThermalLevel(): ThermalLevel {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return ThermalLevel.UNKNOWN
        return when (powerManager?.currentThermalStatus) {
            PowerManager.THERMAL_STATUS_NONE -> ThermalLevel.NOMINAL
            PowerManager.THERMAL_STATUS_LIGHT -> ThermalLevel.LIGHT
            PowerManager.THERMAL_STATUS_MODERATE -> ThermalLevel.MODERATE
            PowerManager.THERMAL_STATUS_SEVERE,
            PowerManager.THERMAL_STATUS_CRITICAL,
            PowerManager.THERMAL_STATUS_EMERGENCY,
            PowerManager.THERMAL_STATUS_SHUTDOWN,
            -> ThermalLevel.SEVERE
            else -> ThermalLevel.UNKNOWN
        }
    }
}

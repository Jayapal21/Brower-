package com.example.motion

import android.os.Handler
import android.os.HandlerThread
import android.view.FrameMetrics
import android.view.Window
import kotlin.math.roundToInt

/**
 * Observes real Window FrameMetrics on a dedicated background HandlerThread so UI rendering
 * incurs zero extra main-thread allocations or lock contention.
 */
class PerformanceMonitor(
    private val onHealthUpdated: (FrameHealthSnapshot) -> Unit,
) {
    private var handlerThread: HandlerThread? = null
    private var metricsHandler: Handler? = null
    private var attachedWindow: Window? = null

    @Volatile
    var targetRefreshRateHz: Float = 60f

    private val recentFrameDurationsNs = ArrayDeque<Long>(WINDOW_CAPACITY)
    private val recentMissedFlags = ArrayDeque<Boolean>(WINDOW_CAPACITY)
    private var lastEmitMs: Long = 0L

    private val listener = Window.OnFrameMetricsAvailableListener { _, frameMetrics, _ ->
        val totalNs = frameMetrics.getMetric(FrameMetrics.TOTAL_DURATION)
        if (totalNs <= 0L) return@OnFrameMetricsAvailableListener

        val hz = targetRefreshRateHz.coerceIn(50f, 240f)
        val budgetNs = (1_000_000_000f / hz).toLong()
        val deadlineWithGraceNs = budgetNs + JITTER_GRACE_NS
        val missed = totalNs > deadlineWithGraceNs

        synchronized(recentFrameDurationsNs) {
            if (recentFrameDurationsNs.size >= WINDOW_CAPACITY) {
                recentFrameDurationsNs.removeFirst()
                recentMissedFlags.removeFirst()
            }
            recentFrameDurationsNs.addLast(totalNs)
            recentMissedFlags.addLast(missed)

            val now = System.currentTimeMillis()
            if (now - lastEmitMs < EMIT_THROTTLE_MS && recentFrameDurationsNs.size < WINDOW_CAPACITY) {
                return@OnFrameMetricsAvailableListener
            }
            if (now - lastEmitMs < EMIT_THROTTLE_MS) {
                return@OnFrameMetricsAvailableListener
            }
            lastEmitMs = now

            val count = recentFrameDurationsNs.size
            val missedCount = recentMissedFlags.count { it }
            val jankPct = if (count > 0) (missedCount * 100f) / count else 0f
            val sorted = recentFrameDurationsNs.sorted()
            val p95Index = ((sorted.size - 1) * 0.95f).roundToInt().coerceIn(0, sorted.lastIndex)
            val p95Ms = sorted[p95Index] / 1_000_000f

            onHealthUpdated(
                FrameHealthSnapshot(
                    sampleCount = count,
                    missedFramesCount = missedCount,
                    jankPercent = jankPct,
                    p95FrameMs = p95Ms,
                    frameBudgetMs = budgetNs / 1_000_000f,
                ),
            )
        }
    }

    fun attach(window: Window) {
        if (attachedWindow === window) return
        detach()

        val thread = HandlerThread("ObscuraFrameMetrics").apply { start() }
        val handler = Handler(thread.looper)
        handlerThread = thread
        metricsHandler = handler
        attachedWindow = window

        runCatching {
            window.addOnFrameMetricsAvailableListener(listener, handler)
        }
    }

    fun detach() {
        attachedWindow?.let { win ->
            runCatching { win.removeOnFrameMetricsAvailableListener(listener) }
        }
        attachedWindow = null
        handlerThread?.quitSafely()
        handlerThread = null
        metricsHandler = null
        synchronized(recentFrameDurationsNs) {
            recentFrameDurationsNs.clear()
            recentMissedFlags.clear()
        }
    }

    companion object {
        private const val WINDOW_CAPACITY = 60
        private const val JITTER_GRACE_NS = 1_500_000L
        private const val EMIT_THROTTLE_MS = 500L
    }
}

package com.example.ui.components

import android.content.Context
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.webkit.WebView
import kotlin.math.abs

/**
 * Lightweight native WebView extension providing top-of-page pull-to-refresh telemetry
 * without interfering with normal page scrolling, pinch zoom, or horizontal gestures.
 */
class ObscuraWebView(context: Context) : WebView(context) {

    var isPullEnabled: Boolean = true
    var onPullProgress: ((Float) -> Unit)? = null
    var onPullRefresh: (() -> Unit)? = null

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private val pullThresholdPx = (96f * resources.displayMetrics.density).coerceAtLeast(180f)

    private var startX = 0f
    private var startY = 0f
    private var pullEligible = false
    private var pulling = false
    private var currentFraction = 0f

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startX = event.x
                startY = event.y
                pullEligible = isPullEnabled && scrollY == 0
                pulling = false
                currentFraction = 0f
            }
            MotionEvent.ACTION_MOVE -> {
                if (!isPullEnabled || event.pointerCount > 1) {
                    resetPull()
                } else if (pullEligible && scrollY == 0) {
                    val dx = event.x - startX
                    val dy = event.y - startY
                    if (!pulling && dy > touchSlop && abs(dy) > abs(dx) * 1.4f) {
                        pulling = true
                    }
                    if (pulling) {
                        val effectiveDy = (dy - touchSlop).coerceAtLeast(0f)
                        val fraction = (effectiveDy / pullThresholdPx).coerceIn(0f, 1.35f)
                        if (fraction != currentFraction) {
                            currentFraction = fraction
                            onPullProgress?.invoke(fraction)
                        }
                        if (dy <= 0f) {
                            resetPull()
                        }
                    } else if (dy < -touchSlop) {
                        pullEligible = false
                    }
                } else if (pulling) {
                    resetPull()
                }
            }
            MotionEvent.ACTION_UP -> {
                if (isPullEnabled && pulling && currentFraction >= 1f) {
                    onPullRefresh?.invoke()
                }
                resetPull()
            }
            MotionEvent.ACTION_CANCEL -> resetPull()
        }
        return super.onTouchEvent(event)
    }

    private fun resetPull() {
        pullEligible = false
        pulling = false
        if (currentFraction != 0f) {
            currentFraction = 0f
            onPullProgress?.invoke(0f)
        }
    }
}

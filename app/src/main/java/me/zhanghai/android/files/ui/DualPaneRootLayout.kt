/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.ui

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.FrameLayout
import me.zhanghai.android.files.util.dpToDimension
import kotlin.math.abs

/**
 * Intercepts upward swipe gestures starting near the bottom edge so users can pull up the recent
 * history sheet without breaking normal taps or scrolling on child panes.
 */
class DualPaneRootLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    var bottomTriggerHeightPx: Float = context.dpToDimension(56f)
    var canSwipeUpFromBottom: (() -> Boolean)? = null
    var onBottomSwipeUp: (() -> Unit)? = null

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private var trackingBottomGesture = false
    private var triggeredSwipeUp = false
    private var downX = 0f
    private var downY = 0f

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                triggeredSwipeUp = false
                val inBottomEdge = height > 0 && ev.y >= height - bottomTriggerHeightPx
                trackingBottomGesture = inBottomEdge && (canSwipeUpFromBottom?.invoke() == true)
                downX = ev.x
                downY = ev.y
            }
            MotionEvent.ACTION_MOVE -> {
                if (!trackingBottomGesture) {
                    return false
                }
                val dy = ev.y - downY
                val dx = ev.x - downX
                if (-dy > touchSlop && -dy > abs(dx) * 1.25f) {
                    triggeredSwipeUp = true
                    return true
                }
                if (dy > touchSlop || abs(dx) > touchSlop * 1.5f) {
                    trackingBottomGesture = false
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                trackingBottomGesture = false
                triggeredSwipeUp = false
            }
        }
        return false
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!trackingBottomGesture && !triggeredSwipeUp) {
            return super.onTouchEvent(event)
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                if (!triggeredSwipeUp) {
                    val dy = event.y - downY
                    val dx = event.x - downX
                    if (-dy > touchSlop && -dy > abs(dx) * 1.25f) {
                        triggeredSwipeUp = true
                    }
                }
            }
            MotionEvent.ACTION_UP -> {
                val shouldOpen = triggeredSwipeUp || (downY - event.y) > touchSlop
                trackingBottomGesture = false
                triggeredSwipeUp = false
                if (shouldOpen) {
                    onBottomSwipeUp?.invoke()
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                trackingBottomGesture = false
                triggeredSwipeUp = false
            }
        }
        return true
    }
}

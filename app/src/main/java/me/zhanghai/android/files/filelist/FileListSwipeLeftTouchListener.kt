/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filelist

import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.recyclerview.widget.RecyclerView
import me.zhanghai.android.files.util.dpToDimension
import kotlin.math.abs

/**
 * Allows swiping left on any file or folder item in a [RecyclerView] to trigger multi-selection
 * with elastic slide feedback while preserving right-swipe drawer gestures and vertical scrolling.
 */
class FileListSwipeLeftTouchListener(
    private val recyclerView: RecyclerView,
    private val canSwipePosition: (Int) -> Boolean,
    private val onSwipeLeftTriggered: (Int) -> Unit
) : RecyclerView.OnItemTouchListener {

    private val touchSlop = ViewConfiguration.get(recyclerView.context).scaledTouchSlop.toFloat()
    private val maxTranslationPx = recyclerView.context.dpToDimension(52f)
    private val triggerThresholdPx = recyclerView.context.dpToDimension(42f)

    private var activeView: View? = null
    private var activePosition = RecyclerView.NO_POSITION
    private var downX = 0f
    private var downY = 0f
    private var isDraggingLeft = false
    private var hapticFired = false

    init {
        recyclerView.addOnChildAttachStateChangeListener(
            object : RecyclerView.OnChildAttachStateChangeListener {
                override fun onChildViewAttachedToWindow(view: View) {}

                override fun onChildViewDetachedFromWindow(view: View) {
                    if (view === activeView) {
                        resetActiveView(animate = false)
                    } else if (view.translationX != 0f) {
                        view.animate().cancel()
                        view.translationX = 0f
                    }
                }
            }
        )
    }

    override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                resetActiveView(animate = false)
                val child = rv.findChildViewUnder(e.x, e.y) ?: return false
                val position = rv.getChildAdapterPosition(child)
                if (position == RecyclerView.NO_POSITION || !canSwipePosition(position)) {
                    return false
                }
                activeView = child
                activePosition = position
                downX = e.x
                downY = e.y
                isDraggingLeft = false
                hapticFired = false
            }
            MotionEvent.ACTION_MOVE -> {
                val view = activeView ?: return false
                val dx = e.x - downX
                val dy = e.y - downY
                if (!isDraggingLeft) {
                    if (abs(dy) > touchSlop && abs(dy) >= abs(dx)) {
                        clearTracking()
                        return false
                    }
                    if (dx > touchSlop) {
                        // Preserve left-to-right swipe for opening NavigationDrawer.
                        clearTracking()
                        return false
                    }
                    if (-dx > touchSlop && -dx > abs(dy) * 1.25f) {
                        isDraggingLeft = true
                        view.animate().cancel()
                        rv.parent?.requestDisallowInterceptTouchEvent(true)
                        return true
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                resetActiveView(animate = isDraggingLeft)
            }
        }
        return false
    }

    override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {
        val view = activeView ?: return
        val position = activePosition
        when (e.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                val dx = e.x - downX
                val clamped = dx.coerceIn(-maxTranslationPx * 1.35f, 0f) * 0.6f
                view.translationX = clamped
                if (!hapticFired && -dx >= triggerThresholdPx) {
                    hapticFired = true
                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                }
            }
            MotionEvent.ACTION_UP -> {
                val dx = e.x - downX
                val shouldTrigger = isDraggingLeft &&
                    (hapticFired || -dx >= triggerThresholdPx) &&
                    position != RecyclerView.NO_POSITION
                resetActiveView(animate = true)
                if (shouldTrigger) {
                    onSwipeLeftTriggered(position)
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                resetActiveView(animate = true)
            }
        }
    }

    override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
        if (disallowIntercept && !isDraggingLeft) {
            resetActiveView(animate = false)
        }
    }

    private fun resetActiveView(animate: Boolean) {
        val view = activeView
        clearTracking()
        if (view != null) {
            view.animate().cancel()
            if (animate && view.isAttachedToWindow && view.translationX != 0f) {
                view.animate()
                    .translationX(0f)
                    .setDuration(160L)
                    .setInterpolator(FastOutSlowInInterpolator())
                    .start()
            } else {
                view.translationX = 0f
            }
        }
    }

    private fun clearTracking() {
        activeView = null
        activePosition = RecyclerView.NO_POSITION
        isDraggingLeft = false
        hapticFired = false
    }
}

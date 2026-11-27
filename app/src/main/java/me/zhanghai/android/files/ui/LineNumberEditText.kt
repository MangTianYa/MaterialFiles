/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import androidx.core.graphics.ColorUtils
import com.google.android.material.R as MaterialR
import me.zhanghai.android.files.util.dpToDimension
import me.zhanghai.android.files.util.getColorByAttr
import kotlin.math.ceil

/**
 * Supports soft-wrapped logical line numbers in the gutter, current line highlight, and
 * incremental line-offset tracking on top of [ScrollingChildEditText].
 */
class LineNumberEditText : ScrollingChildEditText {
    private var isInitialized = false
    private var isUpdatingGutterPadding = false

    private var basePaddingStart = 0
    private var basePaddingTop = 0
    private var basePaddingEnd = 0
    private var basePaddingBottom = 0

    private var lineStarts = IntArray(INITIAL_LINE_CAPACITY)
    private var logicalLineCount = 1
    private var beforeChangeDepth = 0
    private var beforeChangeHasNewline = false
    private var suppressSelectionCallback = false

    private var gutterDigits = 0
    private var gutterWidth = 0f

    private var lastNotifiedCurrentLine = -1
    private var lastNotifiedTotalLines = -1

    private val clipRect = Rect()
    private val gutterBackgroundPaint = Paint()
    private val gutterDividerPaint = Paint()
    private val activeLineHighlightPaint = Paint()
    private val gutterTextPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var activeLineNumberColor = 0
    private var gutterTextColor = 0

    private val gutterStartPadding = context.dpToDimension(10f)
    private val gutterEndPadding = context.dpToDimension(8f)
    private val textGapPadding = context.dpToDimension(10f)
    private val dividerWidth = context.dpToDimension(1f)

    var showLineNumbers: Boolean = true
        set(value) {
            if (field == value) {
                return
            }
            field = value
            if (isInitialized) {
                updateGutterPadding(force = true)
                invalidate()
            }
        }

    var onCursorLineChangedListener: ((currentLine: Int, totalLines: Int) -> Unit)? = null
        set(value) {
            field = value
            if (isInitialized && value != null) {
                value.invoke(currentLineNumber, logicalLineCount)
            }
        }

    val currentLineNumber: Int
        get() {
            val length = text?.length ?: 0
            val offset = selectionStart.coerceIn(0, length)
            return getLineNumberForOffset(offset)
        }

    val totalLineCount: Int
        get() = logicalLineCount

    constructor(context: Context) : super(context) {
        initView()
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        initView()
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context, attrs, defStyleAttr
    ) {
        initView()
    }

    private fun initView() {
        basePaddingStart = paddingStart
        basePaddingTop = paddingTop
        basePaddingEnd = paddingEnd
        basePaddingBottom = paddingBottom

        refreshThemeColors()
        gutterTextPaint.textSize = textSize * LINE_NUMBER_TEXT_SCALE
        gutterTextPaint.typeface = typeface
        gutterTextPaint.fontFeatureSettings = "tnum"

        isInitialized = true
        rebuildLineStarts(text)
        updateGutterPadding(force = true)
        notifyCursorLineChanged()

        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(
                text: CharSequence?,
                start: Int,
                count: Int,
                after: Int
            ) {
                if (beforeChangeDepth == 0) {
                    beforeChangeHasNewline = hasNewline(text, start, start + count)
                    suppressSelectionCallback = true
                }
                beforeChangeDepth++
            }

            override fun onTextChanged(
                text: CharSequence?,
                start: Int,
                before: Int,
                count: Int
            ) {
                val depth = (beforeChangeDepth - 1).coerceAtLeast(0)
                beforeChangeDepth = depth
                val afterHasNewline = hasNewline(text, start, start + count)
                if (depth > 0 || beforeChangeHasNewline || afterHasNewline) {
                    rebuildLineStarts(text)
                    updateGutterPadding(force = false)
                } else {
                    shiftLineStartsAfter(start, count - before)
                }
                if (depth == 0) {
                    suppressSelectionCallback = false
                    notifyCursorLineChanged()
                    invalidate()
                }
            }

            override fun afterTextChanged(s: Editable?) {
                if (beforeChangeDepth > 0) {
                    beforeChangeDepth = 0
                }
                if (suppressSelectionCallback) {
                    suppressSelectionCallback = false
                    notifyCursorLineChanged()
                }
            }
        })
    }

    private fun refreshThemeColors() {
        val surfaceContainerLow = context.getColorByAttr(MaterialR.attr.colorSurfaceContainerLow)
        val outlineVariant = context.getColorByAttr(MaterialR.attr.colorOutlineVariant)
        val onSurfaceVariant = context.getColorByAttr(MaterialR.attr.colorOnSurfaceVariant)
        val primary = context.getColorByAttr(androidx.appcompat.R.attr.colorPrimary)

        gutterBackgroundPaint.color = surfaceContainerLow
        gutterBackgroundPaint.style = Paint.Style.FILL

        gutterDividerPaint.color = ColorUtils.setAlphaComponent(outlineVariant, 160)
        gutterDividerPaint.style = Paint.Style.STROKE
        gutterDividerPaint.strokeWidth = dividerWidth

        activeLineNumberColor = primary
        gutterTextColor = ColorUtils.setAlphaComponent(onSurfaceVariant, 178)
        activeLineHighlightPaint.color = ColorUtils.setAlphaComponent(primary, 18)
        activeLineHighlightPaint.style = Paint.Style.FILL
    }

    override fun setTextSize(unit: Int, size: Float) {
        super.setTextSize(unit, size)
        if (isInitialized) {
            gutterTextPaint.textSize = textSize * LINE_NUMBER_TEXT_SCALE
            updateGutterPadding(force = true)
            invalidate()
        }
    }

    override fun setTypeface(tf: Typeface?) {
        super.setTypeface(tf)
        if (isInitialized) {
            gutterTextPaint.typeface = typeface
            updateGutterPadding(force = true)
            invalidate()
        }
    }

    override fun setPadding(left: Int, top: Int, right: Int, bottom: Int) {
        if (isInitialized && !isUpdatingGutterPadding) {
            basePaddingStart = if (layoutDirection == LAYOUT_DIRECTION_RTL) right else left
            basePaddingTop = top
            basePaddingEnd = if (layoutDirection == LAYOUT_DIRECTION_RTL) left else right
            basePaddingBottom = bottom
            super.setPadding(left, top, right, bottom)
            updateGutterPadding(force = true)
            return
        }
        super.setPadding(left, top, right, bottom)
    }

    override fun setPaddingRelative(start: Int, top: Int, end: Int, bottom: Int) {
        if (isInitialized && !isUpdatingGutterPadding) {
            basePaddingStart = start
            basePaddingTop = top
            basePaddingEnd = end
            basePaddingBottom = bottom
            super.setPaddingRelative(start, top, end, bottom)
            updateGutterPadding(force = true)
            return
        }
        super.setPaddingRelative(start, top, end, bottom)
    }

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        if (!isInitialized || suppressSelectionCallback) {
            return
        }
        notifyCursorLineChanged()
        if (showLineNumbers) {
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        val textLayout = layout
        val editable = text
        if (!showLineNumbers || textLayout == null || editable == null) {
            super.onDraw(canvas)
            return
        }

        canvas.getClipBounds(clipRect)
        val isRtl = layoutDirection == LAYOUT_DIRECTION_RTL
        val gutterLeft = if (isRtl) (scrollX + width - gutterWidth) else scrollX.toFloat()
        val gutterRight = gutterLeft + gutterWidth
        val viewLeft = scrollX.toFloat()
        val viewRight = (scrollX + width).toFloat()
        val drawTop = clipRect.top.toFloat()
        val drawBottom = clipRect.bottom.toFloat()

        val hasCollapsedCursor = selectionStart == selectionEnd
        val currentLine = currentLineNumber
        var highlightTop = 0f
        var highlightBottom = 0f
        var shouldDrawHighlight = false
        if (hasCollapsedCursor) {
            val lineIndex = (currentLine - 1).coerceIn(0, logicalLineCount - 1)
            val startOffset = lineStarts[lineIndex].coerceIn(0, editable.length)
            val endOffset = if (lineIndex + 1 < logicalLineCount) {
                (lineStarts[lineIndex + 1] - 1).coerceIn(startOffset, editable.length)
            } else {
                editable.length
            }
            val firstVisual = textLayout.getLineForOffset(startOffset)
            val lastVisual = textLayout.getLineForOffset(endOffset)
            highlightTop = (textLayout.getLineTop(firstVisual) + extendedPaddingTop).toFloat()
            highlightBottom = (textLayout.getLineBottom(lastVisual) + extendedPaddingTop).toFloat()
            shouldDrawHighlight = highlightBottom >= drawTop && highlightTop <= drawBottom
            if (shouldDrawHighlight) {
                canvas.drawRect(viewLeft, highlightTop, viewRight, highlightBottom, activeLineHighlightPaint)
            }
        }

        canvas.save()
        if (isRtl) {
            canvas.clipRect(viewLeft, drawTop, gutterLeft, drawBottom)
        } else {
            canvas.clipRect(gutterRight, drawTop, viewRight, drawBottom)
        }
        super.onDraw(canvas)
        canvas.restore()

        canvas.drawRect(gutterLeft, drawTop, gutterRight, drawBottom, gutterBackgroundPaint)
        if (shouldDrawHighlight) {
            canvas.drawRect(gutterLeft, highlightTop, gutterRight, highlightBottom, activeLineHighlightPaint)
        }

        val dividerX = if (isRtl) {
            gutterLeft + dividerWidth * 0.5f
        } else {
            gutterRight - dividerWidth * 0.5f
        }
        canvas.drawLine(dividerX, drawTop, dividerX, drawBottom, gutterDividerPaint)

        gutterTextPaint.textAlign = if (isRtl) Paint.Align.LEFT else Paint.Align.RIGHT
        val numberX = if (isRtl) {
            gutterLeft + gutterEndPadding
        } else {
            gutterRight - gutterEndPadding
        }

        val relativeTop = (clipRect.top - extendedPaddingTop).coerceAtLeast(0)
        val relativeBottom = (clipRect.bottom - extendedPaddingTop).coerceAtLeast(0)
        val firstVisualLine = textLayout.getLineForVertical(relativeTop)
        val lastVisualLine = textLayout.getLineForVertical(relativeBottom)

        val firstOffset = textLayout.getLineStart(firstVisualLine)
        var logicalLine = getLineNumberForOffset(firstOffset)
        for (visualLine in firstVisualLine..lastVisualLine) {
            val lineStartOffset = textLayout.getLineStart(visualLine)
            val isLogicalStart = lineStartOffset == 0 ||
                (lineStartOffset <= editable.length && editable[lineStartOffset - 1] == '\n')
            if (visualLine > firstVisualLine && isLogicalStart) {
                logicalLine++
            }
            if (!isLogicalStart) {
                continue
            }
            val isCurrentLine = logicalLine == currentLine
            gutterTextPaint.color = if (isCurrentLine) activeLineNumberColor else gutterTextColor
            gutterTextPaint.isFakeBoldText = isCurrentLine
            val baselineY = (textLayout.getLineBaseline(visualLine) + extendedPaddingTop).toFloat()
            canvas.drawText(logicalLine.toString(), numberX, baselineY, gutterTextPaint)
        }
        gutterTextPaint.isFakeBoldText = false
    }

    private fun hasNewline(text: CharSequence?, start: Int, end: Int): Boolean {
        if (text == null) {
            return false
        }
        val safeStart = start.coerceAtLeast(0)
        val safeEnd = end.coerceAtMost(text.length)
        for (i in safeStart until safeEnd) {
            if (text[i] == '\n') {
                return true
            }
        }
        return false
    }

    private fun shiftLineStartsAfter(editStart: Int, delta: Int) {
        if (delta == 0) {
            return
        }
        val firstAffected = findFirstLineStartAfter(editStart)
        for (i in firstAffected until logicalLineCount) {
            lineStarts[i] += delta
        }
    }

    private fun findFirstLineStartAfter(offset: Int): Int {
        var low = 0
        var high = logicalLineCount
        while (low < high) {
            val mid = (low + high) ushr 1
            if (lineStarts[mid] <= offset) {
                low = mid + 1
            } else {
                high = mid
            }
        }
        return low
    }

    private fun rebuildLineStarts(text: CharSequence?) {
        var count = 1
        lineStarts[0] = 0
        if (text != null) {
            val length = text.length
            for (i in 0 until length) {
                if (text[i] == '\n') {
                    if (count == lineStarts.size) {
                        lineStarts = lineStarts.copyOf(lineStarts.size * 2)
                    }
                    lineStarts[count++] = i + 1
                }
            }
        }
        logicalLineCount = count
    }

    private fun getLineNumberForOffset(offset: Int): Int {
        var low = 0
        var high = logicalLineCount - 1
        while (low <= high) {
            val mid = (low + high) ushr 1
            val lineStart = lineStarts[mid]
            when {
                lineStart < offset -> low = mid + 1
                lineStart > offset -> high = mid - 1
                else -> return mid + 1
            }
        }
        return low.coerceAtLeast(1)
    }

    private fun updateGutterPadding(force: Boolean) {
        if (!showLineNumbers) {
            if (paddingStart != basePaddingStart || paddingTop != basePaddingTop ||
                paddingEnd != basePaddingEnd || paddingBottom != basePaddingBottom) {
                isUpdatingGutterPadding = true
                super.setPaddingRelative(
                    basePaddingStart, basePaddingTop, basePaddingEnd, basePaddingBottom
                )
                isUpdatingGutterPadding = false
            }
            return
        }

        val digits = logicalLineCount.toString().length.coerceAtLeast(MIN_GUTTER_DIGITS)
        if (!force && digits == gutterDigits && gutterWidth > 0f) {
            return
        }
        gutterDigits = digits
        val sample = "8".repeat(digits)
        val digitsWidth = gutterTextPaint.measureText(sample)
        gutterWidth = gutterStartPadding + digitsWidth + gutterEndPadding
        val desiredStartPadding = basePaddingStart + ceil((gutterWidth + textGapPadding).toDouble()).toInt()
        if (paddingStart != desiredStartPadding || paddingTop != basePaddingTop ||
            paddingEnd != basePaddingEnd || paddingBottom != basePaddingBottom) {
            isUpdatingGutterPadding = true
            super.setPaddingRelative(
                desiredStartPadding, basePaddingTop, basePaddingEnd, basePaddingBottom
            )
            isUpdatingGutterPadding = false
        }
    }

    private fun notifyCursorLineChanged() {
        val current = currentLineNumber
        val total = logicalLineCount
        if (current != lastNotifiedCurrentLine || total != lastNotifiedTotalLines) {
            lastNotifiedCurrentLine = current
            lastNotifiedTotalLines = total
            onCursorLineChangedListener?.invoke(current, total)
        }
    }

    companion object {
        private const val INITIAL_LINE_CAPACITY = 64
        private const val MIN_GUTTER_DIGITS = 2
        private const val LINE_NUMBER_TEXT_SCALE = 0.84f
    }
}

/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.theme.custom

import android.content.Context
import android.util.AttributeSet
import androidx.annotation.AttrRes
import androidx.annotation.StyleRes
import androidx.preference.SwitchPreferenceCompat
import com.google.android.material.color.DynamicColors
import me.zhanghai.android.files.R

class DynamicColorPreference : SwitchPreferenceCompat {
    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet?, @AttrRes defStyleAttr: Int) : super(
        context, attrs, defStyleAttr
    )

    constructor(
        context: Context,
        attrs: AttributeSet?,
        @AttrRes defStyleAttr: Int,
        @StyleRes defStyleRes: Int
    ) : super(context, attrs, defStyleAttr, defStyleRes)

    init {
        if (!DynamicColors.isDynamicColorAvailable()) {
            isEnabled = false
            summary = context.getString(R.string.settings_dynamic_color_summary_unavailable)
        } else {
            summaryOn = context.getString(R.string.settings_dynamic_color_summary_on)
            summaryOff = context.getString(R.string.settings_dynamic_color_summary_off)
        }
    }

    override fun onSetInitialValue(defaultValue: Any?) {
        super.onSetInitialValue(defaultValue)
        if (!DynamicColors.isDynamicColorAvailable() && isChecked) {
            isChecked = false
        }
    }
}

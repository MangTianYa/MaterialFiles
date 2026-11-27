/*
 * Copyright (c) 2019 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.theme.custom

import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.annotation.StyleRes
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.color.DynamicColors
import me.zhanghai.android.files.R
import me.zhanghai.android.files.compat.recreateCompat
import me.zhanghai.android.files.compat.setThemeCompat
import me.zhanghai.android.files.compat.themeResIdCompat
import me.zhanghai.android.files.settings.Settings
import me.zhanghai.android.files.theme.night.NightModeHelper
import me.zhanghai.android.files.util.SimpleActivityLifecycleCallbacks
import me.zhanghai.android.files.util.valueCompat

object CustomThemeHelper {
    private data class AppliedState(
        @StyleRes val baseThemeRes: Int,
        val useDynamicColor: Boolean,
        val blackNightMode: Boolean
    )

    private val activityAppliedStates = mutableMapOf<Activity, AppliedState>()

    private val isDynamicColorActive: Boolean
        get() = Settings.DYNAMIC_COLOR.valueCompat && DynamicColors.isDynamicColorAvailable()

    fun initialize(application: Application) {
        application.registerActivityLifecycleCallbacks(object : SimpleActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                check(activityAppliedStates.containsKey(activity)) {
                    "Activity must extend AppActivity: $activity"
                }
            }

            override fun onActivityDestroyed(activity: Activity) {
                activityAppliedStates.remove(activity)
            }
        })
    }

    fun apply(activity: Activity) {
        val baseThemeRes = activity.themeResIdCompat
        val useDynamicColor = isDynamicColorActive
        val blackNightMode = Settings.BLACK_NIGHT_MODE.valueCompat
        activityAppliedStates[activity] = AppliedState(
            baseThemeRes, useDynamicColor, blackNightMode
        )
        val resolvedThemeRes = getResolvedThemeRes(baseThemeRes, useDynamicColor)
        activity.setThemeCompat(resolvedThemeRes)
        if (useDynamicColor) {
            DynamicColors.applyToActivityIfAvailable(activity)
        }
        if (blackNightMode && NightModeHelper.isInNightMode(activity as AppCompatActivity)) {
            activity.theme.applyStyle(R.style.ThemeOverlay_MaterialFiles_Black, true)
        }
    }

    fun sync() {
        val useDynamicColor = isDynamicColorActive
        val blackNightMode = Settings.BLACK_NIGHT_MODE.valueCompat
        for ((activity, appliedState) in activityAppliedStates) {
            val isNightMode = NightModeHelper.isInNightMode(activity as AppCompatActivity)
            val themeChanged = appliedState.useDynamicColor != useDynamicColor ||
                (isNightMode && appliedState.blackNightMode != blackNightMode)
            if (themeChanged) {
                val resolvedThemeRes =
                    getResolvedThemeRes(appliedState.baseThemeRes, useDynamicColor)
                if (activity is OnThemeChangedListener) {
                    (activity as OnThemeChangedListener).onThemeChanged(resolvedThemeRes)
                } else {
                    activity.recreateCompat()
                }
            }
        }
    }

    @StyleRes
    private fun getResolvedThemeRes(@StyleRes baseThemeRes: Int, useDynamicColor: Boolean): Int {
        if (!useDynamicColor) {
            return baseThemeRes
        }
        return when (baseThemeRes) {
            R.style.Theme_MaterialFiles_Translucent ->
                R.style.Theme_MaterialFiles_Translucent_Dynamic
            R.style.Theme_MaterialFiles_Immersive ->
                R.style.Theme_MaterialFiles_Immersive_Dynamic
            else -> R.style.Theme_MaterialFiles_Dynamic
        }
    }

    interface OnThemeChangedListener {
        fun onThemeChanged(@StyleRes theme: Int)
    }
}

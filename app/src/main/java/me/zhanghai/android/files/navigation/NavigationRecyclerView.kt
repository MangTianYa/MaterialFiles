/*
 * Copyright (c) 2018 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.navigation

import android.content.Context
import android.util.AttributeSet
import androidx.annotation.AttrRes
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.RecyclerView
import me.zhanghai.android.files.util.getDimensionPixelSize

class NavigationRecyclerView : RecyclerView {
    private val verticalPadding = context.getDimensionPixelSize(
        com.google.android.material.R.dimen.design_navigation_padding_bottom
    )

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(
        context: Context,
        attrs: AttributeSet?,
        @AttrRes defStyleAttr: Int
    ) : super(context, attrs, defStyleAttr)

    init {
        updatePadding(top = verticalPadding, bottom = verticalPadding)
    }
}

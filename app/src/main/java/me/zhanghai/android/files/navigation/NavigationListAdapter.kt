/*
 * Copyright (c) 2018 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.navigation

import android.content.Context
import android.content.res.ColorStateList
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import me.zhanghai.android.files.R
import me.zhanghai.android.files.compat.foregroundCompat
import me.zhanghai.android.files.compat.getDrawableCompat
import me.zhanghai.android.files.databinding.NavigationItemBinding
import me.zhanghai.android.files.databinding.NavigationSectionItemBinding
import me.zhanghai.android.files.databinding.NavigationStorageItemBinding
import me.zhanghai.android.files.ui.CheckableItemBackground
import me.zhanghai.android.files.ui.SimpleAdapter
import me.zhanghai.android.files.util.getColorByAttr
import me.zhanghai.android.files.util.layoutInflater

class NavigationListAdapter(
    private val listener: NavigationItem.Listener,
    private val onSectionToggle: (NavigationSection) -> Unit,
    context: Context
) : SimpleAdapter<NavigationItem, RecyclerView.ViewHolder>() {
    private val primaryColor = context.getColorByAttr(androidx.appcompat.R.attr.colorPrimary)
    private val onPrimaryColor =
        context.getColorByAttr(com.google.android.material.R.attr.colorOnPrimary)
    private val secondaryColor =
        context.getColorByAttr(com.google.android.material.R.attr.colorSecondary)

    fun notifyCheckedChanged() {
        notifyItemRangeChanged(0, itemCount, PAYLOAD_CHECKED_CHANGED)
    }

    override val hasStableIds: Boolean
        get() = true

    override fun getItemId(position: Int): Long = getItem(position).id

    override fun getItemViewType(position: Int): Int {
        val item = getItem(position)
        val viewType = when {
            item is NavigationSectionItem -> ViewType.SECTION_HEADER
            item.isStorageCard -> ViewType.STORAGE_CARD
            else -> ViewType.ITEM
        }
        return viewType.ordinal
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val context = parent.context
        return when (ViewType.entries[viewType]) {
            ViewType.ITEM ->
                ItemHolder(
                    NavigationItemBinding.inflate(context.layoutInflater, parent, false)
                ).apply {
                    binding.itemLayout.background = CheckableItemBackground.create(0f, 14f, context)
                    binding.itemLayout.foregroundCompat =
                        context.getDrawableCompat(R.drawable.file_item_list_foreground)
                }
            ViewType.STORAGE_CARD ->
                StorageCardHolder(
                    NavigationStorageItemBinding.inflate(context.layoutInflater, parent, false)
                )
            ViewType.SECTION_HEADER ->
                SectionHeaderHolder(
                    NavigationSectionItemBinding.inflate(context.layoutInflater, parent, false)
                )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        throw UnsupportedOperationException()
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int,
        payloads: List<Any>
    ) {
        when (ViewType.entries[getItemViewType(position)]) {
            ViewType.ITEM -> {
                val item = getItem(position)
                val binding = (holder as ItemHolder).binding
                val checked = item.isChecked(listener)
                binding.itemLayout.isChecked = checked
                binding.iconImage.imageTintList =
                    ColorStateList.valueOf(if (checked) primaryColor else secondaryColor)
                if (payloads.isNotEmpty()) {
                    return
                }
                binding.itemLayout.setOnClickListener { item.onClick(listener) }
                binding.itemLayout.setOnLongClickListener { item.onLongClick(listener) }
                binding.iconImage.setImageDrawable(item.getIcon(binding.iconImage.context))
                binding.titleText.text = item.getTitle(binding.titleText.context)
                binding.subtitleText.text = item.getSubtitle(binding.subtitleText.context)
            }
            ViewType.STORAGE_CARD -> {
                val item = getItem(position)
                val binding = (holder as StorageCardHolder).binding
                val checked = item.isChecked(listener)
                binding.itemLayout.isChecked = checked
                binding.iconImage.imageTintList =
                    ColorStateList.valueOf(if (checked) onPrimaryColor else primaryColor)
                binding.percentText.setTextColor(if (checked) primaryColor else secondaryColor)
                if (payloads.isNotEmpty()) {
                    return
                }
                val context = binding.root.context
                binding.itemLayout.setOnClickListener { item.onClick(listener) }
                binding.itemLayout.setOnLongClickListener { item.onLongClick(listener) }
                binding.iconImage.setImageDrawable(item.getIcon(context))
                binding.titleText.text = item.getTitle(context)
                val percent = item.getStorageUsagePercent(context)
                binding.percentText.text = percent?.let { "$it%" }.orEmpty()
                binding.usageProgressBar.progress = percent ?: 0
                binding.subtitleText.text = item.getSubtitle(context)
            }
            ViewType.SECTION_HEADER -> {
                val item = getItem(position) as NavigationSectionItem
                val binding = (holder as SectionHeaderHolder).binding
                binding.titleText.text = item.getTitle(binding.titleText.context)
                binding.chevronImage.setImageResource(
                    if (item.expanded) {
                        R.drawable.expand_less_icon_24dp
                    } else {
                        R.drawable.expand_more_icon_24dp
                    }
                )
                binding.sectionLayout.setOnClickListener { onSectionToggle(item.section) }
            }
        }
    }

    companion object {
        private val PAYLOAD_CHECKED_CHANGED = Any()
    }

    private enum class ViewType {
        ITEM,
        STORAGE_CARD,
        SECTION_HEADER
    }

    private class ItemHolder(val binding: NavigationItemBinding) : RecyclerView.ViewHolder(
        binding.root
    )

    private class StorageCardHolder(
        val binding: NavigationStorageItemBinding
    ) : RecyclerView.ViewHolder(binding.root)

    private class SectionHeaderHolder(
        val binding: NavigationSectionItemBinding
    ) : RecyclerView.ViewHolder(binding.root)
}

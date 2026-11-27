/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filelist

import android.content.Context
import android.text.format.DateUtils
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import me.zhanghai.android.files.R
import me.zhanghai.android.files.databinding.RecentHistoryItemBinding
import me.zhanghai.android.files.file.MimeType
import me.zhanghai.android.files.file.asMimeTypeOrNull
import me.zhanghai.android.files.file.guessFromPath
import me.zhanghai.android.files.file.iconRes
import me.zhanghai.android.files.file.isApk
import me.zhanghai.android.files.file.isImage
import me.zhanghai.android.files.file.isMedia
import me.zhanghai.android.files.file.isPdf
import me.zhanghai.android.files.file.isSupportedArchive
import me.zhanghai.android.files.file.shouldOpenWithTextEditor
import me.zhanghai.android.files.navigation.NavigationRootMapLiveData
import me.zhanghai.android.files.util.getColorByAttr
import me.zhanghai.android.files.util.layoutInflater
import me.zhanghai.android.files.util.valueCompat

class RecentHistoryAdapter(
    private val onItemClick: (RecentAccessEntry) -> Unit
) : ListAdapter<RecentAccessEntry, RecentHistoryAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = RecentHistoryItemBinding.inflate(parent.context.layoutInflater, parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        val context = holder.binding.root.context
        val rootName = NavigationRootMapLiveData.valueCompat[item.path]?.getName(context)
        val title = rootName ?: item.path.name
        val parentText = item.path.parent?.toUserFriendlyString() ?: "/"
        val timeText = formatRelativeTime(context, item.lastAccessedAtMillis)

        holder.binding.nameText.text = title
        holder.binding.detailText.text = if (rootName != null) {
            "$timeText · ${item.path.toUserFriendlyString()}"
        } else {
            "$timeText · $parentText"
        }

        if (item.isDirectory) {
            holder.binding.iconImage.setImageResource(R.drawable.file_directory_icon)
            holder.binding.iconImage.imageTintList = android.content.res.ColorStateList.valueOf(
                context.getColorByAttr(androidx.appcompat.R.attr.colorPrimary)
            )
            holder.binding.actionBadgeText.isVisible = true
            holder.binding.actionBadgeText.setText(R.string.recent_history_badge_open_dir)
            holder.binding.actionIcon.setImageResource(R.drawable.arrow_end_icon_white_24dp)
        } else {
            val mimeType = item.mimeTypeValue.asMimeTypeOrNull()
                ?: MimeType.guessFromPath(item.path.toString())
            holder.binding.iconImage.setImageResource(mimeType.iconRes)
            holder.binding.iconImage.imageTintList = null
            val editable = isDirectlyEditable(mimeType, item.path.name)
            holder.binding.actionBadgeText.isVisible = editable
            if (editable) {
                holder.binding.actionBadgeText.setText(R.string.recent_history_badge_edit)
                holder.binding.actionIcon.setImageResource(R.drawable.edit_icon)
            } else {
                holder.binding.actionIcon.setImageResource(R.drawable.arrow_end_icon_white_24dp)
            }
        }

        holder.binding.root.setOnClickListener { onItemClick(item) }
    }

    private fun formatRelativeTime(context: Context, timestamp: Long): String {
        val now = System.currentTimeMillis()
        val delta = (now - timestamp).coerceAtLeast(0L)
        return if (delta < DateUtils.MINUTE_IN_MILLIS) {
            context.getString(R.string.recent_history_time_just_now)
        } else {
            DateUtils.getRelativeTimeSpanString(
                timestamp, now, DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE
            ).toString()
        }
    }

    class ViewHolder(val binding: RecentHistoryItemBinding) : RecyclerView.ViewHolder(binding.root)

    companion object {
        fun isDirectlyEditable(mimeType: MimeType, fileName: String): Boolean =
            !mimeType.isApk && !mimeType.isImage && !mimeType.isMedia && !mimeType.isPdf &&
                !mimeType.isSupportedArchive && mimeType.shouldOpenWithTextEditor(fileName)

        private val DiffCallback = object : DiffUtil.ItemCallback<RecentAccessEntry>() {
            override fun areItemsTheSame(
                oldItem: RecentAccessEntry,
                newItem: RecentAccessEntry
            ): Boolean = oldItem.path == newItem.path && oldItem.isDirectory == newItem.isDirectory

            override fun areContentsTheSame(
                oldItem: RecentAccessEntry,
                newItem: RecentAccessEntry
            ): Boolean = oldItem == newItem
        }
    }
}

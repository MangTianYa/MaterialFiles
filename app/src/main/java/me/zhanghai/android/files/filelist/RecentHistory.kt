/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filelist

import android.os.Parcelable
import java8.nio.file.Path
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.WriteWith
import me.zhanghai.android.files.file.MimeType
import me.zhanghai.android.files.settings.Settings
import me.zhanghai.android.files.util.ParcelableParceler
import me.zhanghai.android.files.util.valueCompat

@Parcelize
data class RecentAccessEntry(
    val path: @WriteWith<ParcelableParceler> Path,
    val isDirectory: Boolean,
    val mimeTypeValue: String,
    val lastAccessedAtMillis: Long
) : Parcelable

object RecentHistoryManager {
    private const val MAX_RECENT_ENTRIES = 40
    private val lock = Any()

    fun recordDirectory(path: Path) {
        if (!path.isPersistableHistoryPath()) {
            return
        }
        upsert(
            RecentAccessEntry(
                path = path,
                isDirectory = true,
                mimeTypeValue = MimeType.DIRECTORY.value,
                lastAccessedAtMillis = System.currentTimeMillis()
            )
        )
    }

    fun recordFile(path: Path, mimeType: MimeType) {
        if (!path.isPersistableHistoryPath()) {
            return
        }
        upsert(
            RecentAccessEntry(
                path = path,
                isDirectory = false,
                mimeTypeValue = mimeType.value,
                lastAccessedAtMillis = System.currentTimeMillis()
            )
        )
    }

    fun remove(path: Path) {
        synchronized(lock) {
            val current = Settings.RECENT_ACCESS_ENTRIES.valueCompat
            val updated = current.filterNot { it.path == path }
            if (updated.size != current.size) {
                Settings.RECENT_ACCESS_ENTRIES.putValue(updated)
            }
        }
    }

    fun clear() {
        synchronized(lock) {
            if (Settings.RECENT_ACCESS_ENTRIES.valueCompat.isNotEmpty()) {
                Settings.RECENT_ACCESS_ENTRIES.putValue(emptyList())
            }
        }
    }

    private fun upsert(entry: RecentAccessEntry) {
        synchronized(lock) {
            val current = Settings.RECENT_ACCESS_ENTRIES.valueCompat
            val existingTop = current.firstOrNull()
            if (existingTop != null &&
                existingTop.path == entry.path &&
                existingTop.isDirectory == entry.isDirectory
            ) {
                return
            }
            val updated = ArrayList<RecentAccessEntry>(MAX_RECENT_ENTRIES)
            updated.add(entry)
            for (item in current) {
                if (item.path == entry.path && item.isDirectory == entry.isDirectory) {
                    continue
                }
                updated.add(item)
                if (updated.size >= MAX_RECENT_ENTRIES) {
                    break
                }
            }
            Settings.RECENT_ACCESS_ENTRIES.putValue(updated)
        }
    }
}

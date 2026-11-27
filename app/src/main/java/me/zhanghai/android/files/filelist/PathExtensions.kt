/*
 * Copyright (c) 2020 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filelist

import android.net.Uri
import java8.nio.file.Path
import java8.nio.file.Paths
import me.zhanghai.android.files.file.MimeType
import me.zhanghai.android.files.file.isAppPrivatePath
import me.zhanghai.android.files.file.isSupportedArchive
import me.zhanghai.android.files.provider.archive.archiveFile
import me.zhanghai.android.files.provider.archive.isArchivePath
import me.zhanghai.android.files.provider.content.isContentPath
import me.zhanghai.android.files.provider.linux.isLinuxPath
import me.zhanghai.android.files.provider.root.RootablePath
import me.zhanghai.android.files.storage.createOrLog
import me.zhanghai.android.files.util.asPathNameOrNull
import java.net.URI

val Path.name: String
    get() = fileName?.toString() ?: if (isArchivePath) archiveFile.fileName.toString() else "/"

fun Path.toUserFriendlyString(): String = if (isLinuxPath) toFile().path else toUri().toString()

fun Path.toHeaderPathString(): String {
    if (!isLinuxPath) {
        return toUserFriendlyString()
    }
    val raw = toFile().path
    return if (raw == "/" || raw.endsWith('/')) raw else "$raw/"
}

fun String.toNavigablePathOrNull(): Path? {
    if (isEmpty() || asPathNameOrNull() == null) {
        return null
    }
    if (startsWith('/')) {
        return try {
            Paths.get(this)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    val uri = URI::class.createOrLog(this)
        ?: Uri.parse(this).let {
            URI::class.createOrLog(
                it.scheme, it.userInfo, it.host, it.port, it.path, it.query, it.fragment
            )
        }
    val scheme = uri?.scheme?.lowercase()
    if (uri != null && scheme in NAVIGABLE_SCHEMES) {
        try {
            return Paths.get(uri)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    return null
}

private val NAVIGABLE_SCHEMES = setOf("file", "archive", "document")

fun Path.isArchiveFile(mimeType: MimeType): Boolean = !isArchivePath && mimeType.isSupportedArchive

fun Path.isPersistableHistoryPath(): Boolean {
    if (isContentPath || isAppPrivatePath(allowOpenCache = false)) {
        return false
    }
    if (isArchivePath) {
        val archive = archiveFile
        return !archive.isContentPath && !archive.isAppPrivatePath(allowOpenCache = false)
    }
    return true
}

fun Path?.resolveRestorablePathOrNull(): Path? {
    val path = this ?: return null
    if (!path.isPersistableHistoryPath()) {
        return null
    }
    if (path.isLinuxPath) {
        var current: Path? = path
        while (current != null && current.isLinuxPath) {
            if ((current as? RootablePath)?.isRootRequired(false) == true ||
                current.toFile().isDirectory
            ) {
                return current
            }
            current = current.parent
        }
        return null
    }
    if (path.isArchivePath) {
        val archive = path.archiveFile
        if (archive.isLinuxPath && (archive as? RootablePath)?.isRootRequired(false) != true &&
            !archive.toFile().isFile
        ) {
            return archive.parent.resolveRestorablePathOrNull()
        }
    }
    return path
}

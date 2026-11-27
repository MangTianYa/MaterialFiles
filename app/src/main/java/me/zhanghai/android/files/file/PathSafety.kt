/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.file

import android.content.ContentResolver
import android.net.Uri
import android.os.Build
import androidx.core.content.ContextCompat
import java8.nio.file.Path
import me.zhanghai.android.files.BuildConfig
import me.zhanghai.android.files.R
import me.zhanghai.android.files.app.application
import me.zhanghai.android.files.provider.archive.archiveFile
import me.zhanghai.android.files.provider.archive.isArchivePath
import me.zhanghai.android.files.provider.content.ContentPath
import me.zhanghai.android.files.provider.content.isContentPath
import me.zhanghai.android.files.provider.document.DocumentPath
import me.zhanghai.android.files.provider.document.isDocumentPath
import me.zhanghai.android.files.provider.linux.isLinuxPath
import java.io.File
import java.io.IOException
import java.util.Locale

private val ownProviderAuthorities: Set<String> by lazy {
    val packageName = application.packageName.lowercase(Locale.ROOT)
    setOf(
        BuildConfig.FILE_PROVIDIER_AUTHORITY.lowercase(Locale.ROOT),
        application.getString(R.string.app_provider_authority).lowercase(Locale.ROOT),
        "$packageName.file_provider",
        "$packageName.app_provider",
        "$packageName.shizuku",
    )
}

private val multiUserPrivateDirRegex: Regex by lazy {
    val escapedPackageName = Regex.escape(application.packageName)
    Regex("^/data/user(?:_de)?/\\d+/$escapedPackageName(?:/.*)?$")
}

private val openCacheRoots: List<String> by lazy {
    buildList {
        addCanonicalVariants(File(application.cacheDir, "open_cache"))
        ContextCompat.getDataDir(application)?.let { dataDir ->
            addCanonicalVariants(File(dataDir, "cache/open_cache"))
        }
    }.distinct()
}

private val privateRoots: List<String> by lazy {
    val packageName = application.packageName
    buildList {
        ContextCompat.getDataDir(application)?.let(::addCanonicalVariants)
        application.applicationInfo?.dataDir?.let { addCanonicalVariants(File(it)) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            application.applicationInfo?.deviceProtectedDataDir?.let { addCanonicalVariants(File(it)) }
        }
        application.filesDir?.let {
            addCanonicalVariants(it)
            it.parentFile?.let(::addCanonicalVariants)
        }
        application.cacheDir?.let(::addCanonicalVariants)
        application.noBackupFilesDir?.let(::addCanonicalVariants)
        application.codeCacheDir?.let(::addCanonicalVariants)
        addCanonicalVariants(File("/data/data/$packageName"))
        addCanonicalVariants(File("/data/user/0/$packageName"))
        addCanonicalVariants(File("/data/user_de/0/$packageName"))
    }.distinct()
}

fun Path.isAppPrivatePath(allowOpenCache: Boolean = false): Boolean =
    when {
        isArchivePath -> archiveFile.isAppPrivatePath(allowOpenCache)
        isContentPath -> isOwnProviderUri((this as? ContentPath)?.uri, allowOpenCache)
        isDocumentPath -> isOwnProviderUri((this as? DocumentPath)?.treeUri, allowOpenCache)
        isLinuxPath -> isAppPrivateFile(toFile(), allowOpenCache)
        else -> false
    }

private fun isOwnProviderUri(uri: Uri?, allowOpenCache: Boolean): Boolean {
    uri ?: return false
    if (uri.scheme.equals(ContentResolver.SCHEME_FILE, ignoreCase = true)) {
        return uri.path?.let { isAppPrivateFile(File(it), allowOpenCache) } == true
    }
    val authority = uri.authority?.substringAfter('@')?.lowercase(Locale.ROOT)
    return authority != null && authority in ownProviderAuthorities
}

private fun isAppPrivateFile(file: File, allowOpenCache: Boolean): Boolean {
    val candidates = buildList {
        add(canonicalizePath(file))
        add(normalizeLexicalPath(file.absolutePath))
    }.distinct()
    if (allowOpenCache) {
        val allInOpenCache = candidates.all { candidate ->
            openCacheRoots.any { root -> isStrictChild(candidate, root) }
        }
        if (allInOpenCache && candidates.isNotEmpty()) {
            return false
        }
    }
    return candidates.any { candidate ->
        multiUserPrivateDirRegex.matches(candidate) ||
            privateRoots.any { root -> isSameOrChild(candidate, root) }
    }
}

private fun MutableList<String>.addCanonicalVariants(dir: File) {
    add(normalizeLexicalPath(dir.absolutePath))
    add(canonicalizePath(dir))
}

private fun canonicalizePath(file: File): String {
    val trailing = ArrayDeque<String>()
    var current: File? = file
    while (current != null && !current.exists()) {
        val name = current.name
        if (name.isNotEmpty()) {
            trailing.addFirst(name)
        }
        val parent = current.parentFile ?: break
        current = parent
    }
    val basePath = try {
        (current ?: file).canonicalPath
    } catch (_: IOException) {
        normalizeLexicalPath((current ?: file).absolutePath)
    }
    val combined = if (trailing.isEmpty()) {
        basePath
    } else {
        "$basePath/${trailing.joinToString("/")}"
    }
    return normalizeLexicalPath(combined)
}

private fun normalizeLexicalPath(rawPath: String): String {
    val segments = ArrayList<String>()
    for (part in rawPath.split('/')) {
        when (part) {
            "", "." -> {}
            ".." -> if (segments.isNotEmpty()) {
                segments.removeAt(segments.lastIndex)
            }
            else -> segments.add(part)
        }
    }
    return if (segments.isEmpty()) "/" else "/" + segments.joinToString("/")
}

private fun isSameOrChild(candidate: String, root: String): Boolean =
    candidate == root || isStrictChild(candidate, root)

private fun isStrictChild(candidate: String, root: String): Boolean =
    if (root == "/") {
        candidate.length > 1
    } else {
        candidate.length > root.length &&
            candidate.startsWith(root) &&
            candidate[root.length] == '/'
    }

/*
 * Copyright (c) 2020 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.file

import me.zhanghai.android.files.util.asFileNameOrNull

val MimeType.isApk: Boolean
    get() = this == MimeType.APK

val MimeType.isSupportedArchive: Boolean
    get() = this in supportedArchiveMimeTypes

private val supportedArchiveMimeTypes = mutableListOf(
    "application/gzip",
    "application/java-archive",
    "application/rar",
    "application/zip",
    "application/zstd",
    "application/vnd.android.package-archive",
    "application/vnd.debian.binary-package",
    "application/vnd.ms-cab-compressed",
    "application/vnd.rar",
    "application/x-7z-compressed",
    "application/x-bzip2",
    "application/x-cab",
    "application/x-compress",
    "application/x-cpio",
    "application/x-deb",
    "application/x-debian-package",
    "application/x-gtar",
    "application/x-gtar-compressed",
    "application/x-iso9660-image",
    "application/x-java-archive",
    "application/x-lha",
    "application/x-lzma",
    "application/x-redhat-package-manager",
    "application/x-tar",
    "application/x-ustar",
    "application/x-xz"
).map { it.asMimeType() }.toSet()

val MimeType.isImage: Boolean
    get() = icon == MimeTypeIcon.IMAGE

val MimeType.isAudio: Boolean
    get() = icon == MimeTypeIcon.AUDIO

val MimeType.isVideo: Boolean
    get() = icon == MimeTypeIcon.VIDEO

val MimeType.isMedia: Boolean
    get() = isAudio || isVideo

val MimeType.isPdf: Boolean
    get() = this == MimeType.PDF

/**
 * Returns whether clicking this file should directly launch the built-in text editor without
 * showing an external app chooser.
 */
fun MimeType.shouldOpenWithTextEditor(fileName: String? = null): Boolean {
    if (isApk || isImage || isMedia || isPdf || isSupportedArchive) {
        return false
    }
    val extension = fileName?.asFileNameOrNull()?.singleExtension?.lowercase()
    if (extension != null && extension in nonTextBinaryExtensions) {
        return false
    }
    if (this in explicitNonTextMimeTypes) {
        return false
    }
    if (type == "text" ||
        icon == MimeTypeIcon.TEXT ||
        icon == MimeTypeIcon.CODE ||
        icon == MimeTypeIcon.CALENDAR ||
        icon == MimeTypeIcon.CONTACT ||
        suffix == "xml" ||
        suffix == "json" ||
        suffix == "yaml" ||
        this in additionalTextEditorMimeTypes
    ) {
        return true
    }
    if (this == MimeType.GENERIC) {
        return extension == null || extension !in nonTextBinaryExtensions
    }
    return false
}

private val explicitNonTextMimeTypes = setOf(
    "application/x-iso9660-image"
).map { it.asMimeType() }.toSet()

private val additionalTextEditorMimeTypes = setOf(
    "application/graphql",
    "application/ld+json",
    "application/manifest+json",
    "application/sql",
    "application/toml",
    "application/x-bat",
    "application/x-httpd-php",
    "application/x-powershell",
    "application/x-trash",
    "application/x-wine-extension-ini"
).map { it.asMimeType() }.toSet()

private val nonTextBinaryExtensions = setOf(
    "a", "apk", "apkm", "apks", "arsc", "art", "bin", "class", "dat", "db", "db-shm", "db-wal",
    "dex", "dll", "drv", "elf", "exe", "img", "iso", "ko", "lz4", "msi", "o", "oat", "odex",
    "pak", "pyc", "pyo", "qcow2", "raw", "res", "rom", "so", "sqlite", "sqlite3", "sys",
    "vdex", "vdi", "vhd", "vmdk", "wasm", "xapk"
)

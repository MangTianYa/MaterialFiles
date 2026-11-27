/*
 * Copyright (c) 2020 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.file

import android.webkit.MimeTypeMap
import me.zhanghai.android.files.compat.getMimeTypeFromExtensionCompat
import me.zhanghai.android.files.provider.common.PosixFileType
import me.zhanghai.android.files.util.asFileName
import me.zhanghai.android.files.util.asPathName

fun MimeType.Companion.guessFromPath(path: String): MimeType {
    val fileName = path.asPathName().fileName ?: return DIRECTORY
    return guessFromExtension(fileName.asFileName().singleExtension)
}

fun MimeType.Companion.guessFromExtension(extension: String): MimeType {
    val extension = extension.lowercase()
    return extensionToMimeTypeOverrideMap[extension]
        ?: MimeTypeMap.getSingleton().getMimeTypeFromExtensionCompat(extension)?.asMimeTypeOrNull()
        ?: GENERIC
}

// @see https://android.googlesource.com/platform/external/mime-support/+/master/mime.types
// @see https://android.googlesource.com/platform/frameworks/base/+/master/mime/java-res/android.mime.types
// @see http://www.iana.org/assignments/media-types/media-types.xhtml
// @see https://salsa.debian.org/debian/media-types/-/blob/master/mime.types
// @see /usr/share/mime/packages/freedesktop.org.xml
private val extensionToMimeTypeOverrideMap = mapOf(
    // Fixes
    "csv" to "text/csv", // Was "text/comma-separated-values"
    "sh" to "application/x-sh", // Was "text/x-sh"
    // Addition
    "bz" to "application/x-bzip",
    "bz2" to "application/x-bzip2",
    "z" to "application/x-compress",
    "lzma" to "application/x-lzma",
    "p7b" to "application/x-pkcs7-certificates",
    "spc" to "application/x-pkcs7-certificates", // Clashes with "chemical/x-galactic-spc"
    "ts" to "application/typescript", // Clashes with "video/mp2ts"
    "py3" to "text/x-python",
    "py3x" to "text/x-python",
    "pyx" to "text/x-python",
    "wsgi" to "text/x-python",
    "yml" to "application/yaml",
    "yaml" to "application/yaml",
    "toml" to "application/toml",
    "json5" to "application/json",
    "jsonc" to "application/json",
    "jsonl" to "application/json",
    "ndjson" to "application/json",
    "jsx" to "text/javascript",
    "tsx" to "application/typescript",
    "mjs" to "text/javascript",
    "cjs" to "text/javascript",
    "mts" to "application/typescript",
    "cts" to "application/typescript",
    "kt" to "text/x-kotlin",
    "kts" to "text/x-kotlin",
    "dart" to "text/x-dart",
    "rs" to "text/x-rust",
    "go" to "text/x-go",
    "swift" to "text/x-swift",
    "lua" to "text/x-lua",
    "groovy" to "text/x-groovy",
    "gradle" to "text/x-groovy",
    "scss" to "text/x-scss",
    "sass" to "text/x-sass",
    "less" to "text/x-less",
    "vue" to "text/html",
    "svelte" to "text/html",
    "sql" to "application/sql",
    "proto" to "text/x-protobuf",
    "ps1" to "application/x-powershell",
    "bat" to "application/x-bat",
    "cmd" to "application/x-bat",
    "asm" to "text/x-asm",
    "s" to "text/x-asm",
    "cs" to "text/x-csharp",
    "azw" to "application/vnd.amazon.ebook",
    "ibooks" to "application/x-ibooks+zip",
    "msg" to "application/vnd.ms-outlook",
    "mkd" to "text/markdown",
    "cfg" to "text/plain",
    "conf" to "text/plain",
    "env" to "text/plain",
    "ini" to "text/plain",
    "list" to "text/plain",
    "log" to "text/plain",
    "prop" to "text/plain",
    "properties" to "text/plain",
    "rc" to "text/plain",
    "diff" to "text/x-diff",
    "patch" to "text/x-diff",
    // Common audio formats
    "aac" to "audio/aac",
    "alac" to "audio/alac",
    "amr" to "audio/amr",
    "ape" to "audio/x-ape",
    "awb" to "audio/amr-wb",
    "dsf" to "audio/x-dsf",
    "flac" to "audio/flac",
    "m4a" to "audio/mp4",
    "m4b" to "audio/mp4",
    "mid" to "audio/midi",
    "midi" to "audio/midi",
    "mp3" to "audio/mpeg",
    "mp4a" to "audio/mp4",
    "oga" to "audio/ogg",
    "ogg" to "audio/ogg",
    "opus" to "audio/opus",
    "wav" to "audio/x-wav",
    "wma" to "audio/x-ms-wma",
    "wv" to "audio/x-wavpack"
).mapValues { it.value.asMimeType() }

fun MimeType.Companion.forSpecialPosixFileType(type: PosixFileType): MimeType? =
    specialPosixFileTypeToMimeTypeMap[type]

// See also https://developer.gnome.org/shared-mime-info-spec/
/** @see specialPosixFileTypeToNameResMap */
private val specialPosixFileTypeToMimeTypeMap = mapOf(
    PosixFileType.CHARACTER_DEVICE to "inode/chardevice",
    PosixFileType.BLOCK_DEVICE to "inode/blockdevice",
    PosixFileType.FIFO to "inode/fifo",
    PosixFileType.SYMBOLIC_LINK to "inode/symlink",
    PosixFileType.SOCKET to "inode/socket"
).mapValues { it.value.asMimeType() }

val MimeType.extension: String?
    // TODO: Add compat implementation as well.
    get() = MimeTypeMap.getSingleton().getExtensionFromMimeType(value)

val MimeType.intentType: String
    get() = intentMimeType.value

private val MimeType.intentMimeType: MimeType
    get() = mimeTypeToIntentMimeTypeMap[this] ?: this

private val mimeTypeToIntentMimeTypeMap = listOf(
    // Allows matching "text/*"
    "application/ecmascript" to "text/ecmascript",
    "application/javascript" to "text/javascript",
    "application/json" to "text/json",
    "application/typescript" to "text/typescript",
    "application/yaml" to "text/x-yaml",
    "application/x-sh" to "text/x-shellscript",
    "application/x-shellscript" to "text/x-shellscript",
    // Allows matching generic
    MimeType.GENERIC.value to MimeType.ANY.value
).associate { it.first.asMimeType() to it.second.asMimeType() }

val Collection<MimeType>.intentType: String
    get() {
        if (isEmpty()) {
            return MimeType.ANY.value
        }
        val intentMimeTypes = map { it.intentMimeType }
        val firstIntentMimeType = intentMimeTypes.first()
        if (intentMimeTypes.all { firstIntentMimeType.match(it) }) {
            return firstIntentMimeType.value
        }
        val wildcardIntentMimeType = MimeType.of(firstIntentMimeType.type, "*", null)
        if (intentMimeTypes.all { wildcardIntentMimeType.match(it) }) {
            return wildcardIntentMimeType.value
        }
        return MimeType.ANY.value
    }

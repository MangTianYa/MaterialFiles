/*
 * Copyright (c) 2019 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.util

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import java8.nio.file.Path
import java8.nio.file.Paths
import me.zhanghai.android.files.BuildConfig
import me.zhanghai.android.files.app.application
import me.zhanghai.android.files.compat.DocumentsContractCompat
import me.zhanghai.android.files.file.MimeType
import me.zhanghai.android.files.file.fileProviderUri
import me.zhanghai.android.files.file.isAudio
import me.zhanghai.android.files.file.shouldOpenWithTextEditor
import me.zhanghai.android.files.storage.createOrLog
import me.zhanghai.android.files.viewer.audio.AudioPlayerActivity
import me.zhanghai.android.files.viewer.text.TextEditorActivity
import java.net.URI
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

private const val EXTRA_PATH_URI = "${BuildConfig.APPLICATION_ID}.extra.PATH_URI"
private const val EXTRA_PATH_URI_LIST = "${BuildConfig.APPLICATION_ID}.extra.PATH_URI_LIST"
private const val EXTRA_INTERNAL_TOKEN = "${BuildConfig.APPLICATION_ID}.extra.INTERNAL_TOKEN"

private const val TOKEN_PREFS_NAME = "open_file_security"
private const val TOKEN_PREF_KEY_HMAC_SECRET = "hmac_secret"
private const val TOKEN_HMAC_ALGORITHM = "HmacSHA256"
private const val TOKEN_SECRET_KEY_SIZE_BYTES = 32
private val tokenSecretLock = Any()

private fun getOrCreateInternalSecretKey(): ByteArray {
    synchronized(tokenSecretLock) {
        val prefs = application.getSharedPreferences(TOKEN_PREFS_NAME, Context.MODE_PRIVATE)
        val encoded = prefs.getString(TOKEN_PREF_KEY_HMAC_SECRET, null)
        if (encoded != null) {
            try {
                val decoded = Base64.decode(encoded, Base64.NO_WRAP)
                if (decoded.size == TOKEN_SECRET_KEY_SIZE_BYTES) {
                    return decoded
                }
            } catch (_: IllegalArgumentException) {}
        }
        val secret = ByteArray(TOKEN_SECRET_KEY_SIZE_BYTES).apply { SecureRandom().nextBytes(this) }
        prefs.edit().putString(
            TOKEN_PREF_KEY_HMAC_SECRET,
            Base64.encodeToString(secret, Base64.NO_WRAP)
        ).commit()
        return secret
    }
}

private fun Intent.internalPathPayload(): String {
    val single = getStringExtra(EXTRA_PATH_URI).orEmpty()
    val list = getStringArrayListExtra(EXTRA_PATH_URI_LIST)?.joinToString("\u0001").orEmpty()
    return "$single\u0000$list"
}

internal fun computeInternalHmacToken(payload: String): String {
    val mac = Mac.getInstance(TOKEN_HMAC_ALGORITHM)
    mac.init(SecretKeySpec(getOrCreateInternalSecretKey(), TOKEN_HMAC_ALGORITHM))
    return Base64.encodeToString(mac.doFinal(payload.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
}

internal fun verifyInternalHmacToken(payload: String, token: String?): Boolean {
    if (token.isNullOrEmpty()) {
        return false
    }
    val actual = try {
        Base64.decode(token, Base64.NO_WRAP)
    } catch (_: IllegalArgumentException) {
        return false
    }
    val expected = try {
        Base64.decode(computeInternalHmacToken(payload), Base64.NO_WRAP)
    } catch (_: IllegalArgumentException) {
        return false
    }
    return MessageDigest.isEqual(expected, actual)
}

private fun Intent.signInternalPathExtras() {
    putExtra(EXTRA_INTERNAL_TOKEN, computeInternalHmacToken(internalPathPayload()))
}

val Intent.isTrustedInternalIntent: Boolean
    get() = verifyInternalHmacToken(internalPathPayload(), getStringExtra(EXTRA_INTERNAL_TOKEN))

internal val Intent.extraPathUriWithoutTrustCheck: Path?
    get() = getStringExtra(EXTRA_PATH_URI)
        ?.let { URI::class.createOrLog(it) }
        ?.let(::safePathsGet)

private fun safePathsGet(uri: URI): Path? =
    try {
        Paths.get(uri)
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }

private fun safePathsGet(path: String): Path? =
    try {
        Paths.get(path)
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }

var Intent.extraPath: Path?
    get() {
        if (isTrustedInternalIntent) {
            val extraPathUri = getStringExtra(EXTRA_PATH_URI)
            extraPathUri?.let { URI::class.createOrLog(it) }?.let(::safePathsGet)
                ?.let { return it }
            val extraAbsolutePath = getStringExtra("org.openintents.extra.ABSOLUTE_PATH")
                ?.takeIfNotEmpty()
            extraAbsolutePath?.let(::safePathsGet)?.let { return it }
        }
        data?.toPathOrNull()?.let { return it }
        val extraInitialUri = getParcelableExtraSafe<Uri>(DocumentsContractCompat.EXTRA_INITIAL_URI)
        extraInitialUri?.toPathOrNull()?.let { return it }
        return null
    }
    set(value) {
        // We cannot put Path into intent here, otherwise we will crash other apps unmarshalling it.
        // We cannot put URI into intent here either, because ShortcutInfo uses PersistableBundle
        // which doesn't support Serializable.
        putExtra(EXTRA_PATH_URI, value?.toUri()?.toString())
        signInternalPathExtras()
    }

val Intent.saveAsPath: Path?
    get() {
        val uri =
            when (action) {
                Intent.ACTION_VIEW -> data
                Intent.ACTION_SEND -> getParcelableExtraSafe(Intent.EXTRA_STREAM) as? Uri
                else -> null
            }
        return uri?.toPathOrNull()
    }

private fun Uri.toPathOrNull(): Path? =
    when (scheme) {
        ContentResolver.SCHEME_FILE, null -> path?.takeIfNotEmpty()?.let(::safePathsGet)
        ContentResolver.SCHEME_CONTENT -> {
            val uri = URI::class.createOrLog(toString())
                // Some people use Uri.parse() without encoding their path. Let's try saving
                // them by calling the other URI constructor that encodes everything.
                ?: URI::class.createOrLog(scheme, userInfo, host, port, path, query, fragment)
            uri?.let(::safePathsGet)
        }
        else -> null
    }

var Intent.extraPathList: List<Path>
    get() {
        if (isTrustedInternalIntent) {
            val extraPathUris = getStringArrayListExtra(EXTRA_PATH_URI_LIST)?.takeIfNotEmpty()
            extraPathUris?.let {
                return it.mapNotNull { uriString ->
                    URI::class.createOrLog(uriString)?.let(::safePathsGet)
                }
            }
        }
        return listOfNotNull(extraPath)
    }
    set(value) {
        // We cannot put Path into intent here, otherwise we will crash other apps unmarshalling it.
        val pathUris = ArrayList(value.map { it.toUri().toString() })
        putStringArrayListExtra(EXTRA_PATH_URI_LIST, pathUris)
        signInternalPathExtras()
    }

/**
 * Creates a view intent for [this] path and routes editable text/generic files directly to
 * [TextEditorActivity] when [allowInternalTextEditor] is true, bypassing the system chooser.
 */
fun Path.createDirectViewIntent(
    mimeType: MimeType,
    grantWriteUriPermission: Boolean = false,
    allowInternalTextEditor: Boolean = true
): Intent =
    fileProviderUri.createViewIntent(mimeType).apply {
        if (grantWriteUriPermission) {
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
        extraPath = this@createDirectViewIntent
        if (allowInternalTextEditor) {
            when {
                mimeType.isAudio -> setClass(application, AudioPlayerActivity::class.java)
                mimeType.shouldOpenWithTextEditor(fileName?.toString()) ->
                    setClass(application, TextEditorActivity::class.java)
            }
        }
    }

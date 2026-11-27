/*
 * Copyright (c) 2019 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filelist

import android.content.Intent
import android.os.Bundle
import java8.nio.file.Path
import me.zhanghai.android.files.app.AppActivity
import me.zhanghai.android.files.app.application
import me.zhanghai.android.files.file.MimeType
import me.zhanghai.android.files.file.asMimeTypeOrNull
import me.zhanghai.android.files.file.isAppPrivatePath
import me.zhanghai.android.files.filejob.FileJobService
import me.zhanghai.android.files.provider.archive.isArchivePath
import me.zhanghai.android.files.util.computeInternalHmacToken
import me.zhanghai.android.files.util.createDirectViewIntent
import me.zhanghai.android.files.util.extraPath
import me.zhanghai.android.files.util.extraPathUriWithoutTrustCheck
import me.zhanghai.android.files.util.startActivitySafe
import me.zhanghai.android.files.util.verifyInternalHmacToken

class OpenFileActivity : AppActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val intent = intent
        val token = intent.getStringExtra(EXTRA_TOKEN)
        val mimeType = intent.type?.asMimeTypeOrNull()
        val path = intent.extraPath
            ?: if (intent.action == ACTION_OPEN_FILE && !token.isNullOrEmpty()) {
                intent.extraPathUriWithoutTrustCheck
            } else {
                null
            }
        if (
            intent.action == ACTION_OPEN_FILE &&
            path != null &&
            !path.isAppPrivatePath(allowOpenCache = false) &&
            mimeType != null &&
            verifyInternalHmacToken(buildTokenPayload(path, mimeType), token)
        ) {
            openFile(path, mimeType)
        }
        finish()
    }

    private fun openFile(path: Path, mimeType: MimeType) {
        if (path.isArchivePath) {
            FileJobService.open(path, mimeType, false, this)
        } else {
            val intent = path.createDirectViewIntent(mimeType, grantWriteUriPermission = false)
            startActivitySafe(intent)
        }
    }

    companion object {
        private const val ACTION_OPEN_FILE = "me.zhanghai.android.files.intent.action.OPEN_FILE"
        private const val EXTRA_TOKEN = "me.zhanghai.android.files.intent.extra.TOKEN"

        fun createIntent(path: Path, mimeType: MimeType): Intent =
            Intent(ACTION_OPEN_FILE)
                .setPackage(application.packageName)
                .setType(mimeType.value)
                .apply {
                    extraPath = path
                    putExtra(EXTRA_TOKEN, computeInternalHmacToken(buildTokenPayload(path, mimeType)))
                }

        private fun buildTokenPayload(path: Path, mimeType: MimeType): String =
            "${mimeType.value}\u0000${path.toUri()}"
    }
}

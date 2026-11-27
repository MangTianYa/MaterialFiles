/*
 * Copyright (c) 2018 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filelist

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import androidx.activity.result.contract.ActivityResultContract
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import java8.nio.file.Path
import me.zhanghai.android.files.app.AppActivity
import me.zhanghai.android.files.file.MimeType
import me.zhanghai.android.files.util.createIntent
import me.zhanghai.android.files.util.extraPath
import me.zhanghai.android.files.util.putArgs

class FileListActivity : AppActivity() {
    private lateinit var fragment: Fragment

    private val isPickerIntent: Boolean
        get() = when (intent.action) {
            Intent.ACTION_GET_CONTENT,
            Intent.ACTION_OPEN_DOCUMENT,
            Intent.ACTION_CREATE_DOCUMENT,
            Intent.ACTION_OPEN_DOCUMENT_TREE -> true
            else -> false
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Calls ensureSubDecor().
        findViewById<View>(android.R.id.content)
        if (savedInstanceState == null) {
            fragment = if (isPickerIntent) {
                FileListFragment().putArgs(FileListFragment.Args(intent))
            } else {
                DualPaneFileListFragment().putArgs(DualPaneFileListFragment.Args(intent))
            }
            supportFragmentManager.commit { add(android.R.id.content, fragment) }
        } else {
            fragment = supportFragmentManager.findFragmentById(android.R.id.content)!!
        }
    }

    override fun onKeyShortcut(keyCode: Int, event: KeyEvent): Boolean {
        val handled = when (val currentFragment = fragment) {
            is DualPaneFileListFragment -> currentFragment.onKeyShortcut(keyCode, event)
            is FileListFragment -> currentFragment.onKeyShortcut(keyCode, event)
            else -> false
        }
        if (handled) {
            return true
        }
        return super.onKeyShortcut(keyCode, event)
    }

    companion object {
        fun createViewIntent(path: Path): Intent =
            FileListActivity::class.createIntent()
                .setAction(Intent.ACTION_VIEW)
                .apply { extraPath = path }
    }

    class OpenFileContract : ActivityResultContract<List<MimeType>, Path?>() {
        override fun createIntent(context: Context, input: List<MimeType>): Intent =
            FileListActivity::class.createIntent()
                .setAction(Intent.ACTION_OPEN_DOCUMENT)
                .setType(MimeType.ANY.value)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .putExtra(Intent.EXTRA_MIME_TYPES, input.map { it.value }.toTypedArray())

        override fun parseResult(resultCode: Int, intent: Intent?): Path? =
            if (resultCode == RESULT_OK) intent?.extraPath else null
    }

    class CreateFileContract : ActivityResultContract<Triple<MimeType, String?, Path?>, Path?>() {
        override fun createIntent(
            context: Context,
            input: Triple<MimeType, String?, Path?>
        ): Intent =
            FileListActivity::class.createIntent()
                .setAction(Intent.ACTION_CREATE_DOCUMENT)
                .setType(input.first.value)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .apply {
                    input.second?.let { putExtra(Intent.EXTRA_TITLE, it) }
                    input.third?.let { extraPath = it }
                }

        override fun parseResult(resultCode: Int, intent: Intent?): Path? =
            if (resultCode == RESULT_OK) intent?.extraPath else null
    }

    class OpenDirectoryContract : ActivityResultContract<Path?, Path?>() {
        override fun createIntent(context: Context, input: Path?): Intent =
            FileListActivity::class.createIntent()
                .setAction(Intent.ACTION_OPEN_DOCUMENT_TREE)
                .apply { input?.let { extraPath = it } }

        override fun parseResult(resultCode: Int, intent: Intent?): Path? =
            if (resultCode == RESULT_OK) intent?.extraPath else null
    }
}

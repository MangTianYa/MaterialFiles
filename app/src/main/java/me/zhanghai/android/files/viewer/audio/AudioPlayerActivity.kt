/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.viewer.audio

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.fragment.app.commit
import java8.nio.file.Path
import me.zhanghai.android.files.app.AppActivity
import me.zhanghai.android.files.util.extraPathList
import me.zhanghai.android.files.util.putArgs

class AudioPlayerActivity : AppActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Calls ensureSubDecor().
        findViewById<View>(android.R.id.content)
        if (savedInstanceState == null) {
            val intent = intent
            val position = intent.getIntExtra(EXTRA_POSITION, 0)
            val fragment = AudioPlayerFragment()
                .putArgs(AudioPlayerFragment.Args(intent, position))
            supportFragmentManager.commit { add(android.R.id.content, fragment) }
        }
    }

    companion object {
        private val EXTRA_POSITION = "${AudioPlayerActivity::class.java.name}.extra.POSITION"

        fun putExtras(intent: Intent, paths: List<Path>, position: Int) {
            intent.extraPathList = paths
            intent.putExtra(EXTRA_POSITION, position)
        }
    }
}

/*
 * Copyright (c) 2022 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filelist

import java8.nio.file.Path
import me.zhanghai.android.files.R
import me.zhanghai.android.files.util.asPathNameOrNull

abstract class PathDialogFragment : NameDialogFragment() {
    override fun isNameValid(name: String): Boolean {
        if (!super.isNameValid(name)) {
            return false
        }
        if (name.isEmpty()) {
            binding.nameLayout.error = getString(R.string.file_list_path_error_empty)
            return false
        }
        if (name.asPathNameOrNull() == null || name.toNavigablePathOrNull() == null) {
            binding.nameLayout.error = getString(R.string.file_list_path_error_invalid)
            return false
        }
        return true
    }

    final override fun onOk(name: String) {
        onOk(name.toNavigablePathOrNull()!!)
    }

    abstract fun onOk(path: Path)
}

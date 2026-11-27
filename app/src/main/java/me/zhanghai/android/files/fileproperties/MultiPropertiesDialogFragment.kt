/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.fileproperties

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import androidx.appcompat.app.AppCompatDialogFragment
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java8.nio.file.FileVisitResult
import java8.nio.file.FileVisitor
import java8.nio.file.Files
import java8.nio.file.Path
import java8.nio.file.attribute.BasicFileAttributes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.parcelize.Parcelize
import me.zhanghai.android.files.R
import me.zhanghai.android.files.databinding.MultiPropertiesDialogBinding
import me.zhanghai.android.files.file.FileItem
import me.zhanghai.android.files.file.asFileSize
import me.zhanghai.android.files.file.formatLong
import me.zhanghai.android.files.filelist.toUserFriendlyString
import me.zhanghai.android.files.util.ParcelableArgs
import me.zhanghai.android.files.util.args
import me.zhanghai.android.files.util.layoutInflater
import me.zhanghai.android.files.util.putArgs
import me.zhanghai.android.files.util.show
import java.io.IOException

class MultiPropertiesDialogFragment : AppCompatDialogFragment() {
    private val args by args<Args>()

    private lateinit var binding: MultiPropertiesDialogBinding
    private var calculateJob: Job? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()
        val files = args.files
        binding = MultiPropertiesDialogBinding.inflate(context.layoutInflater)

        val dirCount = files.count { it.attributes.isDirectory }
        val fileCount = files.size - dirCount
        binding.selectedValueText.text = getString(
            R.string.multi_properties_selected_value_format, dirCount, fileCount
        )
        binding.locationValueText.text =
            files.firstOrNull()?.path?.parent?.toUserFriendlyString() ?: "/"

        val initialFileBytes = files.filterNot { it.attributes.isDirectory }
            .sumOf { it.attributes.size() }
        binding.sizeValueText.text = formatSizeText(initialFileBytes)
        binding.contentsValueText.text = if (dirCount > 0) {
            getString(R.string.multi_properties_contents_calculating)
        } else {
            getString(R.string.multi_properties_contents_format, 0, fileCount)
        }

        val modifiedTimes = files.map { it.attributes.lastModifiedTime().toInstant() }.sorted()
        binding.modifiedValueText.text = when {
            modifiedTimes.isEmpty() -> "-"
            modifiedTimes.first() == modifiedTimes.last() ->
                modifiedTimes.first().formatLong()
            else -> "${modifiedTimes.first().formatLong()} ~ " +
                modifiedTimes.last().formatLong()
        }

        startCalculatingTotals(files)

        return MaterialAlertDialogBuilder(context, theme)
            .setTitle(getString(R.string.multi_properties_title_format, files.size))
            .setView(binding.root)
            .setPositiveButton(android.R.string.ok, null)
            .create()
    }

    private fun startCalculatingTotals(files: List<FileItem>) {
        calculateJob?.cancel()
        calculateJob = lifecycleScope.launch(Dispatchers.IO) {
            var totalFolders = 0
            var totalFiles = 0
            var totalBytes = 0L
            var lastNotifyMillis = 0L

            suspend fun publishProgress() {
                if (!isActive || !isAdded) return
                val snapshotFolders = totalFolders
                val snapshotFiles = totalFiles
                val snapshotBytes = totalBytes
                withContext(Dispatchers.Main) {
                    if (!isAdded) return@withContext
                    binding.contentsValueText.text = getString(
                        R.string.multi_properties_contents_format, snapshotFolders, snapshotFiles
                    )
                    binding.sizeValueText.text = formatSizeText(snapshotBytes)
                }
            }

            for (item in files) {
                if (!isActive) break
                if (!item.attributes.isDirectory) {
                    totalFiles++
                    totalBytes += item.attributes.size()
                    continue
                }
                totalFolders++
                runCatching {
                    Files.walkFileTree(item.path, object : FileVisitor<Path> {
                        override fun preVisitDirectory(
                            dir: Path,
                            attrs: BasicFileAttributes
                        ): FileVisitResult {
                            if (!isActive) return FileVisitResult.TERMINATE
                            if (dir != item.path) {
                                totalFolders++
                                totalBytes += attrs.size()
                            }
                            return FileVisitResult.CONTINUE
                        }

                        override fun visitFile(
                            file: Path,
                            attrs: BasicFileAttributes
                        ): FileVisitResult {
                            if (!isActive) return FileVisitResult.TERMINATE
                            totalFiles++
                            totalBytes += attrs.size()
                            val now = System.currentTimeMillis()
                            if (now - lastNotifyMillis >= PROGRESS_INTERVAL_MILLIS) {
                                lastNotifyMillis = now
                                val fCount = totalFolders
                                val fiCount = totalFiles
                                val bCount = totalBytes
                                activity?.runOnUiThread {
                                    if (isAdded) {
                                        binding.contentsValueText.text = getString(
                                            R.string.multi_properties_contents_format,
                                            fCount,
                                            fiCount
                                        )
                                        binding.sizeValueText.text = formatSizeText(bCount)
                                    }
                                }
                            }
                            return FileVisitResult.CONTINUE
                        }

                        override fun visitFileFailed(
                            file: Path,
                            exc: IOException
                        ): FileVisitResult = FileVisitResult.CONTINUE

                        override fun postVisitDirectory(
                            dir: Path,
                            exc: IOException?
                        ): FileVisitResult = FileVisitResult.CONTINUE
                    })
                }
            }
            publishProgress()
        }
    }

    private fun formatSizeText(bytes: Long): String {
        val context = requireContext()
        val fileSize = bytes.asFileSize()
        val inBytes = fileSize.formatInBytes(context)
        return if (fileSize.isHumanReadableInBytes) {
            inBytes
        } else {
            getString(
                R.string.file_properties_basic_size_with_human_readable_format,
                fileSize.formatHumanReadable(context),
                inBytes
            )
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        calculateJob?.cancel()
        super.onDismiss(dialog)
    }

    override fun onDestroy() {
        calculateJob?.cancel()
        super.onDestroy()
    }

    companion object {
        private const val PROGRESS_INTERVAL_MILLIS = 200L

        fun show(files: List<FileItem>, fragment: Fragment) {
            MultiPropertiesDialogFragment().putArgs(Args(files)).show(fragment)
        }
    }

    @Parcelize
    class Args(val files: List<FileItem>) : ParcelableArgs
}

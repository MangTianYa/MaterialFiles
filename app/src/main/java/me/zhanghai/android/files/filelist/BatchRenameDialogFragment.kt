/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filelist

import android.app.Dialog
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.WindowManager
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDialogFragment
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java8.nio.file.Path
import kotlinx.parcelize.Parcelize
import me.zhanghai.android.files.R
import me.zhanghai.android.files.databinding.BatchRenameDialogBinding
import me.zhanghai.android.files.file.FileItem
import me.zhanghai.android.files.util.ParcelableArgs
import me.zhanghai.android.files.util.args
import me.zhanghai.android.files.util.asFileNameOrNull
import me.zhanghai.android.files.util.getColorByAttr
import me.zhanghai.android.files.util.layoutInflater
import me.zhanghai.android.files.util.putArgs
import me.zhanghai.android.files.util.show

class BatchRenameDialogFragment : AppCompatDialogFragment() {
    private val args by args<Args>()

    private lateinit var binding: BatchRenameDialogBinding
    private var currentMode = Mode.NUMBER
    private var plannedRenames: List<Pair<FileItem, String>> = emptyList()
    private var positiveButtonEnabled = false

    private enum class Mode {
        NUMBER,
        REPLACE,
        AFFIX
    }

    private val listener: Listener
        get() = requireParentFragment() as Listener

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()
        binding = BatchRenameDialogBinding.inflate(context.layoutInflater)
        binding.summaryText.text = getString(R.string.batch_rename_summary_format, args.files.size)

        binding.modeNumberButton.setOnClickListener { switchMode(Mode.NUMBER) }
        binding.modeReplaceButton.setOnClickListener { switchMode(Mode.REPLACE) }
        binding.modeAffixButton.setOnClickListener { switchMode(Mode.AFFIX) }

        val refresh = { _: Any? -> updatePreviewAndValidation() }
        binding.templateEdit.doAfterTextChanged(refresh)
        binding.startEdit.doAfterTextChanged(refresh)
        binding.digitsEdit.doAfterTextChanged(refresh)
        binding.findEdit.doAfterTextChanged(refresh)
        binding.replaceEdit.doAfterTextChanged(refresh)
        binding.prefixEdit.doAfterTextChanged(refresh)
        binding.suffixEdit.doAfterTextChanged(refresh)
        binding.caseSensitiveCheckBox.setOnCheckedChangeListener { _, _ ->
            updatePreviewAndValidation()
        }
        binding.keepExtensionCheckBox.setOnCheckedChangeListener { _, _ ->
            updatePreviewAndValidation()
        }

        if (savedInstanceState == null) {
            binding.templateEdit.setText("{name}_")
            binding.templateEdit.setSelection(0, "{name}_".length)
        } else {
            currentMode = Mode.entries.getOrElse(savedInstanceState.getInt(STATE_MODE, 0)) {
                Mode.NUMBER
            }
        }
        switchMode(currentMode)

        return MaterialAlertDialogBuilder(context, theme)
            .setTitle(R.string.batch_rename_title)
            .setView(binding.root)
            .setPositiveButton(android.R.string.ok, null)
            .setNegativeButton(android.R.string.cancel, null)
            .create()
            .apply {
                window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
                setOnShowListener {
                    getButton(AlertDialog.BUTTON_POSITIVE).apply {
                        isEnabled = positiveButtonEnabled
                        setOnClickListener { onConfirm() }
                    }
                }
            }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_MODE, currentMode.ordinal)
    }

    private fun switchMode(mode: Mode) {
        currentMode = mode
        binding.panelNumberMode.isVisible = mode == Mode.NUMBER
        binding.panelReplaceMode.isVisible = mode == Mode.REPLACE
        binding.panelAffixMode.isVisible = mode == Mode.AFFIX

        val context = requireContext()
        val selectedBg = ColorStateList.valueOf(
            context.getColorByAttr(androidx.appcompat.R.attr.colorPrimary)
        )
        val selectedFg = context.getColorByAttr(com.google.android.material.R.attr.colorOnPrimary)
        val unselectedFg =
            context.getColorByAttr(com.google.android.material.R.attr.colorOnSurfaceVariant)
        val styleButton: (TextView, Boolean) -> Unit = { button, active ->
            button.backgroundTintList = if (active) selectedBg else null
            button.setTextColor(if (active) selectedFg else unselectedFg)
        }
        styleButton(binding.modeNumberButton, mode == Mode.NUMBER)
        styleButton(binding.modeReplaceButton, mode == Mode.REPLACE)
        styleButton(binding.modeAffixButton, mode == Mode.AFFIX)

        updatePreviewAndValidation()
    }

    private fun updatePreviewAndValidation() {
        if (!this::binding.isInitialized) return
        val files = args.files
        val keepExtension = binding.keepExtensionCheckBox.isChecked
        val startIndex = binding.startEdit.text?.toString()?.toIntOrNull() ?: 1
        val digits = (binding.digitsEdit.text?.toString()?.toIntOrNull() ?: 2).coerceIn(1, 6)
        val template = binding.templateEdit.text?.toString().orEmpty().trim()
        val findText = binding.findEdit.text?.toString().orEmpty()
        val replaceText = binding.replaceEdit.text?.toString().orEmpty()
        val caseSensitive = binding.caseSensitiveCheckBox.isChecked
        val prefixText = binding.prefixEdit.text?.toString().orEmpty()
        val suffixText = binding.suffixEdit.text?.toString().orEmpty()

        val computed = ArrayList<Pair<FileItem, String>>(files.size)
        for ((index, item) in files.withIndex()) {
            val origName = item.name
            val useSplit = keepExtension && !item.attributes.isDirectory && item.extension.isNotEmpty()
            val base = if (useSplit) item.baseName else origName
            val extSuffix = if (useSplit) ".${item.extension}" else ""

            val newBase = when (currentMode) {
                Mode.NUMBER -> {
                    val numStr = (startIndex + index).toString().padStart(digits, '0')
                    when {
                        template.isEmpty() -> "${base}_$numStr"
                        template.contains("{name}") || template.contains("{num}") -> {
                            val withName = template.replace("{name}", base)
                            if (withName.contains("{num}")) {
                                withName.replace("{num}", numStr)
                            } else {
                                "$withName$numStr"
                            }
                        }
                        template.endsWith('_') || template.endsWith('-') ||
                            template.endsWith(' ') || template.endsWith('.') ->
                            "$template$numStr"
                        else -> "${template}_$numStr"
                    }
                }
                Mode.REPLACE -> {
                    if (findText.isEmpty()) {
                        base
                    } else {
                        base.replace(findText, replaceText, ignoreCase = !caseSensitive)
                    }
                }
                Mode.AFFIX -> "$prefixText$base$suffixText"
            }
            computed.add(item to "$newBase$extSuffix")
        }

        val selectedPaths = files.mapTo(HashSet(files.size)) { it.path }
        var errorMessage: String? = null
        val invalidEntry = computed.firstOrNull { (_, newName) ->
            newName.isEmpty() || newName.asFileNameOrNull() == null
        }
        if (invalidEntry != null) {
            errorMessage = getString(R.string.batch_rename_status_invalid_name_format, invalidEntry.second)
        } else {
            val duplicate = computed.groupingBy { it.second }.eachCount().entries.firstOrNull { it.value > 1 }
            if (duplicate != null) {
                errorMessage = getString(R.string.batch_rename_status_duplicate_format, duplicate.key)
            } else {
                val externalConflict = computed.firstOrNull { (item, newName) ->
                    newName != item.name && listener.hasFileWithNameOutsideSelection(newName, selectedPaths)
                }
                if (externalConflict != null) {
                    errorMessage = getString(R.string.batch_rename_status_exists_format, externalConflict.second)
                }
            }
        }

        val changedPairs = computed.filter { (item, newName) -> item.name != newName }
        val context = requireContext()
        val okColor = context.getColorByAttr(androidx.appcompat.R.attr.colorPrimary)
        val normalColor = context.getColorByAttr(com.google.android.material.R.attr.colorOnSurfaceVariant)
        val errorColor = context.getColorByAttr(androidx.appcompat.R.attr.colorError)

        when {
            errorMessage != null -> {
                binding.statusText.text = errorMessage
                binding.statusText.setTextColor(errorColor)
                positiveButtonEnabled = false
            }
            changedPairs.isEmpty() -> {
                binding.statusText.setText(R.string.batch_rename_status_no_change)
                binding.statusText.setTextColor(normalColor)
                positiveButtonEnabled = false
            }
            else -> {
                binding.statusText.text =
                    getString(R.string.batch_rename_status_ready_format, changedPairs.size)
                binding.statusText.setTextColor(okColor)
                positiveButtonEnabled = true
            }
        }
        plannedRenames = changedPairs

        val maxPreviewLines = 15
        val previewLines = computed.take(maxPreviewLines).joinToString("\n") { (item, newName) ->
            if (item.name == newName) {
                "${item.name}  (未变)"
            } else {
                "${item.name}  →  $newName"
            }
        }
        binding.previewText.text = if (computed.size > maxPreviewLines) {
            "$previewLines\n… (+${computed.size - maxPreviewLines})"
        } else {
            previewLines
        }

        (dialog as? AlertDialog)?.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled =
            positiveButtonEnabled
    }

    private fun onConfirm() {
        if (!positiveButtonEnabled || plannedRenames.isEmpty()) {
            return
        }
        listener.batchRenameFiles(plannedRenames)
        dismiss()
    }

    companion object {
        private const val STATE_MODE = "mode"

        fun show(files: List<FileItem>, fragment: Fragment) {
            BatchRenameDialogFragment().putArgs(Args(files)).show(fragment)
        }
    }

    @Parcelize
    class Args(val files: List<FileItem>) : ParcelableArgs

    interface Listener {
        fun hasFileWithNameOutsideSelection(name: String, excludedPaths: Set<Path>): Boolean
        fun batchRenameFiles(renames: List<Pair<FileItem, String>>)
    }
}

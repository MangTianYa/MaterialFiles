/*
 * Copyright (c) 2019 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.viewer.text

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.SubMenu
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.children
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import java8.nio.file.Path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.parcelize.Parcelize
import me.zhanghai.android.files.R
import me.zhanghai.android.files.databinding.TextEditorFragmentBinding
import me.zhanghai.android.files.file.MimeType
import me.zhanghai.android.files.file.guessFromPath
import me.zhanghai.android.files.file.isAppPrivatePath
import me.zhanghai.android.files.filelist.RecentHistoryManager
import me.zhanghai.android.files.filelist.name
import me.zhanghai.android.files.settings.Settings
import me.zhanghai.android.files.ui.ThemedFastScroller
import me.zhanghai.android.files.util.ActionState
import me.zhanghai.android.files.util.DataState
import me.zhanghai.android.files.util.ParcelableArgs
import me.zhanghai.android.files.util.addOnBackPressedCallback
import me.zhanghai.android.files.util.args
import me.zhanghai.android.files.util.extraPath
import me.zhanghai.android.files.util.fadeInUnsafe
import me.zhanghai.android.files.util.fadeOutUnsafe
import me.zhanghai.android.files.util.isReady
import me.zhanghai.android.files.util.isTrustedInternalIntent
import me.zhanghai.android.files.util.showToast
import me.zhanghai.android.files.util.valueCompat
import me.zhanghai.android.files.util.viewModels
import java.nio.charset.Charset

class TextEditorFragment : Fragment(), ConfirmReloadDialogFragment.Listener,
    ConfirmCloseDialogFragment.Listener {
    private val args by args<Args>()
    private lateinit var argsFile: Path
    private var isInvalidArgsFile = false

    private lateinit var binding: TextEditorFragmentBinding

    private lateinit var menuBinding: MenuBinding

    private val viewModel by viewModels { { TextEditorViewModel(argsFile) } }

    private lateinit var onBackPressedCallback: OnBackPressedCallback

    private var isSettingText = false
    private var textVersion = 0L
    private var highlightJob: Job? = null
    private var formatJob: Job? = null
    private var isFormatting = false
    private var defaultTypeface: Typeface? = null
    private var detectedLanguage = SyntaxLanguage.PLAIN_TEXT
    private var hasRecordedRecentAccess = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val allowOpenCache = args.intent.isTrustedInternalIntent
        val resolvedFile = args.intent.extraPath
        if (resolvedFile == null || resolvedFile.isAppPrivatePath(allowOpenCache = allowOpenCache)) {
            isInvalidArgsFile = true
            finish()
            return
        }
        argsFile = resolvedFile

        setHasOptionsMenu(true)

        onBackPressedCallback = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                ConfirmCloseDialogFragment.show(this@TextEditorFragment)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View =
        TextEditorFragmentBinding.inflate(inflater, container, false)
            .also { binding = it }
            .root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (isInvalidArgsFile) {
            finish()
            return
        }

        addOnBackPressedCallback(onBackPressedCallback)

        val activity = requireActivity() as AppCompatActivity
        activity.setSupportActionBar(binding.toolbar)
        activity.supportActionBar?.setDisplayHomeAsUpEnabled(true)
        updateSubtitle()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.encoding.collect { onEncodingChanged(it) } }
                launch { viewModel.textState.collect { onTextStateChanged(it) } }
                launch {
                    viewModel.isTextChanged.collect {
                        onBackPressedCallback.isEnabled = it
                        onIsTextChangedChanged(it)
                    }
                }
                launch { viewModel.writeFileState.collect { onWriteFileStateChanged(it) } }
            }
        }

        ThemedFastScroller.create(binding.scrollView)
        defaultTypeface = binding.textEdit.typeface
        detectedLanguage = SyntaxLanguage.detect(argsFile.name)
        applyEditorTypeface(detectedLanguage)
        binding.textEdit.showLineNumbers = Settings.TEXT_EDITOR_SHOW_LINE_NUMBERS.valueCompat
        binding.textEdit.onCursorLineChangedListener = { _, _ -> updateSubtitle() }
        // Manually save and restore state in view model to avoid TransactionTooLargeException.
        binding.textEdit.isSaveEnabled = false
        val textEditSavedState = viewModel.removeEditTextSavedState()
        if (textEditSavedState != null) {
            binding.textEdit.onRestoreInstanceState(textEditSavedState)
        }
        binding.textEdit.doAfterTextChanged {
            textVersion++
            if (isSettingText) {
                return@doAfterTextChanged
            }
            // Might happen if the animation is running and user is quick enough.
            if (viewModel.textState.value !is DataState.Success) {
                return@doAfterTextChanged
            }
            viewModel.isTextChanged.value = true
            scheduleSyntaxHighlight(immediate = false)
        }

        Settings.TEXT_EDITOR_SHOW_LINE_NUMBERS.observe(
            viewLifecycleOwner, this::onShowLineNumbersChanged
        )
    }

    override fun onDestroyView() {
        highlightJob?.cancel()
        highlightJob = null
        formatJob?.cancel()
        formatJob = null
        super.onDestroyView()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        if (!isInvalidArgsFile && this::binding.isInitialized) {
            viewModel.setEditTextSavedState(binding.textEdit.onSaveInstanceState())
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)

        menuBinding = MenuBinding.inflate(menu, inflater)
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)

        updateFormatMenuItems()
        updateSaveMenuItem()
        updateShowLineNumbersMenuItem()
        updateEncodingMenuItems()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
        when (item.itemId) {
            R.id.action_format -> {
                formatCurrentDocument(asYaml = detectedLanguage == SyntaxLanguage.YAML)
                true
            }
            R.id.action_format_json -> {
                formatCurrentDocument(asYaml = false)
                true
            }
            R.id.action_format_yaml -> {
                formatCurrentDocument(asYaml = true)
                true
            }
            R.id.action_save -> {
                save()
                true
            }
            R.id.action_reload -> {
                onReload()
                true
            }
            R.id.action_show_line_numbers -> {
                Settings.TEXT_EDITOR_SHOW_LINE_NUMBERS.putValue(
                    !Settings.TEXT_EDITOR_SHOW_LINE_NUMBERS.valueCompat
                )
                true
            }
            Menu.FIRST -> {
                if (viewModel.isTextChanged.value) {
                    showToast(R.string.text_editor_save_before_encoding_change)
                    updateEncodingMenuItems()
                } else {
                    val charsetName = item.titleCondensed?.toString()
                    if (!charsetName.isNullOrEmpty()) {
                        viewModel.encoding.value = Charset.forName(charsetName)
                    }
                }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }

    fun onSupportNavigateUp(): Boolean {
        if (!isInvalidArgsFile && this::onBackPressedCallback.isInitialized &&
            onBackPressedCallback.isEnabled
        ) {
            onBackPressedCallback.handleOnBackPressed()
            return true
        }
        return false
    }

    override fun finish() {
        requireActivity().finish()
    }

    private fun onEncodingChanged(encoding: Charset) {
        updateEncodingMenuItems()
    }

    private fun updateEncodingMenuItems() {
        if (!this::menuBinding.isInitialized || isInvalidArgsFile) {
            return
        }
        val charsetName = viewModel.encoding.value.name()
        val charsetItem = menuBinding.encodingSubMenu.children
            .find { it.titleCondensed == charsetName }
        charsetItem?.isChecked = true
    }

    private fun onTextStateChanged(state: DataState<String>) {
        updateTitle()
        when (state) {
            is DataState.Loading -> {
                highlightJob?.cancel()
                binding.progress.fadeInUnsafe()
                binding.errorText.fadeOutUnsafe()
                binding.textEdit.fadeOutUnsafe()
                updateSubtitle()
                updateFormatMenuItems()
            }
            is DataState.Success -> {
                binding.progress.fadeOutUnsafe()
                binding.errorText.fadeOutUnsafe()
                binding.textEdit.fadeInUnsafe()
                if (!hasRecordedRecentAccess && !argsFile.isAppPrivatePath(allowOpenCache = false)) {
                    hasRecordedRecentAccess = true
                    RecentHistoryManager.recordFile(
                        argsFile, MimeType.guessFromPath(argsFile.toString())
                    )
                }
                if (!viewModel.isTextChanged.value) {
                    setText(state.data)
                } else {
                    scheduleSyntaxHighlight(immediate = true)
                }
                updateSubtitle()
                updateFormatMenuItems()
            }
            is DataState.Error -> {
                highlightJob?.cancel()
                state.throwable.printStackTrace()
                binding.progress.fadeOutUnsafe()
                binding.errorText.fadeInUnsafe()
                binding.errorText.text = state.throwable.toString()
                binding.textEdit.fadeOutUnsafe()
                updateSubtitle()
                updateFormatMenuItems()
            }
        }
    }

    private fun onShowLineNumbersChanged(showLineNumbers: Boolean) {
        binding.textEdit.showLineNumbers = showLineNumbers
        updateShowLineNumbersMenuItem()
    }

    private fun updateShowLineNumbersMenuItem() {
        if (!this::menuBinding.isInitialized) {
            return
        }
        menuBinding.showLineNumbersItem.isChecked = Settings.TEXT_EDITOR_SHOW_LINE_NUMBERS.valueCompat
    }

    private fun updateSubtitle() {
        val actionBar = (activity as? AppCompatActivity)?.supportActionBar ?: return
        actionBar.subtitle = if (viewModel.textState.value is DataState.Success && view != null) {
            getString(
                R.string.text_editor_subtitle_format,
                binding.textEdit.currentLineNumber,
                binding.textEdit.totalLineCount
            )
        } else {
            null
        }
    }

    private fun setText(text: String?) {
        // This is also called after saving because we'll be updating our state of the unchanged
        // text, but we don't want to call TextView.setText() again which resets things like cursor
        // position.
        if (binding.textEdit.text.toString() != text) {
            isSettingText = true
            binding.textEdit.setText(text)
            isSettingText = false
        }
        viewModel.isTextChanged.value = false
        scheduleSyntaxHighlight(immediate = true)
    }

    private fun applyEditorTypeface(language: SyntaxLanguage) {
        val target = if (language.isCodeOrConfig) Typeface.MONOSPACE else defaultTypeface
        if (target != null && binding.textEdit.typeface != target) {
            binding.textEdit.typeface = target
        }
    }

    private fun scheduleSyntaxHighlight(immediate: Boolean) {
        if (!isAdded || view == null || !this::binding.isInitialized) {
            return
        }
        if (viewModel.textState.value !is DataState.Success) {
            return
        }
        val editable = binding.textEdit.text ?: return
        val snapshot = editable.toString()
        val version = textVersion
        val newLanguage = SyntaxLanguage.detect(argsFile.name, snapshot)
        if (detectedLanguage != newLanguage) {
            detectedLanguage = newLanguage
            applyEditorTypeface(newLanguage)
            updateFormatMenuItems()
            activity?.invalidateOptionsMenu()
        }
        highlightJob?.cancel()
        if (newLanguage == SyntaxLanguage.PLAIN_TEXT) {
            SyntaxHighlighter.applyHighlights(editable, SyntaxHighlightResult(emptyList()))
            return
        }
        val isLight = SyntaxHighlighter.paletteFor(requireContext())
        highlightJob = viewLifecycleOwner.lifecycleScope.launch {
            if (!immediate) {
                delay(HIGHLIGHT_DEBOUNCE_MILLIS)
            }
            val result = withContext(Dispatchers.Default) {
                SyntaxHighlighter.computeHighlights(snapshot, newLanguage, isLight) {
                    coroutineContext.ensureActive()
                }
            }
            if (!isAdded || view == null || version != textVersion) {
                return@launch
            }
            binding.textEdit.text?.let { currentEditable ->
                SyntaxHighlighter.applyHighlights(currentEditable, result)
            }
        }
    }

    private fun formatCurrentDocument(asYaml: Boolean) {
        if (isFormatting || viewModel.textState.value !is DataState.Success ||
            !this::binding.isInitialized
        ) {
            return
        }
        val raw = binding.textEdit.text?.toString().orEmpty()
        val previousLine = binding.textEdit.currentLineNumber
        isFormatting = true
        updateFormatMenuItems()
        formatJob?.cancel()
        formatJob = viewLifecycleOwner.lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.Default) {
                    if (asYaml) {
                        CodeFormatter.formatYaml(raw)
                    } else {
                        CodeFormatter.formatJson(raw)
                    }
                }
                if (!isAdded || view == null) {
                    return@launch
                }
                val editable = binding.textEdit.text ?: return@launch
                if (editable.toString() != raw) {
                    return@launch
                }
                when (result) {
                    is CodeFormatter.Result.Error -> {
                        showToast(getString(R.string.text_editor_format_error_format, result.reason))
                    }
                    is CodeFormatter.Result.Success -> {
                        if (result.formatted == raw) {
                            showToast(R.string.text_editor_format_already)
                        } else {
                            editable.replace(0, editable.length, result.formatted)
                            val targetOffset = findOffsetAtLineStart(result.formatted, previousLine)
                            binding.textEdit.setSelection(
                                targetOffset.coerceIn(0, result.formatted.length)
                            )
                            val targetLang = if (asYaml) SyntaxLanguage.YAML else SyntaxLanguage.JSON
                            if (detectedLanguage != targetLang) {
                                detectedLanguage = targetLang
                                applyEditorTypeface(targetLang)
                            }
                            scheduleSyntaxHighlight(immediate = true)
                            showToast(
                                if (asYaml) {
                                    R.string.text_editor_format_yaml_success
                                } else {
                                    R.string.text_editor_format_json_success
                                }
                            )
                        }
                    }
                }
            } finally {
                isFormatting = false
                updateFormatMenuItems()
            }
        }
    }

    private fun findOffsetAtLineStart(text: String, oneBasedLine: Int): Int {
        if (oneBasedLine <= 1 || text.isEmpty()) {
            return 0
        }
        var currentLine = 1
        var index = 0
        while (index < text.length && currentLine < oneBasedLine) {
            if (text[index] == '\n') {
                currentLine++
            }
            index++
        }
        return index
    }

    private fun onIsTextChangedChanged(changed: Boolean) {
        updateTitle()
    }

    private fun updateTitle() {
        val fileName = viewModel.file.value.name
        val changed = viewModel.isTextChanged.value
        requireActivity().title = getString(
            if (changed) {
                R.string.text_editor_title_changed_format
            } else {
                R.string.text_editor_title_format
            }, fileName
        )
    }

    private fun onReload() {
        if (viewModel.isTextChanged.value) {
            ConfirmReloadDialogFragment.show(this)
        } else {
            reload()
        }
    }

    override fun reload() {
        viewModel.isTextChanged.value = false
        viewModel.reload()
    }

    private fun save() {
        val text = binding.textEdit.text.toString()
        viewModel.writeFile(argsFile, text, requireContext())
    }

    private fun onWriteFileStateChanged(state: ActionState<Pair<Path, String>, Unit>) {
        when (state) {
            is ActionState.Ready, is ActionState.Running -> updateSaveMenuItem()
            is ActionState.Success -> {
                showToast(R.string.text_editor_save_success)
                if (this::binding.isInitialized &&
                    state.argument.second == binding.textEdit.text?.toString()
                ) {
                    viewModel.isTextChanged.value = false
                }
                viewModel.finishWritingFile()
            }
            // The error will be toasted by service so we should never show it in UI.
            is ActionState.Error -> viewModel.finishWritingFile()
        }
    }

    private fun updateSaveMenuItem() {
        if (!this::menuBinding.isInitialized) {
            return
        }
        menuBinding.saveItem.isEnabled = viewModel.writeFileState.value.isReady
    }

    private fun updateFormatMenuItems() {
        if (!this::menuBinding.isInitialized) {
            return
        }
        val canFormat = viewModel.textState.value is DataState.Success && !isFormatting
        menuBinding.formatItem.isVisible =
            detectedLanguage == SyntaxLanguage.JSON || detectedLanguage == SyntaxLanguage.YAML
        menuBinding.formatItem.isEnabled = canFormat
        menuBinding.formatJsonItem.isEnabled = canFormat
        menuBinding.formatYamlItem.isEnabled = canFormat
    }

    @Parcelize
    class Args(val intent: Intent) : ParcelableArgs

    private class MenuBinding private constructor(
        val menu: Menu,
        val formatItem: MenuItem,
        val saveItem: MenuItem,
        val formatJsonItem: MenuItem,
        val formatYamlItem: MenuItem,
        val showLineNumbersItem: MenuItem,
        val encodingSubMenu: SubMenu
    ) {
        companion object {
            fun inflate(menu: Menu, inflater: MenuInflater): MenuBinding {
                inflater.inflate(R.menu.text_editor, menu)
                val encodingSubMenu = menu.findItem(R.id.action_encoding).subMenu!!
                for ((charsetName, charset) in Charset.availableCharsets()) {
                    // HACK: Use titleCondensed to store charset name.
                    encodingSubMenu.add(Menu.NONE, Menu.FIRST, Menu.NONE, charset.displayName())
                        .titleCondensed = charsetName
                }
                encodingSubMenu.setGroupCheckable(Menu.NONE, true, true)
                return MenuBinding(
                    menu,
                    menu.findItem(R.id.action_format),
                    menu.findItem(R.id.action_save),
                    menu.findItem(R.id.action_format_json),
                    menu.findItem(R.id.action_format_yaml),
                    menu.findItem(R.id.action_show_line_numbers),
                    encodingSubMenu
                )
            }
        }
    }

    companion object {
        private const val HIGHLIGHT_DEBOUNCE_MILLIS = 150L
    }
}

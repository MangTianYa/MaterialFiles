/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filelist

import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.getSystemService
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.interpolator.view.animation.FastOutLinearInInterpolator
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.leinardi.android.speeddial.SpeedDialView
import java8.nio.file.Path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.parcelize.Parcelize
import me.zhanghai.android.files.R
import me.zhanghai.android.files.app.clipboardManager
import me.zhanghai.android.files.compat.setGroupDividerEnabledCompat
import me.zhanghai.android.files.databinding.DualPaneFileListFragmentBinding
import me.zhanghai.android.files.databinding.DualPanePathActionDialogBinding
import me.zhanghai.android.files.databinding.FileListFragmentSpeedDialIncludeBinding
import me.zhanghai.android.files.file.MimeType
import me.zhanghai.android.files.file.asMimeTypeOrNull
import me.zhanghai.android.files.file.guessFromPath
import me.zhanghai.android.files.filejob.FileJobService
import me.zhanghai.android.files.navigation.NavigationFragment
import me.zhanghai.android.files.navigation.NavigationItemListLiveData
import me.zhanghai.android.files.navigation.NavigationRootMapLiveData
import me.zhanghai.android.files.provider.archive.isArchivePath
import me.zhanghai.android.files.provider.common.exists
import me.zhanghai.android.files.provider.linux.isLinuxPath
import me.zhanghai.android.files.settings.SettingLiveData
import me.zhanghai.android.files.settings.Settings
import me.zhanghai.android.files.ui.DrawerLayoutOnBackPressedCallback
import me.zhanghai.android.files.ui.SpeedDialViewOnBackPressedCallback
import me.zhanghai.android.files.util.DebouncedRunnable
import me.zhanghai.android.files.util.ParcelableArgs
import me.zhanghai.android.files.util.args
import me.zhanghai.android.files.util.copyText
import me.zhanghai.android.files.util.createDirectViewIntent
import me.zhanghai.android.files.util.dpToDimension
import me.zhanghai.android.files.util.dpToDimensionPixelSize
import me.zhanghai.android.files.util.extraPath
import me.zhanghai.android.files.util.getColorByAttr
import me.zhanghai.android.files.util.hideTextInputLayoutErrorOnTextChange
import me.zhanghai.android.files.util.layoutInflater
import me.zhanghai.android.files.util.putArgs
import me.zhanghai.android.files.util.setOnEditorConfirmActionListener
import me.zhanghai.android.files.util.showToast
import me.zhanghai.android.files.util.startActivitySafe
import me.zhanghai.android.files.util.valueCompat
import me.zhanghai.android.files.viewer.text.TextEditorActivity

class DualPaneFileListFragment : Fragment(), FileListFragment.DualPaneHost,
    NavigationFragment.Listener {
    private val args by args<Args>()

    private lateinit var binding: DualPaneFileListFragmentBinding
    private lateinit var speedDialView: SpeedDialView

    private lateinit var navigationFragment: NavigationFragment
    private lateinit var leftFragment: FileListFragment
    private lateinit var rightFragment: FileListFragment

    private enum class TopBarMode {
        NORMAL,
        SEARCH,
        SELECTION
    }

    private enum class RecentHistoryFilter {
        ALL,
        FILES,
        FOLDERS
    }

    private var activePaneIndex = 0
    private var splitRatio = 0.5f
    private var isSearchUiVisible = false
    private var ignoreSearchTextChange = false
    private var isActionPanelVisible = false
    private var isRecentHistoryVisible = false
    private var recentHistoryFilter = RecentHistoryFilter.ALL
    private var currentTopBarMode = TopBarMode.NORMAL

    private val activePathLiveData = MutableLiveData<Path>()

    private lateinit var paneBackCallback: OnBackPressedCallback
    private lateinit var recentHistoryAdapter: RecentHistoryAdapter

    private val activeFragment: FileListFragment
        get() = if (activePaneIndex == 0) leftFragment else rightFragment

    private val inactiveFragment: FileListFragment
        get() = if (activePaneIndex == 0) rightFragment else leftFragment

    private val debouncedSearchRunnable = DebouncedRunnable(Handler(Looper.getMainLooper()), 400) {
        if (!isResumed || !isSearchUiVisible) {
            return@DebouncedRunnable
        }
        val query = binding.searchEditText.text?.toString().orEmpty()
        if (query.isEmpty()) {
            activeFragment.triggerStopSearch()
        } else {
            activeFragment.triggerSearch(query)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View =
        DualPaneFileListFragmentBinding.inflate(inflater, container, false)
            .also {
                binding = it
                speedDialView = FileListFragmentSpeedDialIncludeBinding.bind(it.root).speedDialView
            }
            .root

    @SuppressLint("ClickableViewAccessibility")
    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        if (savedInstanceState == null) {
            val hasExplicitTarget = args.intent.extraPath != null ||
                args.intent.action == ACTION_VIEW_DOWNLOADS
            activePaneIndex = if (hasExplicitTarget) {
                0
            } else {
                Settings.DUAL_PANE_ACTIVE_INDEX.valueCompat.coerceIn(0, 1)
            }
            navigationFragment = NavigationFragment()
            val leftIntent = if (hasExplicitTarget) {
                args.intent
            } else {
                createRestoredPaneIntent(Settings.FILE_LIST_LEFT_PANE_DIRECTORY, args.intent)
            }
            leftFragment = FileListFragment().putArgs(
                FileListFragment.Args(leftIntent, isDualPaneChild = true, paneIndex = 0)
            )
            @Suppress("DEPRECATION")
            val downloadsDir =
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val defaultRightIntent = if (downloadsDir.isDirectory) {
                Intent(ACTION_VIEW_DOWNLOADS)
            } else {
                Intent()
            }
            val rightIntent = createRestoredPaneIntent(
                Settings.FILE_LIST_RIGHT_PANE_DIRECTORY,
                defaultRightIntent
            )
            rightFragment = FileListFragment().putArgs(
                FileListFragment.Args(rightIntent, isDualPaneChild = true, paneIndex = 1)
            )
            childFragmentManager.commit {
                add(R.id.navigationFragment, navigationFragment)
                add(R.id.leftPaneContainer, leftFragment)
                add(R.id.rightPaneContainer, rightFragment)
            }
        } else {
            activePaneIndex = savedInstanceState.getInt(STATE_ACTIVE_PANE_INDEX, 0)
            splitRatio = savedInstanceState.getFloat(STATE_SPLIT_RATIO, 0.5f)
            isSearchUiVisible = savedInstanceState.getBoolean(STATE_SEARCH_UI_VISIBLE, false)
            isActionPanelVisible = savedInstanceState.getBoolean(STATE_ACTION_PANEL_VISIBLE, false)
            isRecentHistoryVisible = savedInstanceState.getBoolean(STATE_RECENT_HISTORY_VISIBLE, false)
            navigationFragment = childFragmentManager.findFragmentById(R.id.navigationFragment)
                as NavigationFragment
            leftFragment = childFragmentManager.findFragmentById(R.id.leftPaneContainer)
                as FileListFragment
            rightFragment = childFragmentManager.findFragmentById(R.id.rightPaneContainer)
                as FileListFragment
        }
        navigationFragment.listener = this

        setupWindowInsets()
        setupDividerResize()
        setupTopBars()
        setupFloatingDocks()
        setupSpeedDial()
        setupRecentHistorySheet()
        setupBackCallbacks()

        applySplitRatio()
        if (isSearchUiVisible) {
            transitionTopBarTo(TopBarMode.SEARCH, animate = false, force = true)
            updateClearSearchButton(animate = false)
        } else {
            transitionTopBarTo(TopBarMode.NORMAL, animate = false, force = true)
        }
        if (isRecentHistoryVisible) {
            openRecentHistorySheet(animate = false)
        }
        updateActivePaneUi()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_ACTIVE_PANE_INDEX, activePaneIndex)
        outState.putFloat(STATE_SPLIT_RATIO, splitRatio)
        outState.putBoolean(STATE_SEARCH_UI_VISIBLE, isSearchUiVisible)
        outState.putBoolean(STATE_ACTION_PANEL_VISIBLE, isActionPanelVisible)
        outState.putBoolean(STATE_RECENT_HISTORY_VISIBLE, isRecentHistoryVisible)
        persistPaneState()
    }

    override fun onStop() {
        persistPaneState()
        super.onStop()
    }

    override fun onDestroyView() {
        debouncedSearchRunnable.cancel()
        binding.normalTopBar.animate().cancel()
        binding.searchTopBar.animate().cancel()
        binding.selectionTopBar.animate().cancel()
        binding.clearSearchButton.animate().cancel()
        binding.recentHistoryOverlay.animate().cancel()
        binding.recentHistorySheet.animate().cancel()
        binding.selectionActionTriggerPill.animate().cancel()
        binding.recentBottomBar.animate().cancel()
        super.onDestroyView()
    }

    private fun setupWindowInsets() {
        val mainInitialLeft = binding.mainContentRoot.paddingLeft
        val mainInitialTop = binding.mainContentRoot.paddingTop
        val mainInitialRight = binding.mainContentRoot.paddingRight
        val overlayInitialLeft = binding.floatingActionOverlay.paddingLeft
        val overlayInitialTop = binding.floatingActionOverlay.paddingTop
        val overlayInitialRight = binding.floatingActionOverlay.paddingRight
        val overlayInitialBottom = binding.floatingActionOverlay.paddingBottom
        val pasteDockInitialMarginBottom =
            (binding.floatingPasteDock.layoutParams as ViewGroup.MarginLayoutParams).bottomMargin
        val recentBottomBarInitialMarginBottom =
            (binding.recentBottomBar.layoutParams as ViewGroup.MarginLayoutParams).bottomMargin
        val selectionPillInitialMarginBottom =
            (binding.selectionActionTriggerPill.layoutParams as ViewGroup.MarginLayoutParams)
                .bottomMargin
        val recentSheetInitialPaddingBottom = binding.recentHistorySheet.paddingBottom
        val speedDialContainer = speedDialView.parent as View
        val speedDialInitialMarginBottom =
            (speedDialContainer.layoutParams as ViewGroup.MarginLayoutParams).bottomMargin
        val speedDialOverlay =
            binding.mainContentRoot.findViewById<View?>(R.id.speedDialOverlayLayout)

        // Prevent fitsSystemWindows="true" in shared speed-dial include from double-offsetting.
        ViewCompat.setOnApplyWindowInsetsListener(speedDialContainer) { _, insets -> insets }
        speedDialOverlay?.let { overlay ->
            ViewCompat.setOnApplyWindowInsetsListener(overlay) { _, insets -> insets }
        }

        val maxCardWidthPx = requireContext().dpToDimensionPixelSize(380)
        ViewCompat.setOnApplyWindowInsetsListener(binding.mainContentRoot) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            view.updatePadding(
                left = mainInitialLeft + bars.left,
                top = mainInitialTop + bars.top,
                right = mainInitialRight + bars.right
            )
            binding.mainContentRoot.bottomTriggerHeightPx =
                requireContext().dpToDimension(56f) + bars.bottom
            binding.floatingActionOverlay.updatePadding(
                left = overlayInitialLeft,
                top = overlayInitialTop,
                right = overlayInitialRight,
                bottom = overlayInitialBottom + bars.bottom
            )
            val containerWidth = view.width - view.paddingLeft - view.paddingRight -
                overlayInitialLeft - overlayInitialRight
            if (containerWidth > 0) {
                binding.floatingActionDockScroll.updateLayoutParams<ViewGroup.LayoutParams> {
                    width = containerWidth.coerceAtMost(maxCardWidthPx)
                }
            }
            binding.floatingPasteDock.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = pasteDockInitialMarginBottom + bars.bottom
            }
            binding.recentBottomBar.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = recentBottomBarInitialMarginBottom + bars.bottom
            }
            binding.selectionActionTriggerPill.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = selectionPillInitialMarginBottom + bars.bottom
            }
            binding.recentHistorySheet.updatePadding(
                bottom = recentSheetInitialPaddingBottom + bars.bottom
            )
            speedDialContainer.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = speedDialInitialMarginBottom + bars.bottom
            }
            insets
        }
        binding.mainContentRoot.addOnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
            val containerWidth = view.width - view.paddingLeft - view.paddingRight -
                binding.floatingActionOverlay.paddingLeft - binding.floatingActionOverlay.paddingRight
            if (containerWidth > 0) {
                val targetWidth = containerWidth.coerceAtMost(maxCardWidthPx)
                val lp = binding.floatingActionDockScroll.layoutParams
                if (lp.width != targetWidth) {
                    lp.width = targetWidth
                    binding.floatingActionDockScroll.layoutParams = lp
                }
            }
        }
        ViewCompat.requestApplyInsets(binding.mainContentRoot)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupDividerResize() {
        val doubleTapDetector = GestureDetector(
            requireContext(),
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onDoubleTap(e: MotionEvent): Boolean {
                    splitRatio = 0.5f
                    applySplitRatio()
                    return true
                }
            }
        )
        binding.dividerHandleContainer.setOnTouchListener { _, event ->
            doubleTapDetector.onTouchEvent(event)
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    val width = binding.dualPaneContainer.width
                    if (width > 0) {
                        val loc = IntArray(2)
                        binding.dualPaneContainer.getLocationOnScreen(loc)
                        val relX = event.rawX - loc[0]
                        splitRatio = (relX / width.toFloat()).coerceIn(0.25f, 0.75f)
                        applySplitRatio()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun applySplitRatio() {
        binding.leftPaneWrapper.updateLayoutParams<LinearLayout.LayoutParams> {
            weight = splitRatio
        }
        binding.rightPaneWrapper.updateLayoutParams<LinearLayout.LayoutParams> {
            weight = 1f - splitRatio
        }
        if (this::leftFragment.isInitialized && this::rightFragment.isInitialized) {
            leftFragment.triggerUpdateSpanCount()
            rightFragment.triggerUpdateSpanCount()
        }
    }

    private fun setupTopBars() {
        binding.menuButton.setOnClickListener {
            NavigationItemListLiveData.refreshStorageStats()
            binding.drawerLayout.openDrawer(GravityCompat.START)
        }
        binding.toolbarPathContainer.setOnClickListener { showPathActionsDialog() }
        binding.toolbarPathContainer.setOnLongClickListener {
            val active = activeFragment
            if (active.hasCurrentPath) {
                active.triggerCopyCurrentPath()
                true
            } else {
                false
            }
        }
        binding.searchButton.setOnClickListener { openSearchUi() }
        binding.closeSearchButton.setOnClickListener { closeSearchUi(clearQuery = true) }
        binding.clearSearchButton.setOnClickListener {
            val hasText = !binding.searchEditText.text.isNullOrEmpty()
            if (hasText) {
                debouncedSearchRunnable.cancel()
                ignoreSearchTextChange = true
                binding.searchEditText.setText("")
                ignoreSearchTextChange = false
                updateClearSearchButton(animate = true)
                activeFragment.triggerStopSearch()
                binding.searchEditText.requestFocus()
            } else {
                closeSearchUi(clearQuery = true)
            }
        }
        binding.searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (!ignoreSearchTextChange) {
                    debouncedSearchRunnable()
                }
            }
            override fun afterTextChanged(s: Editable?) {
                updateClearSearchButton(animate = !ignoreSearchTextChange)
            }
        })
        binding.searchEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                debouncedSearchRunnable.cancel()
                val query = binding.searchEditText.text?.toString().orEmpty()
                if (query.isNotEmpty()) {
                    activeFragment.triggerSearch(query)
                }
                hideKeyboard()
                true
            } else {
                false
            }
        }

        binding.sortButton.setOnClickListener { showSortPopup(it) }
        binding.overflowButton.setOnClickListener { showOverflowPopup(it) }

        binding.closeSelectionButton.setOnClickListener { activeFragment.triggerClearSelection() }
        binding.selectAllButton.setOnClickListener { activeFragment.triggerSelectAll() }
        binding.selectionMoreButton.setOnClickListener { showSelectionMorePopup(it) }
    }

    private fun setupFloatingDocks() {
        binding.floatingActionOverlay.setOnClickListener {
            isActionPanelVisible = false
            updateSelectionAndDockUi()
            updateBackCallbackEnabled()
        }
        binding.dockClosePanelButton.setOnClickListener {
            isActionPanelVisible = false
            updateSelectionAndDockUi()
            updateBackCallbackEnabled()
        }
        binding.selectionActionTriggerPill.setOnClickListener {
            isActionPanelVisible = true
            updateSelectionAndDockUi()
            updateBackCallbackEnabled()
        }
        binding.dockCopyTo.setOnClickListener {
            if (canCopyOrMoveToOppositePane()) {
                confirmCopySelectionToOppositePane()
            } else {
                isActionPanelVisible = false
                activeFragment.triggerCopySelectedToClipboard()
            }
        }
        binding.dockMoveTo.setOnClickListener {
            if (canCopyOrMoveToOppositePane()) {
                confirmMoveSelectionToOppositePane()
            } else {
                isActionPanelVisible = false
                activeFragment.triggerCutSelectedToClipboard()
            }
        }
        binding.dockLinkTo.setOnClickListener { confirmLinkSelectionToOppositePane() }
        binding.dockRename.setOnClickListener {
            isActionPanelVisible = false
            updateSelectionAndDockUi()
            updateBackCallbackEnabled()
            activeFragment.triggerRenameSelected()
        }
        binding.dockDelete.setOnClickListener { activeFragment.triggerDeleteSelected() }
        binding.dockCompress.setOnClickListener { activeFragment.triggerArchiveSelected() }
        binding.dockProperties.setOnClickListener {
            isActionPanelVisible = false
            updateSelectionAndDockUi()
            updateBackCallbackEnabled()
            activeFragment.triggerShowPropertiesSelected()
        }
        binding.dockShare.setOnClickListener { confirmShareSelection() }
        binding.dockOpenWith.setOnClickListener { confirmOpenSelectedWith() }
        binding.dockAddBookmark.setOnClickListener { confirmAddBookmarkSelection() }

        binding.pasteConfirmButton.setOnClickListener { activeFragment.triggerPasteHere() }
        binding.pasteCancelButton.setOnClickListener { activeFragment.triggerClearPaste() }
    }

    private fun setupSpeedDial() {
        speedDialView.inflate(R.menu.file_list_speed_dial)
        speedDialView.setOnActionSelectedListener {
            when (it.id) {
                R.id.action_create_file -> activeFragment.triggerCreateFile()
                R.id.action_create_directory -> activeFragment.triggerCreateDirectory()
            }
            speedDialView.close()
            true
        }
    }

    private fun setupBackCallbacks() {
        paneBackCallback = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                when {
                    isRecentHistoryVisible -> closeRecentHistorySheet(animate = true)
                    isSearchUiVisible -> closeSearchUi(clearQuery = true)
                    activeFragment.selectedFilesValue.isNotEmpty() -> {
                        if (isActionPanelVisible) {
                            isActionPanelVisible = false
                            updateSelectionAndDockUi()
                            updateBackCallbackEnabled()
                        } else {
                            activeFragment.triggerClearSelection()
                        }
                    }
                    activeFragment.canNavigateUpBreadcrumbValue ->
                        activeFragment.triggerNavigateUp()
                }
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, paneBackCallback)
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner, SpeedDialViewOnBackPressedCallback(speedDialView)
        )
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner, DrawerLayoutOnBackPressedCallback(binding.drawerLayout)
        )
    }

    private fun updateBackCallbackEnabled() {
        if (!this::paneBackCallback.isInitialized || !this::leftFragment.isInitialized ||
            !this::rightFragment.isInitialized
        ) {
            return
        }
        val active = activeFragment
        paneBackCallback.isEnabled = isRecentHistoryVisible ||
            isSearchUiVisible ||
            active.selectedFilesValue.isNotEmpty() ||
            active.canNavigateUpBreadcrumbValue
    }

    private fun setActivePane(paneIndex: Int) {
        if (activePaneIndex == paneIndex) {
            return
        }
        val previous = activeFragment
        if (isSearchUiVisible) {
            closeSearchUi(clearQuery = true)
        }
        if (previous.selectedFilesValue.isNotEmpty()) {
            previous.triggerClearSelection()
        }
        activePaneIndex = paneIndex
        if (Settings.DUAL_PANE_ACTIVE_INDEX.valueCompat != paneIndex) {
            Settings.DUAL_PANE_ACTIVE_INDEX.putValue(paneIndex)
        }
        val current = activeFragment
        if (current.hasCurrentPath) {
            persistPanePath(paneIndex, current.currentPath)
        }
        updateActivePaneUi()
    }

    private fun updateActivePaneUi() {
        val isLeftActive = activePaneIndex == 0
        val context = requireContext()
        val activeColor = context.getColorByAttr(androidx.appcompat.R.attr.colorPrimary)
        val inactiveColor =
            context.getColorByAttr(com.google.android.material.R.attr.colorOutlineVariant)
        binding.leftPaneActiveIndicator.setBackgroundColor(
            if (isLeftActive) activeColor else inactiveColor
        )
        binding.leftPaneActiveIndicator.alpha = if (isLeftActive) 1f else 0.25f
        binding.rightPaneActiveIndicator.setBackgroundColor(
            if (!isLeftActive) activeColor else inactiveColor
        )
        binding.rightPaneActiveIndicator.alpha = if (!isLeftActive) 1f else 0.25f

        if (this::leftFragment.isInitialized && this::rightFragment.isInitialized) {
            val active = activeFragment
            if (active.hasCurrentPath) {
                binding.toolbarTitleText.text = active.paneTitleValue
                binding.toolbarSubtitleText.text = active.paneSubtitleValue
                activePathLiveData.value = active.currentPath
                updateSelectionAndDockUi()
                updateBackCallbackEnabled()
            } else {
                isActionPanelVisible = false
                binding.floatingActionOverlay.isVisible = false
                binding.selectionActionTriggerPill.isVisible = false
                binding.floatingPasteDock.isVisible = false
                binding.recentBottomBar.isVisible = !isRecentHistoryVisible
                speedDialView.isVisible = true
                transitionTopBarTo(TopBarMode.NORMAL, animate = false)
                if (this::paneBackCallback.isInitialized) {
                    paneBackCallback.isEnabled = isRecentHistoryVisible
                }
            }
        }
    }

    private fun setDockButtonEnabled(view: View, enabled: Boolean) {
        view.isEnabled = enabled
        view.alpha = if (enabled) 1f else 0.38f
    }

    private fun updateSelectionAndDockUi(animateTopBar: Boolean = false) {
        val active = activeFragment
        val selectedFiles = active.selectedFilesValue
        val hasSelection = selectedFiles.isNotEmpty()

        val targetTopBarMode = when {
            hasSelection -> TopBarMode.SELECTION
            isSearchUiVisible -> TopBarMode.SEARCH
            else -> TopBarMode.NORMAL
        }
        transitionTopBarTo(targetTopBarMode, animate = animateTopBar)
        speedDialView.isVisible = !hasSelection

        if (hasSelection) {
            val selectTitle =
                getString(R.string.file_list_select_title_format, selectedFiles.size)
            binding.selectionTitleText.text = selectTitle
            binding.dockHeaderCountText.text = selectTitle
            val isRightPaneActive = activePaneIndex == 1
            val isAnyReadOnly = selectedFiles.any { it.path.fileSystem.isReadOnly }
            val isCurrentReadOnly = active.currentPath.fileSystem.isReadOnly
            val oppositeHasPath = inactiveFragment.hasCurrentPath
            val oppositePath = if (oppositeHasPath) inactiveFragment.currentPath else null
            val canCopyOrMoveTarget = oppositePath != null &&
                !oppositePath.fileSystem.isReadOnly &&
                oppositePath != active.currentPath
            val canLinkTarget = canCopyOrMoveTarget &&
                oppositePath.isLinuxPath &&
                selectedFiles.all { it.path.isLinuxPath }
            binding.dockCopyToText.setText(
                when {
                    canCopyOrMoveTarget && isRightPaneActive -> R.string.dual_pane_copy_to_left
                    canCopyOrMoveTarget -> R.string.dual_pane_copy_to
                    else -> R.string.copy
                }
            )
            binding.dockMoveToText.setText(
                when {
                    canCopyOrMoveTarget && isRightPaneActive -> R.string.dual_pane_move_to_left
                    canCopyOrMoveTarget -> R.string.dual_pane_move_to
                    else -> R.string.dual_pane_move_clipboard
                }
            )
            binding.dockLinkToText.setText(
                if (isRightPaneActive) R.string.dual_pane_link_to_left else R.string.dual_pane_link_to
            )
            val singleSelected = selectedFiles.singleOrNull()
            val canShare = selectedFiles.none { it.attributes.isDirectory }
            val canOpenWith = singleSelected != null && !singleSelected.attributes.isDirectory
            val canAddBookmark = singleSelected != null && singleSelected.attributes.isDirectory

            setDockButtonEnabled(binding.dockCopyTo, true)
            setDockButtonEnabled(binding.dockMoveTo, !isAnyReadOnly)
            setDockButtonEnabled(binding.dockLinkTo, canLinkTarget)
            setDockButtonEnabled(binding.dockRename, !isAnyReadOnly)
            setDockButtonEnabled(binding.dockDelete, !isAnyReadOnly)
            setDockButtonEnabled(binding.dockCompress, !isCurrentReadOnly)
            setDockButtonEnabled(binding.dockProperties, true)
            setDockButtonEnabled(binding.dockShare, canShare)
            setDockButtonEnabled(binding.dockOpenWith, canOpenWith)
            setDockButtonEnabled(binding.dockAddBookmark, canAddBookmark)
        } else {
            isActionPanelVisible = false
        }

        binding.floatingActionOverlay.isVisible = hasSelection && isActionPanelVisible
        binding.selectionActionTriggerPill.isVisible = hasSelection && !isActionPanelVisible
        if (hasSelection && !isActionPanelVisible) {
            binding.selectionActionPillText.text =
                getString(R.string.selection_actions_pill_format, selectedFiles.size)
        }

        val pasteFiles = active.pasteStateValue.files
        val showPasteDock = !hasSelection && pasteFiles.isNotEmpty()
        binding.floatingPasteDock.isVisible = showPasteDock
        binding.recentBottomBar.isVisible = !hasSelection && !showPasteDock && !isRecentHistoryVisible
        if (showPasteDock) {
            val pasteState = active.pasteStateValue
            val areAllArchivePaths = pasteFiles.all { it.path.isArchivePath }
            binding.pasteStatusText.text = getString(
                if (pasteState.copy) {
                    if (areAllArchivePaths) {
                        R.string.file_list_paste_extract_title_format
                    } else {
                        R.string.file_list_paste_copy_title_format
                    }
                } else {
                    R.string.file_list_paste_move_title_format
                },
                pasteFiles.size
            )
            binding.pasteConfirmButton.isEnabled = !active.currentPath.fileSystem.isReadOnly
        }
    }

    private fun showActionConfirmDialog(
        titleRes: Int,
        message: CharSequence,
        onConfirm: () -> Unit
    ) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(titleRes)
            .setMessage(message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.dual_pane_action_confirm) { _, _ -> onConfirm() }
            .show()
    }

    private fun canCopyOrMoveToOppositePane(): Boolean {
        if (!inactiveFragment.hasCurrentPath || !activeFragment.hasCurrentPath) return false
        val targetPath = inactiveFragment.currentPath
        return !targetPath.fileSystem.isReadOnly && targetPath != activeFragment.currentPath
    }

    private fun prepareOppositePaneTarget(): Pair<FileItemSet, Path>? {
        val selected = activeFragment.selectedFilesValue
        if (selected.isEmpty() || !inactiveFragment.hasCurrentPath) return null
        val targetPath = inactiveFragment.currentPath
        if (targetPath.fileSystem.isReadOnly) {
            showToast(R.string.dual_pane_target_read_only_error)
            return null
        }
        if (targetPath == activeFragment.currentPath) {
            showToast(R.string.dual_pane_same_directory_error)
            return null
        }
        return selected to targetPath
    }

    private fun getPathDisplayName(path: Path): String =
        NavigationRootMapLiveData.valueCompat[path]?.getName(requireContext()) ?: path.name

    private fun confirmCopySelectionToOppositePane() {
        val (selected, targetPath) = prepareOppositePaneTarget() ?: return
        val targetName = getPathDisplayName(targetPath)
        val titleRes = if (activePaneIndex == 1) {
            R.string.dual_pane_copy_to_left
        } else {
            R.string.dual_pane_copy_to
        }
        showActionConfirmDialog(
            titleRes,
            getString(R.string.dual_pane_confirm_copy_message_format, selected.size, targetName)
        ) {
            showToast(getString(R.string.dual_pane_copy_started, selected.size, targetName))
            activeFragment.triggerCopyToDirectory(targetPath)
        }
    }

    private fun confirmMoveSelectionToOppositePane() {
        val (selected, targetPath) = prepareOppositePaneTarget() ?: return
        val targetName = getPathDisplayName(targetPath)
        val titleRes = if (activePaneIndex == 1) {
            R.string.dual_pane_move_to_left
        } else {
            R.string.dual_pane_move_to
        }
        showActionConfirmDialog(
            titleRes,
            getString(R.string.dual_pane_confirm_move_message_format, selected.size, targetName)
        ) {
            showToast(getString(R.string.dual_pane_move_started, selected.size, targetName))
            activeFragment.triggerMoveToDirectory(targetPath)
        }
    }

    private fun confirmLinkSelectionToOppositePane() {
        val (selected, targetPath) = prepareOppositePaneTarget() ?: return
        if (!targetPath.isLinuxPath || selected.any { !it.path.isLinuxPath }) {
            return
        }
        val targetName = getPathDisplayName(targetPath)
        val titleRes = if (activePaneIndex == 1) {
            R.string.dual_pane_link_to_left
        } else {
            R.string.dual_pane_link_to
        }
        showActionConfirmDialog(
            titleRes,
            getString(R.string.dual_pane_confirm_link_message_format, selected.size, targetName)
        ) {
            showToast(getString(R.string.dual_pane_link_started_format, targetName))
            activeFragment.triggerCreateSymbolicLinkToDirectory(targetPath)
        }
    }

    private fun confirmShareSelection() {
        val selected = activeFragment.selectedFilesValue
        if (selected.isEmpty()) return
        showActionConfirmDialog(
            R.string.share,
            getString(R.string.dual_pane_confirm_share_message_format, selected.size)
        ) {
            activeFragment.triggerShareSelected()
        }
    }

    private fun confirmOpenSelectedWith() {
        val single = activeFragment.selectedFilesValue.singleOrNull() ?: return
        showActionConfirmDialog(
            R.string.dual_pane_open_with_ellipsis,
            getString(R.string.dual_pane_confirm_open_with_message_format, single.name)
        ) {
            activeFragment.triggerOpenSelectedWith()
        }
    }

    private fun confirmAddBookmarkSelection() {
        val single = activeFragment.selectedFilesValue.singleOrNull() ?: return
        showActionConfirmDialog(
            R.string.file_list_action_add_bookmark,
            getString(R.string.dual_pane_confirm_add_bookmark_message_format, single.name)
        ) {
            activeFragment.triggerAddBookmarkSelected()
        }
    }

    private fun transitionTopBarTo(
        mode: TopBarMode,
        animate: Boolean,
        force: Boolean = false
    ) {
        if (!force && mode == currentTopBarMode) {
            return
        }
        val previousMode = currentTopBarMode
        currentTopBarMode = mode

        binding.normalTopBar.animate().cancel()
        binding.searchTopBar.animate().cancel()
        binding.selectionTopBar.animate().cancel()

        val shouldAnimateSearchTransition = animate &&
            view != null &&
            ((previousMode == TopBarMode.NORMAL && mode == TopBarMode.SEARCH) ||
                (previousMode == TopBarMode.SEARCH && mode == TopBarMode.NORMAL))

        if (!shouldAnimateSearchTransition) {
            binding.normalTopBar.apply {
                resetTopBarVisualState(this)
                isVisible = mode == TopBarMode.NORMAL
            }
            binding.searchTopBar.apply {
                resetTopBarVisualState(this)
                isVisible = mode == TopBarMode.SEARCH
            }
            binding.selectionTopBar.apply {
                resetTopBarVisualState(this)
                isVisible = mode == TopBarMode.SELECTION
            }
            return
        }

        binding.selectionTopBar.apply {
            resetTopBarVisualState(this)
            isVisible = false
        }

        val slidePx = requireContext().dpToDimensionPixelSize(16).toFloat()
        if (mode == TopBarMode.SEARCH) {
            binding.normalTopBar.isVisible = true
            binding.normalTopBar.animate()
                .alpha(0f)
                .translationX(-slidePx)
                .setDuration(140L)
                .setInterpolator(FastOutLinearInInterpolator())
                .withEndAction {
                    if (isAdded && view != null && currentTopBarMode != TopBarMode.NORMAL) {
                        binding.normalTopBar.isVisible = false
                        resetTopBarVisualState(binding.normalTopBar)
                    }
                }
                .start()

            binding.searchTopBar.apply {
                isVisible = true
                alpha = 0f
                translationX = slidePx * 1.5f
                animate()
                    .alpha(1f)
                    .translationX(0f)
                    .setDuration(180L)
                    .setInterpolator(FastOutSlowInInterpolator())
                    .start()
            }
        } else {
            binding.searchTopBar.isVisible = true
            binding.searchTopBar.animate()
                .alpha(0f)
                .translationX(slidePx * 1.5f)
                .setDuration(160L)
                .setInterpolator(FastOutLinearInInterpolator())
                .withEndAction {
                    if (isAdded && view != null && currentTopBarMode != TopBarMode.SEARCH) {
                        binding.searchTopBar.isVisible = false
                        resetTopBarVisualState(binding.searchTopBar)
                    }
                }
                .start()

            binding.normalTopBar.apply {
                isVisible = true
                alpha = 0f
                translationX = -slidePx
                animate()
                    .alpha(1f)
                    .translationX(0f)
                    .setDuration(180L)
                    .setInterpolator(FastOutSlowInInterpolator())
                    .start()
            }
        }
    }

    private fun resetTopBarVisualState(view: View) {
        view.alpha = 1f
        view.translationX = 0f
    }

    private fun updateClearSearchButton(animate: Boolean) {
        val shouldShow = !binding.searchEditText.text.isNullOrEmpty()
        val button = binding.clearSearchButton
        button.animate().cancel()
        if (!animate || !button.isLaidOut) {
            button.isVisible = shouldShow
            button.alpha = 1f
            button.scaleX = 1f
            button.scaleY = 1f
            return
        }
        if (shouldShow) {
            if (!button.isVisible) {
                button.isVisible = true
                button.alpha = 0f
                button.scaleX = 0.75f
                button.scaleY = 0.75f
            }
            button.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(140L)
                .setInterpolator(FastOutSlowInInterpolator())
                .start()
        } else if (button.isVisible) {
            button.animate()
                .alpha(0f)
                .scaleX(0.75f)
                .scaleY(0.75f)
                .setDuration(120L)
                .setInterpolator(FastOutLinearInInterpolator())
                .withEndAction {
                    if (isAdded && view != null && binding.searchEditText.text.isNullOrEmpty()) {
                        button.isVisible = false
                        button.alpha = 1f
                        button.scaleX = 1f
                        button.scaleY = 1f
                    }
                }
                .start()
        }
    }

    private fun openSearchUi() {
        val active = activeFragment
        if (!active.hasCurrentPath) return
        isSearchUiVisible = true
        val query = active.searchQueryValue
        ignoreSearchTextChange = true
        binding.searchEditText.setText(query)
        binding.searchEditText.setSelection(query.length)
        ignoreSearchTextChange = false
        updateClearSearchButton(animate = false)
        updateSelectionAndDockUi(animateTopBar = true)
        updateBackCallbackEnabled()
        binding.searchEditText.post {
            if (isAdded && view != null && isSearchUiVisible) {
                binding.searchEditText.requestFocus()
                requireContext().getSystemService<InputMethodManager>()
                    ?.showSoftInput(binding.searchEditText, InputMethodManager.SHOW_IMPLICIT)
            }
        }
    }

    private fun closeSearchUi(clearQuery: Boolean) {
        debouncedSearchRunnable.cancel()
        isSearchUiVisible = false
        if (clearQuery) {
            ignoreSearchTextChange = true
            binding.searchEditText.setText("")
            ignoreSearchTextChange = false
            updateClearSearchButton(animate = false)
            activeFragment.triggerStopSearch()
        }
        binding.searchEditText.clearFocus()
        hideKeyboard()
        updateSelectionAndDockUi(animateTopBar = true)
        updateBackCallbackEnabled()
    }

    private fun showPathActionsDialog() {
        val active = activeFragment
        if (!active.hasCurrentPath) {
            return
        }
        val currentPath = active.currentPath
        val context = requireContext()
        val dialogBinding = DualPanePathActionDialogBinding.inflate(context.layoutInflater)
        val initialText = currentPath.toUserFriendlyString()
        dialogBinding.pathEdit.setText(initialText)
        dialogBinding.pathEdit.setSelection(0, initialText.length)
        dialogBinding.pathEdit.hideTextInputLayoutErrorOnTextChange(dialogBinding.pathLayout)

        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(R.string.dual_pane_path_dialog_title)
            .setView(dialogBinding.root)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.dual_pane_path_jump, null)
            .create()

        fun performJump() {
            val input = dialogBinding.pathEdit.text?.toString()?.trim().orEmpty()
            if (input.isEmpty()) {
                dialogBinding.pathLayout.error = getString(R.string.file_list_path_error_empty)
                return
            }
            val parsedPath = input.toNavigablePathOrNull()
            if (parsedPath == null) {
                dialogBinding.pathLayout.error = getString(R.string.file_list_path_error_invalid)
                return
            }
            dialog.dismiss()
            if (parsedPath != currentPath) {
                active.triggerNavigateTo(parsedPath)
            }
        }

        dialogBinding.pathEdit.setOnEditorConfirmActionListener { performJump() }
        dialogBinding.searchActionButton.setOnClickListener {
            dialog.dismiss()
            openSearchUi()
        }
        dialogBinding.copyActionButton.setOnClickListener {
            val textToCopy = dialogBinding.pathEdit.text?.toString()?.trim()
                ?.takeIf { it.isNotEmpty() } ?: initialText
            clipboardManager.copyText(textToCopy, context)
            dialog.dismiss()
        }

        dialog.setOnShowListener {
            dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                performJump()
            }
        }
        dialog.show()
    }

    private fun hideKeyboard() {
        val token = binding.searchEditText.windowToken ?: view?.windowToken ?: return
        requireContext().getSystemService<InputMethodManager>()
            ?.hideSoftInputFromWindow(token, 0)
    }

    private fun showSortPopup(anchor: View) {
        val active = activeFragment
        if (!active.hasCurrentPath) return
        val popup = PopupMenu(requireContext(), anchor)
        val menu = popup.menu
        menu.setGroupDividerEnabledCompat(true)

        val viewGroupId = 1
        menu.add(viewGroupId, R.id.action_view_list, 0, R.string.file_list_action_view_list)
        menu.add(viewGroupId, R.id.action_view_grid, 1, R.string.file_list_action_view_grid)
        menu.setGroupCheckable(viewGroupId, true, true)
        when (active.viewTypeValue) {
            FileViewType.LIST -> menu.findItem(R.id.action_view_list).isChecked = true
            FileViewType.GRID -> menu.findItem(R.id.action_view_grid).isChecked = true
        }

        val sortGroupId = 2
        val sortOptions = active.sortOptionsValue
        menu.add(sortGroupId, R.id.action_sort_by_name, 0, R.string.file_list_action_sort_by_name)
        menu.add(sortGroupId, R.id.action_sort_by_type, 1, R.string.file_list_action_sort_by_type)
        menu.add(sortGroupId, R.id.action_sort_by_size, 2, R.string.file_list_action_sort_by_size)
        menu.add(
            sortGroupId, R.id.action_sort_by_last_modified, 3,
            R.string.file_list_action_sort_by_last_modified
        )
        menu.setGroupCheckable(sortGroupId, true, true)
        when (sortOptions.by) {
            FileSortOptions.By.NAME -> menu.findItem(R.id.action_sort_by_name).isChecked = true
            FileSortOptions.By.TYPE -> menu.findItem(R.id.action_sort_by_type).isChecked = true
            FileSortOptions.By.SIZE -> menu.findItem(R.id.action_sort_by_size).isChecked = true
            FileSortOptions.By.LAST_MODIFIED ->
                menu.findItem(R.id.action_sort_by_last_modified).isChecked = true
        }

        val toggleGroupId = 3
        menu.add(
            toggleGroupId, R.id.action_sort_order_ascending, 0,
            R.string.file_list_action_sort_order_ascending
        ).apply {
            isCheckable = true
            isChecked = sortOptions.order == FileSortOptions.Order.ASCENDING
        }
        menu.add(
            toggleGroupId, R.id.action_sort_directories_first, 1,
            R.string.file_list_action_sort_directories_first
        ).apply {
            isCheckable = true
            isChecked = sortOptions.isDirectoriesFirst
        }
        menu.add(
            toggleGroupId, R.id.action_view_sort_path_specific, 2,
            R.string.file_list_action_view_sort_path_specific
        ).apply {
            isCheckable = true
            isChecked = active.isViewSortPathSpecificValue
        }

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_view_list -> active.triggerSetViewType(FileViewType.LIST)
                R.id.action_view_grid -> active.triggerSetViewType(FileViewType.GRID)
                R.id.action_sort_by_name -> active.triggerSetSortBy(FileSortOptions.By.NAME)
                R.id.action_sort_by_type -> active.triggerSetSortBy(FileSortOptions.By.TYPE)
                R.id.action_sort_by_size -> active.triggerSetSortBy(FileSortOptions.By.SIZE)
                R.id.action_sort_by_last_modified ->
                    active.triggerSetSortBy(FileSortOptions.By.LAST_MODIFIED)
                R.id.action_sort_order_ascending -> active.triggerSetSortOrder(
                    if (sortOptions.order == FileSortOptions.Order.ASCENDING) {
                        FileSortOptions.Order.DESCENDING
                    } else {
                        FileSortOptions.Order.ASCENDING
                    }
                )
                R.id.action_sort_directories_first ->
                    active.triggerSetSortDirectoriesFirst(!sortOptions.isDirectoriesFirst)
                R.id.action_view_sort_path_specific ->
                    active.triggerSetViewSortPathSpecific(!active.isViewSortPathSpecificValue)
                else -> return@setOnMenuItemClickListener false
            }
            true
        }
        popup.show()
    }

    private fun showOverflowPopup(anchor: View) {
        val active = activeFragment
        if (!active.hasCurrentPath) return
        val popup = PopupMenu(requireContext(), anchor)
        popup.inflate(R.menu.file_list)
        val menu = popup.menu
        menu.findItem(R.id.action_search)?.isVisible = false
        menu.findItem(R.id.action_view_sort)?.isVisible = false
        menu.findItem(R.id.action_show_hidden_files)?.isChecked =
            Settings.FILE_LIST_SHOW_HIDDEN_FILES.valueCompat
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_new_task -> active.triggerNewTask()
                R.id.action_navigate_up -> active.triggerNavigateUp()
                R.id.action_navigate_to -> active.triggerShowNavigateToPathDialog()
                R.id.action_refresh -> active.triggerRefresh()
                R.id.action_select_all -> active.triggerSelectAll()
                R.id.action_show_hidden_files -> Settings.FILE_LIST_SHOW_HIDDEN_FILES.putValue(
                    !Settings.FILE_LIST_SHOW_HIDDEN_FILES.valueCompat
                )
                R.id.action_share -> active.triggerShareCurrentDir()
                R.id.action_copy_path -> active.triggerCopyCurrentPath()
                R.id.action_open_in_terminal -> active.triggerOpenInTerminal()
                R.id.action_add_bookmark -> active.triggerAddBookmarkCurrent()
                R.id.action_create_shortcut -> active.triggerCreateShortcutCurrent()
                else -> return@setOnMenuItemClickListener false
            }
            true
        }
        popup.show()
    }

    private fun showSelectionMorePopup(anchor: View) {
        val active = activeFragment
        val files = active.selectedFilesValue
        val popup = PopupMenu(requireContext(), anchor)
        popup.inflate(R.menu.file_list_select)
        val menu = popup.menu
        val isAnyReadOnly = files.any { it.path.fileSystem.isReadOnly }
        val areAllArchivePaths = files.all { it.path.isArchivePath }
        val areAllArchiveFiles = files.all { it.isArchiveFile }
        val canShare = files.none { it.attributes.isDirectory }
        menu.findItem(R.id.action_cut)?.isVisible = !isAnyReadOnly
        menu.findItem(R.id.action_copy)?.setTitle(
            if (areAllArchivePaths) R.string.file_list_select_action_extract else R.string.copy
        )
        menu.findItem(R.id.action_delete)?.isVisible = !isAnyReadOnly
        menu.findItem(R.id.action_rename)?.isVisible = !isAnyReadOnly
        menu.findItem(R.id.action_extract)?.isVisible = areAllArchiveFiles
        menu.findItem(R.id.action_archive)?.isVisible = !active.currentPath.fileSystem.isReadOnly
        menu.findItem(R.id.action_share)?.isVisible = canShare
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_cut -> active.triggerCutSelectedToClipboard()
                R.id.action_copy -> active.triggerCopySelectedToClipboard()
                R.id.action_delete -> active.triggerDeleteSelected()
                R.id.action_rename -> active.triggerRenameSelected()
                R.id.action_extract -> active.triggerExtractSelected()
                R.id.action_archive -> active.triggerArchiveSelected()
                R.id.action_share -> confirmShareSelection()
                R.id.action_properties -> active.triggerShowPropertiesSelected()
                R.id.action_select_all -> active.triggerSelectAll()
                else -> return@setOnMenuItemClickListener false
            }
            true
        }
        popup.show()
    }

    fun onKeyShortcut(keyCode: Int, event: KeyEvent): Boolean {
        if (!this::leftFragment.isInitialized || !this::rightFragment.isInitialized) {
            return false
        }
        val active = activeFragment
        return active.hasCurrentPath && active.onKeyShortcut(keyCode, event)
    }

    private fun createRestoredPaneIntent(
        setting: SettingLiveData<Path?>,
        fallbackIntent: Intent
    ): Intent {
        val restoredPath = setting.value.resolveRestorablePathOrNull() ?: return fallbackIntent
        return Intent().apply { extraPath = restoredPath }
    }

    private fun persistPanePath(paneIndex: Int, path: Path) {
        if (!path.isPersistableHistoryPath()) {
            return
        }
        val setting = if (paneIndex == 0) {
            Settings.FILE_LIST_LEFT_PANE_DIRECTORY
        } else {
            Settings.FILE_LIST_RIGHT_PANE_DIRECTORY
        }
        if (setting.value != path) {
            setting.putValue(path)
        }
        if (paneIndex == activePaneIndex && Settings.FILE_LIST_LAST_DIRECTORY.value != path) {
            Settings.FILE_LIST_LAST_DIRECTORY.putValue(path)
        }
    }

    private fun persistPaneState() {
        if (Settings.DUAL_PANE_ACTIVE_INDEX.valueCompat != activePaneIndex) {
            Settings.DUAL_PANE_ACTIVE_INDEX.putValue(activePaneIndex)
        }
        if (this::leftFragment.isInitialized && leftFragment.hasCurrentPath) {
            persistPanePath(0, leftFragment.currentPath)
        }
        if (this::rightFragment.isInitialized && rightFragment.hasCurrentPath) {
            persistPanePath(1, rightFragment.currentPath)
        }
    }

    // FileListFragment.DualPaneHost

    override fun onPaneActivated(paneIndex: Int) {
        setActivePane(paneIndex)
    }

    override fun onPanePathChanged(paneIndex: Int, path: Path) {
        persistPanePath(paneIndex, path)
    }

    override fun onPaneTitleChanged(paneIndex: Int, title: String, subtitle: String) {
        if (paneIndex == activePaneIndex) {
            binding.toolbarTitleText.text = title
            binding.toolbarSubtitleText.text = subtitle
            val active = activeFragment
            if (active.hasCurrentPath) {
                activePathLiveData.value = active.currentPath
            }
            updateBackCallbackEnabled()
        }
    }

    override fun onPaneMultiSelectStarted(paneIndex: Int, showActionPanel: Boolean) {
        setActivePane(paneIndex)
        isActionPanelVisible = showActionPanel
    }

    override fun onPaneShowSelectionActions(paneIndex: Int) {
        setActivePane(paneIndex)
        if (activeFragment.selectedFilesValue.isEmpty()) {
            return
        }
        isActionPanelVisible = true
        updateSelectionAndDockUi()
        updateBackCallbackEnabled()
    }

    override fun onPaneSelectedFilesChanged(paneIndex: Int, files: FileItemSet) {
        if (paneIndex == activePaneIndex) {
            updateSelectionAndDockUi()
            updateBackCallbackEnabled()
        }
    }

    override fun onPanePasteStateChanged(paneIndex: Int, pasteState: PasteState) {
        if (paneIndex == activePaneIndex) {
            updateSelectionAndDockUi()
        }
    }

    override fun onPaneCloseSearch() {
        if (isSearchUiVisible) {
            closeSearchUi(clearQuery = true)
        }
    }

    override fun onPaneStorageAccessGranted() {
        if (this::leftFragment.isInitialized) {
            leftFragment.triggerRefresh()
        }
        if (this::rightFragment.isInitialized) {
            rightFragment.triggerRefresh()
        }
    }

    // NavigationFragment.Listener

    override val currentPathOrNull: Path?
        get() = if (this::leftFragment.isInitialized && this::rightFragment.isInitialized &&
            activeFragment.hasCurrentPath
        ) {
            activeFragment.currentPath
        } else {
            null
        }

    override fun navigateTo(path: Path) {
        if (this::leftFragment.isInitialized && this::rightFragment.isInitialized) {
            activeFragment.triggerNavigateTo(path)
        }
    }

    override fun navigateToRoot(path: Path) {
        if (this::leftFragment.isInitialized && this::rightFragment.isInitialized) {
            activeFragment.triggerResetTo(path)
        }
    }

    override fun navigateToDefaultRoot() {
        if (this::leftFragment.isInitialized && this::rightFragment.isInitialized) {
            activeFragment.triggerResetTo(Settings.FILE_LIST_DEFAULT_DIRECTORY.valueCompat)
        }
    }

    override fun observeCurrentPath(owner: LifecycleOwner, observer: (Path) -> Unit) {
        activePathLiveData.observe(owner, observer)
    }

    override fun closeNavigationDrawer() {
        binding.drawerLayout.closeDrawer(GravityCompat.START)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupRecentHistorySheet() {
        recentHistoryAdapter = RecentHistoryAdapter { entry -> onRecentEntryClicked(entry) }
        binding.recentHistoryRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recentHistoryRecyclerView.adapter = recentHistoryAdapter

        binding.mainContentRoot.canSwipeUpFromBottom = {
            !isRecentHistoryVisible &&
                !binding.drawerLayout.isDrawerOpen(GravityCompat.START) &&
                !isSearchUiVisible &&
                activeFragment.selectedFilesValue.isEmpty() &&
                !speedDialView.isOpen
        }
        binding.mainContentRoot.onBottomSwipeUp = { openRecentHistorySheet(animate = true) }
        binding.recentBottomBar.setOnClickListener { openRecentHistorySheet(animate = true) }
        binding.recentHistoryOverlay.setOnClickListener { closeRecentHistorySheet(animate = true) }
        binding.recentHistoryCloseButton.setOnClickListener { closeRecentHistorySheet(animate = true) }
        binding.recentHistoryClearButton.setOnClickListener { RecentHistoryManager.clear() }

        binding.recentFilterAll.setOnClickListener {
            recentHistoryFilter = RecentHistoryFilter.ALL
            updateRecentHistoryFilterChips()
            refreshRecentHistoryList()
        }
        binding.recentFilterFiles.setOnClickListener {
            recentHistoryFilter = RecentHistoryFilter.FILES
            updateRecentHistoryFilterChips()
            refreshRecentHistoryList()
        }
        binding.recentFilterFolders.setOnClickListener {
            recentHistoryFilter = RecentHistoryFilter.FOLDERS
            updateRecentHistoryFilterChips()
            refreshRecentHistoryList()
        }

        val touchSlop = ViewConfiguration.get(requireContext()).scaledTouchSlop.toFloat()
        val dismissThresholdPx = requireContext().dpToDimension(72f)
        var sheetDownRawY = 0f
        var draggingSheetDown = false
        binding.recentHistoryHeaderArea.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    sheetDownRawY = event.rawY
                    draggingSheetDown = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dy = event.rawY - sheetDownRawY
                    if (!draggingSheetDown && dy > touchSlop) {
                        draggingSheetDown = true
                    }
                    if (draggingSheetDown) {
                        binding.recentHistorySheet.translationY = dy.coerceAtLeast(0f)
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val dy = binding.recentHistorySheet.translationY
                    draggingSheetDown = false
                    if (event.actionMasked == MotionEvent.ACTION_UP && dy >= dismissThresholdPx) {
                        closeRecentHistorySheet(animate = true)
                    } else {
                        binding.recentHistorySheet.animate()
                            .translationY(0f)
                            .setDuration(180L)
                            .setInterpolator(FastOutSlowInInterpolator())
                            .start()
                    }
                    true
                }
                else -> false
            }
        }

        updateRecentHistoryFilterChips()
        Settings.RECENT_ACCESS_ENTRIES.observe(viewLifecycleOwner) {
            refreshRecentHistoryList()
        }
    }

    private fun updateRecentHistoryFilterChips() {
        val context = requireContext()
        val selectedBgTint = ColorStateList.valueOf(
            context.getColorByAttr(androidx.appcompat.R.attr.colorPrimary)
        )
        val selectedTextColor =
            context.getColorByAttr(com.google.android.material.R.attr.colorOnPrimary)
        val unselectedTextColor =
            context.getColorByAttr(com.google.android.material.R.attr.colorOnSurfaceVariant)
        val applyStyle: (TextView, Boolean) -> Unit = { chip, selected ->
            chip.backgroundTintList = if (selected) selectedBgTint else null
            chip.setTextColor(if (selected) selectedTextColor else unselectedTextColor)
        }
        applyStyle(binding.recentFilterAll, recentHistoryFilter == RecentHistoryFilter.ALL)
        applyStyle(binding.recentFilterFiles, recentHistoryFilter == RecentHistoryFilter.FILES)
        applyStyle(binding.recentFilterFolders, recentHistoryFilter == RecentHistoryFilter.FOLDERS)
    }

    private fun refreshRecentHistoryList() {
        if (!this::recentHistoryAdapter.isInitialized) {
            return
        }
        val allEntries = Settings.RECENT_ACCESS_ENTRIES.valueCompat
        val filtered = when (recentHistoryFilter) {
            RecentHistoryFilter.ALL -> allEntries
            RecentHistoryFilter.FILES -> allEntries.filter { !it.isDirectory }
            RecentHistoryFilter.FOLDERS -> allEntries.filter { it.isDirectory }
        }
        recentHistoryAdapter.submitList(filtered)
        binding.recentHistoryClearButton.isVisible = allEntries.isNotEmpty()
        if (filtered.isEmpty()) {
            binding.recentHistoryRecyclerView.isVisible = false
            binding.recentHistoryEmptyText.isVisible = true
        } else {
            binding.recentHistoryRecyclerView.isVisible = true
            binding.recentHistoryEmptyText.isVisible = false
            val maxListHeight = (resources.displayMetrics.heightPixels * 0.44f).toInt()
            val rowHeight = requireContext().dpToDimensionPixelSize(62)
            binding.recentHistoryRecyclerView.updateLayoutParams<ViewGroup.LayoutParams> {
                height = (filtered.size * rowHeight).coerceAtMost(maxListHeight)
            }
        }
    }

    private fun openRecentHistorySheet(animate: Boolean) {
        if (speedDialView.isOpen) {
            speedDialView.close()
        }
        isRecentHistoryVisible = true
        binding.recentBottomBar.isVisible = false
        refreshRecentHistoryList()
        binding.recentHistoryOverlay.animate().cancel()
        binding.recentHistorySheet.animate().cancel()
        binding.recentHistoryOverlay.isVisible = true
        if (!animate) {
            binding.recentHistoryOverlay.alpha = 1f
            binding.recentHistorySheet.translationY = 0f
        } else {
            binding.recentHistoryOverlay.alpha = 0f
            binding.recentHistoryOverlay.animate()
                .alpha(1f)
                .setDuration(200L)
                .setInterpolator(FastOutSlowInInterpolator())
                .start()
            binding.recentHistorySheet.post {
                if (view == null || !isRecentHistoryVisible) return@post
                val slideDistance = binding.recentHistorySheet.height.takeIf { it > 0 }?.toFloat()
                    ?: requireContext().dpToDimension(320f)
                binding.recentHistorySheet.translationY = slideDistance
                binding.recentHistorySheet.animate()
                    .translationY(0f)
                    .setDuration(240L)
                    .setInterpolator(FastOutSlowInInterpolator())
                    .start()
            }
        }
        updateBackCallbackEnabled()
    }

    private fun closeRecentHistorySheet(animate: Boolean) {
        if (!isRecentHistoryVisible && !binding.recentHistoryOverlay.isVisible) {
            return
        }
        isRecentHistoryVisible = false
        updateBackCallbackEnabled()
        if (this::leftFragment.isInitialized && this::rightFragment.isInitialized &&
            activeFragment.hasCurrentPath
        ) {
            updateSelectionAndDockUi()
        } else {
            binding.recentBottomBar.isVisible = true
        }
        binding.recentHistoryOverlay.animate().cancel()
        binding.recentHistorySheet.animate().cancel()
        if (!animate) {
            binding.recentHistoryOverlay.isVisible = false
            binding.recentHistoryOverlay.alpha = 1f
            binding.recentHistorySheet.translationY = 0f
            return
        }
        val slideDistance = binding.recentHistorySheet.height.takeIf { it > 0 }?.toFloat()
            ?: requireContext().dpToDimension(320f)
        binding.recentHistoryOverlay.animate()
            .alpha(0f)
            .setDuration(180L)
            .setInterpolator(FastOutLinearInInterpolator())
            .start()
        binding.recentHistorySheet.animate()
            .translationY(slideDistance)
            .setDuration(200L)
            .setInterpolator(FastOutLinearInInterpolator())
            .withEndAction {
                if (view != null && !isRecentHistoryVisible) {
                    binding.recentHistoryOverlay.isVisible = false
                    binding.recentHistoryOverlay.alpha = 1f
                    binding.recentHistorySheet.translationY = 0f
                }
            }
            .start()
    }

    private fun onRecentEntryClicked(entry: RecentAccessEntry) {
        closeRecentHistorySheet(animate = true)
        viewLifecycleOwner.lifecycleScope.launch {
            val exists = withContext(Dispatchers.IO) {
                runCatching { entry.path.exists() }.getOrDefault(true)
            }
            if (!isAdded) {
                return@launch
            }
            if (!exists) {
                RecentHistoryManager.remove(entry.path)
                showToast(
                    if (entry.isDirectory) {
                        R.string.recent_history_missing_directory
                    } else {
                        R.string.recent_history_missing_file
                    }
                )
                return@launch
            }
            if (entry.isDirectory) {
                RecentHistoryManager.recordDirectory(entry.path)
                if (this@DualPaneFileListFragment::leftFragment.isInitialized &&
                    this@DualPaneFileListFragment::rightFragment.isInitialized
                ) {
                    activeFragment.triggerNavigateTo(entry.path)
                }
            } else {
                val mimeType = entry.mimeTypeValue.asMimeTypeOrNull()
                    ?: MimeType.guessFromPath(entry.path.toString())
                RecentHistoryManager.recordFile(entry.path, mimeType)
                if (entry.path.isArchivePath) {
                    FileJobService.open(entry.path, mimeType, false, requireContext())
                } else if (RecentHistoryAdapter.isDirectlyEditable(mimeType, entry.path.name)) {
                    val intent = Intent(requireContext(), TextEditorActivity::class.java).apply {
                        extraPath = entry.path
                    }
                    startActivitySafe(intent)
                } else {
                    startActivitySafe(
                        entry.path.createDirectViewIntent(
                            mimeType,
                            allowInternalTextEditor = true
                        )
                    )
                }
            }
        }
    }

    companion object {
        private const val ACTION_VIEW_DOWNLOADS =
            "me.zhanghai.android.files.intent.action.VIEW_DOWNLOADS"
        private const val STATE_ACTIVE_PANE_INDEX = "active_pane_index"
        private const val STATE_SPLIT_RATIO = "split_ratio"
        private const val STATE_SEARCH_UI_VISIBLE = "search_ui_visible"
        private const val STATE_ACTION_PANEL_VISIBLE = "action_panel_visible"
        private const val STATE_RECENT_HISTORY_VISIBLE = "recent_history_visible"
    }

    @Parcelize
    class Args(val intent: Intent) : ParcelableArgs
}

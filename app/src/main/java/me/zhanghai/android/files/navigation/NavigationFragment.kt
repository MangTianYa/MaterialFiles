/*
 * Copyright (c) 2018 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.navigation

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleOwner
import androidx.recyclerview.widget.LinearLayoutManager
import java8.nio.file.Path
import me.zhanghai.android.files.databinding.NavigationFragmentBinding
import me.zhanghai.android.files.settings.Settings
import me.zhanghai.android.files.settings.SettingsActivity
import me.zhanghai.android.files.theme.night.NightMode
import me.zhanghai.android.files.theme.night.NightModeHelper
import me.zhanghai.android.files.util.createIntent
import me.zhanghai.android.files.util.startActivitySafe

class NavigationFragment : Fragment(), NavigationItem.Listener {
    private lateinit var binding: NavigationFragmentBinding

    private lateinit var adapter: NavigationListAdapter

    private val collapsedSections = mutableSetOf<NavigationSection>()

    lateinit var listener: Listener

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View =
        NavigationFragmentBinding.inflate(inflater, container, false)
            .also { binding = it }
            .root

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        if (savedInstanceState != null) {
            savedInstanceState.getIntegerArrayList(STATE_COLLAPSED_SECTIONS)?.forEach { ordinal ->
                NavigationSection.entries.getOrNull(ordinal)?.let { collapsedSections.add(it) }
            }
        }

        binding.themeToggleButton.setOnClickListener { toggleNightMode() }
        binding.settingsButton.setOnClickListener {
            startActivitySafe(SettingsActivity::class.createIntent())
            listener.closeNavigationDrawer()
        }

        setupWindowInsets()

        val context = requireContext()
        binding.recyclerView.layoutManager = LinearLayoutManager(context)
        adapter = NavigationListAdapter(this, ::toggleSection, context)
        binding.recyclerView.adapter = adapter

        val viewLifecycleOwner = viewLifecycleOwner
        NavigationItemListLiveData.observe(viewLifecycleOwner) { refreshNavigationItems() }
        listener.observeCurrentPath(viewLifecycleOwner) { onCurrentPathChanged(it) }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putIntegerArrayList(
            STATE_COLLAPSED_SECTIONS,
            ArrayList(collapsedSections.map { it.ordinal })
        )
    }

    private fun setupWindowInsets() {
        val headerInitialTop = binding.drawerHeader.paddingTop
        val rootInitialLeft = binding.drawerRootLayout.paddingLeft
        val recyclerInitialBottom = binding.recyclerView.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(binding.drawerRootLayout) { root, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            root.updatePadding(left = rootInitialLeft + bars.left)
            binding.drawerHeader.updatePadding(top = headerInitialTop + bars.top)
            binding.recyclerView.updatePadding(bottom = recyclerInitialBottom + bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(binding.drawerRootLayout)
    }

    private fun toggleNightMode() {
        val activity = requireActivity() as AppCompatActivity
        val targetMode = if (NightModeHelper.isInNightMode(activity)) {
            NightMode.OFF
        } else {
            NightMode.ON
        }
        Settings.NIGHT_MODE.putValue(targetMode)
        NightModeHelper.sync()
    }

    private fun toggleSection(section: NavigationSection) {
        if (!collapsedSections.add(section)) {
            collapsedSections.remove(section)
        }
        refreshNavigationItems()
    }

    private fun refreshNavigationItems() {
        adapter.replace(buildNavigationItems(collapsedSections))
    }

    private fun onCurrentPathChanged(path: Path) {
        adapter.notifyCheckedChanged()
    }

    override val currentPathOrNull: Path?
        get() = listener.currentPathOrNull

    override fun navigateTo(path: Path) {
        listener.navigateTo(path)
    }

    override fun navigateToRoot(path: Path) {
        listener.navigateToRoot(path)
    }

    override fun launchIntent(intent: Intent) {
        startActivitySafe(intent)
    }

    override fun closeNavigationDrawer() {
        listener.closeNavigationDrawer()
    }

    companion object {
        private const val STATE_COLLAPSED_SECTIONS = "collapsed_sections"
    }

    interface Listener {
        val currentPathOrNull: Path?
        fun navigateTo(path: Path)
        fun navigateToRoot(path: Path)
        fun navigateToDefaultRoot()
        fun observeCurrentPath(owner: LifecycleOwner, observer: (Path) -> Unit)
        fun closeNavigationDrawer()
    }
}

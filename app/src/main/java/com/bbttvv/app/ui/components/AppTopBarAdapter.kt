package com.bbttvv.app.ui.components

import android.graphics.Typeface
import android.os.Build
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.bbttvv.app.databinding.ItemAppTopTabBinding

internal class AppTopBarAdapter : RecyclerView.Adapter<AppTopBarAdapter.TabViewHolder>() {
    private val tabs = ArrayList<AppTopBarEntry>()
    private var selectedTab: AppTopLevelTab? = null
    private var updateSelectedTabOnFocus: Boolean = true
    private var pendingDirectionalId: Long? = null
    private var selectedCategoryIndex = 0
    private var onCategorySelected: (Int) -> Unit = {}
    private var onTabSelected: (AppTopLevelTab) -> Unit = {}
    private var onSelectedTabConfirmed: (AppTopLevelTab) -> Unit = {}
    private var onDpadDown: () -> Boolean = { false }
    private var onTopBarFocused: () -> Unit = {}
    private var onDpadLeft: () -> Boolean = { false }
    private var onDpadRight: () -> Boolean = { false }
    private var themeColors = AppTopBarThemeColors.Dark

    fun setThemeColors(colors: AppTopBarThemeColors) {
        if (themeColors != colors) {
            themeColors = colors
            notifyItemRangeChanged(0, itemCount, TabThemePayload)
        }
    }

    init {
        setHasStableIds(true)
        stateRestorationPolicy = StateRestorationPolicy.PREVENT_WHEN_EMPTY
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TabViewHolder {
        val binding = ItemAppTopTabBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TabViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TabViewHolder, position: Int) {
        holder.bind(tabs[position])
    }

    override fun onBindViewHolder(holder: TabViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.contains(TabSelectionPayload) || payloads.contains(TabThemePayload)) {
            holder.updateSelectionVisual()
            return
        }
        super.onBindViewHolder(holder, position, payloads)
    }

    override fun getItemCount(): Int = tabs.size

    override fun getItemId(position: Int): Long = tabs[position].stableId

    fun updateCallbacks(
        updateSelectedTabOnFocus: Boolean,
        onTabSelected: (AppTopLevelTab) -> Unit,
        onSelectedTabConfirmed: (AppTopLevelTab) -> Unit,
        onDpadDown: () -> Boolean,
        onTopBarFocused: () -> Unit,
        onDpadLeft: () -> Boolean = { false },
        onDpadRight: () -> Boolean = { false },
    ) {
        this.updateSelectedTabOnFocus = updateSelectedTabOnFocus
        if (!updateSelectedTabOnFocus) {
            pendingDirectionalId = null
        }
        this.onTabSelected = onTabSelected
        this.onSelectedTabConfirmed = onSelectedTabConfirmed
        this.onDpadDown = onDpadDown
        this.onTopBarFocused = onTopBarFocused
        this.onDpadLeft = onDpadLeft
        this.onDpadRight = onDpadRight
    }

    fun submitTabs(
        newTabs: List<AppTopBarEntry>,
        newSelectedTab: AppTopLevelTab?,
        categoryIndex: Int = 0,
        onCategorySelected: (Int) -> Unit = {},
    ) {
        this.onCategorySelected = onCategorySelected
        val previousCategoryIndex = selectedCategoryIndex
        selectedCategoryIndex = categoryIndex
        val tabsChanged = tabs != newTabs
        if (tabsChanged) {
            val previousTabs = tabs.toList()
            val previousSelectedTab = selectedTab
            val diff = DiffUtil.calculateDiff(
                AppTopBarDiffCallback(
                    oldTabs = previousTabs,
                    newTabs = newTabs,
                )
            )
            pendingDirectionalId = null
            tabs.clear()
            tabs.addAll(newTabs)
            selectedTab = newSelectedTab
            diff.dispatchUpdatesTo(this)
            if (previousSelectedTab != newSelectedTab) {
                notifyTabChanged(previousSelectedTab)
                notifyTabChanged(newSelectedTab)
            }
            return
        }

        if (selectedTab == newSelectedTab && previousCategoryIndex == categoryIndex) return
        val previous = selectedTab
        selectedTab = newSelectedTab
        tabs.indices.filter { tabs[it].tab == previous || tabs[it].tab == newSelectedTab }.forEach { notifyItemChanged(it, TabSelectionPayload) }
    }

    fun positionOf(tab: AppTopLevelTab?): Int {
        if (tab == null) return RecyclerView.NO_POSITION
        return tabs.indexOfFirst { it.tab == tab && (it.categoryIndex == null || it.categoryIndex == selectedCategoryIndex) }.takeIf { it >= 0 } ?: RecyclerView.NO_POSITION
    }

    fun currentSelectedTab(): AppTopLevelTab? = selectedTab

    fun cancelPendingDirectionalSelection() {
        pendingDirectionalId = null
    }

    fun focusTargetOrFallback(tab: AppTopLevelTab?): AppTopLevelTab? {
        return tab?.takeIf { tabs.any { entry -> entry.tab == it } }
            ?: selectedTab?.takeIf { tabs.any { entry -> entry.tab == it } }
            ?: tabs.firstOrNull()?.tab
    }

    private fun select(entry: AppTopBarEntry) {
        val tab = entry.tab
        if (entry.categoryIndex != null) {
            onCategorySelected(entry.categoryIndex)
            return
        }
        if (selectedTab == tab) {
            onSelectedTabConfirmed(tab)
            return
        }
        if (selectedTab != tab) {
            val previous = selectedTab
            selectedTab = tab
            notifyTabChanged(previous)
            notifyTabChanged(tab)
        }
        onTabSelected(tab)
    }

    private fun notifyTabChanged(tab: AppTopLevelTab?) {
        val position = positionOf(tab)
        if (position != RecyclerView.NO_POSITION) {
            notifyItemChanged(position, TabSelectionPayload)
        }
    }

    inner class TabViewHolder(
        private val binding: ItemAppTopTabBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        private var boundEntry: AppTopBarEntry? = null
        private val boundTab: AppTopLevelTab? get() = boundEntry?.tab
        private fun isSelected(): Boolean = boundEntry?.let { it.tab == selectedTab && (it.categoryIndex == null || it.categoryIndex == selectedCategoryIndex) } == true

        init {
            binding.root.isFocusable = true
            binding.root.isFocusableInTouchMode = true
            binding.root.isClickable = true

            binding.root.setOnClickListener {
                boundEntry?.let(::select)
            }

            binding.root.setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) {
                    // A horizontal D-Pad move selects the newly focused tab. The new holder
                    // receives focus before RecyclerView can dispatch the old holder's
                    // selection payload, so animating the old selected appearance here leaves
                    // a visible second cursor for a frame. Clear it synchronously instead.
                    val hideStaleSelectedVisual =
                        updateSelectedTabOnFocus &&
                            isSelected() &&
                            pendingDirectionalId != null
                    updateVisual(
                        selectedOverride = isSelected() && !hideStaleSelectedVisual,
                    )
                    return@setOnFocusChangeListener
                }

                updateVisual()
                onTopBarFocused()
                val entry = boundEntry ?: return@setOnFocusChangeListener
                val selectFromDirectionalFocus = pendingDirectionalId == entry.stableId
                pendingDirectionalId = null
                if (updateSelectedTabOnFocus && selectFromDirectionalFocus && !isSelected()) {
                    select(entry)
                }
            }

            binding.root.setOnKeyListener { _, keyCode, event ->
                if (event.action != KeyEvent.ACTION_DOWN) {
                    return@setOnKeyListener false
                }
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        if (themeColors.sidebar) return@setOnKeyListener moveSidebar(-1)
                        pendingDirectionalId = null
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        if (themeColors.sidebar) return@setOnKeyListener moveSidebar(1)
                        pendingDirectionalId = null
                        onDpadDown()
                    }
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        if (themeColors.sidebar) return@setOnKeyListener true
                        val position = bindingAdapterPosition
                        val target = tabs.getOrNull(position - 1)
                        if (target == null) {
                            pendingDirectionalId = null
                            onDpadLeft()
                            true
                        } else {
                            pendingDirectionalId = target.stableId
                            false
                        }
                    }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        if (themeColors.sidebar) return@setOnKeyListener onDpadRight()
                        val position = bindingAdapterPosition
                        val target = if (position == RecyclerView.NO_POSITION) {
                            null
                        } else {
                            tabs.getOrNull(position + 1)
                        }
                        if (target == null) {
                            pendingDirectionalId = null
                            true
                        } else {
                            pendingDirectionalId = target.stableId
                            false
                        }
                    }
                    else -> {
                        pendingDirectionalId = null
                        false
                    }
                }
            }
        }

        fun bind(entry: AppTopBarEntry) {
            boundEntry = entry
            val tab = entry.tab
            binding.tvLabel.text = if (themeColors.sidebar) sidebarLabel(tab) else entry.label
            binding.root.contentDescription = if (themeColors.sidebar) sidebarLabel(tab) else entry.label
            updateVisual()
        }

        fun updateSelectionVisual() {
            updateVisual()
        }

        private fun updateVisual(selectedOverride: Boolean = isSelected()) {
            val selected = selectedOverride
            val focused = binding.root.isFocused
            val scale = when {
                themeColors.underline || themeColors.sidebar -> 1f
                focused -> 1.06f
                selected -> 1.03f
                else -> 1f
            }
            // 焦点移动是高频 TV 操作，缩放必须同步更新，避免产生第二个残留光标。
            binding.root.animate().cancel()
            binding.root.scaleX = scale
            binding.root.scaleY = scale

            val cardColor = when {
                themeColors.sidebar && (selected || focused) -> 0xFFFFFFFF.toInt()
                themeColors.underline -> android.graphics.Color.TRANSPARENT
                focused -> themeColors.focusContainer
                else -> android.graphics.Color.TRANSPARENT
            }
            binding.root.setCardBackgroundColor(cardColor)
            if (themeColors.underline) {
                // MaterialCardView also paints a state foreground/ripple independently
                // of its background; keep the flat text-and-underline header unfilled.
                val transparent = android.content.res.ColorStateList.valueOf(android.graphics.Color.TRANSPARENT)
                binding.root.setCardForegroundColor(transparent)
                binding.root.setRippleColor(transparent)
            }
            binding.root.strokeWidth = if (themeColors.sidebar && focused) binding.root.dp(2) else 0
            binding.vIndicator.setBackgroundColor(themeColors.accent)
            binding.root.setStrokeColor(themeColors.accent)
            binding.vIndicator.visibility = if (themeColors.underline && selected) View.VISIBLE else View.GONE
            if (themeColors.sidebar) {
                binding.tvLabel.text = boundTab?.let(::sidebarLabel)
                binding.tvLabel.minWidth = binding.root.dp(92)
                binding.tvLabel.textSize = 14f
                binding.tvLabel.setPadding(binding.root.dp(12), binding.root.dp(12), binding.root.dp(12), binding.root.dp(12))
                val icon = when (boundTab) {
                    AppTopLevelTab.SEARCH -> android.R.drawable.ic_menu_search
                    AppTopLevelTab.RECOMMEND -> android.R.drawable.ic_media_play
                    AppTopLevelTab.LIVE -> android.R.drawable.ic_menu_slideshow
                    AppTopLevelTab.DYNAMIC -> android.R.drawable.ic_menu_myplaces
                    AppTopLevelTab.WATCH_LATER -> android.R.drawable.ic_menu_recent_history
                    AppTopLevelTab.TODAY_WATCH -> android.R.drawable.ic_menu_today
                    else -> android.R.drawable.ic_menu_manage
                }
                val drawable = androidx.core.content.ContextCompat.getDrawable(binding.root.context, icon)?.mutate()
                drawable?.setTint(if (selected || focused) 0xFF18191C.toInt() else themeColors.content)
                drawable?.setBounds(0, 0, binding.root.dp(18), binding.root.dp(18))
                binding.tvLabel.setCompoundDrawables(drawable, null, null, null)
                binding.tvLabel.compoundDrawablePadding = binding.root.dp(10)
            }

            val textColor = when {
                (themeColors.underline || themeColors.sidebar) && (focused || selected) -> 0xFF18191C.toInt()
                focused -> themeColors.focusContent
                selected -> themeColors.selectedContent
                else -> themeColors.content
            }
            binding.tvLabel.setTextColor(textColor)
            binding.tvLabel.typeface = topBarTypeface(
                if ((themeColors.underline || themeColors.sidebar) && (selected || focused)) 700
                else if (selected || focused) 600 else 500
            )
        }

        private fun moveSidebar(delta: Int): Boolean {
            val position = bindingAdapterPosition
            val target = tabs.getOrNull(position + delta) ?: return true
            // Only an explicit directional move selects a different module.
            pendingDirectionalId = target.stableId
            return false
        }
    }

    private fun topBarTypeface(weight: Int): Typeface {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Typeface.create(Typeface.DEFAULT, weight, false)
        } else {
            Typeface.create(Typeface.DEFAULT, if (weight >= 600) Typeface.BOLD else Typeface.NORMAL)
        }
    }

    private companion object {
        val TabSelectionPayload = Any()
        val TabThemePayload = Any()
    }
}

internal fun resolveTopBarSelectedVisual(
    boundTab: AppTopLevelTab?,
    selectedTab: AppTopLevelTab?,
    hideStaleSelectedVisual: Boolean,
): Boolean {
    return !hideStaleSelectedVisual && boundTab == selectedTab
}

internal data class AppTopBarThemeColors(
    val focusContainer: Int,
    val focusContent: Int,
    val selectedContent: Int,
    val content: Int,
    val underline: Boolean = false,
    val sidebar: Boolean = false,
    val accent: Int = 0xFFFB7299.toInt(),
) {
    companion object {
        val Dark = AppTopBarThemeColors(
            focusContainer = android.graphics.Color.WHITE,
            focusContent = android.graphics.Color.rgb(17, 20, 24),
            selectedContent = android.graphics.Color.WHITE,
            content = android.graphics.Color.argb(153, 255, 255, 255),
        )
    }
}

private fun sidebarLabel(tab: AppTopLevelTab): String =
    if (tab == AppTopLevelTab.RECOMMEND) "视频" else tab.title

private class AppTopBarDiffCallback(
    private val oldTabs: List<AppTopBarEntry>,
    private val newTabs: List<AppTopBarEntry>,
) : DiffUtil.Callback() {
    override fun getOldListSize(): Int = oldTabs.size

    override fun getNewListSize(): Int = newTabs.size

    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        return oldTabs[oldItemPosition].stableId == newTabs[newItemPosition].stableId
    }

    override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        return oldTabs[oldItemPosition] == newTabs[newItemPosition]
    }
}

internal data class AppTopBarEntry(
    val tab: AppTopLevelTab,
    val label: String = tab.title,
    val categoryIndex: Int? = null,
    val categoryKey: Long? = null,
) {
    val stableId: Long get() = if (categoryKey == null || categoryKey == 0L) tab.stableId else 10000L + categoryKey
}

internal data class AppTopBarCategory(val label: String, val key: Long)

internal fun homeHeaderEntries(tabs: List<AppTopLevelTab>, categories: List<AppTopBarCategory>): List<AppTopBarEntry> =
    tabs.flatMap { tab ->
        if (tab == AppTopLevelTab.POPULAR && categories.isNotEmpty()) {
            categories.mapIndexed { index, category -> AppTopBarEntry(tab, category.label, index, category.key) }
        } else listOf(AppTopBarEntry(tab))
    }

package com.bbttvv.app.ui.components

import android.graphics.Color
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
    private val tabs = ArrayList<AppTopLevelTab>()
    private var selectedTab: AppTopLevelTab? = null
    private var updateSelectedTabOnFocus: Boolean = true
    private val directionalSelectionTracker = AppTopBarDirectionalSelectionTracker()
    private var onTabSelected: (AppTopLevelTab) -> Unit = {}
    private var onSelectedTabConfirmed: (AppTopLevelTab) -> Unit = {}
    private var onDpadDown: () -> Boolean = { false }
    private var onTopBarFocused: () -> Unit = {}
    private var isLightTheme: Boolean = false

    fun setIsLightTheme(isLight: Boolean) {
        if (isLightTheme != isLight) {
            isLightTheme = isLight
            notifyDataSetChanged()
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
        if (payloads.contains(TabSelectionPayload)) {
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
        onTopBarFocused: () -> Unit
    ) {
        this.updateSelectedTabOnFocus = updateSelectedTabOnFocus
        if (!updateSelectedTabOnFocus) {
            directionalSelectionTracker.clear()
        }
        this.onTabSelected = onTabSelected
        this.onSelectedTabConfirmed = onSelectedTabConfirmed
        this.onDpadDown = onDpadDown
        this.onTopBarFocused = onTopBarFocused
    }

    fun submitTabs(
        newTabs: List<AppTopLevelTab>,
        newSelectedTab: AppTopLevelTab?
    ) {
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
            directionalSelectionTracker.clear()
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

        if (selectedTab == newSelectedTab) return
        val previous = selectedTab
        selectedTab = newSelectedTab
        notifyTabChanged(previous)
        notifyTabChanged(newSelectedTab)
    }

    fun positionOf(tab: AppTopLevelTab?): Int {
        if (tab == null) return RecyclerView.NO_POSITION
        return tabs.indexOf(tab).takeIf { it >= 0 } ?: RecyclerView.NO_POSITION
    }

    fun currentSelectedTab(): AppTopLevelTab? = selectedTab

    fun cancelPendingDirectionalSelection() {
        directionalSelectionTracker.clear()
    }

    fun focusTargetOrFallback(tab: AppTopLevelTab?): AppTopLevelTab? {
        return tab?.takeIf { it in tabs }
            ?: selectedTab?.takeIf { it in tabs }
            ?: tabs.firstOrNull()
    }

    private fun select(tab: AppTopLevelTab) {
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
        private var boundTab: AppTopLevelTab? = null

        init {
            binding.root.isFocusable = true
            binding.root.isFocusableInTouchMode = true
            binding.root.isClickable = true

            binding.root.setOnClickListener {
                boundTab?.let(::select)
            }

            binding.root.setOnFocusChangeListener { _, hasFocus ->
                updateVisual(animate = true)
                if (!hasFocus) return@setOnFocusChangeListener
                onTopBarFocused()
                val tab = boundTab ?: return@setOnFocusChangeListener
                val selectFromDirectionalFocus = directionalSelectionTracker.consume(tab)
                if (updateSelectedTabOnFocus && selectFromDirectionalFocus && tab != selectedTab) {
                    select(tab)
                }
            }

            binding.root.setOnKeyListener { _, keyCode, event ->
                if (event.action != KeyEvent.ACTION_DOWN) {
                    return@setOnKeyListener false
                }
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        directionalSelectionTracker.clear()
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        directionalSelectionTracker.clear()
                        onDpadDown()
                    }
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        val position = bindingAdapterPosition
                        val target = tabs.getOrNull(position - 1)
                        if (target == null) {
                            directionalSelectionTracker.clear()
                            true
                        } else {
                            directionalSelectionTracker.expect(target)
                            false
                        }
                    }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        val position = bindingAdapterPosition
                        val target = if (position == RecyclerView.NO_POSITION) {
                            null
                        } else {
                            tabs.getOrNull(position + 1)
                        }
                        if (target == null) {
                            directionalSelectionTracker.clear()
                            true
                        } else {
                            directionalSelectionTracker.expect(target)
                            false
                        }
                    }
                    else -> {
                        directionalSelectionTracker.clear()
                        false
                    }
                }
            }
        }

        fun bind(tab: AppTopLevelTab) {
            boundTab = tab
            binding.tvLabel.text = tab.title
            binding.root.contentDescription = tab.title
            updateVisual(animate = false)
        }

        fun updateSelectionVisual() {
            updateVisual(animate = false)
        }

        private fun updateVisual(animate: Boolean) {
            val selected = boundTab == selectedTab
            val focused = binding.root.isFocused
            val scale = when {
                focused -> 1.06f
                selected -> 1.03f
                else -> 1f
            }
            if (animate) {
                binding.root.animate()
                    .scaleX(scale)
                    .scaleY(scale)
                    .setDuration(150L)
                    .start()
            } else {
                binding.root.animate().cancel()
                binding.root.scaleX = scale
                binding.root.scaleY = scale
            }

            val cardColor = when {
                focused -> {
                    if (isLightTheme) Color.rgb(251, 114, 153) // #FB7299
                    else Color.WHITE
                }
                else -> Color.TRANSPARENT
            }
            binding.root.setCardBackgroundColor(cardColor)

            val textColor = when {
                focused -> {
                    if (isLightTheme) Color.WHITE
                    else Color.rgb(17, 20, 24)
                }
                selected -> {
                    if (isLightTheme) Color.rgb(251, 114, 153) // #FB7299
                    else Color.WHITE
                }
                else -> {
                    if (isLightTheme) Color.rgb(97, 102, 109) // #61666D
                    else Color.argb(153, 255, 255, 255)
                }
            }
            binding.tvLabel.setTextColor(textColor)
            binding.tvLabel.typeface = topBarTypeface(if (selected || focused) 600 else 500)
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
    }
}

private class AppTopBarDiffCallback(
    private val oldTabs: List<AppTopLevelTab>,
    private val newTabs: List<AppTopLevelTab>,
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

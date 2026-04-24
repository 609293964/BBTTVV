package com.bbttvv.app.ui.components

import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bbttvv.app.databinding.ItemAppTopTabBinding

internal class AppTopBarAdapter : RecyclerView.Adapter<AppTopBarAdapter.TabViewHolder>() {
    private val tabs = ArrayList<AppTopLevelTab>()
    private var selectedTab: AppTopLevelTab? = null
    private var updateSelectedTabOnFocus: Boolean = true
    private var allowFocusedTabSelection: Boolean = false
    private var onTabSelected: (AppTopLevelTab) -> Unit = {}
    private var onSelectedTabConfirmed: (AppTopLevelTab) -> Unit = {}
    private var onDpadDown: () -> Boolean = { false }
    private var onTopBarFocusChanged: (Boolean) -> Unit = {}

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

    override fun getItemCount(): Int = tabs.size

    override fun getItemId(position: Int): Long = tabs[position].ordinal.toLong()

    fun updateCallbacks(
        updateSelectedTabOnFocus: Boolean,
        onTabSelected: (AppTopLevelTab) -> Unit,
        onSelectedTabConfirmed: (AppTopLevelTab) -> Unit,
        onDpadDown: () -> Boolean,
        onTopBarFocusChanged: (Boolean) -> Unit
    ) {
        this.updateSelectedTabOnFocus = updateSelectedTabOnFocus
        this.onTabSelected = onTabSelected
        this.onSelectedTabConfirmed = onSelectedTabConfirmed
        this.onDpadDown = onDpadDown
        this.onTopBarFocusChanged = onTopBarFocusChanged
    }

    fun submitTabs(
        newTabs: List<AppTopLevelTab>,
        newSelectedTab: AppTopLevelTab?
    ) {
        val tabsChanged = tabs != newTabs
        if (tabsChanged) {
            tabs.clear()
            tabs.addAll(newTabs)
            selectedTab = newSelectedTab
            notifyDataSetChanged()
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
            notifyItemChanged(position)
        }
    }

    inner class TabViewHolder(
        private val binding: ItemAppTopTabBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        private var boundTab: AppTopLevelTab? = null

        init {
            binding.root.isFocusable = true
            binding.root.isFocusableInTouchMode = false
            binding.root.isClickable = true

            binding.root.setOnClickListener {
                boundTab?.let(::select)
            }

            binding.root.setOnFocusChangeListener { _, hasFocus ->
                updateVisual(animate = true)
                if (!hasFocus) return@setOnFocusChangeListener
                onTopBarFocusChanged(true)
                val tab = boundTab ?: return@setOnFocusChangeListener
                if (updateSelectedTabOnFocus && allowFocusedTabSelection && tab != selectedTab) {
                    select(tab)
                }
                allowFocusedTabSelection = false
            }

            binding.root.setOnKeyListener { _, keyCode, event ->
                if (event.action != KeyEvent.ACTION_DOWN) {
                    return@setOnKeyListener false
                }
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_UP -> true
                    KeyEvent.KEYCODE_DPAD_DOWN -> onDpadDown()
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        val position = bindingAdapterPosition
                        if (position <= 0) {
                            true
                        } else {
                            allowFocusedTabSelection = true
                            false
                        }
                    }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        val position = bindingAdapterPosition
                        if (position == RecyclerView.NO_POSITION || position >= itemCount - 1) {
                            true
                        } else {
                            allowFocusedTabSelection = true
                            false
                        }
                    }
                    else -> false
                }
            }
        }

        fun bind(tab: AppTopLevelTab) {
            boundTab = tab
            binding.tvLabel.text = tab.title
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

            binding.root.setCardBackgroundColor(
                if (focused) Color.WHITE else Color.TRANSPARENT
            )
            binding.tvLabel.setTextColor(
                when {
                    focused -> Color.rgb(17, 20, 24)
                    selected -> Color.WHITE
                    else -> Color.argb(153, 255, 255, 255)
                }
            )
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
}

package com.bbttvv.app.ui.home

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

class DpadGridTestActivity : ComponentActivity() {
    private lateinit var recyclerView: DpadTestRecyclerView
    private val controller = DpadGridController()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        recyclerView = DpadTestRecyclerView(this, controller).apply {
            layoutManager = GridLayoutManager(this@DpadGridTestActivity, SpanCount)
            adapter = DpadTestAdapter(controller, ItemCount)
            descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
            isFocusable = true
            isFocusableInTouchMode = true
            setBackgroundColor(Color.BLACK)
        }
        controller.updateCallbacks(
            DpadGridController.Callbacks(
                onTopEdge = { true },
            )
        )
        controller.attach(recyclerView)
        setContentView(recyclerView)
    }

    override fun onDestroy() {
        controller.detach()
        super.onDestroy()
    }

    fun focusedAdapterPosition(): Int {
        val focused = recyclerView.rootView?.findFocus() ?: return RecyclerView.NO_POSITION
        if (focused === recyclerView) return RecyclerView.NO_POSITION
        return recyclerView.findContainingViewHolder(focused)?.bindingAdapterPosition
            ?: RecyclerView.NO_POSITION
    }

    private companion object {
        private const val ItemCount = 40
        private const val SpanCount = 4
    }
}

private class DpadTestRecyclerView(
    context: Context,
    private val controller: DpadGridController,
) : RecyclerView(context) {
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && event.keyCode in DirectionalKeyCodes) {
            val focused = rootView?.findFocus()
            val position = focused
                ?.let(::findContainingViewHolder)
                ?.bindingAdapterPosition
                ?.takeIf { it != NO_POSITION }
                ?: NO_POSITION
            if (
                controller.handleItemKeyEvent(
                    itemView = focused,
                    position = position,
                    keyCode = event.keyCode,
                    event = event,
                )
            ) {
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private companion object {
        private val DirectionalKeyCodes = setOf(
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT,
        )
    }
}

private class DpadTestAdapter(
    private val controller: DpadGridController,
    private val itemCountValue: Int,
) : RecyclerView.Adapter<DpadTestViewHolder>() {
    private var initialFocusRequested = false

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DpadTestViewHolder {
        val view = TextView(parent.context).apply {
            layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                280,
            )
            gravity = Gravity.CENTER
            isFocusable = true
            isFocusableInTouchMode = true
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.DKGRAY)
        }
        return DpadTestViewHolder(view, controller)
    }

    override fun onBindViewHolder(holder: DpadTestViewHolder, position: Int) {
        holder.bind(position)
    }

    override fun onViewAttachedToWindow(holder: DpadTestViewHolder) {
        super.onViewAttachedToWindow(holder)
        if (!initialFocusRequested && holder.bindingAdapterPosition == 0) {
            initialFocusRequested = true
            holder.itemView.post { holder.itemView.requestFocus() }
        }
    }

    override fun getItemCount(): Int = itemCountValue
}

private class DpadTestViewHolder(
    itemView: View,
    private val controller: DpadGridController,
) : RecyclerView.ViewHolder(itemView) {
    init {
        itemView.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                controller.onItemFocused(bindingAdapterPosition)
            }
        }
    }

    fun bind(position: Int) {
        (itemView as TextView).text = position.toString()
    }
}

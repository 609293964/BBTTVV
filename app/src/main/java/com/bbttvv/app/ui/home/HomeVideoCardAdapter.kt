package com.bbttvv.app.ui.home

import android.view.LayoutInflater
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.annotation.IdRes
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.imageLoader
import coil.load
import coil.request.ImageRequest
import coil.size.Precision
import coil.size.Size
import coil.transform.CircleCropTransformation
import com.bbttvv.app.R
import com.bbttvv.app.core.util.FormatUtils
import com.bbttvv.app.data.model.response.VideoItem
import com.bbttvv.app.databinding.ItemVideoCardBinding
import com.bbttvv.app.ui.components.stableVideoItemKeys
import com.bbttvv.app.ui.components.toHomeVideoCardUiModel

private val avatarCircleCropTransformation = CircleCropTransformation()

internal class HomeVideoCardAdapter(
    private var gridColumnCount: Int,
    private var showHistoryProgressOnly: Boolean = false,
    private var showDanmakuCount: Boolean = true,
    private var fixedItemWidthPx: Int? = null,
    private val onItemClick: (HomeRecommendVideoCardItem) -> Unit,
    private val onItemFocused: (HomeRecommendVideoCardItem, Int) -> Unit,
    private val onTopRowDpadUp: (() -> Boolean)? = null,
    private val onItemMenu: ((HomeRecommendVideoCardItem) -> Unit)? = null,
    private val onItemLongClick: ((HomeRecommendVideoCardItem) -> Unit)? = null,
    private val onItemKeyEvent: ((View, HomeRecommendVideoCardItem, Int, Int, KeyEvent) -> Boolean)? = null,
    private val onBackKeyUp: (() -> Boolean)? = null,
    private val supportingTextProvider: ((HomeRecommendVideoCardItem) -> String?)? = null,
) : ListAdapter<HomeRecommendVideoCardItem, HomeVideoCardAdapter.VideoViewHolder>(VideoDiffCallback) {

    init {
        setHasStableIds(true)
        stateRestorationPolicy = StateRestorationPolicy.PREVENT_WHEN_EMPTY
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoViewHolder {
        val binding = ItemVideoCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VideoViewHolder(binding)
    }

    override fun onBindViewHolder(holder: VideoViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    override fun getItemId(position: Int): Long = getItem(position).stableId

    fun positionOfKey(key: String): Int = currentList.indexOfFirst { it.key == key }

    fun keyAt(position: Int): String? = currentList.getOrNull(position)?.key

    fun preloadRowsAhead(
        recyclerView: RecyclerView,
        position: Int,
        spanCount: Int,
        rowCount: Int,
    ) {
        val preloadPositions = DpadGridPreloadPolicy.positionsAhead(
            position = position,
            itemCount = itemCount,
            spanCount = spanCount,
            rowCount = rowCount,
        ) ?: return

        preloadPositions.forEach { preloadPosition ->
            currentList.getOrNull(preloadPosition)?.let { item ->
                preloadCardImages(recyclerView, item)
            }
        }
    }

    fun updateLayoutOptions(
        gridColumnCount: Int,
        showHistoryProgressOnly: Boolean,
        showDanmakuCount: Boolean = true,
        fixedItemWidthPx: Int?,
    ) {
        val shouldRebind = this.showHistoryProgressOnly != showHistoryProgressOnly ||
            this.showDanmakuCount != showDanmakuCount ||
            this.fixedItemWidthPx != fixedItemWidthPx
        this.gridColumnCount = gridColumnCount
        this.showHistoryProgressOnly = showHistoryProgressOnly
        this.showDanmakuCount = showDanmakuCount
        this.fixedItemWidthPx = fixedItemWidthPx
        if (shouldRebind && itemCount > 0) {
            notifyItemRangeChanged(0, itemCount)
        }
    }

    fun clearVisibleFocusVisualState(recyclerView: RecyclerView) {
        for (index in 0 until recyclerView.childCount) {
            val child = recyclerView.getChildAt(index)
            (recyclerView.getChildViewHolder(child) as? VideoViewHolder)
                ?.clearFocusVisualState()
        }
    }

    private fun preloadCardImages(
        recyclerView: RecyclerView,
        item: HomeRecommendVideoCardItem,
    ) {
        val context = recyclerView.context
        val imageLoader = context.imageLoader
        val uiModel = item.video.toHomeVideoCardUiModel(showHistoryProgressOnly)
        val coverUrl = FormatUtils.buildSizedImageUrl(uiModel.coverUrl, 480, 270)
        if (coverUrl.isNotBlank()) {
            imageLoader.enqueue(
                ImageRequest.Builder(context)
                    .data(coverUrl)
                    .allowHardware(true)
                    .precision(Precision.INEXACT)
                    .size(Size(480, 270))
                    .build()
            )
        }

        val avatarUrl = FormatUtils.buildSizedImageUrl(uiModel.ownerFaceUrl, 64, 64)
        if (avatarUrl.isNotBlank()) {
            imageLoader.enqueue(
                ImageRequest.Builder(context)
                    .data(avatarUrl)
                    .allowHardware(true)
                    .transformations(avatarCircleCropTransformation)
                    .build()
            )
        }
    }

    inner class VideoViewHolder(private val binding: ItemVideoCardBinding) : RecyclerView.ViewHolder(binding.root) {
        private var consumeBackKeyUp = false

        init {
            applyFixedItemWidth()
            if (binding.root.id == View.NO_ID) {
                binding.root.id = View.generateViewId()
            }
            binding.root.isFocusable = true
            binding.root.isFocusableInTouchMode = false
            binding.root.isClickable = true
            binding.root.isLongClickable = onItemLongClick != null

            binding.root.setOnClickListener {
                currentItem()?.let(onItemClick)
            }

            binding.root.setOnLongClickListener {
                val item = currentItem()
                val longClickHandler = onItemLongClick
                if (item != null && longClickHandler != null) {
                    longClickHandler(item)
                    true
                } else {
                    false
                }
            }

            binding.root.setOnFocusChangeListener { _, hasFocus ->
                binding.root.isSelected = hasFocus
                if (hasFocus) {
                    val position = bindingAdapterPosition.takeIf { it != RecyclerView.NO_POSITION } ?: return@setOnFocusChangeListener
                    updateDpadUpTarget(position)
                    currentItem()?.let { onItemFocused(it, position) }
                }
            }

            binding.root.setOnKeyListener { _, keyCode, event ->
                if (isVideoCardBackKey(keyCode)) {
                    when (event.action) {
                        KeyEvent.ACTION_DOWN -> {
                            consumeBackKeyUp = onBackKeyUp != null
                            return@setOnKeyListener consumeBackKeyUp
                        }

                        KeyEvent.ACTION_UP -> {
                            if (consumeBackKeyUp) {
                                consumeBackKeyUp = false
                                return@setOnKeyListener onBackKeyUp?.invoke() == true
                            }
                        }
                    }
                }

                val position = bindingAdapterPosition.takeIf { it != RecyclerView.NO_POSITION }
                val item = currentList.getOrNull(position ?: RecyclerView.NO_POSITION)
                if (position != null && item != null && onItemKeyEvent?.invoke(binding.root, item, position, keyCode, event) == true) {
                    return@setOnKeyListener true
                }

                if (
                    event.action == KeyEvent.ACTION_DOWN &&
                    keyCode == KeyEvent.KEYCODE_MENU
                ) {
                    val item = currentItem()
                    val menuHandler = onItemMenu
                    if (item != null && menuHandler != null) {
                        menuHandler(item)
                        return@setOnKeyListener true
                    }
                }

                if (
                    event.action == KeyEvent.ACTION_DOWN &&
                    keyCode == KeyEvent.KEYCODE_DPAD_UP
                ) {
                    val position = bindingAdapterPosition.takeIf { it != RecyclerView.NO_POSITION }
                        ?: return@setOnKeyListener false
                    if (position >= gridColumnCount) {
                        val targetPosition = position - gridColumnCount
                        val recyclerView = binding.root.findParentRecyclerView()
                        recyclerView?.findViewHolderForAdapterPosition(targetPosition)?.itemView?.let { target ->
                            if (target.requestFocus()) {
                                return@setOnKeyListener true
                            }
                        }
                        recyclerView?.scrollToPosition(targetPosition)
                        recyclerView?.post {
                            recyclerView.findViewHolderForAdapterPosition(targetPosition)?.itemView?.requestFocus()
                        }
                        return@setOnKeyListener recyclerView != null
                    }
                    if (position in 0 until gridColumnCount) {
                        return@setOnKeyListener onTopRowDpadUp?.invoke() == true
                    }
                }
                false
            }
        }

        fun bind(item: HomeRecommendVideoCardItem) {
            applyFixedItemWidth()
            val video = item.video
            val uiModel = video.toHomeVideoCardUiModel(showHistoryProgressOnly)

            binding.tvTitle.text = uiModel.title
            binding.tvSubtitle.text = uiModel.ownerName
            binding.tvPubdate.text = uiModel.pubDateText
            val supportingText = supportingTextProvider?.invoke(item).orEmpty()
            if (supportingText.isBlank()) {
                binding.tvReasonHint.visibility = View.GONE
                binding.tvReasonHint.text = null
            } else {
                binding.tvReasonHint.visibility = View.VISIBLE
                binding.tvReasonHint.text = supportingText
            }

            val coverUrl = FormatUtils.buildSizedImageUrl(uiModel.coverUrl, 480, 270)
            binding.ivCover.loadIfUrlChanged(R.id.tag_cover_url, coverUrl) {
                crossfade(false)
                allowHardware(true)
                precision(Precision.INEXACT)
                size(Size(480, 270))
            }

            val avatarUrl = FormatUtils.buildSizedImageUrl(uiModel.ownerFaceUrl, 64, 64)
            binding.ivAvatar.loadIfUrlChanged(R.id.tag_avatar_url, avatarUrl) {
                crossfade(false)
                allowHardware(true)
                transformations(avatarCircleCropTransformation)
            }

            val isLiveCard = video.aid == 0L && video.cid == 0L && video.duration <= 0

            if (isLiveCard) {
                binding.tvDuration.visibility = View.GONE
                binding.llStats.visibility = View.GONE
                binding.tvProgressLeft.visibility = View.VISIBLE
                binding.tvProgressLeft.text = uiModel.leadingMetaText
            } else {
                binding.tvDuration.visibility = View.VISIBLE
                binding.tvDuration.text = uiModel.durationMetaText
                binding.llStats.visibility = View.VISIBLE
                binding.tvView.text = formatCompact(video.stat.view)
                binding.ivStatDanmaku.visibility = if (showDanmakuCount) View.VISIBLE else View.GONE
                binding.tvDanmaku.visibility = if (showDanmakuCount) View.VISIBLE else View.GONE
                binding.tvDanmaku.text = if (showDanmakuCount) formatCompact(video.stat.danmaku) else null
                binding.tvProgressLeft.visibility = View.GONE
            }
        }

        fun clearFocusVisualState() {
            if (binding.root.isFocused) {
                binding.root.clearFocus()
            }
            binding.root.isSelected = false
            binding.root.refreshDrawableState()
            binding.root.stateListAnimator?.jumpToCurrentState()
            binding.root.animate().cancel()
            binding.root.scaleX = 1f
            binding.root.scaleY = 1f
        }

        private fun currentItem(): HomeRecommendVideoCardItem? {
            val position = bindingAdapterPosition.takeIf { it != RecyclerView.NO_POSITION } ?: return null
            return currentList.getOrNull(position)
        }

        private fun updateDpadUpTarget(position: Int) {
            if (position < gridColumnCount) {
                binding.root.nextFocusUpId = View.NO_ID
                return
            }
            val recyclerView = binding.root.findParentRecyclerView()
            val targetPosition = position - gridColumnCount
            val targetView = recyclerView
                ?.findViewHolderForAdapterPosition(targetPosition)
                ?.itemView
            if (targetView != null) {
                if (targetView.id == View.NO_ID) {
                    targetView.id = View.generateViewId()
                }
                binding.root.nextFocusUpId = targetView.id
            } else {
                binding.root.nextFocusUpId = View.NO_ID
            }
        }

        private fun applyFixedItemWidth() {
            val width = fixedItemWidthPx ?: return
            val params = binding.root.layoutParams
            if (params != null && params.width != width) {
                params.width = width
                binding.root.layoutParams = params
            }
        }
    }

    private fun formatCompact(count: Int): String {
        return if (count >= 10000) {
            val w = count / 10000
            val r = (count % 10000) / 1000
            if (r == 0) "${w}w" else "$w.${r}w"
        } else {
            count.toString()
        }
    }

    object VideoDiffCallback : DiffUtil.ItemCallback<HomeRecommendVideoCardItem>() {
        override fun areItemsTheSame(
            oldItem: HomeRecommendVideoCardItem,
            newItem: HomeRecommendVideoCardItem,
        ): Boolean {
            return oldItem.key == newItem.key
        }

        override fun areContentsTheSame(
            oldItem: HomeRecommendVideoCardItem,
            newItem: HomeRecommendVideoCardItem,
        ): Boolean {
            return oldItem.video == newItem.video
        }
    }
}

private fun ImageView.loadIfUrlChanged(
    @IdRes tagId: Int,
    url: String,
    builder: ImageRequest.Builder.() -> Unit,
) {
    val oldUrl = getTag(tagId) as? String
    if (oldUrl == url) return

    setTag(tagId, url.takeIf { it.isNotBlank() })
    if (url.isBlank()) {
        setImageDrawable(null)
        return
    }

    load(url, builder = builder)
}

private fun View.findParentRecyclerView(): RecyclerView? {
    var currentParent = parent
    while (currentParent is View) {
        if (currentParent is RecyclerView) return currentParent
        currentParent = currentParent.parent
    }
    return null
}

private fun isVideoCardBackKey(keyCode: Int): Boolean {
    return keyCode == KeyEvent.KEYCODE_BACK ||
        keyCode == KeyEvent.KEYCODE_ESCAPE ||
        keyCode == KeyEvent.KEYCODE_BUTTON_B
}

internal data class HomeRecommendVideoCardItem(
    val key: String,
    val stableId: Long,
    val video: VideoItem,
)

internal fun List<VideoItem>.toHomeRecommendVideoCardItems(): List<HomeRecommendVideoCardItem> {
    val keys = stableVideoItemKeys()
    return mapIndexed { index, video ->
        val key = keys[index]
        HomeRecommendVideoCardItem(
            key = key,
            stableId = stableLongHash(key),
            video = video,
        )
    }
}

private fun stableLongHash(value: String): Long {
    var hash = 1125899906842597L
    for (char in value) {
        hash = 31 * hash + char.code
    }
    return hash and Long.MAX_VALUE
}

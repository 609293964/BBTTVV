package com.bbttvv.app.ui.home

import android.util.Log
import android.view.LayoutInflater
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.annotation.IdRes
import androidx.core.graphics.toColorInt
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import android.graphics.Color
import coil.dispose
import coil.imageLoader
import coil.load
import coil.request.ImageRequest
import coil.size.Precision
import coil.size.Size
import coil.transform.CircleCropTransformation
import com.bbttvv.app.BuildConfig
import com.bbttvv.app.R
import com.bbttvv.app.core.store.SettingsManager
import com.bbttvv.app.core.util.FormatUtils
import com.bbttvv.app.data.model.response.VideoItem
import com.bbttvv.app.databinding.ItemVideoCardBinding
import com.bbttvv.app.ui.components.toHomeVideoCardUiModel
import com.bbttvv.app.ui.focus.GridFocusDebugLog

private val avatarCircleCropTransformation = CircleCropTransformation()

internal object HomeVideoCardImageRetryPolicy {
    private val retryDelaysMs = longArrayOf(1_500L, 5_000L)

    fun delayMillis(completedAttempt: Int): Long? {
        return retryDelaysMs.getOrNull(completedAttempt)
    }
}

private data class FailedImageRequestMarker(
    val url: String,
    val attempt: Int,
)

private object BlankImageRequestMarker

private val lightStrokeColorStateList = android.content.res.ColorStateList(
    arrayOf(
        intArrayOf(android.R.attr.state_focused),
        intArrayOf(android.R.attr.state_selected),
        intArrayOf(android.R.attr.state_pressed),
        intArrayOf()
    ),
    intArrayOf(
        "#FB7299".toColorInt(), // focused: brand pink
        "#FF6699".toColorInt(), // selected: pink
        "#FB7299".toColorInt(), // pressed: brand pink
        Color.TRANSPARENT           // default: transparent
    )
)

private val darkStrokeColorStateList = android.content.res.ColorStateList(
    arrayOf(
        intArrayOf(android.R.attr.state_focused),
        intArrayOf(android.R.attr.state_selected),
        intArrayOf(android.R.attr.state_pressed),
        intArrayOf()
    ),
    intArrayOf(
        "#FFFFFF".toColorInt(), // focused: white
        "#FFFFFF".toColorInt(), // selected: visual focus anchor
        "#FFFFFF".toColorInt(), // pressed: white
        Color.TRANSPARENT           // default: transparent
    )
)

internal class HomeVideoCardAdapter(
    private var showHistoryProgressOnly: Boolean = false,
    private var showDanmakuCount: Boolean = true,
    private var fixedItemWidthPx: Int? = null,
    private val onItemClick: (HomeRecommendVideoCardItem) -> Unit,
    private val onItemFocused: (HomeRecommendVideoCardItem, Int) -> Unit,
    private val onItemMenu: ((HomeRecommendVideoCardItem) -> Unit)? = null,
    private val onItemLongClick: ((HomeRecommendVideoCardItem) -> Unit)? = null,
    private val onItemKeyEvent: ((View, HomeRecommendVideoCardItem, Int, Int, KeyEvent) -> Boolean)? = null,
    private val onBackKeyUp: (() -> Boolean)? = null,
    private val supportingTextProvider: ((HomeRecommendVideoCardItem) -> String?)? = null,
) : ListAdapter<HomeRecommendVideoCardItem, HomeVideoCardAdapter.VideoViewHolder>(VideoDiffCallback) {
    private var visualFocusKey: String? = null
    private var cardPalette: HomeVideoCardPalette? = null

    init {
        setHasStableIds(true)
        stateRestorationPolicy = StateRestorationPolicy.PREVENT_WHEN_EMPTY
    }

    override fun getItemViewType(position: Int): Int = HomeRecyclerViewTypes.VideoCard

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoViewHolder {
        val binding = ItemVideoCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        val palette = cardPalette ?: HomeVideoCardPalette.resolve(parent.context).also {
            cardPalette = it
        }
        return VideoViewHolder(binding, palette)
    }

    override fun onBindViewHolder(holder: VideoViewHolder, position: Int) {
        GridFocusDebugLog.d {
            "HomeVideoCardAdapter.onBindViewHolder adapterPosition=$position " +
                "itemKey=${getItem(position).key} itemCount=$itemCount " +
                "holderFocused=${holder.itemView.hasFocus()}"
        }
        holder.bind(
            item = getItem(position),
            showHistoryProgressOnly = showHistoryProgressOnly,
            showDanmakuCount = showDanmakuCount,
            fixedItemWidthPx = fixedItemWidthPx,
            onItemClick = onItemClick,
            onItemFocused = onItemFocused,
            onItemMenu = onItemMenu,
            onItemLongClick = onItemLongClick,
            onItemKeyEvent = onItemKeyEvent,
            onBackKeyUp = onBackKeyUp,
            supportingTextProvider = supportingTextProvider,
            isLogicallySelected = getItem(position).key == visualFocusKey,
        )
    }

    override fun onBindViewHolder(
        holder: VideoViewHolder,
        position: Int,
        payloads: MutableList<Any>,
    ) {
        val cardPayload = payloads
            .filterIsInstance<HomeVideoCardPayload>()
            .reduceOrNull(HomeVideoCardPayload::merge)
        if (cardPayload != null) {
            holder.bindPayload(
                item = getItem(position),
                payload = cardPayload,
                showHistoryProgressOnly = showHistoryProgressOnly,
                showDanmakuCount = showDanmakuCount,
                supportingTextProvider = supportingTextProvider,
            )
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    override fun onViewRecycled(holder: VideoViewHolder) {
        GridFocusDebugLog.d {
            "HomeVideoCardAdapter.onViewRecycled adapterPosition=${holder.bindingAdapterPosition} " +
                "itemKey=${holder.boundKey} holderFocused=${holder.itemView.hasFocus()} " +
                GridFocusDebugLog.view(holder.itemView.rootView?.findFocus())
        }
        holder.clearBinding()
        super.onViewRecycled(holder)
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
        showHistoryProgressOnly: Boolean,
        showDanmakuCount: Boolean = true,
        fixedItemWidthPx: Int?,
    ) {
        val shouldRebind = this.showHistoryProgressOnly != showHistoryProgressOnly ||
            this.showDanmakuCount != showDanmakuCount ||
            this.fixedItemWidthPx != fixedItemWidthPx
        this.showHistoryProgressOnly = showHistoryProgressOnly
        this.showDanmakuCount = showDanmakuCount
        this.fixedItemWidthPx = fixedItemWidthPx
        if (shouldRebind && itemCount > 0) {
            GridFocusDebugLog.d {
                "HomeVideoCardAdapter.updateLayoutOptions shouldRebind=true " +
                    "notifyItemRangeChanged=true itemCount=$itemCount " +
                    "showHistoryProgressOnly=$showHistoryProgressOnly " +
                    "showDanmakuCount=$showDanmakuCount fixedItemWidthPx=$fixedItemWidthPx"
            }
            notifyItemRangeChanged(0, itemCount)
        } else {
            GridFocusDebugLog.d {
                "HomeVideoCardAdapter.updateLayoutOptions shouldRebind=$shouldRebind " +
                    "notifyItemRangeChanged=false itemCount=$itemCount"
            }
        }
    }

    fun clearVisibleFocusVisualState(recyclerView: RecyclerView) {
        visualFocusKey = null
        for (index in 0 until recyclerView.childCount) {
            val child = recyclerView.getChildAt(index)
            (recyclerView.getChildViewHolder(child) as? VideoViewHolder)
                ?.let { holder ->
                    holder.setLogicalSelected(false)
                    holder.clearFocusVisualState()
                }
        }
    }

    fun setVisualFocusKey(recyclerView: RecyclerView, key: String?) {
        if (visualFocusKey == key) return
        visualFocusKey = key
        for (index in 0 until recyclerView.childCount) {
            val child = recyclerView.getChildAt(index)
            val holder = recyclerView.getChildViewHolder(child) as? VideoViewHolder ?: continue
            holder.setLogicalSelected(holder.boundKey == key)
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
                    .size(Size(64, 64))
                    .transformations(avatarCircleCropTransformation)
                    .build()
            )
        }
    }

    class VideoViewHolder(
        private val binding: ItemVideoCardBinding,
        private val palette: HomeVideoCardPalette,
    ) : RecyclerView.ViewHolder(binding.root) {
        private var consumeBackKeyUp = false
        private var boundItem: HomeRecommendVideoCardItem? = null
        private var boundOnItemClick: ((HomeRecommendVideoCardItem) -> Unit)? = null
        private var boundOnItemFocused: ((HomeRecommendVideoCardItem, Int) -> Unit)? = null
        private var boundOnItemMenu: ((HomeRecommendVideoCardItem) -> Unit)? = null
        private var boundOnItemLongClick: ((HomeRecommendVideoCardItem) -> Unit)? = null
        private var boundOnItemKeyEvent: ((View, HomeRecommendVideoCardItem, Int, Int, KeyEvent) -> Boolean)? = null
        private var boundOnBackKeyUp: (() -> Boolean)? = null
        private var boundIsLogicallySelected: Boolean = false
        val boundKey: String?
            get() = boundItem?.key

        init {
            if (binding.root.id == View.NO_ID) {
                binding.root.id = View.generateViewId()
            }
            binding.root.isFocusable = true
            binding.root.isFocusableInTouchMode = false
            binding.root.isClickable = true

            binding.root.setOnClickListener {
                boundItem?.let { item -> boundOnItemClick?.invoke(item) }
            }

            binding.root.setOnLongClickListener {
                val item = boundItem
                val longClickHandler = boundOnItemLongClick
                if (item != null && longClickHandler != null) {
                    longClickHandler(item)
                    true
                } else {
                    false
                }
            }

            binding.root.setOnFocusChangeListener { _, hasFocus ->
                applySelectedState(hasFocus || boundIsLogicallySelected)
                val position = bindingAdapterPosition.takeIf { it != RecyclerView.NO_POSITION }
                val item = boundItem
                GridFocusDebugLog.d {
                    "HomeVideoCardAdapter.VideoViewHolder.onFocusChanged hasFocus=$hasFocus " +
                        "adapterPosition=${position ?: RecyclerView.NO_POSITION} itemKey=${item?.key} " +
                        GridFocusDebugLog.view(binding.root.rootView?.findFocus())
                }
                if (hasFocus) {
                    val focusedPosition = position ?: return@setOnFocusChangeListener
                    if (BuildConfig.DEBUG) {
                        Log.d("HomeFocus", "Card focused: pos=$focusedPosition key=${item?.key}")
                    }
                    if (item != null) {
                        boundOnItemFocused?.invoke(item, focusedPosition)
                    }
                }
            }

            binding.root.setOnKeyListener { _, keyCode, event ->
                if (isVideoCardBackKey(keyCode)) {
                    when (event.action) {
                        KeyEvent.ACTION_DOWN -> {
                            consumeBackKeyUp = boundOnBackKeyUp != null
                            return@setOnKeyListener consumeBackKeyUp
                        }

                        KeyEvent.ACTION_UP -> {
                            if (consumeBackKeyUp) {
                                consumeBackKeyUp = false
                                return@setOnKeyListener boundOnBackKeyUp?.invoke() == true
                            }
                        }
                    }
                }

                val position = bindingAdapterPosition.takeIf { it != RecyclerView.NO_POSITION }
                val item = boundItem
                if (position != null && item != null) {
                    val controllerHandled =
                        boundOnItemKeyEvent?.invoke(binding.root, item, position, keyCode, event) == true
                    if (controllerHandled) {
                        return@setOnKeyListener true
                    }
                }

                if (
                    event.action == KeyEvent.ACTION_DOWN &&
                    keyCode == KeyEvent.KEYCODE_MENU
                ) {
                    val item = boundItem
                    val menuHandler = boundOnItemMenu
                    if (item != null && menuHandler != null) {
                        menuHandler(item)
                        return@setOnKeyListener true
                    }
                }
                false
            }
        }

        fun bind(
            item: HomeRecommendVideoCardItem,
            showHistoryProgressOnly: Boolean,
            showDanmakuCount: Boolean,
            fixedItemWidthPx: Int?,
            onItemClick: (HomeRecommendVideoCardItem) -> Unit,
            onItemFocused: (HomeRecommendVideoCardItem, Int) -> Unit,
            onItemMenu: ((HomeRecommendVideoCardItem) -> Unit)?,
            onItemLongClick: ((HomeRecommendVideoCardItem) -> Unit)?,
            onItemKeyEvent: ((View, HomeRecommendVideoCardItem, Int, Int, KeyEvent) -> Boolean)?,
            onBackKeyUp: (() -> Boolean)?,
            supportingTextProvider: ((HomeRecommendVideoCardItem) -> String?)?,
            isLogicallySelected: Boolean,
        ) {
            boundItem = item
            boundOnItemClick = onItemClick
            boundOnItemFocused = onItemFocused
            boundOnItemMenu = onItemMenu
            boundOnItemLongClick = onItemLongClick
            boundOnItemKeyEvent = onItemKeyEvent
            boundOnBackKeyUp = onBackKeyUp
            boundIsLogicallySelected = isLogicallySelected

            binding.root.isLongClickable = onItemLongClick != null
            applySelectedState(binding.root.isFocused || boundIsLogicallySelected)
            applyFixedItemWidth(fixedItemWidthPx)
            val video = item.video
            val uiModel = video.toHomeVideoCardUiModel(showHistoryProgressOnly)

            binding.root.setStrokeColor(palette.strokeColors)
            binding.tvTitle.setTextColor(palette.titleColor)
            binding.tvSubtitle.setTextColor(palette.subtitleColor)
            binding.tvPubdate.setTextColor(palette.subtitleColor)
            binding.tvReasonHint.setTextColor(palette.reasonColor)

            binding.tvTitle.text = uiModel.title
            binding.tvSubtitle.text = uiModel.ownerName

            bindCover(uiModel.coverUrl)

            // Keep the title area lightweight on low-end TV devices: no per-card blur work.
            binding.clInfo.setTag(null)
            binding.clInfo.setBackgroundColor(palette.infoBackgroundColor)

            bindAvatar(uiModel.ownerFaceUrl)

            bindMetadata(
                item = item,
                showHistoryProgressOnly = showHistoryProgressOnly,
                showDanmakuCount = showDanmakuCount,
                supportingTextProvider = supportingTextProvider,
            )
            bindAccessibilityDescription(item, showHistoryProgressOnly, showDanmakuCount)
        }

        fun bindPayload(
            item: HomeRecommendVideoCardItem,
            payload: HomeVideoCardPayload,
            showHistoryProgressOnly: Boolean,
            showDanmakuCount: Boolean,
            supportingTextProvider: ((HomeRecommendVideoCardItem) -> String?)?,
        ) {
            boundItem = item
            val uiModel = item.video.toHomeVideoCardUiModel(showHistoryProgressOnly)
            if (payload.titleChanged) {
                binding.tvTitle.text = uiModel.title
            }
            if (payload.ownerChanged) {
                binding.tvSubtitle.text = uiModel.ownerName
                bindAvatar(uiModel.ownerFaceUrl)
            }
            if (payload.coverChanged) {
                bindCover(uiModel.coverUrl)
            }
            if (payload.metadataChanged) {
                bindMetadata(
                    item = item,
                    showHistoryProgressOnly = showHistoryProgressOnly,
                    showDanmakuCount = showDanmakuCount,
                    supportingTextProvider = supportingTextProvider,
                )
            } else {
                bindSupportingText(item, supportingTextProvider)
            }
            bindAccessibilityDescription(item, showHistoryProgressOnly, showDanmakuCount)
        }

        private fun bindCover(rawUrl: String) {
            val coverUrl = FormatUtils.buildSizedImageUrl(rawUrl, 480, 270)
            binding.ivCover.loadIfUrlChanged(
                tagId = R.id.tag_cover_url,
                url = coverUrl,
                placeholderResId = R.drawable.video_card_cover_placeholder,
            ) {
                crossfade(false)
                allowHardware(true)
                precision(Precision.INEXACT)
                size(Size(480, 270))
            }
        }

        private fun bindAvatar(rawUrl: String) {
            val avatarUrl = FormatUtils.buildSizedImageUrl(rawUrl, 64, 64)
            binding.ivAvatar.loadIfUrlChanged(
                tagId = R.id.tag_avatar_url,
                url = avatarUrl,
                placeholderResId = R.drawable.video_card_avatar_placeholder,
            ) {
                crossfade(false)
                allowHardware(true)
                size(Size(64, 64))
                transformations(avatarCircleCropTransformation)
            }
        }

        private fun bindMetadata(
            item: HomeRecommendVideoCardItem,
            showHistoryProgressOnly: Boolean,
            showDanmakuCount: Boolean,
            supportingTextProvider: ((HomeRecommendVideoCardItem) -> String?)?,
        ) {
            val video = item.video
            val uiModel = video.toHomeVideoCardUiModel(showHistoryProgressOnly)
            binding.tvPubdate.text = uiModel.pubDateText
            bindSupportingText(item, supportingTextProvider)

            val isBangumiCard = video.bvid.startsWith("ss") || video.bvid.startsWith("ep")
            val isLiveCard = !isBangumiCard && video.aid == 0L && video.cid == 0L && video.duration <= 0

            if (isBangumiCard) {
                binding.tvDuration.visibility = View.VISIBLE
                binding.tvDuration.text = uiModel.durationMetaText
                binding.llStats.visibility = View.GONE
                binding.tvProgressLeft.visibility = View.GONE
            } else if (isLiveCard) {
                binding.tvDuration.visibility = View.GONE
                binding.llStats.visibility = View.GONE
                binding.tvProgressLeft.visibility = View.VISIBLE
                binding.tvProgressLeft.text = uiModel.leadingMetaText
            } else {
                binding.tvDuration.visibility = View.VISIBLE
                binding.tvDuration.text = uiModel.durationMetaText
                binding.llStats.visibility = View.VISIBLE
                binding.tvView.text = FormatUtils.formatStat(video.stat.view.toLong())
                binding.ivStatDanmaku.visibility = if (showDanmakuCount) View.VISIBLE else View.GONE
                binding.tvDanmaku.visibility = if (showDanmakuCount) View.VISIBLE else View.GONE
                binding.tvDanmaku.text = if (showDanmakuCount) {
                    FormatUtils.formatStat(video.stat.danmaku.toLong())
                } else {
                    null
                }
                binding.tvProgressLeft.visibility = View.GONE
            }
        }

        private fun bindAccessibilityDescription(
            item: HomeRecommendVideoCardItem,
            showHistoryProgressOnly: Boolean,
            showDanmakuCount: Boolean,
        ) {
            val video = item.video
            val uiModel = video.toHomeVideoCardUiModel(showHistoryProgressOnly)
            val context = binding.root.context
            val isBangumiCard = video.bvid.startsWith("ss") || video.bvid.startsWith("ep")
            val isLiveCard = !isBangumiCard && video.aid == 0L && video.cid == 0L && video.duration <= 0
            val details = buildList {
                if (uiModel.ownerName.isNotBlank()) {
                    add(context.getString(R.string.video_card_accessibility_owner, uiModel.ownerName))
                }
                when {
                    isLiveCard -> {
                        uiModel.leadingMetaText.takeIf(String::isNotBlank)?.let(::add)
                    }

                    isBangumiCard -> {
                        uiModel.durationMetaText.takeIf(String::isNotBlank)?.let(::add)
                    }

                    else -> {
                        if (video.stat.view > 0) {
                            add(
                                context.getString(
                                    R.string.video_card_accessibility_play_count,
                                    FormatUtils.formatStat(video.stat.view.toLong()),
                                )
                            )
                        }
                        if (showDanmakuCount && video.stat.danmaku > 0) {
                            add(
                                context.getString(
                                    R.string.video_card_accessibility_danmaku_count,
                                    FormatUtils.formatStat(video.stat.danmaku.toLong()),
                                )
                            )
                        }
                        if (video.duration > 0 && uiModel.durationMetaText.isNotBlank()) {
                            add(uiModel.durationMetaText)
                        }
                    }
                }
            }
            binding.root.contentDescription = if (details.isEmpty()) {
                uiModel.title
            } else {
                context.getString(
                    R.string.video_card_accessibility_description,
                    uiModel.title,
                    details.joinToString(separator = "，"),
                )
            }
        }

        private fun bindSupportingText(
            item: HomeRecommendVideoCardItem,
            supportingTextProvider: ((HomeRecommendVideoCardItem) -> String?)?,
        ) {
            val supportingText = supportingTextProvider?.invoke(item).orEmpty()
            if (supportingText.isBlank()) {
                binding.tvReasonHint.visibility = View.GONE
                binding.tvReasonHint.text = null
            } else {
                binding.tvReasonHint.visibility = View.VISIBLE
                binding.tvReasonHint.text = supportingText
            }
        }

        fun clearBinding() {
            consumeBackKeyUp = false
            boundItem = null
            boundOnItemClick = null
            boundOnItemFocused = null
            boundOnItemMenu = null
            boundOnItemLongClick = null
            boundOnItemKeyEvent = null
            boundOnBackKeyUp = null
            boundIsLogicallySelected = false
            binding.root.isLongClickable = false
            applySelectedState(false)

            binding.ivCover.dispose()
            binding.ivAvatar.dispose()
            binding.ivCover.setTag(R.id.tag_cover_url, null)
            binding.ivAvatar.setTag(R.id.tag_avatar_url, null)
            binding.ivCover.setImageResource(R.drawable.video_card_cover_placeholder)
            binding.ivAvatar.setImageResource(R.drawable.video_card_avatar_placeholder)
            binding.root.contentDescription = null

            binding.clInfo.setTag(null)
            binding.clInfo.background = null
        }

        fun clearFocusVisualState() {
            val hasFocus = binding.root.isFocused
            val hasSelectedState = binding.root.isSelected
            val hasScaleState = binding.root.scaleX != 1f || binding.root.scaleY != 1f
            if (!hasFocus && !hasSelectedState && !hasScaleState) {
                return
            }
            if (hasFocus) {
                binding.root.clearFocus()
            }
            if (hasSelectedState) {
                binding.root.isSelected = false
                binding.root.refreshDrawableState()
            }
            binding.root.stateListAnimator?.jumpToCurrentState()
            binding.root.animate().cancel()
            if (hasScaleState) {
                binding.root.scaleX = 1f
                binding.root.scaleY = 1f
            }
        }

        fun setLogicalSelected(isSelected: Boolean) {
            boundIsLogicallySelected = isSelected
            applySelectedState(binding.root.isFocused || boundIsLogicallySelected)
        }

        private fun applySelectedState(selected: Boolean) {
            if (binding.root.isSelected != selected) {
                binding.root.isSelected = selected
                binding.root.refreshDrawableState()
            }
        }

        private fun applyFixedItemWidth(fixedItemWidthPx: Int?) {
            val width = fixedItemWidthPx ?: ViewGroup.LayoutParams.MATCH_PARENT
            val params = binding.root.layoutParams
            if (params != null && params.width != width) {
                params.width = width
                binding.root.layoutParams = params
            }
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

        override fun getChangePayload(
            oldItem: HomeRecommendVideoCardItem,
            newItem: HomeRecommendVideoCardItem,
        ): Any? {
            return oldItem.video.buildCardPayload(newItem.video)
        }
    }
}

internal data class HomeVideoCardPayload(
    val coverChanged: Boolean,
    val titleChanged: Boolean,
    val ownerChanged: Boolean,
    val metadataChanged: Boolean,
) {
    fun merge(other: HomeVideoCardPayload): HomeVideoCardPayload {
        return HomeVideoCardPayload(
            coverChanged = coverChanged || other.coverChanged,
            titleChanged = titleChanged || other.titleChanged,
            ownerChanged = ownerChanged || other.ownerChanged,
            metadataChanged = metadataChanged || other.metadataChanged,
        )
    }
}

internal data class HomeVideoCardPalette(
    val strokeColors: android.content.res.ColorStateList,
    val titleColor: Int,
    val subtitleColor: Int,
    val reasonColor: Int,
    val infoBackgroundColor: Int,
) {
    companion object {
        fun resolve(context: android.content.Context): HomeVideoCardPalette {
            val isLightTheme =
                SettingsManager.getThemeModeSync(context) == SettingsManager.ThemeMode.LIGHT
            return if (isLightTheme) {
                HomeVideoCardPalette(
                    strokeColors = lightStrokeColorStateList,
                    titleColor = Color.rgb(24, 25, 28),
                    subtitleColor = Color.rgb(97, 102, 109),
                    reasonColor = Color.rgb(224, 64, 96),
                    infoBackgroundColor = Color.rgb(242, 244, 247),
                )
            } else {
                HomeVideoCardPalette(
                    strokeColors = darkStrokeColorStateList,
                    titleColor = Color.rgb(244, 246, 248),
                    subtitleColor = Color.rgb(153, 153, 153),
                    reasonColor = Color.argb(207, 229, 237, 247),
                    infoBackgroundColor = Color.argb(204, 31, 31, 33),
                )
            }
        }
    }
}

private fun VideoItem.buildCardPayload(other: VideoItem): HomeVideoCardPayload? {
    if (this == other) return null
    val supportedChange = copy(
        pic = other.pic,
        title = other.title,
        owner = other.owner,
        stat = other.stat,
        duration = other.duration,
        progress = other.progress,
        view_at = other.view_at,
        pubdate = other.pubdate,
        collectionSubtitle = other.collectionSubtitle,
    ) == other
    if (!supportedChange) return null
    return HomeVideoCardPayload(
        coverChanged = pic != other.pic,
        titleChanged = title != other.title,
        ownerChanged = owner != other.owner,
        metadataChanged = stat != other.stat ||
            duration != other.duration ||
            progress != other.progress ||
            view_at != other.view_at ||
            pubdate != other.pubdate ||
            collectionSubtitle != other.collectionSubtitle,
    )
}

private fun ImageView.loadIfUrlChanged(
    @IdRes tagId: Int,
    url: String,
    placeholderResId: Int,
    retryAttempt: Int = 0,
    builder: ImageRequest.Builder.() -> Unit,
) {
    val oldUrl = getTag(tagId) as? String
    if (oldUrl == url) return

    if (url.isBlank()) {
        setTag(tagId, BlankImageRequestMarker)
        dispose()
        setImageResource(placeholderResId)
        return
    }

    setTag(tagId, url)

    load(url) {
        placeholder(placeholderResId)
        error(placeholderResId)
        fallback(placeholderResId)
        builder()
        listener(
            onError = { _, _ ->
                // A failed URL is not a successfully bound URL. A distinct marker
                // lets later binds retry without allowing an old delayed retry to
                // overwrite a holder that has since been rebound to another item.
                if (getTag(tagId) == url) {
                    val failedMarker = FailedImageRequestMarker(url, retryAttempt)
                    setTag(tagId, failedMarker)
                    HomeVideoCardImageRetryPolicy.delayMillis(retryAttempt)?.let { delayMs ->
                        postDelayed(
                            {
                                if (isAttachedToWindow && getTag(tagId) === failedMarker) {
                                    loadIfUrlChanged(
                                        tagId = tagId,
                                        url = url,
                                        placeholderResId = placeholderResId,
                                        retryAttempt = retryAttempt + 1,
                                        builder = builder,
                                    )
                                }
                            },
                            delayMs,
                        )
                    }
                }
            }
        )
    }
}

private fun isVideoCardBackKey(keyCode: Int): Boolean {
    return keyCode == KeyEvent.KEYCODE_BACK ||
        keyCode == KeyEvent.KEYCODE_ESCAPE ||
        keyCode == KeyEvent.KEYCODE_BUTTON_B
}

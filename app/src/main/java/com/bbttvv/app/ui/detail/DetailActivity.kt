package com.bbttvv.app.ui.detail

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.isDebugInspectorInfoEnabled
import androidx.tv.material3.MaterialTheme
import com.bbttvv.app.data.model.response.Owner
import com.bbttvv.app.data.model.response.Stat
import com.bbttvv.app.data.model.response.VideoItem
import com.bbttvv.app.data.repository.VideoDetailRepository
import com.bbttvv.app.ui.focus.LocalTvFocusEscapeGuard
import com.bbttvv.app.ui.focus.TvFocusEscapeGuard
import com.bbttvv.app.ui.theme.AppTheme

private const val DetailActivityExtraBvid = "com.bbttvv.app.extra.BVID"
private const val DetailActivityExtraAid = "com.bbttvv.app.extra.AID"
private const val DetailActivityExtraCid = "com.bbttvv.app.extra.CID"
private const val DetailActivityExtraTitle = "com.bbttvv.app.extra.TITLE"
private const val DetailActivityExtraCoverUrl = "com.bbttvv.app.extra.COVER_URL"
private const val DetailActivityExtraOwnerMid = "com.bbttvv.app.extra.OWNER_MID"
private const val DetailActivityExtraOwnerName = "com.bbttvv.app.extra.OWNER_NAME"
private const val DetailActivityExtraOwnerFace = "com.bbttvv.app.extra.OWNER_FACE"
private const val DetailActivityExtraDuration = "com.bbttvv.app.extra.DURATION"
private const val DetailActivityExtraPubdate = "com.bbttvv.app.extra.PUBDATE"
private const val DetailActivityExtraStatView = "com.bbttvv.app.extra.STAT_VIEW"
private const val DetailActivityExtraStatDanmaku = "com.bbttvv.app.extra.STAT_DANMAKU"
private const val DetailActivityExtraStatReply = "com.bbttvv.app.extra.STAT_REPLY"
private const val DetailActivityExtraStatLike = "com.bbttvv.app.extra.STAT_LIKE"
private const val DetailActivityExtraStatCoin = "com.bbttvv.app.extra.STAT_COIN"
private const val DetailActivityExtraStatFavorite = "com.bbttvv.app.extra.STAT_FAVORITE"
private const val DetailActivityExtraStatShare = "com.bbttvv.app.extra.STAT_SHARE"

class DetailActivity : ComponentActivity() {
    private val tvFocusEscapeGuard = TvFocusEscapeGuard()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isDebugInspectorInfoEnabled = false

        val initialBvid = intent.getStringExtra(DetailActivityExtraBvid).orEmpty().trim()
        if (initialBvid.isBlank()) {
            finish()
            return
        }
        intent.toDetailPreviewVideo(initialBvid)?.let(VideoDetailRepository::cacheVideoPreview)

        setContent {
            AppTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    CompositionLocalProvider(
                        LocalTvFocusEscapeGuard provides tvFocusEscapeGuard,
                    ) {
                        VideoDetailNavGraph(
                            initialBvid = initialBvid,
                            openMode = DetailOpenMode.StandaloneActivity,
                            onFinish = { finish() },
                        )
                    }
                }
            }
        }
        suppressDetailActivityTransition()
    }

    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (tvFocusEscapeGuard.handleKeyEvent(event, currentFocus)) {
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun finish() {
        super.finish()
        suppressDetailActivityTransition()
    }

    companion object {
        fun createIntent(context: Context, bvid: String): Intent {
            return Intent(context, DetailActivity::class.java)
                .putExtra(DetailActivityExtraBvid, bvid.trim())
                .addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
        }

        fun createIntent(context: Context, video: VideoItem): Intent {
            return createIntent(context, video.bvid)
                .putExtra(DetailActivityExtraAid, video.aid)
                .putExtra(DetailActivityExtraCid, video.cid)
                .putExtra(DetailActivityExtraDuration, video.duration)
                .putExtra(DetailActivityExtraPubdate, video.pubdate)
                .putExtra(DetailActivityExtraStatView, video.stat.view)
                .putExtra(DetailActivityExtraStatDanmaku, video.stat.danmaku)
                .putExtra(DetailActivityExtraStatReply, video.stat.reply)
                .putExtra(DetailActivityExtraStatLike, video.stat.like)
                .putExtra(DetailActivityExtraStatCoin, video.stat.coin)
                .putExtra(DetailActivityExtraStatFavorite, video.stat.favorite)
                .putExtra(DetailActivityExtraStatShare, video.stat.share)
                .putTrimmedExtra(DetailActivityExtraTitle, video.title)
                .putTrimmedExtra(DetailActivityExtraCoverUrl, video.pic)
                .putExtra(DetailActivityExtraOwnerMid, video.owner.mid)
                .putTrimmedExtra(DetailActivityExtraOwnerName, video.owner.name)
                .putTrimmedExtra(DetailActivityExtraOwnerFace, video.owner.face)
        }
    }
}

fun Context.startVideoDetailActivity(bvid: String) {
    val safeBvid = bvid.trim()
    if (safeBvid.isBlank()) return

    val intent = DetailActivity.createIntent(this, safeBvid).apply {
        if (this@startVideoDetailActivity !is Activity) {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
    startActivity(intent)
    (this as? Activity)?.suppressDetailActivityTransition()
}

fun Context.startVideoDetailActivity(video: VideoItem) {
    val safeBvid = video.bvid.trim()
    if (safeBvid.isBlank()) return

    VideoDetailRepository.cacheVideoPreview(video)
    val intent = DetailActivity.createIntent(this, video.copy(bvid = safeBvid)).apply {
        if (this@startVideoDetailActivity !is Activity) {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
    startActivity(intent)
    (this as? Activity)?.suppressDetailActivityTransition()
}

private fun Intent.toDetailPreviewVideo(initialBvid: String): VideoItem? {
    val title = getTrimmedStringExtra(DetailActivityExtraTitle)
    val coverUrl = getTrimmedStringExtra(DetailActivityExtraCoverUrl)
    val ownerName = getTrimmedStringExtra(DetailActivityExtraOwnerName)
    val ownerFace = getTrimmedStringExtra(DetailActivityExtraOwnerFace)
    val aid = getLongExtra(DetailActivityExtraAid, 0L).coerceAtLeast(0L)
    val cid = getLongExtra(DetailActivityExtraCid, 0L).coerceAtLeast(0L)
    val ownerMid = getLongExtra(DetailActivityExtraOwnerMid, 0L).coerceAtLeast(0L)
    val hasPreview = title.isNotBlank() ||
        coverUrl.isNotBlank() ||
        ownerName.isNotBlank() ||
        ownerFace.isNotBlank() ||
        aid > 0L ||
        cid > 0L
    if (!hasPreview) return null

    return VideoItem(
        bvid = initialBvid,
        aid = aid,
        cid = cid,
        title = title,
        pic = coverUrl,
        owner = Owner(
            mid = ownerMid,
            name = ownerName,
            face = ownerFace
        ),
        stat = Stat(
            view = getIntExtra(DetailActivityExtraStatView, 0).coerceAtLeast(0),
            danmaku = getIntExtra(DetailActivityExtraStatDanmaku, 0).coerceAtLeast(0),
            reply = getIntExtra(DetailActivityExtraStatReply, 0).coerceAtLeast(0),
            like = getIntExtra(DetailActivityExtraStatLike, 0).coerceAtLeast(0),
            coin = getIntExtra(DetailActivityExtraStatCoin, 0).coerceAtLeast(0),
            favorite = getIntExtra(DetailActivityExtraStatFavorite, 0).coerceAtLeast(0),
            share = getIntExtra(DetailActivityExtraStatShare, 0).coerceAtLeast(0)
        ),
        duration = getIntExtra(DetailActivityExtraDuration, 0).coerceAtLeast(0),
        pubdate = getLongExtra(DetailActivityExtraPubdate, 0L).coerceAtLeast(0L)
    )
}

private fun Intent.putTrimmedExtra(name: String, value: String): Intent {
    val trimmed = value.trim()
    if (trimmed.isNotBlank()) {
        putExtra(name, trimmed)
    }
    return this
}

private fun Intent.getTrimmedStringExtra(name: String): String {
    return getStringExtra(name).orEmpty().trim()
}

private fun Activity.suppressDetailActivityTransition() {
    if (Build.VERSION.SDK_INT >= 34) {
        overrideActivityTransition(Activity.OVERRIDE_TRANSITION_OPEN, 0, 0)
        overrideActivityTransition(Activity.OVERRIDE_TRANSITION_CLOSE, 0, 0)
    } else {
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }
}

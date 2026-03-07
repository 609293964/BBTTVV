package com.Android.purebilibili.feature.video

import android.os.Bundle
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.purebilibili.data.model.response.Owner
import com.android.purebilibili.data.model.response.RelatedVideo
import com.android.purebilibili.data.model.response.Stat
import com.android.purebilibili.data.model.response.ViewInfo
import com.android.purebilibili.feature.video.screen.VideoContentSection
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VideoContentDanmakuSettingsUiRegressionTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun openingDanmakuSettings_blocksTouchesFromReachingRelatedVideoContent() {
        val relatedTitle = "Danmaku settings should not leak touches"
        var clickedBvid: String? = null
        var clickedBundle: Bundle? = null

        composeTestRule.setContent {
            MaterialTheme {
                Box(
                    modifier = Modifier
                        .size(width = 390.dp, height = 844.dp)
                        .background(Color.Black)
                ) {
                    VideoContentSection(
                        info = ViewInfo(
                            bvid = "BV_TEST",
                            title = "UI regression",
                            desc = "verify danmaku settings overlay layering",
                            owner = Owner(mid = 1L, name = "Tester"),
                            stat = Stat(view = 10, reply = 1)
                        ),
                        relatedVideos = listOf(
                            RelatedVideo(
                                bvid = "BV_RELATED",
                                title = relatedTitle,
                                owner = Owner(mid = 2L, name = "Related UP"),
                                stat = Stat(view = 12, reply = 2)
                            )
                        ),
                        replies = emptyList(),
                        replyCount = 0,
                        emoteMap = emptyMap(),
                        isRepliesLoading = false,
                        isFollowing = false,
                        isFavorited = false,
                        isLiked = false,
                        coinCount = 0,
                        currentPageIndex = 0,
                        onFollowClick = {},
                        onFavoriteClick = {},
                        onLikeClick = {},
                        onCoinClick = {},
                        onTripleClick = {},
                        onPageSelect = {},
                        onUpClick = {},
                        onRelatedVideoClick = { bvid, bundle ->
                            clickedBvid = bvid
                            clickedBundle = bundle
                        },
                        onSubReplyClick = {},
                        onLoadMoreReplies = {},
                        showInteractionActions = false
                    )
                }
            }
        }

        val relatedTitleCenter = composeTestRule
            .onNodeWithText(relatedTitle)
            .fetchSemanticsNode()
            .boundsInRoot
            .center

        composeTestRule
            .onNodeWithContentDescription("弹幕设置")
            .performClick()

        composeTestRule
            .onRoot()
            .performTouchInput {
                click(relatedTitleCenter)
            }

        composeTestRule.runOnIdle {
            assertNull(clickedBvid)
            assertNull(clickedBundle)
        }
    }
}

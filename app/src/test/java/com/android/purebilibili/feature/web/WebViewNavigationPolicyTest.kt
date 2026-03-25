package com.android.purebilibili.feature.web

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class WebViewNavigationPolicyTest {

    @Test
    fun resolveWebViewNavigationAction_blocksAutoAppVideoDeepLink() {
        val action = resolveWebViewNavigationAction(
            urlString = "bilibili://video/170001",
            hasUserGesture = false
        )

        assertIs<WebViewNavigationAction.Block>(action)
    }

    @Test
    fun resolveWebViewNavigationAction_convertsGestureVideoDeepLinkToWebUrl() {
        val action = resolveWebViewNavigationAction(
            urlString = "bilibili://video/170001",
            hasUserGesture = true
        )

        val loadAction = assertIs<WebViewNavigationAction.LoadInWebView>(action)
        assertEquals("https://m.bilibili.com/video/av170001", loadAction.url)
    }
}

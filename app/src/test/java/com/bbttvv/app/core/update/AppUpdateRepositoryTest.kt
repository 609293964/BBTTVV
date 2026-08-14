package com.bbttvv.app.core.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.net.UnknownHostException

class AppUpdateRepositoryTest {
    @Test
    fun selectCompatibleApk_prefersDeviceAbiOrder() {
        val arm64 = GithubReleaseAsset(
            name = "BBTTV-1.28-arm64-v8a.apk",
            downloadUrl = "https://example.test/arm64.apk",
        )
        val armv7 = GithubReleaseAsset(
            name = "BBTTV-1.28-armeabi-v7a.apk",
            downloadUrl = "https://example.test/armv7.apk",
        )

        assertEquals(
            arm64,
            selectCompatibleApk(listOf(armv7, arm64), arrayOf("arm64-v8a", "armeabi-v7a")),
        )
    }

    @Test
    fun selectCompatibleApk_returnsNullWhenNoMatchingAbiExists() {
        val asset = GithubReleaseAsset(
            name = "BBTTV-1.28-arm64-v8a.apk",
            downloadUrl = "https://example.test/arm64.apk",
        )

        assertNull(selectCompatibleApk(listOf(asset), arrayOf("x86_64")))
    }

    @Test
    fun resolveUpdateNetworkError_explainsDnsFailure() {
        assertEquals(
            "无法解析 api.github.com，请检查电视的网络或 DNS 设置",
            resolveUpdateNetworkError(UnknownHostException("api.github.com")),
        )
    }
}

package com.bbttvv.app.core.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import java.io.IOException
import java.net.SocketTimeoutException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Request
import okhttp3.OkHttpClient

private const val GITHUB_LATEST_RELEASE_URL =
    "https://api.github.com/repos/609293964/BBTTVV/releases/latest"
private const val APK_MIME_TYPE = "application/vnd.android.package-archive"

/** A GitHub release that has a compatible APK attached. */
data class AvailableAppUpdate(
    val versionName: String,
    val releaseNotes: String,
    val downloadUrl: String,
    val sha256: String?,
)

sealed interface AppUpdateCheckResult {
    data object NoCompatibleApk : AppUpdateCheckResult
    data class UpdateAvailable(val update: AvailableAppUpdate) : AppUpdateCheckResult
}

sealed interface AppUpdateDownloadResult {
    data class ReadyToInstall(val apkFile: File, val versionName: String) : AppUpdateDownloadResult
    data class Rejected(val reason: String) : AppUpdateDownloadResult
}

/**
 * Reads public GitHub Releases and validates an APK before passing it to Android's installer.
 *
 * The release asset's digest is an integrity check. The package name, versionCode and signing
 * certificate checks below are the upgrade-security boundary; a release title or asset filename
 * is never trusted as a version identifier.
 */
class AppUpdateRepository(
    private val appContext: Context,
    private val httpClient: OkHttpClient = updateHttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun checkLatest(): AppUpdateCheckResult = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(GITHUB_LATEST_RELEASE_URL)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "BBTTVV-update-checker")
            .build()
        val release = httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException(resolveGithubHttpError(response.code, response.body?.string()))
            }
            val body = response.body?.string() ?: error("GitHub 返回为空")
            json.decodeFromString(GithubRelease.serializer(), body)
        }
        val asset = selectCompatibleApk(release.assets, Build.SUPPORTED_ABIS)
            ?: return@withContext AppUpdateCheckResult.NoCompatibleApk
        AppUpdateCheckResult.UpdateAvailable(
            AvailableAppUpdate(
                versionName = asset.name.removeSuffix(".apk").removePrefix("BBTTVV-"),
                releaseNotes = release.body.orEmpty().trim(),
                downloadUrl = asset.downloadUrl,
                sha256 = asset.digest?.removePrefix("sha256:")?.takeIf { it.length == 64 },
            )
        )
    }

    suspend fun downloadAndVerify(update: AvailableAppUpdate): AppUpdateDownloadResult =
        withContext(Dispatchers.IO) {
            val updatesDir = File(appContext.cacheDir, "updates").apply { mkdirs() }
            val target = File(updatesDir, "BBTTVV-update.apk")
            val temporary = File(updatesDir, "BBTTVV-update.apk.part")
            temporary.delete()

            try {
                val request = Request.Builder()
                    .url(update.downloadUrl)
                    .header("Accept", APK_MIME_TYPE)
                    .header("User-Agent", "BBTTVV-updater")
                    .build()
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@withContext AppUpdateDownloadResult.Rejected(
                            "下载失败（${response.code}）"
                        )
                    }
                    val body = response.body ?: return@withContext AppUpdateDownloadResult.Rejected("更新包为空")
                    body.byteStream().use { input ->
                        temporary.outputStream().buffered().use { output -> input.copyTo(output) }
                    }
                }
                if (update.sha256 != null && sha256(temporary) != update.sha256.lowercase()) {
                    return@withContext AppUpdateDownloadResult.Rejected("更新包校验失败，请稍后重试")
                }
                when (val verification = verifyApk(temporary)) {
                    is ApkVerification.Accepted -> {
                        target.delete()
                        if (!temporary.renameTo(target)) {
                            return@withContext AppUpdateDownloadResult.Rejected("无法保存更新包")
                        }
                        AppUpdateDownloadResult.ReadyToInstall(target, verification.versionName)
                    }
                    is ApkVerification.Rejected -> AppUpdateDownloadResult.Rejected(verification.reason)
                }
            } finally {
                temporary.delete()
            }
        }

    private fun verifyApk(apk: File): ApkVerification {
        val archive = appContext.packageManager.getPackageArchiveInfo(
            apk.absolutePath,
            PackageManager.GET_SIGNING_CERTIFICATES,
        ) ?: return ApkVerification.Rejected("更新包无效")
        if (archive.packageName != appContext.packageName) {
            return ApkVerification.Rejected("更新包的应用包名不匹配")
        }
        val current = appContext.packageManager.getPackageInfo(
            appContext.packageName,
            PackageManager.GET_SIGNING_CERTIFICATES,
        )
        if (packageVersionCode(archive) <= packageVersionCode(current)) {
            return ApkVerification.Rejected("该更新包不是更高版本")
        }
        if (certificateDigests(archive) != certificateDigests(current)) {
            return ApkVerification.Rejected("更新包签名与当前应用不一致")
        }
        return ApkVerification.Accepted(archive.versionName.orEmpty())
    }
}

/**
 * Releases are not Bilibili API traffic. In particular, do not reuse guestOkHttpClient here:
 * that client adds Bilibili Origin/Cookie headers and Bilibili-specific request handling.
 */
private val updateHttpClient: OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(20, TimeUnit.SECONDS)
    .readTimeout(45, TimeUnit.SECONDS)
    .writeTimeout(20, TimeUnit.SECONDS)
    .retryOnConnectionFailure(true)
    .followRedirects(true)
    .followSslRedirects(true)
    .build()

private fun resolveGithubHttpError(statusCode: Int, body: String?): String {
    val message = body
        ?.substringAfter("\"message\":\"")
        ?.substringBefore('"')
        ?.takeIf { it.isNotBlank() }
    return buildString {
        append("GitHub 请求失败（HTTP $statusCode）")
        if (message != null) append("：$message")
    }
}

internal fun resolveUpdateNetworkError(error: Exception): String = when (error) {
    is java.net.UnknownHostException -> "无法解析 api.github.com，请检查电视的网络或 DNS 设置"
    is SocketTimeoutException -> "连接 GitHub 超时，请检查电视网络后重试"
    is IOException -> "连接 GitHub 失败：${error.message ?: error.javaClass.simpleName}"
    else -> error.message ?: "检查更新失败"
}

private sealed interface ApkVerification {
    data class Accepted(val versionName: String) : ApkVerification
    data class Rejected(val reason: String) : ApkVerification
}

@Suppress("DEPRECATION")
private fun packageVersionCode(packageInfo: PackageInfo): Long =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) packageInfo.longVersionCode else packageInfo.versionCode.toLong()

@Suppress("DEPRECATION")
private fun certificateDigests(packageInfo: PackageInfo): Set<String> {
    val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        packageInfo.signingInfo?.apkContentsSigners.orEmpty()
    } else {
        packageInfo.signatures.orEmpty()
    }
    return signatures.map { signature -> sha256(signature.toByteArray()) }.toSet()
}

private fun sha256(file: File): String = file.inputStream().buffered().use { input ->
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    while (true) {
        val count = input.read(buffer)
        if (count < 0) break
        digest.update(buffer, 0, count)
    }
    digest.digest().joinToString("") { byte -> "%02x".format(byte) }
}

private fun sha256(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { byte -> "%02x".format(byte) }

@Serializable
private data class GithubRelease(
    val body: String? = null,
    val assets: List<GithubReleaseAsset> = emptyList(),
)

@Serializable
internal data class GithubReleaseAsset(
    val name: String,
    val digest: String? = null,
    @SerialName("browser_download_url") val downloadUrl: String,
)

internal fun selectCompatibleApk(
    assets: List<GithubReleaseAsset>,
    supportedAbis: Array<String>,
): GithubReleaseAsset? = supportedAbis
    .mapNotNull { abi -> assets.firstOrNull { asset -> asset.name.endsWith("-$abi.apk", ignoreCase = true) } }
    .firstOrNull()

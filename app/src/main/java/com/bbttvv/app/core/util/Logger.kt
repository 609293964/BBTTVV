// 文件路径: core/util/Logger.kt
package com.bbttvv.app.core.util

import android.content.Context
import android.util.Log
import com.bbttvv.app.BuildConfig
import java.io.File
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

private const val LOG_DIRECTORY_NAME = "logs"
private const val RUNTIME_LOG_FILE_NAME = "runtime.log"
private const val CRASH_SNAPSHOT_FILE_NAME = "last_crash_log.txt"
private const val CRASH_SNAPSHOT_MARKER_FILE_NAME = "pending_crash.marker"
private const val DOWNLOAD_LOG_RELATIVE_PATH = "Download/BBTTVV/logs"

internal fun resolveLogPersistenceDir(baseDir: File): File = File(baseDir, LOG_DIRECTORY_NAME)

internal fun resolveRuntimeLogFile(baseDir: File): File =
    File(resolveLogPersistenceDir(baseDir), RUNTIME_LOG_FILE_NAME)

internal fun resolveCrashSnapshotFile(baseDir: File): File =
    File(resolveLogPersistenceDir(baseDir), CRASH_SNAPSHOT_FILE_NAME)

internal fun resolveCrashSnapshotMarkerFile(baseDir: File): File =
    File(resolveLogPersistenceDir(baseDir), CRASH_SNAPSHOT_MARKER_FILE_NAME)

internal fun resolvePlayerDiagnosticExportFileName(exportedAtMillis: Long): String {
    val formatter = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
    return "player_diagnostic_${formatter.format(Date(exportedAtMillis))}.txt"
}

internal fun shouldEnableVerboseRuntimeLogs(
    isDebugBuild: Boolean,
    verboseDebugLogsEnabled: Boolean
): Boolean = isDebugBuild && verboseDebugLogsEnabled

internal fun shouldCaptureRuntimeLogEntry(
    level: String,
    verboseRuntimeLogsEnabled: Boolean
): Boolean = when (level) {
    "W", "E" -> true
    else -> verboseRuntimeLogsEnabled
}

internal fun shouldPersistRuntimeLogEntry(
    level: String,
    verboseRuntimeLogPersistenceEnabled: Boolean
): Boolean = verboseRuntimeLogPersistenceEnabled

internal fun resolveLogArtifactDirsToClear(
    filesDir: File,
    cacheDir: File
): List<File> = listOf(
    resolveLogPersistenceDir(filesDir),
    resolveLogPersistenceDir(cacheDir)
).distinctBy { it.absolutePath }

internal fun hasPendingCrashSnapshot(
    markerExists: Boolean,
    snapshotExists: Boolean
): Boolean = markerExists && snapshotExists

internal fun buildCrashSnapshotContent(
    throwable: Throwable,
    entries: List<LogCollector.LogEntry>,
    exportedAtMillis: Long,
    appVersionName: String,
    versionCode: Int,
    manufacturer: String,
    model: String,
    androidRelease: String,
    apiLevel: Int
): String {
    val headerDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
    val safeMessage = sanitizeDiagnosticText(throwable.message.orEmpty())
    val safeThrowable = sanitizedThrowableText(throwable)
    return buildString {
        appendLine("========================================")
        appendLine("BBTTVV 崩溃日志快照")
        appendLine("========================================")
        appendLine("生成时间: ${headerDateFormat.format(Date(exportedAtMillis))}")
        appendLine("应用版本: $appVersionName ($versionCode)")
        appendLine("设备信息: $manufacturer $model")
        appendLine("Android版本: $androidRelease (API $apiLevel)")
        appendLine("异常类型: ${throwable.javaClass.simpleName}")
        appendLine("异常信息: $safeMessage")
        appendLine("========================================")
        appendLine()
        appendLine("----- Throwable -----")
        appendLine(safeThrowable)
        appendLine("----- Recent Logs -----")
        entries.forEach { entry ->
            appendLine(entry.copy(message = sanitizeDiagnosticText(entry.message)).format())
        }
    }
}

object Logger {
    @PublishedApi
    internal val verboseRuntimeLogsEnabled = shouldEnableVerboseRuntimeLogs(
        isDebugBuild = BuildConfig.DEBUG,
        verboseDebugLogsEnabled = false
    )

    @PublishedApi
    internal val verboseRuntimeLogPersistenceEnabled = verboseRuntimeLogsEnabled && false

    fun init(context: Context) {
        LogCollector.init(context.applicationContext)
    }

    fun d(tag: String, message: String) {
        if (!verboseRuntimeLogsEnabled) return
        val safeMessage = sanitizeDiagnosticText(message)
        Log.d(tag, safeMessage)
        if (shouldCaptureRuntimeLogEntry("D", verboseRuntimeLogsEnabled)) {
            LogCollector.add(
                level = "D",
                tag = tag,
                message = safeMessage,
                persistToDisk = shouldPersistRuntimeLogEntry("D", verboseRuntimeLogPersistenceEnabled)
            )
        }
    }

    inline fun d(tag: String, message: () -> String) {
        if (!verboseRuntimeLogsEnabled) return
        d(tag, message())
    }

    fun i(tag: String, message: String) {
        if (!verboseRuntimeLogsEnabled) return
        val safeMessage = sanitizeDiagnosticText(message)
        Log.i(tag, safeMessage)
        if (shouldCaptureRuntimeLogEntry("I", verboseRuntimeLogsEnabled)) {
            LogCollector.add(
                level = "I",
                tag = tag,
                message = safeMessage,
                persistToDisk = shouldPersistRuntimeLogEntry("I", verboseRuntimeLogPersistenceEnabled)
            )
        }
    }

    inline fun i(tag: String, message: () -> String) {
        if (!verboseRuntimeLogsEnabled) return
        i(tag, message())
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        val safeText = sanitizeDiagnosticText(
            if (throwable == null) message else "$message\n${throwable.stackTraceToString()}"
        )
        Log.w(tag, safeText)
        if (shouldCaptureRuntimeLogEntry("W", verboseRuntimeLogsEnabled)) {
            LogCollector.add(
                level = "W",
                tag = tag,
                message = safeText,
                persistToDisk = shouldPersistRuntimeLogEntry("W", verboseRuntimeLogPersistenceEnabled)
            )
        }
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        val safeText = sanitizeDiagnosticText(
            if (throwable == null) message else "$message\n${throwable.stackTraceToString()}"
        )
        Log.e(tag, safeText)
        if (shouldCaptureRuntimeLogEntry("E", verboseRuntimeLogsEnabled)) {
            LogCollector.add(
                level = "E",
                tag = tag,
                message = safeText,
                persistToDisk = shouldPersistRuntimeLogEntry("E", verboseRuntimeLogPersistenceEnabled)
            )
        }
    }

    fun persistCrashSnapshot(throwable: Throwable) {
        LogCollector.persistCrashSnapshot(throwable)
    }

    fun getPendingCrashSnapshotPath(context: Context): String? {
        init(context)
        return LogCollector.getPendingCrashSnapshotFile()?.absolutePath
    }

    fun clearPendingCrashSnapshot(context: Context) {
        init(context)
        LogCollector.clearPendingCrashSnapshot()
    }

    fun getPrivateLogArtifactsSize(context: Context): Long {
        init(context)
        val persistedLogDir = resolveLogPersistenceDir(context.filesDir)
        return if (!persistedLogDir.exists()) 0L
        else persistedLogDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    fun clearPrivateLogArtifacts(context: Context) {
        init(context)
        LogCollector.clear()
        LogCollector.clearPendingCrashSnapshot()
        resolveLogArtifactDirsToClear(
            filesDir = context.filesDir,
            cacheDir = context.cacheDir
        ).forEach { dir ->
            if (dir.exists()) dir.deleteRecursively()
        }
    }

    fun exportPlayerDiagnostic(
        context: Context,
        content: String,
        exportedAtMillis: Long = System.currentTimeMillis()
    ): String? {
        init(context)
        return LogCollector.saveTextArtifact(
            context = context,
            fileName = resolvePlayerDiagnosticExportFileName(exportedAtMillis),
            content = sanitizeDiagnosticText(content),
            replaceExisting = false
        )
    }

    fun exportLogs(context: Context): Result<String> {
        init(context)
        return LogCollector.exportLogs(context)
    }
}

object LogCollector {
    private const val MAX_ENTRIES = 1000
    private const val DUPLICATE_SUPPRESS_WINDOW_MS = 250L
    private const val MAX_PERSISTED_LOG_BYTES = 256 * 1024
    private const val PERSISTED_LOG_TRIM_TARGET_BYTES = 128 * 1024
    private val lock = Any()
    private val buffer = ArrayDeque<LogEntry>(MAX_ENTRIES)
    private val diskWriter = Executors.newSingleThreadExecutor()
    private var lastEntryFingerprint: String? = null
    private var lastEntryTimestamp: Long = 0L

    @Volatile
    private var appContext: Context? = null

    data class LogEntry(
        val timestamp: Long,
        val level: String,
        val tag: String,
        val message: String
    ) {
        fun format(): String {
            val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date(timestamp))
            return "[$time] $level/$tag: $message"
        }
    }

    /** All entries are sanitized before entering memory or disk. */
    fun add(level: String, tag: String, message: String, persistToDisk: Boolean = true) {
        val safeMessage = sanitizeDiagnosticText(message)
        val now = System.currentTimeMillis()
        val fingerprint = "$level|$tag|$safeMessage"
        var entryToPersist: LogEntry? = null
        synchronized(lock) {
            if (fingerprint == lastEntryFingerprint &&
                now - lastEntryTimestamp <= DUPLICATE_SUPPRESS_WINDOW_MS
            ) {
                lastEntryTimestamp = now
                return
            }

            lastEntryFingerprint = fingerprint
            lastEntryTimestamp = now
            entryToPersist = LogEntry(
                timestamp = now,
                level = level,
                tag = tag,
                message = safeMessage
            )
            buffer.addLast(entryToPersist)
            while (buffer.size > MAX_ENTRIES) {
                buffer.removeFirst()
            }
        }

        if (persistToDisk) entryToPersist?.let(::appendEntryToRuntimeFile)
    }

    fun init(context: Context) {
        if (appContext === context.applicationContext) return
        appContext = context.applicationContext
        runCatching {
            resolveLogPersistenceDir(context.filesDir).mkdirs()
        }.onFailure { error ->
            Log.e("LogCollector", sanitizeDiagnosticText("初始化持久化日志目录失败\n${error.stackTraceToString()}"))
        }
    }

    fun getEntries(): List<LogEntry> = synchronized(lock) { buffer.toList() }

    fun getCount(): Int = synchronized(lock) { buffer.size }

    fun clear() {
        synchronized(lock) {
            buffer.clear()
            lastEntryFingerprint = null
            lastEntryTimestamp = 0L
        }
    }

    /** Crash snapshots are automatic artifacts and remain in app-private storage only. */
    fun persistCrashSnapshot(throwable: Throwable) {
        val context = appContext ?: return
        runCatching {
            val content = buildCrashSnapshotContent(
                throwable = throwable,
                entries = getEntries(),
                exportedAtMillis = System.currentTimeMillis(),
                appVersionName = BuildConfig.VERSION_NAME,
                versionCode = BuildConfig.VERSION_CODE,
                manufacturer = android.os.Build.MANUFACTURER,
                model = android.os.Build.MODEL,
                androidRelease = android.os.Build.VERSION.RELEASE,
                apiLevel = android.os.Build.VERSION.SDK_INT
            )
            val snapshotFile = resolveCrashSnapshotFile(context.filesDir)
            val markerFile = resolveCrashSnapshotMarkerFile(context.filesDir)
            snapshotFile.parentFile?.mkdirs()
            snapshotFile.writeText(content)
            markerFile.writeText(System.currentTimeMillis().toString())
        }.onFailure { error ->
            Log.e("LogCollector", sanitizeDiagnosticText("写入崩溃快照失败\n${error.stackTraceToString()}"))
        }
    }

    fun getPendingCrashSnapshotFile(): File? {
        val context = appContext ?: return null
        val snapshotFile = resolveCrashSnapshotFile(context.filesDir)
        val markerFile = resolveCrashSnapshotMarkerFile(context.filesDir)
        return snapshotFile.takeIf {
            hasPendingCrashSnapshot(
                markerExists = markerFile.exists(),
                snapshotExists = snapshotFile.exists()
            )
        }
    }

    fun clearPendingCrashSnapshot() {
        val context = appContext ?: return
        runCatching {
            resolveCrashSnapshotMarkerFile(context.filesDir).delete()
        }.onFailure { error ->
            Log.e("LogCollector", sanitizeDiagnosticText("清理崩溃快照标记失败\n${error.stackTraceToString()}"))
        }
    }

    /** User-triggered export is sanitized before writing to public Downloads. */
    fun exportLogs(context: Context): Result<String> {
        return runCatching {
            val entries = getEntries()
            check(entries.isNotEmpty()) { "暂无日志记录" }
            val header = buildString {
                appendLine("========================================")
                appendLine("BBTTVV 应用日志导出")
                appendLine("========================================")
                appendLine("导出时间: ${logDateFormat().format(Date())}")
                appendLine("应用版本: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                appendLine("设备信息: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
                appendLine("Android版本: ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")
                appendLine("日志条数: ${entries.size}")
                appendLine("========================================")
                appendLine()
            }
            val content = header + entries.joinToString("\n") { entry ->
                entry.copy(message = sanitizeDiagnosticText(entry.message)).format()
            }
            val fileName = "bbttvv_log_${logFileDateFormat().format(Date())}.txt"
            saveToExternalDownload(context, fileName, content)
                ?: error("日志导出目录不可用")
        }.onFailure { error ->
            Log.e("LogCollector", sanitizeDiagnosticText("导出日志失败\n${error.stackTraceToString()}"))
        }
    }

    fun saveTextArtifact(
        context: Context,
        fileName: String,
        content: String,
        replaceExisting: Boolean = false
    ): String? {
        return saveToExternalDownload(
            context = context,
            fileName = fileName,
            content = sanitizeDiagnosticText(content),
            replaceExisting = replaceExisting
        )
    }

    private fun saveToExternalDownload(
        context: Context,
        fileName: String,
        content: String,
        replaceExisting: Boolean = false
    ): String? {
        val safeContent = sanitizeDiagnosticText(content)
        return try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                if (replaceExisting) {
                    findExistingDownloadUri(context, fileName)?.let { uri ->
                        context.contentResolver.delete(uri, null, null)
                    }
                }
                val contentValues = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(android.provider.MediaStore.Downloads.MIME_TYPE, "text/plain")
                    put(android.provider.MediaStore.Downloads.RELATIVE_PATH, DOWNLOAD_LOG_RELATIVE_PATH)
                }
                val uri = context.contentResolver.insert(
                    android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    contentValues
                )
                uri?.let {
                    context.contentResolver.openOutputStream(it)?.use { outputStream ->
                        outputStream.write(safeContent.toByteArray())
                    }
                    "$DOWNLOAD_LOG_RELATIVE_PATH/$fileName"
                }
            } else {
                @Suppress("DEPRECATION")
                val downloadDir = android.os.Environment.getExternalStoragePublicDirectory(
                    android.os.Environment.DIRECTORY_DOWNLOADS
                )
                val logDir = File(downloadDir, "BBTTVV/logs")
                logDir.mkdirs()
                val logFile = File(logDir, fileName)
                logFile.writeText(safeContent)
                logFile.absolutePath
            }
        } catch (error: Exception) {
            Log.w("LogCollector", sanitizeDiagnosticText("无法保存到外部存储\n${error.stackTraceToString()}"))
            null
        }
    }

    @androidx.annotation.RequiresApi(android.os.Build.VERSION_CODES.Q)
    private fun findExistingDownloadUri(context: Context, fileName: String): android.net.Uri? {
        val projection = arrayOf(android.provider.MediaStore.Downloads._ID)
        val selection =
            "${android.provider.MediaStore.Downloads.DISPLAY_NAME}=? AND " +
                "${android.provider.MediaStore.Downloads.RELATIVE_PATH}=?"
        val selectionArgs = arrayOf(fileName, DOWNLOAD_LOG_RELATIVE_PATH)
        return context.contentResolver.query(
            android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            null
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val idIndex = cursor.getColumnIndex(android.provider.MediaStore.Downloads._ID)
            if (idIndex < 0) return@use null
            val id = cursor.getLong(idIndex)
            android.content.ContentUris.withAppendedId(
                android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                id
            )
        }
    }

    private fun logDateFormat(): SimpleDateFormat =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())

    private fun logFileDateFormat(): SimpleDateFormat =
        SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())

    private fun appendEntryToRuntimeFile(entry: LogEntry) {
        val context = appContext ?: return
        val sanitizedEntry = entry.copy(message = sanitizeDiagnosticText(entry.message)).format() + "\n"
        diskWriter.execute {
            runCatching {
                val runtimeLogFile = resolveRuntimeLogFile(context.filesDir)
                runtimeLogFile.parentFile?.mkdirs()
                appendTextWithRollingLimit(runtimeLogFile, sanitizedEntry)
            }.onFailure { error ->
                Log.e("LogCollector", sanitizeDiagnosticText("持久化运行日志失败\n${error.stackTraceToString()}"))
            }
        }
    }

    private fun appendTextWithRollingLimit(file: File, text: String) {
        if (!file.exists()) {
            file.writeText(text)
            return
        }
        if (file.length() + text.toByteArray().size <= MAX_PERSISTED_LOG_BYTES) {
            file.appendText(text)
            return
        }
        val retained = runCatching {
            file.readText().takeLast(PERSISTED_LOG_TRIM_TARGET_BYTES)
        }.getOrDefault("")
        file.writeText(retained + text)
    }
}

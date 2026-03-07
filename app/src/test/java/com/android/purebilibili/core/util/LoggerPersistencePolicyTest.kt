package com.android.purebilibili.core.util

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LoggerPersistencePolicyTest {

    @Test
    fun resolvesStableFilePathsUnderLogDirectory() {
        val baseDir = File("/tmp/bilipai")

        assertEquals(
            File("/tmp/bilipai/logs"),
            resolveLogPersistenceDir(baseDir)
        )
        assertEquals(
            File("/tmp/bilipai/logs/runtime.log"),
            resolveRuntimeLogFile(baseDir)
        )
        assertEquals(
            File("/tmp/bilipai/logs/last_crash_log.txt"),
            resolveCrashSnapshotFile(baseDir)
        )
        assertEquals(
            File("/tmp/bilipai/logs/pending_crash.marker"),
            resolveCrashSnapshotMarkerFile(baseDir)
        )
        assertEquals(
            "Download/BiliPai/logs/last_crash_log.txt",
            resolveCrashSnapshotExportRelativePath()
        )
    }

    @Test
    fun pendingCrashSnapshotRequiresMarkerAndSnapshotFile() {
        assertTrue(
            hasPendingCrashSnapshot(
                markerExists = true,
                snapshotExists = true
            )
        )
        assertFalse(
            hasPendingCrashSnapshot(
                markerExists = true,
                snapshotExists = false
            )
        )
        assertFalse(
            hasPendingCrashSnapshot(
                markerExists = false,
                snapshotExists = true
            )
        )
    }

    @Test
    fun crashSnapshotContentIncludesThrowableAndRecentLogs() {
        val entries = listOf(
            LogCollector.LogEntry(
                timestamp = 1_741_334_800_000L,
                level = "D",
                tag = "MainActivity",
                message = "before crash"
            ),
            LogCollector.LogEntry(
                timestamp = 1_741_334_801_000L,
                level = "E",
                tag = "VideoDetailScreen",
                message = "boom soon"
            )
        )

        val content = buildCrashSnapshotContent(
            throwable = IllegalStateException("Player exploded"),
            entries = entries,
            exportedAtMillis = 1_741_334_802_000L,
            appVersionName = "6.9.0",
            versionCode = 103,
            manufacturer = "nubia",
            model = "NX769J",
            androidRelease = "16",
            apiLevel = 36
        )

        assertTrue(content.contains("BiliPai 崩溃日志快照"))
        assertTrue(content.contains("IllegalStateException"))
        assertTrue(content.contains("Player exploded"))
        assertTrue(content.contains("before crash"))
        assertTrue(content.contains("boom soon"))
    }
}

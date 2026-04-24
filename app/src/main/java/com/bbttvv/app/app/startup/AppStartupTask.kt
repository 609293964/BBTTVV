package com.bbttvv.app.app.startup

internal enum class StartupPhase {
    BEFORE_FIRST_INTERACTIVE,
    AFTER_FIRST_INTERACTIVE
}

internal enum class StartupThread {
    MAIN,
    IO,
    DEFAULT
}

internal data class AppStartupTask(
    val id: String,
    val phase: StartupPhase,
    val thread: StartupThread,
    val delayMs: Long = 0L
)

internal fun defaultAppStartupTasks(
    deferredDelayMs: Long
): List<AppStartupTask> {
    return listOf(
        AppStartupTask(
            id = "network_module_init",
            phase = StartupPhase.BEFORE_FIRST_INTERACTIVE,
            thread = StartupThread.MAIN
        ),
        AppStartupTask(
            id = "token_manager_init",
            phase = StartupPhase.BEFORE_FIRST_INTERACTIVE,
            thread = StartupThread.MAIN
        ),
        AppStartupTask(
            id = "video_repository_init",
            phase = StartupPhase.BEFORE_FIRST_INTERACTIVE,
            thread = StartupThread.MAIN
        ),
        AppStartupTask(
            id = "background_manager_init",
            phase = StartupPhase.BEFORE_FIRST_INTERACTIVE,
            thread = StartupThread.MAIN
        ),
        AppStartupTask(
            id = "player_settings_cache_init",
            phase = StartupPhase.AFTER_FIRST_INTERACTIVE,
            thread = StartupThread.IO
        ),
        AppStartupTask(
            id = "home_feed_preload",
            phase = StartupPhase.AFTER_FIRST_INTERACTIVE,
            thread = StartupThread.IO
        ),
        AppStartupTask(
            id = "crash_reporter_init",
            phase = StartupPhase.AFTER_FIRST_INTERACTIVE,
            thread = StartupThread.DEFAULT,
            delayMs = deferredDelayMs
        ),
        AppStartupTask(
            id = "plugin_manager_init",
            phase = StartupPhase.AFTER_FIRST_INTERACTIVE,
            thread = StartupThread.IO,
            delayMs = deferredDelayMs
        )
    )
}

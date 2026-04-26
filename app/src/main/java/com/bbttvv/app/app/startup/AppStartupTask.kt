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
    val delayMs: Long = 0L,
    val coldStartBudgetMs: Double? = null
)

internal fun defaultAppStartupTasks(
    deferredDelayMs: Long
): List<AppStartupTask> {
    return listOf(
        AppStartupTask(
            id = "network_module_init",
            phase = StartupPhase.BEFORE_FIRST_INTERACTIVE,
            thread = StartupThread.MAIN,
            coldStartBudgetMs = 1.0
        ),
        AppStartupTask(
            id = "token_manager_init",
            phase = StartupPhase.BEFORE_FIRST_INTERACTIVE,
            thread = StartupThread.MAIN,
            coldStartBudgetMs = 2.0
        ),
        AppStartupTask(
            id = "video_repository_init",
            phase = StartupPhase.BEFORE_FIRST_INTERACTIVE,
            thread = StartupThread.MAIN,
            coldStartBudgetMs = 2.0
        ),
        AppStartupTask(
            id = "background_manager_init",
            phase = StartupPhase.BEFORE_FIRST_INTERACTIVE,
            thread = StartupThread.MAIN,
            coldStartBudgetMs = 1.0
        ),
        AppStartupTask(
            id = "player_settings_cache_init",
            phase = StartupPhase.AFTER_FIRST_INTERACTIVE,
            thread = StartupThread.IO
        ),
        AppStartupTask(
            id = "token_manager_warmup",
            phase = StartupPhase.AFTER_FIRST_INTERACTIVE,
            thread = StartupThread.IO
        ),
        AppStartupTask(
            id = "home_feed_preload",
            phase = StartupPhase.AFTER_FIRST_INTERACTIVE,
            thread = StartupThread.IO
        ),
        AppStartupTask(
            id = "background_warmup",
            phase = StartupPhase.AFTER_FIRST_INTERACTIVE,
            thread = StartupThread.IO,
            delayMs = deferredDelayMs + 1_200L
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

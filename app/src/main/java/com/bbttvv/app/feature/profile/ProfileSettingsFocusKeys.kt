package com.bbttvv.app.feature.profile

internal object ProfileSettingsFocusKeys {
    const val LoggedInMenu = "profile:settings:logged-in-menu"
    const val GuestAction = "profile:settings:guest-action"
    const val ErrorAction = "profile:settings:error-action"

    val All = listOf(LoggedInMenu, GuestAction, ErrorAction)
}

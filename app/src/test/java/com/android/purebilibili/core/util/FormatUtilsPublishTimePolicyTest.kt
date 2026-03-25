package com.android.purebilibili.core.util

import java.util.Locale
import java.util.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals

class FormatUtilsPublishTimePolicyTest {

    @Test
    fun formatPrecisePublishTime_usesProvidedTimeZoneAndLocale() {
        assertEquals(
            "1970-01-01 08:30",
            FormatUtils.formatPrecisePublishTime(
                timestampSeconds = 1_800L,
                locale = Locale.US,
                timeZone = TimeZone.getTimeZone("Asia/Shanghai")
            )
        )
    }
}

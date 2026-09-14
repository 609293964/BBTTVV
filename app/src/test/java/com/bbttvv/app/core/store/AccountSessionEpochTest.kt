package com.bbttvv.app.core.store

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountSessionEpochTest {
    @Test
    fun `advancing session invalidates captured generation`() {
        val captured = AccountSessionEpoch.current()

        assertTrue(AccountSessionEpoch.isCurrent(captured))

        AccountSessionEpoch.advance()

        assertFalse(AccountSessionEpoch.isCurrent(captured))
        assertTrue(AccountSessionEpoch.isCurrent(AccountSessionEpoch.current()))
    }
}

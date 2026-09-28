package com.tji.device.ui.components

import org.junit.Assert.assertFalse
import org.junit.Test

class LoginEntryVisibilityTest {
    @Test
    fun customerLoginHidesDirectLinkEntry() {
        assertFalse(SHOW_DIRECT_LINK_LOGIN_ENTRY)
    }
}

package com.tji.device.ui.components

import com.tji.device.data.local.RememberedLoginCredentials
import org.junit.Assert.assertEquals
import org.junit.Test

class LoginFormDraftTest {
    private val remembered = RememberedLoginCredentials(
        account = "saved-account",
        password = "saved-password"
    )

    @Test
    fun untouchedFormReceivesRememberedCredentials() {
        assertEquals(
            LoginFormDraft(
                account = "saved-account",
                password = "saved-password",
                rememberMe = true
            ),
            restoreRememberedLogin(
                current = LoginFormDraft(),
                remembered = remembered,
                userHasEdited = false
            )
        )
    }

    @Test
    fun lateBackgroundRestoreNeverOverwritesUserInput() {
        val userDraft = LoginFormDraft(
            account = "new-account",
            password = "new-password",
            rememberMe = false
        )

        assertEquals(
            userDraft,
            restoreRememberedLogin(
                current = userDraft,
                remembered = remembered,
                userHasEdited = true
            )
        )
    }

    @Test
    fun missingRememberedCredentialsKeepsCurrentForm() {
        val current = LoginFormDraft(account = "typing")

        assertEquals(
            current,
            restoreRememberedLogin(
                current = current,
                remembered = null,
                userHasEdited = false
            )
        )
    }
}

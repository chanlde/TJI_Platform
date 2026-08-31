package com.tji.device.ui.main

import com.tji.device.data.model.AuthState
import org.junit.Assert.assertEquals
import org.junit.Test

class AppNavigationStateTest {
    @Test
    fun loggedOutSessionShowsLogin() {
        assertEquals(
            AppState.LOGIN,
            resolveAppState(authState = AuthState.LoggedOut, updateBlockedLogin = false)
        )
    }

    @Test
    fun loggedInSessionShowsMainAndSurvivesUiRecalculation() {
        repeat(2) {
            assertEquals(
                AppState.MAIN,
                resolveAppState(authState = AuthState.LoggedIn("account"), updateBlockedLogin = false)
            )
        }
    }

    @Test
    fun requiredUpdateKeepsSuccessfulLoginBehindUpdateGate() {
        assertEquals(
            AppState.LOGIN,
            resolveAppState(authState = AuthState.LoggedIn("account"), updateBlockedLogin = true)
        )
    }

    @Test
    fun loggedOutUserCanEnterDirectLinkWithoutLogin() {
        assertEquals(
            AppState.DIRECT_LINK,
            resolveAppState(
                authState = AuthState.LoggedOut,
                updateBlockedLogin = false,
                directControlRequested = true
            )
        )
    }

    @Test
    fun loggingOutKeepsMainVisibleUntilCleanupCompletes() {
        assertEquals(
            AppState.MAIN,
            resolveAppState(
                authState = AuthState.LoggingOut("account"),
                updateBlockedLogin = false
            )
        )
    }
}

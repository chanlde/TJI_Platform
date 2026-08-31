package com.tji.device.product.speaker.ui.control

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.down
import androidx.compose.ui.test.up
import com.tji.device.product.speaker.viewmodel.SpeakerTalkMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PushToTalkButtonTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun recordingStateChangeDoesNotCancelActivePress() {
        var mode by mutableStateOf(SpeakerTalkMode.Idle)
        var presses = 0
        var releases = 0
        var cancellations = 0

        composeRule.setContent {
            MaterialTheme {
                PushToTalkButton(
                    enabled = mode == SpeakerTalkMode.Idle,
                    hasMicPermission = true,
                    mode = mode,
                    idleLabel = "按住说话",
                    activeLabel = "松开发送",
                    footer = "",
                    requestPermission = {},
                    onPress = {
                        presses += 1
                        mode = SpeakerTalkMode.Recording
                    },
                    onRelease = {
                        releases += 1
                        mode = SpeakerTalkMode.Idle
                    },
                    onCancel = {
                        cancellations += 1
                        mode = SpeakerTalkMode.Idle
                    }
                )
            }
        }

        composeRule.onRoot().performTouchInput {
            down(center)
            advanceEventTime(1_000)
            up()
        }
        composeRule.runOnIdle {
            assertEquals(SpeakerTalkMode.Idle, mode)
            assertEquals(1, presses)
            assertEquals(1, releases)
            assertEquals(0, cancellations)
        }
    }
}

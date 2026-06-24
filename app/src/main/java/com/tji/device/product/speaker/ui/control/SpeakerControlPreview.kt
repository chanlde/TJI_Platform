package com.tji.device.product.speaker.ui.control

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.tji.device.data.model.BoundAccountDevice
import com.tji.device.data.model.ProductType
import com.tji.device.product.speaker.model.DEFAULT_SPEAKER_VOLUME
import com.tji.device.product.speaker.model.SpeakerDeviceState
import com.tji.device.product.speaker.viewmodel.SpeakerCommandFeedback
import com.tji.device.product.speaker.viewmodel.SpeakerCommandFeedbackStatus
import com.tji.device.product.speaker.viewmodel.SpeakerTalkMode
import com.tji.device.product.speaker.viewmodel.SpeakerTalkState
import com.tji.device.ui.theme.BucketTheme

internal fun previewSpeakerState(device: BoundAccountDevice) =
    SpeakerDeviceState(
        serialNumber = device.serialNumber,
        name = device.name,
        isOnline = true,
        playing = false,
        volume = DEFAULT_SPEAKER_VOLUME,
        network = "4G"
    )

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun SpeakerControlScreenPreview() {
    BucketTheme {
        SpeakerControlScreen(
            device = BoundAccountDevice(
                serialNumber = "SPK-00420042",
                name = "喊话器 01",
                productType = ProductType.Speaker
            )
        )
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun SpeakerHeaderCardPreview() {
    val device = previewSpeakerDevice()
    BucketTheme {
        SpeakerHeaderCard(
            device = device,
            state = previewSpeakerState(device),
            outputGain = 0.72f,
            feedback = SpeakerCommandFeedback(
                status = SpeakerCommandFeedbackStatus.Success,
                text = "音量设置已确认"
            )
        )
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun SpeakerPushToTalkCardPreview() {
    BucketTheme {
        PushToTalkCard(
            talkState = SpeakerTalkState(mode = SpeakerTalkMode.Recording),
            enabled = true,
            hasMicPermission = true,
            requestPermission = {},
            onPress = {},
            onRelease = {},
            onCancel = {}
        )
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun SpeakerOutputVolumeCardPreview() {
    BucketTheme {
        OutputVolumeCard(
            volumeGain = 0.55f,
            enabled = true,
            onVolumeGainChange = {},
            onVolumeCommitted = {},
            onStop = {}
        )
    }
}

private fun previewSpeakerDevice() = BoundAccountDevice(
    serialNumber = "SPK-00420042",
    name = "喊话器 01",
    productType = ProductType.Speaker
)

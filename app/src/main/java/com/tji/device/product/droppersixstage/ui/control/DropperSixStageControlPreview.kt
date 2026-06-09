package com.tji.device.product.droppersixstage.ui.control

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.tji.device.data.model.BoundAccountDevice
import com.tji.device.data.model.ProductType
import com.tji.device.product.droppersixstage.model.DropperSixStageState
import com.tji.device.product.droppersixstage.model.DropperStageState
import com.tji.device.product.droppersixstage.viewmodel.DropperCommandFeedback
import com.tji.device.product.droppersixstage.viewmodel.DropperCommandFeedbackStatus
import com.tji.device.ui.theme.BucketTheme

internal fun previewDropperState(serialNumber: String, name: String) =
    DropperSixStageState(
        serialNumber = serialNumber,
        name = name,
        isOnline = true,
        stages = listOf(
            DropperStageState(1, isOpen = false, payloadLoaded = true),
            DropperStageState(2, isOpen = true, payloadLoaded = true),
            DropperStageState(3, isOpen = false, payloadLoaded = true),
            DropperStageState(4, isOpen = false, payloadLoaded = false),
            DropperStageState(5, isOpen = false, payloadLoaded = null),
            DropperStageState(6, isOpen = false, payloadLoaded = true)
        ),
        batteryPercent = 86
    )

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun DropperSixStageControlScreenPreview() {
    BucketTheme {
        DropperSixStageControlScreen(
            device = BoundAccountDevice(
                serialNumber = "DROP-6-0001",
                name = "六段抛投 01",
                productType = ProductType.DropperSixStage
            )
        )
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun DropperStageSelectorPreview() {
    BucketTheme {
        StageSelectorCard(
            stages = previewDropperState("DROP-6-0001", "六段抛投 01").stages,
            selectedStageIndex = 2,
            onSelectStage = {}
        )
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun DropperSelectedStageControlPreview() {
    BucketTheme {
        SelectedStageControlCard(
            stage = DropperStageState(index = 2, isOpen = true, payloadLoaded = true),
            enabled = true,
            durationMs = 1_000,
            isTesting = false,
            testEnabled = true,
            onOpen = {},
            onClose = {},
            onTimedOpen = {},
            onToggleTest = {}
        )
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun DropperHeaderCardPreview() {
    val device = BoundAccountDevice(
        serialNumber = "DROP-6-0001",
        name = "六段抛投 01",
        productType = ProductType.DropperSixStage
    )
    BucketTheme {
        DropperHeaderCard(
            device = device,
            state = previewDropperState(device.serialNumber, device.name),
            feedback = DropperCommandFeedback(
                status = DropperCommandFeedbackStatus.Success,
                text = "2段开钩成功"
            )
        )
    }
}

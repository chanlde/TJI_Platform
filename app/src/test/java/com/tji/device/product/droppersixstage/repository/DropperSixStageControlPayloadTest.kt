package com.tji.device.product.droppersixstage.repository

import com.tji.device.product.droppersixstage.model.DropperSixStageCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class DropperSixStageControlPayloadTest {
    @Test
    fun stageCommandMatchesV1SetHookPayloadExactly() {
        val payload = DropperSixStageCommand.StageSwitch(
            msgId = "stage-2",
            stage = 2,
            open = true,
            durationMs = 1_500
        ).toDropperControlJson()

        assertEquals(
            setOf("v", "msgId", "module", "action", "hook", "state", "duration"),
            payloadKeys(payload)
        )
        assertEquals("set_hook", payload.getString("action"))
        assertEquals(2, payload.getInt("hook"))
        assertEquals("open", payload.getString("state"))
        assertEquals(1_500, payload.getInt("duration"))
    }

    @Test
    fun closeAllAndQueryMatchV1PayloadsExactly() {
        val all = DropperSixStageCommand.AllStages(
            msgId = "all-close",
            open = false
        ).toDropperControlJson()
        val ping = DropperSixStageCommand.Ping("ping")
            .toDropperControlJson()

        assertEquals(setOf("v", "msgId", "module", "action"), payloadKeys(all))
        assertEquals("close_all", all.getString("action"))
        assertEquals(setOf("v", "msgId", "module", "action"), payloadKeys(ping))
        assertEquals("query", ping.getString("action"))
    }

    @Test
    fun armAndDisarmUseFireDropSafetyActions() {
        val arm = DropperSixStageCommand.Arm("arm-1").toDropperControlJson()
        val disarm = DropperSixStageCommand.Disarm("disarm-1").toDropperControlJson()

        assertEquals(setOf("v", "msgId", "module", "action"), payloadKeys(arm))
        assertEquals(setOf("v", "msgId", "module", "action"), payloadKeys(disarm))
        assertEquals("firedrop", arm.getString("module"))
        assertEquals("arm", arm.getString("action"))
        assertEquals("disarm", disarm.getString("action"))
    }

    @Test
    fun legacyFieldsAreNotSent() {
        val payload = DropperSixStageCommand.StageSwitch("stage-1", 1, open = true)
            .toDropperControlJson()

        listOf("ts", "cmd", "cmdName", "stage", "open", "durationMs").forEach {
            assertFalse("V1 payload must not include $it", payload.has(it))
        }
    }

    private fun payloadKeys(payload: org.json.JSONObject): Set<String> = buildSet {
        val keys = payload.keys()
        while (keys.hasNext()) add(keys.next())
    }
}

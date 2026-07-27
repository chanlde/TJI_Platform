package com.tji.device.product.droppersixstage.repository

import com.tji.device.product.droppersixstage.model.DropperSixStageCommand
import org.junit.Assert.assertEquals
import org.junit.Test

class DropperSixStageControlPayloadTest {
    @Test
    fun stageCommandContainsCurrentProtocolAndLegacyCompatibilityFields() {
        val payload = DropperSixStageCommand.StageSwitch(
            msgId = "stage-2",
            stage = 2,
            open = true,
            durationMs = 1_500
        ).toDropperControlJson(timestampMillis = 123)

        assertEquals(1, payload.getInt("v"))
        assertEquals("stage-2", payload.getString("msgId"))
        assertEquals(123L, payload.getLong("ts"))
        assertEquals(10, payload.getInt("cmd"))
        assertEquals("SET_STAGE_SWITCH", payload.getString("cmdName"))
        assertEquals(2, payload.getInt("stage"))
        assertEquals(true, payload.getBoolean("open"))
        assertEquals(1_500, payload.getInt("durationMs"))
        assertEquals("firedrop", payload.getString("module"))
        assertEquals("set_hook", payload.getString("action"))
        assertEquals(2, payload.getInt("hook"))
        assertEquals("open", payload.getString("state"))
        assertEquals(1_500, payload.getInt("duration"))
    }

    @Test
    fun allStagesAndPingUseDocumentedCommandCodes() {
        val all = DropperSixStageCommand.AllStages(
            msgId = "all-close",
            open = false
        ).toDropperControlJson(timestampMillis = 456)
        val ping = DropperSixStageCommand.Ping("ping")
            .toDropperControlJson(timestampMillis = 789)

        assertEquals(11, all.getInt("cmd"))
        assertEquals("SET_ALL_STAGES", all.getString("cmdName"))
        assertEquals(false, all.getBoolean("open"))
        assertEquals("close_all", all.getString("action"))
        assertEquals(0, ping.getInt("cmd"))
        assertEquals("PING", ping.getString("cmdName"))
        assertEquals("query", ping.getString("action"))
    }
}

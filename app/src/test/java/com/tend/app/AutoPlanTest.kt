package com.tend.app

import com.tend.app.ai.AiAction
import com.tend.app.ai.AiProtocol
import com.tend.app.notif.ReminderScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class AutoPlanTest {

    @Test
    fun `auto-plan response parses into blocks`() {
        val raw = """
            {"reply": "Planned your morning.",
             "blocks": [
               {"title": "Deep work", "start": "09:00", "durationMin": 90, "kind": "focus"},
               {"title": "Lunch", "start": "13:00", "durationMin": 45, "kind": "event"}
             ]}
        """.trimIndent()
        val (reply, blocks) = AiProtocol.parseAutoPlan(raw)!!
        assertEquals("Planned your morning.", reply)
        assertEquals(2, blocks.size)
        assertEquals(9 * 60, blocks[0].startMin)
        assertEquals(90, blocks[0].durationMin)
        assertEquals("focus", blocks[0].kind)
        assertEquals("event", blocks[1].kind)
    }

    @Test
    fun `garbage auto-plan response returns null`() {
        assertNull(AiProtocol.parseAutoPlan("sorry, I cannot help with that"))
    }

    @Test
    fun `blocks overlapping fixed items are dropped`() {
        val blocks = listOf(
            AiAction.AddPlanBlock("Overlaps meeting", 10 * 60, 60),       // 10:00-11:00 clashes
            AiAction.AddPlanBlock("Fits after", 11 * 60 + 15, 45),        // fine
            AiAction.AddPlanBlock("Overlaps sibling", 11 * 60 + 30, 30),  // clashes with previous kept block
        )
        val busy = listOf(10 * 60 to 11 * 60) // fixed meeting 10:00-11:00
        val kept = AiProtocol.filterOverlaps(blocks, busy)
        assertEquals(listOf("Fits after"), kept.map { it.title })
    }

    @Test
    fun `next reminder picks the earliest upcoming occurrence`() {
        val zone = ZoneOffset.UTC
        val today = LocalDate.of(2026, 7, 10)
        val now = today.atTime(12, 0).toInstant(zone).toEpochMilli()

        val next = ReminderScheduler.computeNextMillis(
            nowMillis = now,
            zone = zone,
            habitReminderMins = listOf(8 * 60, 14 * 60),   // 8:00 rolls to tomorrow; 14:00 upcoming
            taskDues = listOf(today.toEpochDay() to 13 * 60), // 13:00 today — the earliest
            checkinMin = 21 * 60 + 30,
        )
        assertEquals(today.atTime(13, 0).toInstant(zone).toEpochMilli(), next)
    }

    @Test
    fun `daily reminders roll over to tomorrow when already past`() {
        val zone = ZoneOffset.UTC
        val today = LocalDate.of(2026, 7, 10)
        val now = today.atTime(22, 0).toInstant(zone).toEpochMilli()

        val next = ReminderScheduler.computeNextMillis(
            nowMillis = now,
            zone = zone,
            habitReminderMins = listOf(8 * 60),
            taskDues = emptyList(),
            checkinMin = 21 * 60 + 30, // already fired today → tomorrow
        )
        assertEquals(today.plusDays(1).atTime(8, 0).toInstant(zone).toEpochMilli(), next)
    }

    @Test
    fun `no reminders means no alarm`() {
        assertNull(
            ReminderScheduler.computeNextMillis(
                nowMillis = 0L,
                zone = ZoneOffset.UTC,
                habitReminderMins = emptyList(),
                taskDues = emptyList(),
                checkinMin = null,
            )
        )
    }
}

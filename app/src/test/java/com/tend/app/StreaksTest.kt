package com.tend.app

import com.tend.app.domain.Streaks
import com.tend.app.domain.Time
import org.junit.Assert.assertEquals
import org.junit.Test

class StreaksTest {
    private val today = 20_000L

    @Test
    fun `current streak counts consecutive days ending today`() {
        val done = setOf(today, today - 1, today - 2, today - 5)
        assertEquals(3, Streaks.current(done, today))
    }

    @Test
    fun `current streak falls back to yesterday when today unchecked`() {
        val done = setOf(today - 1, today - 2, today - 3)
        assertEquals(3, Streaks.current(done, today))
    }

    @Test
    fun `current streak is zero with a gap yesterday and today`() {
        val done = setOf(today - 2, today - 3)
        assertEquals(0, Streaks.current(done, today))
    }

    @Test
    fun `best streak finds the longest run`() {
        val done = setOf(1L, 2L, 3L, 7L, 8L, 9L, 10L, 15L)
        assertEquals(4, Streaks.best(done))
    }

    @Test
    fun `rate is percentage of trailing window`() {
        val done = ((today - 14)..today).toSet() // 15 of the last 30 days
        assertEquals(50, Streaks.rate(done, today, window = 30))
    }

    @Test
    fun `rate window is clipped to the habit's creation day`() {
        // Habit created 5 days ago, done every day since: 5/5 = 100%, not 5/30.
        val done = ((today - 4)..today).toSet()
        assertEquals(100, Streaks.rate(done, today, window = 30, sinceDay = today - 4))
    }

    @Test
    fun `rate handles a habit created today`() {
        assertEquals(100, Streaks.rate(setOf(today), today, window = 30, sinceDay = today))
        assertEquals(0, Streaks.rate(emptySet(), today, window = 30, sinceDay = today))
    }

    @Test
    fun `time formatting matches the design`() {
        assertEquals("7:30", Time.clock(450))
        assertEquals("7:45 AM", Time.clockAmPm(465))
        assertEquals("7:30 – 7:45 AM · 15 min", Time.range(450, 465))
        assertEquals("1h 30m", Time.duration(90))
        assertEquals("1h", Time.duration(60))
        assertEquals("9.6h", Time.hours(576))
    }
}

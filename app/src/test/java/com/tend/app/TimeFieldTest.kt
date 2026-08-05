package com.tend.app

import com.tend.app.ui.components.parseClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The typed-time parser.
 *
 * Typing is the fast path in the new time field, so this is where a bad input
 * would quietly become midnight on someone's reminder. Every case here is a
 * form a person plausibly types.
 */
class TimeFieldTest {

    private fun at(hour: Int, minute: Int = 0) = hour * 60 + minute

    @Test
    fun `reads the common written forms`() {
        assertEquals(at(9, 30), parseClock("9:30"))
        assertEquals(at(9, 30), parseClock("930"))
        assertEquals(at(21, 30), parseClock("21:30"))
        assertEquals(at(21, 30), parseClock("2130"))
        assertEquals(at(0, 15), parseClock("0:15"))
    }

    @Test
    fun `honours am and pm however they are spaced`() {
        assertEquals(at(21, 30), parseClock("9:30 pm"))
        assertEquals(at(21, 30), parseClock("9:30PM"))
        assertEquals(at(21), parseClock("9 pm"))
        assertEquals(at(9, 30), parseClock("9:30 am"))
        assertEquals(at(0), parseClock("12:00 am"))
        assertEquals(at(12), parseClock("12:00 pm"))
    }

    @Test
    fun `a bare afternoon hour is read as afternoon`() {
        // Matches the offline parser's rule: "at 5" almost never means 5am.
        assertEquals(at(17), parseClock("5"))
        assertEquals(at(19), parseClock("7"))
        // …but only up to 7, so a genuine morning hour is left alone.
        assertEquals(at(8), parseClock("8"))
        assertEquals(at(9, 30), parseClock("9:30"))
    }

    @Test
    fun `an explicit meridiem always wins over the bare-hour guess`() {
        assertEquals(at(5), parseClock("5 am"))
        assertEquals(at(17), parseClock("5 pm"))
    }

    @Test
    fun `nonsense returns null rather than midnight`() {
        // The dangerous failure mode: a half-typed value silently becoming 00:00
        // on a reminder the user then never receives.
        assertNull(parseClock(""))
        assertNull(parseClock("   "))
        assertNull(parseClock("abc"))
        assertNull(parseClock("25:00"))
        assertNull(parseClock("9:99"))
        assertNull(parseClock("123456"))
    }

    @Test
    fun `partial minutes are padded the way they are typed`() {
        // "9:3" is on the way to 9:30, not 9:03.
        assertEquals(at(9, 30), parseClock("9:3"))
    }
}

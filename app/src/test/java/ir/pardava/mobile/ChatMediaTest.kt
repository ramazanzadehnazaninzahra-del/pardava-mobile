package ir.pardava.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ir.pardava.mobile.core.chatDayLabel
import ir.pardava.mobile.core.tehranClock

class ChatMediaTest {

    @Test
    fun `utc server time shifts to tehran clock`() {
        // 19:45 UTC + 3:30 → 23:15 Tehran
        assertEquals("۲۳:۱۵", tehranClock("2026-09-24 19:45:00", "fa"))
        assertEquals("23:15", tehranClock("2026-09-24 19:45:00", "en"))
    }

    @Test
    fun `offset crossing midnight wraps clock`() {
        // 21:30 UTC + 3:30 → 01:00 next day
        assertEquals("۰۱:۰۰", tehranClock("2026-09-24 21:30:00", "fa"))
    }

    @Test
    fun `invalid timestamps yield null`() {
        assertNull(tehranClock(null, "fa"))
        assertNull(tehranClock("short", "fa"))
        assertNull(tehranClock("2026-09-24 ab:cd:00", "fa"))
    }

    @Test
    fun `jalali day label for old dates`() {
        // 2026-01-01 → ۱۱ دی ۱۴۰۴
        val label = chatDayLabel("2026-01-01 10:00:00", "fa")
        assertEquals("۱۱ دی", label)
        // English falls back to a gregorian numeric label
        val en = chatDayLabel("2026-01-01 10:00:00", "en")
        assertTrue(en!!.contains("2026") || en.contains("/"))
    }

    @Test
    fun `today has no chip and null input is safe`() {
        assertNull(chatDayLabel(null, "fa"))
        assertNull(chatDayLabel("garbage", "fa"))
    }
}

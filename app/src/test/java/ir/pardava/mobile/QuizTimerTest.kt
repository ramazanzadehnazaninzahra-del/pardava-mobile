package ir.pardava.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ir.pardava.mobile.core.QuizTimer
import java.time.Instant

class QuizTimerTest {

    @Test
    fun `parses ISO instant with Z suffix`() {
        val t = QuizTimer.parse("2026-09-21T12:00:00Z")
        assertEquals(Instant.parse("2026-09-21T12:00:00Z"), t)
    }

    @Test
    fun `parses offset timestamps produced by the backend`() {
        // FastAPI/Pydantic emits +00:00 style offsets.
        val t = QuizTimer.parse("2026-09-21T12:00:00.123456+00:00")
        assertEquals(Instant.parse("2026-09-21T12:00:00.123456Z"), t)
    }

    @Test
    fun `null or blank yields null`() {
        assertNull(QuizTimer.parse(null))
        assertNull(QuizTimer.parse(""))
    }

    @Test
    fun `seconds left counts down and clamps at zero`() {
        val now = Instant.parse("2026-09-21T12:00:00Z")
        val exp = Instant.parse("2026-09-21T12:10:00Z")
        assertEquals(600L, QuizTimer.secondsLeft(exp, now))
        assertEquals(0L, QuizTimer.secondsLeft(exp, now.plusSeconds(700)))
        assertEquals(-1L, QuizTimer.secondsLeft(null, now))
    }

    @Test
    fun `expiry detection`() {
        val now = Instant.parse("2026-09-21T12:00:00Z")
        val exp = Instant.parse("2026-09-21T12:00:30Z")
        assertFalse(QuizTimer.isExpired(exp, now))
        assertTrue(QuizTimer.isExpired(exp, now.plusSeconds(30)))
        assertFalse(QuizTimer.isExpired(null, now))
    }
}

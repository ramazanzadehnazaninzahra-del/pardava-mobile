package ir.pardava.mobile.core

import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeFormatter

/**
 * Quiz attempt countdown. The server closes the attempt at `expires_at` (ISO-8601,
 * with a 60s network-grace window); the client only *displays* the countdown and
 * auto-submits at zero — grading itself is always server-side.
 */
object QuizTimer {

    /** Parse an ISO timestamp such as `2026-09-21T12:34:56.789012+00:00` or `...Z`. */
    fun parse(iso: String?): Instant? {
        if (iso.isNullOrBlank()) return null
        return runCatching { Instant.parse(iso) }
            .recoverCatching { OffsetDateTimeParser.parse(iso) }
            .getOrNull()
    }

    /** Seconds remaining until [expiresAt], clamped at ≥ 0. */
    fun secondsLeft(expiresAt: Instant?, now: Instant = Instant.now()): Long {
        expiresAt ?: return -1L
        val left = Duration.between(now, expiresAt).seconds
        return left.coerceAtLeast(0)
    }

    /** True when the countdown reached zero. */
    fun isExpired(expiresAt: Instant?, now: Instant = Instant.now()): Boolean {
        expiresAt ?: return false
        return !now.isBefore(expiresAt)
    }

    private object OffsetDateTimeParser {
        fun parse(iso: String): Instant =
            java.time.OffsetDateTime.parse(iso, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant()
    }
}

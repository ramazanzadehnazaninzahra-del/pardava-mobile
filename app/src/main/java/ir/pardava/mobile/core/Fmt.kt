package ir.pardava.mobile.core

import java.util.Locale

/**
 * Formatting helpers. The backend returns Western digits; the Persian UI shows
 * Persian digits (۰–۹) while the English UI keeps Latin digits.
 */
object Fmt {

    private val FA_DIGITS = charArrayOf('۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹')

    /** Convert every ASCII digit in [s] to Persian digits (other characters untouched). */
    fun toPersianDigits(s: String): String = buildString(s.length) {
        for (c in s) append(if (c in '0'..'9') FA_DIGITS[c - '0'] else c)
    }

    /** Localize digits according to [lang]. */
    fun digits(s: String, lang: String): String = if (lang == "fa") toPersianDigits(s) else s

    fun int(value: Int, lang: String): String = digits(value.toString(), lang)

    /** `mm:ss` (or `h:mm:ss` beyond one hour), localized. */
    fun duration(totalSeconds: Int, lang: String): String {
        val s = totalSeconds.coerceAtLeast(0)
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        val text = if (h > 0) {
            String.format(Locale.US, "%d:%02d:%02d", h, m, sec)
        } else {
            String.format(Locale.US, "%02d:%02d", m, sec)
        }
        return digits(text, lang)
    }

    /** Mask a phone number the way the backend does for display names: +989***111 style. */
    fun maskPhone(phone: String): String {
        if (phone.length < 7) return phone
        return phone.take(4) + "***" + phone.takeLast(3)
    }

    /** XP → level mirrors the backend formula: level(n) = (n-1)^2 * 100. */
    fun levelFor(xp: Int): Int {
        var level = 1
        while ((level) * (level) * 100L <= xp) level++
        return level
    }
}

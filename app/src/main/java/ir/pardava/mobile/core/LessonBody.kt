package ir.pardava.mobile.core

/**
 * Parser for lesson body text with in-text image markers.
 *
 * The server stores the lesson description as plain text; admins insert images
 * with `![alt](url)` markers (relative `/uploads/…` or absolute https URLs).
 * Both the website renderer and this app understand the same marker.
 */
sealed interface BodySegment {

    /** Plain text chunk (may contain single newlines → render as line breaks). */
    data class Text(val value: String) : BodySegment

    /** In-text image with optional alt text and resolved URL. */
    data class Image(val url: String, val alt: String) : BodySegment
}

object LessonBody {

    // ![alt](https://… | /…) — mirrors the server-side regex in courses_page.py
    private val IMG_RE = Regex("!\\[([^\\]]*)\\]\\(((?:https?://[^)\\s]+)|/(?:[^)\\s]+))\\)")

    /**
     * Splits the raw description into text/image segments in original order.
     * Empty text chunks are dropped; images keep their alt for accessibility.
     */
    fun parse(body: String?): List<BodySegment> {
        if (body.isNullOrBlank()) return emptyList()
        val segments = mutableListOf<BodySegment>()
        var last = 0
        for (match in IMG_RE.findAll(body)) {
            val text = body.substring(last, match.range.first)
            if (text.isNotBlank()) segments += BodySegment.Text(text.trim('\n'))
            segments += BodySegment.Image(url = match.groupValues[2], alt = match.groupValues[1].ifBlank { "" })
            last = match.range.last + 1
        }
        val tail = body.substring(last)
        if (tail.isNotBlank()) segments += BodySegment.Text(tail.trim('\n'))
        return segments
    }
}

package ir.pardava.mobile

import ir.pardava.mobile.core.BodySegment
import ir.pardava.mobile.core.LessonBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** In-text lesson image markers (v0.9.6): parse text/images in original order. */
class LessonBodyTest {

    @Test
    fun `plain text without markers yields one text segment`() {
        val segments = LessonBody.parse("متن اول\n\nمتن دوم")
        assertEquals(1, segments.size)
        assertTrue(segments[0] is BodySegment.Text)
        assertEquals("متن اول\n\nمتن دوم", (segments[0] as BodySegment.Text).value)
    }

    @Test
    fun `marker between texts splits into three segments in order`() {
        val body = "پیش\n![نمودار](/uploads/lessons/a.png)\nپس"
        val segments = LessonBody.parse(body)
        assertEquals(3, segments.size)
        assertTrue(segments[0] is BodySegment.Text)
        val img = segments[1] as BodySegment.Image
        assertEquals("/uploads/lessons/a.png", img.url)
        assertEquals("نمودار", img.alt)
        assertTrue(segments[2] is BodySegment.Text)
        assertEquals("پس", (segments[2] as BodySegment.Text).value)
    }

    @Test
    fun `absolute https urls are supported`() {
        val segments = LessonBody.parse("![pic](https://pardava.ir/uploads/lessons/x.jpg)")
        assertEquals(1, segments.size)
        assertEquals("https://pardava.ir/uploads/lessons/x.jpg", (segments[0] as BodySegment.Image).url)
    }

    @Test
    fun `null or blank body yields no segments`() {
        assertTrue(LessonBody.parse(null).isEmpty())
        assertTrue(LessonBody.parse("   ").isEmpty())
    }

    @Test
    fun `multiple images keep positions`() {
        val body = "الف ![a](/u/1.png) ب ![b](/u/2.png) ج"
        val segments = LessonBody.parse(body)
        val images = segments.filterIsInstance<BodySegment.Image>()
        assertEquals(listOf("/u/1.png", "/u/2.png"), images.map { it.url })
        assertEquals(3, segments.filterIsInstance<BodySegment.Text>().size)
    }

    @Test
    fun `image-only body still yields the image`() {
        val segments = LessonBody.parse("![x](/u/3.png)")
        assertEquals(1, segments.size)
        assertTrue(segments[0] is BodySegment.Image)
    }
}

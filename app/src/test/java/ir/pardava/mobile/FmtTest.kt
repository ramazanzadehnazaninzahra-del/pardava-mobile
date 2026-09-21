package ir.pardava.mobile

import org.junit.Assert.assertEquals
import org.junit.Test
import ir.pardava.mobile.core.Fmt

class FmtTest {

    @Test
    fun `persian digit conversion`() {
        assertEquals("۰۱۲۳۴۵۶۷۸۹", Fmt.toPersianDigits("0123456789"))
        assertEquals("۱۰٪", Fmt.toPersianDigits("10٪"))
        assertEquals("abc", Fmt.toPersianDigits("abc"))
    }

    @Test
    fun `digits respects language`() {
        assertEquals("۵", Fmt.digits("5", "fa"))
        assertEquals("5", Fmt.digits("5", "en"))
    }

    @Test
    fun `duration formats mm ss`() {
        assertEquals("05:09", Fmt.duration(309, "en"))
        assertEquals("۰۰:۰۹", Fmt.duration(9, "fa"))
        assertEquals("1:01:05", Fmt.duration(3665, "en"))
        assertEquals("00:00", Fmt.duration(-5, "en"))
    }

    @Test
    fun `phone masking matches backend style`() {
        assertEquals("+989***111", Fmt.maskPhone("+989121111111"))
        assertEquals("123456", Fmt.maskPhone("123456"))
    }

    @Test
    fun `level formula mirrors backend level n = (n-1)^2 x 100`() {
        assertEquals(1, Fmt.levelFor(0))
        assertEquals(1, Fmt.levelFor(99))
        assertEquals(2, Fmt.levelFor(100))
        assertEquals(2, Fmt.levelFor(399))
        assertEquals(3, Fmt.levelFor(400))
        assertEquals(4, Fmt.levelFor(901))
    }
}

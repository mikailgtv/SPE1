package ir.guardianlink.core.common

import org.junit.Assert.assertEquals
import org.junit.Test

class TimeFmtTest {
    @Test
    fun hmFormats() {
        assertEquals("0:00", TimeFmt.hm(0))
        assertEquals("0:45", TimeFmt.hm(45))
        assertEquals("1:00", TimeFmt.hm(60))
        assertEquals("2:34", TimeFmt.hm(154))
        assertEquals("24:00", TimeFmt.hm(1440))
        assertEquals("0:00", TimeFmt.hm(-5))
    }

    @Test
    fun compactFormats() {
        assertEquals("0m", TimeFmt.compact(0))
        assertEquals("45m", TimeFmt.compact(45))
        assertEquals("1h0m", TimeFmt.compact(60))
        assertEquals("2h34m", TimeFmt.compact(154))
    }

    @Test
    fun pctSafe() {
        assertEquals(0, TimeFmt.pct(10, 0))
        assertEquals(50, TimeFmt.pct(30, 60))
        assertEquals(25, TimeFmt.pct(30, 120))
        assertEquals(100, TimeFmt.pct(200, 120))
    }
}

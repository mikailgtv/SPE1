package ir.guardianlink.core.common

import org.junit.Assert.assertEquals
import org.junit.Test

class BatteryBandTest {
    @Test
    fun bandsWithDefaultThresholds() {
        val low = 30
        val critical = 15
        assertEquals(BatteryBand.NORMAL, BatteryBand.of(100, low, critical))
        assertEquals(BatteryBand.NORMAL, BatteryBand.of(50, low, critical))
        assertEquals(BatteryBand.NORMAL, BatteryBand.of(31, low, critical))
        assertEquals(BatteryBand.LOW, BatteryBand.of(30, low, critical))
        assertEquals(BatteryBand.LOW, BatteryBand.of(15, low, critical))
        assertEquals(BatteryBand.CRITICAL, BatteryBand.of(14, low, critical))
        assertEquals(BatteryBand.CRITICAL, BatteryBand.of(5, low, critical))
        assertEquals(BatteryBand.CRITICAL, BatteryBand.of(0, low, critical))
        assertEquals(BatteryBand.UNKNOWN, BatteryBand.of(null, low, critical))
    }

    @Test
    fun bandsWithCustomThresholds() {
        assertEquals(BatteryBand.LOW, BatteryBand.of(45, 50, 40))
        // هم‌راستا با سرور: level == criticalPct هنوز «low» است؛ critical یعنی اکیداً کمتر
        assertEquals(BatteryBand.LOW, BatteryBand.of(40, 50, 40))
        assertEquals(BatteryBand.CRITICAL, BatteryBand.of(39, 50, 40))
        assertEquals(BatteryBand.NORMAL, BatteryBand.of(51, 50, 40))
    }
}

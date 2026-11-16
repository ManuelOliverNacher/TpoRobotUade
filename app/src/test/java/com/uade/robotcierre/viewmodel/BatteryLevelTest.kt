package com.uade.robotcierre.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Test

class BatteryLevelTest {
    @Test
    fun umbralesDeBateria() {
        assertEquals(BatteryLevel.OK, BatteryLevel.of(100))
        assertEquals(BatteryLevel.OK, BatteryLevel.of(21))
        assertEquals(BatteryLevel.WARNING, BatteryLevel.of(20))
        assertEquals(BatteryLevel.WARNING, BatteryLevel.of(10))
        assertEquals(BatteryLevel.CRITICAL, BatteryLevel.of(9))
        assertEquals(BatteryLevel.CRITICAL, BatteryLevel.of(0))
    }
}

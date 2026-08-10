package com.delizioso.app.ui.screens.cook

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StepTimerTest {

    @Test
    fun `parses minutes seconds and hours`() {
        assertEquals(180, StepTimer.parseSeconds("Set a timer for 3 minutes for a soft yolk."))
        assertEquals(30, StepTimer.parseSeconds("Blanch for 30 seconds"))
        assertEquals(3600, StepTimer.parseSeconds("Let the dough rest 1 hour"))
        assertEquals(600, StepTimer.parseSeconds("Bake 10 mins until golden"))
    }

    @Test
    fun `takes the first bound of a range`() {
        assertEquals(600, StepTimer.parseSeconds("Simmer 10-12 minutes"))
    }

    @Test
    fun `untimed steps yield null`() {
        assertNull(StepTimer.parseSeconds("Chop the onion finely"))
        assertNull(StepTimer.parseSeconds("Season with 2 tsp salt"))
    }

    @Test
    fun `absurd durations are rejected`() {
        assertNull(StepTimer.parseSeconds("Cure for 48 hours"))
    }

    @Test
    fun `formats as mm ss`() {
        assertEquals("03:00", StepTimer.format(180))
        assertEquals("00:09", StepTimer.format(9))
        assertEquals("00:00", StepTimer.format(-5))
    }
}

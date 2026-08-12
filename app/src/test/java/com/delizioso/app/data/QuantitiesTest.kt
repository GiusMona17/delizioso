package com.delizioso.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QuantitiesTest {

    /** "1.5" is one and a half, not the fraction one fifth. */
    @Test
    fun `decimals are not read as fractions`() {
        assertEquals(1.5, Quantities.parse("1.5")!!, 0.0001)
        assertEquals(1.5, Quantities.parse("1,5")!!, 0.0001)
        assertEquals(0.2, Quantities.parse("1/5")!!, 0.0001)
        assertEquals(2.75, Quantities.parse("2.75")!!, 0.0001)
    }

    @Test
    fun `whole numbers, fractions and mixed numbers`() {
        assertEquals(2.0, Quantities.parse("2")!!, 0.0001)
        assertEquals(0.5, Quantities.parse("1/2")!!, 0.0001)
        assertEquals(1.5, Quantities.parse("1 1/2")!!, 0.0001)
        assertEquals(0.5, Quantities.parse("½")!!, 0.0001)
        assertEquals(2.5, Quantities.parse("2½")!!, 0.0001)
    }

    @Test
    fun `non-numeric amounts are rejected rather than guessed`() {
        assertNull(Quantities.parse("q.b."))
        assertNull(Quantities.parse("a pinch"))
    }

    @Test
    fun `scaling a decimal amount is right`() {
        assertEquals("3", Quantities.scale("1.5", 2.0))
        assertEquals("q.b.", Quantities.scale("q.b.", 2.0))
    }
}

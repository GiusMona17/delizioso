package com.delizioso.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class QuantitiesScaleInTextTest {

    @Test
    fun `amounts with a unit of measure scale`() {
        assertEquals("Aggiungi 400 g di farina", Quantities.scaleInText("Aggiungi 200 g di farina", 2.0))
        assertEquals("Versa 125 ml di latte", Quantities.scaleInText("Versa 250 ml di latte", 0.5))
        assertEquals("Unisci 3 cucchiai di olio", Quantities.scaleInText("Unisci 1.5 cucchiai di olio", 2.0))
    }

    /** Doubling a recipe does not double the oven or the clock. */
    @Test
    fun `times and temperatures are never scaled`() {
        assertEquals("Cuoci per 20 minuti", Quantities.scaleInText("Cuoci per 20 minuti", 2.0))
        assertEquals("Inforna a 180 °C", Quantities.scaleInText("Inforna a 180 °C", 2.0))
        assertEquals("Bake for 30 min at 200 C", Quantities.scaleInText("Bake for 30 min at 200 C", 3.0))
        assertEquals("Riposa 2 ore", Quantities.scaleInText("Riposa 2 ore", 2.0))
    }

    @Test
    fun `plain counts without a known unit are left alone`() {
        assertEquals("Taglia in 4 parti", Quantities.scaleInText("Taglia in 4 parti", 2.0))
        assertEquals("Passaggio 3 di 5", Quantities.scaleInText("Passaggio 3 di 5", 2.0))
    }

    @Test
    fun `countable kitchen units do scale`() {
        assertEquals("Sbatti 4 uova", Quantities.scaleInText("Sbatti 2 uova", 2.0))
        assertEquals("Trita 2 spicchi d'aglio", Quantities.scaleInText("Trita 1 spicchi d'aglio", 2.0))
    }

    @Test
    fun `a factor of one changes nothing`() {
        val text = "Aggiungi 200 g di farina e cuoci 20 minuti"
        assertEquals(text, Quantities.scaleInText(text, 1.0))
    }

    @Test
    fun `fractions are understood`() {
        assertEquals("Usa 1 cup di riso", Quantities.scaleInText("Usa 1/2 cup di riso", 2.0))
    }
}

package com.delizioso.app.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImperialUnitsTest {

    @Test
    fun `spots the units an Italian kitchen has no feel for`() {
        assertTrue(ImperialUnits.isPresentIn("2/3 cups jasmine rice"))
        assertTrue(ImperialUnits.isPresentIn("10 oz thinly sliced beef"))
        assertTrue(ImperialUnits.isPresentIn("2 tbsp soy sauce"))
        assertTrue(ImperialUnits.isPresentIn("Bake at 350 F for 20 minutes"))
        assertTrue(ImperialUnits.isPresentIn("1 lb ground pork"))
    }

    @Test
    fun `metric recipes are left alone`() {
        assertFalse(ImperialUnits.isPresentIn("200 g di pasta"))
        assertFalse(ImperialUnits.isPresentIn("500 ml di latte, 180 °C"))
        assertFalse(ImperialUnits.isPresentIn("Cuoci per 30 minuti"))
    }

    @Test
    fun `does not fire on words that merely contain a unit`() {
        // "occupato" contains "cup", "boiled" contains "oz"? — guard the word boundary.
        assertFalse(ImperialUnits.isPresentIn("Il piano è occupato dalla pentola"))
        assertFalse(ImperialUnits.isPresentIn("Mescola con un cucchiaio"))
    }
}

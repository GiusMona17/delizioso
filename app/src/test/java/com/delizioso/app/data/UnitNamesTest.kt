package com.delizioso.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class UnitNamesTest {

    /** The case that forced this table: NMT read "cloves" as the spice. */
    @Test
    fun `countable units are translated correctly`() {
        assertEquals("spicchi", UnitNames.localize("cloves", "it"))
        assertEquals("spicchio", UnitNames.localize("clove", "it"))
        assertEquals("fette", UnitNames.localize("slices", "it"))
        assertEquals("pizzico", UnitNames.localize("pinch", "it"))
    }

    @Test
    fun `metric symbols are never touched`() {
        assertEquals("g", UnitNames.localize("g", "it"))
        assertEquals("ml", UnitNames.localize("ml", "it"))
        assertEquals("kg", UnitNames.localize("kg", "it"))
    }

    @Test
    fun `unknown words and other languages are left alone`() {
        assertEquals("blorps", UnitNames.localize("blorps", "it"))
        assertEquals("cloves", UnitNames.localize("cloves", "fr"))
        assertEquals("cloves", UnitNames.localize("cloves", "en"))
        assertEquals(null, UnitNames.localize(null, "it"))
    }

    @Test
    fun `capitalisation is preserved`() {
        assertEquals("Spicchi", UnitNames.localize("Cloves", "it"))
    }
}

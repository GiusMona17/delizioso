package com.delizioso.app.data.import

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IngredientParserTrailingTest {

    @Test
    fun `parses Italian trailing quantities from GialloZafferano correctly`() {
        val spaghetti = IngredientParser.split("Spaghetti 320 g")
        assertEquals("320", spaghetti.quantity)
        assertEquals("g", spaghetti.unit)
        assertEquals("Spaghetti", spaghetti.name)

        val guanciale = IngredientParser.split("Guanciale 150g")
        assertEquals("150", guanciale.quantity)
        assertEquals("g", guanciale.unit)
        assertEquals("Guanciale", guanciale.name)

        val pecorino = IngredientParser.split("Pecorino Romano DOP 50 gr")
        assertEquals("50", pecorino.quantity)
        assertEquals("gr", pecorino.unit)
        assertEquals("Pecorino Romano DOP", pecorino.name)

        val tuorli = IngredientParser.split("Tuorli di uova medie 6")
        assertEquals("6", tuorli.quantity)
        assertNull(tuorli.unit)
        assertEquals("Tuorli di uova medie", tuorli.name)

        val uova = IngredientParser.split("Uova 4")
        assertEquals("4", uova.quantity)
        assertNull(uova.unit)
        assertEquals("Uova", uova.name)
    }

    @Test
    fun `parses standard leading quantities correctly`() {
        val flour = IngredientParser.split("200 g di farina 00")
        assertEquals("200", flour.quantity)
        assertEquals("g", flour.unit)
        assertEquals("farina 00", flour.name)

        val milk = IngredientParser.split("150ml latte")
        assertEquals("150", milk.quantity)
        assertEquals("ml", milk.unit)
        assertEquals("latte", milk.name)

        val eggs = IngredientParser.split("3 uova")
        assertEquals("3", eggs.quantity)
        assertNull(eggs.unit)
        assertEquals("uova", eggs.name)
    }

    @Test
    fun `parses q b and to taste expressions correctly`() {
        val sale = IngredientParser.split("Sale fino q.b.")
        assertNull(sale.quantity)
        assertEquals("q.b.", sale.unit)
        assertEquals("Sale fino", sale.name)

        val pepe = IngredientParser.split("Pepe nero - quanto basta")
        assertNull(pepe.quantity)
        assertEquals("q.b.", pepe.unit)
        assertEquals("Pepe nero", pepe.name)
    }
}

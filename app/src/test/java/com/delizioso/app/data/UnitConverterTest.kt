package com.delizioso.app.data

import com.delizioso.app.data.import.IngredientParser
import com.delizioso.app.data.import.StructuredRecipe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UnitConverterTest {

    @Test
    fun `weights become grams`() {
        assertEquals("280 g thinly sliced beef", UnitConverter.convertLine("10 oz thinly sliced beef"))
        assertEquals("455 g ground pork", UnitConverter.convertLine("1 lb ground pork"))
    }

    @Test
    fun `volumes become millilitres`() {
        assertEquals("30 ml soy sauce", UnitConverter.convertLine("2 tbsp soy sauce"))
        assertEquals("5 ml vanilla extract", UnitConverter.convertLine("1 tsp vanilla extract"))
        assertEquals("80 ml beef bone broth", UnitConverter.convertLine("1/3 cup beef bone broth"))
    }

    @Test
    fun `fractions and mixed numbers are understood`() {
        assertEquals("160 ml water", UnitConverter.convertLine("2/3 cup water"))
        assertEquals("25 ml oil", UnitConverter.convertLine("1 1/2 tbsp oil"))
    }

    /** A cup of flour is not a cup of milk — staples convert to grams. */
    @Test
    fun `known dry staples convert to grams, everything else to millilitres`() {
        assertEquals("120 g all-purpose flour", UnitConverter.convertLine("1 cup all-purpose flour"))
        assertEquals("185 g jasmine rice", UnitConverter.convertLine("1 cup jasmine rice"))
        assertEquals("200 g sugar", UnitConverter.convertLine("1 cup sugar"))
        // Not in the density table: millilitres are always true.
        assertEquals("240 ml chicken stock", UnitConverter.convertLine("1 cup chicken stock"))
    }

    @Test
    fun `brown sugar beats plain sugar`() {
        assertEquals("220 g brown sugar", UnitConverter.convertLine("1 cup brown sugar"))
    }

    @Test
    fun `fahrenheit becomes celsius`() {
        // Rounded to tens so the result is a temperature an oven actually has.
        assertEquals("Bake at 180 °C for 20 minutes", UnitConverter.convertLine("Bake at 350 F for 20 minutes"))
        assertEquals("Preheat to 200 °C", UnitConverter.convertLine("Preheat to 400°F"))
        assertEquals("Roast at 220 °C", UnitConverter.convertLine("Roast at 425 F"))
    }

    @Test
    fun `metric input is left exactly as it was`() {
        val metric = "200 g di pasta, 500 ml di latte, forno a 180 °C"
        assertEquals(metric, UnitConverter.convertLine(metric))
        assertEquals("Cuoci per 30 minuti", UnitConverter.convertLine("Cuoci per 30 minuti"))
    }

    @Test
    fun `a unit hiding inside a word is not converted`() {
        // "occupato" contains "cup", "once" contains "onc" — neither is a measurement.
        assertEquals("Il piano è occupato", UnitConverter.convertLine("Il piano è occupato"))
        assertEquals("Mescola con un cucchiaio", UnitConverter.convertLine("Mescola con un cucchiaio"))
        assertEquals("Aggiungi 2 uova", UnitConverter.convertLine("Aggiungi 2 uova"))
    }

    @Test
    fun `converts a whole recipe, ingredients and steps`() {
        val recipe = StructuredRecipe(
            title = "Gyudon",
            ingredients = listOf(
                IngredientParser.split("10 oz thinly sliced beef"),
                IngredientParser.split("2/3 cups jasmine rice"),
            ),
            steps = listOf("Bake at 350 F", "Add 2 tbsp soy sauce"),
        )
        val converted = UnitConverter.convert(recipe)
        val lines = converted.ingredients.map { it.rawText ?: it.name }
        assertTrue(lines[0].startsWith("280 g"))
        assertTrue(lines[1].startsWith("125 g"))
        assertEquals("Bake at 180 °C", converted.steps[0])
        assertEquals("Add 30 ml soy sauce", converted.steps[1])
    }
}

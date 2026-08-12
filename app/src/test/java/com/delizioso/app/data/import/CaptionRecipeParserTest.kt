package com.delizioso.app.data.import

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptionRecipeParserTest {

    /** The reel that regressed: macros and a "Toppings:" sub-heading in the way. */
    private val gyudon = """
        joexfitness

        ⇩ Full Recipe 🥘 ⇩

        Macros
        Protein: 42g
        Fat: 10g
        Calories: 528

        One Pot Rice Cooker Japanese Gyudon

        Ingredients per 2 servings:
        - 10 oz thinly sliced eye of round beef (shabushabu)
        - 2/3 cups jasmine rice, uncooked/raw
        - 1/3 cup beef bone broth
        - 2 tbsp soy sauce

        Toppings:
        - 1 green onion

        Instructions:
        1. Wash your rice with water.
        2. Cook for 30 minutes in your rice cooker.
        3. Boil the eggs for six minutes.

        #gyudon #mealprep
    """.trimIndent()

    @Test
    fun `splits on the caption's own headings`() {
        val recipe = CaptionRecipeParser.parse(gyudon)
        assertNotNull(recipe)
        val names = recipe!!.ingredients.map { it.rawText ?: it.name }
        assertEquals(5, names.size)
        assertTrue(names.first().startsWith("10 oz thinly sliced eye of round beef"))
        assertEquals(3, recipe.steps.size)
        assertEquals("Wash your rice with water.", recipe.steps.first())
    }

    @Test
    fun `macros, hashtags and calls to action never become content`() {
        val recipe = CaptionRecipeParser.parse(gyudon)!!
        val all = recipe.ingredients.map { it.rawText ?: it.name } + recipe.steps
        assertTrue(all.none { it.contains("Calories", ignoreCase = true) })
        assertTrue(all.none { it.contains("Full Recipe", ignoreCase = true) })
        assertTrue(all.none { it.startsWith("#") })
        // "Toppings:" is a sub-heading, not an ingredient.
        assertTrue(all.none { it.equals("Toppings:", ignoreCase = true) })
    }

    @Test
    fun `reads servings off the ingredients heading`() {
        assertEquals(2, CaptionRecipeParser.parse(gyudon)!!.servings)
    }

    @Test
    fun `bullets and step numbers are stripped`() {
        val recipe = CaptionRecipeParser.parse(gyudon)!!
        assertTrue(recipe.ingredients.none { (it.rawText ?: "").startsWith("-") })
        assertTrue(recipe.steps.none { it.first().isDigit() })
    }

    @Test
    fun `works in Italian`() {
        val italian = """
            Pasta al pesto

            INGREDIENTI
            - 200 g di pasta
            - 50 g di pesto

            PROCEDIMENTO
            1. Cuoci la pasta.
            2. Manteca con il pesto.
        """.trimIndent()
        val recipe = CaptionRecipeParser.parse(italian)!!
        assertEquals(2, recipe.ingredients.size)
        assertEquals(2, recipe.steps.size)
        assertEquals("Pasta al pesto", recipe.title)
    }

    @Test
    fun `captions without headings are left to the model`() {
        assertNull(CaptionRecipeParser.parse("Selfie prank 😂"))
        assertNull(CaptionRecipeParser.parse("Just mix flour and water and bake it, trust me"))
        // Ingredients declared but no method — not enough to be confident.
        assertNull(CaptionRecipeParser.parse("Ingredients:\n- flour\n- water"))
    }
}

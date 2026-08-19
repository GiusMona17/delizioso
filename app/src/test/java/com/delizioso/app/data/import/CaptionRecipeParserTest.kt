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

    /**
     * From a real reel: the caption labels its sections "Ingredienti" and "Mini
     * procedimento". Anchoring the keyword to the start of the line missed the
     * qualified heading, so the whole recipe fell through to the model and was
     * imported as an empty shell.
     */
    @Test
    fun `a heading with a qualifying word is still a heading`() {
        val caption = """
            Benvenuti in UDON LAB.

            Ingredienti

            200 g udon
            2 cucchiai tahina

            Mini procedimento

            Cuoci gli udon.
            Mescola tahina e soia.
        """.trimIndent()

        val recipe = CaptionRecipeParser.parse(caption)!!
        assertEquals(listOf("200 g udon", "2 cucchiai tahina"), recipe.ingredients.map { it.rawText })
        assertEquals(listOf("Cuoci gli udon.", "Mescola tahina e soia."), recipe.steps)
    }

    /** "Full ingredients" / "Quick method" are headings in English captions too. */
    @Test
    fun `qualified headings work in english`() {
        val caption = """
            Honey chicken

            Full ingredients

            700 g chicken

            Quick method

            Fry the chicken.
        """.trimIndent()
        val recipe = CaptionRecipeParser.parse(caption)!!
        assertEquals(listOf("700 g chicken"), recipe.ingredients.map { it.rawText })
        assertEquals(listOf("Fry the chicken."), recipe.steps)
    }

    /** A sentence that merely mentions the word must not open a section. */
    @Test
    fun `a prose line mentioning the keyword is not a heading`() {
        val caption = """
            Torta

            Ingredienti

            200 g farina

            Procedimento

            Aggiungi gli ingredienti nella ciotola e mescola bene.
            Inforna per 30 minuti.
        """.trimIndent()
        val recipe = CaptionRecipeParser.parse(caption)!!
        assertEquals(listOf("200 g farina"), recipe.ingredients.map { it.rawText })
        assertEquals(2, recipe.steps.size)
    }

    /**
     * From the same real reel: the blurb says "Nuovi ingredienti." long before the
     * actual "Ingredienti" heading. Allowing a qualifying word made that promo
     * line look like a heading, and the section opened in the wrong place.
     */
    @Test
    fun `a promo sentence ending in a full stop is not a heading`() {
        val caption = """
            Benvenuti in UDON LAB.

            Nuove salse.

            Nuovi ingredienti.

            Sempre veloci.

            Ingredienti

            200 g udon

            Mini procedimento

            Cuoci gli udon.
        """.trimIndent()

        val recipe = CaptionRecipeParser.parse(caption)!!
        assertEquals(listOf("200 g udon"), recipe.ingredients.map { it.rawText })
        assertEquals(listOf("Cuoci gli udon."), recipe.steps)
    }
}

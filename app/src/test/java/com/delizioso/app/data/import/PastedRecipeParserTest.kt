package com.delizioso.app.data.import

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PastedRecipeParserTest {

    @Test
    fun `headings win when the text has them`() {
        val recipe = PastedRecipeParser.parse(
            """
            Pasta al pomodoro
            INGREDIENTI:
            200 g di spaghetti
            300 g di passata
            PROCEDIMENTO:
            Cuoci la pasta in acqua salata.
            Aggiungi la passata e servi.
            """.trimIndent()
        )
        assertEquals("Pasta al pomodoro", recipe.title)
        assertEquals(2, recipe.ingredients.size)
        assertEquals(2, recipe.steps.size)
    }

    @Test
    fun `without headings, amounts are ingredients and sentences are steps`() {
        val recipe = PastedRecipeParser.parse(
            """
            Carbonara
            200 g spaghetti
            100 g guanciale
            2 uova
            Sale
            Rosola il guanciale in padella finché non è croccante.
            Sbatti le uova con il pecorino e unisci alla pasta.
            """.trimIndent()
        )
        assertEquals("Carbonara", recipe.title)
        assertEquals(listOf("200 g spaghetti", "100 g guanciale", "2 uova", "Sale"), recipe.ingredients.map { it.rawText })
        assertEquals(2, recipe.steps.size)
        assertTrue(recipe.steps[0].startsWith("Rosola"))
    }

    /** A numbered instruction opens with a digit but is not an amount. */
    @Test
    fun `numbered steps are not mistaken for ingredients`() {
        val recipe = PastedRecipeParser.parse(
            """
            Pancakes
            - 2 uova
            - 250 ml latte
            1. Mescola gli ingredienti secchi in una ciotola.
            2. Aggiungi il latte e le uova, poi mescola bene.
            """.trimIndent()
        )
        assertEquals(2, recipe.ingredients.size)
        assertEquals(2, recipe.steps.size)
        assertEquals("Mescola gli ingredienti secchi in una ciotola.", recipe.steps[0])
    }

    @Test
    fun `bullets and social noise are dropped`() {
        val recipe = PastedRecipeParser.parse(
            """
            Riso al curry
            #food #ricette
            • 200 g riso
            Link in bio per la ricetta completa
            Fai bollire il riso per dieci minuti.
            """.trimIndent()
        )
        assertEquals(listOf("200 g riso"), recipe.ingredients.map { it.rawText })
        assertEquals(listOf("Fai bollire il riso per dieci minuti."), recipe.steps)
    }

    @Test
    fun `empty text produces an empty recipe rather than throwing`() {
        val recipe = PastedRecipeParser.parse("   \n\n  ")
        assertEquals(null, recipe.title)
        assertTrue(recipe.ingredients.isEmpty())
        assertTrue(recipe.steps.isEmpty())
    }
}

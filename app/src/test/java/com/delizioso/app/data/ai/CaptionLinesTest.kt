package com.delizioso.app.data.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptionLinesTest {

    private val caption = """
        joexfitness

        ⇩ Full Recipe 🥘 ⇩

        INGREDIENTS
        10 oz thinly sliced beef
        2/3 cups jasmine rice
        2 tbsp soy sauce

        INSTRUCTIONS
        1. Wash your rice with water.
        2. Cook for 30 minutes in your rice cooker.
    """.trimIndent()

    @Test
    fun `blank lines are dropped so numbering is stable`() {
        val lines = CaptionLines.split(caption)
        assertEquals("joexfitness", lines[0])
        assertEquals("INGREDIENTS", lines[2])
        assertEquals("10 oz thinly sliced beef", lines[3])
    }

    @Test
    fun `numbering is one-based`() {
        val numbered = CaptionLines.numbered(CaptionLines.split(caption))
        assertTrue(numbered.startsWith("1. joexfitness"))
        assertTrue(numbered.contains("4. 10 oz thinly sliced beef"))
    }

    @Test
    fun `a range resolves to the author's own wording`() {
        val lines = CaptionLines.split(caption)
        assertEquals(
            listOf("10 oz thinly sliced beef", "2/3 cups jasmine rice", "2 tbsp soy sauce"),
            CaptionLines.resolve(lines, listOf("4-6")),
        )
    }

    @Test
    fun `single indices and reversed ranges both work`() {
        val lines = CaptionLines.split(caption)
        assertEquals(listOf("2 tbsp soy sauce"), CaptionLines.resolve(lines, listOf("6")))
        assertEquals(2, CaptionLines.resolve(lines, listOf("6-5")).size)
    }

    @Test
    fun `out of range references are dropped, not guessed`() {
        val lines = CaptionLines.split(caption)
        assertEquals(emptyList<String>(), CaptionLines.resolve(lines, listOf("99", "0")))
    }

    @Test
    fun `falls back to the text when the model answers with content`() {
        val lines = CaptionLines.split(caption)
        assertEquals(
            listOf("2 tbsp soy sauce", "1 tbsp mirin"),
            CaptionLines.resolve(lines, listOf("2 tbsp soy sauce", "- 1 tbsp mirin")),
        )
    }

    @Test
    fun `bullets and step numbers are stripped from resolved lines`() {
        val lines = CaptionLines.split(caption)
        assertEquals(
            listOf("Wash your rice with water.", "Cook for 30 minutes in your rice cooker."),
            CaptionLines.resolve(lines, listOf("8-9")),
        )
        val bulleted = listOf("- 2 tbsp soy sauce", "• 1 tbsp mirin")
        assertEquals(listOf("2 tbsp soy sauce", "1 tbsp mirin"), CaptionLines.resolve(bulleted, listOf("1-2")))
    }

    @Test
    fun `duplicate references collapse`() {
        val lines = CaptionLines.split(caption)
        assertEquals(1, CaptionLines.resolve(lines, listOf("4", "4")).size)
    }
}

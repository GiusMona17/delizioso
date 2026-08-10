package com.delizioso.app.data.ai

import com.delizioso.app.data.import.StructuredRecipe
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NanoChatTest {

    private val chat = NanoChat(consentProvider = { true })
    private val recipe = StructuredRecipe(title = "Udon", steps = listOf("Boil the noodles"))

    private fun history(turns: Int): List<ChatMessage> = (1..turns).flatMap { i ->
        listOf(
            ChatMessage(ChatMessage.Role.USER, "question $i"),
            ChatMessage(ChatMessage.Role.ASSISTANT, "answer $i"),
        )
    }

    @Test
    fun `keeps the whole history when it fits`() = runTest {
        val prompt = chat.trimToFit(recipe, history(3), "and now?", budget = 10_000) { it.length / 4 }
        assertTrue(prompt.contains("question 1"))
        assertTrue(prompt.contains("question 3"))
        assertTrue(prompt.contains("and now?"))
    }

    @Test
    fun `drops the oldest exchanges first when over budget`() = runTest {
        val full = chat.trimToFit(recipe, history(5), "and now?", budget = 10_000) { it.length / 4 }
        // A budget between "recipe only" and "recipe plus all history" forces trimming.
        val budget = (full.length / 4) - 20
        val prompt = chat.trimToFit(recipe, history(5), "and now?", budget) { it.length / 4 }
        assertFalse("oldest turn should be dropped", prompt.contains("question 1"))
        assertTrue("newest turn should survive", prompt.contains("question 5"))
    }

    @Test
    fun `always keeps the recipe and the question even at an impossible budget`() = runTest {
        val prompt = chat.trimToFit(recipe, history(4), "help", budget = 1) { it.length / 4 }
        assertTrue(prompt.contains("Udon"))
        assertTrue(prompt.contains("help"))
        // History is gone, so the loop terminated instead of spinning forever.
        assertFalse(prompt.contains("question 1"))
    }
}

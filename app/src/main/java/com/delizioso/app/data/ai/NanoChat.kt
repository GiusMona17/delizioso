package com.delizioso.app.data.ai

import com.delizioso.app.data.import.StructuredRecipe
import com.google.mlkit.genai.prompt.GenerateContentRequest
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.TextPart
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** One turn in a recipe conversation. */
data class ChatMessage(
    val role: Role,
    val text: String,
) {
    enum class Role { USER, ASSISTANT }
}

/**
 * "Ask about this recipe" — free-form Q&A grounded in the saved recipe.
 *
 * Gemini Nano has no conversation object: every call is one-shot, so each turn
 * re-sends the recipe plus the recent history as a single prompt. The context
 * window is small, so [trimToFit] drops the oldest turns until the prompt
 * actually fits the model's own reported limit rather than a guessed character
 * count.
 */
class NanoChat(
    private val consentProvider: suspend () -> Boolean,
) {

    /** Share of the context window left free for the answer. */
    private val outputReserve = 0.35

    suspend fun availability(): NanoInference.Availability =
        NanoInference.availability(consentProvider())

    /**
     * Streams the answer, emitting the text accumulated so far so the UI can
     * render it as it arrives.
     */
    fun ask(
        recipe: StructuredRecipe,
        history: List<ChatMessage>,
        question: String,
    ): Flow<String> = flow {
        if (!consentProvider()) throw AiUnavailableException("On-device AI consent required")
        NanoInference.ensureDownloaded()
        val model = Generation.getClient()

        val limit = runCatching { model.getTokenLimit() }.getOrNull() ?: DEFAULT_TOKEN_LIMIT
        val budget = ((1.0 - outputReserve) * limit).toInt()
        val prompt = trimToFit(recipe, history, question, budget) { text ->
            runCatching {
                model.countTokens(
                    GenerateContentRequest.builder(TextPart(text)).build()
                ).totalTokens
            }.getOrNull() ?: estimateTokens(text)
        }

        var answer = ""
        model.generateContentStream(prompt).collect { response ->
            val chunk = response.candidates.firstOrNull()?.text.orEmpty()
            if (chunk.isEmpty()) return@collect
            // Streaming may emit deltas or the whole answer so far — handle both.
            answer = if (chunk.startsWith(answer) && chunk.length >= answer.length) chunk else answer + chunk
            emit(answer)
        }
        if (answer.isBlank()) throw AiUnavailableException("Gemini Nano returned no answer")
    }

    /** Builds the prompt, dropping the oldest turns until it fits [budget] tokens. */
    internal suspend fun trimToFit(
        recipe: StructuredRecipe,
        history: List<ChatMessage>,
        question: String,
        budget: Int,
        countTokens: suspend (String) -> Int,
    ): String {
        var kept = history
        while (true) {
            val prompt = ChatPrompt.build(recipe, kept, question)
            if (kept.isEmpty() || countTokens(prompt) <= budget) return prompt
            // Drop the oldest exchange (a user turn and its answer).
            kept = kept.drop(2)
        }
    }

    private companion object {
        /** Nano's window when the model won't say; deliberately conservative. */
        const val DEFAULT_TOKEN_LIMIT = 1024

        /** Rough fallback when countTokens is unavailable: ~4 characters per token. */
        fun estimateTokens(text: String): Int = text.length / 4
    }
}

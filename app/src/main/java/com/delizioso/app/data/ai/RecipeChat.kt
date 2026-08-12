package com.delizioso.app.data.ai

import com.delizioso.app.data.import.StructuredRecipe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Routes a recipe question to the best model installed.
 *
 * Gemini Nano is always there but AICore caps one answer at 256 tokens, which
 * cuts a substitution explanation off mid-sentence. A user-supplied Gemma model
 * has no such ceiling, so when one is installed it answers instead. Nano remains
 * the default so the chat works on a phone with nothing downloaded.
 */
class RecipeChat(
    private val nano: NanoChat,
    private val gemma: GemmaEngine,
) {

    enum class Engine { NANO, GEMMA }

    fun active(): Engine = if (gemma.isInstalled()) Engine.GEMMA else Engine.NANO

    /** True when the first Nano call still has to download the model. */
    suspend fun needsDownload(): Boolean =
        active() == Engine.NANO && nano.availability() == NanoInference.Availability.DOWNLOADABLE

    /** Streams the answer; Gemma emits once, Nano emits as tokens arrive. */
    fun ask(
        recipe: StructuredRecipe,
        history: List<ChatMessage>,
        question: String,
    ): Flow<String> = when (active()) {
        Engine.NANO -> nano.ask(recipe, history, question)
        Engine.GEMMA -> flow {
            // MediaPipe's synchronous call returns the whole answer, so there is
            // nothing to stream: the UI shows its spinner until this arrives.
            val answer = gemma.generate(
                ChatPrompt.build(recipe, history.takeLast(MAX_GEMMA_TURNS), question)
            ).trim()
            if (answer.isBlank()) throw AiUnavailableException("The model returned no answer")
            emit(answer)
        }
    }

    private companion object {
        /** Gemma's window is large, but a whole session still has to fit in it. */
        const val MAX_GEMMA_TURNS = 8
    }
}

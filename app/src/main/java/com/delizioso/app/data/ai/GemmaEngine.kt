package com.delizioso.app.data.ai

import android.content.Context
import android.net.Uri
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import java.io.File

/**
 * A user-supplied Gemma model run through MediaPipe's LLM Inference API.
 *
 * Why this exists next to [NanoStructurer]: AICore caps Gemini Nano at 256
 * output tokens, which is not enough to rewrite a recipe (translate it, convert
 * cups to grams, expand terse steps). MediaPipe lets us set the limit ourselves,
 * so the rewrite features live here.
 *
 * The model file comes from the user — Gemma downloads are licence-gated, so the
 * app never fetches one itself. [install] copies the picked `.task` into private
 * storage because the native runtime needs a real filesystem path, not a
 * content:// URI.
 */
class GemmaEngine(
    private val appContext: Context,
) {

    /** Guards the native handle: MediaPipe's session is not re-entrant. */
    private val lock = Mutex()
    private var engine: LlmInference? = null
    private var loadedFrom: String? = null

    /** Whether a model file is installed and ready to load. */
    fun isInstalled(): Boolean = modelFile().exists()

    /** Size on disk, for the settings screen. */
    fun installedSizeBytes(): Long = modelFile().takeIf { it.exists() }?.length() ?: 0L

    /**
     * Copies the user's picked model into private storage. Slow (the file is
     * hundreds of MB), so callers should run it off the main thread and show
     * progress. Returns the installed size.
     */
    suspend fun install(uri: Uri): Long = withContext(Dispatchers.IO) {
        lock.lock()
        try {
            close()
            val destination = modelFile()
            appContext.contentResolver.openInputStream(uri)?.use { input ->
                destination.outputStream().use { output -> input.copyTo(output, DEFAULT_BUFFER_SIZE) }
            } ?: throw IllegalStateException("Could not read the selected file")
            destination.length()
        } finally {
            lock.unlock()
        }
    }

    suspend fun uninstall() = withContext(Dispatchers.IO) {
        lock.lock()
        try {
            close()
            modelFile().delete()
        } finally {
            lock.unlock()
        }
    }

    /**
     * Runs [prompt] and returns the whole answer. [maxTokens] is the model's own
     * budget — unlike Nano there is no 256-token ceiling here.
     */
    suspend fun generate(prompt: String, maxTokens: Int = DEFAULT_MAX_TOKENS): String =
        withContext(Dispatchers.IO) {
            lock.lock()
            try {
                val model = engine ?: load(maxTokens).also { engine = it }
                // Sampling lives on the session, not the engine. Left at its default
                // a 1B model wanders and can lock into a repetition loop
                // ("2nd:2nd:2nd…"), so decode greedily: this job wants the most
                // likely token, not a creative one.
                val session = LlmInferenceSession.createFromOptions(
                    model,
                    LlmInferenceSession.LlmInferenceSessionOptions.builder()
                        .setTemperature(0.1f)
                        .setTopK(1)
                        .setRandomSeed(1)
                        .build(),
                )
                session.use {
                    it.addQueryChunk(prompt)
                    it.generateResponse() ?: throw AiUnavailableException("The model returned no answer")
                }
            } catch (e: AiUnavailableException) {
                throw e
            } catch (e: Exception) {
                // A failed load leaves a dead handle behind; drop it so the next
                // attempt starts clean instead of failing forever.
                close()
                throw AiUnavailableException("Gemma failed: ${e.message}", retryable = true)
            } finally {
                lock.unlock()
            }
        }

    private fun load(maxTokens: Int): LlmInference {
        val file = modelFile()
        if (!file.exists()) throw AiUnavailableException("No Gemma model is installed")
        val options = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(file.absolutePath)
            .setMaxTokens(maxTokens)
            // Ceiling for whatever the session asks for; must not be below it.
            .setMaxTopK(64)
            .build()
        loadedFrom = file.absolutePath
        return LlmInference.createFromOptions(appContext, options)
    }

    /** Frees the native handle and the RAM the weights occupy. */
    fun close() {
        runCatching { engine?.close() }
        engine = null
        loadedFrom = null
    }

    private fun modelFile() = File(appContext.filesDir, MODEL_FILE)

    private companion object {
        const val MODEL_FILE = "gemma-model.task"

        /** Enough for a rewritten recipe; Gemma 1B accepts far more. */
        const val DEFAULT_MAX_TOKENS = 2048
    }
}

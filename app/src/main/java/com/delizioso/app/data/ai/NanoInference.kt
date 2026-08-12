package com.delizioso.app.data.ai

import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.common.GenAiException
import com.google.mlkit.genai.prompt.GenerateContentRequest
import com.google.mlkit.genai.prompt.GenerateContentResponse
import com.google.mlkit.genai.prompt.GenerativeModel
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.TextPart
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect

/** Shared Gemini Nano plumbing: availability, download, quota/BUSY backoff. */
object NanoInference {

    enum class Availability { UNAVAILABLE, DOWNLOADABLE, AVAILABLE }

    /** Whether Gemini Nano can be used (consent + feature status). */
    suspend fun availability(consent: Boolean): Availability {
        if (!consent) return Availability.UNAVAILABLE
        return when (Generation.getClient().checkStatus()) {
            FeatureStatus.AVAILABLE -> Availability.AVAILABLE
            FeatureStatus.DOWNLOADABLE, FeatureStatus.DOWNLOADING -> Availability.DOWNLOADABLE
            else -> Availability.UNAVAILABLE
        }
    }

    /** Downloads Gemini Nano (no-op when already available). */
    suspend fun ensureDownloaded() {
        val model = Generation.getClient()
        when (model.checkStatus()) {
            FeatureStatus.AVAILABLE, FeatureStatus.DOWNLOADING -> Unit
            FeatureStatus.DOWNLOADABLE -> model.download().collect { status ->
                if (status is DownloadStatus.DownloadFailed) {
                    throw AiUnavailableException("Model download failed")
                }
            }
            else -> throw AiUnavailableException("Gemini Nano is not available on this device")
        }
    }

    /**
     * Builds a request that answers with as much as the platform allows.
     *
     * AICore hard-caps a single answer at [MAX_OUTPUT_TOKENS] ("maxOutputTokens
     * must be between 1 and 256"), roughly 900 characters — which is why asking
     * Nano to echo a whole recipe back as JSON truncates mid-array. Prompts have
     * to be designed for a small answer; this just makes sure we ask for all of it.
     */
    fun requestFor(prompt: String): GenerateContentRequest =
        GenerateContentRequest.builder(TextPart(prompt))
            .apply { maxOutputTokens = MAX_OUTPUT_TOKENS }
            .build()

    /** AICore's hard ceiling on one answer. Not a tuning knob — the API rejects more. */
    const val MAX_OUTPUT_TOKENS = 256

    /**
     * AICore enforces a per-app inference quota — under load it returns BUSY.
     * Retry with exponential backoff (500ms → 2s → 8s) before giving up.
     */
    suspend fun generateContentWithBackoff(
        model: GenerativeModel,
        prompt: String,
        maxAttempts: Int = 4,
    ): GenerateContentResponse {
        val request = requestFor(prompt)
        var attempt = 0
        var backoff = 500L
        while (true) {
            attempt++
            try {
                return model.generateContent(request)
            } catch (e: GenAiException) {
                if (attempt >= maxAttempts) {
                    throw AiUnavailableException(
                        "On-device AI is busy (code ${e.errorCode}); try again in a moment",
                        retryable = true,
                    )
                }
                delay(backoff)
                backoff *= 4
            } catch (e: AiUnavailableException) {
                throw e
            } catch (e: Exception) {
                throw AiUnavailableException("On-device AI failed: ${e.message}", retryable = true)
            }
        }
    }

    /**
     * Closes an object the model ran out of tokens mid-way through.
     *
     * A truncated answer still contains most of the recipe; throwing it away loses
     * every ingredient the model did get right. Rewinds to the last completed
     * member or array element, then closes whatever brackets are still open.
     * Returns null when there is nothing salvageable.
     */
    fun repairTruncatedJson(raw: String): String? {
        val start = raw.indexOf('{')
        if (start == -1) return null
        val stack = ArrayDeque<Char>()
        var inString = false
        var escaped = false
        // Index just past the last point where the document was structurally complete.
        var lastSafe = -1

        for (i in start until raw.length) {
            val c = raw[i]
            if (inString) {
                when {
                    escaped -> escaped = false
                    c == '\\' -> escaped = true
                    c == '"' -> inString = false
                }
                continue
            }
            when (c) {
                '"' -> inString = true
                '{' -> stack.addLast('}')
                '[' -> stack.addLast(']')
                '}', ']' -> {
                    if (stack.isEmpty()) return null
                    stack.removeLast()
                    lastSafe = i + 1
                }
                ',' -> lastSafe = i
            }
        }
        if (stack.isEmpty()) return raw.substring(start)
        if (lastSafe <= start) return null

        val head = raw.substring(start, lastSafe).trimEnd().trimEnd(',')
        return head + stack.reversed().joinToString("")
    }

    /** Strips markdown fences and surrounding prose, keeping the JSON object. */
    fun stripToJson(raw: String): String {
        val noFences = raw.trim()
            .removePrefix("```json").removePrefix("```")
            .removeSuffix("```").trim()
        val start = noFences.indexOf('{')
        val end = noFences.lastIndexOf('}')
        if (start == -1 || end == -1 || end <= start) {
            throw AiUnavailableException("Gemini Nano output had no JSON object")
        }
        return noFences.substring(start, end + 1)
    }
}

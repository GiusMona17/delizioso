package com.delizioso.app.data.ai

import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.common.GenAiException
import com.google.mlkit.genai.prompt.GenerateContentResponse
import com.google.mlkit.genai.prompt.GenerativeModel
import com.google.mlkit.genai.prompt.Generation
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
     * AICore enforces a per-app inference quota — under load it returns BUSY.
     * Retry with exponential backoff (500ms → 2s → 8s) before giving up.
     */
    suspend fun generateContentWithBackoff(
        model: GenerativeModel,
        prompt: String,
        maxAttempts: Int = 4,
    ): GenerateContentResponse {
        var attempt = 0
        var backoff = 500L
        while (true) {
            attempt++
            try {
                return model.generateContent(prompt)
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

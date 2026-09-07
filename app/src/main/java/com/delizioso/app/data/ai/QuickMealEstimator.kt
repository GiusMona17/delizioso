package com.delizioso.app.data.ai

import com.delizioso.app.data.export.QuickMealPrompt
import com.delizioso.app.data.import.QuickMealData
import com.delizioso.app.data.import.QuickMealJsonParser
import com.google.mlkit.genai.prompt.Generation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class QuickMealEstimator(
    private val consentProvider: suspend () -> Boolean = { true }
) {

    suspend fun isAvailable(): Boolean =
        NanoInference.availability(consentProvider()) == NanoInference.Availability.AVAILABLE

    suspend fun estimate(foodDescription: String, targetLanguage: String): QuickMealData? = withContext(Dispatchers.IO) {
        if (!isAvailable()) return@withContext null
        runCatching {
            val prompt = QuickMealPrompt.buildPrompt(foodDescription, targetLanguage)
            val client = Generation.getClient()
            val response = NanoInference.generateContentWithBackoff(client, prompt)
            val raw = response.candidates.firstOrNull()?.text ?: return@runCatching null
            QuickMealJsonParser.parse(raw)
        }.getOrNull()
    }
}

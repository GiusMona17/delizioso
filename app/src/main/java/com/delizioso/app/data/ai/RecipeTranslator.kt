package com.delizioso.app.data.ai

import com.delizioso.app.data.import.StructuredRecipe
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.tasks.await
import java.util.Locale

/**
 * Translates a recipe with ML Kit's on-device NMT.
 *
 * Deliberately not a general LLM: translation is the one job in this app that a
 * model genuinely has to do, and a purpose-built translator does it far better
 * than a 1B generalist (Gemma 3 1B ignored the instruction entirely). Models are
 * ~30MB per language pair, downloaded once, then fully offline.
 *
 * Quantities never reach the model — only ingredient *names* are translated, so
 * the numbers [com.delizioso.app.data.UnitConverter] produced survive verbatim.
 */
class RecipeTranslator {

    /** Nothing to do: the recipe is already in the target language. */
    class AlreadyInTargetLanguage : Exception("Already in the target language")

    suspend fun translate(
        recipe: StructuredRecipe,
        targetLanguage: String = Locale.getDefault().language,
    ): StructuredRecipe {
        val target = TranslateLanguage.fromLanguageTag(targetLanguage)
            ?: throw AiUnavailableException("$targetLanguage is not a supported translation language")
        val source = identify(recipe)
            ?: throw AiUnavailableException("Could not tell what language this recipe is in")
        if (source == target) throw AlreadyInTargetLanguage()

        val translator = Translation.getClient(
            TranslatorOptions.Builder()
                .setSourceLanguage(source)
                .setTargetLanguage(target)
                .build()
        )
        return translator.use { client ->
            runCatching { client.downloadModelIfNeeded(DownloadConditions.Builder().build()).await() }
                .getOrElse { throw AiUnavailableException("The $source→$target language pack could not be downloaded — connect to the internet once to install it") }
            recipe.copy(
                title = client.translateOrKeep(recipe.title),
                description = client.translateOrKeep(recipe.description),
                ingredients = recipe.ingredients.map { ingredient ->
                    val name = client.translateOrKeep(ingredient.name) ?: ingredient.name
                    ingredient.copy(
                        name = name,
                        // Amount and unit are already correct — rebuild around them
                        // rather than letting the model near a number.
                        rawText = listOfNotNull(ingredient.quantity, ingredient.unit, name)
                            .joinToString(" ")
                            .trim(),
                    )
                },
                steps = recipe.steps.map { step -> client.translateOrKeep(step) ?: step },
            )
        }
    }

    /** BCP-47 code of the recipe's language, or null when ML Kit can't tell. */
    private suspend fun identify(recipe: StructuredRecipe): String? {
        val sample = recipe.toPlainText().take(1000)
        if (sample.isBlank()) return null
        val tag = LanguageIdentification.getClient().identifyLanguage(sample).await()
        if (tag == "und") return null
        return TranslateLanguage.fromLanguageTag(tag)
    }

    /** A blank or failed translation must never blank out the original line. */
    private suspend fun Translator.translateOrKeep(text: String?): String? {
        if (text.isNullOrBlank()) return text
        return runCatching { translate(text).await() }.getOrNull()?.takeIf { it.isNotBlank() } ?: text
    }
}

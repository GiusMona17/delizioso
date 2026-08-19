package com.delizioso.app.data.import

import com.delizioso.app.data.ai.NanoStructurer

/**
 * Fetches a saved recipe's source again and re-reads it.
 *
 * The app keeps the URL it imported from, so a recipe mangled by a bad parse — or
 * by an edit the user regrets — can be pulled back from where it came from. It is
 * the same path the original import took, minus the preview: fetch, then structure
 * with the deterministic parser and only then the model.
 */
class SourceRefresher(
    private val registry: RecipeImporterRegistry,
    private val structurer: NanoStructurer,
) {

    class NotRefreshable(message: String) : Exception(message)

    /**
     * The re-read recipe plus the text it came from — the caption is stored again
     * so the "hand it to an assistant" prompt quotes what the source says now, not
     * what it said when the recipe was first imported.
     */
    data class Refreshed(val recipe: StructuredRecipe, val rawText: String?)

    /** Throws when the source can't be reached or read. */
    suspend fun refetch(url: String): Refreshed {
        if (url.isBlank()) throw NotRefreshable("This recipe has no source link")
        val raw = registry.import(url)
        val caption = (raw.content as? ImportContent.RawText)?.text
        return when (val content = raw.content) {
            is ImportContent.Structured -> content.recipe
            is ImportContent.RawText -> {
                val text = content.text.takeIf { it.isNotBlank() && !LoginWall.matches(it) }
                    ?: content.title?.takeIf { it.isNotBlank() && !LoginWall.matches(it) }
                    ?: throw NotRefreshable("The source did not return any recipe text")
                structurer.structure(text)
            }
        }.let { recipe ->
            // A refresh that produced nothing would otherwise wipe the recipe it was
            // meant to repair.
            if (recipe.ingredients.isEmpty() && recipe.steps.isEmpty()) {
                throw NotRefreshable("The source no longer holds a readable recipe")
            }
            Refreshed(recipe.copy(imageUrl = recipe.imageUrl ?: raw.thumbnailUrl), caption)
        }
    }
}

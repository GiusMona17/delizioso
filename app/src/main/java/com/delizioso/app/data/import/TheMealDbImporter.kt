package com.delizioso.app.data.import

import com.delizioso.app.data.search.MealDbMapper
import com.delizioso.app.data.search.TheMealDbClient

/**
 * Imports a recipe from a `themealdb.com/meal/<id>` link.
 *
 * It exists so that recipes found through online search can be refreshed like any
 * other: their stored source URL is this one, and [SourceRefresher] resolves it
 * through the same registry as every other link. It also makes those URLs
 * pasteable in the import field.
 */
class TheMealDbImporter(
    private val client: TheMealDbClient = TheMealDbClient(),
) : RecipeImporter {

    override val platform: Platform = Platform.MEALDB

    override suspend fun fetch(rawUrl: String): RawImport {
        val id = PlatformDetector.mealDbId(rawUrl)
            ?: throw ImportException("Not a valid TheMealDB link")
        val meal = client.lookup(id)
            ?: throw ImportException("TheMealDB has no recipe with id $id", retryable = true)
        // The mapper builds the same RawImport for search results; a second copy
        // here is only a chance for the two paths to drift apart.
        return MealDbMapper.toRawImport(meal)
    }

    companion object {
        const val AUTHOR = "TheMealDB"

        /** The canonical, human-viewable page — stored as the recipe's source. */
        fun webUrl(id: String): String = "https://www.themealdb.com/meal/$id"
    }
}

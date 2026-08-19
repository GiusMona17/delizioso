package com.delizioso.app.data.search

import com.delizioso.app.data.Categories
import com.delizioso.app.data.import.ImportContent
import com.delizioso.app.data.import.IngredientParser
import com.delizioso.app.data.import.Platform
import com.delizioso.app.data.import.RawImport
import com.delizioso.app.data.import.StructuredRecipe
import com.delizioso.app.data.import.TheMealDbImporter
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Turns TheMealDB's denormalised meal object into the app's recipe.
 *
 * The API spreads ingredients across `strIngredient1..20` with a parallel
 * `strMeasure1..20`, leaving unused slots as "", " " or null — anywhere in the
 * range, not only at the end. It has no servings and no timings at all, so those
 * stay null rather than being guessed.
 */
object MealDbMapper {

    /** The API's fixed number of ingredient slots. */
    private const val INGREDIENT_SLOTS = 20

    /** "1. ", "2) ", "STEP 3 - " at the start of an instruction line. */
    private val LEADING_NUMBER = Regex("""^(?:step\s*)?\d{1,2}\s*[.)\-:]\s*""", RegexOption.IGNORE_CASE)

    fun mealId(meal: JsonObject): String? = meal.str("idMeal")

    fun toRecipe(meal: JsonObject): StructuredRecipe = StructuredRecipe(
        title = meal.str("strMeal"),
        imageUrl = meal.str("strMealThumb"),
        ingredients = ingredients(meal),
        steps = steps(meal.str("strInstructions")),
        // The site already classified it; map its words onto our vocabulary.
        categories = Categories.canonicalise(
            listOfNotNull(meal.str("strCategory"), meal.str("strArea"))
        ),
    )

    /**
     * The meal as the import flow expects it, source URL and all.
     *
     * Search results travel to the preview screen as a [RawImport] so that saving,
     * photo caching and the source link behave exactly as they do for a pasted
     * link — one path, not two.
     */
    fun toRawImport(meal: JsonObject): RawImport {
        val recipe = toRecipe(meal)
        val id = mealId(meal)
        return RawImport(
            platform = Platform.MEALDB.key,
            url = id?.let(TheMealDbImporter::webUrl),
            author = TheMealDbImporter.AUTHOR,
            content = ImportContent.Structured(recipe),
            thumbnailUrl = recipe.imageUrl,
        )
    }

    private fun ingredients(meal: JsonObject) = (1..INGREDIENT_SLOTS)
        .mapNotNull { slot ->
            val name = meal.str("strIngredient$slot") ?: return@mapNotNull null
            listOfNotNull(meal.str("strMeasure$slot"), name).joinToString(" ")
        }
        .mapIndexed { index, line -> IngredientParser.split(line).copy(position = index) }

    private fun steps(instructions: String?): List<String> = instructions.orEmpty()
        .split(Regex("""\r?\n"""))
        .map { it.trim().replace(LEADING_NUMBER, "") }
        .filter { it.isNotEmpty() }

    /** Blank, whitespace-only and the literal string "null" all mean absent. */
    private fun JsonObject.str(key: String): String? =
        (this[key] as? JsonPrimitive)?.content?.trim()?.takeIf { it.isNotEmpty() && it != "null" }
}

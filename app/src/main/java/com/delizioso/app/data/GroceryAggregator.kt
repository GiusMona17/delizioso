package com.delizioso.app.data

import com.delizioso.app.data.local.RecipeWithDetails
import com.delizioso.app.R

/** A consolidated grocery-list line. */
data class GroceryItem(
    val name: String,
    /** Final display line, e.g. "3 cups flour" or the original "2 large onions". */
    val line: String,
    /** True when several planned meals were merged into this line. */
    val isMerged: Boolean,
    /** Titles of the planned recipes this line came from (empty for custom items). */
    val recipeTitles: List<String> = emptyList(),
    /** Supermarket aisle, used by the "By Category" view. */
    val category: String = GroceryCategories.OTHER,
)

/**
 * Aggregates ingredients across the planned recipes into a shopping list:
 * quantities that parse as numbers are summed per ingredient name; otherwise the
 * original lines are kept. (No unit conversion — "cups" and "g" are kept separate.)
 */
object GroceryAggregator {

    fun aggregate(recipes: List<RecipeWithDetails>): List<GroceryItem> {
        val sources = recipes.flatMap { details ->
            details.ingredients.map { it to details.recipe.title }
        }
        if (sources.isEmpty()) return emptyList()

        return sources
            .groupBy { (ingredient, _) -> normalize(ingredient.name) }
            .values
            .map { group ->
                val ingredients = group.map { it.first }
                val titles = group.map { it.second }.distinct()
                val name = ingredients.first().name
                val quantities = ingredients.map { it.quantity?.let(Quantities::parse) }
                val allNumeric = quantities.isNotEmpty() && quantities.all { it != null }
                val line = if (allNumeric) {
                    val total = quantities.filterNotNull().sum()
                    val unit = ingredients.firstNotNullOfOrNull { it.unit }
                    buildString {
                        append(Quantities.format(total))
                        if (!unit.isNullOrBlank()) append(" $unit")
                        append(" $name")
                    }
                } else {
                    ingredients.first().rawText ?: name
                }
                GroceryItem(
                    name = name,
                    line = line,
                    isMerged = allNumeric && ingredients.size > 1,
                    recipeTitles = titles,
                    category = GroceryCategories.of(name),
                )
            }
            .sortedBy { it.name.lowercase() }
    }

    private fun normalize(name: String): String =
        name.lowercase().trim().trimEnd('.', ',', '!')
}

/** Rough supermarket-aisle bucketing so the list can be shopped in order. */
object GroceryCategories {

    const val OTHER = "Other"

    private val BUCKETS: List<Pair<String, List<String>>> = listOf(
        "Produce" to listOf(
            "onion", "garlic", "tomato", "potato", "carrot", "celery", "pepper", "chilli", "chili",
            "lettuce", "spinach", "kale", "cucumber", "avocado", "lemon", "lime", "orange", "apple",
            "banana", "berry", "berries", "mushroom", "courgette", "zucchini", "aubergine", "eggplant",
            "broccoli", "cauliflower", "cabbage", "leek", "ginger", "basil", "parsley", "coriander",
            "cilantro", "mint", "thyme", "rosemary", "salad", "peas", "corn", "squash",
        ),
        "Dairy & Eggs" to listOf(
            "milk", "cream", "butter", "cheese", "parmesan", "mozzarella", "cheddar", "feta", "ricotta",
            "yogurt", "yoghurt", "egg", "mascarpone",
        ),
        "Meat & Fish" to listOf(
            "chicken", "beef", "pork", "lamb", "bacon", "sausage", "ham", "turkey", "mince", "steak",
            "salmon", "tuna", "cod", "prawn", "shrimp", "fish", "anchovy", "chorizo", "pancetta",
        ),
        "Bakery" to listOf("bread", "sourdough", "baguette", "bun", "tortilla", "pita", "brioche", "croissant"),
        "Frozen" to listOf("frozen", "ice cream"),
        "Pantry" to listOf(
            "flour", "sugar", "salt", "oil", "vinegar", "rice", "pasta", "spaghetti",
            "noodle", "bean", "lentil", "chickpea", "stock", "broth", "sauce", "soy", "honey", "syrup",
            "yeast", "baking", "vanilla", "cocoa", "chocolate", "almond", "walnut", "seed", "oat",
            "cumin", "paprika", "cinnamon", "curry", "mustard", "ketchup", "mayonnaise",
        ),
    )

    /** Aisle order for the "By Category" view; unknown items sink to the bottom. */
    val ORDER: List<String> = BUCKETS.map { it.first } + OTHER

    fun of(ingredientName: String): String {
        val name = ingredientName.lowercase()
        return BUCKETS.firstOrNull { (_, keywords) -> keywords.any { name.contains(it) } }?.first ?: OTHER
    }

    /** String resource for the localized display label of an aisle [category]. */
    fun labelRes(aisle: String): Int = when (aisle) {
        "Produce" -> R.string.grocery_aisle_produce
        "Dairy & Eggs" -> R.string.grocery_aisle_dairy
        "Meat & Fish" -> R.string.grocery_aisle_meat
        "Bakery" -> R.string.grocery_aisle_bakery
        "Frozen" -> R.string.grocery_aisle_frozen
        "Pantry" -> R.string.grocery_aisle_pantry
        else -> R.string.grocery_aisle_other
    }
}

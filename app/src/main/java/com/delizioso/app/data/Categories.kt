package com.delizioso.app.data

import com.delizioso.app.R

enum class CategoryGroup(val id: String, val titleRes: Int) {
    MEAL_TYPE("meal_type", R.string.category_group_meal_type),
    COURSE_COMPONENT("course_component", R.string.category_group_course_component),
    DIET_STYLE("diet_style", R.string.category_group_diet_style),
}

data class CategoryDefinition(
    val name: String,
    val group: CategoryGroup,
    val displayNameRes: Int,
)

/**
 * The closed category vocabulary.
 *
 * Categories double as the library's filter chips, so the vocabulary has to be
 * fixed: free text let "Vegetarian", "vegetarian" and "Veggie" each become their
 * own chip. Everything that assigns a category — the AI, a blog's own metadata,
 * the user — goes through [canonicalise], and anything not on this list is dropped.
 */
object Categories {

    val DEFINITIONS: List<CategoryDefinition> = listOf(
        // Meal types
        CategoryDefinition("Breakfast", CategoryGroup.MEAL_TYPE, R.string.data_category_breakfast),
        CategoryDefinition("Lunch", CategoryGroup.MEAL_TYPE, R.string.data_category_lunch),
        CategoryDefinition("Dinner", CategoryGroup.MEAL_TYPE, R.string.data_category_dinner),
        CategoryDefinition("Snack", CategoryGroup.MEAL_TYPE, R.string.data_category_snack),
        CategoryDefinition("Dessert", CategoryGroup.MEAL_TYPE, R.string.data_category_dessert),

        // Courses & Components (Non-dish & specialized elements)
        CategoryDefinition("Pasta", CategoryGroup.COURSE_COMPONENT, R.string.data_category_pasta),
        CategoryDefinition("Soup", CategoryGroup.COURSE_COMPONENT, R.string.data_category_soup),
        CategoryDefinition("Salad", CategoryGroup.COURSE_COMPONENT, R.string.data_category_salad),
        CategoryDefinition("Sauce", CategoryGroup.COURSE_COMPONENT, R.string.data_category_sauce),
        CategoryDefinition("Bread", CategoryGroup.COURSE_COMPONENT, R.string.data_category_bread),
        CategoryDefinition("Side", CategoryGroup.COURSE_COMPONENT, R.string.data_category_side),
        CategoryDefinition("Drink", CategoryGroup.COURSE_COMPONENT, R.string.data_category_drink),
        CategoryDefinition("Dressing & Marinade", CategoryGroup.COURSE_COMPONENT, R.string.data_category_dressing),
        CategoryDefinition("Base & Broth", CategoryGroup.COURSE_COMPONENT, R.string.data_category_base_broth),
        CategoryDefinition("Preserve", CategoryGroup.COURSE_COMPONENT, R.string.data_category_preserve),
        CategoryDefinition("Baking", CategoryGroup.COURSE_COMPONENT, R.string.data_category_baking),

        // Diet & Style
        CategoryDefinition("Vegetarian", CategoryGroup.DIET_STYLE, R.string.data_category_vegetarian),
        CategoryDefinition("Vegan", CategoryGroup.DIET_STYLE, R.string.data_category_vegan),
        CategoryDefinition("Healthy", CategoryGroup.DIET_STYLE, R.string.data_category_healthy),
        CategoryDefinition("Quick", CategoryGroup.DIET_STYLE, R.string.data_category_quick),
        CategoryDefinition("Comfort", CategoryGroup.DIET_STYLE, R.string.data_category_comfort),
        CategoryDefinition("Spicy", CategoryGroup.DIET_STYLE, R.string.data_category_spicy),
    )

    val ALL: List<String> = DEFINITIONS.map { it.name }

    /** At most this many per recipe, so cards and chips stay readable. */
    const val MAX_PER_RECIPE = 3

    private val BY_LOWERCASE: Map<String, String> = ALL.associateBy { it.lowercase() }

    /**
     * Common wordings that mean one of ours. Kept deliberately small — this is for
     * mapping a site's own `recipeCategory`, not for guessing.
     */
    private val SYNONYMS: Map<String, String> = mapOf(
        "main" to "Dinner",
        "main course" to "Dinner",
        "mains" to "Dinner",
        "supper" to "Dinner",
        "starter" to "Snack",
        "appetizer" to "Snack",
        "appetiser" to "Snack",
        "side" to "Side",
        "side dish" to "Side",
        "sides" to "Side",
        "contorno" to "Side",
        "contorni" to "Side",
        "brunch" to "Breakfast",
        "pudding" to "Dessert",
        "sweet" to "Dessert",
        "cake" to "Baking",
        "bread" to "Bread",
        "pane" to "Bread",
        "focaccia" to "Bread",
        "dough" to "Bread",
        "impasto" to "Bread",
        "pizza" to "Bread",
        "sauce" to "Sauce",
        "sauces" to "Sauce",
        "salsa" to "Sauce",
        "salse" to "Sauce",
        "pesto" to "Sauce",
        "gravy" to "Sauce",
        "dip" to "Sauce",
        "condimento" to "Sauce",
        "condimenti" to "Sauce",
        "dressing" to "Dressing & Marinade",
        "dressings" to "Dressing & Marinade",
        "marinade" to "Dressing & Marinade",
        "marinades" to "Dressing & Marinade",
        "vinaigrette" to "Dressing & Marinade",
        "marinata" to "Dressing & Marinade",
        "broth" to "Base & Broth",
        "brodo" to "Base & Broth",
        "stock" to "Base & Broth",
        "fondo" to "Base & Broth",
        "base" to "Base & Broth",
        "preserve" to "Preserve",
        "preserves" to "Preserve",
        "jam" to "Preserve",
        "marmellata" to "Preserve",
        "confettura" to "Preserve",
        "pickle" to "Preserve",
        "pickles" to "Preserve",
        "sottoli" to "Preserve",
        "sottaceti" to "Preserve",
        "conserva" to "Preserve",
        "conserve" to "Preserve",
        "drink" to "Drink",
        "drinks" to "Drink",
        "beverage" to "Drink",
        "beverages" to "Drink",
        "cocktail" to "Drink",
        "smoothie" to "Drink",
        "bevanda" to "Drink",
        "bevande" to "Drink",
        "cookies" to "Baking",
        "noodles" to "Pasta",
        "veggie" to "Vegetarian",
        "meat-free" to "Vegetarian",
        "plant-based" to "Vegan",
        "stew" to "Soup",
        "easy" to "Quick",
        "fast" to "Quick",
        "light" to "Healthy",
        "hot" to "Spicy",
    )

    /** Exact (case-insensitive) match, then a small synonym table; null when neither. */
    fun canonicalise(raw: String): String? {
        val key = raw.trim().lowercase()
        if (key.isEmpty()) return null
        return BY_LOWERCASE[key] ?: SYNONYMS[key]
    }

    /**
     * Filters a model's or a site's suggestions down to valid, deduplicated
     * categories, capped at [MAX_PER_RECIPE] and ordered as in [ALL].
     */
    fun canonicalise(raw: List<String>): List<String> = raw
        .mapNotNull(::canonicalise)
        .distinct()
        .sortedBy { ALL.indexOf(it) }
        .take(MAX_PER_RECIPE)

    fun groupOf(category: String): CategoryGroup? =
        DEFINITIONS.firstOrNull { it.name.equals(category, ignoreCase = true) }?.group

    fun byGroup(group: CategoryGroup): List<CategoryDefinition> =
        DEFINITIONS.filter { it.group == group }

    /** String resource for the localized display label of a canonical [category]. */
    fun displayNameRes(category: String): Int =
        DEFINITIONS.firstOrNull { it.name.equals(category, ignoreCase = true) }?.displayNameRes
            ?: R.string.data_category_other
}

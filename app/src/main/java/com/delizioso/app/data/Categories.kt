package com.delizioso.app.data

/**
 * The closed category vocabulary.
 *
 * Categories double as the library's filter chips, so the vocabulary has to be
 * fixed: free text let "Vegetarian", "vegetarian" and "Veggie" each become their
 * own chip. Everything that assigns a category — the AI, a blog's own metadata,
 * the user — goes through [canonicalise], and anything not on this list is dropped.
 */
object Categories {

    val ALL: List<String> = listOf(
        "Breakfast",
        "Lunch",
        "Dinner",
        "Snack",
        "Dessert",
        "Pasta",
        "Soup",
        "Salad",
        "Baking",
        "Vegetarian",
        "Vegan",
        "Healthy",
        "Quick",
        "Comfort",
        "Spicy",
    )

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
        "side" to "Snack",
        "side dish" to "Snack",
        "brunch" to "Breakfast",
        "pudding" to "Dessert",
        "sweet" to "Dessert",
        "cake" to "Baking",
        "bread" to "Baking",
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
}

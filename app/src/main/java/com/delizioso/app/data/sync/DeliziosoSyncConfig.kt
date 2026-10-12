package com.delizioso.app.data.sync

/**
 * Everything in the database is user data. Recipes and tags first: every other table
 * references a recipe by id, and recipe↔tag references a tag by name.
 */
val DeliziosoSyncConfig = SyncConfig(
    appName = "delizioso",
    tables = listOf(
        "recipes",
        "tags",
        "ingredients",
        "steps",
        "sources",
        "recipe_tag_cross_ref",
        "quick_meals",
        "planned_meals",
        "cooking_logs",
        "pantry_items",
    ),
    fileColumns = mapOf(
        "recipes" to setOf("imageUri"),
        "cooking_logs" to setOf("photoUri"),
    ),
    unsyncedTables = emptySet(),
    // The grocery list and nutrition targets are the user's plan, not device settings.
    prefKeys = setOf(
        "grocery_checked",
        "grocery_custom_items",
        "target_calories_kcal",
        "target_protein_g",
        "target_fat_g",
        "target_carbs_g",
        "default_servings",
    ),
)

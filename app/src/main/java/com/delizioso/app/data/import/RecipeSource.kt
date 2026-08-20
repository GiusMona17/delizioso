package com.delizioso.app.data.import

enum class RecipeSourceCategory {
    SEARCH_APIS,
    ITALIAN_SITES,
    INTERNATIONAL,
    SOCIAL_MEDIA,
    GENERIC,
}

enum class RecipeSource(
    val id: String,
    val category: RecipeSourceCategory,
    val defaultEnabled: Boolean = true,
    val domains: List<String> = emptyList(),
) {
    // Search APIs
    THE_MEAL_DB("the_meal_db", RecipeSourceCategory.SEARCH_APIS, domains = listOf("themealdb.com")),

    // Italian Recipe Portals
    GIALLO_ZAFFERANO("giallo_zafferano", RecipeSourceCategory.ITALIAN_SITES, domains = listOf("ricette.giallozafferano.it", "giallozafferano.it")),
    COOKIST("cookist", RecipeSourceCategory.ITALIAN_SITES, domains = listOf("cookist.it")),
    CUCCHIAIO("cucchiaio", RecipeSourceCategory.ITALIAN_SITES, domains = listOf("cucchiaio.it")),
    RICETTE_BIMBY("ricette_bimby", RecipeSourceCategory.ITALIAN_SITES, domains = listOf("ricetteperbimby.it")),

    // International Recipe Sites
    ALL_RECIPES("all_recipes", RecipeSourceCategory.INTERNATIONAL, domains = listOf("allrecipes.com")),
    BBC_GOOD_FOOD("bbc_good_food", RecipeSourceCategory.INTERNATIONAL, domains = listOf("bbcgoodfood.com")),
    SERIOUS_EATS("serious_eats", RecipeSourceCategory.INTERNATIONAL, domains = listOf("seriouseats.com")),

    // Social Media & Video
    YOUTUBE("youtube", RecipeSourceCategory.SOCIAL_MEDIA, domains = listOf("youtube.com", "youtu.be")),
    TIKTOK("tiktok", RecipeSourceCategory.SOCIAL_MEDIA, domains = listOf("tiktok.com")),
    INSTAGRAM("instagram", RecipeSourceCategory.SOCIAL_MEDIA, domains = listOf("instagram.com", "ig.me")),
    FACEBOOK("facebook", RecipeSourceCategory.SOCIAL_MEDIA, domains = listOf("facebook.com", "fb.watch")),

    // Generic Fallback
    GENERIC_WEB("generic_web", RecipeSourceCategory.GENERIC);

    companion object {
        fun fromId(id: String): RecipeSource? = values().firstOrNull { it.id.equals(id, ignoreCase = true) }
        fun defaultActiveSources(): Set<RecipeSource> = values().filter { it.defaultEnabled }.toSet()
    }
}

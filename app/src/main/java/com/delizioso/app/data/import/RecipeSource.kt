package com.delizioso.app.data.import

import com.delizioso.app.R

enum class RecipeSourceCategory {
    SEARCH_APIS,
    ITALIAN_SITES,
    INTERNATIONAL,
    SOCIAL_MEDIA,
    GENERIC,
}

val RecipeSourceCategory.displayNameRes: Int
    get() = when (this) {
        RecipeSourceCategory.SEARCH_APIS -> R.string.sources_cat_search
        RecipeSourceCategory.ITALIAN_SITES -> R.string.sources_cat_italian
        RecipeSourceCategory.INTERNATIONAL -> R.string.sources_cat_intl
        RecipeSourceCategory.SOCIAL_MEDIA -> R.string.sources_cat_social
        RecipeSourceCategory.GENERIC -> R.string.sources_cat_generic
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

val RecipeSource.displayNameRes: Int
    get() = when (this) {
        RecipeSource.THE_MEAL_DB -> R.string.source_the_meal_db
        RecipeSource.GIALLO_ZAFFERANO -> R.string.source_giallo_zafferano
        RecipeSource.COOKIST -> R.string.source_cookist
        RecipeSource.CUCCHIAIO -> R.string.source_cucchiaio
        RecipeSource.RICETTE_BIMBY -> R.string.source_ricette_bimby
        RecipeSource.ALL_RECIPES -> R.string.source_all_recipes
        RecipeSource.BBC_GOOD_FOOD -> R.string.source_bbc_good_food
        RecipeSource.SERIOUS_EATS -> R.string.source_serious_eats
        RecipeSource.YOUTUBE -> R.string.source_youtube
        RecipeSource.TIKTOK -> R.string.source_tiktok
        RecipeSource.INSTAGRAM -> R.string.source_instagram
        RecipeSource.FACEBOOK -> R.string.source_facebook
        RecipeSource.GENERIC_WEB -> R.string.source_generic_web
    }

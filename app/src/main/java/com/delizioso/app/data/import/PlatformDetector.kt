package com.delizioso.app.data.import

enum class Platform(val key: String) {
    INSTAGRAM("INSTAGRAM"),
    FACEBOOK("FACEBOOK"),
    TIKTOK("TIKTOK"),
    YOUTUBE("YOUTUBE"),
    BLOG("BLOG"),
    MEALDB("MEALDB"),
}

/**
 * Detects the platform for a pasted link. Matching is prefix-based (`find`, not
 * `matches`) so trailing slashes / query strings don't break detection.
 * Social short-links (vm.tiktok.com, youtu.be, fb.watch, ig.me) are recognized by
 * host so the fetcher can resolve them.
 */
object PlatformDetector {

    private val instagram = Regex(
        """^(?:https?://)?(?:www\.)?instagram\.com/(?:p|reel|reels|tv)/([A-Za-z0-9_-]+)"""
    )
    private val instagramShort = Regex(
        """^(?:https?://)?(?:www\.)?ig\.me/([A-Za-z0-9_-]+)"""
    )
    private val facebook = Regex(
        """^(?:https?://)?(?:www\.|m\.|web\.)?facebook\.com/.*?(?:videos|reel|watch|share|story\.php)"""
    )
    private val facebookShort = Regex(
        """^(?:https?://)?(?:www\.)?fb\.watch/"""
    )
    private val tiktok = Regex(
        """^(?:https?://)?(?:www\.|vm\.|vt\.)?tiktok\.com/"""
    )
    private val tiktokVideo = Regex(
        """^(?:https?://)?(?:www\.|vm\.|vt\.)?tiktok\.com/@[\w.-]+/video/(\d+)"""
    )
    private val youtube = Regex(
        """^(?:https?://)?(?:www\.|m\.)?(?:youtube\.com/watch\?v=|youtube\.com/shorts/|youtu\.be/)([\w-]{11})"""
    )
    private val mealDb = Regex(
        """^(?:https?://)?(?:www\.)?themealdb\.com/meal/(\d+)"""
    )

    fun sourceFor(rawUrl: String): RecipeSource {
        val url = rawUrl.trim().lowercase()
        return when {
            RecipeSource.GIALLO_ZAFFERANO.domains.any { url.contains(it) } -> RecipeSource.GIALLO_ZAFFERANO
            RecipeSource.COOKIST.domains.any { url.contains(it) } -> RecipeSource.COOKIST
            RecipeSource.CUCCHIAIO.domains.any { url.contains(it) } -> RecipeSource.CUCCHIAIO
            RecipeSource.RICETTE_BIMBY.domains.any { url.contains(it) } -> RecipeSource.RICETTE_BIMBY
            RecipeSource.ALL_RECIPES.domains.any { url.contains(it) } -> RecipeSource.ALL_RECIPES
            RecipeSource.BBC_GOOD_FOOD.domains.any { url.contains(it) } -> RecipeSource.BBC_GOOD_FOOD
            RecipeSource.SERIOUS_EATS.domains.any { url.contains(it) } -> RecipeSource.SERIOUS_EATS
            RecipeSource.THE_MEAL_DB.domains.any { url.contains(it) } -> RecipeSource.THE_MEAL_DB
            youtube.find(url) != null -> RecipeSource.YOUTUBE
            tiktok.find(url) != null -> RecipeSource.TIKTOK
            instagram.find(url) != null || instagramShort.find(url) != null -> RecipeSource.INSTAGRAM
            facebook.find(url) != null || facebookShort.find(url) != null -> RecipeSource.FACEBOOK
            else -> RecipeSource.GENERIC_WEB
        }
    }

    fun detect(rawUrl: String): Platform? {
        val url = rawUrl.trim()
        return when {
            instagram.find(url) != null || instagramShort.find(url) != null -> Platform.INSTAGRAM
            facebook.find(url) != null || facebookShort.find(url) != null -> Platform.FACEBOOK
            tiktok.find(url) != null -> Platform.TIKTOK
            youtube.find(url) != null -> Platform.YOUTUBE
            mealDb.find(url) != null -> Platform.MEALDB
            url.startsWith("http://") || url.startsWith("https://") -> Platform.BLOG
            else -> null
        }
    }

    fun instagramCode(rawUrl: String): String? =
        instagram.find(rawUrl.trim())?.groupValues?.get(1)
            ?: instagramShort.find(rawUrl.trim())?.groupValues?.get(1)

    fun youtubeId(rawUrl: String): String? = youtube.find(rawUrl.trim())?.groupValues?.get(1)

    fun mealDbId(rawUrl: String): String? = mealDb.find(rawUrl.trim())?.groupValues?.get(1)

    fun tiktokVideoId(rawUrl: String): String? = tiktokVideo.find(rawUrl.trim())?.groupValues?.get(1)
}

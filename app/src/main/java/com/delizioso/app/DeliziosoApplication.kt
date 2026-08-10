package com.delizioso.app

import android.app.Application
import android.content.Context
import com.delizioso.app.data.RecipeRepository
import com.delizioso.app.data.ai.NanoAdvisor
import com.delizioso.app.data.ai.NanoStructurer
import com.delizioso.app.data.ai.OcrTextExtractor
import com.delizioso.app.data.import.BlogImporter
import com.delizioso.app.data.import.FacebookImporter
import com.delizioso.app.data.import.InstagramImporter
import com.delizioso.app.data.import.RecipeImporterRegistry
import com.delizioso.app.data.import.TikTokImporter
import com.delizioso.app.data.import.WebViewCaptionExtractor
import com.delizioso.app.data.import.YouTubeImporter
import com.delizioso.app.data.local.AppDatabase
import com.delizioso.app.data.local.UserPreferences
import kotlinx.coroutines.flow.first

/** Lightweight manual DI container (no Hilt — keeps the dependency surface small). */
class AppContainer(context: Context) {
    val database: AppDatabase = AppDatabase.build(context)
    val recipeRepository: RecipeRepository = RecipeRepository(database.recipeDao())
    val preferences: UserPreferences = UserPreferences(context)

    val nanoStructurer: NanoStructurer = NanoStructurer(
        consentProvider = { preferences.aiConsentGiven.first() }
    )
    val nanoAdvisor: NanoAdvisor = NanoAdvisor(
        consentProvider = { preferences.aiConsentGiven.first() }
    )
    val ocrTextExtractor: OcrTextExtractor = OcrTextExtractor()

    val importRegistry: RecipeImporterRegistry = RecipeImporterRegistry(
        listOf(
            TikTokImporter(),
            YouTubeImporter(
                apiKeyProvider = {
                    val buildKey = BuildConfig.YOUTUBE_API_KEY
                    if (buildKey.isNotBlank()) buildKey else preferences.youTubeApiKey.first()
                }
            ),
            BlogImporter(),
            InstagramImporter(WebViewCaptionExtractor(context)),
            FacebookImporter(WebViewCaptionExtractor(context)),
        )
    )
}

class DeliziosoApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

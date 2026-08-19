package com.delizioso.app

import android.app.Application
import android.content.Context
import com.delizioso.app.data.RecipeRepository
import com.delizioso.app.data.ai.GemmaEngine
import com.delizioso.app.data.ai.NanoChat
import com.delizioso.app.data.ai.RecipeChat
import com.delizioso.app.data.ai.RecipeTranslator
import com.delizioso.app.data.backup.BackupManager
import com.delizioso.app.data.ai.NanoStructurer
import com.delizioso.app.data.ai.OcrTextExtractor
import com.delizioso.app.data.import.BlogImporter
import com.delizioso.app.data.import.FacebookImporter
import com.delizioso.app.data.import.InstagramImporter
import com.delizioso.app.data.import.RecipeImporterRegistry
import com.delizioso.app.data.import.SourceRefresher
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
    val nanoChat: NanoChat = NanoChat(
        consentProvider = { preferences.aiConsentGiven.first() }
    )
    /** Optional, user-supplied Gemma model. Import never uses it — chat is its job. */
    val gemmaEngine: GemmaEngine = GemmaEngine(context.applicationContext)
    val recipeChat: RecipeChat = RecipeChat(nanoChat, gemmaEngine)
    val recipeTranslator: RecipeTranslator = RecipeTranslator()
    val backupManager: BackupManager = BackupManager(context.applicationContext, recipeRepository)
    val sourceRefresher: SourceRefresher by lazy { SourceRefresher(importRegistry, nanoStructurer) }
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

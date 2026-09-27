package com.delizioso.app

import android.app.Application
import android.content.Context
import coil.ImageLoader
import coil.ImageLoaderFactory
import okhttp3.OkHttpClient
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import com.delizioso.app.data.RecipeRepository
import com.delizioso.app.data.sync.DeliziosoSyncConfig
import com.delizioso.app.data.sync.FileHashCache
import com.delizioso.app.data.sync.SnapsyncClient
import com.delizioso.app.data.sync.SyncEngine
import com.delizioso.app.data.sync.SyncNotifier
import com.delizioso.app.data.sync.SyncSettingsRepository
import com.delizioso.app.data.sync.SyncWorker
import com.delizioso.app.data.sync.TableSnapshot
import java.io.File
import com.delizioso.app.data.ai.GemmaEngine
import com.delizioso.app.data.ai.NanoChat
import com.delizioso.app.data.ai.RecipeChat
import com.delizioso.app.data.ai.RecipeTranslator
import com.delizioso.app.data.backup.BackupManager
import com.delizioso.app.data.ai.NanoStructurer
import com.delizioso.app.data.ai.OcrTextExtractor
import com.delizioso.app.data.import.BlogImporter
import com.delizioso.app.data.import.ImportHttp
import com.delizioso.app.data.import.FacebookImporter
import com.delizioso.app.data.import.InstagramImporter
import com.delizioso.app.data.import.RecipeImporterRegistry
import com.delizioso.app.data.import.SourceRefresher
import com.delizioso.app.data.import.TheMealDbImporter
import com.delizioso.app.data.import.TikTokImporter
import com.delizioso.app.data.import.WebViewCaptionExtractor
import com.delizioso.app.data.import.YouTubeImporter
import com.delizioso.app.data.local.AppDatabase
import com.delizioso.app.data.local.UserPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/** Lightweight manual DI container (no Hilt — keeps the dependency surface small). */
class AppContainer(context: Context) {
    val database: AppDatabase = AppDatabase.build(context)
    val recipeRepository: RecipeRepository = RecipeRepository(database.recipeDao(), database.pantryDao())
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
        importers = listOf(
            TikTokImporter(),
            YouTubeImporter(
                apiKeyProvider = {
                    val buildKey = BuildConfig.YOUTUBE_API_KEY
                    if (buildKey.isNotBlank()) buildKey else preferences.youTubeApiKey.first()
                }
            ),
            BlogImporter(),
            TheMealDbImporter(),
            InstagramImporter(WebViewCaptionExtractor(context)),
            FacebookImporter(WebViewCaptionExtractor(context)),
        ),
        enabledSourcesProvider = { runBlocking { preferences.enabledSources.first() } }
    )

    val theMealDbClient: com.delizioso.app.data.search.TheMealDbClient = com.delizioso.app.data.search.TheMealDbClient()

    val searchProviders: List<com.delizioso.app.data.search.RecipeSearchProvider> by lazy {
        listOf(
            com.delizioso.app.data.search.GialloZafferanoSearchProvider(),
            com.delizioso.app.data.search.BimbySearchProvider(),
            com.delizioso.app.data.search.BbcGoodFoodSearchProvider(),
            com.delizioso.app.data.search.TheMealDbSearchProvider(theMealDbClient),
        )
    }

    val syncSettings: SyncSettingsRepository = SyncSettingsRepository(preferences.dataStore)

    /** Plain client: snapsync needs neither the browser User-Agent nor the import interceptors. */
    private val syncHttp: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }

    val syncEngine: SyncEngine by lazy {
        val workDir = File(context.filesDir, "snapsync")
        SyncEngine(
            TableSnapshot(
                database,
                DeliziosoSyncConfig,
                context.filesDir,
                FileHashCache(File(workDir, "hashes.json")),
                preferences.dataStore,
            ),
            syncSettings,
            { s -> SnapsyncClient(syncHttp, s.serverUrl, s.token, DeliziosoSyncConfig.appName) },
            workDir,
        )
    }

    val syncNotifier: SyncNotifier = SyncNotifier(context.applicationContext)
}

class DeliziosoApplication : Application(), ImageLoaderFactory {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        SyncWorker.schedulePeriodic(this)
        // Pull when the app comes forward, push when it leaves (spec §2 triggers).
        ProcessLifecycleOwner.get().lifecycle.addObserver(LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START || event == Lifecycle.Event.ON_STOP) SyncWorker.enqueue(this)
        })
    }

    /**
     * Provides a dedicated HTTP client for image loading with the browser User-Agent.
     * This ensures remote thumbnails carry the same User-Agent as other requests in the app —
     * stopping recipe sites from answering 403 for hotlinked images —
     * while remaining independent of the import client's configuration (timeouts, interceptors, etc.).
     * Built lazily on first image load.
     */
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .okHttpClient {
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    chain.proceed(
                        chain.request().newBuilder()
                            .header("User-Agent", ImportHttp.BROWSER_USER_AGENT)
                            .build()
                    )
                }
                .build()
        }
        .crossfade(true)
        .build()
}

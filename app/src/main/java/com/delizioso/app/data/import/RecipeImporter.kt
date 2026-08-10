package com.delizioso.app.data.import

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/** Shared HTTP client with a mobile browser fingerprint (best resilience vs. bot blocks). */
object ImportHttp {
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header(
                        "User-Agent",
                        "Mozilla/5.0 (Linux; Android 15; Pixel 10) AppleWebKit/537.36 " +
                            "(KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"
                    )
                    .header("Accept-Language", "en")
                    .build()
            )
        }
        .build()
}

/** Common contract: each platform knows how to turn a link into [RawImport]. */
interface RecipeImporter {
    val platform: Platform
    suspend fun fetch(rawUrl: String): RawImport
}

/** Blocking OkHttp call wrapped as suspend, off the main thread. */
internal suspend fun OkHttpClient.newCallSuspend(request: Request): Response =
    withContext(Dispatchers.IO) { newCall(request).execute() }

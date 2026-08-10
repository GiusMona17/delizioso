package com.delizioso.app.data

import android.content.Context
import android.net.Uri
import com.delizioso.app.data.import.ImportHttp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/** Copies picked images into the app's internal storage so URIs stay valid long-term. */
object ImageStore {

    fun saveToInternal(context: Context, uri: Uri): String {
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IOException("Cannot open image")
        val dest = newFile(context)
        input.use { src -> FileOutputStream(dest).use { dst -> src.copyTo(dst) } }
        return dest.absolutePath
    }

    /**
     * Downloads a source's cover image so the recipe keeps its photo after the CDN
     * link expires — Instagram and TikTok thumbnail URLs are short-lived.
     * Returns null when the download fails; a missing photo is never worth failing
     * an import over.
     */
    suspend fun downloadToInternal(context: Context, url: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url(url).get().build()
            ImportHttp.client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@runCatching null
                val body = response.body ?: return@runCatching null
                val dest = newFile(context)
                body.byteStream().use { src -> FileOutputStream(dest).use { dst -> src.copyTo(dst) } }
                dest.absolutePath
            }
        }.getOrNull()
    }

    /** Deletes a photo this app owns; ignores anything outside its own directory. */
    fun deleteIfOwned(context: Context, path: String?) {
        if (path.isNullOrBlank()) return
        val dir = directory(context)
        val file = File(path)
        if (file.parentFile == dir) file.delete()
    }

    private fun directory(context: Context): File =
        File(context.filesDir, "recipe_images").apply { mkdirs() }

    private fun newFile(context: Context) =
        File(directory(context), "recipe_${System.currentTimeMillis()}.jpg")
}

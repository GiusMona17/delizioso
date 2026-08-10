package com.delizioso.app.data

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/** Copies picked images into the app's internal storage so URIs stay valid long-term. */
object ImageStore {

    fun saveToInternal(context: Context, uri: Uri): String {
        val dir = File(context.filesDir, "recipe_images").apply { mkdirs() }
        val dest = File(dir, "recipe_${System.currentTimeMillis()}.jpg")
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IOException("Cannot open image")
        input.use { src -> FileOutputStream(dest).use { dst -> src.copyTo(dst) } }
        return dest.absolutePath
    }
}

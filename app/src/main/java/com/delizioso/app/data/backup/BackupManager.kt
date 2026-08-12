package com.delizioso.app.data.backup

import android.content.Context
import android.net.Uri
import com.delizioso.app.data.ImageStore
import com.delizioso.app.data.RecipeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Writes and reads the whole library as a single `.zip`.
 *
 * The app keeps everything on the phone, which means an uninstall, a reset or a
 * lost handset takes the collection with it. This is the way back: one file the
 * user puts wherever they keep their own backups, holding both the recipes and
 * the photos, restorable onto a fresh install.
 *
 * The destination and source are chosen through the system file picker, so the
 * app never needs storage permissions and never writes anywhere on its own.
 */
class BackupManager(
    private val appContext: Context,
    private val repository: RecipeRepository,
) {

    /** What a restore did, so the user is told rather than left guessing. */
    data class RestoreResult(val added: Int, val skipped: Int)

    class BackupException(message: String) : Exception(message)

    /** Writes every recipe and photo to [destination]. Returns how many were saved. */
    suspend fun exportTo(destination: Uri): Int = withContext(Dispatchers.IO) {
        val library = repository.allWithDetails.first()
        val output = appContext.contentResolver.openOutputStream(destination)
            ?: throw BackupException("Could not write to the chosen file")

        ZipOutputStream(output.buffered()).use { zip ->
            val records = library.map { details ->
                val photo = details.recipe.imageUri?.let { File(it) }?.takeIf { it.exists() }
                if (photo != null) {
                    zip.putNextEntry(ZipEntry(BackupFile.PHOTO_DIR + photo.name))
                    photo.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
                details.toBackup(photo?.name)
            }
            zip.putNextEntry(ZipEntry(BackupFile.MANIFEST))
            zip.write(
                BackupFile.json
                    .encodeToString(BackupFile.serializer(), BackupFile(recipes = records))
                    .toByteArray()
            )
            zip.closeEntry()
            records.size
        }
    }

    /**
     * Restores from [source], adding recipes that aren't already here.
     *
     * Additive on purpose: a restore must never be able to delete a recipe added
     * since the backup was taken, and re-running the same file must not double
     * the library.
     */
    suspend fun importFrom(source: Uri): RestoreResult = withContext(Dispatchers.IO) {
        val input = appContext.contentResolver.openInputStream(source)
            ?: throw BackupException("Could not read the chosen file")

        var manifest: BackupFile? = null
        val photos = mutableMapOf<String, ByteArray>()
        ZipInputStream(input.buffered()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                when {
                    entry.name == BackupFile.MANIFEST ->
                        manifest = runCatching {
                            BackupFile.json.decodeFromString(
                                BackupFile.serializer(),
                                zip.readBytes().decodeToString(),
                            )
                        }.getOrElse { throw BackupException("That file is not a Delizioso backup") }

                    entry.name.startsWith(BackupFile.PHOTO_DIR) && !entry.isDirectory ->
                        photos[entry.name.removePrefix(BackupFile.PHOTO_DIR)] = zip.readBytes()
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }

        val backup = manifest ?: throw BackupException("That file is not a Delizioso backup")
        if (backup.version > BackupFile.FORMAT_VERSION) {
            throw BackupException("That backup was made by a newer version of the app")
        }

        val existing = repository.allWithDetails.first().map { it.recipe.identity() }.toSet()
        var added = 0
        var skipped = 0
        for (record in backup.recipes) {
            if (record.identity() in existing) {
                skipped++
                continue
            }
            val photoPath = record.photo
                ?.let { photos[it] }
                ?.let { bytes -> runCatching { ImageStore.saveBytes(appContext, bytes) }.getOrNull() }
            repository.save(record.toDetails(photoPath), record.tags)
            added++
        }
        RestoreResult(added = added, skipped = skipped)
    }

    /** Default file name offered to the picker. */
    fun suggestedFileName(): String {
        val stamp = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            .format(java.util.Date())
        return "delizioso-$stamp.zip"
    }
}

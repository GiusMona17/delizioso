package com.delizioso.app.data.backup

import com.delizioso.app.data.local.IngredientEntity
import com.delizioso.app.data.local.RecipeEntity
import com.delizioso.app.data.local.RecipeWithDetails
import com.delizioso.app.data.local.SourceEntity
import com.delizioso.app.data.local.StepEntity
import com.delizioso.app.data.local.TagEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The on-disk shape of a library backup.
 *
 * Deliberately its own set of classes rather than serialising the Room entities:
 * a backup written today has to still restore after the database schema moves on,
 * so the file format must be free to stay put while the entities change.
 * [photo] is a file name inside the archive's `photos/` folder, not a path — the
 * absolute paths in the database belong to one install only.
 */
@Serializable
data class BackupFile(
    val version: Int = FORMAT_VERSION,
    val exportedAt: Long = System.currentTimeMillis(),
    val recipes: List<BackupRecipe> = emptyList(),
) {
    companion object {
        const val FORMAT_VERSION = 1

        /** Entry names inside the archive. */
        const val MANIFEST = "library.json"
        const val PHOTO_DIR = "photos/"

        val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    }
}

@Serializable
data class BackupRecipe(
    val title: String,
    val description: String? = null,
    val servings: Int? = null,
    val prepTimeMinutes: Int? = null,
    val cookTimeMinutes: Int? = null,
    val notes: String? = null,
    val isFavorite: Boolean = false,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val photo: String? = null,
    val ingredients: List<BackupIngredient> = emptyList(),
    val steps: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val source: BackupSource? = null,
)

@Serializable
data class BackupIngredient(
    val quantity: String? = null,
    val unit: String? = null,
    val name: String,
    val rawText: String? = null,
    val note: String? = null,
)

@Serializable
data class BackupSource(
    val platform: String,
    val url: String? = null,
    val author: String? = null,
    val rawText: String? = null,
    val fetchedAt: Long = 0L,
)

/** Database rows → backup record. [photoName] is the archive entry, if any. */
fun RecipeWithDetails.toBackup(photoName: String?): BackupRecipe = BackupRecipe(
    title = recipe.title,
    description = recipe.description,
    servings = recipe.servings,
    prepTimeMinutes = recipe.prepTimeMinutes,
    cookTimeMinutes = recipe.cookTimeMinutes,
    notes = recipe.notes,
    isFavorite = recipe.isFavorite,
    createdAt = recipe.createdAt,
    updatedAt = recipe.updatedAt,
    photo = photoName,
    ingredients = ingredients.sortedBy { it.position }.map {
        BackupIngredient(it.quantity, it.unit, it.name, it.rawText, it.note)
    },
    steps = steps.sortedBy { it.position }.map { it.text },
    tags = tags.map { it.name },
    source = source?.let { BackupSource(it.platform, it.url, it.author, it.rawText, it.fetchedAt) },
)

/** Backup record → database rows. [photoPath] is where the photo was restored to. */
fun BackupRecipe.toDetails(photoPath: String?): RecipeWithDetails = RecipeWithDetails(
    recipe = RecipeEntity(
        title = title,
        description = description,
        servings = servings,
        prepTimeMinutes = prepTimeMinutes,
        cookTimeMinutes = cookTimeMinutes,
        imageUri = photoPath,
        notes = notes,
        isFavorite = isFavorite,
        createdAt = createdAt.takeIf { it > 0 } ?: System.currentTimeMillis(),
        updatedAt = updatedAt.takeIf { it > 0 } ?: System.currentTimeMillis(),
    ),
    ingredients = ingredients.mapIndexed { i, it ->
        IngredientEntity(
            recipeId = 0,
            position = i,
            quantity = it.quantity,
            unit = it.unit,
            name = it.name,
            rawText = it.rawText,
            note = it.note,
        )
    },
    steps = steps.mapIndexed { i, text -> StepEntity(recipeId = 0, position = i + 1, text = text) },
    source = source?.let {
        SourceEntity(
            recipeId = 0,
            platform = it.platform,
            url = it.url,
            author = it.author,
            rawText = it.rawText,
            fetchedAt = it.fetchedAt.takeIf { at -> at > 0 } ?: System.currentTimeMillis(),
        )
    },
    tags = tags.map { TagEntity(it) },
)

/**
 * Identity of a recipe across installs.
 *
 * Row ids are meaningless in another database, so restoring the same backup twice
 * would otherwise duplicate the whole library. Title plus creation time is stable
 * and effectively unique — two recipes created in the same millisecond with the
 * same name are the same recipe.
 */
fun BackupRecipe.identity(): String = "$title@$createdAt"

fun RecipeEntity.identity(): String = "$title@$createdAt"

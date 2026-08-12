package com.delizioso.app.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** A saved recipe (user-facing core entity). */
@Entity(tableName = "recipes")
data class RecipeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String? = null,
    val servings: Int? = null,
    val prepTimeMinutes: Int? = null,
    val cookTimeMinutes: Int? = null,
    /** Local image URI (content:// or file://) or null. */
    val imageUri: String? = null,
    val notes: String? = null,
    val isFavorite: Boolean = false,
    // Macros are not stored: they are summed from the ingredients on the fly by
    // MacroCalculator, so an edit can never leave a stale total behind.
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "ingredients",
    foreignKeys = [
        ForeignKey(
            entity = RecipeEntity::class,
            parentColumns = ["id"],
            childColumns = ["recipeId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("recipeId")],
)
data class IngredientEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recipeId: Long,
    /** Sort order within the recipe. */
    val position: Int,
    /** Parsed amount text, e.g. "2". */
    val quantity: String? = null,
    /** Parsed unit, e.g. "cups". */
    val unit: String? = null,
    /** Canonical ingredient name, e.g. "flour". */
    val name: String,
    /** Original line as extracted, e.g. "2 cups all-purpose flour". */
    val rawText: String? = null,
    /** Substitution / note, e.g. "can use almond flour". */
    val note: String? = null,
)

@Entity(
    tableName = "steps",
    foreignKeys = [
        ForeignKey(
            entity = RecipeEntity::class,
            parentColumns = ["id"],
            childColumns = ["recipeId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("recipeId")],
)
data class StepEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recipeId: Long,
    /** 1-based step number. */
    val position: Int,
    val text: String,
)

/** Dietary / category label, e.g. "Vegan", "Dinner". Name is the unique key. */
@Entity(tableName = "tags")
data class TagEntity(
    @PrimaryKey val name: String,
)

@Entity(
    tableName = "recipe_tag_cross_ref",
    primaryKeys = ["recipeId", "tagName"],
    foreignKeys = [
        ForeignKey(
            entity = RecipeEntity::class,
            parentColumns = ["id"],
            childColumns = ["recipeId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = TagEntity::class,
            parentColumns = ["name"],
            childColumns = ["tagName"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("tagName")],
)
data class RecipeTagCrossRef(
    val recipeId: Long,
    val tagName: String,
)

/** Import provenance (1:1 with a recipe). */
@Entity(
    tableName = "sources",
    foreignKeys = [
        ForeignKey(
            entity = RecipeEntity::class,
            parentColumns = ["id"],
            childColumns = ["recipeId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
)
data class SourceEntity(
    @PrimaryKey val recipeId: Long,
    /** MANUAL | INSTAGRAM | FACEBOOK | TIKTOK | YOUTUBE | BLOG | OCR. */
    val platform: String,
    val url: String? = null,
    /** @author handle, channel name or site name. */
    val author: String? = null,
    /** Raw caption/description/text that was fetched. */
    val rawText: String? = null,
    val fetchedAt: Long = System.currentTimeMillis(),
)

object Platform {
    const val MANUAL = "MANUAL"
    const val INSTAGRAM = "INSTAGRAM"
    const val FACEBOOK = "FACEBOOK"
    const val TIKTOK = "TIKTOK"
    const val YOUTUBE = "YOUTUBE"
    const val BLOG = "BLOG"
    const val OCR = "OCR"
}

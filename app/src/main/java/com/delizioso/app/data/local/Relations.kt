package com.delizioso.app.data.local

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation

/** A recipe with all its nested content, loaded atomically. */
data class RecipeWithDetails(
    @Embedded val recipe: RecipeEntity,
    @Relation(parentColumn = "id", entityColumn = "recipeId")
    val ingredients: List<IngredientEntity> = emptyList(),
    @Relation(parentColumn = "id", entityColumn = "recipeId")
    val steps: List<StepEntity> = emptyList(),
    @Relation(parentColumn = "id", entityColumn = "recipeId")
    val source: SourceEntity? = null,
    @Relation(
        parentColumn = "id",
        entityColumn = "name",
        associateBy = Junction(
            value = RecipeTagCrossRef::class,
            parentColumn = "recipeId",
            entityColumn = "tagName",
        )
    )
    val tags: List<TagEntity> = emptyList(),
)

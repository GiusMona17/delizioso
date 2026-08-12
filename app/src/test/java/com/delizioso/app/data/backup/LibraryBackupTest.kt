package com.delizioso.app.data.backup

import com.delizioso.app.data.import.IngredientParser
import com.delizioso.app.data.local.RecipeEntity
import com.delizioso.app.data.local.RecipeWithDetails
import com.delizioso.app.data.local.SourceEntity
import com.delizioso.app.data.local.StepEntity
import com.delizioso.app.data.local.TagEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A backup is only worth having if what comes back is what went in, so the
 * conversion is pinned in both directions.
 */
class LibraryBackupTest {

    private val details = RecipeWithDetails(
        recipe = RecipeEntity(
            id = 7,
            title = "Carbonara",
            description = "Romana",
            servings = 2,
            prepTimeMinutes = 10,
            cookTimeMinutes = 15,
            imageUri = "/data/user/0/app/files/recipe_images/recipe_1.jpg",
            notes = "Usa il pecorino",
            isFavorite = true,
            createdAt = 1_700_000_000_000,
            updatedAt = 1_700_000_500_000,
        ),
        ingredients = listOf("200 g spaghetti", "100 g guanciale", "2 uova")
            .mapIndexed { i, line -> IngredientParser.split(line).copy(recipeId = 7, position = i) },
        steps = listOf("Rosola il guanciale.", "Manteca fuori dal fuoco.")
            .mapIndexed { i, text -> StepEntity(recipeId = 7, position = i + 1, text = text) },
        source = SourceEntity(recipeId = 7, platform = "BLOG", url = "https://example.com/x", author = "Tizio"),
        tags = listOf(TagEntity("Dinner"), TagEntity("Pasta")),
    )

    @Test
    fun `a recipe survives the round trip intact`() {
        val restored = details.toBackup("recipe_1.jpg").toDetails("/new/path/recipe_1.jpg")

        assertEquals("Carbonara", restored.recipe.title)
        assertEquals("Romana", restored.recipe.description)
        assertEquals(2, restored.recipe.servings)
        assertEquals(10, restored.recipe.prepTimeMinutes)
        assertEquals(15, restored.recipe.cookTimeMinutes)
        assertEquals("Usa il pecorino", restored.recipe.notes)
        assertTrue(restored.recipe.isFavorite)
        assertEquals(1_700_000_000_000, restored.recipe.createdAt)
        // The photo lands wherever the restoring install put it.
        assertEquals("/new/path/recipe_1.jpg", restored.recipe.imageUri)

        assertEquals(3, restored.ingredients.size)
        assertEquals("200 g spaghetti", restored.ingredients[0].rawText)
        assertEquals("spaghetti", restored.ingredients[0].name)
        assertEquals(listOf(0, 1, 2), restored.ingredients.map { it.position })

        assertEquals(listOf("Rosola il guanciale.", "Manteca fuori dal fuoco."), restored.steps.map { it.text })
        assertEquals(listOf(1, 2), restored.steps.map { it.position })

        assertEquals("https://example.com/x", restored.source?.url)
        assertEquals("Tizio", restored.source?.author)
        assertEquals(listOf("Dinner", "Pasta"), restored.tags.map { it.name })
    }

    @Test
    fun `order is restored from position, not row order`() {
        val jumbled = details.copy(
            ingredients = details.ingredients.reversed(),
            steps = details.steps.reversed(),
        )
        val restored = jumbled.toBackup(null).toDetails(null)
        assertEquals("spaghetti", restored.ingredients[0].name)
        assertEquals("Rosola il guanciale.", restored.steps[0].text)
    }

    /** Restoring the same file twice must not double the library. */
    @Test
    fun `identity matches the same recipe across installs`() {
        val record = details.toBackup(null)
        assertEquals(details.recipe.identity(), record.identity())

        val other = details.recipe.copy(id = 99, createdAt = 1_700_000_000_001)
        assertNotEquals(other.identity(), record.identity())
    }

    @Test
    fun `the manifest is valid json and reloads`() {
        val file = BackupFile(recipes = listOf(details.toBackup("recipe_1.jpg")))
        val text = BackupFile.json.encodeToString(BackupFile.serializer(), file)
        val reloaded = BackupFile.json.decodeFromString(BackupFile.serializer(), text)

        assertEquals(BackupFile.FORMAT_VERSION, reloaded.version)
        assertEquals(1, reloaded.recipes.size)
        assertEquals("Carbonara", reloaded.recipes[0].title)
        assertEquals("recipe_1.jpg", reloaded.recipes[0].photo)
    }

    @Test
    fun `a recipe with no photo, source or tags still round-trips`() {
        val bare = RecipeWithDetails(recipe = RecipeEntity(title = "Pane", createdAt = 1L, updatedAt = 1L))
        val restored = bare.toBackup(null).toDetails(null)
        assertEquals("Pane", restored.recipe.title)
        assertEquals(null, restored.recipe.imageUri)
        assertEquals(null, restored.source)
        assertTrue(restored.tags.isEmpty())
    }
}

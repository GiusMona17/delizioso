package com.delizioso.app.data.docs

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.delizioso.app.PlainTestApplication
import com.delizioso.app.data.RecipeRepository
import com.delizioso.app.data.local.CustomGroceryLine
import com.delizioso.app.data.local.IngredientEntity
import com.delizioso.app.data.local.PantryItemEntity
import com.delizioso.app.data.local.PlannedMealEntity
import com.delizioso.app.data.local.RecipeEntity
import com.delizioso.app.data.local.RecipeWithDetails
import com.delizioso.app.data.sync.newDeliziosoDb
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = PlainTestApplication::class)
class DeliziosoDocsLoaderTest {

    private val db = newDeliziosoDb()
    private val repository = RecipeRepository(db.recipeDao(), db.pantryDao(), db.quickMealDao())
    private val now = Instant.parse("2026-10-08T15:23:54Z")
    private val today = LocalDate.of(2026, 10, 8)
    private var checked: Set<String> = emptySet()
    private var custom: List<CustomGroceryLine> = emptyList()

    private fun load() = runBlocking {
        DeliziosoDocsLoader(repository, { checked }, { custom }).load(now, ZoneId.of("Europe/Rome"))
    }

    private fun recipe(title: String, vararg ingredients: String): Long = runBlocking {
        repository.save(
            RecipeWithDetails(
                recipe = RecipeEntity(title = title, caloriesKcal = 480.0),
                ingredients = ingredients.mapIndexed { i, name -> IngredientEntity(recipeId = 0, position = i, name = name) },
            ),
        )
    }

    private fun plan(recipeId: Long?, day: LocalDate, slot: String, name: String? = null, kcal: Double? = null, servings: Int = 2) = runBlocking {
        repository.addMeal(
            PlannedMealEntity(
                recipeId = recipeId, customName = name, caloriesKcal = kcal,
                dateEpochDay = day.toEpochDay(), slot = slot, servings = servings,
            ),
        )
    }

    @Test
    fun `a planned meal gets its recipe title, or its own name, and its calories`() {
        val risotto = recipe("Risotto ai funghi")
        plan(risotto, today, "DINNER")
        plan(null, today, "LUNCH", name = "Piadina", kcal = 610.0, servings = 1)

        val meals = load().meals.sortedBy { it.slot }

        assertEquals(listOf("DINNER", "LUNCH"), meals.map { it.slot })
        assertEquals("Risotto ai funghi", meals[0].title)
        assertEquals(480, meals[0].kcal)       // the recipe's calories when the meal has none
        assertEquals("Piadina", meals[1].title)
        assertEquals(610, meals[1].kcal)
        assertEquals(risotto, meals[0].recipeId)
    }

    @Test
    fun `only the next 14 days are read`() {
        val id = recipe("Pasta")
        plan(id, today.plusDays(14), "DINNER")
        plan(id, today.plusDays(15), "DINNER")

        assertEquals(listOf(today.plusDays(14)), load().meals.map { it.date })
    }

    @Test
    fun `the shopping list is the next 7 days of recipes plus custom items, minus ticked and in-pantry ones`() = runBlocking {
        val id = recipe("Spaghetti al pomodoro", "spaghetti")
        plan(id, today.plusDays(1), "DINNER")
        plan(recipe("Torta", "farina"), today.plusDays(9), "DINNER") // beyond the 7-day shopping range
        custom = listOf(
            CustomGroceryLine("detersivo", null),
            CustomGroceryLine("lievito", null),
            CustomGroceryLine("sale grosso", null),
        )
        checked = setOf("lievito")
        repository.savePantryItem(PantryItemEntity(name = "sale grosso", inStock = true))

        val lines = load().shopping.map { it.line }

        assertTrue(lines.any { "spaghetti" in it })
        assertTrue(lines.none { "farina" in it })
        assertTrue("detersivo" in lines)
        assertFalse("lievito" in lines)      // ticked
        assertFalse("sale grosso" in lines)  // in the pantry
    }

    @Test
    fun `the pantry is read whole and the day and the timezone come from the clock`() = runBlocking {
        repository.savePantryItem(PantryItemEntity(name = "Latte", expiresAtEpochDay = today.plusDays(2).toEpochDay()))
        repository.savePantryItem(PantryItemEntity(name = "Farina", inStock = false))

        val input = load()

        assertEquals(setOf("Latte", "Farina"), input.pantry.map { it.name }.toSet())
        assertEquals(today, input.today)
        assertEquals("Europe/Rome", input.timezone)
        assertEquals(now, input.generatedAt)
    }
}

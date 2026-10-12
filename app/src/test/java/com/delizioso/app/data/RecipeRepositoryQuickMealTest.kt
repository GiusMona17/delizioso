package com.delizioso.app.data

import com.delizioso.app.data.local.PantryDao
import com.delizioso.app.data.local.PlannedMealEntity
import com.delizioso.app.data.local.QuickMealDao
import com.delizioso.app.data.local.QuickMealEntity
import com.delizioso.app.data.local.RecipeDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.lang.reflect.Proxy

class RecipeRepositoryQuickMealTest {

    private val quickMealsList = mutableListOf<QuickMealEntity>()
    private val plannedMealsList = mutableListOf<PlannedMealEntity>()

    private val fakeQuickMealDao = object : QuickMealDao {
        override fun getAll(): Flow<List<QuickMealEntity>> = MutableStateFlow(quickMealsList)
        override fun getForSlot(slot: String): Flow<List<QuickMealEntity>> =
            MutableStateFlow(quickMealsList.filter { it.defaultSlot == slot || it.defaultSlot == null })
        override suspend fun getById(id: Long): QuickMealEntity? = quickMealsList.firstOrNull { it.id == id }
        override suspend fun insert(meal: QuickMealEntity): Long {
            val assignedId = if (meal.id == 0L) (quickMealsList.size + 1).toLong() else meal.id
            val inserted = meal.copy(id = assignedId)
            quickMealsList.removeAll { it.id == assignedId }
            quickMealsList.add(inserted)
            return assignedId
        }
        override suspend fun update(meal: QuickMealEntity) {
            quickMealsList.removeAll { it.id == meal.id }
            quickMealsList.add(meal)
        }
        override suspend fun delete(id: Long) {
            quickMealsList.removeAll { it.id == id }
        }
    }

    private val fakeRecipeDao = Proxy.newProxyInstance(
        RecipeDao::class.java.classLoader,
        arrayOf(RecipeDao::class.java),
    ) { _, method, args ->
        when (method.name) {
            "insertMeal" -> {
                val meal = args[0] as PlannedMealEntity
                val assignedId = (plannedMealsList.size + 1).toLong()
                plannedMealsList.add(meal.copy(id = assignedId))
                assignedId
            }
            "deleteMeal" -> {
                val id = args[0] as Long
                plannedMealsList.removeAll { it.id == id }
                Unit
            }
            else -> null
        }
    } as RecipeDao

    private val fakePantryDao = Proxy.newProxyInstance(
        PantryDao::class.java.classLoader,
        arrayOf(PantryDao::class.java),
    ) { _, _, _ -> null } as PantryDao

    private val repository = RecipeRepository(fakeRecipeDao, fakePantryDao, fakeQuickMealDao)

    @Test
    fun `save and delete quick meals in catalog`() = runBlocking {
        val id = repository.saveQuickMeal(
            QuickMealEntity(name = "Toast al prosciutto", caloriesKcal = 280.0)
        )
        assertEquals(1L, id)
        assertEquals(1, repository.quickMeals.first().size)

        repository.deleteQuickMeal(id)
        assertEquals(0, repository.quickMeals.first().size)
    }

    @Test
    fun `addSavedQuickMeal plans meal with snapshot macros and quickMealId`() = runBlocking {
        val quickMeal = QuickMealEntity(
            id = 10L,
            name = "Pasta al pomodoro semplice",
            caloriesKcal = 450.0,
            proteinG = 14.0,
            fatG = 8.0,
            carbsG = 80.0,
        )

        repository.addSavedQuickMeal(
            dateEpochDay = 100L,
            slot = "LUNCH",
            meal = quickMeal,
            servings = 2,
        )

        assertEquals(1, plannedMealsList.size)
        val planned = plannedMealsList[0]
        assertEquals(10L, planned.quickMealId)
        assertEquals("Pasta al pomodoro semplice", planned.customName)
        assertEquals(450.0, planned.caloriesKcal)
        assertEquals(2, planned.servings)
    }

    @Test
    fun `createAndPlanQuickMeal saves to catalog and plans meal simultaneously`() = runBlocking {
        val quickMeal = QuickMealEntity(
            name = "Frullato banana e latte",
            caloriesKcal = 220.0,
            proteinG = 8.0,
            fatG = 3.0,
            carbsG = 40.0,
        )

        repository.createAndPlanQuickMeal(
            dateEpochDay = 105L,
            slot = "SNACK",
            meal = quickMeal,
            servings = 1,
        )

        assertEquals(1, quickMealsList.size)
        assertEquals(1, plannedMealsList.size)
        assertEquals(quickMealsList[0].id, plannedMealsList[0].quickMealId)
        assertEquals("Frullato banana e latte", plannedMealsList[0].customName)
    }
}

package com.delizioso.app.data.nutrition

import com.delizioso.app.data.local.MealSlot
import com.delizioso.app.data.local.PlannedMealEntity
import com.delizioso.app.data.local.PlannedMealWithRecipe
import com.delizioso.app.data.local.RecipeEntity
import com.delizioso.app.data.local.RecipeWithDetails
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class NutritionAggregatorTest {

    @Test
    fun `computeDistribution calculates correct percentage splits summing to 100`() {
        val dist = NutritionAggregator.computeDistribution(
            proteinG = 100, // 400 kcal
            fatG = 50,     // 450 kcal
            carbsG = 150,  // 600 kcal
        )
        // Total = 1450 kcal
        // Protein = 400 / 1450 = 27.5% -> 27%
        // Fat = 450 / 1450 = 31.0% -> 31%
        // Carbs = remainder (42%)
        assertEquals(27, dist.proteinPct)
        assertEquals(31, dist.fatPct)
        assertEquals(42, dist.carbsPct)
        assertEquals(100, dist.proteinPct + dist.fatPct + dist.carbsPct)
    }

    @Test
    fun `computeDistribution handles zero energy safely`() {
        val dist = NutritionAggregator.computeDistribution(0, 0, 0)
        assertEquals(0, dist.proteinPct)
        assertEquals(0, dist.fatPct)
        assertEquals(0, dist.carbsPct)
    }

    @Test
    fun `computeDayRecap sums macros of planned meals for the day`() {
        val date = LocalDate.of(2026, 8, 21)
        val recipe1 = RecipeEntity(
            id = 1L,
            title = "Grilled Chicken Salad",
            servings = 1,
            caloriesKcal = 450.0,
            proteinG = 40.0,
            fatG = 15.0,
            carbsG = 20.0,
        )
        val recipe2 = RecipeEntity(
            id = 2L,
            title = "Protein Pasta",
            servings = 1,
            caloriesKcal = 550.0,
            proteinG = 35.0,
            fatG = 10.0,
            carbsG = 80.0,
        )

        val meal1 = PlannedMealWithRecipe(
            meal = PlannedMealEntity(id = 10L, recipeId = 1L, dateEpochDay = date.toEpochDay(), slot = MealSlot.LUNCH),
            recipe = recipe1,
        )
        val meal2 = PlannedMealWithRecipe(
            meal = PlannedMealEntity(id = 20L, recipeId = 2L, dateEpochDay = date.toEpochDay(), slot = MealSlot.DINNER),
            recipe = recipe2,
        )

        val detailsList = listOf(
            RecipeWithDetails(recipe = recipe1),
            RecipeWithDetails(recipe = recipe2),
        )

        val recap = NutritionAggregator.computeDayRecap(
            date = date,
            meals = listOf(meal1, meal2),
            allRecipes = detailsList,
        )

        assertEquals(date, recap.date)
        assertEquals(2, recap.mealCount)
        assertEquals(1000, recap.nutrients.caloriesKcal)
        assertEquals(75, recap.nutrients.proteinG)
        assertEquals(25, recap.nutrients.fatG)
        assertEquals(100, recap.nutrients.carbsG)
        assertTrue(recap.isCompleteEstimate)
    }

    @Test
    fun `computeWeekRecap aggregates 7 days and calculates daily averages`() {
        val monday = LocalDate.of(2026, 8, 17)
        val recipe = RecipeEntity(
            id = 1L,
            title = "Salmon & Asparagus",
            servings = 1,
            caloriesKcal = 600.0,
            proteinG = 50.0,
            fatG = 20.0,
            carbsG = 30.0,
        )

        // Plan salmon for Monday, Wednesday, Friday
        val meals = listOf(
            PlannedMealWithRecipe(
                meal = PlannedMealEntity(id = 1L, recipeId = 1L, dateEpochDay = monday.toEpochDay(), slot = MealSlot.DINNER),
                recipe = recipe,
            ),
            PlannedMealWithRecipe(
                meal = PlannedMealEntity(id = 2L, recipeId = 1L, dateEpochDay = monday.plusDays(2).toEpochDay(), slot = MealSlot.DINNER),
                recipe = recipe,
            ),
            PlannedMealWithRecipe(
                meal = PlannedMealEntity(id = 3L, recipeId = 1L, dateEpochDay = monday.plusDays(4).toEpochDay(), slot = MealSlot.DINNER),
                recipe = recipe,
            ),
        )

        val weekRecap = NutritionAggregator.computeWeekRecap(
            weekStart = monday,
            meals = meals,
            allRecipes = listOf(RecipeWithDetails(recipe = recipe)),
        )

        assertEquals(7, weekRecap.days.size)
        assertEquals(3, weekRecap.totalPlannedMeals)
        assertEquals(1800, weekRecap.totalNutrients.caloriesKcal)
        assertEquals(150, weekRecap.totalNutrients.proteinG)
        assertEquals(60, weekRecap.totalNutrients.fatG)
        assertEquals(90, weekRecap.totalNutrients.carbsG)

        // Divisor is 3 (active days)
        assertEquals(600, weekRecap.dailyAverage.caloriesKcal)
        assertEquals(50, weekRecap.dailyAverage.proteinG)
        assertEquals(20, weekRecap.dailyAverage.fatG)
        assertEquals(30, weekRecap.dailyAverage.carbsG)
    }
}

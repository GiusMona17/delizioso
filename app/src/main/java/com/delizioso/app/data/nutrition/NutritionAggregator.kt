package com.delizioso.app.data.nutrition

import com.delizioso.app.data.local.PlannedMealWithRecipe
import com.delizioso.app.data.local.RecipeWithDetails
import java.time.LocalDate

data class DailyNutrients(
    val caloriesKcal: Int = 0,
    val proteinG: Int = 0,
    val fatG: Int = 0,
    val carbsG: Int = 0,
)

data class MacroDistribution(
    val proteinPct: Int = 0,
    val fatPct: Int = 0,
    val carbsPct: Int = 0,
)

data class DayNutritionRecap(
    val date: LocalDate,
    val nutrients: DailyNutrients = DailyNutrients(),
    val distribution: MacroDistribution = MacroDistribution(),
    val mealCount: Int = 0,
    val isCompleteEstimate: Boolean = true,
)

data class WeekNutritionRecap(
    val weekStart: LocalDate,
    val days: List<DayNutritionRecap> = emptyList(),
    val totalNutrients: DailyNutrients = DailyNutrients(),
    val dailyAverage: DailyNutrients = DailyNutrients(),
    val averageDistribution: MacroDistribution = MacroDistribution(),
    val totalPlannedMeals: Int = 0,
)

object NutritionAggregator {

    /**
     * Compute macro energy distribution:
     * - Protein: 4 kcal / gram
     * - Carbs: 4 kcal / gram
     * - Fat: 9 kcal / gram
     */
    fun computeDistribution(proteinG: Int, fatG: Int, carbsG: Int): MacroDistribution {
        val proteinKcal = proteinG * 4.0
        val fatKcal = fatG * 9.0
        val carbsKcal = carbsG * 4.0
        val totalEnergyKcal = proteinKcal + fatKcal + carbsKcal

        if (totalEnergyKcal <= 0.0) {
            return MacroDistribution(0, 0, 0)
        }

        val pPct = ((proteinKcal / totalEnergyKcal) * 100).toInt()
        val fPct = ((fatKcal / totalEnergyKcal) * 100).toInt()
        val cPct = (100 - pPct - fPct).coerceAtLeast(0)

        return MacroDistribution(
            proteinPct = pPct,
            fatPct = fPct,
            carbsPct = cPct,
        )
    }

    /**
     * Compute single-day nutritional aggregation for one person.
     */
    fun computeDayRecap(
        date: LocalDate,
        meals: List<PlannedMealWithRecipe>,
        allRecipes: List<RecipeWithDetails>,
    ): DayNutritionRecap {
        val dayMeals = meals.filter { it.meal.dateEpochDay == date.toEpochDay() }
        var totalKcal = 0.0
        var totalProtein = 0.0
        var totalFat = 0.0
        var totalCarbs = 0.0
        var hasRecognizedNutrition = false

        dayMeals.forEach { planned ->
            val recipe = planned.recipe ?: return@forEach
            val details = allRecipes.firstOrNull { it.recipe.id == recipe.id } ?: RecipeWithDetails(recipe = recipe)
            val macros = MacroCalculator.of(details)
            if (macros != null) {
                hasRecognizedNutrition = true
                totalKcal += macros.kcal
                totalProtein += macros.proteinG
                totalFat += macros.fatG
                totalCarbs += macros.carbsG
            }
        }

        val nutrients = DailyNutrients(
            caloriesKcal = totalKcal.toInt(),
            proteinG = totalProtein.toInt(),
            fatG = totalFat.toInt(),
            carbsG = totalCarbs.toInt(),
        )

        return DayNutritionRecap(
            date = date,
            nutrients = nutrients,
            distribution = computeDistribution(nutrients.proteinG, nutrients.fatG, nutrients.carbsG),
            mealCount = dayMeals.size,
            isCompleteEstimate = hasRecognizedNutrition || dayMeals.isEmpty(),
        )
    }

    /**
     * Compute 7-day weekly nutritional aggregation.
     */
    fun computeWeekRecap(
        weekStart: LocalDate,
        meals: List<PlannedMealWithRecipe>,
        allRecipes: List<RecipeWithDetails>,
    ): WeekNutritionRecap {
        val days = (0..6).map { offset ->
            val day = weekStart.plusDays(offset.toLong())
            computeDayRecap(day, meals, allRecipes)
        }

        val daysWithMeals = days.filter { it.mealCount > 0 }
        val divisor = if (daysWithMeals.isNotEmpty()) daysWithMeals.size else 7

        var sumKcal = 0
        var sumProtein = 0
        var sumFat = 0
        var sumCarbs = 0
        var totalMeals = 0

        days.forEach { day ->
            sumKcal += day.nutrients.caloriesKcal
            sumProtein += day.nutrients.proteinG
            sumFat += day.nutrients.fatG
            sumCarbs += day.nutrients.carbsG
            totalMeals += day.mealCount
        }

        val totalNutrients = DailyNutrients(sumKcal, sumProtein, sumFat, sumCarbs)
        val dailyAverage = DailyNutrients(
            caloriesKcal = sumKcal / divisor,
            proteinG = sumProtein / divisor,
            fatG = sumFat / divisor,
            carbsG = sumCarbs / divisor,
        )

        return WeekNutritionRecap(
            weekStart = weekStart,
            days = days,
            totalNutrients = totalNutrients,
            dailyAverage = dailyAverage,
            averageDistribution = computeDistribution(dailyAverage.proteinG, dailyAverage.fatG, dailyAverage.carbsG),
            totalPlannedMeals = totalMeals,
        )
    }
}

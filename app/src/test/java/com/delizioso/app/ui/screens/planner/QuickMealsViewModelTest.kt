package com.delizioso.app.ui.screens.planner

import com.delizioso.app.data.local.MealSlot
import com.delizioso.app.data.local.QuickMealEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class QuickMealsViewModelTest {

    @Test
    fun `filtering meals by slot and search query`() {
        val meals = listOf(
            QuickMealEntity(id = 1, name = "Caffè e cornetto", defaultSlot = MealSlot.BREAKFAST),
            QuickMealEntity(id = 2, name = "Pasta al burro", defaultSlot = MealSlot.LUNCH),
            QuickMealEntity(id = 3, name = "Insalata mista", defaultSlot = null),
            QuickMealEntity(id = 4, name = "Latte e fette biscottate", defaultSlot = MealSlot.BREAKFAST),
        )

        // Slot filter BREAKFAST (includes meals with defaultSlot BREAKFAST or null)
        val breakfastMeals = meals.filter { it.defaultSlot == MealSlot.BREAKFAST || it.defaultSlot == null }
        assertEquals(3, breakfastMeals.size)

        // Slot filter LUNCH
        val lunchMeals = meals.filter { it.defaultSlot == MealSlot.LUNCH || it.defaultSlot == null }
        assertEquals(2, lunchMeals.size)

        // Search filter "caffè"
        val searchMeals = meals.filter { it.name.contains("caffè", ignoreCase = true) }
        assertEquals(1, searchMeals.size)
        assertEquals(1L, searchMeals[0].id)

        // Search filter with slot filter
        val combined = meals.filter {
            val matchesSlot = it.defaultSlot == MealSlot.BREAKFAST || it.defaultSlot == null
            val matchesQuery = it.name.contains("latte", ignoreCase = true)
            matchesSlot && matchesQuery
        }
        assertEquals(1, combined.size)
        assertEquals(4L, combined[0].id)
    }
}

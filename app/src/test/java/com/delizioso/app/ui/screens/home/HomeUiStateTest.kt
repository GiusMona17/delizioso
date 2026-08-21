package com.delizioso.app.ui.screens.home

import com.delizioso.app.data.local.MealSlot
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class HomeUiStateTest {

    @Test
    fun `greeting enum covers all times of day`() {
        fun greetingForHour(hour: Int): TimeOfDayGreeting = when {
            hour in 5..11 -> TimeOfDayGreeting.MORNING
            hour in 12..17 -> TimeOfDayGreeting.AFTERNOON
            else -> TimeOfDayGreeting.EVENING
        }

        assertEquals(TimeOfDayGreeting.MORNING, greetingForHour(8))
        assertEquals(TimeOfDayGreeting.MORNING, greetingForHour(11))
        assertEquals(TimeOfDayGreeting.AFTERNOON, greetingForHour(12))
        assertEquals(TimeOfDayGreeting.AFTERNOON, greetingForHour(15))
        assertEquals(TimeOfDayGreeting.EVENING, greetingForHour(18))
        assertEquals(TimeOfDayGreeting.EVENING, greetingForHour(23))
        assertEquals(TimeOfDayGreeting.EVENING, greetingForHour(3))
    }

    @Test
    fun `target slot resolves correctly per time of day`() {
        fun targetSlotForHour(hour: Int): String = when {
            hour in 5..10 -> MealSlot.BREAKFAST
            hour in 11..15 -> MealSlot.LUNCH
            hour in 16..22 -> MealSlot.DINNER
            else -> MealSlot.BREAKFAST
        }

        assertEquals(MealSlot.BREAKFAST, targetSlotForHour(7))
        assertEquals(MealSlot.LUNCH, targetSlotForHour(12))
        assertEquals(MealSlot.DINNER, targetSlotForHour(19))
        assertEquals(MealSlot.BREAKFAST, targetSlotForHour(23))
    }

    @Test
    fun `upcoming meal state defaults properly`() {
        val defaultState = UpcomingMealState(slot = MealSlot.DINNER, isPlanned = false)
        assertEquals(MealSlot.DINNER, defaultState.slot)
        assertEquals(false, defaultState.isPlanned)
        assertEquals(null, defaultState.mainMeal)
        assertEquals(emptyList<Any>(), defaultState.sideMeals)
    }

    @Test
    fun `day overview marks today correctly`() {
        val today = LocalDate.now()
        val overview = DayOverview(
            date = today,
            isToday = true,
            hasPlanned = true,
            hasCooked = false,
        )
        assertEquals(true, overview.isToday)
        assertEquals(true, overview.hasPlanned)
        assertEquals(false, overview.hasCooked)
    }
}

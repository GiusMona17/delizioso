package com.delizioso.app.data.local

import androidx.sqlite.db.SupportSQLiteDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy

class QuickMealEntityAndMigrationTest {

    @Test
    fun `quick meal entity default values and properties`() {
        val meal = QuickMealEntity(
            id = 5L,
            name = "Latte e biscotti",
            defaultSlot = MealSlot.BREAKFAST,
            caloriesKcal = 320.0,
            proteinG = 10.0,
            fatG = 8.0,
            carbsG = 52.0,
        )

        assertEquals(5L, meal.id)
        assertEquals("Latte e biscotti", meal.name)
        assertEquals(MealSlot.BREAKFAST, meal.defaultSlot)
        assertEquals(320.0, meal.caloriesKcal)
        assertEquals(10.0, meal.proteinG)
        assertEquals(8.0, meal.fatG)
        assertEquals(52.0, meal.carbsG)
        assertTrue(meal.createdAtEpochMilli > 0)
    }

    @Test
    fun `planned meal entity supports nullable quickMealId`() {
        val planned = PlannedMealEntity(
            id = 1L,
            quickMealId = 5L,
            customName = "Latte e biscotti",
            caloriesKcal = 320.0,
            dateEpochDay = 100L,
            slot = MealSlot.BREAKFAST,
        )

        assertEquals(5L, planned.quickMealId)
        assertNull(planned.recipeId)
        assertEquals("Latte e biscotti", planned.customName)
    }

    @Test
    fun `migration 9 to 10 executes expected schema statements`() {
        val executedSql = mutableListOf<String>()
        val db = Proxy.newProxyInstance(
            SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(SupportSQLiteDatabase::class.java),
        ) { _, method, args ->
            if (method.name == "execSQL" && args != null && args.isNotEmpty()) {
                executedSql.add(args[0] as String)
            }
            null
        } as SupportSQLiteDatabase

        AppDatabase.MIGRATION_9_10.migrate(db)

        assertEquals(5, executedSql.size)
        assertTrue(executedSql[0].contains("CREATE TABLE IF NOT EXISTS `quick_meals`"))
        assertTrue(executedSql[0].contains("`name` TEXT NOT NULL"))
        assertTrue(executedSql[1].contains("CREATE INDEX IF NOT EXISTS `index_quick_meals_name` ON `quick_meals` (`name`)"))
        assertTrue(executedSql[2].contains("CREATE INDEX IF NOT EXISTS `index_quick_meals_defaultSlot` ON `quick_meals` (`defaultSlot`)"))
        assertTrue(executedSql[3].contains("ALTER TABLE `planned_meals` ADD COLUMN `quickMealId` INTEGER DEFAULT NULL REFERENCES `quick_meals`(`id`) ON DELETE SET NULL"))
        assertTrue(executedSql[4].contains("CREATE INDEX IF NOT EXISTS `index_planned_meals_quickMealId` ON `planned_meals` (`quickMealId`)"))
    }
}

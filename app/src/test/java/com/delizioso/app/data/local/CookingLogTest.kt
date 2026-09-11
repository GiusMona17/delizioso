package com.delizioso.app.data.local

import androidx.sqlite.db.SupportSQLiteDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy
import java.time.LocalDate

class CookingLogTest {

    @Test
    fun `cooking log entity retains all attributes`() {
        val log = CookingLogEntity(
            id = 42L,
            recipeId = 7L,
            dateEpochDay = LocalDate.of(2026, 9, 11).toEpochDay(),
            notes = "Added extra garlic and baked for 25 minutes instead of 20.",
            photoUri = "/data/user/0/app/files/recipe_images/dish_1.jpg",
            rating = 5,
            createdAtEpochMilli = 1700000000000L,
        )

        assertEquals(42L, log.id)
        assertEquals(7L, log.recipeId)
        assertEquals(LocalDate.of(2026, 9, 11).toEpochDay(), log.dateEpochDay)
        assertEquals("Added extra garlic and baked for 25 minutes instead of 20.", log.notes)
        assertEquals("/data/user/0/app/files/recipe_images/dish_1.jpg", log.photoUri)
        assertEquals(5, log.rating)
        assertEquals(1700000000000L, log.createdAtEpochMilli)
    }

    @Test
    fun `cooking log entity default values`() {
        val todayEpoch = LocalDate.now().toEpochDay()
        val log = CookingLogEntity(
            recipeId = 10L,
            dateEpochDay = todayEpoch,
            notes = "First attempt",
        )

        assertEquals(0L, log.id)
        assertEquals(10L, log.recipeId)
        assertEquals(todayEpoch, log.dateEpochDay)
        assertEquals("First attempt", log.notes)
        assertNull(log.photoUri)
        assertNull(log.rating)
        assertTrue(log.createdAtEpochMilli > 0)
    }

    @Test
    fun `recipe with details includes cooking logs`() {
        val recipe = RecipeEntity(id = 1L, title = "Tiramisù")
        val logs = listOf(
            CookingLogEntity(id = 1L, recipeId = 1L, dateEpochDay = 100L, notes = "Delicious"),
            CookingLogEntity(id = 2L, recipeId = 1L, dateEpochDay = 105L, notes = "Used decaf coffee"),
        )
        val details = RecipeWithDetails(
            recipe = recipe,
            cookingLogs = logs,
        )

        assertEquals(2, details.cookingLogs.size)
        assertEquals("Delicious", details.cookingLogs[0].notes)
        assertEquals("Used decaf coffee", details.cookingLogs[1].notes)
    }

    @Test
    fun `cooking logs sorting orders newest first`() {
        val logs = listOf(
            CookingLogEntity(id = 1L, recipeId = 1L, dateEpochDay = 100L, notes = "Oldest"),
            CookingLogEntity(id = 2L, recipeId = 1L, dateEpochDay = 200L, notes = "Newest"),
            CookingLogEntity(id = 3L, recipeId = 1L, dateEpochDay = 150L, notes = "Middle"),
            CookingLogEntity(id = 4L, recipeId = 1L, dateEpochDay = 200L, notes = "Newest tie higher id"),
        )

        val sorted = logs.sortedWith(
            compareByDescending<CookingLogEntity> { it.dateEpochDay }.thenByDescending { it.id }
        )

        assertEquals(4L, sorted[0].id)
        assertEquals(2L, sorted[1].id)
        assertEquals(3L, sorted[2].id)
        assertEquals(1L, sorted[3].id)
    }

    @Test
    fun `migration 8 to 9 executes expected SQL statements`() {
        val executedSql = mutableListOf<String>()
        val db = Proxy.newProxyInstance(
            SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(SupportSQLiteDatabase::class.java)
        ) { _, method, args ->
            if (method.name == "execSQL" && args != null && args.isNotEmpty()) {
                executedSql.add(args[0] as String)
            }
            null
        } as SupportSQLiteDatabase

        AppDatabase.MIGRATION_8_9.migrate(db)

        assertEquals(3, executedSql.size)
        assertTrue(executedSql[0].contains("CREATE TABLE IF NOT EXISTS `cooking_logs`"))
        assertTrue(executedSql[0].contains("`recipeId` INTEGER NOT NULL"))
        assertTrue(executedSql[0].contains("`notes` TEXT NOT NULL"))
        assertTrue(executedSql[0].contains("`photoUri` TEXT"))
        assertTrue(executedSql[0].contains("FOREIGN KEY(`recipeId`) REFERENCES `recipes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE"))
        assertTrue(executedSql[1].contains("CREATE INDEX IF NOT EXISTS `index_cooking_logs_recipeId` ON `cooking_logs` (`recipeId`)"))
        assertTrue(executedSql[2].contains("CREATE INDEX IF NOT EXISTS `index_cooking_logs_dateEpochDay` ON `cooking_logs` (`dateEpochDay`)"))
    }
}

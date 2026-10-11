package com.delizioso.app.data.provider

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.delizioso.app.PlainTestApplication
import com.delizioso.app.data.local.AppDatabase
import com.delizioso.app.data.local.MealSlot
import com.delizioso.app.data.local.PlannedMealEntity
import com.delizioso.app.data.local.RecipeEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
@Config(application = PlainTestApplication::class)
class DeliziosoMealsProviderTest {

    private lateinit var db: AppDatabase
    private lateinit var provider: DeliziosoMealsProvider
    private val testDate = LocalDate.of(2026, 10, 11)

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<PlainTestApplication>()
        db = AppDatabase.build(context)
        provider = Robolectric.buildContentProvider(DeliziosoMealsProvider::class.java).create().get()

        runBlocking {
            val recipeId = db.recipeDao().insertRecipe(
                RecipeEntity(title = "Pancakes proteici"),
            )
            db.recipeDao().insertMeal(
                PlannedMealEntity(
                    recipeId = recipeId,
                    dateEpochDay = testDate.toEpochDay(),
                    slot = MealSlot.BREAKFAST,
                    cooked = true,
                ),
            )
            db.recipeDao().insertMeal(
                PlannedMealEntity(
                    customName = "Insalata di pollo",
                    dateEpochDay = testDate.toEpochDay(),
                    slot = MealSlot.LUNCH,
                    cooked = false,
                ),
            )
            db.recipeDao().insertMeal(
                PlannedMealEntity(
                    customName = "Salmone al vapore",
                    dateEpochDay = testDate.toEpochDay(),
                    slot = MealSlot.DINNER,
                    cooked = true,
                ),
            )
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testCallGetDailyMeals() {
        val bundle = provider.call(
            DeliziosoMealsProvider.METHOD_GET_DAILY_MEALS,
            testDate.toString(),
            null,
        )

        assertNotNull(bundle)
        assertTrue(bundle!!.getBoolean(DeliziosoMealsProvider.KEY_HAS_MEALS))
        assertEquals("Pancakes proteici", bundle.getString(DeliziosoMealsProvider.KEY_COLAZIONE))
        assertEquals("Insalata di pollo", bundle.getString(DeliziosoMealsProvider.KEY_PRANZO))
        assertEquals("Salmone al vapore", bundle.getString(DeliziosoMealsProvider.KEY_CENA))
        assertTrue(bundle.getBoolean(DeliziosoMealsProvider.KEY_COLAZIONE_COOKED))
        assertEquals(false, bundle.getBoolean(DeliziosoMealsProvider.KEY_PRANZO_COOKED))
        assertTrue(bundle.getBoolean(DeliziosoMealsProvider.KEY_CENA_COOKED))
    }

    @Test
    fun testQueryMeals() {
        val uri = Uri.parse("content://${DeliziosoMealsProvider.AUTHORITY}/meals?date=$testDate")
        val cursor = provider.query(uri, null, null, null, null)

        assertNotNull(cursor)
        cursor!!.use {
            assertEquals(3, it.count)
            assertTrue(it.moveToFirst())
            assertEquals(MealSlot.BREAKFAST, it.getString(it.getColumnIndexOrThrow("slot")))
            assertEquals("Pancakes proteici", it.getString(it.getColumnIndexOrThrow("name")))
            assertEquals(1, it.getInt(it.getColumnIndexOrThrow("cooked")))
        }
    }
}

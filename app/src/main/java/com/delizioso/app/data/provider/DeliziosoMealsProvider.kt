package com.delizioso.app.data.provider

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import com.delizioso.app.DeliziosoApplication
import com.delizioso.app.data.local.AppDatabase
import com.delizioso.app.data.local.PlannedMealWithRecipe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.time.LocalDate

class DeliziosoMealsProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? {
        val dateParam = uri.getQueryParameter("date") ?: uri.lastPathSegment
        val date = runCatching { LocalDate.parse(dateParam) }.getOrNull()
            ?: runCatching { LocalDate.ofEpochDay(dateParam!!.toLong()) }.getOrNull()
            ?: return null

        val db = getDatabase()
        val planned = runBlocking(Dispatchers.IO) {
            db.recipeDao().getMealsForDay(date.toEpochDay())
        }

        val cursor = MatrixCursor(arrayOf("_id", "slot", "name", "cooked", "servings"))
        for (item in planned) {
            val name = item.recipe?.title ?: item.meal.customName.orEmpty()
            cursor.addRow(
                arrayOf<Any?>(
                    item.meal.id,
                    item.meal.slot,
                    name,
                    if (item.meal.cooked) 1 else 0,
                    item.meal.servings,
                ),
            )
        }
        return cursor
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        if (method == METHOD_GET_DAILY_MEALS && arg != null) {
            val date = runCatching { LocalDate.parse(arg) }.getOrNull() ?: return null
            val db = getDatabase()
            val planned = runBlocking(Dispatchers.IO) {
                db.recipeDao().getMealsForDay(date.toEpochDay())
            }

            val breakfast = planned.filter { it.meal.slot.equals("BREAKFAST", ignoreCase = true) }
            val lunch = planned.filter { it.meal.slot.equals("LUNCH", ignoreCase = true) }
            val dinner = planned.filter { it.meal.slot.equals("DINNER", ignoreCase = true) }
            val snack = planned.filter { it.meal.slot.equals("SNACK", ignoreCase = true) }

            fun formatItems(list: List<PlannedMealWithRecipe>): String =
                list.mapNotNull { it.recipe?.title ?: it.meal.customName }
                    .filter { it.isNotBlank() }
                    .joinToString(", ")

            return Bundle().apply {
                putString(KEY_COLAZIONE, formatItems(breakfast))
                putString(KEY_PRANZO, formatItems(lunch))
                putString(KEY_CENA, formatItems(dinner))
                putString(KEY_SPUNTINI, formatItems(snack))
                putBoolean(KEY_COLAZIONE_COOKED, breakfast.any { it.meal.cooked })
                putBoolean(KEY_PRANZO_COOKED, lunch.any { it.meal.cooked })
                putBoolean(KEY_CENA_COOKED, dinner.any { it.meal.cooked })
                putBoolean(KEY_HAS_MEALS, planned.isNotEmpty())
            }
        }
        return super.call(method, arg, extras)
    }

    override fun getType(uri: Uri): String? = "vnd.android.cursor.dir/vnd.com.delizioso.app.meals"

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    private fun getDatabase(): AppDatabase {
        val ctx = context ?: throw IllegalStateException("Context is null")
        return (ctx.applicationContext as? DeliziosoApplication)?.container?.database
            ?: AppDatabase.build(ctx)
    }

    companion object {
        const val AUTHORITY = "com.delizioso.app.meals"
        const val METHOD_GET_DAILY_MEALS = "get_daily_meals"
        const val KEY_COLAZIONE = "colazione"
        const val KEY_PRANZO = "pranzo"
        const val KEY_CENA = "cena"
        const val KEY_SPUNTINI = "spuntini"
        const val KEY_COLAZIONE_COOKED = "colazione_cooked"
        const val KEY_PRANZO_COOKED = "pranzo_cooked"
        const val KEY_CENA_COOKED = "cena_cooked"
        const val KEY_HAS_MEALS = "has_meals"
    }
}

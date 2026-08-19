package com.delizioso.app.data.search

import com.delizioso.app.data.import.ImportContent
import com.delizioso.app.data.import.Platform
import com.delizioso.app.data.import.TheMealDbImporter
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

class SearchResultHandoffTest {

    @Test
    fun `a meal becomes a RawImport carrying its source url`() {
        val meal = Json.parseToJsonElement(
            """{"idMeal":"52771","strMeal":"Penne","strMealThumb":"https://x/y.jpg"}"""
        ) as JsonObject

        val raw = MealDbMapper.toRawImport(meal)

        assertEquals(Platform.MEALDB.key, raw.platform)
        assertEquals("https://www.themealdb.com/meal/52771", raw.url)
        assertEquals(TheMealDbImporter.AUTHOR, raw.author)
        assertEquals("https://x/y.jpg", raw.thumbnailUrl)
        assertEquals("Penne", (raw.content as ImportContent.Structured).recipe.title)
    }
}

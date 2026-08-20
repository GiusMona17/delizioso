package com.delizioso.app.data.import

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecipeSourceTest {

    @Test
    fun `all expected sources exist and have unique IDs`() {
        val sources = RecipeSource.values()
        val ids = sources.map { it.id }
        assertEquals(ids.size, ids.distinct().size)
        assertTrue(sources.any { it.id == "giallo_zafferano" })
        assertTrue(sources.any { it.id == "the_meal_db" })
        assertTrue(sources.any { it.id == "youtube" })
        assertTrue(sources.any { it.id == "generic_web" })
    }

    @Test
    fun `lookup from ID works case-insensitively`() {
        assertEquals(RecipeSource.GIALLO_ZAFFERANO, RecipeSource.fromId("giallo_zafferano"))
        assertEquals(RecipeSource.GIALLO_ZAFFERANO, RecipeSource.fromId("GIALLO_ZAFFERANO"))
        assertNotNull(RecipeSource.fromId("tiktok"))
    }

    @Test
    fun `default active sources contains all sources with defaultEnabled true`() {
        val active = RecipeSource.defaultActiveSources()
        assertTrue(active.contains(RecipeSource.GIALLO_ZAFFERANO))
        assertTrue(active.contains(RecipeSource.GENERIC_WEB))
    }
}

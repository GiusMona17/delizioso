package com.delizioso.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CategoriesTest {

    @Test
    fun `matches ignoring case and padding`() {
        assertEquals("Vegetarian", Categories.canonicalise("  vegetarian "))
        assertEquals("Pasta", Categories.canonicalise("PASTA"))
    }

    @Test
    fun `maps a site's own wording onto the vocabulary`() {
        assertEquals("Dinner", Categories.canonicalise("Main course"))
        assertEquals("Dessert", Categories.canonicalise("pudding"))
        assertEquals("Vegetarian", Categories.canonicalise("veggie"))
    }

    @Test
    fun `rejects anything off the list`() {
        assertNull(Categories.canonicalise("Gluten-free"))
        assertNull(Categories.canonicalise("chef's special"))
        assertNull(Categories.canonicalise(""))
    }

    @Test
    fun `drops invented categories from a model's answer`() {
        val suggested = listOf("Pasta", "Weeknight Wonder", "vegetarian")
        assertEquals(listOf("Pasta", "Vegetarian"), Categories.canonicalise(suggested))
    }

    @Test
    fun `deduplicates and caps`() {
        val suggested = listOf("Quick", "quick", "Healthy", "Vegan", "Soup", "Salad")
        val result = Categories.canonicalise(suggested)
        assertEquals(Categories.MAX_PER_RECIPE, result.size)
        assertEquals(result.distinct(), result)
    }

    @Test
    fun `orders as declared, not as suggested`() {
        assertEquals(
            listOf("Dessert", "Baking", "Quick"),
            Categories.canonicalise(listOf("Quick", "Baking", "Dessert")),
        )
    }
}

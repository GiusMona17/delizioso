package com.delizioso.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoriesTest {

    @Test
    fun `matches ignoring case and padding`() {
        assertEquals("Vegetarian", Categories.canonicalise("  vegetarian "))
        assertEquals("Pasta", Categories.canonicalise("PASTA"))
        assertEquals("Sauce", Categories.canonicalise("sauce"))
        assertEquals("Bread", Categories.canonicalise("bread"))
        assertEquals("Side", Categories.canonicalise("side"))
        assertEquals("Drink", Categories.canonicalise("drink"))
    }

    @Test
    fun `maps a site's own wording onto the vocabulary`() {
        assertEquals("Dinner", Categories.canonicalise("Main course"))
        assertEquals("Dessert", Categories.canonicalise("pudding"))
        assertEquals("Vegetarian", Categories.canonicalise("veggie"))
        assertEquals("Sauce", Categories.canonicalise("pesto"))
        assertEquals("Sauce", Categories.canonicalise("salsa"))
        assertEquals("Bread", Categories.canonicalise("focaccia"))
        assertEquals("Bread", Categories.canonicalise("pane"))
        assertEquals("Side", Categories.canonicalise("contorno"))
        assertEquals("Dressing & Marinade", Categories.canonicalise("vinaigrette"))
        assertEquals("Base & Broth", Categories.canonicalise("brodo"))
        assertEquals("Preserve", Categories.canonicalise("marmellata"))
    }

    @Test
    fun `rejects anything off the list`() {
        assertNull(Categories.canonicalise("Gluten-free"))
        assertNull(Categories.canonicalise("chef's special"))
        assertNull(Categories.canonicalise(""))
    }

    @Test
    fun `drops invented categories from a model's answer`() {
        val suggested = listOf("Pasta", "Weeknight Wonder", "vegetarian", "salsa")
        assertEquals(listOf("Pasta", "Sauce", "Vegetarian"), Categories.canonicalise(suggested))
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

    @Test
    fun `category grouping is accurate and complete`() {
        assertEquals(CategoryGroup.MEAL_TYPE, Categories.groupOf("Breakfast"))
        assertEquals(CategoryGroup.MEAL_TYPE, Categories.groupOf("Dinner"))
        assertEquals(CategoryGroup.COURSE_COMPONENT, Categories.groupOf("Sauce"))
        assertEquals(CategoryGroup.COURSE_COMPONENT, Categories.groupOf("Bread"))
        assertEquals(CategoryGroup.COURSE_COMPONENT, Categories.groupOf("Side"))
        assertEquals(CategoryGroup.DIET_STYLE, Categories.groupOf("Vegetarian"))

        Categories.ALL.forEach { cat ->
            assertNotNull("Category $cat should have a group", Categories.groupOf(cat))
            assertTrue(Categories.displayNameRes(cat) != 0)
        }
    }
}

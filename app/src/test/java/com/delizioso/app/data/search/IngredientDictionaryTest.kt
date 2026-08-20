package com.delizioso.app.data.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IngredientDictionaryTest {

    @Test
    fun `contains comprehensive list of Italian ingredients`() {
        val ingredients = IngredientDictionary.ALL_ITALIAN_INGREDIENTS
        assertTrue(ingredients.isNotEmpty())
        assertTrue(ingredients.contains("Pollo"))
        assertTrue(ingredients.contains("Pomodoro"))
        assertTrue(ingredients.contains("Uova"))
        assertTrue(ingredients.contains("Aglio"))
        assertTrue(ingredients.contains("Cipolla"))
        assertTrue(ingredients.contains("Pasta"))
        assertTrue(ingredients.contains("Farina"))
        assertTrue(ingredients.contains("Parmigiano"))
        assertTrue(ingredients.contains("Salmone"))
    }

    @Test
    fun `translates Italian ingredients to English for international providers`() {
        assertEquals("chicken", IngredientDictionary.toEnglish("Pollo"))
        assertEquals("chicken breast", IngredientDictionary.toEnglish("petto di pollo"))
        assertEquals("tomato", IngredientDictionary.toEnglish("Pomodoro"))
        assertEquals("eggs", IngredientDictionary.toEnglish("Uova"))
        assertEquals("garlic", IngredientDictionary.toEnglish("Aglio"))
        assertEquals("onion", IngredientDictionary.toEnglish("Cipolla"))
        assertEquals("bacon", IngredientDictionary.toEnglish("Pancetta"))
        assertEquals("butter", IngredientDictionary.toEnglish("Burro"))
        assertEquals("flour", IngredientDictionary.toEnglish("Farina"))
        assertEquals("mushrooms", IngredientDictionary.toEnglish("Funghi"))
    }

    @Test
    fun `preserves unknown or already English terms`() {
        assertEquals("avocado", IngredientDictionary.toEnglish("avocado"))
        assertEquals("curry powder", IngredientDictionary.toEnglish("curry"))
        assertEquals("unknownitem", IngredientDictionary.toEnglish("unknownitem"))
    }
}

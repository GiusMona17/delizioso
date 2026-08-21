package com.delizioso.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class GroceryCategoriesTest {

    @Test
    fun `of categorizes Italian and English ingredients to appropriate aisles`() {
        assertEquals(GroceryCategories.PRODUCE, GroceryCategories.of("Pomodori freschi"))
        assertEquals(GroceryCategories.PRODUCE, GroceryCategories.of("Yellow onion"))
        assertEquals(GroceryCategories.MEAT, GroceryCategories.of("Petto di pollo"))
        assertEquals(GroceryCategories.MEAT, GroceryCategories.of("Minced beef"))
        assertEquals(GroceryCategories.SEAFOOD, GroceryCategories.of("Gamberetti boreali"))
        assertEquals(GroceryCategories.SEAFOOD, GroceryCategories.of("Salmon fillet"))
        assertEquals(GroceryCategories.EGGS, GroceryCategories.of("4 uova medie"))
        assertEquals(GroceryCategories.DAIRY, GroceryCategories.of("Parmigiano Reggiano DOP"))
        assertEquals(GroceryCategories.DAIRY, GroceryCategories.of("Heavy cream"))
        assertEquals(GroceryCategories.BAKERY, GroceryCategories.of("Pane casereccio"))
        assertEquals(GroceryCategories.PANTRY, GroceryCategories.of("Spaghetti n.5"))
        assertEquals(GroceryCategories.PANTRY, GroceryCategories.of("Farina 00"))
        assertEquals(GroceryCategories.CANNED, GroceryCategories.of("Passata di pomodoro"))
        assertEquals(GroceryCategories.SPICES, GroceryCategories.of("Olio extravergine di oliva"))
        assertEquals(GroceryCategories.BEVERAGES, GroceryCategories.of("Vino bianco secco"))
    }
}

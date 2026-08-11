package com.delizioso.app.ui.screens.import

import org.junit.Assert.assertEquals
import org.junit.Test

class ImportViewModelShellTitleTest {

    private val rawOgTitle = "Crispy Honey BBQ Chicken Tenders 🍯✨ | " +
        "INGREDIENTI: 700g chicken, 1 cup buttermilk, 1 tsp salt | " +
        "PROCEDIMENTO: Marinate, dredge, fry | Hescooks | Facebook"

    @Test
    fun `first line of multi-line caption wins`() {
        // Regression: the whole recipe (ingredients + procedure) ended up in the title.
        val multiLineCaption = "Crispy Honey BBQ Chicken Tenders 🍯✨\n\n" +
            "Marinade\n• 700 g chicken tenderloins\n• 1 cup (240 ml) buttermilk\n\n" +
            "#recipe #honey"
        assertEquals("Crispy Honey BBQ Chicken Tenders 🍯✨", shellTitle(multiLineCaption, rawOgTitle))
    }

    @Test
    fun `blank caption falls back to og title`() {
        // Login-wall case: no usable caption text, og:title still gives a name.
        assertEquals("A Recipe Name", shellTitle("", "A Recipe Name"))
    }

    @Test
    fun `long first line is truncated`() {
        val longFirstLine = "x".repeat(200) + "\nrest"
        assertEquals(120, shellTitle(longFirstLine, "og title").length)
        assertEquals("x".repeat(120), shellTitle(longFirstLine, "og title"))
    }

    @Test
    fun `both blank gives empty title`() {
        assertEquals("", shellTitle("", null))
    }
}

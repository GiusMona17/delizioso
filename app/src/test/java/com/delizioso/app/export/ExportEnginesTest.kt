package com.delizioso.app.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportEnginesTest {

    @Test
    fun `SocialCardGenerator defines 4 to 5 social aspect ratio dimensions`() {
        assertEquals(1080, SocialCardGenerator.CARD_WIDTH)
        assertEquals(1350, SocialCardGenerator.CARD_HEIGHT)
        val ratio = SocialCardGenerator.CARD_HEIGHT.toDouble() / SocialCardGenerator.CARD_WIDTH.toDouble()
        assertEquals(1.25, ratio, 0.01)
    }

    @Test
    fun `RecipePdfExporter defines standard A4 dimensions in points`() {
        assertEquals(595, RecipePdfExporter.PAGE_WIDTH)
        assertEquals(842, RecipePdfExporter.PAGE_HEIGHT)
    }
}

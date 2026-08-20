package com.delizioso.app.data.search

import com.delizioso.app.data.import.RecipeSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BbcGoodFoodSearchProviderTest {

    @Test
    fun `parses BBC Good Food search NEXT_DATA JSON correctly`() {
        val html = """
            <html>
            <body>
                <script id="__NEXT_DATA__" type="application/json">
                {
                    "props": {
                        "pageProps": {
                            "items": [
                                {
                                    "title": "Ultimate spaghetti carbonara recipe",
                                    "url": "https://www.bbcgoodfood.com/recipes/ultimate-spaghetti-carbonara-recipe",
                                    "image": {
                                        "url": "https://images.immediate.co.uk/carbonara.jpg"
                                    }
                                }
                            ]
                        }
                    }
                }
                </script>
            </body>
            </html>
        """.trimIndent()

        val provider = BbcGoodFoodSearchProvider()
        val results = provider.parseSearchResults(html)

        assertEquals(1, results.size)
        assertEquals("Ultimate spaghetti carbonara recipe", results[0].title)
        assertEquals("https://www.bbcgoodfood.com/recipes/ultimate-spaghetti-carbonara-recipe", results[0].id)
        assertEquals("https://images.immediate.co.uk/carbonara.jpg", results[0].thumbnailUrl)
        assertEquals(RecipeSource.BBC_GOOD_FOOD, results[0].source)
    }
}

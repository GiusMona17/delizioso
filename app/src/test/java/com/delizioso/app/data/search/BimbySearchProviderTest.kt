package com.delizioso.app.data.search

import com.delizioso.app.data.import.RecipeSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BimbySearchProviderTest {

    @Test
    fun `parses Bimby search results JSON-LD correctly`() {
        val html = """
            <html>
            <head>
                <script type="application/ld+json">
                {
                    "@context": "http://schema.org",
                    "@type": "ItemList",
                    "itemListElement": [
                        {
                            "@type": "ListItem",
                            "position": 1,
                            "image": "https://www.ricetteperbimby.it/foto-ricette/carbonara-veloce-bimby-thumb.jpg",
                            "name": "Carbonara veloce",
                            "url": "https://www.ricetteperbimby.it/ricette/carbonara-veloce-bimby"
                        }
                    ]
                }
                </script>
            </head>
            <body></body>
            </html>
        """.trimIndent()

        val provider = BimbySearchProvider()
        val results = provider.parseSearchResults(html)

        assertEquals(1, results.size)
        assertEquals("Carbonara veloce", results[0].title)
        assertEquals("https://www.ricetteperbimby.it/ricette/carbonara-veloce-bimby", results[0].id)
        assertEquals(RecipeSource.RICETTE_BIMBY, results[0].source)
    }
}

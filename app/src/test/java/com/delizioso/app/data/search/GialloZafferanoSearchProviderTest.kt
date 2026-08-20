package com.delizioso.app.data.search

import com.delizioso.app.data.import.RecipeSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GialloZafferanoSearchProviderTest {

    @Test
    fun `parses GialloZafferano search result HTML correctly`() {
        val html = """
            <html>
            <body>
                <section class="gz-carousel-search">
                    <article class="gz-card gz-card-vertical">
                        <a href="https://ricette.giallozafferano.it/Spaghetti-alla-Carbonara.html" title="Spaghetti alla Carbonara">
                            <div class="gz-card-image">
                                <img src="https://www.giallozafferano.it/images/244-24489/Spaghetti-alla-Carbonara_360x300.jpg" alt="Carbonara" />
                            </div>
                            <div class="gz-card-content">
                                <h4 class="gz-title">Spaghetti alla Carbonara</h4>
                            </div>
                        </a>
                    </article>
                    <article class="gz-card gz-card-vertical">
                        <a href="https://ricette.giallozafferano.it/Carbonara-di-mare.html" title="Carbonara di mare">
                            <div class="gz-card-image">
                                <img src="https://www.giallozafferano.it/images/233-23340/Carbonara-di-mare_360x300.jpg" alt="Carbonara di mare" />
                            </div>
                            <div class="gz-card-content">
                                <h4 class="gz-title">Carbonara di mare</h4>
                            </div>
                        </a>
                    </article>
                </section>
            </body>
            </html>
        """.trimIndent()

        val provider = GialloZafferanoSearchProvider()
        val results = provider.parseSearchResults(html)

        assertEquals(2, results.size)
        assertEquals("Spaghetti alla Carbonara", results[0].title)
        assertEquals("https://ricette.giallozafferano.it/Spaghetti-alla-Carbonara.html", results[0].id)
        assertEquals("https://www.giallozafferano.it/images/244-24489/Spaghetti-alla-Carbonara_360x300.jpg", results[0].thumbnailUrl)
        assertEquals(RecipeSource.GIALLO_ZAFFERANO, results[0].source)

        assertEquals("Carbonara di mare", results[1].title)
        assertEquals("https://ricette.giallozafferano.it/Carbonara-di-mare.html", results[1].id)
        assertEquals(RecipeSource.GIALLO_ZAFFERANO, results[1].source)
    }
}

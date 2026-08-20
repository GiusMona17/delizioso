package com.delizioso.app.data.import

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class RecipeImporterRegistrySourceTest {

    @Test
    fun `detects correct RecipeSource for different URLs`() {
        assertEquals(RecipeSource.GIALLO_ZAFFERANO, PlatformDetector.sourceFor("https://ricette.giallozafferano.it/Spaghetti-alla-Carbonara.html"))
        assertEquals(RecipeSource.GIALLO_ZAFFERANO, PlatformDetector.sourceFor("https://www.giallozafferano.it/ricetta-tiramsiu"))
        assertEquals(RecipeSource.COOKIST, PlatformDetector.sourceFor("https://www.cookist.it/tiramisu/"))
        assertEquals(RecipeSource.CUCCHIAIO, PlatformDetector.sourceFor("https://www.cucchiaio.it/ricetta/pasta-alla-norma/"))
        assertEquals(RecipeSource.RICETTE_BIMBY, PlatformDetector.sourceFor("https://www.ricetteperbimby.it/ricette/pasta/"))
        assertEquals(RecipeSource.ALL_RECIPES, PlatformDetector.sourceFor("https://www.allrecipes.com/recipe/123/pancakes/"))
        assertEquals(RecipeSource.BBC_GOOD_FOOD, PlatformDetector.sourceFor("https://www.bbcgoodfood.com/recipes/classic-scones"))
        assertEquals(RecipeSource.SERIOUS_EATS, PlatformDetector.sourceFor("https://www.seriouseats.com/easy-pan-pizza-recipe"))
        assertEquals(RecipeSource.THE_MEAL_DB, PlatformDetector.sourceFor("https://www.themealdb.com/meal/52772"))
        assertEquals(RecipeSource.YOUTUBE, PlatformDetector.sourceFor("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertEquals(RecipeSource.YOUTUBE, PlatformDetector.sourceFor("https://youtu.be/dQw4w9WgXcQ"))
        assertEquals(RecipeSource.TIKTOK, PlatformDetector.sourceFor("https://www.tiktok.com/@user/video/1234567890"))
        assertEquals(RecipeSource.TIKTOK, PlatformDetector.sourceFor("https://vm.tiktok.com/ZM1234567/"))
        assertEquals(RecipeSource.INSTAGRAM, PlatformDetector.sourceFor("https://www.instagram.com/reel/C123456/"))
        assertEquals(RecipeSource.INSTAGRAM, PlatformDetector.sourceFor("https://ig.me/C123456/"))
        assertEquals(RecipeSource.FACEBOOK, PlatformDetector.sourceFor("https://www.facebook.com/reel/123456/"))
        assertEquals(RecipeSource.FACEBOOK, PlatformDetector.sourceFor("https://fb.watch/123456/"))
        assertEquals(RecipeSource.GENERIC_WEB, PlatformDetector.sourceFor("https://unknownrecipeblog.com/pie"))
    }

    private class FakeImporter(
        override val platform: Platform,
        private val canned: RawImport,
    ) : RecipeImporter {
        override suspend fun fetch(rawUrl: String): RawImport = canned
    }

    @Test
    fun `import succeeds when detected source is enabled`() = runTest {
        val fakeBlogImport = RawImport(
            platform = Platform.BLOG.key,
            url = "https://www.cookist.it/tiramisu/",
            author = "Cookist",
            content = ImportContent.RawText("Delicious tiramisu"),
        )
        val registry = RecipeImporterRegistry(
            importers = listOf(FakeImporter(Platform.BLOG, fakeBlogImport)),
            enabledSourcesProvider = { setOf(RecipeSource.COOKIST) },
        )

        val result = registry.import("https://www.cookist.it/tiramisu/")
        assertEquals("Cookist", result.author)
    }

    @Test
    fun `import throws ImportException when detected source and generic fallback are disabled`() = runTest {
        val fakeBlogImport = RawImport(
            platform = Platform.BLOG.key,
            url = "https://www.cookist.it/tiramisu/",
            author = "Cookist",
            content = ImportContent.RawText("Delicious tiramisu"),
        )
        val registry = RecipeImporterRegistry(
            importers = listOf(FakeImporter(Platform.BLOG, fakeBlogImport)),
            enabledSourcesProvider = { setOf(RecipeSource.GIALLO_ZAFFERANO) },
        )

        val exception = assertThrows(ImportException::class.java) {
            kotlinx.coroutines.runBlocking {
                registry.import("https://www.cookist.it/tiramisu/")
            }
        }
        assertEquals("This recipe source is disabled in Settings", exception.message)
        assertFalse(exception.retryable)
    }

    @Test
    fun `import succeeds when specific source is disabled but generic fallback is enabled`() = runTest {
        val fakeBlogImport = RawImport(
            platform = Platform.BLOG.key,
            url = "https://www.cookist.it/tiramisu/",
            author = "Cookist",
            content = ImportContent.RawText("Delicious tiramisu"),
        )
        val registry = RecipeImporterRegistry(
            importers = listOf(FakeImporter(Platform.BLOG, fakeBlogImport)),
            enabledSourcesProvider = { setOf(RecipeSource.GENERIC_WEB) },
        )

        val result = registry.import("https://www.cookist.it/tiramisu/")
        assertEquals("Cookist", result.author)
    }

    @Test
    fun `import throws ImportException when generic web source is disabled`() = runTest {
        val fakeBlogImport = RawImport(
            platform = Platform.BLOG.key,
            url = "https://unknownrecipeblog.com/pie",
            author = "Unknown Blog",
            content = ImportContent.RawText("Pie recipe"),
        )
        val registry = RecipeImporterRegistry(
            importers = listOf(FakeImporter(Platform.BLOG, fakeBlogImport)),
            enabledSourcesProvider = { setOf(RecipeSource.YOUTUBE) },
        )

        val exception = assertThrows(ImportException::class.java) {
            kotlinx.coroutines.runBlocking {
                registry.import("https://unknownrecipeblog.com/pie")
            }
        }
        assertEquals("This recipe source is disabled in Settings", exception.message)
        assertFalse(exception.retryable)
    }
}

package com.delizioso.app.data.import

/** Picks the right [RecipeImporter] for a pasted link. */
class RecipeImporterRegistry(
    importers: List<RecipeImporter>,
    private val enabledSourcesProvider: () -> Set<RecipeSource> = { RecipeSource.values().toSet() },
) {

    private val byPlatform: Map<Platform, RecipeImporter> = importers.associateBy { it.platform }

    fun importerFor(rawUrl: String): RecipeImporter? =
        PlatformDetector.detect(rawUrl)?.let { byPlatform[it] }

    suspend fun import(rawUrl: String): RawImport {
        val source = PlatformDetector.sourceFor(rawUrl)
        val enabled = enabledSourcesProvider()
        if (source !in enabled && RecipeSource.GENERIC_WEB !in enabled) {
            throw ImportException("This recipe source is disabled in Settings", retryable = false)
        }
        val importer = importerFor(rawUrl)
            ?: throw ImportException("This link is not supported yet")
        return importer.fetch(rawUrl)
    }
}

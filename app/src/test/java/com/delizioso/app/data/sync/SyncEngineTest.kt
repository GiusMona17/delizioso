package com.delizioso.app.data.sync

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.delizioso.app.PlainTestApplication
import com.delizioso.app.data.local.AppDatabase
import com.delizioso.app.data.local.PlannedMealEntity
import com.delizioso.app.data.local.RecipeEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File

/** An in-memory snapsync with the same rules as the real one. */
class FakeSnapshotStore : SnapshotStore {
    var etag: String? = null
    var body: ByteArray? = null
    val blobs = mutableMapOf<String, ByteArray>()
    private var counter = 0

    override suspend fun head() = etag
    override suspend fun download() = (etag ?: throw SnapsyncException("404")) to body!!
    override suspend fun missing(hashes: Collection<String>) = hashes.filterNot { it in blobs }.toSet()
    override suspend fun uploadBlob(sha: String, file: File) { blobs[sha] = file.readBytes() }
    override suspend fun downloadBlob(sha: String, target: File) {
        target.parentFile?.mkdirs()
        target.writeBytes(blobs.getValue(sha))
    }
    override suspend fun push(body: ByteArray, baseEtag: String?): PushResult {
        if (baseEtag != etag) return PushResult.Stale
        val referenced = JSONObject(body.decodeToString()).getJSONArray("blobs").toStringList()
        if (referenced.any { it !in blobs }) return PushResult.MissingBlobs
        this.body = body
        etag = "\"${++counter}\""
        return PushResult.Created(etag!!)
    }
}

@RunWith(AndroidJUnit4::class)
@Config(application = PlainTestApplication::class)
class SyncEngineTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val pi = FakeSnapshotStore()

    private inner class Device(name: String, token: String = "token") {
        val filesDir: File = tmp.newFolder(name)
        val workDir = File(filesDir, "snapsync")
        val db: AppDatabase = newDeliziosoDb()
        val appPrefs = newPrefs(File(tmp.root, "$name-app.preferences_pb"))
        val settings = SyncSettingsRepository(newPrefs(File(tmp.root, "$name-sync.preferences_pb")))
            .also { runBlocking { it.setServer("https://pi", token) } }
        val codec = TableSnapshot(db, DeliziosoSyncConfig, filesDir, FileHashCache(File(workDir, "hashes.json")), appPrefs)
        val engine = SyncEngine(codec, settings, { pi }, workDir)

        fun addRecipe(title: String, photo: String? = null): Long = runBlocking {
            val image = photo?.let {
                File(filesDir, "recipe_images/${title.lowercase()}.jpg").apply { parentFile!!.mkdirs(); writeText(it) }.absolutePath
            }
            db.recipeDao().insertRecipe(RecipeEntity(title = title, imageUri = image))
        }

        fun planDinner(recipeId: Long, epochDay: Long) = runBlocking {
            db.recipeDao().insertMeal(PlannedMealEntity(recipeId = recipeId, dateEpochDay = epochDay, slot = "DINNER"))
        }

        fun deleteRecipe(title: String) =
            db.openHelper.writableDatabase.execSQL("DELETE FROM recipes WHERE title = ?", arrayOf(title))

        fun titles(): List<String> = query("SELECT title FROM recipes ORDER BY title")
        fun plannedTitles(): List<String> =
            query("SELECT r.title FROM planned_meals p JOIN recipes r ON r.id = p.recipeId ORDER BY p.dateEpochDay")
        fun imageOf(title: String): String? = query("SELECT imageUri FROM recipes WHERE title = '$title'")
            .firstOrNull()?.let { File(it).readText() }

        private fun query(sql: String): List<String> = db.openHelper.readableDatabase.query(sql).use { c ->
            buildList { while (c.moveToNext()) if (!c.isNull(0)) add(c.getString(0)) }
        }

        fun sync() = runBlocking { engine.sync() }
    }

    @Test
    fun `a fresh device gets recipes, photos, the weekly plan and the grocery list`() {
        val a = Device("a")
        val carbonara = a.addRecipe("Carbonara", photo = "carbonara pixels")
        a.planDinner(carbonara, epochDay = 20_000)
        runBlocking { a.appPrefs.edit { it[stringSetPreferencesKey("grocery_checked")] = setOf("guanciale") } }
        val b = Device("b")

        assertEquals(SyncOutcome.Pushed, a.sync())
        assertEquals(SyncOutcome.Pulled, b.sync())

        assertEquals(listOf("Carbonara"), b.titles())
        assertEquals("carbonara pixels", b.imageOf("Carbonara"))
        assertEquals("the planned meal still points at its recipe", listOf("Carbonara"), b.plannedTitles())
        assertEquals(setOf("guanciale"), runBlocking { b.appPrefs.data.first() }[stringSetPreferencesKey("grocery_checked")])
        assertEquals(SyncOutcome.UpToDate, a.sync())
        assertEquals(SyncOutcome.UpToDate, b.sync())
    }

    @Test
    fun `edits and deletions reach the other device`() {
        val a = Device("a").apply { addRecipe("Carbonara", photo = "carbonara pixels") }
        a.sync()
        val b = Device("b").also { it.sync() }

        b.deleteRecipe("Carbonara")
        b.addRecipe("Amatriciana")
        assertEquals(SyncOutcome.Pushed, b.sync())
        assertEquals(SyncOutcome.Pulled, a.sync())

        assertEquals(listOf("Amatriciana"), a.titles())
        assertFalse(File(a.filesDir, "recipe_images/carbonara.jpg").exists())
    }

    @Test
    fun `both edited offline - conflict, then use the Pi's version`() {
        val a = Device("a").apply { addRecipe("Carbonara") }
        a.sync()
        val b = Device("b").also { it.sync() }
        a.addRecipe("From A"); a.sync()
        b.addRecipe("From B")

        assertEquals(SyncOutcome.Conflict, b.sync())
        assertEquals("waits for the user", SyncOutcome.Conflict, b.sync())
        assertEquals(SyncOutcome.Pulled, runBlocking { b.engine.resolveUsePi() })
        assertEquals(listOf("Carbonara", "From A"), b.titles())
        assertTrue(File(b.workDir, "pre-restore.zip").exists())
    }

    @Test
    fun `both edited offline - conflict, then keep this device`() {
        val a = Device("a").apply { addRecipe("Carbonara") }
        a.sync()
        val b = Device("b").also { it.sync() }
        a.addRecipe("From A"); a.sync()
        b.addRecipe("From B")
        b.sync()

        assertEquals(SyncOutcome.Pushed, runBlocking { b.engine.resolveKeepLocal() })
        assertEquals(SyncOutcome.Pulled, a.sync())
        assertEquals(listOf("Carbonara", "From B"), a.titles())
    }

    @Test
    fun `a device with its own never-synced library gets a conflict, not an overwrite`() {
        Device("a").apply { addRecipe("Carbonara"); sync() }
        val c = Device("c").apply { addRecipe("Mine") }
        assertEquals(SyncOutcome.Conflict, c.sync())
        assertEquals(listOf("Mine"), c.titles())
    }

    @Test
    fun `a snapshot from a newer app version is refused`() {
        pi.etag = "\"9-x\""
        pi.body = """{"format":1,"schema":999,"blobs":[],"data":{"tables":{},"files":{},"prefs":{}}}""".toByteArray()
        assertEquals(SyncOutcome.UpdateRequired, Device("b").sync())
    }

    @Test
    fun `manual archive export and import move all data`() {
        val a = Device("a").apply { addRecipe("Carbonara", photo = "carbonara pixels") }
        val zip = ByteArrayOutputStream().also { runBlocking { a.engine.exportArchive(it) } }.toByteArray()
        val d = Device("d").apply { addRecipe("Old") }

        assertEquals(SyncOutcome.Pulled, runBlocking { d.engine.importArchive(zip.inputStream()) })
        assertEquals(listOf("Carbonara"), d.titles())
        assertEquals("carbonara pixels", d.imageOf("Carbonara"))
    }

    @Test
    fun `a fresh device finds out the Pi has nothing yet`() {
        assertEquals(SyncOutcome.NothingOnPi, Device("fresh").sync())
    }

    @Test
    fun `without a token nothing happens`() {
        assertEquals(SyncOutcome.NotConfigured, Device("x", token = "").sync())
    }
}

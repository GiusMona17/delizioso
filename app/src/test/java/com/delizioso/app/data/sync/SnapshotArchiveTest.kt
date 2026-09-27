package com.delizioso.app.data.sync

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.delizioso.app.PlainTestApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
@Config(application = PlainTestApplication::class)
class SnapshotArchiveTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `write then read returns the same envelope and verified blobs`() {
        val filesDir = tmp.newFolder("files")
        val db = newDeliziosoDb()
        seedEveryColumn(db, DeliziosoSyncConfig, filesDir, "a")
        val snapshot = TableSnapshot(
            db,
            DeliziosoSyncConfig,
            filesDir,
            FileHashCache(tmp.newFile()),
            newPrefs(tmp.newFile("prefs.preferences_pb").also { it.delete() }),
        ).export()

        val zip = ByteArrayOutputStream().also { SnapshotArchive.write(snapshot, it) }.toByteArray()
        val (envelope, staged) = SnapshotArchive.read(ByteArrayInputStream(zip), tmp.newFolder("staging"))

        assertEquals(snapshot.envelope.toString(), envelope.toString())
        assertEquals(snapshot.filesBySha.keys, staged.keys)
        staged.forEach { (sha, file) -> assertEquals(sha, sha256Of(file)) }
    }

    @Test
    fun `a corrupted blob is rejected`() {
        val sha = sha256Of("real".toByteArray())
        val zip = ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use { z ->
                z.putNextEntry(ZipEntry("snapshot.json")); z.write("{}".toByteArray()); z.closeEntry()
                z.putNextEntry(ZipEntry("blobs/$sha")); z.write("tampered".toByteArray()); z.closeEntry()
            }
        }.toByteArray()
        assertThrows(IllegalArgumentException::class.java) {
            SnapshotArchive.read(ByteArrayInputStream(zip), tmp.newFolder("staging"))
        }
    }

    @Test
    fun `a zip without snapshot json is rejected`() {
        val zip = ByteArrayOutputStream().also { ZipOutputStream(it).use { z -> z.putNextEntry(ZipEntry("x")); z.closeEntry() } }
        assertThrows(IllegalArgumentException::class.java) {
            SnapshotArchive.read(ByteArrayInputStream(zip.toByteArray()), File(tmp.root, "staging"))
        }
    }
}

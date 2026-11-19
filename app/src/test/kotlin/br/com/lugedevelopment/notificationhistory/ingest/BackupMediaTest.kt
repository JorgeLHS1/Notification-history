package br.com.lugedevelopment.notificationhistory.ingest

import androidx.test.core.app.ApplicationProvider
import br.com.lugedevelopment.notificationhistory.backup.InvalidBackupException
import br.com.lugedevelopment.notificationhistory.data.AttachmentType
import br.com.lugedevelopment.notificationhistory.data.HistoryDatabase
import br.com.lugedevelopment.notificationhistory.media.MediaFile
import br.com.lugedevelopment.notificationhistory.media.MediaSource
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Backup export/import round-trip and media recovery, through the Ingestor. */
@RunWith(RobolectricTestRunner::class)
class BackupMediaTest {
    private lateinit var source: HistoryDatabase
    private lateinit var target: HistoryDatabase
    private lateinit var a: Ingestor
    private lateinit var b: Ingestor

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        source = HistoryDatabase.inMemory(ctx)
        target = HistoryDatabase.inMemory(ctx)
        a = Ingestor(source)
        b = Ingestor(target)
    }

    @After
    fun tearDown() {
        source.close()
        target.close()
    }

    private fun post(ingestor: Ingestor, title: String, text: String, at: Long, key: String = "k$at", extras: Map<String, String> = emptyMap()) =
        ingestor.ingest(NotificationSnapshot("com.whatsapp", "WhatsApp", key, at, title = title, text = text, extras = extras))

    private fun export(ingestor: Ingestor) = ByteArrayOutputStream().also { ingestor.exportBackup(it, now = 1) }.toByteArray()

    private fun snapshot(ingestor: Ingestor) = ingestor.conversations().sortedBy { it.conversation.conversationKey }.map { s ->
        Triple(s.conversation.title, ingestor.messages(s.conversation.id).map { Triple(it.sender, it.text, it.pinned to it.deleted) },
            ingestor.messages(s.conversation.id).flatMap { m -> ingestor.attachments(m.id).map { it.type to it.value } })
    }

    @Test
    fun exportImportRoundTripRestoresHistory() {
        post(a, "Ana", "hello https://example.org", 1_000)
        post(a, "Ana", "second", 2_000)
        post(a, "Bob", "photo", 3_000, extras = mapOf(AttachmentExtras.PICTURE to "embedded"))
        val ana = a.conversations().first { it.conversation.title == "Ana" }.conversation
        val first = a.messages(ana.id).first()
        a.setPinned(first.id, true)
        a.setCaptureEnabled("com.example.off", false, "Off")

        val result = b.importBackup(ByteArrayInputStream(export(a)))

        assertEquals(3, result.messages)
        assertEquals(0, result.duplicates)
        assertEquals(snapshot(a), snapshot(b))
        assertEquals(a.apps(), b.apps())
        assertEquals(2, b.conversations().first { it.conversation.title == "Ana" }.conversation.unreadCount)
    }

    @Test
    fun importTwiceAndIntoOverlappingHistoryAddsNoDuplicates() {
        post(a, "Ana", "one", 1_000)
        post(a, "Ana", "two", 2_000)
        post(b, "Ana", "one", 1_000) // already present on the target device
        post(b, "Cid", "local only", 5_000)
        val bytes = export(a)

        val first = b.importBackup(ByteArrayInputStream(bytes))
        val second = b.importBackup(ByteArrayInputStream(bytes))

        assertEquals(1, first.messages)
        assertEquals(1, first.duplicates)
        assertEquals(0, second.messages)
        assertEquals(2, second.duplicates)
        val ana = b.conversations().first { it.conversation.title == "Ana" }.conversation
        assertEquals(listOf("one", "two"), b.messages(ana.id).map { it.text })
        assertEquals(2, b.conversations().size)
    }

    @Test(expected = InvalidBackupException::class)
    fun rejectsForeignZip() {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { it.putNextEntry(ZipEntry("history.json")); it.write("{\"format\":\"other\"}".toByteArray()) }
        b.importBackup(ByteArrayInputStream(out.toByteArray()))
    }

    @Test(expected = InvalidBackupException::class)
    fun rejectsNewerVersion() {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use {
            it.putNextEntry(ZipEntry("history.json"))
            it.write("{\"format\":\"notification-history-backup\",\"version\":99}".toByteArray())
        }
        b.importBackup(ByteArrayInputStream(out.toByteArray()))
    }

    private class FakeSource(val files: List<MediaFile>) : MediaSource {
        override fun list() = files
        override fun open(file: MediaFile): InputStream = ByteArrayInputStream(file.name.toByteArray())
    }

    @Test
    fun recoverMediaCopiesClosestFileOnceAndOnlyForPhotoMessages() {
        post(a, "Ana", "photo", 100_000, extras = mapOf(AttachmentExtras.PICTURE to "embedded"))
        post(a, "Ana", "plain text", 200_000)
        post(a, "Bob", "late photo", 1_000_000, extras = mapOf(AttachmentExtras.PICTURE to "embedded"))
        val files = listOf(
            MediaFile("1", "IMG-a.jpg", "image/jpeg", 101_000),
            MediaFile("2", "IMG-b.jpg", "image/jpeg", 140_000),
            MediaFile("3", "IMG-c.jpg", "image/jpeg", 199_000), // near only the text message
        )
        val saved = mutableListOf<Pair<Long, String>>()
        val save = { id: Long, f: MediaFile -> saved += id to f.name; "/private/media/${id}_${f.name}" }

        val r = a.recoverMedia(FakeSource(files), save)
        val again = a.recoverMedia(FakeSource(files), save)

        assertEquals(3, r.scanned)
        assertEquals(1, r.copied)
        assertEquals(0, again.copied)
        val photo = a.messages(a.conversations().first { it.conversation.title == "Ana" }.conversation.id).first()
        val media = a.attachments(photo.id).filter { it.type == AttachmentType.MEDIA_FILE }
        assertEquals(listOf("/private/media/${photo.id}_IMG-a.jpg"), media.map { it.value })
        assertTrue(saved.single().second == "IMG-a.jpg")
    }
}

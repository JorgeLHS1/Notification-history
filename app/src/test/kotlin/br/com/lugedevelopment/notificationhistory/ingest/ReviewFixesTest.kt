package br.com.lugedevelopment.notificationhistory.ingest

import androidx.test.core.app.ApplicationProvider
import br.com.lugedevelopment.notificationhistory.data.AttachmentType
import br.com.lugedevelopment.notificationhistory.data.HistoryDatabase
import br.com.lugedevelopment.notificationhistory.media.MediaFile
import br.com.lugedevelopment.notificationhistory.media.MediaSource
import br.com.lugedevelopment.notificationhistory.media.PrivateMediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.nio.file.Files

/** Regressions for the v0.4.0 code-review findings, all through the Ingestor. */
@RunWith(RobolectricTestRunner::class)
class ReviewFixesTest {
    private lateinit var db: HistoryDatabase
    private lateinit var ingestor: Ingestor
    private val deletedFiles = mutableListOf<String>()

    @Before
    fun setUp() {
        db = HistoryDatabase.inMemory(ApplicationProvider.getApplicationContext())
        ingestor = Ingestor(db, deleteMediaFile = { deletedFiles += it })
    }

    @After
    fun tearDown() = db.close()

    /** One notification key per chat, as messengers do; MessagingStyle entries carry their own time. */
    private fun chat(vararg messages: SnapshotMessage, key: String = "0|chat.app|1|contact|1") = NotificationSnapshot(
        "chat.app", "Chat", key, messages.last().timestamp, title = "Ana", text = messages.last().text,
        messages = messages.toList(),
    )

    private fun plain(text: String, at: Long, key: String = "k1", extras: Map<String, String> = emptyMap()) =
        NotificationSnapshot("chat.app", "Chat", key, at, title = "Ana", text = text, extras = extras)

    private fun messages() = ingestor.messages(ingestor.conversations().single().conversation.id)

    // --- Dedup (finding 1) ---

    @Test
    fun sameTextTwiceUnderOneChatKeyIsKept() {
        ingestor.ingest(chat(SnapshotMessage("Ana", "ok", 1_000)))
        val result = ingestor.ingest(chat(SnapshotMessage("Ana", "ok", 1_000), SnapshotMessage("Ana", "ok", 5_000)))

        assertEquals(IngestResult.Stored(newMessages = 1, duplicates = 1), result)
        assertEquals(listOf(1_000L, 5_000L), messages().map { it.postedAt })
    }

    @Test
    fun plainRepostUnderSameKeyIsStillDeduplicated() {
        ingestor.ingest(plain("ok", 1_000))
        assertEquals(IngestResult.Stored(0, 1), ingestor.ingest(plain("ok", 2_000)))
    }

    @Test
    fun backupImportKeepsRepeatedMessagesOfOneChatKey() {
        ingestor.ingest(chat(SnapshotMessage("Ana", "ok", 1_000), SnapshotMessage("Ana", "ok", 5_000)))
        val bytes = ByteArrayOutputStream().also { ingestor.exportBackup(it, now = 1) }.toByteArray()
        val other = HistoryDatabase.inMemory(ApplicationProvider.getApplicationContext())
        try {
            val target = Ingestor(other)
            assertEquals(2, target.importBackup(ByteArrayInputStream(bytes)).messages)
            assertEquals(0, target.importBackup(ByteArrayInputStream(bytes)).messages)
        } finally {
            other.close()
        }
    }

    // --- Deletion fallback (finding 3) ---

    @Test
    fun keyFallbackDoesNotGuessBetweenSeveralMessages() {
        ingestor.ingest(plain("first", 1_000))
        ingestor.ingest(plain("second", 2_000)) // same key, new text: plain update
        val result = ingestor.ingest(plain("This message was deleted", 3_000))

        assertEquals(0, (result as IngestResult.Stored).markedDeleted)
        assertEquals(listOf(false, false), messages().map { it.deleted })
    }

    @Test
    fun keyFallbackMarksTheOnlyCandidate() {
        ingestor.ingest(plain("secret", 1_000))
        val result = ingestor.ingest(plain("This message was deleted", 3_000))

        assertEquals(1, (result as IngestResult.Stored).markedDeleted)
        assertTrue(messages().single().deleted)
    }

    @Test
    fun timestampMatchWinsOverNewerMessageWithSameKey() {
        ingestor.ingest(chat(SnapshotMessage("Ana", "a", 1_000), SnapshotMessage("Ana", "b", 2_000)))
        ingestor.ingest(chat(SnapshotMessage("Ana", "This message was deleted", 1_000), SnapshotMessage("Ana", "b", 2_000)))

        assertEquals(listOf(true, false), messages().map { it.deleted })
    }

    // --- Placeholder without original (finding 4) ---

    @Test
    fun placeholderForUnknownChatCreatesNoConversation() {
        ingestor.ingest(chat(SnapshotMessage("Ana", "This message was deleted", 1_000)))
        assertTrue(ingestor.conversations().isEmpty())
    }

    // --- Media cleanup (finding 5) ---

    private class FakeSource(val files: List<MediaFile>) : MediaSource {
        override fun list() = files
        override fun open(file: MediaFile): InputStream = ByteArrayInputStream(ByteArray(0))
    }

    private fun photoWithMedia(at: Long, key: String): Long {
        ingestor.ingest(plain("photo $at", at, key, mapOf(AttachmentExtras.PICTURE to "embedded")))
        ingestor.recoverMedia(FakeSource(listOf(MediaFile("f$at", "IMG-$at.jpg", "image/jpeg", at))), save = { id, f ->
            "/media/${id}_${f.name}"
        })
        return messages().single { it.postedAt == at }.id
    }

    @Test
    fun deletingMessagesRemovesTheirCopiedMedia() {
        val keep = photoWithMedia(1_000, "a")
        val drop = photoWithMedia(500_000, "b")

        assertEquals(1, ingestor.deleteMessages(listOf(drop)))

        assertEquals(listOf("/media/${drop}_IMG-500000.jpg"), deletedFiles)
        assertEquals(1, ingestor.attachments(keep).count { it.type == AttachmentType.MEDIA_FILE })
    }

    @Test
    fun retentionRemovesCopiedMediaOfExpiredMessagesOnly() {
        val day = 24L * 60 * 60 * 1000
        val old = photoWithMedia(1_000, "a")
        val pinned = photoWithMedia(500_000, "b")
        ingestor.setPinned(pinned, true)
        photoWithMedia(100 * day, "c")

        ingestor.applyRetention(RetentionPolicy(generalDays = 30, deletedDays = null), now = 100 * day)

        assertEquals(listOf("/media/${old}_IMG-1000.jpg"), deletedFiles)
    }

    @Test
    fun privateStoreDeletesOnlyInsideItsRoot() {
        val dir = Files.createTempDirectory("nh-media").toFile()
        try {
            val root = File(dir, "media")
            val store = PrivateMediaStore(root)
            val copy = store.save(7, MediaFile("1", "IMG 1.jpg", "image/jpeg", 0), ByteArrayInputStream(byteArrayOf(1)))
            val outside = File(dir, "history.db").apply { writeText("db") }

            assertFalse(store.delete(outside.path))
            assertFalse(store.delete(File(root, "../history.db").path))
            assertTrue(outside.exists())
            assertTrue(store.delete(copy.path))
            assertFalse(copy.exists())
        } finally {
            dir.deleteRecursively()
        }
    }

    // --- Conversation list and change notifications (finding 6) ---

    @Test
    fun conversationsCarryTheirOwnLastMessage() {
        ingestor.ingest(plain("a1", 1_000, "ka"))
        ingestor.ingest(plain("a2", 3_000, "ka2"))
        ingestor.ingest(NotificationSnapshot("chat.app", "Chat", "kb", 2_000, title = "Bob", text = "b1"))

        val last = ingestor.conversations().associate { it.conversation.title to it.lastMessage?.text }
        assertEquals(mapOf("Ana" to "a2", "Bob" to "b1"), last)
    }

    @Test
    fun changesEmitsInitiallyAndAfterAWrite() = runBlocking {
        val events = Channel<Unit>(Channel.UNLIMITED)
        val job = launch(Dispatchers.IO) { ingestor.changes().collect { events.send(Unit) } }
        try {
            withTimeout(5_000) { events.receive() } // initial load
            // The observer is registered asynchronously: keep writing until one change is seen.
            withTimeout(10_000) {
                var at = 1_000L
                while (true) {
                    ingestor.ingest(plain("m$at", at, "k$at"))
                    at++
                    if (events.tryReceive().isSuccess) break
                    kotlinx.coroutines.delay(100)
                }
            }
        } finally {
            job.cancel()
        }
    }
}

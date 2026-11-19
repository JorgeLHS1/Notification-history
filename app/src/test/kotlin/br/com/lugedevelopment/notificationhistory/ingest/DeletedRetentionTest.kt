package br.com.lugedevelopment.notificationhistory.ingest

import androidx.test.core.app.ApplicationProvider
import br.com.lugedevelopment.notificationhistory.data.HistoryDatabase
import br.com.lugedevelopment.notificationhistory.ingest.parser.WhatsAppParser
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** WhatsApp parser, deleted detection and retention, all through the Ingestor. */
@RunWith(RobolectricTestRunner::class)
class DeletedRetentionTest {
    private lateinit var db: HistoryDatabase
    private lateinit var ingestor: Ingestor

    @Before
    fun setUp() {
        db = HistoryDatabase.inMemory(ApplicationProvider.getApplicationContext())
        ingestor = Ingestor(db)
    }

    @After
    fun tearDown() = db.close()

    /** Shape of a WhatsApp 1:1 MessagingStyle post (names/text are synthetic). */
    private fun direct(
        messages: List<SnapshotMessage>,
        pkg: String = WhatsAppParser.PACKAGE,
        key: String = "0|$pkg|1|contact@s|10123",
    ) = NotificationSnapshot(
        pkg, "WhatsApp", key, messages.last().timestamp, title = "Contact A", text = messages.last().text,
        conversationTitle = null, isGroupConversation = false, messages = messages,
    )

    /** Shape of a WhatsApp group MessagingStyle post with a counter suffix. */
    private fun group(messages: List<SnapshotMessage>, count: Int = messages.size) = NotificationSnapshot(
        WhatsAppParser.PACKAGE, "WhatsApp", "0|com.whatsapp|1|group@g|10123", messages.last().timestamp,
        title = "Group X ($count messages)", text = messages.last().text,
        conversationTitle = "Group X ($count messages)", isGroupConversation = true, messages = messages,
    )

    private fun onlyConversationMessages() = ingestor.messages(ingestor.conversations().single().conversation.id)

    // --- Parser ---

    @Test
    fun groupCounterSuffixDoesNotSplitConversation() {
        ingestor.ingest(group(listOf(SnapshotMessage("Member 1", "hello", 1_000))))
        ingestor.ingest(
            group(listOf(SnapshotMessage("Member 1", "hello", 1_000), SnapshotMessage("Member 2", "hey", 2_000))),
        )

        val conv = ingestor.conversations().single().conversation
        assertEquals("Group X", conv.title)
        assertTrue(conv.isGroup)
        assertEquals(listOf("Member 1", "Member 2"), onlyConversationMessages().map { it.sender })
    }

    @Test
    fun appLevelSummaryIsIgnoredAndPlainGroupTitleIsSplit() {
        val summary = NotificationSnapshot(
            WhatsAppParser.PACKAGE, "WhatsApp", "s", 1_000, title = "WhatsApp", text = "5 messages from 3 chats",
        )
        assertEquals(IngestResult.Ignored, ingestor.ingest(summary))

        ingestor.ingest(
            NotificationSnapshot(
                WhatsAppParser.PACKAGE_BUSINESS, "WhatsApp Business", "p", 2_000,
                title = "Group Y (2 mensagens): Member 3", text = "ok",
            ),
        )
        val conv = ingestor.conversations().single().conversation
        assertEquals("Group Y", conv.title)
        assertEquals("Member 3", onlyConversationMessages().single().sender)
    }

    // --- Deleted detection ---

    @Test
    fun englishPlaceholderMarksOriginalDeletedAndKeepsText() {
        ingestor.ingest(direct(listOf(SnapshotMessage(null, "secret plan", 1_000))))
        val result = ingestor.ingest(direct(listOf(SnapshotMessage(null, "This message was deleted", 1_000))))

        assertEquals(1, (result as IngestResult.Stored).markedDeleted)
        val msg = onlyConversationMessages().single()
        assertEquals("secret plan", msg.text)
        assertTrue(msg.deleted)
        assertEquals(1_000L, msg.deletedAt)
    }

    @Test
    fun portuguesePlaceholderWithIconInBusinessApp() {
        val pkg = WhatsAppParser.PACKAGE_BUSINESS
        ingestor.ingest(
            direct(listOf(SnapshotMessage(null, "first", 1_000), SnapshotMessage(null, "second", 2_000)), pkg = pkg),
        )
        ingestor.ingest(
            direct(
                listOf(SnapshotMessage(null, "first", 1_000), SnapshotMessage(null, "🚫 Esta mensagem foi apagada", 2_000)),
                pkg = pkg,
            ),
        )

        val msgs = onlyConversationMessages()
        assertEquals(listOf("first", "second"), msgs.map { it.text })
        assertEquals(listOf(false, true), msgs.map { it.deleted })
    }

    @Test
    fun placeholderMatchesOnlySameSenderInGroup() {
        ingestor.ingest(
            group(listOf(SnapshotMessage("Member 1", "from one", 1_000), SnapshotMessage("Member 2", "from two", 1_000))),
        )
        ingestor.ingest(group(listOf(SnapshotMessage("Member 2", "Mensagem apagada", 1_000))))

        val bySender = onlyConversationMessages().associateBy { it.sender }
        assertFalse(bySender.getValue("Member 1").deleted)
        assertTrue(bySender.getValue("Member 2").deleted)
        assertEquals("from two", bySender.getValue("Member 2").text)
    }

    @Test
    fun placeholderWithoutCapturedOriginalIsNotStored() {
        val result = ingestor.ingest(direct(listOf(SnapshotMessage(null, "This message was deleted", 1_000))))

        assertEquals(IngestResult.Stored(0, 0, 0), result)
        assertTrue(onlyConversationMessages().isEmpty())
    }

    @Test
    fun removedEventDoesNotMarkDeleted() {
        ingestor.ingest(direct(listOf(SnapshotMessage(null, "hi", 1_000))))
        val removed = direct(listOf(SnapshotMessage(null, "hi", 1_000))).copy(event = NotificationSnapshot.Event.REMOVED)

        assertEquals(IngestResult.Ignored, ingestor.ingest(removed))
        assertFalse(onlyConversationMessages().single().deleted)
    }

    // --- Retention ---

    private val day = 24L * 60 * 60 * 1000

    @Test
    fun retentionUsesSeparateWindowsAndKeepsPinned() {
        val now = 400 * day
        ingestor.ingest(
            direct(
                listOf(
                    SnapshotMessage(null, "old", now - 100 * day),
                    SnapshotMessage(null, "old pinned", now - 100 * day + 1),
                    SnapshotMessage(null, "old deleted", now - 100 * day + 2),
                    SnapshotMessage(null, "recent", now - 10 * day),
                ),
            ),
        )
        val byText = onlyConversationMessages().associateBy { it.text }
        ingestor.setPinned(byText.getValue("old pinned").id, true)
        ingestor.ingest(direct(listOf(SnapshotMessage(null, "This message was deleted", now - 100 * day + 2))))

        val result = ingestor.applyRetention(RetentionPolicy(generalDays = 90, deletedDays = 365), now)

        assertEquals(RetentionResult(1, 0, 0), result)
        assertEquals(setOf("old pinned", "old deleted", "recent"), onlyConversationMessages().map { it.text }.toSet())

        // Deleted window counts from deletion time (the placeholder's post time here).
        val later = ingestor.applyRetention(RetentionPolicy(generalDays = 90, deletedDays = 30), now + 80 * day)
        assertEquals(1, later.expiredDeletedMessages)
        assertEquals(setOf("old pinned", "recent"), onlyConversationMessages().map { it.text }.toSet())
    }

    @Test
    fun retentionRemovesEmptiedConversationsAndNullKeepsForever() {
        val now = 400 * day
        ingestor.ingest(direct(listOf(SnapshotMessage(null, "ancient", 0))))

        assertEquals(RetentionResult(0, 0, 0), ingestor.applyRetention(RetentionPolicy(null, null), now))
        assertEquals(1, ingestor.conversations().size)

        assertEquals(RetentionResult(1, 0, 1), ingestor.applyRetention(RetentionPolicy(), now))
        assertTrue(ingestor.conversations().isEmpty())
        assertNull(ingestor.apps().firstOrNull { !it.captureEnabled })
    }
}

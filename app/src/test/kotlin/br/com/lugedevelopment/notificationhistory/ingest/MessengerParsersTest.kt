package br.com.lugedevelopment.notificationhistory.ingest

import androidx.test.core.app.ApplicationProvider
import br.com.lugedevelopment.notificationhistory.data.AttachmentType
import br.com.lugedevelopment.notificationhistory.data.HistoryDatabase
import br.com.lugedevelopment.notificationhistory.ingest.parser.InstagramParser
import br.com.lugedevelopment.notificationhistory.ingest.parser.MessengerParser
import br.com.lugedevelopment.notificationhistory.ingest.parser.TelegramParser
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Telegram, Instagram and Messenger parsers plus attachment metadata, via the Ingestor. */
@RunWith(RobolectricTestRunner::class)
class MessengerParsersTest {
    private lateinit var db: HistoryDatabase
    private lateinit var ingestor: Ingestor

    @Before
    fun setUp() {
        db = HistoryDatabase.inMemory(ApplicationProvider.getApplicationContext())
        ingestor = Ingestor(db)
    }

    @After
    fun tearDown() = db.close()

    private fun plain(
        pkg: String, label: String, title: String?, text: String?, key: String = "k1", at: Long = 1_000,
        extras: Map<String, String> = emptyMap(),
    ) = NotificationSnapshot(pkg, label, key, at, title = title, text = text, extras = extras)

    private fun styled(
        pkg: String, label: String, title: String, group: String?, msgs: List<SnapshotMessage>, key: String = "k1",
    ) = NotificationSnapshot(
        pkg, label, key, msgs.last().timestamp, title = title, text = msgs.last().text,
        conversationTitle = group, isGroupConversation = group != null, messages = msgs,
    )

    private fun messagesOf(pkg: String) =
        ingestor.conversations(setOf(pkg)).flatMap { ingestor.messages(it.conversation.id) }

    @Test
    fun telegramDirectAndGroupWithAtTitle() {
        ingestor.ingest(plain(TelegramParser.PACKAGE, "Telegram", "Contact A", "hello", "k1", 1_000))
        ingestor.ingest(plain(TelegramParser.PACKAGE_WEB, "Telegram", "Person B @ Team Chat (3 messages)", "lunch?", "k3", 3_000))
        val convs = ingestor.conversations().map { it.conversation }
        val group = convs.single { it.isGroup }
        assertEquals("Team Chat", group.title)
        val m = ingestor.messages(group.id).single()
        assertEquals("Person B", m.sender)
        assertEquals("lunch?", m.text)
        assertTrue(convs.any { it.title == "Contact A" && !it.isGroup })
    }

    @Test
    fun telegramMessagingStyleGroupSplitsSenders() {
        ingestor.ingest(
            styled(
                TelegramParser.PACKAGE, "Telegram", "Team Chat", "Team Chat",
                listOf(SnapshotMessage("Person B", "one", 1_000), SnapshotMessage("Person C", "two", 2_000)),
            ),
        )
        val c = ingestor.conversations().single().conversation
        assertTrue(c.isGroup)
        assertEquals(listOf("Person B", "Person C"), ingestor.messages(c.id).map { it.sender })
    }

    @Test
    fun summariesAndEmptyPostsAreIgnored() {
        assertEquals(IngestResult.Ignored, ingestor.ingest(plain(TelegramParser.PACKAGE, "Telegram", "Telegram", "5 new messages")))
        assertEquals(IngestResult.Ignored, ingestor.ingest(plain(InstagramParser.PACKAGE, "Instagram", "user_a", "3 new messages")))
        assertEquals(IngestResult.Ignored, ingestor.ingest(plain(MessengerParser.PACKAGE, "Messenger", "Messenger", "Chat heads active")))
        assertEquals(IngestResult.Ignored, ingestor.ingest(plain(MessengerParser.PACKAGE, "Messenger", "Contact A", null)))
    }

    @Test
    fun instagramDirectMessageKeepsThreadStable() {
        ingestor.ingest(plain(InstagramParser.PACKAGE, "Instagram", "user_a", "hi", "k1", 1_000))
        ingestor.ingest(plain(InstagramParser.PACKAGE, "Instagram", "user_a (2 new messages)", "again", "k2", 2_000))
        val c = ingestor.conversations().single().conversation
        assertEquals("user_a", c.title)
        assertEquals(listOf("hi", "again"), ingestor.messages(c.id).map { it.text })
    }

    @Test
    fun messengerGroupViaMessagingStyleAndDedup() {
        val snap = styled(
            MessengerParser.PACKAGE, "Messenger", "Friends", "Friends",
            listOf(SnapshotMessage("Person B", "hey", 1_000), SnapshotMessage("Person C", "yo", 2_000)),
        )
        assertEquals(IngestResult.Stored(2, 0), ingestor.ingest(snap))
        assertEquals(IngestResult.Stored(0, 2), ingestor.ingest(snap))
        assertEquals(2, messagesOf(MessengerParser.PACKAGE).size)
    }

    @Test
    fun linksImagesIconsAndActionsBecomeAttachments() {
        ingestor.ingest(
            plain(
                MessengerParser.PACKAGE, "Messenger", "Contact A",
                "look https://example.org/a?x=1, and (http://example.com/b).",
                extras = mapOf(
                    AttachmentExtras.PICTURE to "content://example/pic/1",
                    AttachmentExtras.LARGE_ICON to "embedded",
                    "${AttachmentExtras.ACTION_PREFIX}1" to "Mark as read",
                    "${AttachmentExtras.ACTION_PREFIX}0" to "Reply",
                    "bigText" to "not an attachment",
                ),
            ),
        )
        val m = messagesOf(MessengerParser.PACKAGE).single()
        val att = ingestor.attachments(m.id)
        assertEquals(
            listOf("https://example.org/a?x=1", "http://example.com/b"),
            att.filter { it.type == AttachmentType.LINK }.map { it.value },
        )
        assertEquals("content://example/pic/1", att.single { it.type == AttachmentType.IMAGE }.value)
        assertEquals("embedded", att.single { it.type == AttachmentType.LARGE_ICON }.value)
        assertEquals(listOf("Reply", "Mark as read"), att.filter { it.type == AttachmentType.ACTION }.map { it.value })
        assertEquals(att.groupBy { it.messageId }, ingestor.attachments(listOf(m.id)))
    }

    @Test
    fun duplicatesDoNotAddAttachmentsAndRetentionCascades() {
        val s = plain(TelegramParser.PACKAGE, "Telegram", "Contact A", "see https://example.org", "k1", 1_000)
        ingestor.ingest(s)
        ingestor.ingest(s)
        val m = messagesOf(TelegramParser.PACKAGE).single()
        assertEquals(1, ingestor.attachments(m.id).size)
        ingestor.applyRetention(RetentionPolicy(generalDays = 1, deletedDays = null), now = 10L * 24 * 60 * 60 * 1000)
        assertTrue(ingestor.attachments(m.id).isEmpty())
    }

    @Test
    fun linkExtractorTrimsPunctuation() {
        assertEquals(listOf("https://example.org/x"), LinkExtractor.find("go to https://example.org/x.").map { it.url })
        assertTrue(LinkExtractor.find("no link here, https:// alone").isEmpty())
    }
}

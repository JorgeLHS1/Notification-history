package br.com.lugedevelopment.notificationhistory.ingest

import androidx.test.core.app.ApplicationProvider
import br.com.lugedevelopment.notificationhistory.data.HistoryDatabase
import br.com.lugedevelopment.notificationhistory.ingest.parser.ParsedMessage
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class IngestorTest {
    private lateinit var db: HistoryDatabase
    private lateinit var ingestor: Ingestor

    @Before
    fun setUp() {
        db = HistoryDatabase.inMemory(ApplicationProvider.getApplicationContext())
        ingestor = Ingestor(db)
    }

    @After
    fun tearDown() = db.close()

    private fun plain(pkg: String = CHAT, key: String = "k1", title: String? = "Ana", text: String? = "hi", at: Long = 1_000) =
        NotificationSnapshot(pkg, "Label $pkg", key, at, title = title, text = text)

    private fun messaging(
        key: String,
        messages: List<SnapshotMessage>,
        title: String? = "Ana",
        conversationTitle: String? = null,
        group: Boolean = false,
    ) = NotificationSnapshot(
        CHAT, "Chat", key, messages.last().timestamp, title = title, text = messages.last().text,
        conversationTitle = conversationTitle, isGroupConversation = group, messages = messages,
    )

    // --- Capture ---

    @Test
    fun capturesPlainNotificationFromAnyApp() {
        ingestor.ingest(plain(pkg = "com.example.mail", title = "Bob", text = "Invoice attached"))

        val conv = ingestor.conversations().single()
        assertEquals("Bob", conv.conversation.title)
        assertEquals("Invoice attached", conv.lastMessage?.text)
        val msg = ingestor.messages(conv.conversation.id).single()
        assertEquals("Bob", msg.sender)
        assertFalse(msg.deleted || msg.read || msg.pinned)
        assertEquals("com.example.mail", ingestor.apps().single().packageName)
    }

    @Test
    fun notificationWithoutTitleGoesToAppLabelConversation() {
        ingestor.ingest(plain(pkg = "com.example.sys", title = null, text = "Update ready"))
        assertEquals("Label com.example.sys", ingestor.conversations().single().conversation.title)
    }

    @Test
    fun groupSummariesAndEmptyNotificationsAreNotStored() {
        ingestor.ingest(plain().copy(isGroupSummary = true))
        ingestor.ingest(plain(key = "k2", text = " "))
        assertTrue(ingestor.conversations().isEmpty())
    }

    @Test
    fun extrasAreKeptAsMessageMetadata() {
        ingestor.ingest(plain().copy(extras = mapOf("link" to "https://example.com")))
        val msg = ingestor.messages(ingestor.conversations().single().conversation.id).single()
        assertTrue(msg.metadata!!.contains("https://example.com"))
    }

    // --- Grouping ---

    @Test
    fun oneToOneMessagesFromSameContactShareConversation() {
        ingestor.ingest(messaging("k1", listOf(SnapshotMessage("Ana", "oi", 1))))
        ingestor.ingest(messaging("k1", listOf(SnapshotMessage("Ana", "oi", 1), SnapshotMessage("Ana", "tudo bem?", 2))))
        ingestor.ingest(messaging("k2", listOf(SnapshotMessage("Bia", "hey", 3)), title = "Bia"))

        val convs = ingestor.conversations()
        assertEquals(listOf("Bia", "Ana"), convs.map { it.conversation.title })
        val ana = convs.last()
        assertFalse(ana.conversation.isGroup)
        assertEquals(listOf("oi", "tudo bem?"), ingestor.messages(ana.conversation.id).map { it.text })
        assertEquals(2, ana.conversation.unreadCount)
    }

    @Test
    fun groupMessagesAreAttributedToSenders() {
        ingestor.ingest(
            messaging(
                "g1",
                listOf(SnapshotMessage("Ana", "bom dia", 10), SnapshotMessage("Caio", "opa", 11)),
                title = "Caio",
                conversationTitle = "Family",
                group = true,
            ),
        )

        val conv = ingestor.conversations().single().conversation
        assertEquals("Family", conv.title)
        assertTrue(conv.isGroup)
        assertEquals(
            listOf("Ana" to "bom dia", "Caio" to "opa"),
            ingestor.messages(conv.id).map { it.sender to it.text },
        )
    }

    @Test
    fun sameContactNameInDifferentAppsIsSeparateConversations() {
        ingestor.ingest(plain(pkg = "com.example.a"))
        ingestor.ingest(plain(pkg = "com.example.b"))
        assertEquals(2, ingestor.conversations().size)
    }

    // --- Dedup ---

    @Test
    fun repostingSameNotificationDoesNotDuplicate() {
        ingestor.ingest(plain(at = 1_000))
        val result = ingestor.ingest(plain(at = 2_000)) // same key, re-posted later
        assertEquals(IngestResult.Stored(newMessages = 0, duplicates = 1), result)
        assertEquals(1, ingestor.messages(ingestor.conversations().single().conversation.id).size)
    }

    @Test
    fun updatedMessagingNotificationOnlyAddsNewMessages() {
        val first = SnapshotMessage("Ana", "a", 1)
        ingestor.ingest(messaging("k1", listOf(first)))
        ingestor.ingest(messaging("k9", listOf(first, SnapshotMessage("Ana", "b", 2))))
        ingestor.ingest(messaging("k9", listOf(first, SnapshotMessage("Ana", "b", 2))))

        val conv = ingestor.conversations().single().conversation
        assertEquals(listOf("a", "b"), ingestor.messages(conv.id).map { it.text })
        assertEquals(2, conv.unreadCount)
    }

    @Test
    fun sameTextSentTwiceAsDifferentNotificationsIsKept() {
        ingestor.ingest(plain(key = "k1", text = "ok", at = 1))
        ingestor.ingest(plain(key = "k2", text = "ok", at = 2))
        assertEquals(2, ingestor.messages(ingestor.conversations().single().conversation.id).size)
    }

    // --- App filter ---

    @Test
    fun appWithCaptureDisabledIsNotStored() {
        ingestor.setCaptureEnabled("com.example.noisy", false)
        assertEquals(IngestResult.Skipped, ingestor.ingest(plain(pkg = "com.example.noisy")))
        assertTrue(ingestor.conversations().isEmpty())

        ingestor.setCaptureEnabled("com.example.noisy", true)
        ingestor.ingest(plain(pkg = "com.example.noisy", key = "k2"))
        assertEquals(1, ingestor.conversations().size)
    }

    @Test
    fun newAppsAreCapturedByDefaultAndListed() {
        ingestor.ingest(plain(pkg = "com.example.a"))
        assertTrue(ingestor.apps().single().captureEnabled)
    }

    @Test
    fun conversationsCanBeFilteredByApp() {
        ingestor.ingest(plain(pkg = "com.example.a", title = "A"))
        ingestor.ingest(plain(pkg = "com.example.b", title = "B"))
        ingestor.ingest(plain(pkg = "com.example.c", title = "C"))

        val filtered = ingestor.conversations(setOf("com.example.a", "com.example.c"))
        assertEquals(setOf("A", "C"), filtered.map { it.conversation.title }.toSet())
        assertEquals(3, ingestor.conversations(emptySet()).size)
    }

    // --- Parser registry ---

    @Test
    fun registeredParserIsUsedForItsPackage() {
        ingestor.parsers.register("com.example.custom") { s ->
            listOf(ParsedMessage("fixed", "Custom", false, "bot", s.text!!.uppercase(), s.postedAt))
        }
        ingestor.ingest(plain(pkg = "com.example.custom", text = "hello"))
        val conv = ingestor.conversations().single().conversation
        assertEquals("Custom", conv.title)
        assertEquals("HELLO", ingestor.messages(conv.id).single().text)
    }

    private companion object {
        const val CHAT = "com.example.chat"
    }
}

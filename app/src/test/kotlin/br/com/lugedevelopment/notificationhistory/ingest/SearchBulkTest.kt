package br.com.lugedevelopment.notificationhistory.ingest

import androidx.test.core.app.ApplicationProvider
import br.com.lugedevelopment.notificationhistory.data.HistoryDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Search, deleted-only filter and bulk operations, all through the Ingestor. */
@RunWith(RobolectricTestRunner::class)
class SearchBulkTest {
    private lateinit var db: HistoryDatabase
    private lateinit var ingestor: Ingestor

    @Before
    fun setUp() {
        db = HistoryDatabase.inMemory(ApplicationProvider.getApplicationContext())
        ingestor = Ingestor(db)
    }

    @After
    fun tearDown() = db.close()

    private fun post(pkg: String, title: String, text: String, at: Long, key: String = "$pkg-$title-$at") =
        ingestor.ingest(NotificationSnapshot(pkg, "Label $pkg", key, at, title = title, text = text))

    private fun allMessages() = ingestor.conversations().flatMap { ingestor.messages(it.conversation.id) }

    private fun idsOf(vararg texts: String) =
        allMessages().filter { it.text in texts }.map { it.id }

    // --- Search ---

    @Test
    fun searchMatchesPartialWordsCaseInsensitively() {
        post(CHAT, "Ana", "Meeting tomorrow at noon", 1_000)
        post(CHAT, "Bob", "Lunch plans", 2_000)

        assertEquals(listOf("Meeting tomorrow at noon"), ingestor.search("MEET").map { it.message.text })
        assertEquals(listOf("Meeting tomorrow at noon"), ingestor.search("tomor noo").map { it.message.text })
        assertTrue(ingestor.search("zzz").isEmpty())
    }

    @Test
    fun searchMatchesSenderAndReturnsConversation() {
        post(CHAT, "Ana", "ping", 1_000)

        val hit = ingestor.search("an").single()
        assertEquals("Ana", hit.conversation.title)
        assertEquals(hit.message.conversationId, hit.conversation.id)
    }

    @Test
    fun blankOrSymbolOnlyQueryReturnsNothingAndDoesNotCrash() {
        post(CHAT, "Ana", "hello", 1_000)

        assertTrue(ingestor.search("").isEmpty())
        assertTrue(ingestor.search("   ").isEmpty())
        assertTrue(ingestor.search("\"* ( -").isEmpty())
        assertEquals(1, ingestor.search("\"hel(\"").size)
    }

    @Test
    fun searchResultsAreNewestFirstAndFilterByApp() {
        post(CHAT, "Ana", "invoice one", 1_000)
        post("com.example.mail", "Bob", "invoice two", 2_000)
        post(CHAT, "Ana", "invoice three", 3_000)

        assertEquals(
            listOf("invoice three", "invoice two", "invoice one"),
            ingestor.search("invoice").map { it.message.text },
        )
        assertEquals(
            listOf("invoice two"),
            ingestor.search("invoice", packages = setOf("com.example.mail")).map { it.message.text },
        )
    }

    @Test
    fun searchRespectsLimit() {
        repeat(5) { post(CHAT, "Ana", "note $it", 1_000L + it) }

        assertEquals(2, ingestor.search("note", limit = 2).size)
    }

    @Test
    fun deletedMessagesRemainSearchable() {
        post(CHAT, "Ana", "secret plan", 1_000, key = "k1")
        post(CHAT, "Ana", "This message was deleted", 1_000, key = "k1")

        assertEquals(listOf("secret plan"), ingestor.search("secret").map { it.message.text })
    }

    @Test
    fun deletedOnlyFiltersSearchAndListsAllWhenQueryBlank() {
        post(CHAT, "Ana", "secret plan", 1_000, key = "k1")
        post(CHAT, "Ana", "This message was deleted", 1_000, key = "k1")
        post(CHAT, "Ana", "secret recipe", 2_000, key = "k2")
        post(CHAT, "Bob", "other", 3_000, key = "k3")
        post(CHAT, "Bob", "This message was deleted", 3_000, key = "k3")

        assertEquals(
            listOf("secret plan", "secret recipe"),
            ingestor.search("secret").map { it.message.text }.sorted(),
        )
        assertEquals(listOf("secret plan"), ingestor.search("secret", deletedOnly = true).map { it.message.text })
        assertEquals(
            listOf("other", "secret plan"),
            ingestor.search("", deletedOnly = true).map { it.message.text }.sorted(),
        )
        assertTrue(ingestor.search("", deletedOnly = true, packages = setOf("com.example.none")).isEmpty())
    }

    @Test
    fun deletingMessageRemovesItFromSearch() {
        post(CHAT, "Ana", "findable text", 1_000)
        post(CHAT, "Ana", "other text", 2_000)

        ingestor.deleteMessages(idsOf("findable text"))

        assertTrue(ingestor.search("findable").isEmpty())
        assertEquals(1, ingestor.search("other").size)
    }

    @Test
    fun highlightRangesCoverMatchedPrefixes() {
        assertEquals(listOf(0..2, 17..19), SearchText.highlightRanges("Meeting at noon, meet", "mee"))
        assertTrue(SearchText.highlightRanges("anything", " ").isEmpty())
    }

    // --- Bulk read/unread ---

    @Test
    fun markReadAndUnreadUpdateMessagesAndConversationCounter() {
        post(CHAT, "Ana", "one", 1_000)
        post(CHAT, "Ana", "two", 2_000)
        post(CHAT, "Bob", "three", 3_000)
        val conversationAna = ingestor.conversations().first { it.conversation.title == "Ana" }.conversation.id
        assertEquals(2, ingestor.conversations().first { it.conversation.title == "Ana" }.conversation.unreadCount)

        ingestor.setRead(idsOf("one", "two"), read = true)

        assertTrue(ingestor.messages(conversationAna).all { it.read })
        assertEquals(0, ingestor.conversations().first { it.conversation.title == "Ana" }.conversation.unreadCount)
        assertEquals(1, ingestor.conversations().first { it.conversation.title == "Bob" }.conversation.unreadCount)

        ingestor.setRead(idsOf("two"), read = false)

        assertEquals(listOf(true, false), ingestor.messages(conversationAna).map { it.read })
        assertEquals(1, ingestor.conversations().first { it.conversation.title == "Ana" }.conversation.unreadCount)
    }

    @Test
    fun newMessageAfterMarkReadCountsAsUnreadAgain() {
        post(CHAT, "Ana", "one", 1_000)
        ingestor.setRead(idsOf("one"), read = true)
        post(CHAT, "Ana", "two", 2_000)

        assertEquals(1, ingestor.conversations().single().conversation.unreadCount)
    }

    // --- Bulk pin ---

    @Test
    fun bulkPinProtectsFromRetentionAndUnpinReleases() {
        post(CHAT, "Ana", "keep me", 1_000)
        post(CHAT, "Ana", "drop me", 1_001)
        val now = 1_000L + 200L * 24 * 60 * 60 * 1000

        ingestor.setPinned(idsOf("keep me"), pinned = true)
        ingestor.applyRetention(RetentionPolicy(generalDays = 90, deletedDays = null), now)
        assertEquals(listOf("keep me"), allMessages().map { it.text })

        ingestor.setPinned(allMessages().map { it.id }, pinned = false)
        assertFalse(allMessages().single().pinned)
        ingestor.applyRetention(RetentionPolicy(generalDays = 90, deletedDays = null), now)
        assertTrue(allMessages().isEmpty())
    }

    // --- Bulk delete ---

    @Test
    fun deleteRemovesMessagesRecountsUnreadAndDropsEmptyConversations() {
        post(CHAT, "Ana", "one", 1_000)
        post(CHAT, "Ana", "two", 2_000)
        post(CHAT, "Bob", "three", 3_000)
        ingestor.setRead(idsOf("one"), read = true)

        val removed = ingestor.deleteMessages(idsOf("two", "three"))

        assertEquals(2, removed)
        val remaining = ingestor.conversations().single()
        assertEquals("Ana", remaining.conversation.title)
        assertEquals(0, remaining.conversation.unreadCount)
        assertEquals(listOf("one"), ingestor.messages(remaining.conversation.id).map { it.text })
    }

    @Test
    fun deleteRemovesPinnedMessagesWhenUserPicksThem() {
        post(CHAT, "Ana", "pinned one", 1_000)
        ingestor.setPinned(idsOf("pinned one"), pinned = true)

        assertEquals(1, ingestor.deleteMessages(idsOf("pinned one")))
        assertTrue(ingestor.conversations().isEmpty())
    }

    @Test
    fun emptyAndUnknownIdsAreNoOps() {
        post(CHAT, "Ana", "one", 1_000)

        ingestor.setRead(emptyList(), true)
        ingestor.setPinned(emptyList(), true)
        assertEquals(0, ingestor.deleteMessages(emptyList()))
        assertEquals(0, ingestor.deleteMessages(listOf(9_999L)))
        assertEquals(1, allMessages().size)
    }

    @Test
    fun bulkOperationsHandleMoreIdsThanOneSqlChunk() {
        repeat(1_200) { post(CHAT, "Ana", "bulk $it", 1_000L + it, key = "k$it") }
        val ids = allMessages().map { it.id }
        assertEquals(1_200, ids.size)

        ingestor.setRead(ids, true)
        assertEquals(0, ingestor.conversations().single().conversation.unreadCount)
        assertEquals(1_200, ingestor.deleteMessages(ids))
        assertTrue(ingestor.conversations().isEmpty())
    }

    private companion object {
        const val CHAT = "com.example.chat"
    }
}

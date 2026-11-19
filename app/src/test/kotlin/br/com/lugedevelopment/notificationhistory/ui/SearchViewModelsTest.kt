package br.com.lugedevelopment.notificationhistory.ui

import androidx.test.core.app.ApplicationProvider
import br.com.lugedevelopment.notificationhistory.data.HistoryDatabase
import br.com.lugedevelopment.notificationhistory.ingest.Ingestor
import br.com.lugedevelopment.notificationhistory.ingest.NotificationSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SearchViewModelsTest {
    private lateinit var db: HistoryDatabase
    private lateinit var ingestor: Ingestor

    @Before
    fun setUp() {
        db = HistoryDatabase.inMemory(ApplicationProvider.getApplicationContext())
        ingestor = Ingestor(db)
    }

    @After
    fun tearDown() = db.close()

    private fun post(title: String, text: String, at: Long, key: String = "$title-$at") =
        ingestor.ingest(NotificationSnapshot("com.example.chat", "Chat", key, at, title = title, text = text))

    @Test
    fun searchStateListsHitsAndClosesCleanly() = runBlocking {
        post("Ana", "dinner tonight", 1_000)
        post("Bob", "lunch", 2_000)
        val vm = HomeViewModel(ingestor, Dispatchers.Unconfined)

        vm.openSearch()
        vm.setQuery("dinn")
        vm.load()
        assertEquals(listOf("dinner tonight"), vm.state.value.hits.map { it.message.text })

        vm.closeSearch()
        vm.load()
        assertTrue(vm.state.value.hits.isEmpty())
        assertEquals(false, vm.state.value.searchOpen)
    }

    @Test
    fun deletedOnlyListsDeletedMessages() = runBlocking {
        post("Ana", "secret", 1_000, key = "k1")
        post("Ana", "This message was deleted", 1_000, key = "k1")
        post("Ana", "visible", 2_000, key = "k2")
        val vm = HomeViewModel(ingestor, Dispatchers.Unconfined)

        vm.openSearch()
        vm.setDeletedOnly(true)
        vm.load()

        assertEquals(listOf("secret"), vm.state.value.hits.map { it.message.text })
    }

    @Test
    fun selectionActionsApplyToSearchResults() = runBlocking {
        post("Ana", "alpha one", 1_000)
        post("Ana", "alpha two", 2_000)
        val vm = HomeViewModel(ingestor, Dispatchers.Unconfined)
        vm.openSearch()
        vm.setQuery("alpha")
        vm.load()
        val ids = vm.state.value.hits.map { it.message.id }

        vm.toggleSelected(ids[0])
        vm.toggleSelected(ids[1])
        vm.toggleSelected(ids[1])
        assertEquals(setOf(ids[0]), vm.state.value.selection)

        vm.markSelectedRead(true)
        assertTrue(vm.state.value.selection.isEmpty())
        assertEquals(listOf(true, false), vm.state.value.hits.map { it.message.read })

        vm.toggleSelected(ids[0])
        vm.deleteSelected()
        assertEquals(listOf("alpha one"), vm.state.value.hits.map { it.message.text })
    }

    @Test
    fun conversationSelectionPinsAndDeletes() = runBlocking {
        post("Ana", "one", 1_000)
        post("Ana", "two", 2_000)
        val id = ingestor.conversations().single().conversation.id
        val vm = ConversationViewModel(ingestor, id, Dispatchers.Unconfined)
        vm.load()
        val first = vm.state.value.messages.first().id

        vm.toggleSelected(first)
        vm.pinSelected(true)
        assertTrue(vm.state.value.messages.first().pinned)
        assertTrue(vm.state.value.selection.isEmpty())

        vm.toggleSelected(first)
        vm.deleteSelected()
        assertEquals(listOf("two"), vm.state.value.messages.map { it.text })
    }
}

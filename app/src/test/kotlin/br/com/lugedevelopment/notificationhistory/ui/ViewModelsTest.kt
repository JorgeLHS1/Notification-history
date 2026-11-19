package br.com.lugedevelopment.notificationhistory.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import br.com.lugedevelopment.notificationhistory.data.HistoryDatabase
import br.com.lugedevelopment.notificationhistory.ingest.Ingestor
import br.com.lugedevelopment.notificationhistory.ingest.NotificationSnapshot
import br.com.lugedevelopment.notificationhistory.settings.RetentionSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ViewModelsTest {
    private lateinit var db: HistoryDatabase
    private lateinit var ingestor: Ingestor
    private lateinit var retention: RetentionSettings

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = HistoryDatabase.inMemory(context)
        ingestor = Ingestor(db)
        retention = RetentionSettings.from(context)
        context.getSharedPreferences(RetentionSettings.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
    }

    @After
    fun tearDown() = db.close()

    private fun post(pkg: String, title: String, text: String, at: Long, key: String = "$pkg-$at") =
        ingestor.ingest(NotificationSnapshot(pkg, "App $pkg", key, at, title = title, text = text))

    @Test
    fun homeListsConversationsAndFiltersByApp() = runBlocking {
        post("a.chat", "Ana", "hi", 1_000)
        post("b.mail", "Bob", "yo", 2_000)
        val vm = HomeViewModel(ingestor, Dispatchers.Unconfined)

        vm.load()
        assertTrue(vm.state.value.loaded)
        assertEquals(listOf("Bob", "Ana"), vm.state.value.conversations.map { it.conversation.title })
        assertEquals(setOf("a.chat", "b.mail"), vm.state.value.apps.map { it.packageName }.toSet())

        vm.toggleApp("a.chat")
        vm.load()
        assertEquals(listOf("Ana"), vm.state.value.conversations.map { it.conversation.title })

        vm.clearFilter()
        vm.load()
        assertEquals(2, vm.state.value.conversations.size)
    }

    @Test
    fun conversationLoadsMessagesOldestFirst() = runBlocking {
        post("a.chat", "Ana", "first", 1_000)
        post("a.chat", "Ana", "second", 2_000)
        val id = ingestor.conversations().single().conversation.id
        val vm = ConversationViewModel(ingestor, id, Dispatchers.Unconfined)

        vm.load()
        assertEquals(listOf("first", "second"), vm.state.value.messages.map { it.text })
    }

    @Test
    fun settingsToggleCaptureStopsIngestion() = runBlocking {
        post("a.chat", "Ana", "hi", 1_000)
        val vm = SettingsViewModel(ingestor, retention, Dispatchers.Unconfined)
        vm.load()
        val app = vm.state.value.apps.single()
        assertTrue(app.captureEnabled)

        vm.setCapture(app, false)
        assertFalse(vm.state.value.apps.single().captureEnabled)
        post("a.chat", "Ana", "ignored", 3_000)
        assertEquals(1, ingestor.messages(ingestor.conversations().single().conversation.id).size)
    }

    @Test
    fun retentionValuesDefaultAndPersist() {
        val vm = SettingsViewModel(ingestor, retention, Dispatchers.Unconfined)
        assertEquals(90, vm.state.value.generalDays)
        assertEquals(RetentionSettings.KEEP_FOREVER, vm.state.value.deletedDays)

        vm.setGeneralDays(30)
        vm.setDeletedDays(365)
        assertEquals(30, vm.state.value.generalDays)

        val reread = RetentionSettings.from(ApplicationProvider.getApplicationContext())
        assertEquals(30, reread.generalDays)
        assertEquals(365, reread.deletedDays)
    }
}

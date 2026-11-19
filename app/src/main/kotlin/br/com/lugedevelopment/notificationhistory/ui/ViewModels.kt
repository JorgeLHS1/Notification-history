package br.com.lugedevelopment.notificationhistory.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import br.com.lugedevelopment.notificationhistory.data.AppEntity
import br.com.lugedevelopment.notificationhistory.data.MessageEntity
import br.com.lugedevelopment.notificationhistory.ingest.ConversationSummary
import br.com.lugedevelopment.notificationhistory.ingest.Ingestor
import br.com.lugedevelopment.notificationhistory.settings.RetentionSettings
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ViewModels read exclusively through the [Ingestor]. Its calls are blocking, so every
 * access runs on [io]. `load()` is suspend so tests can drive it deterministically.
 */

data class HomeState(
    val apps: List<AppEntity> = emptyList(),
    /** Packages selected in the filter chips; empty means all apps. */
    val selected: Set<String> = emptySet(),
    val conversations: List<ConversationSummary> = emptyList(),
    val loaded: Boolean = false,
)

class HomeViewModel(
    private val ingestor: Ingestor,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state.asStateFlow()

    suspend fun load() {
        val selected = _state.value.selected
        val (apps, conversations) = withContext(io) {
            ingestor.apps() to ingestor.conversations(selected)
        }
        _state.update { it.copy(apps = apps, conversations = conversations, loaded = true) }
    }

    fun refresh() {
        viewModelScope.launch { load() }
    }

    fun toggleApp(packageName: String) {
        _state.update {
            val next = if (packageName in it.selected) it.selected - packageName else it.selected + packageName
            it.copy(selected = next)
        }
        refresh()
    }

    fun clearFilter() {
        _state.update { it.copy(selected = emptySet()) }
        refresh()
    }
}

data class ConversationState(
    val messages: List<MessageEntity> = emptyList(),
    val loaded: Boolean = false,
)

class ConversationViewModel(
    private val ingestor: Ingestor,
    private val conversationId: Long,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val _state = MutableStateFlow(ConversationState())
    val state: StateFlow<ConversationState> = _state.asStateFlow()

    suspend fun load() {
        val messages = withContext(io) { ingestor.messages(conversationId) }
        _state.value = ConversationState(messages, loaded = true)
    }

    fun refresh() {
        viewModelScope.launch { load() }
    }
}

data class SettingsState(
    val apps: List<AppEntity> = emptyList(),
    val generalDays: Int = RetentionSettings.DEFAULT_GENERAL_DAYS,
    val deletedDays: Int = RetentionSettings.DEFAULT_DELETED_DAYS,
)

class SettingsViewModel(
    private val ingestor: Ingestor,
    private val retention: RetentionSettings,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val _state = MutableStateFlow(
        SettingsState(generalDays = retention.generalDays, deletedDays = retention.deletedDays),
    )
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    suspend fun load() {
        val apps = withContext(io) { ingestor.apps() }
        _state.update { it.copy(apps = apps) }
    }

    fun refresh() {
        viewModelScope.launch { load() }
    }

    suspend fun setCapture(app: AppEntity, enabled: Boolean) {
        withContext(io) { ingestor.setCaptureEnabled(app.packageName, enabled, app.label) }
        load()
    }

    fun onCaptureToggled(app: AppEntity, enabled: Boolean) {
        viewModelScope.launch { setCapture(app, enabled) }
    }

    fun setGeneralDays(days: Int) {
        retention.generalDays = days
        _state.update { it.copy(generalDays = days) }
    }

    fun setDeletedDays(days: Int) {
        retention.deletedDays = days
        _state.update { it.copy(deletedDays = days) }
    }
}

/** Builds the ViewModels from an [Ingestor] handed in by whoever owns it (see TemporaryIngestorProvider). */
class UiViewModelFactory(
    private val ingestor: Ingestor,
    private val retention: RetentionSettings,
    private val conversationId: Long = 0,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when (modelClass) {
        HomeViewModel::class.java -> HomeViewModel(ingestor)
        ConversationViewModel::class.java -> ConversationViewModel(ingestor, conversationId)
        SettingsViewModel::class.java -> SettingsViewModel(ingestor, retention)
        else -> throw IllegalArgumentException("Unknown ViewModel ${modelClass.name}")
    } as T
}

package br.com.lugedevelopment.notificationhistory.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import br.com.lugedevelopment.notificationhistory.ingest.Ingestor
import br.com.lugedevelopment.notificationhistory.settings.RetentionSettings

/**
 * Runs [block] once and then after every committed change to the history (Room invalidation
 * via [Ingestor.changes]) while the screen is at least STARTED: no polling, no idle reloads.
 */
@Composable
private fun RefreshOnChange(ingestor: Ingestor, block: suspend () -> Unit) {
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(owner, ingestor) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            ingestor.changes().collect { block() }
        }
    }
}

/**
 * Root composable: list -> conversation / settings, using a tiny saveable back stack
 * (no navigation library needed for three destinations).
 * The [ingestor] comes from the caller so it can be shared with the listener (T3).
 */
@Composable
fun NotificationHistoryApp(ingestor: Ingestor) {
    val context = LocalContext.current
    val retention = RetentionSettings.from(context)

    var conversationId by rememberSaveable { mutableStateOf<Long?>(null) }
    var conversationTitle by rememberSaveable { mutableStateOf("") }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    // Set when a conversation is opened from a search result.
    var focusMessageId by rememberSaveable { mutableStateOf<Long?>(null) }
    var highlightQuery by rememberSaveable { mutableStateOf("") }

    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
        val openId = conversationId
        when {
            showSettings -> {
                BackHandler { showSettings = false }
                val vm: SettingsViewModel = viewModel(factory = UiViewModelFactory(ingestor, retention))
                RefreshOnChange(ingestor) { vm.load() }
                SettingsScreen(vm, onToggleCapture = vm::onCaptureToggled, onBack = { showSettings = false })
            }
            openId != null -> {
                BackHandler {
                    conversationId = null
                    focusMessageId = null
                    highlightQuery = ""
                }
                val vm: ConversationViewModel = viewModel(
                    key = "conversation-$openId",
                    factory = UiViewModelFactory(ingestor, retention, openId),
                )
                RefreshOnChange(ingestor) { vm.load() }
                ConversationScreen(
                    vm,
                    conversationTitle,
                    onBack = {
                        conversationId = null
                        focusMessageId = null
                        highlightQuery = ""
                    },
                    focusMessageId = focusMessageId,
                    query = highlightQuery,
                )
            }
            else -> {
                val vm: HomeViewModel = viewModel(factory = UiViewModelFactory(ingestor, retention))
                RefreshOnChange(ingestor) { vm.load() }
                HomeScreen(
                    vm,
                    onOpenConversation = { id, title ->
                        conversationTitle = title
                        conversationId = id
                    },
                    onOpenSettings = { showSettings = true },
                    onOpenMessage = { id, title, messageId, query ->
                        conversationTitle = title
                        focusMessageId = messageId
                        highlightQuery = query
                        conversationId = id
                    },
                )
            }
        }
    }
}

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
import kotlinx.coroutines.delay

private const val REFRESH_INTERVAL_MS = 2_000L

/** Runs [block] repeatedly while the screen is at least STARTED, so captured data shows up live. */
@Composable
private fun PollWhileVisible(block: suspend () -> Unit) {
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                block()
                delay(REFRESH_INTERVAL_MS)
            }
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

    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
        val openId = conversationId
        when {
            showSettings -> {
                BackHandler { showSettings = false }
                val vm: SettingsViewModel = viewModel(factory = UiViewModelFactory(ingestor, retention))
                PollWhileVisible { vm.load() }
                SettingsScreen(vm, onToggleCapture = vm::onCaptureToggled, onBack = { showSettings = false })
            }
            openId != null -> {
                BackHandler { conversationId = null }
                val vm: ConversationViewModel = viewModel(
                    key = "conversation-$openId",
                    factory = UiViewModelFactory(ingestor, retention, openId),
                )
                PollWhileVisible { vm.load() }
                ConversationScreen(vm, conversationTitle, onBack = { conversationId = null })
            }
            else -> {
                val vm: HomeViewModel = viewModel(factory = UiViewModelFactory(ingestor, retention))
                PollWhileVisible { vm.load() }
                HomeScreen(
                    vm,
                    onOpenConversation = { id, title ->
                        conversationTitle = title
                        conversationId = id
                    },
                    onOpenSettings = { showSettings = true },
                )
            }
        }
    }
}

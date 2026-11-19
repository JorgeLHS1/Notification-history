package br.com.lugedevelopment.notificationhistory.onboarding

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import br.com.lugedevelopment.notificationhistory.R
import br.com.lugedevelopment.notificationhistory.listener.ListenerStatus

/**
 * Wraps the app UI: shows onboarding when notification access is missing/revoked or the first-run
 * guide has not been finished, otherwise [content] with a status card on top while anything is pending.
 * Access and battery state are re-read on every ON_RESUME (the user returns from system settings).
 */
@Composable
fun OnboardingGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val settings = remember { OnboardingSettings.from(context) }
    var accessGranted by remember { mutableStateOf(ListenerStatus.isAccessGranted(context)) }
    var batteryIgnored by remember { mutableStateOf(isBatteryOptimizationIgnored(context)) }
    var completed by remember { mutableStateOf(settings.completed) }
    var guideRequested by rememberSaveable { mutableStateOf(false) }
    val connected by ListenerStatus.connected.collectAsState()

    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                accessGranted = ListenerStatus.isAccessGranted(context)
                batteryIgnored = isBatteryOptimizationIgnored(context)
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    val status = SetupStatus(accessGranted, connected, batteryIgnored)
    val step = nextStep(accessGranted, completed, guideRequested)
    if (step != OnboardingStep.DONE) {
        OnboardingScreen(
            step = step,
            revoked = isRevoked(accessGranted, completed),
            status = status,
            onFinish = {
                settings.completed = true
                completed = true
                guideRequested = false
            },
        )
        return
    }

    Column(Modifier.fillMaxSize()) {
        if (!status.allOk) {
            StatusCard(
                status,
                Modifier.statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                OutlinedButton(onClick = { guideRequested = true }) {
                    Text(stringResource(R.string.onb_open_guide))
                }
            }
        }
        val inner = if (status.allOk) Modifier else Modifier.consumeWindowInsets(WindowInsets.statusBars)
        Box(Modifier.weight(1f).then(inner)) { content() }
    }
}

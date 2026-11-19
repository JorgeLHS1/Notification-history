package br.com.lugedevelopment.notificationhistory.onboarding

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import br.com.lugedevelopment.notificationhistory.R
import br.com.lugedevelopment.notificationhistory.listener.ListenerStatus

/** Three-line indicator: access granted / listener connected / battery optimization ignored. */
@Composable
fun StatusCard(status: SetupStatus, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.onb_status_title), style = MaterialTheme.typography.titleMedium)
            StatusRow(stringResource(R.string.onb_status_access), status.accessGranted)
            StatusRow(stringResource(R.string.onb_status_connected), status.listenerConnected)
            StatusRow(stringResource(R.string.onb_status_battery), status.batteryOptimizationIgnored)
            action?.invoke()
        }
    }
}

@Composable
private fun StatusRow(label: String, ok: Boolean) {
    val mark = stringResource(if (ok) R.string.onb_status_ok else R.string.onb_status_pending)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(
            mark,
            style = MaterialTheme.typography.bodyMedium,
            color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        )
    }
}

private fun Context.launch(candidates: List<Intent>, fallback: Intent?) {
    val result = launchFirst(candidates, fallback) { startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    if (result == LaunchResult.FAILED) {
        Toast.makeText(this, R.string.onb_open_failed, Toast.LENGTH_LONG).show()
    }
}

@Composable
fun OnboardingScreen(
    step: OnboardingStep,
    revoked: Boolean,
    status: SetupStatus,
    onFinish: () -> Unit,
) {
    val context = LocalContext.current
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (step == OnboardingStep.ACCESS) {
            AccessStep(context, revoked)
        } else {
            GuideStep(context, status, onFinish)
        }
    }
}

@Composable
private fun AccessStep(context: Context, revoked: Boolean) {
    Text(
        stringResource(if (revoked) R.string.onb_revoked_title else R.string.onb_access_title),
        style = MaterialTheme.typography.headlineSmall,
    )
    Text(stringResource(if (revoked) R.string.onb_revoked_body else R.string.onb_access_body))
    Text(stringResource(R.string.onb_access_privacy), style = MaterialTheme.typography.bodyMedium)
    Button(
        onClick = { context.launch(listOf(ListenerStatus.accessSettingsIntent()), null) },
        modifier = Modifier.fillMaxWidth(),
    ) { Text(stringResource(R.string.onb_access_button)) }
    Text(stringResource(R.string.onb_restricted_hint), style = MaterialTheme.typography.bodySmall)
    OutlinedButton(
        onClick = { context.launch(emptyList(), SettingsIntents.appDetails(context.packageName)) },
        modifier = Modifier.fillMaxWidth(),
    ) { Text(stringResource(R.string.onb_app_info_button)) }
}

@Composable
private fun GuideStep(context: Context, status: SetupStatus, onFinish: () -> Unit) {
    val xiaomi = OemDetector.isXiaomiFamily()
    val label = stringResource(R.string.app_name)
    Text(stringResource(R.string.onb_guide_title), style = MaterialTheme.typography.headlineSmall)
    StatusCard(status)
    Text(stringResource(R.string.onb_guide_body))
    OutlinedButton(
        onClick = { context.launch(listOf(SettingsIntents.batteryOptimizationList()), SettingsIntents.appDetails(context.packageName)) },
        modifier = Modifier.fillMaxWidth(),
    ) { Text(stringResource(R.string.onb_battery_button)) }
    if (xiaomi) {
        Text(stringResource(R.string.onb_xiaomi_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.onb_xiaomi_body))
        OutlinedButton(
            onClick = { context.launch(SettingsIntents.xiaomiAutostart(), SettingsIntents.appDetails(context.packageName)) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.onb_xiaomi_autostart_button)) }
        OutlinedButton(
            onClick = {
                context.launch(
                    SettingsIntents.xiaomiBatterySaver(context.packageName, label),
                    SettingsIntents.appDetails(context.packageName),
                )
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.onb_xiaomi_battery_button)) }
        Text(stringResource(R.string.onb_xiaomi_lock_tip), style = MaterialTheme.typography.bodySmall)
    } else {
        Text(stringResource(R.string.onb_generic_body), style = MaterialTheme.typography.bodyMedium)
    }
    Spacer(Modifier.height(4.dp))
    Button(onClick = onFinish, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.onb_finish)) }
}

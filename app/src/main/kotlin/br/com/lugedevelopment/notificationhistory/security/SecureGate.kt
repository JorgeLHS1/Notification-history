package br.com.lugedevelopment.notificationhistory.security

import android.view.WindowManager
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import br.com.lugedevelopment.notificationhistory.R

private const val AUTHENTICATORS = BIOMETRIC_WEAK or DEVICE_CREDENTIAL

/** Blocks screenshots, screen recording and the recents thumbnail. Call before setContent. */
fun FragmentActivity.applySecureWindow() {
    window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
}

/**
 * Shows [content] only after biometric / device-credential authentication.
 * Prompts on first open and again on resume after [lock]'s timeout. Fails closed:
 * if the device has no screen lock/biometric enrolled, content stays hidden.
 * [lock] must outlive the activity (app-scoped, see NotificationHistoryApp.sessionLock):
 * a lock created in composition would be lost on rotation and prompt again.
 */
@Composable
fun SecureGate(
    lock: SessionLock,
    content: @Composable () -> Unit,
) {
    val activity = LocalContext.current as FragmentActivity
    val lifecycleOwner = LocalLifecycleOwner.current
    var unlocked by remember { mutableStateOf(lock.isUnlocked) }
    var error by remember { mutableStateOf<String?>(null) }
    var unavailable by remember { mutableStateOf(false) }
    var prompting by remember { mutableStateOf(false) }

    val title = stringResource(R.string.security_prompt_title)
    val subtitle = stringResource(R.string.security_prompt_subtitle)
    val noLockMsg = stringResource(R.string.security_no_lock)

    fun authenticate() {
        if (prompting) return
        val can = BiometricManager.from(activity).canAuthenticate(AUTHENTICATORS)
        if (can != BiometricManager.BIOMETRIC_SUCCESS) {
            unavailable = true
            error = noLockMsg
            return
        }
        unavailable = false
        prompting = true
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    prompting = false
                    error = null
                    lock.onAuthenticated()
                    unlocked = true
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    prompting = false
                    error = errString.toString()
                }
            },
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setAllowedAuthenticators(AUTHENTICATORS)
                .build(),
        )
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> lock.onBackgrounded()
                Lifecycle.Event.ON_START -> {
                    val needsAuth = lock.onForegrounded()
                    unlocked = !needsAuth
                    if (needsAuth) authenticate()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (unlocked) {
        content()
    } else {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.security_locked), style = MaterialTheme.typography.titleLarge)
            error?.let {
                Text(it, modifier = Modifier.padding(top = 12.dp), style = MaterialTheme.typography.bodyMedium)
            }
            if (!unavailable) {
                Button(onClick = { authenticate() }, modifier = Modifier.padding(top = 16.dp)) {
                    Text(stringResource(R.string.security_unlock))
                }
            }
        }
    }
}

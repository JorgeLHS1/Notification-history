package br.com.lugedevelopment.notificationhistory.listener

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Observable state of the notification listener, for the UI (onboarding, status banner).
 * Two independent facts: whether the user granted access, and whether the system
 * currently has the service bound.
 */
object ListenerStatus {
    private val _connected = MutableStateFlow(false)

    /** True between onListenerConnected and onListenerDisconnected (process-local). */
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    internal fun setConnected(value: Boolean) {
        _connected.value = value
    }

    /** True when the user enabled notification access for this app. Cheap; re-check on resume. */
    fun isAccessGranted(context: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    /** Intent that opens the system "Notification access" screen. */
    fun accessSettingsIntent(): Intent =
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

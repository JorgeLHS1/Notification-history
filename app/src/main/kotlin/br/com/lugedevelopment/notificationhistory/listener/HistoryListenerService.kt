package br.com.lugedevelopment.notificationhistory.listener

import android.content.ComponentName
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import br.com.lugedevelopment.notificationhistory.NotificationHistoryApp
import br.com.lugedevelopment.notificationhistory.ingest.NotificationSnapshot

/**
 * Thin bridge: StatusBarNotification -> NotificationSnapshot -> Ingestor (AGENTS.md rule 7).
 * No filtering or business rules here. Boot/restart is handled by the system, which
 * rebinds enabled listeners on its own.
 */
class HistoryListenerService : NotificationListenerService() {

    override fun onListenerConnected() {
        ListenerStatus.setConnected(true)
    }

    override fun onListenerDisconnected() {
        ListenerStatus.setConnected(false)
        // Ask the system to bind us again (no-op if access was revoked).
        runCatching { requestRebind(ComponentName(this, HistoryListenerService::class.java)) }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) =
        forward(sbn, NotificationSnapshot.Event.POSTED)

    override fun onNotificationRemoved(sbn: StatusBarNotification) =
        forward(sbn, NotificationSnapshot.Event.REMOVED)

    private fun forward(sbn: StatusBarNotification, event: NotificationSnapshot.Event) {
        val app = application as NotificationHistoryApp
        // Map on the binder thread (the sbn is only guaranteed valid here), ingest off it.
        val snapshot = runCatching {
            SnapshotMapper.map(sbn.packageName, labelFor(sbn.packageName), sbn.key, sbn.postTime, sbn.notification, event)
        }.getOrNull() ?: return
        app.ioExecutor.execute {
            runCatching { app.ingestor.ingest(snapshot) }
        }
    }

    private fun labelFor(pkg: String): String = runCatching {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)
}

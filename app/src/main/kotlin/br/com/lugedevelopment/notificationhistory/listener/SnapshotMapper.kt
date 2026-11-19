package br.com.lugedevelopment.notificationhistory.listener

import android.app.Notification
import androidx.core.app.NotificationCompat
import br.com.lugedevelopment.notificationhistory.ingest.AttachmentExtras
import br.com.lugedevelopment.notificationhistory.ingest.NotificationSnapshot
import br.com.lugedevelopment.notificationhistory.ingest.SnapshotMessage

/** Pure field copy from a platform [Notification] to a [NotificationSnapshot]. No rules here. */
object SnapshotMapper {
    fun map(
        packageName: String,
        appLabel: String,
        key: String,
        postedAt: Long,
        notification: Notification,
        event: NotificationSnapshot.Event,
    ): NotificationSnapshot {
        val extras = notification.extras
        val style = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification)
        val messages = style?.messages.orEmpty().mapNotNull { m ->
            val text = m.text?.toString() ?: return@mapNotNull null
            SnapshotMessage(m.person?.name?.toString(), text, m.timestamp)
        }
        val meta = buildMap {
            extras?.getCharSequence(Notification.EXTRA_BIG_TEXT)?.let { put("bigText", it.toString()) }
            extras?.getCharSequence(Notification.EXTRA_SUB_TEXT)?.let { put("subText", it.toString()) }
            // References only (no pixels): the URI when the app gives one, else a marker.
            extras?.getString("android.pictureUri") // EXTRA_PICTURE_URI needs API 31?.let { put(AttachmentExtras.PICTURE, it) }
                ?: extras?.takeIf { it.containsKey(Notification.EXTRA_PICTURE) }
                    ?.let { put(AttachmentExtras.PICTURE, "embedded") }
            if (extras?.containsKey(Notification.EXTRA_LARGE_ICON) == true) put(AttachmentExtras.LARGE_ICON, "embedded")
            notification.actions.orEmpty().forEachIndexed { i, a ->
                a.title?.toString()?.let { put("${AttachmentExtras.ACTION_PREFIX}$i", it) }
            }
        }
        return NotificationSnapshot(
            packageName = packageName,
            appLabel = appLabel,
            key = key,
            postedAt = postedAt,
            title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
            text = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
            conversationTitle = style?.conversationTitle?.toString(),
            isGroupConversation = style?.isGroupConversation ?: false,
            messages = messages,
            isGroupSummary = notification.flags and Notification.FLAG_GROUP_SUMMARY != 0,
            extras = meta,
            event = event,
        )
    }
}

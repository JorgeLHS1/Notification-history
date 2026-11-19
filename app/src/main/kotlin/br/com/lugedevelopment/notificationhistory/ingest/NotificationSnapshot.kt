package br.com.lugedevelopment.notificationhistory.ingest

/**
 * Platform-free copy of a posted or removed notification. The listener service builds it
 * from a StatusBarNotification; everything else in the app only sees this type.
 */
data class NotificationSnapshot(
    val packageName: String,
    val appLabel: String,
    /** StatusBarNotification key: stable across re-posts/updates of the same notification. */
    val key: String,
    val postedAt: Long,
    val title: String? = null,
    val text: String? = null,
    /** MessagingStyle conversation title (group name), when present. */
    val conversationTitle: String? = null,
    /** MessagingStyle isGroupConversation, when present. */
    val isGroupConversation: Boolean = false,
    /** MessagingStyle messages, oldest first. Empty for non-messaging notifications. */
    val messages: List<SnapshotMessage> = emptyList(),
    /** Group summary notifications carry no own content and are skipped. */
    val isGroupSummary: Boolean = false,
    /** Extra metadata (links, image URIs...) kept as plain strings. */
    val extras: Map<String, String> = emptyMap(),
    val event: Event = Event.POSTED,
) {
    enum class Event { POSTED, REMOVED }
}

data class SnapshotMessage(
    val sender: String?,
    val text: String,
    val timestamp: Long,
)

package br.com.lugedevelopment.notificationhistory.ingest.parser

import br.com.lugedevelopment.notificationhistory.ingest.NotificationSnapshot

/**
 * Fallback parser for any app.
 * - MessagingStyle snapshots: one message per entry; the conversation is the
 *   conversation title (groups) or the notification title (1:1).
 * - Plain snapshots: one message; the conversation is the title, or the app label.
 * - Group summaries and empty text produce nothing.
 */
class GenericParser : NotificationParser {
    override fun parse(snapshot: NotificationSnapshot): List<ParsedMessage> {
        if (snapshot.isGroupSummary) return emptyList()

        if (snapshot.messages.isNotEmpty()) {
            val isGroup = snapshot.isGroupConversation
            val name = (if (isGroup) snapshot.conversationTitle else null)
                ?.takeIf { it.isNotBlank() }
                ?: snapshot.title?.takeIf { it.isNotBlank() }
                ?: snapshot.conversationTitle?.takeIf { it.isNotBlank() }
                ?: snapshot.appLabel
            return snapshot.messages
                .filter { it.text.isNotBlank() }
                .map {
                    ParsedMessage(
                        conversationKey = name,
                        conversationTitle = name,
                        isGroup = isGroup,
                        sender = it.sender ?: if (isGroup) null else name,
                        text = it.text,
                        postedAt = it.timestamp,
                    )
                }
        }

        val text = snapshot.text?.takeIf { it.isNotBlank() } ?: return emptyList()
        val name = snapshot.title?.takeIf { it.isNotBlank() } ?: snapshot.appLabel
        return listOf(
            ParsedMessage(
                conversationKey = name,
                conversationTitle = name,
                isGroup = false,
                sender = snapshot.title,
                text = text,
                postedAt = snapshot.postedAt,
            ),
        )
    }
}

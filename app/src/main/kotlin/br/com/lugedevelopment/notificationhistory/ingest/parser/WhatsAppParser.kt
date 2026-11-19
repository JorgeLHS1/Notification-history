package br.com.lugedevelopment.notificationhistory.ingest.parser

import br.com.lugedevelopment.notificationhistory.ingest.NotificationSnapshot

/**
 * Parser for WhatsApp and WhatsApp Business.
 *
 * Builds on [GenericParser] with the shapes these apps post:
 * - Group conversation titles may carry a counter suffix ("Family (3 messages)",
 *   "Familia (3 mensagens)"); it is stripped so the conversation key stays stable.
 * - Plain (non-MessagingStyle) group posts use "Group: Sender" as title; split into
 *   group conversation + sender.
 * - App-level summaries ("5 messages from 3 chats", title = app label) carry no content
 *   and are dropped.
 */
class WhatsAppParser(private val generic: GenericParser = GenericParser()) : NotificationParser {

    override fun parse(snapshot: NotificationSnapshot): List<ParsedMessage> {
        if (snapshot.isGroupSummary) return emptyList()

        if (snapshot.messages.isEmpty()) {
            val title = snapshot.title?.trim()
            if (title.isNullOrEmpty() || title == snapshot.appLabel) return emptyList()
            val text = snapshot.text?.takeIf { it.isNotBlank() } ?: return emptyList()
            val colon = title.indexOf(": ")
            if (colon > 0) {
                val group = stripCounter(title.substring(0, colon))
                return listOf(
                    ParsedMessage(group, group, true, title.substring(colon + 2), text, snapshot.postedAt),
                )
            }
        }

        val cleaned = snapshot.copy(
            title = snapshot.title?.let(::stripCounter),
            conversationTitle = snapshot.conversationTitle?.let(::stripCounter),
        )
        return generic.parse(cleaned)
    }

    companion object {
        const val PACKAGE = "com.whatsapp"
        const val PACKAGE_BUSINESS = "com.whatsapp.w4b"

        private val COUNTER = Regex("""\s*\(\d+\s+(messages?|mensagens?|mensajes?)\)\s*$""", RegexOption.IGNORE_CASE)

        fun stripCounter(title: String): String = title.replace(COUNTER, "").trim()
    }
}

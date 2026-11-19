package br.com.lugedevelopment.notificationhistory.ingest.parser

import br.com.lugedevelopment.notificationhistory.ingest.NotificationSnapshot

/** One message extracted from a snapshot, ready to be grouped and stored. */
data class ParsedMessage(
    /** Identifies the conversation inside its app (contact or group name). */
    val conversationKey: String,
    val conversationTitle: String,
    val isGroup: Boolean,
    val sender: String?,
    val text: String,
    val postedAt: Long,
    /**
     * True when [postedAt] is the message's own timestamp (MessagingStyle entry): dedup then
     * relies on it alone, since chat apps reuse one notification key per conversation.
     * False when it is the snapshot post time (plain posts): dedup also falls back to the key.
     */
    val hasOwnTimestamp: Boolean = false,
)

/** Per-app strategy that turns a snapshot into zero or more messages. */
fun interface NotificationParser {
    fun parse(snapshot: NotificationSnapshot): List<ParsedMessage>
}

/** Parsers keyed by package name, with a generic fallback for every other app. */
class ParserRegistry(
    private val fallback: NotificationParser = GenericParser(),
    parsers: Map<String, NotificationParser> = defaultParsers(),
) {
    companion object {
        /** Built-in per-app parsers. */
        fun defaultParsers(): Map<String, NotificationParser> {
            val whatsApp = WhatsAppParser()
            val telegram = TelegramParser()
            return mapOf(
                WhatsAppParser.PACKAGE to whatsApp,
                WhatsAppParser.PACKAGE_BUSINESS to whatsApp,
                TelegramParser.PACKAGE to telegram,
                TelegramParser.PACKAGE_WEB to telegram,
                InstagramParser.PACKAGE to InstagramParser(),
                MessengerParser.PACKAGE to MessengerParser(),
            )
        }
    }

    private val byPackage = parsers.toMutableMap()

    fun register(packageName: String, parser: NotificationParser) {
        byPackage[packageName] = parser
    }

    fun parserFor(packageName: String): NotificationParser = byPackage[packageName] ?: fallback
}

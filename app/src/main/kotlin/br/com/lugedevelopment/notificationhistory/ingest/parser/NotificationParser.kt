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
            return mapOf(
                WhatsAppParser.PACKAGE to whatsApp,
                WhatsAppParser.PACKAGE_BUSINESS to whatsApp,
            )
        }
    }

    private val byPackage = parsers.toMutableMap()

    fun register(packageName: String, parser: NotificationParser) {
        byPackage[packageName] = parser
    }

    fun parserFor(packageName: String): NotificationParser = byPackage[packageName] ?: fallback
}

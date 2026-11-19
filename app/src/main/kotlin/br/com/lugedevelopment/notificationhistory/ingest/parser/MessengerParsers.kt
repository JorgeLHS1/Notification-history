package br.com.lugedevelopment.notificationhistory.ingest.parser

import br.com.lugedevelopment.notificationhistory.ingest.NotificationSnapshot

/**
 * Shared cleanup for chat apps built on top of [GenericParser]:
 * - strips "(3 messages)" counters from titles so the conversation key stays stable;
 * - drops app-level summaries ("5 new messages", title = app label) and ongoing
 *   "chat bubble active" style posts that carry no content;
 * - lets a subclass split plain (non-MessagingStyle) group posts.
 */
abstract class ChatAppParser(private val generic: GenericParser = GenericParser()) : NotificationParser {

    /** Splits a plain post into a group message, or null when it is a 1:1 post. */
    protected open fun splitPlainGroup(title: String, text: String, postedAt: Long): ParsedMessage? = null

    override fun parse(snapshot: NotificationSnapshot): List<ParsedMessage> {
        if (snapshot.isGroupSummary) return emptyList()

        if (snapshot.messages.isEmpty()) {
            val title = snapshot.title?.trim()
            if (title.isNullOrEmpty() || title == snapshot.appLabel) return emptyList()
            val text = snapshot.text?.trim()?.takeIf { it.isNotEmpty() } ?: return emptyList()
            if (SUMMARY.matches(text)) return emptyList()
            splitPlainGroup(title, text, snapshot.postedAt)?.let { return listOf(it) }
        }

        return generic.parse(
            snapshot.copy(
                title = snapshot.title?.let(::stripCounter),
                conversationTitle = snapshot.conversationTitle?.let(::stripCounter),
            ),
        )
    }

    companion object {
        private val COUNTER = Regex("""\s*\(\d+\s+(new\s+)?(messages?|mensagens?|mensajes?)\)\s*$""", RegexOption.IGNORE_CASE)
        private val SUMMARY = Regex(
            """\d+\s+(new\s+|novas?\s+|nuevos?\s+)?(messages?|mensagens?|mensajes?)(\s+(from|de)\s+\d+\s+\w+)?""",
            RegexOption.IGNORE_CASE,
        )

        fun stripCounter(title: String): String = title.replace(COUNTER, "").trim()
    }
}

/**
 * Telegram (official and web-flavored builds). Plain group posts use "Sender @ Group"
 * as title; MessagingStyle posts go through the generic path.
 */
class TelegramParser : ChatAppParser() {
    override fun splitPlainGroup(title: String, text: String, postedAt: Long): ParsedMessage? {
        val at = title.indexOf(" @ ")
        if (at <= 0 || at + 3 >= title.length) return null
        val sender = title.substring(0, at).trim()
        val group = stripCounter(title.substring(at + 3))
        return ParsedMessage(group, group, true, sender, text, postedAt)
    }

    companion object {
        const val PACKAGE = "org.telegram.messenger"
        const val PACKAGE_WEB = "org.telegram.messenger.web"
    }
}

/** Instagram direct messages. Activity notifications without a thread title are dropped. */
class InstagramParser : ChatAppParser() {
    companion object {
        const val PACKAGE = "com.instagram.android"
    }
}

/** Messenger. Group posts arrive as MessagingStyle; plain posts are treated as 1:1. */
class MessengerParser : ChatAppParser() {
    companion object {
        const val PACKAGE = "com.facebook.orca"
    }
}

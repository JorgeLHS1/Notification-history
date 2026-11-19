package br.com.lugedevelopment.notificationhistory.ingest

import br.com.lugedevelopment.notificationhistory.data.AppEntity
import br.com.lugedevelopment.notificationhistory.data.ConversationEntity
import br.com.lugedevelopment.notificationhistory.data.HistoryDatabase
import br.com.lugedevelopment.notificationhistory.data.MessageEntity
import br.com.lugedevelopment.notificationhistory.ingest.parser.ParserRegistry

/** Result of ingesting one snapshot. */
sealed interface IngestResult {
    /** App has capture turned off. */
    data object Skipped : IngestResult
    /** Removal events (handled from T7 on) or snapshots with no content. */
    data object Ignored : IngestResult
    data class Stored(val newMessages: Int, val duplicates: Int, val markedDeleted: Int = 0) : IngestResult
}

/** A row of the conversations list. */
data class ConversationSummary(
    val conversation: ConversationEntity,
    val lastMessage: MessageEntity?,
)

/**
 * The single seam of the app: receives [NotificationSnapshot]s and owns capture rules,
 * parsing, grouping, dedup and persistence, plus the query surface for the UI.
 *
 * Calls are blocking (Room on the caller thread): call them off the main thread in the app.
 */
class Ingestor(
    private val database: HistoryDatabase,
    val parsers: ParserRegistry = ParserRegistry(),
) {
    private val dao = database.historyDao()

    fun ingest(snapshot: NotificationSnapshot): IngestResult = database.runInTransaction<IngestResult> {
        val app = dao.app(snapshot.packageName)
            ?: AppEntity(snapshot.packageName, snapshot.appLabel).also { dao.insertApp(it) }
        if (!app.captureEnabled) return@runInTransaction IngestResult.Skipped
        if (snapshot.event == NotificationSnapshot.Event.REMOVED) return@runInTransaction IngestResult.Ignored

        val parsed = parsers.parserFor(snapshot.packageName).parse(snapshot)
        if (parsed.isEmpty()) return@runInTransaction IngestResult.Ignored

        val metadata = snapshot.extras.takeIf { it.isNotEmpty() }
            ?.entries?.joinToString("\n") { "${it.key}=${it.value}" }
        var stored = 0
        var duplicates = 0
        var markedDeleted = 0
        for (message in parsed) {
            var conversation = dao.conversation(snapshot.packageName, message.conversationKey)
                ?: ConversationEntity(
                    packageName = snapshot.packageName,
                    conversationKey = message.conversationKey,
                    title = message.conversationTitle,
                    isGroup = message.isGroup,
                    lastActivityAt = message.postedAt,
                ).let { it.copy(id = dao.insertConversation(it)) }

            if (DeletedPlaceholders.matches(message.text)) {
                // The app replaced a captured message with a "deleted" placeholder:
                // flag the original and keep its text; the placeholder itself is not stored.
                val original = dao.deletionCandidate(
                    conversation.id, message.sender, message.postedAt, snapshot.key,
                )
                if (original != null) {
                    dao.markDeleted(original.id, snapshot.postedAt)
                    markedDeleted++
                }
                continue
            }

            val isDuplicate = dao.countDuplicates(
                conversation.id, message.sender, message.text, message.postedAt, snapshot.key,
            ) > 0
            if (isDuplicate) {
                duplicates++
                continue
            }
            dao.insertMessage(
                MessageEntity(
                    conversationId = conversation.id,
                    sender = message.sender,
                    text = message.text,
                    postedAt = message.postedAt,
                    notificationKey = snapshot.key,
                    metadata = metadata,
                ),
            )
            conversation = conversation.copy(
                title = message.conversationTitle,
                isGroup = conversation.isGroup || message.isGroup,
                lastActivityAt = maxOf(conversation.lastActivityAt, message.postedAt),
                unreadCount = conversation.unreadCount + 1,
            )
            dao.updateConversation(conversation)
            stored++
        }
        IngestResult.Stored(stored, duplicates, markedDeleted)
    }

    // --- Retention --------------------------------------------------------------------

    /**
     * Deletes expired messages: regular ones older than [RetentionPolicy.generalDays]
     * (by postedAt) and deleted ones older than [RetentionPolicy.deletedDays] (by deletedAt).
     * Pinned messages never expire. Conversations left empty are removed.
     */
    fun applyRetention(policy: RetentionPolicy = RetentionPolicy(), now: Long = System.currentTimeMillis()): RetentionResult =
        database.runInTransaction<RetentionResult> {
            val general = policy.generalDays?.let { dao.deleteExpiredMessages(now - it * DAY_MS) } ?: 0
            val deleted = policy.deletedDays?.let { dao.deleteExpiredDeletedMessages(now - it * DAY_MS) } ?: 0
            RetentionResult(general, deleted, dao.deleteEmptyConversations())
        }

    fun setPinned(messageId: Long, pinned: Boolean) = dao.setPinned(messageId, pinned)

    // --- App filter -------------------------------------------------------------------

    fun apps(): List<AppEntity> = dao.apps()

    /** Turns capture on/off for an app; registers the app if it was never seen. */
    fun setCaptureEnabled(packageName: String, enabled: Boolean, label: String = packageName) {
        database.runInTransaction {
            dao.insertApp(AppEntity(packageName, label, enabled))
            dao.setCaptureEnabled(packageName, enabled)
        }
    }

    // --- Query surface ----------------------------------------------------------------

    /** Conversations, most recent first. [packages] null or empty means all apps. */
    fun conversations(packages: Set<String>? = null): List<ConversationSummary> {
        val rows = if (packages.isNullOrEmpty()) dao.conversations() else dao.conversationsOf(packages)
        return rows.map { ConversationSummary(it, dao.lastMessage(it.id)) }
    }

    /** Messages of a conversation, oldest first. */
    fun messages(conversationId: Long): List<MessageEntity> = dao.messages(conversationId)

    private companion object {
        const val DAY_MS = 24L * 60 * 60 * 1000
    }
}

/** Retention windows in days; null keeps forever. Pinned messages never expire. */
data class RetentionPolicy(
    val generalDays: Long? = DEFAULT_GENERAL_DAYS,
    val deletedDays: Long? = DEFAULT_DELETED_DAYS,
) {
    companion object {
        const val DEFAULT_GENERAL_DAYS = 90L
        const val DEFAULT_DELETED_DAYS = 365L
    }
}

data class RetentionResult(val expiredMessages: Int, val expiredDeletedMessages: Int, val removedConversations: Int)

/** Localized "this message was deleted" placeholders (EN, PT-BR, PT-PT, ES). */
object DeletedPlaceholders {
    private val known = setOf(
        "this message was deleted",
        "esta mensagem foi apagada",
        "mensagem apagada",
        "esta mensagem foi eliminada",
        "mensagem eliminada",
        "se eliminó este mensaje",
        "este mensaje fue eliminado",
    )

    fun matches(text: String): Boolean {
        val normalized = text.trim().trimStart { !it.isLetter() }.trimEnd('.', ' ').lowercase()
        return normalized in known
    }
}

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
    data class Stored(val newMessages: Int, val duplicates: Int) : IngestResult
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
        for (message in parsed) {
            var conversation = dao.conversation(snapshot.packageName, message.conversationKey)
                ?: ConversationEntity(
                    packageName = snapshot.packageName,
                    conversationKey = message.conversationKey,
                    title = message.conversationTitle,
                    isGroup = message.isGroup,
                    lastActivityAt = message.postedAt,
                ).let { it.copy(id = dao.insertConversation(it)) }

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
        IngestResult.Stored(stored, duplicates)
    }

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
}

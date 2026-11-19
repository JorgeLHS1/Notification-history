package br.com.lugedevelopment.notificationhistory.ingest

import br.com.lugedevelopment.notificationhistory.data.AppEntity
import br.com.lugedevelopment.notificationhistory.data.AttachmentEntity
import br.com.lugedevelopment.notificationhistory.data.ConversationEntity
import br.com.lugedevelopment.notificationhistory.data.HistoryDatabase
import br.com.lugedevelopment.notificationhistory.data.MessageEntity
import br.com.lugedevelopment.notificationhistory.backup.BackupConversation
import br.com.lugedevelopment.notificationhistory.backup.BackupData
import br.com.lugedevelopment.notificationhistory.backup.BackupMessage
import br.com.lugedevelopment.notificationhistory.backup.HistoryBackup
import br.com.lugedevelopment.notificationhistory.backup.ImportResult
import br.com.lugedevelopment.notificationhistory.data.AttachmentType
import br.com.lugedevelopment.notificationhistory.ingest.parser.ParserRegistry
import br.com.lugedevelopment.notificationhistory.media.MediaCandidate
import br.com.lugedevelopment.notificationhistory.media.MediaFile
import br.com.lugedevelopment.notificationhistory.media.MediaMatcher
import br.com.lugedevelopment.notificationhistory.media.MediaRecoveryResult
import br.com.lugedevelopment.notificationhistory.media.MediaSource
import java.io.InputStream
import java.io.OutputStream

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

/** A search result: the matching message and the conversation that holds it. */
data class SearchHit(val message: MessageEntity, val conversation: ConversationEntity)

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
        var lastStoredId: Long? = null
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
            val messageId = dao.insertMessage(
                MessageEntity(
                    conversationId = conversation.id,
                    sender = message.sender,
                    text = message.text,
                    postedAt = message.postedAt,
                    notificationKey = snapshot.key,
                    metadata = metadata,
                ),
            )
            val links = AttachmentBuilder.links(message.text)
            if (links.isNotEmpty()) dao.insertAttachments(links.map { it.copy(messageId = messageId) })
            lastStoredId = messageId
            conversation = conversation.copy(
                title = message.conversationTitle,
                isGroup = conversation.isGroup || message.isGroup,
                lastActivityAt = maxOf(conversation.lastActivityAt, message.postedAt),
                unreadCount = conversation.unreadCount + 1,
            )
            dao.updateConversation(conversation)
            stored++
        }
        // Image/large-icon/action metadata belongs to the notification: attach it to the newest new message.
        lastStoredId?.let { id ->
            val extra = AttachmentBuilder.fromExtras(snapshot.extras)
            if (extra.isNotEmpty()) dao.insertAttachments(extra.map { it.copy(messageId = id) })
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

    // --- Search and bulk operations (T8) -----------------------------------------------

    /**
     * Full-text search over sender and text. Every word of [query] must match as a prefix
     * (partial words work), case-insensitively for ASCII. [deletedOnly] keeps only deleted
     * messages; with a blank [query] it lists all deleted messages. A blank query without
     * [deletedOnly] returns nothing. [packages] null or empty means all apps. Newest first.
     */
    fun search(
        query: String,
        deletedOnly: Boolean = false,
        packages: Set<String>? = null,
        limit: Int = SEARCH_LIMIT,
    ): List<SearchHit> {
        val anyPackage = packages.isNullOrEmpty()
        val scope = packages ?: emptySet()
        val match = SearchText.matchExpression(query)
        val rows = when {
            match != null -> dao.search(match, deletedOnly, anyPackage, scope, limit)
            deletedOnly && query.isBlank() -> dao.deletedMessages(anyPackage, scope, limit)
            else -> emptyList()
        }
        val conversations = HashMap<Long, ConversationEntity?>()
        return rows.mapNotNull { message ->
            val conversation = conversations.getOrPut(message.conversationId) { dao.conversationById(message.conversationId) }
            conversation?.let { SearchHit(message, it) }
        }
    }

    /** Marks the messages read or unread and recomputes the unread counter of their conversations. */
    fun setRead(messageIds: Collection<Long>, read: Boolean) = bulk(messageIds) { ids ->
        dao.setRead(ids, read)
    }

    /** Pins or unpins the messages (pinned messages never expire). */
    fun setPinned(messageIds: Collection<Long>, pinned: Boolean) = bulk(messageIds) { ids ->
        dao.setPinned(ids, pinned)
    }

    /**
     * Deletes the messages for good (pinned ones too, since the user picked them), refreshes
     * the unread counters and removes conversations left empty. Returns how many were deleted.
     */
    fun deleteMessages(messageIds: Collection<Long>): Int {
        var removed = 0
        bulk(messageIds) { ids -> removed += dao.deleteMessages(ids) }
        return removed
    }

    private fun bulk(messageIds: Collection<Long>, action: (List<Long>) -> Unit) {
        if (messageIds.isEmpty()) return
        database.runInTransaction {
            val touched = HashSet<Long>()
            for (chunk in messageIds.distinct().chunked(BULK_CHUNK)) {
                touched += dao.conversationIdsOf(chunk)
                action(chunk)
            }
            for (chunk in touched.chunked(BULK_CHUNK)) dao.recountUnread(chunk)
            dao.deleteEmptyConversations()
        }
    }

    // --- Backup export/import (T10) ---------------------------------------------------

    /** Writes the whole history (apps, conversations, messages, attachments) as a versioned JSON in a ZIP. */
    fun exportBackup(out: OutputStream, now: Long = System.currentTimeMillis()) {
        val data = database.runInTransaction<BackupData> {
            val byConversation = dao.allMessages().groupBy { it.conversationId }
            val attachments = dao.allAttachments().groupBy { it.messageId }
            BackupData(
                apps = dao.apps(),
                conversations = dao.conversations().map { c ->
                    BackupConversation(c, byConversation[c.id].orEmpty().map { BackupMessage(it, attachments[it.id].orEmpty()) })
                },
            )
        }
        HistoryBackup.write(data, out, now)
    }

    /**
     * Merges a backup into the current history without duplicating anything: apps are kept
     * as they are if already known, conversations are matched by (package, key) and messages
     * by the ingest dedup rule. Flags (deleted, pinned, read) of new messages are restored.
     * Throws InvalidBackupException for unknown files; nothing is written in that case.
     */
    fun importBackup(input: InputStream): ImportResult {
        val data = HistoryBackup.read(input)
        return database.runInTransaction<ImportResult> {
            data.apps.forEach { dao.insertApp(it) }
            var newConversations = 0
            var newMessages = 0
            var duplicates = 0
            val touched = HashSet<Long>()
            for (bc in data.conversations) {
                val c = bc.conversation
                if (dao.app(c.packageName) == null) dao.insertApp(AppEntity(c.packageName, c.packageName))
                var conversation = dao.conversation(c.packageName, c.conversationKey)
                    ?: c.copy(id = 0, unreadCount = 0).let { newConversations++; it.copy(id = dao.insertConversation(it)) }
                for (bm in bc.messages) {
                    val m = bm.message
                    if (dao.countDuplicates(conversation.id, m.sender, m.text, m.postedAt, m.notificationKey) > 0) {
                        duplicates++
                        continue
                    }
                    val id = dao.insertMessage(m.copy(id = 0, conversationId = conversation.id))
                    if (bm.attachments.isNotEmpty()) dao.insertAttachments(bm.attachments.map { it.copy(id = 0, messageId = id) })
                    newMessages++
                }
                if (c.lastActivityAt > conversation.lastActivityAt) {
                    conversation = conversation.copy(lastActivityAt = c.lastActivityAt)
                    dao.updateConversation(conversation)
                }
                touched += conversation.id
            }
            for (chunk in touched.chunked(BULK_CHUNK)) dao.recountUnread(chunk)
            ImportResult(newConversations, newMessages, duplicates)
        }
    }

    // --- Media recovery (T10) ------------------------------------------------------------

    /**
     * Copies media from [source] into app-private storage for messages that announced a
     * picture (IMAGE attachment) and have no MEDIA_FILE yet, matching by timestamp
     * ([MediaMatcher]). [save] performs the copy and returns the stored path (null = failed).
     */
    fun recoverMedia(
        source: MediaSource,
        save: (messageId: Long, file: MediaFile) -> String?,
        since: Long = 0,
        windowMs: Long = MediaMatcher.DEFAULT_WINDOW_MS,
    ): MediaRecoveryResult {
        val files = source.list()
        val candidates = dao.messagesAwaitingMedia(since).map { MediaCandidate(it.id, it.postedAt) }
        // A file already copied for another message is not offered again.
        val copiedValues = dao.mediaFileValues()
        val fresh = files.filter { f -> copiedValues.none { it.endsWith("_" + MediaMatcher.safeName(f.name)) } }
        val matches = MediaMatcher.match(candidates, fresh, windowMs)
        var copied = 0
        for ((messageId, file) in matches) {
            val path = save(messageId, file) ?: continue
            dao.insertAttachments(listOf(AttachmentEntity(messageId = messageId, type = AttachmentType.MEDIA_FILE, value = path)))
            copied++
        }
        return MediaRecoveryResult(files.size, matches.size, copied)
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

    /** Attachments (links, images, actions) of one message, in insertion order. */
    fun attachments(messageId: Long): List<AttachmentEntity> = dao.attachments(messageId)

    /** Attachments of many messages grouped by message id (empty ids give an empty map). */
    fun attachments(messageIds: Collection<Long>): Map<Long, List<AttachmentEntity>> =
        if (messageIds.isEmpty()) emptyMap() else dao.attachmentsOf(messageIds).groupBy { it.messageId }

    /** Conversations, most recent first. [packages] null or empty means all apps. */
    fun conversations(packages: Set<String>? = null): List<ConversationSummary> {
        val rows = if (packages.isNullOrEmpty()) dao.conversations() else dao.conversationsOf(packages)
        return rows.map { ConversationSummary(it, dao.lastMessage(it.id)) }
    }

    /** Messages of a conversation, oldest first. */
    fun messages(conversationId: Long): List<MessageEntity> = dao.messages(conversationId)

    private companion object {
        const val DAY_MS = 24L * 60 * 60 * 1000
        const val SEARCH_LIMIT = 200
        const val BULK_CHUNK = 500
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

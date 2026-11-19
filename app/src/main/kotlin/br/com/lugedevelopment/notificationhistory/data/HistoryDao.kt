package br.com.lugedevelopment.notificationhistory.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface HistoryDao {
    @Query("SELECT * FROM app WHERE packageName = :packageName")
    fun app(packageName: String): AppEntity?

    @Query("SELECT * FROM app ORDER BY label COLLATE NOCASE")
    fun apps(): List<AppEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertApp(app: AppEntity): Long

    @Query("UPDATE app SET captureEnabled = :enabled WHERE packageName = :packageName")
    fun setCaptureEnabled(packageName: String, enabled: Boolean)

    @Query("SELECT * FROM conversation WHERE packageName = :packageName AND conversationKey = :key")
    fun conversation(packageName: String, key: String): ConversationEntity?

    @Insert
    fun insertConversation(conversation: ConversationEntity): Long

    @Update
    fun updateConversation(conversation: ConversationEntity)

    @Query("SELECT * FROM conversation ORDER BY lastActivityAt DESC")
    fun conversations(): List<ConversationEntity>

    @Query("SELECT * FROM conversation WHERE packageName IN (:packages) ORDER BY lastActivityAt DESC")
    fun conversationsOf(packages: Collection<String>): List<ConversationEntity>

    @Query(
        "SELECT COUNT(*) FROM message WHERE conversationId = :conversationId AND text = :text " +
            "AND ((sender IS NULL AND :sender IS NULL) OR sender = :sender) " +
            "AND (postedAt = :postedAt OR notificationKey = :notificationKey)",
    )
    fun countDuplicates(
        conversationId: Long,
        sender: String?,
        text: String,
        postedAt: Long,
        notificationKey: String,
    ): Int

    /** Original message a "deleted" placeholder replaces: same sender, same timestamp preferred, else same notification. */
    @Query(
        "SELECT * FROM message WHERE conversationId = :conversationId AND deleted = 0 " +
            "AND ((sender IS NULL AND :sender IS NULL) OR sender = :sender) " +
            "AND (postedAt = :postedAt OR notificationKey = :notificationKey) " +
            "ORDER BY (postedAt = :postedAt) DESC, postedAt DESC, id DESC LIMIT 1",
    )
    fun deletionCandidate(conversationId: Long, sender: String?, postedAt: Long, notificationKey: String): MessageEntity?

    @Query("UPDATE message SET deleted = 1, deletedAt = :at WHERE id = :id")
    fun markDeleted(id: Long, at: Long)

    @Query("UPDATE message SET pinned = :pinned WHERE id = :id")
    fun setPinned(id: Long, pinned: Boolean)

    @Query("DELETE FROM message WHERE pinned = 0 AND deleted = 0 AND postedAt < :cutoff")
    fun deleteExpiredMessages(cutoff: Long): Int

    @Query("DELETE FROM message WHERE pinned = 0 AND deleted = 1 AND COALESCE(deletedAt, postedAt) < :cutoff")
    fun deleteExpiredDeletedMessages(cutoff: Long): Int

    @Query("DELETE FROM conversation WHERE id NOT IN (SELECT DISTINCT conversationId FROM message)")
    fun deleteEmptyConversations(): Int

    @Insert
    fun insertAttachments(attachments: List<AttachmentEntity>)

    @Query("SELECT * FROM attachment WHERE messageId = :messageId ORDER BY id")
    fun attachments(messageId: Long): List<AttachmentEntity>

    @Query("SELECT * FROM attachment WHERE messageId IN (:messageIds) ORDER BY id")
    fun attachmentsOf(messageIds: Collection<Long>): List<AttachmentEntity>

    @Insert
    fun insertMessage(message: MessageEntity): Long

    @Query("SELECT * FROM message WHERE conversationId = :conversationId ORDER BY postedAt, id")
    fun messages(conversationId: Long): List<MessageEntity>

    @Query("SELECT * FROM message WHERE conversationId = :conversationId ORDER BY postedAt DESC, id DESC LIMIT 1")
    fun lastMessage(conversationId: Long): MessageEntity?

    @Query("SELECT * FROM conversation WHERE id = :id")
    fun conversationById(id: Long): ConversationEntity?

    // --- Search and bulk operations (T8) ---

    /** Full-text search; [match] is an FTS4 MATCH expression built by the Ingestor. */
    @Query(
        "SELECT m.* FROM message m " +
            "JOIN message_fts ON m.id = message_fts.rowid " +
            "JOIN conversation c ON c.id = m.conversationId " +
            "WHERE message_fts MATCH :match " +
            "AND (:deletedOnly = 0 OR m.deleted = 1) " +
            "AND (:anyPackage = 1 OR c.packageName IN (:packages)) " +
            "ORDER BY m.postedAt DESC, m.id DESC LIMIT :limit",
    )
    fun search(match: String, deletedOnly: Boolean, anyPackage: Boolean, packages: Collection<String>, limit: Int): List<MessageEntity>

    @Query(
        "SELECT m.* FROM message m JOIN conversation c ON c.id = m.conversationId " +
            "WHERE m.deleted = 1 AND (:anyPackage = 1 OR c.packageName IN (:packages)) " +
            "ORDER BY m.postedAt DESC, m.id DESC LIMIT :limit",
    )
    fun deletedMessages(anyPackage: Boolean, packages: Collection<String>, limit: Int): List<MessageEntity>

    @Query("UPDATE message SET read = :read WHERE id IN (:ids)")
    fun setRead(ids: Collection<Long>, read: Boolean)

    @Query("UPDATE message SET pinned = :pinned WHERE id IN (:ids)")
    fun setPinned(ids: Collection<Long>, pinned: Boolean)

    @Query("DELETE FROM message WHERE id IN (:ids)")
    fun deleteMessages(ids: Collection<Long>): Int

    @Query("SELECT DISTINCT conversationId FROM message WHERE id IN (:ids)")
    fun conversationIdsOf(ids: Collection<Long>): List<Long>

    /** Recomputes the unread counter from the messages that are still unread. */
    @Query(
        "UPDATE conversation SET unreadCount = " +
            "(SELECT COUNT(*) FROM message WHERE conversationId = conversation.id AND read = 0) " +
            "WHERE id IN (:conversationIds)",
    )
    fun recountUnread(conversationIds: Collection<Long>)

    // --- Backup and media (T10) ---

    @Query("SELECT * FROM message ORDER BY postedAt, id")
    fun allMessages(): List<MessageEntity>

    @Query("SELECT * FROM attachment ORDER BY id")
    fun allAttachments(): List<AttachmentEntity>

    /** Messages announcing a picture that have no recovered media file yet. */
    @Query(
        "SELECT * FROM message WHERE postedAt >= :since " +
            "AND id IN (SELECT messageId FROM attachment WHERE type = 'IMAGE') " +
            "AND id NOT IN (SELECT messageId FROM attachment WHERE type = 'MEDIA_FILE') ORDER BY postedAt",
    )
    fun messagesAwaitingMedia(since: Long): List<MessageEntity>

    @Query("SELECT value FROM attachment WHERE type = 'MEDIA_FILE'")
    fun mediaFileValues(): List<String>
}
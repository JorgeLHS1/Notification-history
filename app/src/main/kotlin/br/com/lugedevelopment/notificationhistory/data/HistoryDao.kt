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
}

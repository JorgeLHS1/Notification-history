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

    @Insert
    fun insertMessage(message: MessageEntity): Long

    @Query("SELECT * FROM message WHERE conversationId = :conversationId ORDER BY postedAt, id")
    fun messages(conversationId: Long): List<MessageEntity>

    @Query("SELECT * FROM message WHERE conversationId = :conversationId ORDER BY postedAt DESC, id DESC LIMIT 1")
    fun lastMessage(conversationId: Long): MessageEntity?
}

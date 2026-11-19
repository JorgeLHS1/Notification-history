package br.com.lugedevelopment.notificationhistory.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "app")
data class AppEntity(
    @PrimaryKey val packageName: String,
    val label: String,
    val captureEnabled: Boolean = true,
)

@Entity(
    tableName = "conversation",
    indices = [Index(value = ["packageName", "conversationKey"], unique = true)],
    foreignKeys = [
        ForeignKey(
            entity = AppEntity::class,
            parentColumns = ["packageName"],
            childColumns = ["packageName"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class ConversationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val conversationKey: String,
    val title: String,
    val isGroup: Boolean,
    val lastActivityAt: Long,
    val unreadCount: Int = 0,
)

@Entity(
    tableName = "message",
    indices = [Index("conversationId"), Index("notificationKey"), Index("postedAt")],
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val conversationId: Long,
    val sender: String?,
    val text: String,
    val postedAt: Long,
    val notificationKey: String,
    val deleted: Boolean = false,
    val read: Boolean = false,
    val pinned: Boolean = false,
    /** Snapshot extras serialized as "key=value" lines; attachments come later. */
    val metadata: String? = null,
)

/** Full-text index over messages, kept in sync by Room (external content). */
@Fts4(contentEntity = MessageEntity::class)
@Entity(tableName = "message_fts")
data class MessageFts(
    @ColumnInfo(name = "rowid") @PrimaryKey val rowId: Long,
    val sender: String?,
    val text: String,
)

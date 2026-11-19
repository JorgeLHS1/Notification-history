package br.com.lugedevelopment.notificationhistory.backup

import br.com.lugedevelopment.notificationhistory.data.AppEntity
import br.com.lugedevelopment.notificationhistory.data.AttachmentEntity
import br.com.lugedevelopment.notificationhistory.data.AttachmentType
import br.com.lugedevelopment.notificationhistory.data.ConversationEntity
import br.com.lugedevelopment.notificationhistory.data.MessageEntity
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** In-memory form of a backup: database ids are not portable, so messages nest under conversations. */
data class BackupData(val apps: List<AppEntity>, val conversations: List<BackupConversation>)

data class BackupConversation(val conversation: ConversationEntity, val messages: List<BackupMessage>)

data class BackupMessage(val message: MessageEntity, val attachments: List<AttachmentEntity>)

/** Thrown for files that are not a backup of this app or come from a newer, unknown format. */
class InvalidBackupException(message: String) : Exception(message)

/**
 * Versioned JSON inside a ZIP (entry [ENTRY]). Format v1:
 * `{format, version, exportedAt, apps:[{packageName,label,captureEnabled}],
 *   conversations:[{packageName,conversationKey,title,isGroup,lastActivityAt,
 *   messages:[{sender?,text,postedAt,notificationKey,deleted,read,pinned,deletedAt?,metadata?,
 *   attachments:[{type,value}]}]}]}`.
 * Recovered media files are not included (only their attachment rows are, as references).
 */
object HistoryBackup {
    const val FORMAT = "notification-history-backup"
    const val VERSION = 1
    const val ENTRY = "history.json"

    fun write(data: BackupData, out: OutputStream, exportedAt: Long = System.currentTimeMillis()) {
        val zip = ZipOutputStream(out)
        zip.putNextEntry(ZipEntry(ENTRY))
        zip.write(toJson(data, exportedAt).toString().toByteArray(Charsets.UTF_8))
        zip.closeEntry()
        zip.finish()
        zip.flush()
    }

    fun read(input: InputStream): BackupData {
        val zip = ZipInputStream(input)
        while (true) {
            val entry = zip.nextEntry ?: break
            if (entry.name == ENTRY) {
                val text = zip.readBytes().toString(Charsets.UTF_8)
                return fromJson(runCatching { JSONObject(text) }.getOrElse { throw InvalidBackupException("bad json") })
            }
        }
        throw InvalidBackupException("missing $ENTRY")
    }

    fun toJson(data: BackupData, exportedAt: Long): JSONObject = JSONObject()
        .put("format", FORMAT)
        .put("version", VERSION)
        .put("exportedAt", exportedAt)
        .put("apps", JSONArray(data.apps.map { JSONObject().put("packageName", it.packageName).put("label", it.label).put("captureEnabled", it.captureEnabled) }))
        .put("conversations", JSONArray(data.conversations.map { bc ->
            val c = bc.conversation
            JSONObject()
                .put("packageName", c.packageName).put("conversationKey", c.conversationKey).put("title", c.title)
                .put("isGroup", c.isGroup).put("lastActivityAt", c.lastActivityAt)
                .put("messages", JSONArray(bc.messages.map { bm ->
                    val m = bm.message
                    JSONObject()
                        .put("sender", m.sender ?: JSONObject.NULL).put("text", m.text).put("postedAt", m.postedAt)
                        .put("notificationKey", m.notificationKey).put("deleted", m.deleted).put("read", m.read)
                        .put("pinned", m.pinned).put("deletedAt", m.deletedAt ?: JSONObject.NULL)
                        .put("metadata", m.metadata ?: JSONObject.NULL)
                        .put("attachments", JSONArray(bm.attachments.map { JSONObject().put("type", it.type.name).put("value", it.value) }))
                }))
        }))

    fun fromJson(json: JSONObject): BackupData {
        if (json.optString("format") != FORMAT) throw InvalidBackupException("not a backup")
        val version = json.optInt("version", -1)
        if (version < 1 || version > VERSION) throw InvalidBackupException("unsupported version $version")
        val apps = json.optJSONArray("apps").objects().map {
            AppEntity(it.getString("packageName"), it.optString("label", it.getString("packageName")), it.optBoolean("captureEnabled", true))
        }
        val conversations = json.optJSONArray("conversations").objects().map { c ->
            BackupConversation(
                ConversationEntity(
                    packageName = c.getString("packageName"),
                    conversationKey = c.getString("conversationKey"),
                    title = c.optString("title"),
                    isGroup = c.optBoolean("isGroup"),
                    lastActivityAt = c.optLong("lastActivityAt"),
                ),
                c.optJSONArray("messages").objects().map { m ->
                    BackupMessage(
                        MessageEntity(
                            conversationId = 0,
                            sender = m.nullableString("sender"),
                            text = m.getString("text"),
                            postedAt = m.getLong("postedAt"),
                            notificationKey = m.optString("notificationKey"),
                            deleted = m.optBoolean("deleted"),
                            read = m.optBoolean("read"),
                            pinned = m.optBoolean("pinned"),
                            deletedAt = if (m.isNull("deletedAt")) null else m.optLong("deletedAt"),
                            metadata = m.nullableString("metadata"),
                        ),
                        m.optJSONArray("attachments").objects().mapNotNull { a ->
                            val type = AttachmentType.entries.firstOrNull { it.name == a.optString("type") } ?: return@mapNotNull null
                            AttachmentEntity(messageId = 0, type = type, value = a.optString("value"))
                        },
                    )
                },
            )
        }
        return BackupData(apps, conversations)
    }

    private fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }

    private fun JSONObject.nullableString(name: String): String? = if (isNull(name)) null else optString(name)
}

/** Outcome of an import merge. */
data class ImportResult(val conversations: Int, val messages: Int, val duplicates: Int)

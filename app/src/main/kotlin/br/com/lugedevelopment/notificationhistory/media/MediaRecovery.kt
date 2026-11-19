package br.com.lugedevelopment.notificationhistory.media

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import java.io.File
import java.io.InputStream

/** A media file visible to the app through some source (e.g. a user-granted SAF folder). */
data class MediaFile(val id: String, val name: String, val mimeType: String?, val modifiedAt: Long)

/**
 * Pluggable origin of media files. The only implementation shipped is [SafTreeMediaSource]
 * (a folder the user picked); others (e.g. a MediaStore query) can be added without touching the Ingestor.
 */
interface MediaSource {
    fun list(): List<MediaFile>
    fun open(file: MediaFile): InputStream?
}

/** Outcome of a recovery pass. */
data class MediaRecoveryResult(val scanned: Int, val matched: Int, val copied: Int)

/**
 * Lists image/video/audio files under a SAF tree (recursively, depth-limited).
 * Requires a persisted read grant on [treeUri] (ACTION_OPEN_DOCUMENT_TREE).
 */
class SafTreeMediaSource(private val context: Context, private val treeUri: Uri) : MediaSource {
    override fun list(): List<MediaFile> {
        val out = ArrayList<MediaFile>()
        walk(DocumentsContract.getTreeDocumentId(treeUri), 0, out)
        return out
    }

    private fun walk(parentId: String, depth: Int, out: MutableList<MediaFile>) {
        if (depth > MAX_DEPTH || out.size >= MAX_FILES) return
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )
        val cursor = runCatching { context.contentResolver.query(children, projection, null, null, null) }.getOrNull() ?: return
        cursor.use {
            while (it.moveToNext()) {
                val id = it.getString(0) ?: continue
                val name = it.getString(1) ?: id
                val mime = it.getString(2)
                if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                    walk(id, depth + 1, out)
                } else if (MediaMatcher.isMedia(mime)) {
                    out += MediaFile(id, name, mime, it.getLong(3))
                }
            }
        }
    }

    override fun open(file: MediaFile): InputStream? = runCatching {
        context.contentResolver.openInputStream(DocumentsContract.buildDocumentUriUsingTree(treeUri, file.id))
    }.getOrNull()

    private companion object {
        const val MAX_DEPTH = 4
        const val MAX_FILES = 5_000
    }
}

/** Candidate message for media: id plus the time the notification was posted. */
data class MediaCandidate(val messageId: Long, val postedAt: Long)

/**
 * Pairs media files with messages by timestamp: each candidate gets the unused file whose
 * modification time is closest to its postedAt, within [windowMs]. Each file is used once.
 */
object MediaMatcher {
    const val DEFAULT_WINDOW_MS = 2 * 60 * 1000L

    /** File-system safe version of a display name, used for the private copy. */
    fun safeName(name: String): String = name.replace(Regex("[^A-Za-z0-9._-]"), "_").take(80)

    fun isMedia(mime: String?): Boolean =
        mime != null && (mime.startsWith("image/") || mime.startsWith("video/") || mime.startsWith("audio/"))

    fun match(candidates: List<MediaCandidate>, files: List<MediaFile>, windowMs: Long = DEFAULT_WINDOW_MS): Map<Long, MediaFile> {
        val used = HashSet<String>()
        val result = LinkedHashMap<Long, MediaFile>()
        for (c in candidates.sortedBy { it.postedAt }) {
            val best = files.asSequence()
                .filter { it.id !in used && kotlin.math.abs(it.modifiedAt - c.postedAt) <= windowMs }
                .minByOrNull { kotlin.math.abs(it.modifiedAt - c.postedAt) } ?: continue
            used += best.id
            result[c.messageId] = best
        }
        return result
    }
}

/** App-private copy target: files/media/<messageId>_<safe name>. */
class PrivateMediaStore(private val root: File) {
    fun save(messageId: Long, file: MediaFile, input: InputStream): File {
        root.mkdirs()
        val target = File(root, "${messageId}_${MediaMatcher.safeName(file.name)}")
        input.use { src -> target.outputStream().use { src.copyTo(it) } }
        return target
    }

    companion object {
        fun of(context: Context) = PrivateMediaStore(File(context.filesDir, "media"))
    }
}

/** Persisted SAF folder chosen for media recovery (private SharedPreferences). */
object MediaSettings {
    private const val FILE = "media_settings"
    private const val KEY_TREE = "media_tree_uri"

    fun treeUri(context: Context): Uri? =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY_TREE, null)?.let(Uri::parse)

    fun setTreeUri(context: Context, uri: Uri?) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString(KEY_TREE, uri?.toString()).apply()
    }
}

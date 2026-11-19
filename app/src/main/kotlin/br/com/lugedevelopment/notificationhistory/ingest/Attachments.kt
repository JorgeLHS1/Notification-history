package br.com.lugedevelopment.notificationhistory.ingest

import br.com.lugedevelopment.notificationhistory.data.AttachmentEntity
import br.com.lugedevelopment.notificationhistory.data.AttachmentType

/** Extras keys the listener writes into [NotificationSnapshot.extras] for attachments. */
object AttachmentExtras {
    const val LARGE_ICON = "largeIcon"
    const val PICTURE = "picture"
    /** "action.0", "action.1", ... hold action button labels. */
    const val ACTION_PREFIX = "action."
}

/** Finds http(s) links in text. Shared by the Ingestor (storage) and the UI (tappable text). */
object LinkExtractor {
    private val URL = Regex("""https?://[^\s<>"']+""", RegexOption.IGNORE_CASE)
    private const val TRAILING = ".,;:!?)]}"

    data class Link(val url: String, val start: Int, val end: Int)

    fun find(text: String): List<Link> = URL.findAll(text).mapNotNull { m ->
        var end = m.range.last + 1
        while (end > m.range.first && text[end - 1] in TRAILING) end--
        val url = text.substring(m.range.first, end)
        if (url.length > "https://".length) Link(url, m.range.first, end) else null
    }.toList()
}

/** Builds attachment rows for a stored message (messageId filled by the caller). */
internal object AttachmentBuilder {
    fun links(text: String): List<AttachmentEntity> =
        LinkExtractor.find(text).map { it.url }.distinct()
            .map { AttachmentEntity(messageId = 0, type = AttachmentType.LINK, value = it) }

    fun fromExtras(extras: Map<String, String>): List<AttachmentEntity> = buildList {
        extras[AttachmentExtras.PICTURE]?.takeIf { it.isNotBlank() }
            ?.let { add(AttachmentEntity(messageId = 0, type = AttachmentType.IMAGE, value = it)) }
        extras[AttachmentExtras.LARGE_ICON]?.takeIf { it.isNotBlank() }
            ?.let { add(AttachmentEntity(messageId = 0, type = AttachmentType.LARGE_ICON, value = it)) }
        extras.filterKeys { it.startsWith(AttachmentExtras.ACTION_PREFIX) }
            .toSortedMap(compareBy { it.removePrefix(AttachmentExtras.ACTION_PREFIX).toIntOrNull() ?: Int.MAX_VALUE })
            .values.filter { it.isNotBlank() }
            .forEach { add(AttachmentEntity(messageId = 0, type = AttachmentType.ACTION, value = it)) }
    }
}

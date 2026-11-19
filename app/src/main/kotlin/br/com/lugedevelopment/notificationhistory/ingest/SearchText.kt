package br.com.lugedevelopment.notificationhistory.ingest

/** Query helpers shared by the Ingestor (FTS expression) and the UI (highlighting). */
object SearchText {
    /** Words of a query: runs of letters/digits, lowercase. Everything else is a separator. */
    fun terms(query: String): List<String> =
        query.split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }.map { it.lowercase() }.distinct()

    /** FTS4 expression where every word is a prefix match, or null if the query has no words. */
    fun matchExpression(query: String): String? =
        terms(query).takeIf { it.isNotEmpty() }?.joinToString(" ") { "$it*" }

    /** Ranges of [text] that start a word matching a query term (prefix, case-insensitive). */
    fun highlightRanges(text: String, query: String): List<IntRange> {
        val terms = terms(query)
        if (terms.isEmpty()) return emptyList()
        val ranges = mutableListOf<IntRange>()
        for (word in Regex("[\\p{L}\\p{N}]+").findAll(text)) {
            val lower = word.value.lowercase()
            val hit = terms.filter { lower.startsWith(it) }.maxOfOrNull { it.length } ?: continue
            ranges += word.range.first until word.range.first + hit
        }
        return ranges
    }
}

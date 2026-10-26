package `in`.artham.fundamentals.domain

import kotlin.math.ln

data class RetrievedChunk(
    val docId: Long,
    val docTitle: String,
    val ord: Int,
    val body: String,
    val score: Double
)

/**
 * On-device retrieval over documents the user supplies — annual report text, concall
 * transcripts, their own notes.
 *
 * Deliberately lexical (BM25) rather than embedding-based. Two reasons, both honest:
 * a vector index needs a second model file on top of the language model, and BM25 over a
 * few hundred pages is fast, explainable and needs nothing but the text. Every retrieved
 * passage can be shown to the user with the exact terms that matched it, which a cosine
 * similarity score cannot do.
 *
 * BM25 parameters are the standard defaults: k1 = 1.5, b = 0.75.
 */
object DocumentIndex {

    private val stopwords = setOf(
        "the", "and", "for", "was", "were", "are", "with", "that", "this", "from", "have", "has", "had",
        "not", "but", "its", "our", "their", "which", "will", "would", "could", "should", "been", "than",
        "then", "them", "they", "there", "into", "over", "under", "about", "also", "such", "may", "can",
        "per", "any", "all", "each", "other", "more", "most", "some", "only", "very", "upon", "both"
    )

    fun tokenize(text: String): List<String> = text.lowercase()
        .split(Regex("[^a-z0-9]+"))
        .filter { it.length >= 2 && it !in stopwords }

    /**
     * Splits a document into overlapping chunks on paragraph boundaries where possible.
     * The overlap keeps a sentence that straddles a boundary retrievable.
     */
    fun chunk(text: String, targetChars: Int = 900, overlapChars: Int = 150): List<String> {
        val cleaned = text.replace("\r\n", "\n").replace(Regex("\n{3,}"), "\n\n").trim()
        if (cleaned.isEmpty()) return emptyList()
        if (cleaned.length <= targetChars) return listOf(cleaned)

        val paragraphs = cleaned.split(Regex("\n\\s*\n")).filter { it.isNotBlank() }
        val chunks = ArrayList<String>()
        val current = StringBuilder()

        fun flush() {
            if (current.isNotBlank()) {
                chunks.add(current.toString().trim())
                val tail = current.toString().takeLast(overlapChars.coerceAtMost(current.length / 3))
                current.clear()
                if (tail.isNotBlank()) current.append(tail).append(' ')
            }
        }

        for (paragraph in paragraphs) {
            if (paragraph.length > targetChars) {
                // hard split a single enormous paragraph
                var index = 0
                while (index < paragraph.length) {
                    val end = minOf(index + targetChars, paragraph.length)
                    current.append(paragraph, index, end).append(' ')
                    flush()
                    index = end
                }
            } else {
                if (current.length + paragraph.length + 1 > targetChars) flush()
                current.append(paragraph).append("\n\n")
            }
        }
        if (current.isNotBlank()) chunks.add(current.toString().trim())
        return chunks
    }

    /**
     * Scores chunks against a query and returns the best [topK].
     * [titles] maps a document id to its display title.
     */
    fun search(
        chunks: List<Pair<`in`.artham.fundamentals.data.DocChunk, String>>,
        titles: Map<Long, String>,
        query: String,
        topK: Int = 5
    ): List<RetrievedChunk> {
        val queryTerms = tokenize(query).distinct()
        if (queryTerms.isEmpty() || chunks.isEmpty()) return emptyList()

        val docs = chunks.map { (chunk, _) -> tokenize(chunk.body) }
        val total = docs.size.toDouble()
        val averageLength = docs.sumOf { it.size }.toDouble() / total
        if (averageLength <= 0.0) return emptyList()

        val documentFrequency = HashMap<String, Int>()
        for (tokens in docs) {
            for (term in queryTerms) {
                if (tokens.contains(term)) documentFrequency[term] = (documentFrequency[term] ?: 0) + 1
            }
        }

        val k1 = 1.5
        val b = 0.75
        val scored = ArrayList<RetrievedChunk>()
        chunks.forEachIndexed { index, (chunk, title) ->
            val tokens = docs[index]
            val length = tokens.size.toDouble()
            var score = 0.0
            val counts = tokens.groupingBy { it }.eachCount()
            for (term in queryTerms) {
                val tf = counts[term] ?: continue
                val df = documentFrequency[term] ?: 0
                val idf = ln(1.0 + (total - df + 0.5) / (df + 0.5))
                val numerator = tf * (k1 + 1)
                val denominator = tf + k1 * (1 - b + b * length / averageLength)
                score += idf * (numerator / denominator)
            }
            if (score > 0.0) {
                scored.add(
                    RetrievedChunk(
                        docId = chunk.docId,
                        docTitle = titles[chunk.docId] ?: title,
                        ord = chunk.ord,
                        body = chunk.body,
                        score = score
                    )
                )
            }
        }
        return scored.sortedByDescending { it.score }.take(topK)
    }
}

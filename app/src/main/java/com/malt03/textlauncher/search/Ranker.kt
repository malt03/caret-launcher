package com.malt03.textlauncher.search

import java.text.Collator
import java.util.Locale

/** Something that can be searched: the app label plus the user's search strings, all normalized. */
data class SearchCandidate<T>(
    val item: T,
    val key: String,
    val label: String,
    val normalizedTargets: List<String>,
)

/** Ranks candidates for a query (docs/spec.md 2.3). */
object Ranker {
    enum class Group { EXACT, LEARNED, PREFIX, PARTIAL }

    private val collator: Collator = Collator.getInstance(Locale.JAPANESE)

    val labelComparator: Comparator<String> = Comparator { a, b ->
        val c = collator.compare(a, b)
        if (c != 0) c else a.compareTo(b)
    }

    /**
     * @param normalizedQuery the query after [Normalizer.normalize]
     * @param scoreOf the learning score for (normalizedQuery, candidate key); 0 when none
     */
    fun <T> rank(
        normalizedQuery: String,
        candidates: List<SearchCandidate<T>>,
        scoreOf: (String) -> Double,
    ): List<SearchCandidate<T>> {
        if (normalizedQuery.isEmpty()) return emptyList()
        data class Scored<T>(val candidate: SearchCandidate<T>, val group: Group, val score: Double)

        val scored = candidates.mapNotNull { candidate ->
            val targets = candidate.normalizedTargets
            val matchGroup = when {
                targets.any { it == normalizedQuery } -> Group.EXACT
                targets.any { it.startsWith(normalizedQuery) } -> Group.PREFIX
                targets.any { it.contains(normalizedQuery) } -> Group.PARTIAL
                else -> null
            }
            val score = scoreOf(candidate.key)
            val group = when {
                matchGroup == Group.EXACT -> Group.EXACT
                score > 0.0 -> Group.LEARNED
                else -> matchGroup
            } ?: return@mapNotNull null
            Scored(candidate, group, score)
        }

        return scored.sortedWith(
            compareBy<Scored<T>> { it.group.ordinal }
                .thenByDescending { if (it.group == Group.EXACT || it.group == Group.LEARNED) it.score else 0.0 }
                .thenBy(labelComparator) { it.candidate.label }
        ).map { it.candidate }
    }
}

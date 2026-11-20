package com.malt03.textlauncher.data

import com.malt03.textlauncher.search.LearningRecord
import com.malt03.textlauncher.search.LearningScore
import com.malt03.textlauncher.search.Normalizer
import java.util.concurrent.Executor

/**
 * In-memory view of search strings and learning records, written through to [LauncherDatabase].
 * Accessed on the main thread; disk writes go to [ioExecutor].
 */
class SearchStore(
    private val db: LauncherDatabase,
    private val ioExecutor: Executor,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val terms = HashMap<String, List<String>>()
    private val learning = HashMap<String, HashMap<String, LearningRecord>>()

    /** Called on a background thread once, before the store is used. */
    fun loadBlocking() {
        val loadedTerms = db.loadSearchTerms()
        val loadedLearning = db.loadLearning()
        val now = clock()
        val pruned = mutableListOf<Pair<String, String>>()
        synchronized(this) {
            terms.clear(); terms.putAll(loadedTerms)
            learning.clear()
            for ((query, records) in loadedLearning) {
                val kept = HashMap<String, LearningRecord>()
                for ((appKey, record) in records) {
                    if (LearningScore.shouldPrune(record, now)) pruned += query to appKey else kept[appKey] = record
                }
                if (kept.isNotEmpty()) learning[query] = kept
            }
        }
        pruned.forEach { (query, appKey) -> db.deleteLearning(query, appKey) }
    }

    @Synchronized
    fun termsOf(appKey: String): List<String> = terms[appKey].orEmpty()

    @Synchronized
    fun setTerms(appKey: String, newTerms: List<String>) {
        val cleaned = newTerms.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (cleaned.isEmpty()) terms.remove(appKey) else terms[appKey] = cleaned
        ioExecutor.execute { db.replaceSearchTerms(appKey, cleaned) }
    }

    /** The current learning score for (normalized query, app). */
    @Synchronized
    fun scoreOf(normalizedQuery: String, appKey: String): Double {
        val record = learning[normalizedQuery]?.get(appKey) ?: return 0.0
        return LearningScore.current(record, clock())
    }

    @Synchronized
    fun recordLaunch(query: String, appKey: String) {
        val normalized = Normalizer.normalize(query)
        if (normalized.isEmpty()) return
        val records = learning.getOrPut(normalized) { HashMap() }
        val next = LearningScore.onLaunch(records[appKey], clock())
        records[appKey] = next
        ioExecutor.execute { db.putLearning(normalized, appKey, next) }
    }
}

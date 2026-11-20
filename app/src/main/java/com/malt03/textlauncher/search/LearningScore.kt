package com.malt03.textlauncher.search

import kotlin.math.pow

/** A decayed launch score for one (query, app) pair (docs/spec.md 3.2). */
data class LearningRecord(val score: Double, val updatedAtMillis: Long)

object LearningScore {
    const val DEFAULT_HALF_LIFE_MILLIS: Long = 14L * 24 * 60 * 60 * 1000
    const val PRUNE_THRESHOLD = 0.05

    /** The score as of [nowMillis]. */
    fun current(
        record: LearningRecord,
        nowMillis: Long,
        halfLifeMillis: Long = DEFAULT_HALF_LIFE_MILLIS,
    ): Double {
        val elapsed = (nowMillis - record.updatedAtMillis).coerceAtLeast(0L)
        return record.score * 0.5.pow(elapsed.toDouble() / halfLifeMillis)
    }

    /** The record after one more launch at [nowMillis]. */
    fun onLaunch(
        record: LearningRecord?,
        nowMillis: Long,
        halfLifeMillis: Long = DEFAULT_HALF_LIFE_MILLIS,
    ): LearningRecord {
        val decayed = record?.let { current(it, nowMillis, halfLifeMillis) } ?: 0.0
        return LearningRecord(decayed + 1.0, nowMillis)
    }

    fun shouldPrune(
        record: LearningRecord,
        nowMillis: Long,
        halfLifeMillis: Long = DEFAULT_HALF_LIFE_MILLIS,
    ): Boolean = current(record, nowMillis, halfLifeMillis) < PRUNE_THRESHOLD
}

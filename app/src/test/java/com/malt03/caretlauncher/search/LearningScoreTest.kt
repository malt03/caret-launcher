package com.malt03.caretlauncher.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LearningScoreTest {
    private val halfLife = LearningScore.DEFAULT_HALF_LIFE_MILLIS

    @Test
    fun firstLaunchScoresOne() {
        val record = LearningScore.onLaunch(null, nowMillis = 1_000)
        assertEquals(1.0, record.score, 1e-9)
        assertEquals(1_000, record.updatedAtMillis)
    }

    @Test
    fun scoreHalvesAfterHalfLife() {
        val record = LearningRecord(score = 4.0, updatedAtMillis = 0)
        assertEquals(2.0, LearningScore.current(record, halfLife), 1e-9)
        assertEquals(1.0, LearningScore.current(record, halfLife * 2), 1e-9)
    }

    @Test
    fun launchDecaysThenAddsOne() {
        val record = LearningRecord(score = 2.0, updatedAtMillis = 0)
        val next = LearningScore.onLaunch(record, nowMillis = halfLife)
        assertEquals(2.0, next.score, 1e-9)
        assertEquals(halfLife, next.updatedAtMillis)
    }

    @Test
    fun recentLaunchesBeatOldFrequentOnes() {
        var old: LearningRecord? = null
        repeat(5) { old = LearningScore.onLaunch(old, nowMillis = 0) }
        var recent: LearningRecord? = null
        repeat(2) { recent = LearningScore.onLaunch(recent, nowMillis = halfLife * 3) }
        val now = halfLife * 3
        assertTrue(LearningScore.current(recent!!, now) > LearningScore.current(old!!, now))
    }

    @Test
    fun pruneBelowThreshold() {
        val record = LearningRecord(score = 1.0, updatedAtMillis = 0)
        assertFalse(LearningScore.shouldPrune(record, halfLife * 4))
        assertTrue(LearningScore.shouldPrune(record, halfLife * 5))
    }

    @Test
    fun clockGoingBackwardsDoesNotIncreaseScore() {
        val record = LearningRecord(score = 1.0, updatedAtMillis = 1_000)
        assertEquals(1.0, LearningScore.current(record, 0), 1e-9)
    }
}

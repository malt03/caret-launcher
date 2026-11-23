package com.malt03.textlauncher.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RankerTest {
    private fun candidate(label: String, vararg terms: String) = SearchCandidate(
        item = label,
        key = label,
        label = label,
        normalizedTargets = (listOf(label) + terms).map(Normalizer::normalize),
    )

    private fun rank(query: String, candidates: List<SearchCandidate<String>>, scores: Map<String, Double> = emptyMap()) =
        Ranker.rank(Normalizer.normalize(query), candidates) { scores[it] ?: 0.0 }.map { it.item }

    @Test
    fun emptyQueryReturnsNothing() {
        assertTrue(rank("", listOf(candidate("Chrome"))).isEmpty())
    }

    @Test
    fun groupsAreOrderedExactPrefixPartial() {
        val candidates = listOf(
            candidate("Gmail"),      // partial "ma"
            candidate("Maps"),       // prefix "ma"
            candidate("Music", "ma"), // exact via search string
        )
        assertEquals(listOf("Music", "Maps", "Gmail"), rank("ma", candidates))
    }

    @Test
    fun learnedComesAfterExactButBeforePrefix() {
        val candidates = listOf(
            candidate("Gmail"),
            candidate("Maps"),
            candidate("Music", "ma"),
        )
        val result = rank("ma", candidates, mapOf("Gmail" to 3.0))
        assertEquals(listOf("Music", "Gmail", "Maps"), result)
    }

    @Test
    fun exactGroupIsSortedByScore() {
        val candidates = listOf(candidate("Alpha", "x"), candidate("Beta", "x"))
        assertEquals(listOf("Beta", "Alpha"), rank("x", candidates, mapOf("Beta" to 1.0)))
    }

    @Test
    fun learnedGroupIsSortedByScore() {
        val candidates = listOf(candidate("Slack"), candidate("Settings"), candidate("Spotify"))
        val result = rank("s", candidates, mapOf("Spotify" to 1.0, "Settings" to 2.0))
        assertEquals(listOf("Settings", "Spotify", "Slack"), result)
    }

    @Test
    fun prefixAndPartialAreSortedByLabel() {
        val candidates = listOf(candidate("Cb"), candidate("Ca"), candidate("xCb"), candidate("xCa"))
        assertEquals(listOf("Ca", "Cb", "xCa", "xCb"), rank("c", candidates))
    }

    @Test
    fun namesStartingWithALetterOrDigitComeFirst() {
        // LabelComparator, as in the all apps list.
        val candidates = listOf(candidate("_Cam"), candidate("Acme"))
        assertEquals(listOf("Acme", "_Cam"), rank("c", candidates))
    }

    @Test
    fun katakanaLabelMatchesHiraganaQuery() {
        assertEquals(listOf("カメラ"), rank("かめ", listOf(candidate("カメラ"), candidate("設定"))))
    }

    @Test
    fun registeredHiraganaMatchesKanjiApp() {
        assertEquals(listOf("設定"), rank("せってい", listOf(candidate("設定", "せってい"), candidate("カメラ"))))
    }

    @Test
    fun nonMatchingWithoutScoreIsExcluded() {
        assertEquals(emptyList<String>(), rank("zzz", listOf(candidate("Chrome"))))
    }
}

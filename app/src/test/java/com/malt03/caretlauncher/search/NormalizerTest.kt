package com.malt03.caretlauncher.search

import org.junit.Assert.assertEquals
import org.junit.Test

class NormalizerTest {
    @Test
    fun fullWidthAsciiBecomesHalfWidthLowercase() {
        assertEquals("chrome", Normalizer.normalize("ＣＨＲＯＭＥ"))
    }

    @Test
    fun upperCaseBecomesLowerCase() {
        assertEquals("youtube", Normalizer.normalize("YouTube"))
    }

    @Test
    fun katakanaBecomesHiragana() {
        assertEquals("かめら", Normalizer.normalize("カメラ"))
    }

    @Test
    fun halfWidthKanaBecomesHiragana() {
        assertEquals("かめら", Normalizer.normalize("ｶﾒﾗ"))
    }

    @Test
    fun voicedKatakanaBecomesVoicedHiragana() {
        assertEquals("ぐーぐる", Normalizer.normalize("グーグル"))
    }

    @Test
    fun prolongedSoundMarkIsKept() {
        assertEquals("めーる", Normalizer.normalize("メール"))
    }

    @Test
    fun kanjiAndHiraganaAreUnchanged() {
        assertEquals("設定せってい", Normalizer.normalize("設定せってい"))
    }
}

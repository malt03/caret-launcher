package com.malt03.textlauncher.search

import org.junit.Assert.assertEquals
import org.junit.Test

class DrawerIndexTest {
    @Test
    fun latinLabelsUseUppercaseLetter() {
        assertEquals("C", DrawerIndex.sectionOf("chrome"))
        assertEquals("Y", DrawerIndex.sectionOf("YouTube"))
    }

    @Test
    fun kanaLabelsUseRowHead() {
        assertEquals("か", DrawerIndex.sectionOf("カメラ"))
        assertEquals("か", DrawerIndex.sectionOf("ギャラリー"))
        assertEquals("は", DrawerIndex.sectionOf("ぷよぷよ"))
        assertEquals("わ", DrawerIndex.sectionOf("ワン"))
    }

    @Test
    fun kanjiDigitsAndSymbolsGoToOther() {
        assertEquals(DrawerIndex.OTHER, DrawerIndex.sectionOf("設定"))
        assertEquals(DrawerIndex.OTHER, DrawerIndex.sectionOf("1Password"))
        assertEquals(DrawerIndex.OTHER, DrawerIndex.sectionOf(""))
    }
}

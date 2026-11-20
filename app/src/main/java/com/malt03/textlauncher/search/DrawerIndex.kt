package com.malt03.textlauncher.search

/** Section headers for the all-apps list (docs/spec.md 6.4): A-Z, あかさ..., and others. */
object DrawerIndex {
    const val OTHER = "#"

    private val kanaRows: List<Pair<String, String>> = listOf(
        "あ" to "ぁあぃいぅうぇえぉおゔ",
        "か" to "かがきぎくぐけげこごゕゖ",
        "さ" to "さざしじすずせぜそぞ",
        "た" to "ただちぢっつづてでとど",
        "な" to "なにぬねの",
        "は" to "はばぱひびぴふぶぷへべぺほぼぽ",
        "ま" to "まみむめも",
        "や" to "ゃやゅゆょよ",
        "ら" to "らりるれろ",
        "わ" to "ゎわゐゑをん",
    )

    /** The section for a label. Labels whose reading is unknown (kanji, symbols) go to [OTHER]. */
    fun sectionOf(label: String): String {
        val first = Normalizer.normalize(label.trim()).firstOrNull() ?: return OTHER
        if (first in 'a'..'z') return first.uppercaseChar().toString()
        for ((head, members) in kanaRows) {
            if (members.indexOf(first) >= 0) return head
        }
        return OTHER
    }
}

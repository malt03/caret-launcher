package com.malt03.caretlauncher.search

import java.text.Normalizer as JavaNormalizer
import java.util.Locale

/**
 * Normalizes query strings and match targets (docs/spec.md 2.2).
 *
 * 1. NFKC (full-width ASCII -> half-width, half-width kana -> full-width)
 * 2. Lower case
 * 3. Katakana (U+30A1..U+30F6) -> Hiragana
 */
object Normalizer {
    private const val KATAKANA_START = 0x30A1
    private const val KATAKANA_END = 0x30F6
    private const val KATAKANA_TO_HIRAGANA = 0x60

    fun normalize(text: String): String {
        val nfkc = JavaNormalizer.normalize(text, JavaNormalizer.Form.NFKC).lowercase(Locale.ROOT)
        val builder = StringBuilder(nfkc.length)
        for (ch in nfkc) {
            val code = ch.code
            if (code in KATAKANA_START..KATAKANA_END) {
                builder.append((code - KATAKANA_TO_HIRAGANA).toChar())
            } else {
                builder.append(ch)
            }
        }
        return builder.toString()
    }
}

package com.ses.asistan.beceri

import java.util.Locale

/**
 * Sunulan seçeneklerden sesle seçme ("ikinci", "sonuncusu", adıyla). Harita `RuleParser.choice` + `byName`'den
 * taşındı; Android'e bağlı değil.
 */
object Secim {
    private val TR = Locale("tr")
    private val AYRAC = Regex("[^\\p{L}\\p{N}]+")
    private val ORDINALS = listOf(
        listOf("birinci", "ilk", "ilki", "bir", "1"), listOf("ikinci", "iki", "2"), listOf("üçüncü", "üç", "3"),
        listOf("dördüncü", "dört", "4"), listOf("beşinci", "beş", "5"), listOf("altıncı", "altı", "6"),
        listOf("yedinci", "yedi", "7"), listOf("sekizinci", "sekiz", "8"), listOf("dokuzuncu", "dokuz", "9"),
        listOf("onuncu", "on", "10"),
    )

    /** 0 tabanlı seçenek ya da null. */
    fun sec(metin: String, secenekler: List<String>): Int? {
        if (secenekler.isEmpty()) return null
        val t = metin.lowercase(TR)
        val words = t.split(AYRAC).filter { it.isNotEmpty() }
        sira(words, secenekler.size)?.let { return it }
        return adiyla(t, secenekler)
    }

    private fun sira(words: List<String>, n: Int): Int? {
        for (w0 in words) {
            val w = w0.removeSuffix("si").removeSuffix("sı").removeSuffix("sü").removeSuffix("su")
            if (w == "son" || w == "sonuncu" || w0 == "sonuncusu") return n - 1
            ORDINALS.forEachIndexed { i, forms -> if (i < n && (w in forms || w0 in forms)) return i }
        }
        return null
    }

    /** Seçeneği adıyla bul: tüm seçeneklerde ortak olan kelimeler ayırt edici sayılmaz. */
    private fun adiyla(t: String, options: List<String>): Int? {
        val words = options.map { significant(it).toSet() }
        val common = words.reduce { x, y -> x intersect y }
        val scores = words.map { ws -> (ws - common).count { t.contains(it) } }
        val best = scores.maxOrNull() ?: 0
        return if (best > 0 && scores.count { it == best } == 1) scores.indexOf(best) else null
    }

    private fun significant(label: String) = label.lowercase(TR).split(AYRAC).filter { it.length >= 4 }

    /** Seçeneklerin sesli okunuşu: ilk [enCok] tanesi numarasıyla. */
    fun oku(baslik: String, secenekler: List<String>, enCok: Int = 5): String =
        "$baslik: " + secenekler.take(enCok).mapIndexed { i, s -> "${i + 1}, $s" }.joinToString(". ") + ". Hangisi?"
}

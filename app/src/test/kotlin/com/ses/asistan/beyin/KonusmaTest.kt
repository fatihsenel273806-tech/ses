package com.ses.asistan.beyin

import org.junit.Assert.assertEquals
import org.junit.Test

/** Harita `ConversationTest`'ten taşındı. */
class KonusmaTest {
    private var t = 0L
    private val k = Konusma { t }

    @Test fun cevapSonSozeEklenir() {
        k.ekle("yolda eczane", "")
        k.cevap("Üç eczane buldum. Hangisi?")
        assertEquals("Üç eczane buldum. Hangisi?", k.gecmis().single().cevap)
    }

    @Test fun enCokAltiSozTutulur() {
        repeat(9) { k.ekle("söz $it", "cevap") }
        val h = k.gecmis()
        assertEquals(Konusma.EN_COK_TUR, h.size)
        assertEquals("söz 3", h.first().soz)
    }

    @Test fun uzunSessizliktenSonraUnutulur() {
        k.ekle("Kızılay'a git", "Tamam")
        t += Konusma.BOSTA_MS + 1
        assertEquals(0, k.gecmis().size)
    }
}

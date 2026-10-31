package com.ses.asistan.beyin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Harita `GeminiPoolTest`'ten taşındı (Harita'nın yerel kota sayacı asistanda yok). */
class GeminiHavuzuTest {
    private val anahtarlar = listOf("AIzaKEY_AAAAAA", "AIzaKEY_BBBBBB", "AIzaKEY_CCCCCC")
    private val modeller = listOf("m1", "m2", "m3")

    private fun havuz(): GeminiHavuzu { var depo = ""; return GeminiHavuzu({ depo }, { depo = it }) }

    @Test
    fun ilkModelTumAnahtarlardaDenenirSonraIkinci() {
        assertEquals(
            listOf("AAAAAA|m1", "BBBBBB|m1", "CCCCCC|m1", "AAAAAA|m2"),
            GeminiHavuzu.sira(anahtarlar, modeller).take(4).map { (k, m) -> GeminiHavuzu.yuva(k, m) },
        )
    }

    @Test
    fun hakkiBitenCiftlerSifirlanincayaKadarAtlanir() {
        val h = havuz()
        val denenen = ArrayList<String>()
        val gunluk = ApiHatasi(429, "Quota exceeded for metric: GenerateRequestsPerDayPerProjectPerModel-FreeTier")
        val simdi = 1_700_000_000_000L
        val r = h.calistir(anahtarlar, modeller, simdi) { k, m ->
            denenen.add(GeminiHavuzu.yuva(k, m))
            if (m == "m1") throw gunluk else "tamam $m"
        }
        assertEquals("tamam m2", r)
        assertEquals(listOf("AAAAAA|m1", "BBBBBB|m1", "CCCCCC|m1", "AAAAAA|m2"), denenen)
        // sonraki istek dolu çiftleri hiç denemez
        denenen.clear()
        h.calistir(anahtarlar, modeller, simdi + 60_000) { k, m -> denenen.add(GeminiHavuzu.yuva(k, m)); "tamam" }
        assertEquals(listOf("AAAAAA|m2"), denenen)
        // Pasifik gece yarısından sonra 1. modele dönülür
        denenen.clear()
        h.calistir(anahtarlar, modeller, GeminiHavuzu.pasifikGeceYarisi(simdi) + 1) { k, m -> denenen.add(GeminiHavuzu.yuva(k, m)); "tamam" }
        assertEquals(listOf("AAAAAA|m1"), denenen)
    }

    @Test
    fun dakikalikSinirKisaBeklerBaskaHataDurur() {
        val simdi = 1_700_000_000_000L
        val bitis = GeminiHavuzu.bekletmeBitisi(ApiHatasi(429, "Resource exhausted. Please retry in 39.4s."), simdi)!!
        assertTrue(bitis - simdi in 39_000..45_000)
        assertEquals(null, GeminiHavuzu.bekletmeBitisi(ApiHatasi(400, "Invalid JSON payload"), simdi))
        val h = havuz()
        val e = runCatching { h.calistir(anahtarlar.take(1), modeller.take(1), simdi) { _, _ -> throw ApiHatasi(429, "per day") } }.exceptionOrNull()
        assertTrue(e is ApiHatasi && e.kod == 429)
    }

    @Test
    fun anahtarListesiBolunur() {
        assertEquals(listOf("k1", "k2", "k3"), GeminiHavuzu.bol(" k1, k2\nk3;k1 "))
    }
}

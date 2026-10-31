package com.ses.asistan.beceri

import com.ses.asistan.beceri.KanallarSozleri.Eylem
import com.ses.asistan.beceri.KanallarSozleri.Komut
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Harita `MediaWordsTest`'ten taşındı (RuleParser.instant yerine KanallarSozleri.parse); yeşil kalmalı. */
class KanallarSozleriTest {
    private fun p(s: String) = KanallarSozleri.parse(s)

    @Test fun seek() {
        assertEquals(Komut.Sar(-30_000, false), p("30 saniye geri sar"))
        assertEquals(Komut.Sar(60_000, false), p("bir dakika ileri"))
        assertEquals(Komut.Sar(-10_000, false), p("geri sar"))
        assertEquals(Komut.Sar(0, true), p("videoyu baştan başlat"))
        assertEquals(Komut.Sar(5 * 60_000, true), p("5. dakikaya git"))
    }

    @Test fun listsAndWhat() {
        assertEquals(Komut.Medya(Eylem.WHAT), p("ne çalıyor"))
        assertEquals(Komut.Medya(Eylem.RECENT), p("son izlenenleri aç"))
        assertEquals(Komut.Medya(Eylem.MAP_LIST), p("harita listemi aç"))
        assertEquals(Komut.Medya(Eylem.PLAYLISTS), p("oynatma listesini aç"))
        assertEquals(Komut.Medya(Eylem.PAUSE), p("videoyu durdur"))
    }

    @Test fun findByName() {
        assertEquals(Komut.Bul("belgesel"), p("Kanallarım'da belgesel aç"))
        assertEquals(Komut.Bul("osmanlı tarihi"), p("osmanlı tarihi videosunu oynat"))
        assertNull(p("eczaneye git"))
        assertEquals(Komut.Medya(Eylem.NEXT), p("sonraki videoyu aç"))
    }

    @Test fun bestTitle() {
        val titles = listOf("Harita listem", "Osmanlı Tarihi 3. Bölüm", "Uzay belgeseli", "Yemek tarifleri")
        assertEquals(2, KanallarSozleri.best("belgesel", titles))
        assertEquals(1, KanallarSozleri.best("osmanlı tarihi", titles))
        assertEquals(-1, KanallarSozleri.best("futbol", titles))
    }

    // ---- asistan

    @Test fun videoNotu() {
        assertEquals(Komut.Not(""), p("videoya not al"))
        assertEquals(Komut.Not("Burayı tekrar izle"), p("Videoya not al: Burayı tekrar izle."))
        // notun içindeki "durdur" komut sanılmaz
        assertEquals(Komut.Not("müziği durdur bölümü önemli"), p("video notu müziği durdur bölümü önemli"))
    }

    @Test fun genelKomutlarDicioIleOrtak() {
        assertTrue(KanallarSozleri.genel(p("durdur")!!))
        assertTrue(KanallarSozleri.genel(p("sonraki parça")!!))
        assertFalse(KanallarSozleri.genel(p("geri sar")!!))
        assertFalse(KanallarSozleri.genel(p("kaldığım yerden devam")!!))
        // "kaydı durdur" medya değil
        assertNull(p("kaydı durdur"))
    }

    @Test fun calanCumlesi() {
        assertEquals("Şu an çalan bir şey yok.", KanallarSozleri.calanCumlesi(null, null, 0))
        assertEquals("Uzay belgeseli, Bilim Kanalı, 12. dakikada", KanallarSozleri.calanCumlesi("Uzay belgeseli", "Bilim Kanalı", 12 * 60_000 + 5))
        assertEquals("Uzay belgeseli", KanallarSozleri.calanCumlesi("Uzay belgeseli", "", 30_000))
    }
}

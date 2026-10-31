package com.ses.asistan.beceri

import com.ses.asistan.beceri.NotlarSozleri.Komut
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotlarSozleriTest {
    private fun p(s: String) = NotlarSozleri.parse(s)

    @Test fun notAl() {
        // Harita NOTE kuralı birebir: ardındaki metin nottur, büyük harfi korunur
        assertEquals(Komut.NotAl("Yarın Ahmet'i ara"), p("not al Yarın Ahmet'i ara."))
        assertEquals(Komut.NotAl("süt bitti"), p("Not et: süt bitti"))
        assertEquals(Komut.NotAl("ekmek"), p("nota ekle ekmek"))
        assertEquals(Komut.NotAl("toplantı saat üçte"), p("not alır mısın toplantı saat üçte"))
        assertEquals(Komut.NotAl(""), p("not al"))
        // "videoya not al" Kanallarım'ın
        assertNull(p("videoya not al güzel sahne"))
        assertNull(p("notalar"))
    }

    @Test fun sonNot() {
        assertEquals(Komut.SonuOku, p("son notumu oku"))
        assertEquals(Komut.SonuOku, p("son notum ne"))
        assertEquals(Komut.SonuOku, p("en son not neydi"))
        assertEquals(Komut.SonuOku, p("son notumu okur musun"))
        assertEquals(Komut.Son(3), p("son notlarım"))
        assertEquals(Komut.Son(3), p("son notlarımı oku"))
        assertEquals(Komut.Son(5), p("son beş notum"))
        assertEquals(Komut.Son(2), p("son 2 notu söyle"))
    }

    @Test fun ara() {
        assertEquals(Komut.Ara(listOf("ankara"), "ankara'yı"), p("notlarımda Ankara'yı ara"))
        assertEquals(Komut.Ara(listOf("toplantı"), "toplantıyı"), p("notlarda toplantıyı bul"))
        assertEquals(Komut.Ara(listOf("doktor"), "doktor"), p("notlarımda doktor var mı"))
        assertEquals(Komut.Ara(listOf("kira", "sözleşme"), "kira sözleşmesi"), p("kira sözleşmesi ile ilgili notları bul"))
        assertEquals(Komut.Ara(listOf("market"), "market"), p("market notunu oku"))
        assertEquals(Komut.Ara(listOf("bayram"), "bayram"), p("bayram geçen notlar"))
        assertNull(p("saat kaç"))
        assertNull(p("hava nasıl"))
    }

    @Test fun okunus() {
        assertEquals("Market. süt, ekmek", NotlarSozleri.notCumlesi("Market", "süt, ekmek"))
        assertEquals("süt", NotlarSozleri.notCumlesi("", "süt"))
        assertEquals("bir iki üç…", NotlarSozleri.notCumlesi("", "bir iki üç dört", enCok = 12))
        assertEquals("a b c d e f…", NotlarSozleri.ad("", "a b c d e f g"))
        assertEquals("Başlık", NotlarSozleri.ad("Başlık", "metin"))
        assertEquals("üç", NotlarSozleri.adetSozu(3))
    }
}

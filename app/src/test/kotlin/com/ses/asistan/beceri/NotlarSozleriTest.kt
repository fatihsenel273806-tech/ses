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
        assertNull(p("notalar"))
    }

    /** Telefon (2026-11-01): bunlar anlaşılmıyordu. */
    @Test fun serbestSoyleyis() {
        assertEquals(Komut.NotAl("deneme"), p("Not alır mısın deneme diye"))
        assertEquals(Komut.NotAl("deneme"), p("Yeni bir not aç içerisinde deneme yaz"))
        assertEquals(Komut.NotAl("süt al"), p("süt al diye not al"))
        assertEquals(Komut.NotAl("yarın toplantı var"), p("yarın toplantı var not eder misin"))
        assertEquals(Komut.NotAl("ekmek"), p("şunu not al ekmek"))
        assertEquals(Komut.NotAl("market listesi"), p("bir not oluştur market listesi"))
        assertEquals(Komut.NotAl(""), p("yeni not aç"))
        assertEquals(Komut.NotAl("kira"), p("notuma ekle kira"))
        assertNull(p("not defteri nerede"))
    }

    @Test fun ekler() {
        assertEquals(Komut.NotAl("", ekran = true), p("ekran görüntüsü al"))
        assertEquals(Komut.NotAl("", ekran = true), p("Ekran görüntüsü alıp nota ekle"))
        assertEquals(Komut.NotAl("", ekran = true), p("ekran görüntüsünü al ve not olarak kaydet"))
        assertEquals(Komut.NotAl("deneme", ekran = true), p("not al deneme ve ekran görüntüsü ekle"))
        assertEquals(Komut.NotAl("bunu sonra oku", ekran = true), p("SS al not al bunu sonra oku"))
        assertEquals(Komut.NotAl("park yeri", konum = true), p("not al park yeri konumu da ekle"))
        assertEquals(Komut.NotAl("", konum = true), p("konumumu not al"))
        assertEquals(Komut.NotAl("güzel yer", ekran = true, konum = true), p("not al güzel yer ekran görüntüsü ve konum ekle"))
        // konum tek başına not değil; "ekranı kapat" ekran görüntüsü değil
        assertNull(p("konumum ne"))
        assertNull(p("ekranı kapat"))
        // "konum" kelimesi notun içindeyse ek sayılmaz
        assertEquals(Komut.NotAl("yeni konum toplantısı"), p("not al yeni konum toplantısı"))
    }

    @Test fun videoNotu() {
        assertEquals(Komut.NotAl("güzel sahne", video = true), p("videoya not al güzel sahne"))
        assertEquals(Komut.NotAl("", video = true), p("videoya not al"))
        assertEquals(Komut.NotAl("Burayı tekrar izle", video = true), p("Videoya not al: Burayı tekrar izle."))
        // notun içindeki "durdur" komut sanılmaz
        assertEquals(Komut.NotAl("müziği durdur bölümü önemli", video = true), p("video notu müziği durdur bölümü önemli"))
        assertEquals(Komut.NotAl("önemli", video = true), p("video notu al önemli"))
        assertEquals(Komut.NotAl("önemli", ekran = true, video = true), p("video notuna ekle önemli ekran görüntüsü de ekle"))
        assertEquals(Komut.NotAl("", ekran = true, video = true), p("ekran görüntüsü al video notuna ekle"))
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

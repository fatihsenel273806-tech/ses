package com.ses.asistan.ajan

import com.ses.asistan.ajan.EkranSozleri.Komut
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EkranSozleriTest {
    private fun p(s: String) = EkranSozleri.parse(s)

    @Test fun komutlar() {
        assertEquals(Komut.Kaydir("asagi"), p("aşağı kaydır"))
        assertEquals(Komut.Kaydir("yukari"), p("sayfayı yukarı kaydırır mısın"))
        assertEquals(Komut.Kaydir("saga"), p("sağa kaydır"))
        assertEquals(Komut.Geri, p("geri git"))
        assertEquals(Komut.Geri, p("geri"))
        assertEquals(Komut.Dokun("kaydet"), p("Kaydet'e dokun"))
        assertEquals(Komut.Dokun("ayarlar"), p("ayarlar düğmesine bas"))
        assertEquals(Komut.Dokun("favoriler"), p("favoriler sekmesine tıkla"))
        assertEquals(Komut.Yaz("arama", "kızılay"), p("arama kutusuna Kızılay yaz"))
        assertEquals(Komut.Oku, p("ekranda ne yazıyor"))
    }

    @Test fun baskaBecerilerinSozleri() {
        assertNull(p("geri sar"))
        assertNull(p("not al"))
        assertNull(p("haritayı aç"))
        assertNull(p("saat kaç"))
    }
}

package com.ses.asistan.beceri

import com.ses.asistan.beceri.DosyalarSozleri.Komut
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DosyalarSozleriTest {
    private fun p(s: String) = DosyalarSozleri.parse(s)

    @Test fun ac() {
        assertEquals(Komut.Ac("kira sözleşmesi"), p("Kira sözleşmesi dosyasını aç"))
        assertEquals(Komut.Ac("fatura"), p("fatura pdf'ini aç"))
        assertEquals(Komut.Ac("ahmet"), p("Ahmet'in belgesini açar mısın"))
        assertEquals(Komut.Ac("rapor"), p("rapor dosyasını dosya okuyucuda aç"))
        assertEquals(Komut.Ac("bütçe"), p("bütçe excel dosyasını göster"))
    }

    @Test fun sonDosya() {
        assertEquals(Komut.Ac(""), p("son dosyayı aç"))
        assertEquals(Komut.Ac(""), p("en son açtığım dosyayı aç"))
        assertEquals(Komut.Ac(""), p("son belgeyi açar mısın"))
    }

    @Test fun sesliOku() {
        assertEquals(Komut.SesliOku(""), p("sesli oku"))
        assertEquals(Komut.SesliOku(""), p("bu dosyayı sesli oku"))
        assertEquals(Komut.SesliOku(""), p("belgeyi oku"))
        assertEquals(Komut.SesliOku(""), p("son dosyayı oku"))
        assertEquals(Komut.SesliOku("sözleşme"), p("sözleşme dosyasını sesli oku"))
        assertEquals(Komut.Durdur, p("okumayı durdur"))
        assertEquals(Komut.Durdur, p("sesli okumayı kes"))
        assertEquals(Komut.Durdur, p("okumayı durdurur musun"))
    }

    @Test fun ozetle() {
        assertEquals(Komut.Ozetle(""), p("özetle"))
        assertEquals(Komut.Ozetle(""), p("bu belgeyi özetler misin"))
        assertEquals(Komut.Ozetle(""), p("dosyanın özetini çıkar"))
        assertEquals(Komut.Ozetle(""), p("son dosyayı özetle"))
        assertEquals(Komut.Ozetle("toplantı tutanağı"), p("toplantı tutanağı dosyasını özetle"))
    }

    @Test fun baskaBecerilerinCumleleri() {
        assertNull(p("haritayı aç"))
        assertNull(p("son notumu oku"))
        assertNull(p("not al dosyayı yarın gönder"))
        assertNull(p("geri sar"))
        assertNull(p("durdur"))
        assertNull(p("bu dosyayı aç"))
    }
}

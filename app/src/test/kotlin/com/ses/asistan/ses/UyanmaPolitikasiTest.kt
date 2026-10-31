package com.ses.asistan.ses

import com.ses.asistan.ses.UyanmaPolitikasi.Durum
import com.ses.asistan.ses.UyanmaPolitikasi.Kip
import com.ses.asistan.ses.UyanmaPolitikasi.dinlemeli
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UyanmaPolitikasiTest {
    @Test fun aractaVeElle() {
        assertFalse(dinlemeli(Durum()))
        assertTrue(dinlemeli(Durum(elleAcik = true)))
        assertTrue(dinlemeli(Durum(navigasyonda = true)))
        assertTrue(dinlemeli(Durum(uygulamaIstiyor = true)))
        assertTrue(dinlemeli(Durum(bluetoothAdlari = listOf("Renault R-Link"))))
    }

    @Test fun aracAdiVerildiyseYalnizO() {
        assertFalse(dinlemeli(Durum(bluetoothAdlari = listOf("Redmi Buds 5"), aracAdi = "r-link")))
        assertTrue(dinlemeli(Durum(bluetoothAdlari = listOf("Redmi Buds 5", "Renault R-Link"), aracAdi = "r-link")))
    }

    @Test fun gorusmedeHicDinlenmez() {
        for (k in Kip.values()) assertFalse(dinlemeli(Durum(kip = k, elleAcik = true, gorusmede = true)))
    }

    @Test fun digerKipler() {
        assertTrue(dinlemeli(Durum(kip = Kip.HER_ZAMAN)))
        assertFalse(dinlemeli(Durum(kip = Kip.YALNIZ_ELLE, navigasyonda = true, uygulamaIstiyor = true, bluetoothAdlari = listOf("Araç"))))
        assertTrue(dinlemeli(Durum(kip = Kip.YALNIZ_ELLE, elleAcik = true)))
    }

    @Test fun asistanEkrandayken() {
        // asistanı açmak elle açmak sayılır: araçta değilken de "Hey Asistan" duyulsun
        assertTrue(dinlemeli(Durum(asistanEkranda = true)))
        assertTrue(dinlemeli(Durum(kip = Kip.YALNIZ_ELLE, asistanEkranda = true)))
        assertFalse(dinlemeli(Durum(asistanEkranda = true, gorusmede = true)))
    }

    @Test fun nedenSoylenir() {
        assertTrue(UyanmaPolitikasi.neden(Durum()).startsWith("Dinlenmiyor: araçta değil"))
        assertTrue(UyanmaPolitikasi.neden(Durum(bluetoothAdlari = listOf("Buds"), aracAdi = "r-link")).contains("eşleşmiyor"))
        assertTrue(UyanmaPolitikasi.neden(Durum(bluetoothAdlari = listOf("R-Link"))).startsWith("Dinleniyor"))
    }
}

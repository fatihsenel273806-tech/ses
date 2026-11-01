package com.ses.asistan.ses

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SesEsigiTest {
    private val boy = 1600
    private fun kare(genlik: Int, isaret: Short = 0) = ShortArray(boy) { i -> if (i == 0 && isaret != 0.toShort()) isaret else (if (i % 2 == 0) genlik else -genlik).toShort() }

    @Test fun sessizlikteTaniyiciCalismaz() {
        val e = SesEsigi(boy)
        var verilen = 0
        repeat(100) { e.gec(kare(30)) { verilen++ } }
        assertEquals(0, verilen)
        assertEquals(100L, e.toplam)
    }

    @Test fun sesGelinceOnBellekVeSurmeVerilir() {
        val e = SesEsigi(boy)
        val sira = ArrayList<Short>()
        // sessiz kareler ilk örneğe işaret koyar: hangisinin verildiği izlenir
        for (i in 1..10) e.gec(kare(30, i.toShort())) { sira += it[0] }
        assertTrue(sira.isEmpty())
        e.gec(kare(3000, 99)) { sira += it[0] }
        // son üç sessiz kare (8, 9, 10) eskiden yeniye, sonra sesli kare
        assertEquals(listOf<Short>(8, 9, 10, 99), sira)
        assertTrue(e.acik)
        // ardından SURME_KARE sessiz kare daha verilir, sonuncusunda kapanır
        var kapandi = false
        var sayi = 0
        repeat(SesEsigi.SURME_KARE) { kapandi = e.gec(kare(30)) { sayi++ } }
        assertEquals(SesEsigi.SURME_KARE, sayi)
        assertTrue(kapandi)
        assertFalse(e.acik)
        assertFalse(e.gec(kare(30)) { sayi++ })
        assertEquals(SesEsigi.SURME_KARE, sayi)
    }

    @Test fun surekliSesteKapiAcikKalir() {
        // video/araç: taban yükselse de TAVAN'ı geçen her kare verilir
        val e = SesEsigi(boy)
        var verilen = 0
        repeat(200) { e.gec(kare(900)) { verilen++ } }
        assertEquals(200, verilen)
    }

    @Test fun gurultuTabaniOgrenilir() {
        // hafif sürekli uğultu (EN_AZ'ın altında) kapıyı açmaz, üstündeki konuşma açar
        val e = SesEsigi(boy)
        var verilen = 0
        repeat(50) { e.gec(kare(120)) { verilen++ } }
        assertEquals(0, verilen)
        e.gec(kare(400)) { verilen++ }
        assertTrue(verilen > 0)
    }
}

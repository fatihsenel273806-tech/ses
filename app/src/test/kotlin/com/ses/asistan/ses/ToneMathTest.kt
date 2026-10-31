package com.ses.asistan.ses

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ToneMathTest {
    @Test fun uzunlukNotalarinToplami() {
        for (k in Tones.Kind.values()) {
            val ms = ToneMath.notes(k).sumOf { it.second + it.third }
            assertEquals((ms * 22_050 / 1000).toDouble(), ToneMath.pcm(k, 22_050).size.toDouble(), 22_050 / 100.0)
        }
    }

    @Test fun uyariSesiBirBucukSaniyeyiGecmez() {
        val ms = ToneMath.notes(Tones.Kind.ALERT).sumOf { it.second + it.third }
        assertTrue(ms in 800..1500)
    }

    @Test fun tikirtisizBaslarVeBiter() {
        val pcm = ToneMath.pcm(Tones.Kind.LISTEN, 22_050)
        assertTrue(kotlin.math.abs(pcm[0].toInt()) < 200)
        assertTrue(kotlin.math.abs(pcm[pcm.size - 1].toInt()) < 200)
    }

    @Test fun uyariSesiKisik() {
        // en yüksek örnek tam ölçeğin dörtte birini geçmez (eskiden %60'tı, sürücüyü irkiltiyordu)
        val peak = ToneMath.pcm(Tones.Kind.ALERT, 22_050).maxOf { kotlin.math.abs(it.toInt()) }
        assertTrue(peak in 1000..Short.MAX_VALUE / 4)
    }

    @Test fun dinlemeSesiYumusak() {
        // "Hey Harita" sonrası işaret: tam ölçeğin beşte birini geçmez, 1 kHz altında
        val peak = ToneMath.pcm(Tones.Kind.LISTEN, 22_050).maxOf { kotlin.math.abs(it.toInt()) }
        assertTrue(peak in 1000..Short.MAX_VALUE / 5)
        assertTrue(ToneMath.notes(Tones.Kind.LISTEN).all { it.first < 1000 })
    }
}

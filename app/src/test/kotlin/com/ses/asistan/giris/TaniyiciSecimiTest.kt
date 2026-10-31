package com.ses.asistan.giris

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TaniyiciSecimiTest {
    private val kendi = "com.ses.asistan"
    private val adaylar = listOf(
        "com.ses.asistan/org.stypox.dicio.io.input.stt_service.SttService",
        "com.google.android.googlequicksearchbox/com.google.android.voicesearch.serviceapi.GoogleRecognitionService",
        "com.google.android.tts/com.google.android.apps.speech.tts.googletts.service.GoogleTTSRecognitionService",
    )

    @Test fun varsayilanBaskasininsaDokunulmaz() {
        var soruldu = false
        assertNull(TaniyiciSecimi.sec(adaylar[2], kendi) { soruldu = true; adaylar })
        assertEquals(false, soruldu)
    }

    @Test fun varsayilanKendisiyseGoogleSecilir() {
        assertEquals(adaylar[2], TaniyiciSecimi.sec(adaylar[0], kendi) { adaylar })
        assertEquals(adaylar[1], TaniyiciSecimi.sec(null, kendi) { adaylar.dropLast(1) })
    }

    @Test fun googleYoksaBaskaBiri_hicYoksaVarsayilan() {
        assertEquals("x.y/Z", TaniyiciSecimi.sec("", kendi) { listOf(adaylar[0], "x.y/Z") })
        assertNull(TaniyiciSecimi.sec(adaylar[0], kendi) { listOf(adaylar[0]) })
    }
}

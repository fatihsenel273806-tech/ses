package com.ses.asistan.yonlendirme

import com.ses.asistan.yonlendirme.Yonlendirme.Aday
import com.ses.asistan.yonlendirme.Yonlendirme.Devir
import com.ses.asistan.yonlendirme.Yonlendirme.Karar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class YonlendirmeTest {
    private val harita = Aday("com.harita.ankara", "harita", Devir.NAVIGASYONDA)
    private val notlar = Aday("dev.kaimau.notlar", "notlar", Devir.HAYIR)

    @Test fun uyanmaAdiylaDogrudan() {
        assertEquals(Karar.Uygulamaya("com.harita.ankara", "Kızılay'a git", uyanma = true), Yonlendirme.once("Kızılay'a git", "harita", listOf(harita)))
        assertEquals(Karar.Kanallar("geri sar"), Yonlendirme.once("geri sar", "kanallar", listOf(harita)))
        // "hey asistan": Dicio becerileri
        assertNull(Yonlendirme.once("saat kaç", "asistan", listOf(harita)))
        assertNull(Yonlendirme.once("saat kaç", null, listOf(harita)))
    }

    @Test fun cumleninBasindakiAd() {
        // tanıyıcı uyanma sözünü de duyduysa ad atılır
        assertEquals(Karar.Uygulamaya("com.harita.ankara", "eve git", uyanma = true), Yonlendirme.once("Hey Harita, eve git", "harita", listOf(harita)))
        // uyanma "asistan" iken cümle "harita …" diye başlıyorsa Harita'ya
        assertEquals(Karar.Uygulamaya("com.harita.ankara", "ne kadar kaldı", uyanma = true), Yonlendirme.once("harita ne kadar kaldı", "asistan", listOf(harita)))
        assertEquals(Karar.Kanallar("ne çalıyor"), Yonlendirme.once("kanallar ne çalıyor", null, emptyList()))
        // bilinmeyen ad / sıradan cümle
        assertNull(Yonlendirme.once("hey dostum nasılsın", null, listOf(harita)))
        assertNull(Yonlendirme.once("haritayı büyüt", null, listOf(harita)))
    }

    @Test fun uygulamayiAdiylaAc() {
        // telefonda (2026-11-01): Notlar becerisine ve "kaldığın yerden"e düşüyordu
        assertEquals(Karar.Ac("notlar"), Yonlendirme.once("notlar uygulamasını aç", null, listOf(harita)))
        assertEquals(Karar.Ac("notlar"), Yonlendirme.once("Notlarımı aç", null, emptyList()))
        assertEquals(Karar.Ac("kanallar"), Yonlendirme.once("kanallarımı aç", null, emptyList()))
        assertEquals(Karar.Ac("kanallar"), Yonlendirme.once("Kanallarım uygulamasını açar mısın", null, emptyList()))
        assertEquals(Karar.Ac("harita"), Yonlendirme.once("haritayı aç", "asistan", listOf(harita)))
        assertEquals(Karar.Ac("dosyalar"), Yonlendirme.once("dosya okuyucuyu aç", null, emptyList()))
        // uyanma adından sonra yalnız "aç" yeter
        assertEquals(Karar.Ac("harita"), Yonlendirme.once("aç", "harita", listOf(harita)))
        assertEquals(Karar.Ac("notlar"), Yonlendirme.once("Hey Notlar, uygulamayı aç", null, emptyList()))
        // başka komutlar açma değil
        assertEquals(Karar.Notlar("son notum ne"), Yonlendirme.once("son notum ne", "notlar", emptyList()))
        assertNull(Yonlendirme.once("youtube'u aç", null, listOf(harita)))
        assertNull(Yonlendirme.once("aç", null, listOf(harita)))
    }

    @Test fun kurulMamisUygulamaninAdi() {
        // Notlar'ın köprü servisi yoksa (aday değil) "hey notlar" asistanın Notlar becerisine (yayın alıcısı)
        assertEquals(Karar.Notlar("son notum ne"), Yonlendirme.once("son notum ne", "notlar", listOf(harita)))
        assertEquals(Karar.Notlar("not al süt"), Yonlendirme.once("Hey Notlar, not al süt", null, emptyList()))
        assertEquals("dev.kaimau.notlar", (Yonlendirme.once("son notum ne", "notlar", listOf(harita, notlar)) as Karar.Uygulamaya).paket)
    }

    @Test fun metinDevriKosulu() {
        assertNull(Yonlendirme.sonra("ikinci", listOf(harita, notlar)))
        val yolda = harita.copy(navigasyonda = true)
        assertEquals(Karar.Uygulamaya("com.harita.ankara", "ikinci", uyanma = false), Yonlendirme.sonra("ikinci", listOf(notlar, yolda)))
        val onde = Aday("x", null, Devir.ONDE, onde = true)
        assertEquals("x", (Yonlendirme.sonra("a", listOf(onde)) as Karar.Uygulamaya).paket)
        // navigasyondaki önde olandan önce gelir
        assertEquals("com.harita.ankara", (Yonlendirme.sonra("a", listOf(onde, yolda)) as Karar.Uygulamaya).paket)
        assertEquals(Devir.HER_ZAMAN, Devir.oku("her_zaman"))
        assertEquals(Devir.HAYIR, Devir.oku(null))
    }
}

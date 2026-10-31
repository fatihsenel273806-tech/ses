package com.ses.asistan.beyin

import com.ses.asistan.yonlendirme.Yonlendirme.Devir
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiTest {
    private val harita = Gemini.Uygulama("com.harita.ankara", "Harita Ankara", "harita", Devir.NAVIGASYONDA, mapOf("git" to "Bir yere rota kurup yola çıkar"))
    private val dosya = Gemini.Uygulama(
        "com.dosyaokuyucu.dosya_okuyucu", "Dosya Okuyucu", null, Devir.HAYIR,
        mapOf("ac" to "Adı verilen ya da son dosyayı açar", "ozetle" to "Dosyayı özetler"),
    )
    private val araclar = Gemini.araclar(listOf(harita, dosya), listOf("Saat kaç", "Not al, yarın süt alınacak", "Saat kaç"))

    private fun adlar(a: Gemini.Araclar) = (0 until a.bildirim.length()).map { a.bildirim.getJSONObject(it).getString("name") }

    private fun cevap(vararg parca: JSONObject) = JSONObject().put(
        "candidates", JSONArray().put(JSONObject().put("content", JSONObject().put("parts", JSONArray(parca.toList())))),
    ).toString()

    private fun cagri(ad: String, args: JSONObject) = JSONObject().put("functionCall", JSONObject().put("name", ad).put("args", args))

    @Test fun araclarUygulamalardanKurulur() {
        assertEquals(listOf("asistan_komutu", "uygulamaya_harita", "dosya_okuyucu_ac", "dosya_okuyucu_ozetle"), adlar(araclar))
        val asistan = araclar.bildirim.getJSONObject(0).getString("description")
        assertTrue(asistan, "Saat kaç; Not al, yarın süt alınacak." in asistan)
        assertTrue("Bir yere rota kurup" in araclar.bildirim.getJSONObject(1).getString("description"))
    }

    @Test fun adlarAsciiVeTekil() {
        assertEquals("kanallarim_ac", Gemini.adParcasi("Kanallarım: aç!"))
        val iki = Gemini.araclar(listOf(harita, harita.copy(paket = "baska")), emptyList())
        assertEquals(listOf("asistan_komutu", "uygulamaya_harita", "uygulamaya_harita_2"), adlar(iki))
        assertEquals("baska", (iki.adlar["uygulamaya_harita_2"] as Gemini.Arac.Devret).paket)
    }

    @Test fun cagrilarCozulur() {
        assertEquals(
            Gemini.Cevap.Devret("com.harita.ankara", "Harita Ankara", "Kızılay'a git"),
            Gemini.coz(cevap(cagri("uygulamaya_harita", JSONObject().put("metin", "Kızılay'a git"))), araclar, "oraya git"),
        )
        // metin boşsa söylenen aynen gider (uygulama kendisi yorumlar)
        assertEquals(
            Gemini.Cevap.Devret("com.harita.ankara", "Harita Ankara", "oraya git"),
            Gemini.coz(cevap(cagri("uygulamaya_harita", JSONObject())), araclar, "oraya git"),
        )
        assertEquals(
            Gemini.Cevap.Komut("com.dosyaokuyucu.dosya_okuyucu", "Dosya Okuyucu", "ac", "kira"),
            Gemini.coz(cevap(cagri("dosya_okuyucu_ac", JSONObject().put("sorgu", "kira"))), araclar, "kira dosyasını göster"),
        )
        assertEquals(Gemini.Cevap.Asistan("saat kaç"), Gemini.coz(cevap(cagri("asistan_komutu", JSONObject().put("cumle", "saat kaç"))), araclar, "saati söyler misin"))
        assertEquals(Gemini.Cevap.Bos, Gemini.coz(cevap(cagri("bilinmeyen", JSONObject())), araclar, "x"))
    }

    @Test fun metinSesliOkunacakHaleGelir() {
        val c = Gemini.coz(
            cevap(JSONObject().put("text", "düşünüyorum").put("thought", true), JSONObject().put("text", "**Ankara** Türkiye'nin\n başkentidir.")),
            araclar, "başkent neresi",
        )
        assertEquals(Gemini.Cevap.Soz("Ankara Türkiye'nin başkentidir."), c)
        assertEquals(Gemini.Cevap.Bos, Gemini.coz("{bozuk", araclar, "x"))
    }

    @Test fun istekGecmisVeDurumTasir() {
        val gecmis = listOf(Konusma.Tur("yakında eczane var mı", "Üç eczane var. Hangisi?"), Konusma.Tur("teşekkürler", ""))
        val o = Gemini.istek("ikincisine git", "Navigasyonda: Harita Ankara", gecmis, araclar, "gemini-2.5-flash")
        val icerik = o.getJSONArray("contents")
        assertEquals(5, icerik.length())
        assertEquals("Tamam.", icerik.getJSONObject(3).getJSONArray("parts").getJSONObject(0).getString("text"))
        val son = icerik.getJSONObject(4).getJSONArray("parts").getJSONObject(0).getString("text")
        assertEquals("Durum:\nNavigasyonda: Harita Ankara\n\nKullanıcı: ikincisine git", son)
        assertEquals(0, o.getJSONObject("generationConfig").getJSONObject("thinkingConfig").getInt("thinkingBudget"))
        assertFalse(Gemini.istek("x", "", emptyList(), araclar, "gemini-flash-latest").getJSONObject("generationConfig").has("thinkingConfig"))
    }

    @Test fun baglamOzeti() {
        val t = java.util.GregorianCalendar(2026, 9, 31, 18, 40).timeInMillis
        val m = Baglam.metin(t, listOf("Harita Ankara"), "Harita Ankara", Baglam.Calma("Ankara turu", "Kanallarım", true, 185_000, null))
        assertEquals(
            "Zaman: 31 Ekim 2026 Cumartesi 18:40\nNavigasyonda: Harita Ankara (kullanıcı araç kullanıyor)\nKanallarım: “Ankara turu” çalıyor, 3:05",
            m,
        )
        assertEquals("1:02:09", Baglam.sure(3_729_000))
    }
}

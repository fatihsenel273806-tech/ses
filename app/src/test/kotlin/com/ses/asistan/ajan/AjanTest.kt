package com.ses.asistan.ajan

import com.ses.asistan.ajan.EkranMetni.Dugum
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AjanTest {
    private val dugumler = listOf(
        Dugum(0, "yazı alanı", "Ara", yazilir = true),
        Dugum(1, "düğme", "Kaydet", tiklanir = true),
        Dugum(2, "düğme", "Notu sil", tiklanir = true),
        Dugum(3, "liste", "", kaydirilir = true),
        Dugum(4, "yazı", "Kaydedilenler"),
    )

    @Test fun ekranMetni() {
        val m = EkranMetni.yaz("Notlar", dugumler)
        assertTrue(m, m.startsWith("Uygulama: Notlar\n[0] yazı alanı \"Ara\" (yazılır)"))
        assertTrue(m, "[3] liste (kaydırılır)" in m)
        assertEquals("düğme", EkranMetni.tur("android.widget.Button"))
        assertEquals("yazı alanı", EkranMetni.tur("android.widget.EditText"))
    }

    @Test fun adlaBulma() {
        assertEquals(1, EkranMetni.bul(dugumler, "kaydet")?.no)
        assertEquals(2, EkranMetni.bul(dugumler, "sil")?.no)
        assertEquals(4, EkranMetni.bul(dugumler, "kaydedilenler")?.no)
        assertNull(EkranMetni.bul(dugumler, "harita"))
    }

    @Test fun riskliDugmeler() {
        assertTrue(EkranMetni.riskli("Notu sil"))
        assertTrue(EkranMetni.riskli("Gönder"))
        assertTrue(EkranMetni.riskli("Satın al"))
        assertFalse(EkranMetni.riskli("Kaydet"))
        assertFalse(EkranMetni.riskli("Silindir"))
    }

    @Test fun onay() {
        assertTrue(Ajan.onaylandi("evet sil"))
        assertTrue(Ajan.onaylandi("Tamam"))
        assertFalse(Ajan.onaylandi("hayır"))
        assertFalse(Ajan.onaylandi("yok iptal"))
        assertFalse(Ajan.onaylandi("evet ama hayır"))
        assertFalse(Ajan.onaylandi(null))
    }

    private fun cevap(vararg parca: JSONObject) = JSONObject().put(
        "candidates", JSONArray().put(JSONObject().put("content", JSONObject().put("role", "model").put("parts", JSONArray(parca.toList())))),
    ).toString()

    private fun cagri(ad: String, args: JSONObject) = JSONObject().put("functionCall", JSONObject().put("name", ad).put("args", args))

    @Test fun adimlarCozulur() {
        assertEquals(Ajan.Adim.Tikla(3), Ajan.coz(cevap(cagri("tikla", JSONObject().put("no", 3))))?.adim)
        assertEquals(Ajan.Adim.Yaz(0, "Kızılay"), Ajan.coz(cevap(cagri("yaz", JSONObject().put("no", 0).put("metin", "Kızılay"))))?.adim)
        assertEquals(Ajan.Adim.Kaydir("asagi", null), Ajan.coz(cevap(cagri("kaydir", JSONObject().put("yon", "asagi"))))?.adim)
        assertEquals(Ajan.Adim.Ac("notlar"), Ajan.coz(cevap(cagri("uygulama_ac", JSONObject().put("ad", "notlar"))))?.adim)
        assertEquals(Ajan.Adim.Onay("Sileyim mi?"), Ajan.coz(cevap(cagri("onay_iste", JSONObject().put("soru", "Sileyim mi?"))))?.adim)
        assertEquals(Ajan.Adim.Bitir("Eklendi.", true), Ajan.coz(cevap(cagri("bitir", JSONObject().put("cevap", "Eklendi."))))?.adim)
        // bilinmeyen uygulama / araç: yapılamadı
        assertEquals(Ajan.Adim.Bitir("Bunu yapamadım.", false), Ajan.coz(cevap(cagri("uygulama_ac", JSONObject().put("ad", "banka"))))?.adim)
        assertEquals(Ajan.Adim.Bitir("Ekranda Kızılay yazıyor.", true), Ajan.coz(cevap(JSONObject().put("text", "Ekranda Kızılay yazıyor.")))?.adim)
        assertNull(Ajan.coz("{}"))
    }

    @Test fun istekteYalnizSonEkranTam() {
        val c = Ajan.coz(cevap(JSONObject().put("thoughtSignature", "imza").put("functionCall", JSONObject().put("name", "tikla").put("args", JSONObject().put("no", 1)))))!!
        // modelin içeriği (imza dahil) aynen geri gider
        assertEquals("imza", c.model.getJSONArray("parts").getJSONObject(0).getString("thoughtSignature"))
        val gecmis = listOf(
            Ajan.baslangic("notu kaydet", "EKRAN0"), c.model, Ajan.sonuc("tikla", "dokunuldu", "EKRAN1"),
            c.model, Ajan.sonuc("tikla", "dokunuldu", "EKRAN2"),
        )
        val icerik = Ajan.istek(gecmis, null).getJSONArray("contents").toString()
        assertTrue("EKRAN2" in icerik)
        assertFalse("EKRAN1" in icerik)
        assertTrue("(eski ekran)" in icerik)
        assertEquals("ANY", Ajan.istek(gecmis, null).getJSONObject("toolConfig").getJSONObject("functionCallingConfig").getString("mode"))
    }
}

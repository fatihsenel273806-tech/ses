package com.ses.asistan.ajan

import org.json.JSONArray
import org.json.JSONObject

/**
 * Ekran ajanı (2026-11-01, kullanıcı: "asistan ilgili uygulamalarda A'dan Z'ye her şeyi yapabilsin"): Gemini her adımda
 * ekranın metnini ([EkranMetni]) görür, tek eylem seçer; [AjanCalistirici] yapar ve yeni ekranı geri verir. Yalnız
 * bağlı dört uygulamada (kullanıcı kararı). Geri alınamayan işten önce `onay_iste` (Gemini unutursa çalıştırıcı
 * kendisi sorar, [EkranMetni.riskli]). Android'e bağlı değil: istek ve cevap biçimi JVM testinde.
 *
 * Çok turlu function calling: modelin cevabı (içindeki `thoughtSignature` dahil) aynen geri gönderilir, ardından
 * `functionResponse` (sonuç + yeni ekran). İstek küçük kalsın diye yalnız son ekran tam gider.
 */
object Ajan {
    sealed interface Adim {
        data class Tikla(val no: Int) : Adim
        data class Yaz(val no: Int, val metin: String) : Adim
        /** [yon]: asagi, yukari, sola, saga; [no]: kaydırılacak liste (yoksa ekranın ilk listesi). */
        data class Kaydir(val yon: String, val no: Int?) : Adim
        data object Geri : Adim
        /** [ad]: harita, kanallar, notlar, dosyalar. */
        data class Ac(val ad: String) : Adim
        data object Bekle : Adim
        data class Onay(val soru: String) : Adim
        data class Bitir(val cevap: String, val basarili: Boolean) : Adim
    }

    /** Cevap: yapılacak adım, modelin aynen geri gönderilecek içeriği ve fonksiyonun adı. */
    class Cevap(val adim: Adim, val model: JSONObject, val ad: String)

    const val MAKS_ADIM = 15
    val UYGULAMALAR = listOf("harita", "kanallar", "notlar", "dosyalar")

    const val SISTEM = "Telefonda kullanıcının yerine bir uygulamayı ekrandaki öğelerle kullanan Türkçe bir asistansın. " +
        "Her adımda ekranın listesini görürsün: her satır [numara] tür \"yazı\" (özellikler). Görevi bitirmek için her " +
        "seferinde TEK fonksiyon çağır. Yalnız listedeki numaraları kullan, uydurma. Yazı alanına yazmadan önce gerekirse " +
        "ona tıkla. Aradığın öğe yoksa kaydır ya da ilgili sekmeye/menüye git. Silme, gönderme, paylaşma, satın alma, ödeme " +
        "gibi geri alınamayan bir işten ÖNCE mutlaka onay_iste çağır. Görev bitince ya da yapılamıyorsa bitir çağır; " +
        "cevap sesli okunur: tek kısa cümle. Kullanıcı bir bilgi sorduysa (ör. ekranda ne yazıyor) cevabı bitir'e yaz."

    private fun fn(ad: String, aciklama: String, ozellik: JSONObject = JSONObject(), gerekli: List<String> = emptyList()) =
        JSONObject().put("name", ad).put("description", aciklama).put(
            "parameters", JSONObject().put("type", "object").put("properties", ozellik).also {
                if (gerekli.isNotEmpty()) it.put("required", JSONArray(gerekli))
            },
        )

    private fun tip(t: String, aciklama: String) = JSONObject().put("type", t).put("description", aciklama)
    private fun secenek(aciklama: String, degerler: List<String>) = tip("string", aciklama).put("enum", JSONArray(degerler))

    val ARACLAR: JSONArray = JSONArray()
        .put(fn("tikla", "Numaralı öğeye dokun", JSONObject().put("no", tip("integer", "Öğenin numarası")), listOf("no")))
        .put(fn("yaz", "Numaralı yazı alanının içeriğini bu metinle değiştir",
            JSONObject().put("no", tip("integer", "Yazı alanının numarası")).put("metin", tip("string", "Yazılacak metin")), listOf("no", "metin")))
        .put(fn("kaydir", "Listeyi ya da ekranı kaydır (aşağı: daha fazlasını gör)",
            JSONObject().put("yon", secenek("Yön", listOf("asagi", "yukari", "sola", "saga"))).put("no", tip("integer", "Kaydırılacak listenin numarası; boşsa ilk liste")), listOf("yon")))
        .put(fn("geri", "Geri düğmesi"))
        .put(fn("uygulama_ac", "Bağlı uygulamalardan birini aç", JSONObject().put("ad", secenek("Uygulama", UYGULAMALAR)), listOf("ad")))
        .put(fn("bekle", "Ekran yükleniyorsa biraz bekle"))
        .put(fn("onay_iste", "Geri alınamayan işten önce kullanıcıya evet/hayır sorusu sor",
            JSONObject().put("soru", tip("string", "Kısa soru, ör. 'Market notunu sileyim mi?'")), listOf("soru")))
        .put(fn("bitir", "Görev bitti ya da yapılamıyor",
            JSONObject().put("cevap", tip("string", "Kullanıcıya söylenecek tek kısa cümle")).put("basarili", tip("boolean", "Görev yapıldı mı")), listOf("cevap")))

    /** İlk kullanıcı mesajı: görev ve ilk ekran. */
    fun baslangic(gorev: String, ekran: String): JSONObject = metin("user", "Görev: $gorev\n\nEkran:\n$ekran")

    /** Adımın sonucu ve yeni ekran (`functionResponse`). */
    fun sonuc(ad: String, sonuc: String, ekran: String?): JSONObject = JSONObject().put("role", "user").put(
        "parts", JSONArray().put(JSONObject().put("functionResponse", JSONObject().put("name", ad).put(
            "response", JSONObject().put("sonuc", sonuc).also { if (ekran != null) it.put("ekran", ekran) },
        ))),
    )

    private fun metin(rol: String, t: String) = JSONObject().put("role", rol).put("parts", JSONArray().put(JSONObject().put("text", t)))

    /**
     * İstek gövdesi. [gecmis]: baslangic, model, sonuc, model, sonuc … Son ekran dışındaki ekranlar kısaltılır (istek
     * küçük kalsın; Gemini'nin geçmişte ne yaptığını görmesi yeter).
     */
    fun istek(gecmis: List<JSONObject>, dusunme: JSONObject?): JSONObject {
        val sonEkran = gecmis.indexOfLast { it.optJSONArray("parts")?.optJSONObject(0)?.has("functionResponse") == true }
        val icerik = JSONArray()
        gecmis.forEachIndexed { i, m ->
            val fr = m.optJSONArray("parts")?.optJSONObject(0)?.optJSONObject("functionResponse")
            if (fr != null && i != sonEkran && fr.optJSONObject("response")?.has("ekran") == true) {
                val r = JSONObject(fr.getJSONObject("response").toString()).put("ekran", "(eski ekran)")
                icerik.put(sonuc(fr.optString("name"), r.optString("sonuc"), "(eski ekran)"))
            } else icerik.put(m)
        }
        return JSONObject()
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", SISTEM))))
            .put("contents", icerik)
            .put("tools", JSONArray().put(JSONObject().put("functionDeclarations", ARACLAR)))
            .put("toolConfig", JSONObject().put("functionCallingConfig", JSONObject().put("mode", "ANY")))
            .put("generationConfig", JSONObject().put("temperature", 0.1).also { g -> dusunme?.let { g.put("thinkingConfig", it) } })
    }

    /** Cevaptan adım; fonksiyon çağrısı yoksa metin bitiş sayılır. null: boş/bozuk cevap. */
    fun coz(govde: String): Cevap? {
        val icerik = runCatching { JSONObject(govde) }.getOrNull()?.optJSONArray("candidates")?.optJSONObject(0)
            ?.optJSONObject("content") ?: return null
        val parcalar = icerik.optJSONArray("parts") ?: return null
        val model = JSONObject(icerik.toString()).put("role", "model")
        var metin = ""
        for (i in 0 until parcalar.length()) {
            val p = parcalar.optJSONObject(i) ?: continue
            val fc = p.optJSONObject("functionCall")
            if (fc == null) { if (!p.optBoolean("thought")) metin += p.optString("text"); continue }
            val a = fc.optJSONObject("args") ?: JSONObject()
            val ad = fc.optString("name")
            val no = a.optInt("no", -1)
            val adim = when (ad) {
                "tikla" -> if (no >= 0) Adim.Tikla(no) else null
                "yaz" -> if (no >= 0) Adim.Yaz(no, a.optString("metin")) else null
                "kaydir" -> Adim.Kaydir(a.optString("yon").ifEmpty { "asagi" }, no.takeIf { it >= 0 })
                "geri" -> Adim.Geri
                "uygulama_ac" -> a.optString("ad").takeIf { it in UYGULAMALAR }?.let { Adim.Ac(it) }
                "bekle" -> Adim.Bekle
                "onay_iste" -> Adim.Onay(a.optString("soru").ifBlank { "Devam edeyim mi?" })
                "bitir" -> Adim.Bitir(a.optString("cevap").trim(), a.optBoolean("basarili", true))
                else -> null
            } ?: return Cevap(Adim.Bitir("Bunu yapamadım.", false), model, ad)
            return Cevap(adim, model, ad)
        }
        return metin.trim().takeIf { it.isNotEmpty() }?.let { Cevap(Adim.Bitir(it, true), model, "bitir") }
    }

    private val EVET = Regex("(?<!\\p{L})(evet|tamam|olur|yap|sil|gönder|onaylıyorum|aynen|devam)(?!\\p{L})")
    private val HAYIR = Regex("(?<!\\p{L})(hayır|yok|iptal|vazgeç|dur|yapma|istemiyorum)(?!\\p{L})")

    /** Onay sorusuna verilen sesli cevap: "hayır" kelimesi varsa hayır (güvenli yan), "evet" varsa evet. */
    fun onaylandi(cevap: String?): Boolean {
        val c = cevap?.lowercase(java.util.Locale("tr")) ?: return false
        return !HAYIR.containsMatchIn(c) && EVET.containsMatchIn(c)
    }
}

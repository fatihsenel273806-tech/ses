package com.ses.asistan.beyin

import com.ses.asistan.yonlendirme.Yonlendirme
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/**
 * Gemini isteği ve cevabı (PLAN Faz 6): Android'e bağlı değil, JVM testiyle sınanır. İstek biçimi Harita
 * `GeminiClient.interpret`'inkiyle aynı (geçmiş + "Durum: … Kullanıcı: …", function calling, hızlı düşünme).
 *
 * Araçlar (function declarations):
 *  - `asistan_komutu(cumle)`: asistanın kendi becerileri (Dicio + bizimkiler). Gemini cümleyi becerinin anladığı
 *    sade biçime getirir, beceri sıralayıcısı yeniden dener.
 *  - Metin devri kabul eden uygulama (Harita): `uygulamaya_<ad>(metin)`. Uygulama cümleyi **kendi** komut anlayışıyla
 *    (kendi Gemini'siyle) yorumlar; asistanın Gemini'si yalnız hedefi seçer ve göndermeleri ("oraya") açar.
 *  - Yapılandırılmış komutu olan uygulama (Dosya Okuyucu): `<ad>_<komut>(sorgu)` → köprüden `KOMUT`.
 */
object Gemini {
    /** Köprüsü olan uygulama (`ses_yetenekler.xml`) ve görünen adı. */
    data class Uygulama(
        val paket: String,
        val ad: String,
        val uyanma: String?,
        val devir: Yonlendirme.Devir,
        val komutlar: Map<String, String>,
    )

    sealed interface Arac {
        data object Asistan : Arac
        data class Devret(val paket: String, val ad: String) : Arac
        data class Komut(val paket: String, val ad: String, val komut: String) : Arac
    }

    class Araclar(val bildirim: JSONArray, val adlar: Map<String, Arac>)

    sealed interface Cevap {
        /** Gemini'nin kendi sözü (sesli okunur). */
        data class Soz(val metin: String) : Cevap
        data class Asistan(val cumle: String) : Cevap
        data class Devret(val paket: String, val ad: String, val metin: String) : Cevap
        data class Komut(val paket: String, val ad: String, val komut: String, val sorgu: String) : Cevap
        data object Bos : Cevap
    }

    const val ASISTAN = "asistan_komutu"

    const val SISTEM = "Sen Türkçe konuşan kişisel bir sesli asistansın; telefondaki uygulamalara (harita, video, notlar, " +
        "dosyalar) bağlısın. Cevapların sesli okunur: kısa ve doğal konuş (en çok iki cümle), madde işareti, emoji, " +
        "markdown kullanma. İstek asistanın ya da bir uygulamanın yapabileceği bir işse ilgili fonksiyonu çağır, yalnız " +
        "anlatma. Önceki konuşmaları hatırla: 'oraya', 'onu', 'ikincisi', 'peki orada otopark var mı' gibi sözleri geçmişe " +
        "göre çöz. İstek belirsizse uydurma: tek kısa soruyla netleştir (soru işaretiyle bitsin). Genel sorulara (bilgi, " +
        "sohbet) kısa ve doğru cevap ver; bilmediğin güncel bilgiyi (haber, fiyat, maç sonucu) uydurma. Durumda navigasyon " +
        "varsa kullanıcı araç kullanıyor: cevabı daha da kısa tut, dikkat dağıtma."

    private val TR = Locale("tr")
    private val ASCII = mapOf('ç' to 'c', 'ğ' to 'g', 'ı' to 'i', 'ö' to 'o', 'ş' to 's', 'ü' to 'u', 'â' to 'a', 'î' to 'i', 'û' to 'u')
    private val AYRAC = Regex("[^a-z0-9]+")

    /** Fonksiyon adına uygun parça: küçük harf, Türkçe harfler ASCII, geri kalan "_". */
    fun adParcasi(s: String): String =
        s.lowercase(TR).map { ASCII[it] ?: it }.joinToString("").replace(AYRAC, "_").trim('_').ifEmpty { "uygulama" }

    private fun fn(ad: String, aciklama: String, ozellikler: JSONObject = JSONObject(), gerekli: List<String> = emptyList()) =
        JSONObject().put("name", ad).put("description", aciklama).put(
            "parameters", JSONObject().put("type", "object").put("properties", ozellikler).also {
                if (gerekli.isNotEmpty()) it.put("required", JSONArray(gerekli))
            }
        )

    private fun yazi(aciklama: String) = JSONObject().put("type", "string").put("description", aciklama)

    /**
     * Araç bildirimi. [ornekler]: asistanın açık becerilerinin örnek cümleleri ("Saat kaç", "Not al, yarın süt alınacak").
     * Ad çakışırsa (iki uygulama aynı adı verdi) sonrakine sıra numarası eklenir.
     */
    fun araclar(uygulamalar: List<Uygulama>, ornekler: List<String>): Araclar {
        val dizi = JSONArray()
        val adlar = LinkedHashMap<String, Arac>()
        fun ekle(ad0: String, arac: Arac, bildirim: (String) -> JSONObject) {
            var ad = ad0.take(60)
            var i = 2
            while (ad in adlar) ad = "${ad0.take(57)}_${i++}"
            adlar[ad] = arac
            dizi.put(bildirim(ad))
        }
        ekle(ASISTAN, Arac.Asistan) {
            fn(
                it, "Asistanın kendi becerisini çalıştır. Yapabildikleri (örnek cümleler): " +
                    ornekler.filter { o -> o.isNotBlank() }.distinct().joinToString("; ") + ".",
                JSONObject().put("cumle", yazi("Becerinin anlayacağı sade Türkçe komut cümlesi, örneklerdeki gibi (ör. 'saat kaç', '5 dakikalık zamanlayıcı kur', 'geri sar')")),
                listOf("cumle"),
            )
        }
        for (u in uygulamalar) {
            val kisa = adParcasi(u.uyanma ?: u.ad)
            if (u.devir != Yonlendirme.Devir.HAYIR || u.uyanma != null) {
                val yapar = u.komutlar.values.filter { it.isNotBlank() }
                ekle("uygulamaya_$kisa", Arac.Devret(u.paket, u.ad)) {
                    fn(
                        it, "${u.ad} uygulamasına cümleyi ver; uygulama kendisi anlar, yapar ve cevabı kendisi söyler." +
                            (if (yapar.isEmpty()) "" else " Yapabildikleri: ${yapar.joinToString("; ")}."),
                        JSONObject().put("metin", yazi("Uygulamaya verilecek Türkçe cümle: kullanıcının sözü aynen; yalnız 'oraya', 'onu' gibi göndermeler geçmişteki açık adla değiştirilir")),
                        listOf("metin"),
                    )
                }
            } else {
                for ((komut, aciklama) in u.komutlar) {
                    ekle("${kisa}_${adParcasi(komut)}", Arac.Komut(u.paket, u.ad, komut)) {
                        fn(it, "${u.ad}: ${aciklama.ifBlank { komut }}", JSONObject().put("sorgu", yazi("Gerekirse arama sözü (ör. dosya adında geçen kelime); yoksa boş")))
                    }
                }
            }
        }
        return Araclar(dizi, adlar)
    }

    /**
     * Düşünme ayarı (Harita `GeminiClient.fastThinking`): varsayılan "düşünme" yanıtı 10-30 sn geciktirir. 2.5 Flash'ta
     * kapalı, 2.5 Pro'da en az, 3.x'te düşük. Bilinmeyen modelde (null) istek olduğu gibi gider.
     */
    fun hizliDusunme(model: String): JSONObject? {
        val m = model.removePrefix("models/").lowercase()
        return when {
            m.startsWith("gemini-2.5-pro") -> JSONObject().put("thinkingBudget", 128)
            m.startsWith("gemini-2.5-flash") -> JSONObject().put("thinkingBudget", 0)
            m.startsWith("gemini-3") -> JSONObject().put("thinkingLevel", "low")
            else -> null
        }
    }

    fun modelYolu(m: String) = if (m.startsWith("models/")) m else "models/$m"

    /** İstek gövdesi: geçmiş konuşma, sonra "Durum: … Kullanıcı: …" (Harita'daki gibi). */
    fun istek(soz: String, durum: String, gecmis: List<Konusma.Tur>, araclar: Araclar, model: String): JSONObject {
        val icerik = JSONArray()
        fun mesaj(rol: String, metin: String) = icerik.put(JSONObject().put("role", rol).put("parts", JSONArray().put(JSONObject().put("text", metin))))
        for (t in gecmis) {
            mesaj("user", t.soz)
            mesaj("model", t.cevap.ifEmpty { "Tamam." })
        }
        mesaj("user", (if (durum.isBlank()) "" else "Durum:\n$durum\n\n") + "Kullanıcı: $soz")
        return JSONObject()
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", SISTEM))))
            .put("contents", icerik)
            .put("tools", JSONArray().put(JSONObject().put("functionDeclarations", araclar.bildirim)))
            .put("toolConfig", JSONObject().put("functionCallingConfig", JSONObject().put("mode", "AUTO")))
            .put("generationConfig", JSONObject().put("temperature", 0.35).also { g -> hizliDusunme(model)?.let { g.put("thinkingConfig", it) } })
    }

    /** Cevap gövdesi → ne yapılacağı. İlk fonksiyon çağrısı geçerlidir; yoksa metin parçaları birleşip söylenir. */
    fun coz(govde: String, araclar: Araclar, soz: String): Cevap {
        val parcalar = runCatching { JSONObject(govde) }.getOrNull()?.optJSONArray("candidates")?.optJSONObject(0)
            ?.optJSONObject("content")?.optJSONArray("parts") ?: return Cevap.Bos
        var metin: String? = null
        for (i in 0 until parcalar.length()) {
            val p = parcalar.optJSONObject(i) ?: continue
            p.optJSONObject("functionCall")?.let { fc ->
                val a = fc.optJSONObject("args") ?: JSONObject()
                return when (val arac = araclar.adlar[fc.optString("name")]) {
                    Arac.Asistan -> a.optString("cumle").trim().takeIf { it.isNotEmpty() }?.let { Cevap.Asistan(it) } ?: Cevap.Bos
                    is Arac.Devret -> Cevap.Devret(arac.paket, arac.ad, a.optString("metin").trim().ifEmpty { soz.trim() })
                    is Arac.Komut -> Cevap.Komut(arac.paket, arac.ad, arac.komut, a.optString("sorgu").trim())
                    null -> Cevap.Bos
                }
            }
            // düşünce özetleri (thought) söylenmez
            if (p.has("text") && !p.optBoolean("thought")) metin = (metin ?: "") + p.optString("text")
        }
        return metin?.let(::sesli)?.takeIf { it.isNotEmpty() }?.let { Cevap.Soz(it) } ?: Cevap.Bos
    }

    private val ISARET = Regex("[*_#`]+")
    private val BOSLUK = Regex("\\s+")

    /** Sesli okunacak metin: markdown işaretleri ve fazla boşluk atılır. */
    fun sesli(metin: String): String = metin.replace(ISARET, "").replace(BOSLUK, " ").trim()
}

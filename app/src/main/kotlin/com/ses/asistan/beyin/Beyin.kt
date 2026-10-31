package com.ses.asistan.beyin

import android.content.Context
import android.util.Log
import com.ses.asistan.beceri.Kanallar
import com.ses.asistan.giris.OndekiUygulama
import com.ses.asistan.kopru.Kopru
import com.ses.asistan.kopru.Uygulamalar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Asistanın beyni (PLAN Faz 6): hiçbir beceri ve metin devri anlamadığında Gemini'ye sorulur ([BeyinBecerisi]).
 * Konuşma belleği ([Konusma]) her etkileşimi tutar (Dicio becerilerinin cevapları da), böylece "oraya git",
 * "ikincisi" gibi cümleler önceki konuşmaya göre çözülür. Bağlam: bağlı uygulamaların `DURUM`'u ve çalan video.
 *
 * Çift yorum yok: metni bir uygulamaya devreden yönlendirme (uyanma adı, navigasyonda devir) Gemini'ye hiç uğramaz;
 * Gemini de uygulamaya cümleyi verir, o uygulama kendi anlayışıyla (kendi Gemini'siyle) yorumlar.
 */
object Beyin {
    private const val TAG = "Beyin"
    private const val ZAMAN_ASIMI_MS = 12_000
    /** Devredilen uygulamanın cevabı bu süre içinde gelirse belleğe o sözün cevabı olarak yazılır. */
    private const val DEVIR_CEVAP_MS = 30_000L
    private const val CALAN_MS = 1_500L
    private const val TABAN = "https://generativelanguage.googleapis.com/v1beta"

    private val konusma = Konusma()
    @Volatile private var sonDevir: Pair<String, Long>? = null

    /** Her etkileşimden sonra (SkillEvaluator kancası): söz ve sesli cevabı. */
    fun hatirla(soz: String, cevap: String) = synchronized(konusma) { konusma.ekle(soz, cevap) }

    /** Metin bir uygulamaya verildi; cevabını uygulama `SÖYLE` ile kendisi söyleyecek. */
    fun devredildi(paket: String) { sonDevir = paket to System.currentTimeMillis() }

    /** Uygulamanın `SÖYLE(cevap)`'ı ([Kopru]): son devrin cevabıysa belleğe yazılır. */
    fun uygulamaCevabi(paket: String, metin: String) {
        val (p, t) = sonDevir ?: return
        if (p != paket || System.currentTimeMillis() - t > DEVIR_CEVAP_MS || metin.isBlank()) return
        synchronized(konusma) { konusma.cevap(metin) }
    }

    fun gecmis(): List<Konusma.Tur> = synchronized(konusma) { konusma.gecmis() }

    /** [ornekler]: asistanın açık becerilerinin örnek cümleleri. Ana iş parçacığında çağrılmaz. */
    suspend fun sor(ctx: Context, soz: String, ornekler: List<String>): Pair<Gemini.Cevap, Gemini.Araclar> {
        val a = BeyinAyarlari.al(ctx)
        val araclar = withContext(Dispatchers.IO) { Gemini.araclar(uygulamalar(ctx), ornekler) }
        val durum = durum(ctx)
        val gecmis = gecmis()
        return withContext(Dispatchers.IO) {
            val govde = a.havuz.calistir(a.anahtarlar, a.modeller) { k, m -> istek(k, m, Gemini.istek(soz, durum, gecmis, araclar, m)) }
            Gemini.coz(govde, araclar, soz) to araclar
        }
    }

    private fun etiket(ctx: Context, paket: String) = runCatching {
        ctx.packageManager.getApplicationLabel(ctx.packageManager.getApplicationInfo(paket, 0)).toString()
    }.getOrDefault(paket.substringAfterLast('.'))

    private fun uygulamalar(ctx: Context) = Uygulamalar.hepsi(ctx).map {
        Gemini.Uygulama(it.paket, etiket(ctx, it.paket), it.uyanma, it.devir, it.komutlar)
    }

    private suspend fun durum(ctx: Context): String {
        val (nav, onde) = withContext(Dispatchers.IO) {
            Kopru.navigasyondakiler().map { etiket(ctx, it) } to
                (Kopru.ondeOlan() ?: OndekiUygulama.paket(ctx))?.takeIf { it != ctx.packageName }?.let { etiket(ctx, it) }
        }
        // çalan video: Kanallarım'a bağlanmak sürebilir, beklenmez
        val c = withTimeoutOrNull(CALAN_MS) { runCatching { Kanallar.al(ctx).calan() }.getOrNull() }
        val calan = c?.baslik?.takeIf { it.isNotBlank() }?.let {
            Baglam.Calma(it, c.uygulama ?: "Kanallarım", c.oynuyor, c.konumMs, c.sanatci)
        }
        return Baglam.metin(System.currentTimeMillis(), nav, onde, calan)
    }

    private fun istek(anahtar: String, model: String, govde: JSONObject): String {
        val c = URL("$TABAN/${Gemini.modelYolu(model)}:generateContent").openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"
            c.connectTimeout = ZAMAN_ASIMI_MS
            c.readTimeout = ZAMAN_ASIMI_MS
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
            c.setRequestProperty("x-goog-api-key", anahtar)
            c.outputStream.use { it.write(govde.toString().toByteArray()) }
            val kod = c.responseCode
            if (kod !in 200..299) {
                val hata = c.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                val mesaj = runCatching { JSONObject(hata).getJSONObject("error").getString("message") }.getOrDefault(hata.take(200))
                Log.w(TAG, "$model: HTTP $kod $mesaj")
                throw ApiHatasi(kod, mesaj)
            }
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }
}

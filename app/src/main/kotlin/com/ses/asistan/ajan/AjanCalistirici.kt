package com.ses.asistan.ajan

import android.content.Context
import android.content.Intent
import android.util.Log
import com.ses.asistan.beceri.DosyalarInfo
import com.ses.asistan.beceri.Kanallar
import com.ses.asistan.beceri.Notlar
import com.ses.asistan.beceri.dikte
import com.ses.asistan.beceri.konusVeBekle
import com.ses.asistan.beyin.BeyinAyarlari
import com.ses.asistan.beyin.Beyin
import com.ses.asistan.beyin.Gemini
import com.ses.asistan.ek.EkranServisi
import com.ses.asistan.ses.Ses
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Ekran ajanını çalıştırır ([Ajan]): ekranı okur, Gemini'nin seçtiği adımı yapar, yeni ekranı geri verir; en çok
 * [Ajan.MAKS_ADIM] adım. Yalnız bağlı dört uygulamada; başka uygulamaya geçilirse durur. Riskli düğmeye (sil, gönder…)
 * Gemini onay istemeden basmaya kalkarsa sesli onay burada sorulur. Dönen cümle sesli söylenir.
 */
object AjanCalistirici {
    private const val TAG = "Ajan"
    /** Eylemden sonra ekranın oturması. */
    private const val OTUR_MS = 900L
    private const val ACILIS_MS = 1_800L

    val PAKET = mapOf(
        "harita" to "com.harita.ankara", "kanallar" to Kanallar.PKG, "notlar" to Notlar.PKG, "dosyalar" to DosyalarInfo.PKG,
    )

    /** [uygulama]: önce açılacak uygulama ([Ajan.UYGULAMALAR]); null: öndeki uygulamada. */
    suspend fun yap(ctx: Context, gorev: String, uygulama: String? = null): String {
        val s = EkranServisi.tek ?: return "Bunun için Ayarlar, Erişilebilirlik'ten Asistan'ın ekran kontrolünü açın."
        if (!BeyinAyarlari.al(ctx).hazir()) return "Bunun için Gemini anahtarı gerekiyor."
        EkranServisi.asistaniGizle()
        uygulama?.let { if (!ac(ctx, it)) return "Uygulama açılamadı." }
        var ekran = oku() ?: return DISARIDA
        val gecmis = mutableListOf(Ajan.baslangic(gorev, ekran.metin()))
        var onayli = false
        repeat(Ajan.MAKS_ADIM) {
            val govde = try {
                Beyin.cagir(ctx) { m -> Ajan.istek(gecmis, Gemini.hizliDusunme(m)) }
            } catch (e: Exception) {
                Log.w(TAG, "Gemini", e)
                return "Gemini'ye ulaşılamadı."
            }
            val c = Ajan.coz(govde) ?: return "Gemini cevap vermedi."
            gecmis += c.model
            val sonuc = when (val a = c.adim) {
                is Ajan.Adim.Bitir -> return a.cevap.ifBlank { if (a.basarili) "Tamam." else "Yapamadım." }
                is Ajan.Adim.Onay -> {
                    if (!sor(ctx, a.soru)) return "Tamam, yapmadım."
                    onayli = true
                    "kullanıcı onayladı"
                }
                is Ajan.Adim.Tikla -> {
                    val n = ekran.dugum(a.no)
                    val etiket = ekran.etiket(a.no)
                    if (n != null && EkranMetni.riskli(etiket) && !onayli && !sor(ctx, "“$etiket” düğmesine basayım mı?")) {
                        return "Tamam, yapmadım."
                    }
                    onayli = false
                    when {
                        n == null -> "geçersiz numara"
                        withContext(Dispatchers.Main) { Eylem.dokun(s, n) } -> "dokunuldu"
                        else -> "dokunulamadı"
                    }
                }
                is Ajan.Adim.Yaz -> {
                    val n = ekran.dugum(a.no)
                    if (n != null && withContext(Dispatchers.Main) { Eylem.yaz(n, a.metin) }) "yazıldı" else "yazılamadı"
                }
                is Ajan.Adim.Kaydir -> {
                    val n = a.no?.let { ekran.dugum(it) }
                    if (withContext(Dispatchers.Main) { Eylem.kaydir(s, ekran, n, a.yon) }) "kaydırıldı" else "kaydırılamadı"
                }
                Ajan.Adim.Geri -> { withContext(Dispatchers.Main) { s.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK) }; "geri" }
                is Ajan.Adim.Ac -> if (ac(ctx, a.ad)) "açıldı" else "açılamadı"
                Ajan.Adim.Bekle -> { delay(OTUR_MS); "beklendi" }
            }
            delay(OTUR_MS)
            ekran = oku() ?: return DISARIDA
            gecmis += Ajan.sonuc(c.ad, sonuc, ekran.metin())
        }
        return "Görevi bitiremedim."
    }

    private const val DISARIDA = "Bu ekranda çalışamam; yalnız Harita, Kanallarım, Notlar ve Dosya Okuyucu'da."

    private suspend fun oku(): EkranOkuyucu? = withContext(Dispatchers.Main) { EkranServisi.tek?.let { EkranOkuyucu.oku(it) } }

    /** Uygulamayı ön plana getirir (erişilebilirlik servisi bağlıyken arka plandan açmaya izin var). */
    suspend fun ac(ctx: Context, ad: String): Boolean {
        val paket = PAKET[ad] ?: return false
        val i = ctx.packageManager.getLaunchIntentForPackage(paket)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) ?: return false
        if (runCatching { ctx.startActivity(i) }.isFailure) return false
        delay(ACILIS_MS)
        return true
    }

    /** Sesli evet/hayır; "hayır" ya da sessizlik hayır sayılır. */
    suspend fun sor(ctx: Context, soru: String): Boolean {
        val ses = withContext(Dispatchers.Main) { Ses.al(ctx) }
        konusVeBekle(ses, soru)
        return Ajan.onaylandi(dikte(ctx, ""))
    }
}

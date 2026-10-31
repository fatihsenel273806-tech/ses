package com.ses.asistan.beceri

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Notlar'ın kişisel derlemesindeki yayın alıcısı (PROTOKOL.md › "Notlar yayınları"; Notlar'da
 * `android/app/src/kisisel`). Notlar öne gelmez, Flutter motoru açılmaz. Alıcı `dev.kaimau.notlar.izin.ASISTAN` ile
 * korunur: genel (dağıtılan) Notlar'da alıcı yoktur, yayın cevapsız kalır (0).
 */
object Notlar {
    const val PKG = "dev.kaimau.notlar"
    private const val ALICI = "$PKG.HaritaNotAlici"
    private const val YAZ = "$PKG.HARITA_NOT"
    private const val SORGU = "$PKG.ASISTAN_SORGU"
    /** Cevap kodları. */
    const val TAMAM = 1
    const val SIRADA = 2
    const val GIRIS_YOK = 3
    /** Asistanın aldığı notlar bu klasöre ("Not al" Harita'da "Haritalar"a gider). */
    const val KLASOR = "Asistan"
    private const val SURE_MS = 12_000L

    class Bulunan(val baslik: String, val metin: String, val zaman: Long)

    fun kurulu(ctx: Context) = runCatching { ctx.packageManager.getPackageInfo(PKG, 0) }.isSuccess

    /** Notu yazar; [TAMAM], [SIRADA] ya da 0 (kurulu değil, kişisel derleme değil, cevap yok). */
    suspend fun yaz(ctx: Context, metin: String, klasor: String = KLASOR): Int =
        gonder(ctx, Intent(YAZ).putExtra("text", metin).putExtra("folder", klasor))?.first ?: 0

    /** Son notlar ([terimler] boşsa) ya da terimlerin hepsini içerenler. null: ulaşılamadı; boş liste: yok. */
    suspend fun sorgula(ctx: Context, terimler: List<String>, adet: Int): Pair<Int, List<Bulunan>>? {
        val i = Intent(SORGU).putExtra("istek", if (terimler.isEmpty()) "son" else "ara")
            .putStringArrayListExtra("terimler", ArrayList(terimler)).putExtra("adet", adet)
        val (kod, ek) = gonder(ctx, i) ?: return null
        if (kod != TAMAM) return kod to emptyList()
        val b = ek?.getStringArrayList("basliklar").orEmpty()
        val m = ek?.getStringArrayList("metinler").orEmpty()
        val z = ek?.getLongArray("zamanlar") ?: LongArray(0)
        return kod to b.indices.map { Bulunan(b[it], m.getOrElse(it) { "" }, z.getOrElse(it) { 0L }) }
    }

    /** Sıralı yayın; cevap kodu ve ekleri. null: gönderilemedi ya da süre doldu. */
    private suspend fun gonder(ctx: Context, i: Intent): Pair<Int, android.os.Bundle?>? {
        if (!kurulu(ctx)) return null
        i.setComponent(ComponentName(PKG, ALICI))
            .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES or Intent.FLAG_RECEIVER_FOREGROUND)
        return withTimeoutOrNull(SURE_MS) {
            suspendCancellableCoroutine { k ->
                val cevap = object : BroadcastReceiver() {
                    override fun onReceive(c: Context, r: Intent) {
                        if (k.isActive) k.resume(resultCode to getResultExtras(false))
                    }
                }
                runCatching {
                    ctx.sendOrderedBroadcast(i, null, cevap, Handler(Looper.getMainLooper()), Activity.RESULT_CANCELED, null, null)
                }.onFailure { if (k.isActive) k.resume(0 to null) }
            }
        }
    }
}

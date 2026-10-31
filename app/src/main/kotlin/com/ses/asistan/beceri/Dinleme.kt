package com.ses.asistan.beceri

import android.content.Context
import com.ses.asistan.giris.Kulak
import com.ses.asistan.ses.Ses
import com.ses.asistan.ses.SpeechQueue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/** Becerilerin ortak sesli adımları (Kanallarım ve Notlar notu). */

/** [metin] söylenir, bitince döner (ardından dikte açılacaksa mikrofon kendi sesini duymasın). */
internal suspend fun konusVeBekle(ses: Ses, metin: String) = withContext(Dispatchers.Main) {
    suspendCancellableCoroutine { k ->
        ses.soyle(metin, SpeechQueue.Kind.CEVAP)
        ses.bitinceCalistir { if (k.isActive) k.resume(Unit) }
    }
}

/**
 * Kesintisiz dikte (Harita `dictateNote`): [baslangic] komutla söylenen kısım, susunca (~3,5 sn) ya da "tamam" deyince
 * biter; null: "iptal" denildi ya da hiçbir şey söylenmedi.
 */
internal suspend fun dikte(ctx: Context, baslangic: String): String? = withContext(Dispatchers.Main) {
    val kulak = Kulak.al(ctx)
    suspendCancellableCoroutine { k ->
        k.invokeOnCancellation { kulak.iptal() }
        kulak.dikte(baslangic) { t -> if (k.isActive) k.resume(t) }
    }
}

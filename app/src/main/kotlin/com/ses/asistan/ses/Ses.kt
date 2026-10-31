package com.ses.asistan.ses

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock

/**
 * Asistanın tek ses çıkışı: bütün konuşmalar [SpeechQueue]'dan sırayla geçer (PLAN §1, D3), ses [Voice]'tan
 * (Antalia, yedek Android TTS). Uyarıdan önce kısa ses işareti çalar ([Tones], Harita `HazardVoice` düzeni).
 * Konuşurken uyanma kendi sesini duymasın diye [SesDurumu.konusuyor] açıktır.
 *
 * Tek örnek, ana iş parçacığında kullanılır ([al]). Dicio'nun cevapları [AsistanSpeechDevice] ile [Kind.CEVAP]
 * olarak gelir; bağlı uygulamaların `SÖYLE` iletisi ([com.ses.asistan.kopru.Kopru]) aynı [soyle]'ye, kaynağıyla.
 * Her öğenin [SpeechQueue.Item.bitti]'si bir kez çağrılır: okununca, kesilince ya da okunmadan atılınca.
 */
class Ses private constructor(ctx: Context) {
    private val main = Handler(Looper.getMainLooper())
    val ayarlar = SesAyarlari.al(ctx)
    val voice = Voice(ctx, ayarlar)
    private val tones = Tones()
    private val kuyruk = SpeechQueue(max = KUYRUK) { it, bayat -> it.bitti?.invoke(bayat) }
    private val bitince = ArrayList<Runnable>()
    @Volatile private var simdiki: SpeechQueue.Item? = null

    /** Ana iş parçacığındaysa hemen (Dicio speak'ten hemen sonra isSpeaking'e bakar), değilse sıraya. */
    private fun ana(f: () -> Unit) { if (Looper.myLooper() === main.looper) f() else main.post(f) }

    /** Kuyrukta ya da sesli konuşma sürüyor mu. */
    val konusuyor get() = simdiki != null

    var onStatus: (String) -> Unit
        get() = voice.onStatus
        set(v) { voice.onStatus = v }

    /**
     * Söylenecekleri sıraya koyar; boştaysa hemen başlar. [again]: talimat bayatlarsa yeniden üretir. [kaynak]: bağlı
     * uygulama; [isaret]: uyarıdan önce ses işareti; [bitti]: okununca/atılınca (true: bayat, okunmadı).
     */
    fun soyle(
        metin: String, tur: SpeechQueue.Kind = SpeechQueue.Kind.CEVAP, again: (() -> String?)? = null,
        kaynak: String? = null, isaret: Boolean = true, bitti: ((Boolean) -> Unit)? = null,
    ) {
        if (metin.isBlank()) { bitti?.let { b -> ana { b(false) } }; return }
        ana {
            kuyruk.push(SpeechQueue.Item(metin, tur, SystemClock.elapsedRealtime(), again, kaynak, isaret, bitti))
            if (simdiki == null) sonraki()
        }
    }

    /**
     * [tur] verilirse yalnız o sınıf susar (ör. Dicio'nun "sus"u yalnız cevapları keser), yoksa hepsi. [kaynak]
     * verilirse yalnız o uygulamanın sesleri (Harita kendi talimatını keser, asistanın cevabına dokunmaz).
     */
    fun sustur(tur: SpeechQueue.Kind? = null, kaynak: String? = null) {
        ana {
            when {
                kaynak != null -> kuyruk.drop(kaynak, tur)
                tur == null -> kuyruk.clear()
                else -> kuyruk.drop(tur)
            }
            val s = simdiki
            if (s != null && (tur == null || s.kind == tur) && (kaynak == null || s.kaynak == kaynak)) {
                voice.stop()
                main.removeCallbacks(alarmdanSonra)
                s.bitti?.invoke(false)
                // kalanlar (başka sınıf ya da başka kaynak) sırayla sürer
                simdiki = null
                bekleyen = null
                sonraki()
            }
        }
    }

    /** Konuşma (ve kuyruk) bitince bir kez çalışır; konuşmuyorsa hemen. */
    fun bitinceCalistir(r: Runnable) {
        ana { if (simdiki == null) r.run() else bitince.add(r) }
    }

    private var bekleyen: SpeechQueue.Item? = null
    private val alarmdanSonra = Runnable { bekleyen?.let { konus(it) } }

    private fun sonraki() {
        val it = kuyruk.nextItem(SystemClock.elapsedRealtime())
        if (it == null) { bitti(); return }
        simdiki = it
        SesDurumu.konusuyor = true
        if (it.kind == SpeechQueue.Kind.UYARI && it.isaret) {
            tones.play(Tones.Kind.ALERT)
            bekleyen = it
            main.postDelayed(alarmdanSonra, ALARM_MS)
        } else {
            konus(it)
        }
    }

    private fun konus(it: SpeechQueue.Item) {
        voice.speak(it.text) { if (simdiki === it) { it.bitti?.invoke(false); sonraki() } }
    }

    private fun bitti() {
        simdiki = null
        bekleyen = null
        // kendi sesimizin yankısı uyanmaya karışmasın: kısa süre daha sağır
        main.postDelayed({ if (simdiki == null) SesDurumu.konusuyor = false }, SAGIR_SONRA_MS)
        val l = ArrayList(bitince)
        bitince.clear()
        l.forEach { it.run() }
    }

    /** Dinleme işareti ("dinliyorum", "anladım"); konuşmayı beklemez. */
    fun isaret(k: Tones.Kind) = tones.play(k)

    companion object {
        private const val KUYRUK = 6
        /** Uyarı işaretinin süresi (ToneMath ALERT ≈ 1,35 sn) kadar beklenip cümle okunur. */
        private val ALARM_MS = ToneMath.notes(Tones.Kind.ALERT).sumOf { it.second + it.third }.toLong()
        private const val SAGIR_SONRA_MS = 300L

        @Volatile private var tek: Ses? = null

        /** Ana iş parçacığında çağrılmalı (TextToSpeech orada kurulur). */
        fun al(ctx: Context): Ses = tek ?: synchronized(this) {
            tek ?: Ses(ctx.applicationContext).also { tek = it }
        }
    }
}

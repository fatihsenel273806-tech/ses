package com.ses.asistan.ses

import kotlin.math.sqrt

/**
 * Uyanma tanıyıcısının önündeki ses kapısı (pil, 2026-11-01): telefonda Asistan pilin %42'sini harcıyordu (CPU 28 dk,
 * mikrofon 2 sa 14 dk); Vosk sessiz odada da her 100 ms'lik kareyi işliyordu. Kapı yalnız ses varken açılır:
 * kare enerjisi (RMS) gürültü tabanının [FAKTOR] katını geçince önceki [ON_KARE] kare (sözün başı, "hey"in "h"si
 * kaçmasın) ve ardından [SURME_KARE] boyunca her kare verilir (Vosk cümle sonunu sessizlikten anlar). Sürekli ses
 * varken (video, araç) eşik [TAVAN]'da kalır: kapı açık, uyanma eskisi gibi. Android'e bağlı değil (JVM testi);
 * döngüde nesne üretmez (ön bellek önceden ayrılmış halka).
 */
class SesEsigi(private val kareBoyu: Int) {
    private val halka = Array(ON_KARE) { ShortArray(kareBoyu) }
    private var bas = 0
    private var dolu = 0
    private var kalan = 0
    private var taban = BASLANGIC_TABAN

    /** Açıkken verilen / toplam kare (ayarlarda gösterilir). */
    var verilen = 0L
        private set
    var toplam = 0L
        private set

    /** Kapı açık mı (son kare tanıyıcıya gitti mi). */
    val acik: Boolean get() = kalan > 0

    /**
     * [kare] için tanıyıcıya verilecek kareleri [ver] ile sırayla verir (açılırken önce ön bellek). Dönüş: kapı bu
     * karede kapandı (tanıyıcının son sonucu alınmalı).
     */
    fun gec(kare: ShortArray, ver: (ShortArray) -> Unit): Boolean {
        toplam++
        val e = rms(kare)
        val esik = minOf(maxOf(taban * FAKTOR, EN_AZ), TAVAN)
        if (e >= esik) {
            if (kalan == 0) {
                // açılış: ön bellekteki kareler eskiden yeniye
                for (i in 0 until dolu) { ver(halka[(bas + i) % ON_KARE]); verilen++ }
                dolu = 0
            }
            kalan = SURME_KARE
            ver(kare); verilen++
            return false
        }
        // taban yalnız sessizlikte öğrenilir: aşağı hızlı, yukarı yavaş
        taban = if (e < taban) taban * 0.7 + e * 0.3 else taban * 0.98 + e * 0.02
        if (kalan > 0) {
            kalan--
            ver(kare); verilen++
            return kalan == 0
        }
        // halka doluysa en eskinin üstüne yazılır
        kare.copyInto(halka[(bas + dolu) % ON_KARE])
        if (dolu < ON_KARE) dolu++ else bas = (bas + 1) % ON_KARE
        return false
    }

    fun sifirla() { kalan = 0; dolu = 0; bas = 0 }

    companion object {
        const val ON_KARE = 3
        const val SURME_KARE = 15
        const val FAKTOR = 2.0
        /** Sessiz odada bile bu altı ses sayılmaz (16 bit örnek). */
        const val EN_AZ = 150.0
        /** Bunu geçen her kare sestir: sürekli seste taban yükselse de kapı açık kalsın. */
        const val TAVAN = 500.0
        private const val BASLANGIC_TABAN = 100.0

        fun rms(k: ShortArray): Double {
            var t = 0.0
            for (s in k) t += s.toDouble() * s
            return if (k.isEmpty()) 0.0 else sqrt(t / k.size)
        }
    }
}

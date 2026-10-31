package com.ses.asistan.beyin

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Gemini'ye giden kısa durum özeti (PLAN Faz 6 "Bağlam"): saat, bağlı uygulamaların `DURUM`'u (navigasyon, önde olan)
 * ve çalan video. Android'e bağlı değil; değerleri [Beyin] toplar.
 */
object Baglam {
    /** Çalan medya ([com.ses.asistan.beceri.Kanallar.Calan]'ın özü). */
    data class Calma(val baslik: String, val uygulama: String, val oynuyor: Boolean, val konumMs: Long, val sanatci: String?)

    private val TR = Locale("tr")

    fun metin(simdi: Long, navigasyonda: List<String>, onde: String?, calan: Calma?): String = buildString {
        appendLine("Zaman: ${SimpleDateFormat("d MMMM yyyy EEEE HH:mm", TR).format(Date(simdi))}")
        if (navigasyonda.isNotEmpty()) appendLine("Navigasyonda: ${navigasyonda.joinToString(", ")} (kullanıcı araç kullanıyor)")
        if (onde != null && onde !in navigasyonda) appendLine("Ekrandaki uygulama: $onde")
        if (calan != null) {
            val kim = calan.sanatci?.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()
            append("${calan.uygulama}: “${calan.baslik}”$kim ")
            appendLine(if (calan.oynuyor) "çalıyor, ${sure(calan.konumMs)}" else "duraklatıldı, ${sure(calan.konumMs)}")
        }
    }.trimEnd()

    /** 3:05, 1:02:09 */
    fun sure(ms: Long): String {
        val s = (ms / 1000).coerceAtLeast(0)
        val (h, m, sn) = Triple(s / 3600, s / 60 % 60, s % 60)
        return if (h > 0) "%d:%02d:%02d".format(h, m, sn) else "%d:%02d".format(m, sn)
    }
}

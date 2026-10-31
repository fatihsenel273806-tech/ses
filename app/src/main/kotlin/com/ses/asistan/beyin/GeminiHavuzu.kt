package com.ses.asistan.beyin

import java.util.Calendar
import java.util.TimeZone

/** Gemini'nin HTTP hatası (Harita `ApiException`). */
class ApiHatasi(val kod: Int, mesaj: String) : Exception("HTTP $kod: $mesaj")

/**
 * Birden çok Gemini anahtarı ve modeli (Harita `GeminiPool`'dan birebir): önce 1. model tüm anahtarlarda denenir,
 * hepsinde hak bitince 2. modele geçilir. Hakkı biten (anahtar, model) çifti bir süre atlanır: günlük hak bittiyse
 * Pasifik gece yarısına (Gemini'nin sıfırlandığı saat) kadar, dakikalık sınırsa Google'ın söylediği süre kadar.
 * Kayıtlar [yukle]/[kaydet] ile saklanır (uygulama yeniden açılınca dolmuş çift yine denenmesin). Saf sınıf.
 */
class GeminiHavuzu(private val yukle: () -> String, private val kaydet: (String) -> Unit) {
    /** Son başarılı çift. */
    @Volatile var simdiki: Pair<String, String>? = null
        private set

    /** Bekletilen çiftler: "anahtar sonu|model" → bekleme bitişi (ms). */
    @Synchronized
    fun bekleyen(simdi: Long = System.currentTimeMillis()): Map<String, Long> =
        yukle().lineSequence().mapNotNull { l ->
            val i = l.lastIndexOf('\t')
            val bitis = l.substring(i + 1).toLongOrNull()
            if (i <= 0 || bitis == null || bitis <= simdi) null else l.substring(0, i) to bitis
        }.toMap()

    /** [cagri]'yı sırayla dener; hak/erişim hatasında sıradaki çifte geçer. Hepsi doluysa son hata fırlatılır. */
    fun <T> calistir(
        anahtarlar: List<String>, modeller: List<String>, simdi: Long = System.currentTimeMillis(),
        cagri: (anahtar: String, model: String) -> T,
    ): T {
        val hepsi = sira(anahtarlar, modeller)
        if (hepsi.isEmpty()) throw IllegalStateException("Gemini anahtarı ya da modeli yok")
        val atla = bekleyen(simdi)
        var sonHata: Exception? = null
        for ((k, m) in hepsi) {
            if (atla.containsKey(yuva(k, m))) continue
            try {
                val r = cagri(k, m)
                simdiki = k to m
                return r
            } catch (e: ApiHatasi) {
                val bitis = bekletmeBitisi(e, simdi) ?: throw e
                beklet(yuva(k, m), bitis, simdi)
                sonHata = e
            }
        }
        throw sonHata ?: ApiHatasi(429, "Tüm Gemini anahtar ve modellerinin bugünkü hakkı doldu")
    }

    @Synchronized
    private fun beklet(yuva: String, bitis: Long, simdi: Long) {
        val kalan = bekleyen(simdi).filterKeys { it != yuva } + (yuva to bitis)
        kaydet(kalan.entries.joinToString("\n") { "${it.key}\t${it.value}" })
    }

    /** Bekletmeleri sil (anahtar/model listesi değişince). */
    @Synchronized
    fun sifirla() { kaydet(""); simdiki = null }

    companion object {
        /** Deneme sırası: model önce (1. model tüm anahtarlarda), sonra anahtar. */
        fun sira(anahtarlar: List<String>, modeller: List<String>): List<Pair<String, String>> {
            val k = anahtarlar.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
            return modeller.map { it.trim() }.filter { it.isNotEmpty() }.distinct().flatMap { m -> k.map { it to m } }
        }

        /** Anahtarın kendisi saklanmaz, yalnız sonu (ayarlarda da böyle gösterilir). */
        fun son(anahtar: String) = anahtar.trim().takeLast(6)

        fun yuva(anahtar: String, model: String) = "${son(anahtar)}|${model.removePrefix("models/")}"

        /** "k1, k2\nk3" → sıralı, tekrarsız liste (Harita `KeyPool.split`). */
        fun bol(ham: String): List<String> = ham.split(',', ';', '\n').map { it.trim() }.filter { it.isNotEmpty() }.distinct()

        private val TEKRAR = Regex("""retry in (\d+(?:\.\d+)?)\s*(s|m|h)""", RegexOption.IGNORE_CASE)
        private val GUNLUK = Regex("(?i)per ?day|daily|günlük")
        private val ANAHTAR = Regex("(?i)api key")

        /**
         * Hatanın sıradaki çifte geçmeyi gerektirip gerektirmediği; gerektiriyorsa bu çiftin bekleme bitişi.
         * null: başka hata (ağ, bozuk istek): deneme bırakılır.
         */
        fun bekletmeBitisi(e: ApiHatasi, simdi: Long): Long? {
            val msg = e.message.orEmpty()
            val gunluk = GUNLUK.containsMatchIn(msg)
            return when {
                e.kod == 429 && gunluk -> pasifikGeceYarisi(simdi)
                e.kod == 429 -> TEKRAR.find(msg)?.let { m ->
                    val v = m.groupValues[1].toDouble()
                    val sn = when (m.groupValues[2].lowercase()) { "h" -> v * 3600; "m" -> v * 60; else -> v }
                    // uzun bekleme günlük hakkın bittiğini gösterir
                    if (sn > 3600) pasifikGeceYarisi(simdi) else simdi + ((sn + 2) * 1000).toLong()
                } ?: (simdi + 60_000)
                // model bu anahtarda yok / anahtar geçersiz ya da kısıtlı: gün boyu atlanır
                e.kod == 400 && ANAHTAR.containsMatchIn(msg) -> pasifikGeceYarisi(simdi)
                e.kod == 401 || e.kod == 403 || e.kod == 404 -> pasifikGeceYarisi(simdi)
                // aşırı yük: kısa bekleme
                e.kod == 500 || e.kod == 503 -> simdi + 30_000
                else -> null
            }
        }

        fun pasifikGeceYarisi(simdi: Long): Long = Calendar.getInstance(TimeZone.getTimeZone("America/Los_Angeles")).run {
            timeInMillis = simdi
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            timeInMillis
        }
    }
}

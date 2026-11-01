package com.ses.asistan.ajan

import java.util.Locale

/**
 * Ekrandaki öğelerin Gemini'ye ve kurallı komutlara verilen kısa hâli. Android'e bağlı değil (JVM testi): erişilebilirlik
 * ağacı [EkranOkuyucu]'da [Dugum] listesine çevrilir.
 */
object EkranMetni {
    /** Ekranda bir öğe; [no] eylemlerde kullanılan sıra numarası. */
    data class Dugum(
        val no: Int,
        val tur: String,
        val metin: String,
        val tiklanir: Boolean = false,
        val yazilir: Boolean = false,
        val kaydirilir: Boolean = false,
        val secili: Boolean = false,
    ) {
        /** Gemini'ye giden satır: `[3] düğme "Kaydet" (tıklanır)`. */
        fun satir(): String {
            val ozellik = listOfNotNull(
                if (tiklanir) "tıklanır" else null, if (yazilir) "yazılır" else null,
                if (kaydirilir) "kaydırılır" else null, if (secili) "seçili" else null,
            )
            val m = if (metin.isBlank()) "" else " \"${metin.take(MAKS_METIN)}\""
            return "[$no] $tur$m" + if (ozellik.isEmpty()) "" else " (${ozellik.joinToString()})"
        }
    }

    const val MAKS_METIN = 80
    /** Gemini'ye giden ekran metninin sınırı (istek küçük kalsın). */
    const val MAKS_SATIR = 150

    fun yaz(uygulama: String, dugumler: List<Dugum>): String =
        "Uygulama: $uygulama\n" + dugumler.take(MAKS_SATIR).joinToString("\n") { it.satir() }

    /** Android sınıf adından okunur tür ("android.widget.Button" → "düğme"). */
    fun tur(sinif: String?): String {
        val s = sinif?.substringAfterLast('.').orEmpty()
        return when {
            s.contains("EditText") -> "yazı alanı"
            s.contains("Button") -> "düğme"
            s.contains("CheckBox") || s.contains("Switch") -> "anahtar"
            s.contains("Image") -> "görsel"
            s.contains("Tab") -> "sekme"
            s.contains("RecyclerView") || s.contains("ListView") || s.contains("ScrollView") -> "liste"
            s.contains("SeekBar") -> "kaydırıcı"
            s.contains("TextView") -> "yazı"
            else -> "öğe"
        }
    }

    private val TR = Locale("tr")
    private val TEMIZ = Regex("[^\\p{L}\\p{N} ]")
    private val BOSLUK = Regex("\\s+")
    private fun sade(s: String) = s.lowercase(TR).replace(TEMIZ, " ").replace(BOSLUK, " ").trim()

    /**
     * Söylenen ada en uygun tıklanır öğe ("Kaydet'e dokun"): tam eşleşme, sonra başlangıç, sonra içinde geçme.
     * null: bulunamadı.
     */
    fun bul(dugumler: List<Dugum>, ad: String): Dugum? {
        val a = sade(ad).takeIf { it.isNotEmpty() } ?: return null
        val adaylar = dugumler.filter { it.metin.isNotBlank() }
        val sirali = adaylar.sortedByDescending { it.tiklanir || it.yazilir }
        return sirali.firstOrNull { sade(it.metin) == a }
            ?: sirali.firstOrNull { sade(it.metin).startsWith(a) }
            ?: sirali.firstOrNull { sade(it.metin).contains(a) }
            ?: sirali.firstOrNull { a.length >= 4 && a.contains(sade(it.metin)) && sade(it.metin).length >= 3 }
    }

    /** Geri alınamayan iş düğmesi: basmadan önce sesli onay istenir (kullanıcı kararı 2026-11-01). */
    private val RISK = Regex(
        "(?<!\\p{L})(sil|silin|kalıcı olarak|kaldır|gönder|paylaş|satın al|öde|ödeme|abone ol|abonelikten çık|boşalt|" +
            "sıfırla|çıkış yap|oturumu kapat|hesabı sil|onayla|yayınla|delete|remove|send|share|buy|pay|confirm|empty)(?!\\p{L})",
    )

    fun riskli(etiket: String): Boolean = RISK.containsMatchIn(etiket.lowercase(TR))
}

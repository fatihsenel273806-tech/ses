package com.ses.asistan.yonlendirme

import java.util.Locale

/**
 * Söylenenin nereye gideceği (PLAN §1). Android'e bağlı değil: JVM testiyle sınanır. Sıra:
 *  1. [once]: uyanma adı ("Hey Harita …", "Hey Kanallar …"); cümlenin başında söylenen ad da sayılır. (Uygulamanın
 *     kendi dinleme isteği — `DİNLE`, `DİKTE` — buraya hiç gelmez: duyulan doğrudan o uygulamaya gider.)
 *  2. Dicio becerileri (bizimkiler dahil) — bu sınıfın dışında, SkillEvaluator'da.
 *  3. [sonra]: hiçbir beceri anlamadıysa metin devri: devri o an kabul eden bağlı uygulama (ör. navigasyondaki Harita).
 *  4. Gemini (Faz 6).
 */
object Yonlendirme {
    /** Asistanın kendi becerisi olan uyanma adı (köprüsü olan uygulama değil). */
    const val KANALLAR = "kanallar"
    const val ASISTAN = "asistan"

    /** Metin devrini ne zaman kabul ettiği (`ses_yetenekler.xml` › `devir`). */
    enum class Devir { HAYIR, NAVIGASYONDA, ONDE, HER_ZAMAN;
        companion object {
            fun oku(s: String?) = when (s?.trim()?.lowercase(Locale.ROOT)) {
                "navigasyonda" -> NAVIGASYONDA
                "onde", "önde" -> ONDE
                "her_zaman" -> HER_ZAMAN
                else -> HAYIR
            }
        }
    }

    /** Köprüsü olan bir uygulama ve o anki durumu (bağlıysa bildirdiği DURUM'lar). */
    data class Aday(
        val paket: String,
        val uyanma: String?,
        val devir: Devir,
        val navigasyonda: Boolean = false,
        val onde: Boolean = false,
    ) {
        fun devirAlir() = when (devir) {
            Devir.HAYIR -> false
            Devir.HER_ZAMAN -> true
            Devir.NAVIGASYONDA -> navigasyonda
            Devir.ONDE -> onde || navigasyonda
        }
    }

    sealed interface Karar {
        /** Metin uygulamaya devredilir; [uyanma]: uyanma sözünden hemen sonra söylendi (ilk hece kaçmış olabilir). */
        data class Uygulamaya(val paket: String, val metin: String, val uyanma: Boolean) : Karar
        /** "Hey Kanallar …": yalnız Kanallarım becerisi dinlenir. */
        data class Kanallar(val metin: String) : Karar
    }

    private val TR = Locale("tr")
    private val ONEK = Regex("^(?:hey|hay|hei|ey)?\\s*(\\p{L}+)[,.!]?\\s*", RegexOption.IGNORE_CASE)

    /** Cümlenin başındaki uyanma adı (bilinenlerden biriyse) ve geri kalanı. */
    fun basindakiAd(metin: String, adlar: Collection<String>): Pair<String, String>? {
        val m = ONEK.find(metin.trim()) ?: return null
        val ad = m.groupValues[1].lowercase(TR)
        if (ad !in adlar) return null
        return ad to metin.trim().substring(m.range.last + 1).trim()
    }

    /** 1. adım. [uyanmaAdi]: Vosk'un duyduğu ad. null: Dicio becerilerine geçilir. */
    fun once(metin: String, uyanmaAdi: String?, adaylar: List<Aday>): Karar? {
        val adlar = adaylar.mapNotNull { it.uyanma } + KANALLAR
        val bas = basindakiAd(metin, adlar)
        val ad = uyanmaAdi?.takeIf { it != ASISTAN } ?: bas?.first ?: return null
        val kalan = if (bas != null && bas.first == ad) bas.second else metin.trim()
        if (ad == KANALLAR) return Karar.Kanallar(kalan)
        val a = adaylar.firstOrNull { it.uyanma == ad } ?: return null
        return Karar.Uygulamaya(a.paket, kalan, uyanma = uyanmaAdi != null)
    }

    /** 3. adım: hiçbir beceri anlamadı; devri o an kabul eden uygulama (önce navigasyondaki). */
    fun sonra(metin: String, adaylar: List<Aday>): Karar? {
        val a = adaylar.filter { it.devirAlir() }.maxByOrNull { if (it.navigasyonda) 2 else if (it.onde) 1 else 0 } ?: return null
        return Karar.Uygulamaya(a.paket, metin.trim(), uyanma = false)
    }
}

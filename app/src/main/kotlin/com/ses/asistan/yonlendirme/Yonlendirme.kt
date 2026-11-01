package com.ses.asistan.yonlendirme

import java.util.Locale

/**
 * Söylenenin nereye gideceği (PLAN §1). Android'e bağlı değil: JVM testiyle sınanır. Sıra:
 *  0. [once]: "notlar uygulamasını aç", "kanallarımı aç", "Hey Harita, aç" → o uygulama açılır ([Karar.Ac]).
 *  1. [once]: uyanma adı ("Hey Harita …", "Hey Kanallar …", "Hey Notlar …"); cümlenin başında söylenen ad da sayılır.
 *     Ad ipucudur: not komutu her adla Notlar'a, adın becerisi anlamazsa 2. adıma geçilir (Yonlendirici). (Uygulamanın
 *     kendi dinleme isteği — `DİNLE`, `DİKTE` — buraya hiç gelmez: duyulan doğrudan o uygulamaya gider.)
 *  2. Dicio becerileri (bizimkiler dahil) — bu sınıfın dışında, SkillEvaluator'da.
 *  3. [sonra]: hiçbir beceri anlamadıysa metin devri: devri o an kabul eden bağlı uygulama (ör. navigasyondaki Harita).
 *  4. Gemini (Faz 6).
 */
object Yonlendirme {
    /** Asistanın kendi becerisi olan uyanma adı (köprüsü olan uygulama değil). */
    const val KANALLAR = "kanallar"
    /** Notlar'ın köprü servisi yok: yayın alıcısıyla asistanın kendi becerisi konuşur (Faz 4). */
    const val NOTLAR = "notlar"
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
        /** "Hey Kanallar …": önce Kanallarım becerisi (anlamazsa diğer beceriler; not komutu Notlar'ın). */
        data class Kanallar(val metin: String) : Karar
        /** "Hey Notlar …": önce Notlar becerisi (Notlar'ın köprü servisi yoksa; anlamazsa diğer beceriler). */
        data class Notlar(val metin: String) : Karar
        /** Uygulamanın kendisi açılır; [ad]: [ACILAN]'daki anahtar (harita, kanallar, notlar, dosyalar). */
        data class Ac(val ad: String) : Karar
    }

    /**
     * Adıyla açılabilen bağlı uygulamalar ve söylenişleri (ekli/eksiz). Telefonda: "notlar uygulamasını aç" Notlar
     * becerisine, "kanallarımı aç" kaldığı yerden sürdürmeye düşüyordu (kullanıcı 2026-11-01).
     */
    val ACILAN = linkedMapOf(
        "harita" to "harita(?:yı|ya)?",
        KANALLAR to "kanallar(?:ım)?(?:ı)?|kanallarım'?ı",
        NOTLAR to "not(?:lar(?:ım)?(?:ı)?)?|notlar'?ı",
        "dosyalar" to "dosya okuyucu(?:'?yu|yu)?",
    )
    private const val UYG = "(?:uygulama(?:sı|sını|yı)?|app'?i?)"
    private const val ACMA = "(?:aç|başlat|çalıştır|göster|getir)(?:ır mısın|ar mısın|abilir misin|ır mısınız)?(?: lütfen)?"
    private val AC_ADLI = ACILAN.map { (ad, r) -> ad to Regex("^(?:hey |ey )?(?:$r)(?: $UYG)? $ACMA$") }
    private val AC_YALIN = Regex("^(?:$UYG )?$ACMA$")
    /** Uygulamalar arası geçiş: "notlara geç", "haritaya dön", "kanallarıma geç" (2026-11-01). */
    private val GECIS = mapOf(
        "harita" to "haritaya", KANALLAR to "kanallar(?:ım)?a", NOTLAR to "notlar(?:ım)?a", "dosyalar" to "dosya okuyucu(?:'?ya|ya)",
    ).map { (ad, r) -> ad to Regex("^(?:hey |ey )?(?:$r)(?: (?:uygulamasına|geri))? (?:geç|dön)(?:er misin|ebilir misin)?(?: lütfen)?$") }
    private val TEMIZ = Regex("[.!?,;:]")
    private val BOSLUK = Regex("\\s+")

    /** "notlar uygulamasını aç" → notlar. [uyanmaAdi] verilmişse ("Hey Notlar") yalnız "aç" / "uygulamayı aç" da yeter. */
    fun acilacak(metin: String, uyanmaAdi: String?): String? {
        val t = metin.lowercase(TR).replace(TEMIZ, " ").replace(BOSLUK, " ").trim()
        AC_ADLI.firstOrNull { it.second.matches(t) }?.let { return it.first }
        GECIS.firstOrNull { it.second.matches(t) }?.let { return it.first }
        return uyanmaAdi?.takeIf { it in ACILAN && AC_YALIN.matches(t) }
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
        val adlar = adaylar.mapNotNull { it.uyanma } + KANALLAR + NOTLAR
        val bas = basindakiAd(metin, adlar)
        acilacak(metin, uyanmaAdi?.takeIf { it != ASISTAN })?.let { return Karar.Ac(it) }
        val ad = uyanmaAdi?.takeIf { it != ASISTAN } ?: bas?.first ?: return null
        val kalan = if (bas != null && bas.first == ad) bas.second else metin.trim()
        acilacak(kalan, ad)?.let { return Karar.Ac(it) }
        if (ad == KANALLAR) return Karar.Kanallar(kalan)
        val a = adaylar.firstOrNull { it.uyanma == ad } ?: return if (ad == NOTLAR) Karar.Notlar(kalan) else null
        return Karar.Uygulamaya(a.paket, kalan, uyanma = uyanmaAdi != null)
    }

    /** 3. adım: hiçbir beceri anlamadı; devri o an kabul eden uygulama (önce navigasyondaki). */
    fun sonra(metin: String, adaylar: List<Aday>): Karar? {
        val a = adaylar.filter { it.devirAlir() }.maxByOrNull { if (it.navigasyonda) 2 else if (it.onde) 1 else 0 } ?: return null
        return Karar.Uygulamaya(a.paket, metin.trim(), uyanma = false)
    }
}

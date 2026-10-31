package com.ses.asistan.beceri

import java.util.Locale

/**
 * Dosya Okuyucu sesli komutları (PLAN Faz 5): "X dosyasını aç", "son dosyayı aç", "sesli oku", "okumayı durdur",
 * "özetle". Android'e bağlı değil (JVM testi). Komutlar Dosya Okuyucu'nun kişisel derlemesindeki köprüye `KOMUT`
 * olarak gider ([Dosyalar]); dosya adında arama orada yapılır.
 *
 * Cümlede "dosya", "belge", "pdf" … gibi bir sözcük şarttır: "haritayı aç", "son notumu oku" gibi başka becerilerin
 * cümleleri buraya düşmesin.
 */
object DosyalarSozleri {
    sealed interface Komut {
        /** Komutun köprüdeki adı (PROTOKOL.md › "Dosya Okuyucu komutları"). */
        val ad: String
        /** Dosya adında aranacak söz; boşsa son açılan (ya da açık olan) dosya. */
        val sorgu: String

        data class Ac(override val sorgu: String) : Komut { override val ad get() = AC }
        data class SesliOku(override val sorgu: String) : Komut { override val ad get() = OKU }
        data object Durdur : Komut { override val ad get() = DURDUR; override val sorgu get() = "" }
        data class Ozetle(override val sorgu: String) : Komut { override val ad get() = OZETLE }
    }

    const val AC = "ac"
    const val OKU = "oku"
    const val DURDUR = "durdur"
    const val OZETLE = "ozetle"

    private val TR = Locale("tr")
    private val NOKTALAMA = Regex("[.!?,;:]")
    private val BOSLUK = Regex("\\s+")

    // "açar mısın", "okur musun", "özetleyebilir misin", "durdurur musun"
    private const val RICA = "(?: lütfen)?(?:[ıiuüae]?r m[ıiuü]s[ıiuü]n(?:ız)?|(?:y)?[ae]bilir misin(?:iz)?)?(?: lütfen)?"
    /** "dosyasını", "belgeyi", "pdf'ini", "word dosyasını" …: türün kendisi ve iyelik/belirtme ekleri. */
    private const val TUR =
        "(?:dosya|belge|doküman|döküman|pdf|word|excel|sunum|tablo)(?:['’]?(?:y[ıi]|s[ıi]n[ıi]|s[ıi]|[ıi]|[ıi]n[ıi]|m[ıi]|lar[ıi]n[ıi]))?"
    private const val SON = "(?:en )?son (?:açtığım |açılan |okuduğum )?"
    private const val BU = "(?:bu |şu |açık |açtığım )?"
    private const val YERDE = "(?: dosya okuyucu(?:da|['’]da)?)?"

    private val SON_AC = Regex("^$SON$TUR$YERDE (?:aç|göster|getir)$RICA$")
    private val AC_ = Regex("^(.+?) $TUR$YERDE (?:aç|göster|getir|bul)$RICA$")
    private val DURDUR_ = Regex(
        "^(?:sesli )?okuma(?:yı|y[ıi])? (?:durdur|kes|bitir|kapat)$RICA$|^(?:sesli )?okumayı bırak$RICA$",
    )
    private val OKU_BU = Regex("^(?:$BU$TUR )?(?:sesli |yüksek sesle )oku$RICA$|^$BU$TUR oku$RICA$")
    private val OKU_SON = Regex("^$SON$TUR (?:sesli |yüksek sesle )?oku$RICA$")
    private val OKU_ = Regex("^(.+?) $TUR (?:sesli |yüksek sesle )oku$RICA$")
    private val OZET_BU = Regex("^(?:$BU$TUR )?özetle$RICA$|^$BU$TUR(?:nın|nin|ın|in)? özet(?:i|ini)(?: çıkar| söyle| oku)?$RICA$")
    private val OZET_SON = Regex("^$SON$TUR özetle$RICA$")
    private val OZET_ = Regex("^(.+?) $TUR özetle$RICA$")

    /** Sözün başındaki "bu", "şu" gibi göstericiler arama sözü değildir. */
    private val GOSTERICI = setOf("bu", "şu", "o", "açık", "açtığım")
    /** "bütçe excel dosyası": tür adı dosya adında geçmez, aramadan atılır. */
    private val TUR_ADI = setOf("pdf", "word", "excel", "sunum", "tablo", "belge", "doküman", "döküman")

    fun parse(raw: String): Komut? {
        val t = raw.lowercase(TR).replace(NOKTALAMA, " ").replace(BOSLUK, " ").trim()
        if (DURDUR_.matches(t)) return Komut.Durdur
        if (SON_AC.matches(t)) return Komut.Ac("")
        if (OKU_SON.matches(t)) return Komut.SesliOku("")
        if (OZET_SON.matches(t)) return Komut.Ozetle("")
        if (OKU_BU.matches(t)) return Komut.SesliOku("")
        if (OZET_BU.matches(t)) return Komut.Ozetle("")
        AC_.matchEntire(t)?.let { m -> sorgu(m.groupValues[1])?.let { return Komut.Ac(it) } }
        OKU_.matchEntire(t)?.let { m -> sorgu(m.groupValues[1])?.let { return Komut.SesliOku(it) } }
        OZET_.matchEntire(t)?.let { m -> sorgu(m.groupValues[1])?.let { return Komut.Ozetle(it) } }
        return null
    }

    /** Arama sözü: kesme ekli kelimenin eki atılır ("Ahmet'in" → "ahmet"), baştaki gösterici ve sondaki tür adı atılır; boş kalırsa null. */
    private fun sorgu(s: String): String? {
        val w = s.split(' ').filter { it.isNotEmpty() }.map { it.substringBefore('\'').substringBefore('’') }
        val anlamli = w.dropWhile { it in GOSTERICI }.dropLastWhile { it in TUR_ADI }
        return anlamli.joinToString(" ").ifEmpty { null }
    }
}

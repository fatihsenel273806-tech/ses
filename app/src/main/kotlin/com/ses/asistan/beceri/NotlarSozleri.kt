package com.ses.asistan.beceri

import java.util.Locale

/**
 * Notlar sesli komutları (PLAN Faz 4): "not al …", "notlarımda X'i ara", "son notumu oku", "son (üç) notum".
 * "Not al" kuralı Harita `Assistant.kt` › `NOTE`'tan birebir. Android'e bağlı değil (JVM testi).
 *
 * "Videoya not al" burada değil: o Kanallarım'ın ([KanallarSozleri]); "videoya" cümlenin başında olduğu için
 * buradaki kurallara zaten uymaz.
 */
object NotlarSozleri {
    sealed interface Komut {
        /** [metin] boşsa dikte edilir. */
        data class NotAl(val metin: String) : Komut
        /** [terimler]: notta hepsi geçmeli (küçük harf, ekleri atılmış). [soylenen]: cevapta okunur. */
        data class Ara(val terimler: List<String>, val soylenen: String) : Komut
        data object SonuOku : Komut
        data class Son(val adet: Int) : Komut
    }

    private val TR = Locale("tr")
    private val NOKTALAMA = Regex("[.!?,;:]")
    private val BOSLUK = Regex("\\s+")

    // Harita Assistant.kt NOTE (birebir)
    private val NOTE = Regex(
        "^(?:not (?:al|et|yaz|düş)|nota (?:ekle|yaz|al))(?:ır mısın|ar mısın|abilir misin|ır mısınız)?(?=\\s|[:,.]|$)[\\s:,.-]*(.*)$",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
    // "okur musun", "bulur musun", "arar mısın", "okuyabilir misin"
    private const val RICA = "(?: lütfen)?(?:[ıiuüae]?r m[ıiuü]s[ıiuü]n|(?:y)?abilir misin|(?:y)?ebilir misin)?(?: lütfen)?"
    private val SONU_OKU = Regex("^(?:en )?son not(?:umu|um|u|unuz|unuzu)? ?(?:oku|söyle|ne|neydi|nedir|ne yazıyor)?$RICA$")
    private const val SAYI = "(bir|iki|üç|dört|beş|altı|yedi|sekiz|dokuz|on|\\d+)"
    private val SON = Regex("^(?:en )?son (?:$SAYI )?not(?:lar)?(?:ım|ımı|ı|u|um|umu|larım|larımı|ları)? ?(?:oku|say|söyle|neler|ne|neler var|göster)?$RICA$")
    private val ARA = listOf(
        // notlarımda X ara / notlarda X var mı / notlarımı X için ara
        Regex("^not(?:lar)?(?:ım)?(?:da|ı|ımı|ları)? (.+?)(?: için)? (?:ara|bul|var mı|geçiyor mu)$RICA$"),
        // X ile ilgili notları bul / X hakkındaki notu oku / X geçen notlar
        Regex("^(.+?) (?:ile ilgili|hakkında(?:ki)?|geçen|yazan) not(?:lar)?(?:ım|ımı|ı|u|um|umu|larımı|ları)? ?(?:bul|ara|oku|göster|neler|var mı)?$RICA$"),
        // X notunu bul / X notumu oku
        Regex("^(.+?) not(?:unu|umu|u|larını|larımı) (?:bul|ara|oku|göster|aç)$RICA$"),
    )
    private val ATILAN = setOf("bir", "şu", "bu", "ile", "için", "ilgili", "benim", "tüm", "bütün", "olan")
    private val KESME = Regex("['’`]\\p{L}*$")
    private val BELIRTME = Regex("(?<=\\p{L}{3})(?:[sy]?[ıiuü]|n[ıiuü]n|[dt][ae]ki?|[dt][ae]n)$")

    /** [raw]: uyanma sözü atılmış cümle (not metni büyük/küçük harfiyle kalsın). */
    fun parse(raw: String): Komut? {
        NOTE.find(raw.trim())?.let { return Komut.NotAl(it.groupValues[1].trim().trimEnd('.')) }
        val t = raw.lowercase(TR).replace(NOKTALAMA, " ").replace(BOSLUK, " ").trim()
        if (SONU_OKU.matches(t)) return Komut.SonuOku
        SON.matchEntire(t)?.let { m -> return Komut.Son(sayi(m.groupValues[1]) ?: 3) }
        for (r in ARA) {
            val m = r.matchEntire(t) ?: continue
            val s = m.groupValues[1].trim()
            val terimler = terimler(s)
            if (terimler.isNotEmpty()) return Komut.Ara(terimler, s)
        }
        return null
    }

    /** Söylenen arama ifadesinin kelimeleri, Türkçe ekleri atılmış: notta alt dize olarak aranır. */
    fun terimler(s: String): List<String> = s.lowercase(TR).split(BOSLUK)
        .map { it.replace(KESME, "").trim('\'', '"', '“', '”') }
        .filter { it.length >= 2 && it !in ATILAN }
        .map { if (it.length >= 5) it.replace(BELIRTME, "") else it }

    private fun sayi(w: String): Int? = w.toIntOrNull() ?: when (w) {
        "bir" -> 1; "iki" -> 2; "üç" -> 3; "dört" -> 4; "beş" -> 5; "altı" -> 6; "yedi" -> 7; "sekiz" -> 8
        "dokuz" -> 9; "on" -> 10
        else -> null
    }

    private val SAYILAR = listOf("", "bir", "iki", "üç", "dört", "beş", "altı", "yedi", "sekiz", "dokuz", "on")

    /** Bir notun sesli okunuşu: başlık ve metin, metin [enCok] karakterde kelime sınırından kesilir. */
    fun notCumlesi(baslik: String, metin: String, enCok: Int = 300): String {
        val m = if (metin.length <= enCok) metin else metin.take(enCok).substringBeforeLast(' ') + "…"
        return listOf(baslik, m).filter { it.isNotBlank() }.joinToString(". ")
    }

    /** Listede notun adı: başlığı, yoksa metninin ilk birkaç kelimesi. */
    fun ad(baslik: String, metin: String): String =
        baslik.ifBlank { metin.split(BOSLUK).take(6).joinToString(" ").let { if (metin.length > it.length) "$it…" else it } }

    fun adetSozu(n: Int) = SAYILAR.getOrNull(n) ?: n.toString()
}

package com.ses.asistan.ajan

import java.util.Locale

/**
 * Gemini olmadan da çalışan ekran komutları (bağlı dört uygulamada): "aşağı kaydır", "geri git", "Kaydet'e dokun",
 * "arama kutusuna Kızılay yaz", "ekranda ne yazıyor". Android'e bağlı değil (JVM testi).
 */
object EkranSozleri {
    sealed interface Komut {
        data class Kaydir(val yon: String) : Komut
        data object Geri : Komut
        data class Dokun(val ad: String) : Komut
        data class Yaz(val alan: String, val metin: String) : Komut
        data object Oku : Komut
    }

    private val TR = Locale("tr")
    private val NOKTALAMA = Regex("[.!?,;:]")
    private val BOSLUK = Regex("\\s+")
    private const val RICA = "(?: lütfen)?(?:[ıiuüae]?r m[ıiuü]s[ıiuü]n|(?:y)?[ae]bilir mis[ıi]n)?(?: lütfen)?"

    private val KAYDIR = Regex("^(?:sayfayı |ekranı |listeyi )?(aşağı|yukarı|sağa|sola|aşağıya|yukarıya)(?: doğru)? (?:kaydır|in|çık|git)$RICA$")
    private val GERI = Regex("^(?:bir )?geri(?: (?:git|dön|gel|al))?$RICA$|^önceki (?:ekrana|sayfaya) dön$RICA$")
    private val DOKUN = Regex(
        "^(.+?)(?:'?(?:[yn]?[ae]|[yn]?[ıiuü]))?(?: (?:düğmesine|butonuna|yazısına|sekmesine|seçeneğine|simgesine|tuşuna))? " +
            "(?:dokun|tıkla|bas)$RICA$",
    )
    private val YAZ = Regex("^(.+?) (?:kutusuna|alanına|kısmına) (.+?) (?:yaz|gir)$RICA$")
    private val OKU = Regex("^(?:ekranda|sayfada) ne (?:yazıyor|var)$RICA$|^ekranı oku$RICA$")
    /** "geri sar", "not al" gibi başka becerilerin sözleri dokunma adı sanılmasın. */
    private val YASAK = setOf("geri", "ileri", "not", "video", "harita")

    fun parse(raw: String): Komut? {
        val t = raw.lowercase(TR).replace(NOKTALAMA, " ").replace(BOSLUK, " ").trim()
        KAYDIR.matchEntire(t)?.let { m ->
            return Komut.Kaydir(when (m.groupValues[1].removeSuffix("ya")) {
                "aşağı" -> "asagi"; "yukarı" -> "yukari"; "sağa" -> "saga"; else -> "sola"
            })
        }
        if (GERI.matches(t)) return Komut.Geri
        if (OKU.matches(t)) return Komut.Oku
        YAZ.matchEntire(t)?.let { return Komut.Yaz(it.groupValues[1], it.groupValues[2]) }
        DOKUN.matchEntire(t)?.let { m ->
            val ad = m.groupValues[1].trim().trimEnd('\'', '’')
            if (ad.isNotEmpty() && ad !in YASAK) return Komut.Dokun(ad)
        }
        return null
    }

    /** "Ekranda ne yazıyor": görünen yazılar (tekrarsız), en çok [adet]. */
    fun okunus(dugumler: List<EkranMetni.Dugum>, adet: Int = 12): String =
        dugumler.map { it.metin }.filter { it.isNotBlank() }.distinct().take(adet).joinToString(". ")
}

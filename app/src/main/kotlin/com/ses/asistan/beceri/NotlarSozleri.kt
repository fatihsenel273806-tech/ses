package com.ses.asistan.beceri

import java.util.Locale

/**
 * Notlar sesli komutları (PLAN Faz 4): "not al …", "notlarımda X'i ara", "son notumu oku", "son (üç) notum".
 * "Not al" kuralının çekirdeği Harita `Assistant.kt` › `NOTE`'tan birebir; 2026-11-01'de genişletildi (kullanıcı:
 * "not akışı her yerde doğru çalışmalı … ben ne dersem o"): "X diye not al", "yeni bir not aç içine X yaz", "şunu not
 * al", ve cümlenin içindeki ekler — "ekran görüntüsü (de) ekle", "konumu(mu) ekle", "video notu / videoya not". Ek
 * istenmediyse düz not. Yalnız "ekran görüntüsü al" da nottur (görüntü + dikte edilen açıklama). Android'e bağlı değil
 * (JVM testi). Video notu da burada (Kanallarım becerisi not komutlarını bu sınıfa bırakır).
 */
object NotlarSozleri {
    sealed interface Komut {
        /**
         * [metin] boşsa dikte edilir. [ekran]: ekran görüntüsü eklensin; [konum]: bulunulan yer eklensin; [video]:
         * Kanallarım'da çalan videonun notuna (o anın dakikasıyla).
         */
        data class NotAl(
            val metin: String, val ekran: Boolean = false, val konum: Boolean = false, val video: Boolean = false,
        ) : Komut
        /** [terimler]: notta hepsi geçmeli (küçük harf, ekleri atılmış). [soylenen]: cevapta okunur. */
        data class Ara(val terimler: List<String>, val soylenen: String) : Komut
        data object SonuOku : Komut
        data class Son(val adet: Int) : Komut
    }

    private val TR = Locale("tr")
    private val NOKTALAMA = Regex("[.!?,;:]")
    private val BOSLUK = Regex("\\s+")
    private val SECENEK = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)

    /** "alır mısın", "eder misin", "yazabilir misin", "ekler misin" … */
    private const val RICA_NOT = "(?:[ıiuüae]?r m[ıiuü]s[ıiuü]n(?:ız)?|(?:y)?[ae]bilir mis[ıi]n(?:iz)?)?(?: lütfen)?"
    private const val SON = "(?=\\s|[:,.]|$)[\\s:,.-]*"
    /** "not al/et/yaz…"; "et" ünlüyle yumuşar: "not eder misin", "not edebilir misin". */
    private const val NOT_FIIL = "(?:al|et|ed(?=er m|ebilir)|yaz|düş|kaydet|kayded(?=er m|ebilir))"

    // Harita Assistant.kt NOTE (çekirdek birebir; "not yaz/ekle/kaydet", "notuma/notlara ekle" eklendi)
    private val NOTE = Regex(
        "^(?:not $NOT_FIIL|nota (?:ekle|yaz|al|geç|kaydet)|not(?:uma|lara|larıma) (?:ekle|yaz|kaydet))" +
            "$RICA_NOT$SON(.*)$",
        SECENEK,
    )
    /** "yeni bir not aç içine X yaz", "not oluştur X", "bir not aç ve içerisine X yaz". */
    private val YENI = Regex(
        "^(?:yeni )?(?:bir )?not (?:aç|oluştur|başlat|yarat)$RICA_NOT$SON(?:ve )?" +
            "(?:(?:içine|içerisine|içinde|içerisinde|ona|altına)\\s+)?(.*?)(?:\\s+(?:yaz|ekle|kaydet|koy)$RICA_NOT)?$",
        SECENEK,
    )
    /** "süt al diye not al", "yarın toplantı var not et", "X diye nota ekle", "X not olarak kaydet". */
    private val SONDA = Regex(
        "^(.+?)\\s+(?:(?:diye|şeklinde|yazan)\\s+)?(?:bir )?(?:not $NOT_FIIL|nota (?:ekle|yaz|al|geç|kaydet)|" +
            "not olarak (?:kaydet|ekle|al|yaz))$RICA_NOT$",
        SECENEK,
    )
    /** Ek yoksa da not sayılan kalıntı ("ekran görüntüsü al" sonrası "nota ekle", "not olarak kaydet"). */
    private val YALIN = Regex("^(?:(?:bir )?not(?:a| olarak)? ?(?:ekle|kaydet|al|yaz|aç)?$RICA_NOT|kaydet|ekle)?$", SECENEK)

    /** Ekran görüntüsü isteği (cümleden çıkarılır). */
    private val EKRAN = Regex(
        "(?<!\\p{L})(?:bir )?(?:ekran (?:görüntüsü|resmi|fotoğrafı)(?:nü|nu|ü|u|n[ıi])?|ss|screenshot|ekranı)" +
            "(?: de| da)?(?: (?:al(?:ıp|arak|ır mısın)?|ekle(?:yip|yerek|r misin)?|çek(?:ip|erek|er misin)?|koy(?:up)?|kaydet(?:ip)?|yakala))?" +
            "(?=\\s|[:,.]|$)",
        SECENEK,
    )
    /** Konum isteği: "konumu ekle", "konumumu da yaz", "bulunduğum yeri ekle". */
    private const val KONUM_FIIL = "(?:ekle(?:yip|yerek|r misin)?|yaz(?:ıp|arak)?|koy(?:up)?|kaydet(?:ip)?|işaretle)"
    private val KONUM = Regex(
        "(?<!\\p{L})(?:(?:konum(?:umu|unu|u)|bulunduğum (?:yer|konum)(?:i|u)?|olduğum yer(?:i)?|nerede olduğumu)(?: de| da)?(?: $KONUM_FIIL)?" +
            "|konum(?: de| da)? $KONUM_FIIL)(?=\\s|[:,.]|$)",
        SECENEK,
    )
    /** Video notu: "videoya not al" → "not al", "video notu(na) …" → "not(a) …", "videoyla ilgili not al". */
    private val VIDEO = Regex(
        "(?:(?:bu |şu )?videoya|videoyla ilgili|video için|kanallarım'?a) (?=not)|(?:bu |şu )?video not(?=u|una|larına)(?:u(?:na)?|larına)",
        SECENEK,
    )
    private val BAGLAC = Regex("^(?:ve|ile|de|da|ayrıca|sonra|bir de|şunu|bunu)\\s+|\\s+(?:ve|ile|de|da|ayrıca|sonra)$", SECENEK)
    private val SON_BAGLAC = Regex("\\s+(?:ve|ile|de|da|ayrıca|sonra)$", SECENEK)
    private val DIYE = Regex("\\s+(?:diye|şeklinde)$", SECENEK)

    // "okur musun", "bulur musun", "arar mısın", "okuyabilir misin"
    private const val RICA = "(?: lütfen)?(?:[ıiuüae]?r m[ıiuü]s[ıiuü]n|(?:y)?abilir misin|(?:y)?ebilir misin)?(?: lütfen)?"
    private val SONU_OKU = Regex("^(?:en )?son not(?:umu|um|u|unuz|unuzu)? ?(?:oku|söyle|ne|neydi|nedir|ne yazıyor)?$RICA$")
    private const val SAYI = "(bir|iki|üç|dört|beş|altı|yedi|sekiz|dokuz|on|\\d+)"
    private val SON_N = Regex("^(?:en )?son (?:$SAYI )?not(?:lar)?(?:ım|ımı|ı|u|um|umu|larım|larımı|ları)? ?(?:oku|say|söyle|neler|ne|neler var|göster)?$RICA$")
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

    /** Not alma komutu mu (Kanallarım becerisi bunları Notlar'a bırakır). */
    fun notMu(raw: String) = notAl(raw) != null

    /**
     * Not alma komutu: önce ekler (ekran, konum, video) cümleden çıkarılır, kalan "not al" kalıplarına bakılır.
     * Yalnız ekran görüntüsü istendiyse ("ekran görüntüsü al") de nottur; yalnız konum değil ("konumum ne" not değil).
     */
    fun notAl(raw: String): Komut.NotAl? {
        var t = raw.trim().trimEnd('.', '!', '?').replace(BOSLUK, " ")
        val video = VIDEO.containsMatchIn(t)
        if (video) t = VIDEO.replace(t) { m ->
            val v = m.value.lowercase(TR)
            if (v.endsWith("una") || v.endsWith("larına")) "nota" else if (v.endsWith("u")) "not" else ""
        }
        // konum yalnız not cümlesinde ek sayılır ("konumumu söyle" not değil); "ekranı kapat" ekran görüntüsü değil
        val ekran = EKRAN.find(t)?.takeIf { ekranMi(it.value) }
        if (ekran != null) t = t.removeRange(ekran.range)
        val konum = if (t.contains("not", ignoreCase = true) || ekran != null) KONUM.find(t) else null
        if (konum != null) t = t.removeRange(konum.range)
        t = temizle(t)
        val metin = when {
            NOTE.find(t) != null -> NOTE.find(t)!!.groupValues[1]
            YENI.matches(t) -> YENI.matchEntire(t)!!.groupValues[1]
            SONDA.matches(t) -> SONDA.matchEntire(t)!!.groupValues[1]
            (ekran != null || video) && YALIN.matches(t) -> ""
            video && t.startsWith("not ", ignoreCase = true) -> t.substring(4)
            else -> return null
        }
        // metnin başı nottur ("bunu sonra oku"): yalnız sondaki bağlaç atılır
        val m = metin.trim().trim(',', ':', '-', ' ').replace(SON_BAGLAC, "").replace(DIYE, "").trim().trimEnd('.')
        return Komut.NotAl(m, ekran != null, konum != null, video)
    }

    /** "ekranı" ve "ss" yalnız fiille ekran görüntüsüdür ("ekranı kapat" değil); "ekran görüntüsü" fiilsiz de. */
    private fun ekranMi(s: String): Boolean {
        val k = s.lowercase(TR).removePrefix("bir ")
        if (k.startsWith("ekranı")) return EKRAN_CEK.containsMatchIn(k)
        if (k.startsWith("ss") || k.startsWith("screenshot")) return k.contains(' ')
        return true
    }
    private val EKRAN_CEK = Regex("çek|kaydet|yakala")

    private fun temizle(s: String): String {
        var t = s.replace(BOSLUK, " ").trim().trim(',', ':', '-', ' ')
        while (true) {
            val y = t.replace(BAGLAC, "").trim().trim(',', ':', '-', ' ')
            if (y == t) return t
            t = y
        }
    }

    /** [raw]: uyanma sözü atılmış cümle (not metni büyük/küçük harfiyle kalsın). */
    fun parse(raw: String): Komut? {
        notAl(raw)?.let { return it }
        val t = raw.lowercase(TR).replace(NOKTALAMA, " ").replace(BOSLUK, " ").trim()
        if (SONU_OKU.matches(t)) return Komut.SonuOku
        SON_N.matchEntire(t)?.let { m -> return Komut.Son(sayi(m.groupValues[1]) ?: 3) }
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

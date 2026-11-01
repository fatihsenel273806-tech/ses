package com.ses.asistan.beceri

import java.util.Locale

/**
 * Kanallarım (ve çalan uygulama) sesli komutları. Harita `MediaWords` (sarma, baştan, "ne çalıyor", son izlenenler,
 * harita listesi, adıyla video/liste açma) ve `RuleParser.media` (duraklat, sonraki, kaldığım yer,
 * indirilenler, oynatma listeleri) kuralları birebir taşındı. Android'e bağlı değil (JVM testi).
 *
 * Sıra Harita'daki gibi: önce not komutları (video notu dahil; [NotlarSozleri]'ne bırakılır, içindeki "durdur" gibi
 * kelimeler komut sanılmasın), sonra MediaWords, sonra genel medya kuralları ("X videosunu aç" eskiden "video aç" sanılıp kaldığı yerden sürdürüyordu).
 */
object KanallarSozleri {
    /** RESUME: Kanallarım'da kaldığım yerden; PLAY: çalan/son uygulamayı sürdür. WHAT: "ne çalıyor". */
    enum class Eylem { RESUME, PLAY, PAUSE, NEXT, PREVIOUS, DOWNLOADS, PLAYLISTS, WHAT, RECENT, MAP_LIST }

    sealed interface Komut {
        data class Medya(val eylem: Eylem) : Komut
        /** [ms] ileri (+) / geri (−); [mutlak] ise videonun o anına ("baştan" 0). */
        data class Sar(val ms: Long, val mutlak: Boolean) : Komut
        /** Listelerde adıyla aranır. */
        data class Bul(val ad: String) : Komut
    }

    /** Genel medya komutu mu (Dicio'nun medya becerisiyle ortak): Kanallarım çalmıyorsa Dicio'nunki çalışır. */
    fun genel(k: Komut) = k is Komut.Medya && k.eylem in GENEL
    private val GENEL = setOf(Eylem.PLAY, Eylem.PAUSE, Eylem.NEXT, Eylem.PREVIOUS)

    private val TR = Locale("tr")
    private val NOKTALAMA = Regex("[!?,]")
    private val NOKTALAMA2 = Regex("[.!?,]")
    private val BOSLUK = Regex("\\s+")

    private const val NUM = "(\\d+|bir|iki|üç|dört|beş|altı|yedi|sekiz|dokuz|on|on beş|yirmi|otuz|kırk|kırk beş|elli|altmış|doksan|yarım)"
    private val SEEK = Regex("$NUM (saniye|sn|dakika|dk)(?:lık)? (geri|ileri|öne)")
    private val SEEK_TO = Regex("$NUM\\.? ?(?:dakika|dk)(?:ya|ye|sına|sine)? (?:git|atla|sar|gel|al)")
    private val BACK = Regex("(?<!\\p{L})(geri sar|biraz geri sar|geriye sar|videoyu geri)(?!\\p{L})")
    private val FORWARD = Regex("(?<!\\p{L})(ileri sar|öne sar|reklamı geç|videoyu ileri)(?!\\p{L})")
    private val RESTART = Regex("(?<!\\p{L})(baştan (başlat|oynat|izle|al|çal)|başa (sar|dön|al)|en başa)(?!\\p{L})")
    private val WHAT = Regex("ne (çalıyor|oynuyor|izliyorum|dinliyorum)|(hangi|ne) (video|şarkı|parça)(sı)? (bu|çalıyor)|bu (video|şarkı|parça) ne|çalan ne|ne izliyoruz")
    private val RECENT = Regex("son izle(nen|diğim|diklerim)|en son izlediğim|izleme geçmişi")
    // baştaki "harita" uyanma sözü sanılıp atılır: "harita listemi aç" buraya "listemi aç" diye gelir
    private val MAP_LIST = Regex("harita listem|harita listesi|^listem(i|e)?( aç| oynat| çal| başlat)?$")
    private val FIND = Regex("^(?:kanallarım'?da(?:n)?|kanallarımdan) (.+?)(?: videosu(?:nu)?| listesi(?:ni)?| bölümü(?:nü)?)? ?(?:aç|oynat|çal|başlat|izle|bul|izlet)?$")
    private val FIND_KIND = Regex("^(.+?) (?:videosunu|listesini|bölümünü|şarkısını|videosu|listesi) (?:aç|oynat|çal|başlat|izle|izlet|bul)$")
    private val NOT_A_NAME = setOf("oynatma", "harita", "indirilen", "son", "bu", "şu", "sonraki", "önceki", "sıradaki", "bir")

    // Harita RuleParser.MEDIA / MEDIA_ALONE
    private val MEDIA = listOf(
        Regex("indirilen") to Eylem.DOWNLOADS,
        Regex("oynatma listes|listelerim") to Eylem.PLAYLISTS,
        Regex("duraklat|(müziği|videoyu|sesi|yayını|podcasti?) durdur") to Eylem.PAUSE,
        Regex("(sonraki|sıradaki) (video|bölüm|parça|şarkı)|(videoyu|parçayı|şarkıyı) geç") to Eylem.NEXT,
        Regex("önceki (video|bölüm|parça|şarkı)") to Eylem.PREVIOUS,
        Regex("kaldığım yer|(dinlemeye|izlemeye) devam|devam ettir|(video|müzi[kğ]|podcast|kanallarım)\\S* ?(aç|başlat|çal|oynat|devam)") to Eylem.RESUME,
    )
    // tek kelimelik "durdur", "oynat": yalnızca cümlenin tamamıysa (kaydı durdur, navigasyonu başlat etkilenmesin)
    private val MEDIA_ALONE = listOf(
        Eylem.PAUSE to Regex("^(durdur|dur|duraklat|beklet|pause|stop)( lütfen)?$|^(müzi[kğ]i?|videoyu?|şarkıyı|parçayı|radyoyu|youtube'?u|yayını|podcasti?)( biraz)? (durdur|kapat|kes|beklet|duraklat)"),
        Eylem.PLAY to Regex("^(oynat|çal|devam|devam et|sürdür|play)( lütfen)?$|^(müzi[kğ]i?|videoyu?|şarkıyı|parçayı|radyoyu|youtube'?u|yayını) (oynat|çal|başlat|sürdür|aç|devam ettir)|^(müzi[kğ]e|videoya|şarkıya|yayına) devam"),
    )

    /** [raw]: uyanma sözü atılmış özgün cümle (not metni büyük/küçük harfiyle kalsın). */
    fun parse(raw: String): Komut? {
        // "videoya not al … durdur …": Notlar becerisinin (video notu orada)
        if (NotlarSozleri.notMu(raw)) return null
        val t = raw.lowercase(TR).replace(NOKTALAMA, " ").replace(BOSLUK, " ").trim().trimEnd('.')
        SEEK.find(t)?.let { m ->
            val n = number(m.groupValues[1]) ?: return@let
            val ms = (n * (if (m.groupValues[2].startsWith("d")) 60_000 else 1000)).toLong()
            return Komut.Sar(if (m.groupValues[3] == "geri") -ms else ms, mutlak = false)
        }
        SEEK_TO.find(t)?.let { m -> number(m.groupValues[1])?.let { return Komut.Sar((it * 60_000).toLong(), mutlak = true) } }
        if (RESTART.containsMatchIn(t)) return Komut.Sar(0, mutlak = true)
        if (BACK.containsMatchIn(t)) return Komut.Sar(-10_000, mutlak = false)
        if (FORWARD.containsMatchIn(t)) return Komut.Sar(30_000, mutlak = false)
        if (WHAT.containsMatchIn(t)) return Komut.Medya(Eylem.WHAT)
        if (RECENT.containsMatchIn(t)) return Komut.Medya(Eylem.RECENT)
        if (MAP_LIST.containsMatchIn(t)) return Komut.Medya(Eylem.MAP_LIST)
        (FIND.find(t) ?: FIND_KIND.find(t))?.let { m ->
            val q = m.groupValues[1].trim()
            if (q.length >= 2 && q.substringBefore(' ') !in NOT_A_NAME) return Komut.Bul(q)
        }
        return media(raw)
    }

    private fun media(raw: String): Komut.Medya? {
        val t = raw.lowercase(TR).replace(NOKTALAMA2, " ").replace(BOSLUK, " ").trim()
        MEDIA_ALONE.firstOrNull { it.second.containsMatchIn(t) }?.let { return Komut.Medya(it.first) }
        return MEDIA.firstOrNull { it.first.containsMatchIn(t) }?.let { Komut.Medya(it.second) }
    }

    private fun number(w: String): Double? = w.toDoubleOrNull() ?: when (w) {
        "yarım" -> 0.5; "bir" -> 1.0; "iki" -> 2.0; "üç" -> 3.0; "dört" -> 4.0; "beş" -> 5.0; "altı" -> 6.0; "yedi" -> 7.0
        "sekiz" -> 8.0; "dokuz" -> 9.0; "on" -> 10.0; "on beş" -> 15.0; "yirmi" -> 20.0; "otuz" -> 30.0; "kırk" -> 40.0
        "kırk beş" -> 45.0; "elli" -> 50.0; "altmış" -> 60.0; "doksan" -> 90.0
        else -> null
    }

    private val KELIME = Regex("[^\\p{L}\\p{N}]+")

    /**
     * Söylenen adla en iyi eşleşen başlık (-1: yok). Kelimeler ön ekle karşılaştırılır (Türkçe ekler: "belgeseli" ~
     * "belgesel"); söylenen kelimelerin en az yarısı başlıkta olmalı.
     */
    fun best(query: String, titles: List<String>): Int {
        val q = words(query)
        if (q.isEmpty()) return -1
        var bestI = -1
        var bestScore = 0.0
        titles.forEachIndexed { i, title ->
            val tw = words(title)
            if (tw.isEmpty()) return@forEachIndexed
            val hit = q.count { w -> tw.any { same(w, it) } }
            // tam eşleşen kısa başlık, uzun başlıktaki tek kelimeden önce gelsin
            val score = hit.toDouble() / q.size + hit.toDouble() / tw.size * 0.25
            if (hit * 2 >= q.size && score > bestScore) { bestScore = score; bestI = i }
        }
        return bestI
    }

    private fun words(s: String) = s.lowercase(TR).split(KELIME).filter { it.length >= 2 }

    private fun same(a: String, b: String): Boolean {
        if (a == b) return true
        val n = minOf(a.length, b.length)
        return n >= 4 && a.take(n - 1) == b.take(n - 1) || (n >= 3 && (a.startsWith(b) || b.startsWith(a)))
    }

    /** "Ne çalıyor" cevabı: başlık, kanal ve kaçıncı dakikada olunduğu (Harita `sayNowPlaying`). */
    fun calanCumlesi(baslik: String?, sanatci: String?, konumMs: Long): String {
        if (baslik == null) return "Şu an çalan bir şey yok."
        val min = konumMs / 60_000
        val at = if (min > 0) ", ${min}. dakikada" else ""
        return baslik + (sanatci?.takeIf { it.isNotBlank() }?.let { ", $it" } ?: "") + at
    }
}

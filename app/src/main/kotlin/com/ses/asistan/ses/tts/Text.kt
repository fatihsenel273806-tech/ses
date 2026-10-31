package com.ses.asistan.ses.tts

import java.text.Normalizer

/**
 * Antalia metin ön yüzü: ham Türkçe → okunuş biçimi (küçük harf) → sembol kimlikleri.
 *
 * Orijinal normalleştiricinin (antalia_mini/text/normalize.py) navigasyon ve asistan cümlelerinde
 * geçen kısmının karşılığı: sayılar, ondalıklar, saatler, sıra sayıları, yüzde, birimler (km, m, dk,
 * km/sa…), adres kısaltmaları (Cd., Sk., Mah., Blv.), kısaltma harfleri (AVM → aveme) ve sayıya
 * kesme işaretiyle eklenen ekler ("12'de" → "on ikide").
 */
object Text {
    val SYMBOLS: List<String> = listOf("<pad>", "<bos>", "<eos>") +
        " .,?!'-:;".map { it.toString() } + "abcçdefgğhıijklmnoöpqrsştuüvwxyz".map { it.toString() } +
        "âîû".map { it.toString() }
    private val ID = SYMBOLS.withIndex().associate { it.value to it.index }
    const val BOS = 1
    const val EOS = 2
    val SPACE = ID.getValue(" ")

    fun encode(modelText: String): IntArray {
        val out = ArrayList<Int>(modelText.length + 2)
        out.add(BOS)
        for (ch in modelText) ID[ch.toString()]?.let { out.add(it) }
        out.add(EOS)
        return out.toIntArray()
    }

    fun turkishLower(s: String): String =
        s.replace('I', 'ı').replace('İ', 'i').lowercase(java.util.Locale("tr")).replace("\u0307", "")

    // ------------------------------------------------------------------ sayılar
    private val ONES = listOf("sıfır", "bir", "iki", "üç", "dört", "beş", "altı", "yedi", "sekiz", "dokuz")
    private val TENS = listOf("", "on", "yirmi", "otuz", "kırk", "elli", "altmış", "yetmiş", "seksen", "doksan")
    private val ORD = mapOf(
        "bir" to "birinci", "iki" to "ikinci", "üç" to "üçüncü", "dört" to "dördüncü", "beş" to "beşinci",
        "altı" to "altıncı", "yedi" to "yedinci", "sekiz" to "sekizinci", "dokuz" to "dokuzuncu", "on" to "onuncu",
        "yirmi" to "yirminci", "otuz" to "otuzuncu", "kırk" to "kırkıncı", "elli" to "ellinci",
        "altmış" to "altmışıncı", "yetmiş" to "yetmişinci", "seksen" to "sekseninci", "doksan" to "doksanıncı",
        "yüz" to "yüzüncü", "bin" to "bininci", "milyon" to "milyonuncu", "milyar" to "milyarıncı",
    )

    fun numberToWords(n: Long): String {
        if (n < 0) return "eksi " + numberToWords(-n)
        if (n < 10) return ONES[n.toInt()]
        if (n < 100) {
            val t = (n / 10).toInt(); val o = (n % 10).toInt()
            return if (o == 0) TENS[t] else "${TENS[t]} ${ONES[o]}"
        }
        if (n < 1000) {
            val h = (n / 100).toInt(); val r = n % 100
            val p = if (h == 1) "yüz" else "${ONES[h]} yüz"
            return if (r == 0L) p else "$p ${numberToWords(r)}"
        }
        for ((scale, label) in listOf(1_000_000_000_000L to "trilyon", 1_000_000_000L to "milyar", 1_000_000L to "milyon", 1_000L to "bin")) {
            if (n >= scale) {
                val c = n / scale; val r = n % scale
                val p = if (scale == 1000L && c == 1L) label else "${numberToWords(c)} $label"
                return if (r == 0L) p else "$p ${numberToWords(r)}"
            }
        }
        return n.toString()
    }

    private fun ordinal(n: Long): String {
        val w = numberToWords(n).split(" ").toMutableList()
        w[w.size - 1] = ORD[w.last()] ?: (w.last() + "inci")
        return w.joinToString(" ")
    }

    private fun digits(s: String) = s.map { ONES[it - '0'] }.joinToString(" ")

    private fun digitGroup(s: String): String {
        val st = s.trimStart('0')
        val zeros = List(s.length - st.length) { "sıfır" }
        return (zeros + if (st.isEmpty()) emptyList() else listOf(numberToWords(st.toLong()))).joinToString(" ")
    }

    private fun decimal(v: String): String {
        val parts = v.split(Regex("[,.]"), limit = 2)
        val whole = numberToWords(parts[0].toLong())
        val frac = parts[1]
        if (frac == "5" && parts[0].toLong() != 0L) return "$whole buçuk"
        val fw = if (frac.length <= 2) digitGroup(frac) else digits(frac)
        return "$whole virgül $fw"
    }

    private fun numeric(v: String): String {
        if (Regex("\\d{1,3}(?:\\.\\d{3})+(?:,\\d+)?").matches(v)) {
            val p = v.replace(".", "").split(",", limit = 2)
            return if (p.size == 2) decimal("${p[0]},${p[1]}") else numberToWords(p[0].toLong())
        }
        return if (v.contains(',') || v.contains('.')) decimal(v) else numberToWords(v.toLong())
    }

    // ------------------------------------------------------------------ sözlükler
    private val ABBR = linkedMapOf(
        "mah." to "mahallesi", "mh." to "mahallesi", "cad." to "caddesi", "cd." to "caddesi",
        "sok." to "sokak", "sk." to "sokak", "bul." to "bulvarı", "blv." to "bulvarı", "apt." to "apartmanı",
        "dr." to "doktor", "prof." to "profesör", "doç." to "doçent", "vb." to "ve benzeri", "vs." to "vesaire",
        "no." to "numara", "örn." to "örneğin", "şti." to "şirketi", "ecz." to "eczacı", "av." to "avukat",
    )
    private val BEFORE_WORD = setOf("mah.", "mh.", "cad.", "cd.", "sok.", "sk.", "bul.", "blv.", "apt.", "doç.", "ecz.", "av.")
    private val PLAIN_ABBR = mapOf("km" to "kilometre", "kg" to "kilogram", "cm" to "santimetre", "lt" to "litre", "tl" to "lira")
    private val NUMBER_UNITS = linkedMapOf(
        "km" to "kilometre", "m" to "metre", "dk" to "dakika", "sn" to "saniye", "sa" to "saat",
        "kg" to "kilogram", "lt" to "litre", "cm" to "santimetre", "ms" to "milisaniye",
    )
    private val CAPS_LEXICON = mapOf(
        "SMS" to "esemes", "YÖK" to "yök", "BİM" to "bim", "ŞOK" to "şok", "MEB" to "meb", "İTÜ" to "itü",
        "İETT" to "ietete", "ALO" to "alo", "OK" to "okey", "TV" to "tivi", "HD" to "heyçdi",
        "ODTÜ" to "odtü", "AŞTİ" to "aşti", "EGO" to "ego", "ASKİ" to "aski", "ANKARAY" to "ankaray",
    )
    private val LETTERS = mapOf(
        'A' to "a", 'B' to "be", 'C' to "ce", 'Ç' to "çe", 'D' to "de", 'E' to "e", 'F' to "fe", 'G' to "ge",
        'Ğ' to "yumuşak ge", 'H' to "he", 'I' to "i", 'İ' to "i", 'J' to "je", 'K' to "ka", 'L' to "le", 'M' to "me",
        'N' to "ne", 'O' to "o", 'Ö' to "ö", 'P' to "pe", 'R' to "re", 'S' to "se", 'Ş' to "şe", 'T' to "te",
        'U' to "u", 'Ü' to "ü", 'V' to "ve", 'Y' to "ye", 'Z' to "ze", 'Q' to "kü", 'W' to "dabılyu", 'X' to "iks",
    )
    private const val VOWELS = "aeıioöuüAEIİOÖUÜ"

    private fun pronounceable(w: String): Boolean {
        if (w.length < 4) return false
        val v = w.count { it in VOWELS }
        return v.toDouble() / w.length >= 0.3 && !Regex("[^AEIİOÖUÜ]{3}").containsMatchIn(w)
    }

    private val SPOKEN_STEMS: List<String> = (
        ONES + TENS.filter { it.isNotEmpty() } + listOf(
            "yüz", "bin", "milyon", "milyar", "trilyon", "lira", "kuruş", "saniye", "dakika", "saat", "buçuk",
            "metre", "kilometre", "kilogram", "litre", "santimetre", "milisaniye",
        ) + ORD.values
        ).distinct().sortedByDescending { it.length }

    /** Sayıya eklenen eki okunuşa ses uyumuyla bağla: "on iki'de" → "on ikide", "dört'e" → "dörde". */
    private fun attachSuffix(stem0: String, suffix0: String): String {
        var stem = stem0
        var suffix = suffix0
        if (stem.last() !in VOWELS && suffix.length > 1 && suffix[0] in "yns" && suffix[1] in VOWELS) suffix = suffix.substring(1)
        if (suffix[0] in VOWELS) stem = stem.replace(Regex("buçuk$"), "buçuğ").replace(Regex("dört$"), "dörd")
        var vowel = stem.lastOrNull { it in VOWELS } ?: 'e'
        val out = StringBuilder()
        suffix.forEachIndexed { i, c0 ->
            var c = c0
            if (c in "ae") c = if (vowel in "aıou") 'a' else 'e'
            else if (c in "ıiuü") {
                val back = vowel in "aıou"
                val round = vowel in "ouöü"
                c = if (back) (if (round) 'u' else 'ı') else (if (round) 'ü' else 'i')
            } else if (i == 0 && c in "dt") c = if (stem.last() in "çfhkpsşt") 't' else 'd'
            if (c in VOWELS) vowel = c
            out.append(c)
        }
        return stem + out
    }

    // ------------------------------------------------------------------ ana dönüşüm
    // \b ve \w JVM'de yalnızca ASCII, Android'de (ICU) Unicode: ikisinde de aynı çalışsın diye açık sınıflar.
    private const val W = "[\\p{L}\\p{N}_]"

    private fun rx(p: String, ignoreCase: Boolean = false): Regex {
        val q = p.replace("⟨", "(?<!$W)").replace("⟩", "(?!$W)")
        return if (ignoreCase) Regex(q, RegexOption.IGNORE_CASE) else Regex(q)
    }

    fun toModelText(raw: String): String {
        var t = Normalizer.normalize(raw, Normalizer.Form.NFKC)
            .replace(Regex("[’‘`´]"), "'").replace(Regex("[\"“”„«»]"), "").replace("''", "")
            .replace('\u2212', '-')
        t = t.replace(Regex("\\s+"), " ").trim()
        t = rx("'([A-ZÇĞİÖŞÜ]+)⟩").replace(t) { "'" + turkishLower(it.groupValues[1]) }
        t = rx("⟨no\\s*[:.]\\s*(?=\\d)", true).replace(t, "numara ")
        t = t.replace("&", " ve ").replace("→", " , ")
        // hız: "90 km/sa" → "saatte doksan kilometre"
        t = rx("(?<![\\d.,])(\\d+(?:,\\d+)?)\\s?km/(?:sa|s|h)⟩").replace(t) { "saatte ${numeric(it.groupValues[1])} kilometre" }
        // birimler (yalnızca sayıdan sonra)
        t = Regex("(?<=\\d) ?(" + NUMBER_UNITS.keys.joinToString("|") + ")(?![\\p{L}\\p{N}_/])").replace(t) { " " + NUMBER_UNITS.getValue(it.groupValues[1]) }
        // yüzde
        t = Regex("%\\s*(\\d+(?:[,.]\\d+)?)").replace(t) { "yüzde ${numeric(it.groupValues[1])}" }
        t = Regex("(?<![\\d.,])(\\d+(?:,\\d+)?)\\s?%").replace(t) { "yüzde ${numeric(it.groupValues[1])}" }
        // sayı aralığı "3-5" → "üç beş"
        t = Regex("(?<![\\d\\-])(\\d+)\\s*[-–]\\s*(\\d+)(?![\\d\\-])").replace(t) {
            "${numberToWords(it.groupValues[1].toLong())} ${numberToWords(it.groupValues[2].toLong())}"
        }
        t = rx("(?<![\\p{L}\\p{N}_.,:)\\]])-(?=\\d)").replace(t, "eksi ")
        // saat "14:30", "08.05"
        t = rx("⟨([01]?\\d|2[0-3])[:.]([0-5]\\d)⟩").replace(t) {
            val h = numberToWords(it.groupValues[1].toLong())
            val m = it.groupValues[2]
            if (m.toInt() == 0) h else "$h ${if (m.startsWith("0")) digits(m) else numberToWords(m.toLong())}"
        }
        t = rx("⟨\\d{1,3}(?:\\.\\d{3})+(?:,\\d+)?⟩").replace(t) { numeric(it.value) }
        t = rx("⟨(\\d+)[,.](\\d+)⟩").replace(t) { decimal(it.value) }
        t = rx("⟨(\\d+)'?(?:inci|ıncı|üncü|uncu|nci|ncı|ncü|ncu)⟩").replace(t) { ordinal(it.groupValues[1].toLong()) }
        t = rx("⟨(\\d+)\\.(?=[a-zçğıöşü])").replace(t) { ordinal(it.groupValues[1].toLong()) }
        t = rx("⟨([1-9]\\d*)\\.(?=\\s+[a-zçğıöşü])").replace(t) { ordinal(it.groupValues[1].toLong()) }
        t = rx("⟨\\d{5,}⟩").replace(t) { digits(it.value) }
        t = rx("⟨0\\d+⟩").replace(t) { digitGroup(it.value) }
        t = Regex("(?<=\\p{L})(?=\\d)|(?<=\\d)(?=\\p{L})").replace(t, " ")
        t = rx("⟨\\d+⟩").replace(t) { numberToWords(it.value.toLong()) }
        // kısaltmalar
        for ((ab, ex) in ABBR.entries.sortedByDescending { it.key.length }) {
            val q = Regex.escape(ab)
            t = rx("(?<!$W)$q\\s*(?=$W)", true).replace(t, "$ex ")
            if (ab !in BEFORE_WORD) t = rx("(?<!$W)$q", true).replace(t, ex)
        }
        for ((ab, ex) in PLAIN_ABBR) t = rx("(?<!$W)${Regex.escape(ab)}(?!$W)", true).replace(t, ex)
        // büyük harf kısaltmaları
        t = rx("⟨[A-ZÇĞİÖŞÜQWX]{2,7}⟩").replace(t) { m ->
            val w = m.value
            CAPS_LEXICON[w] ?: if (pronounceable(w)) w else w.map { LETTERS[it] ?: "" }.joinToString("")
        }
        // sayı okunuşuna ek: "on iki'de" → "on ikide"
        val stems = SPOKEN_STEMS.joinToString("|") { Regex.escape(it) }
        t = rx("⟨($stems|\\p{L}+(?:inci|ıncı|üncü|uncu|nci|ncı|ncü|ncu))'([a-zçğıöşü]+)⟩").replace(t) {
            attachSuffix(it.groupValues[1], it.groupValues[2])
        }
        t = Regex("(?<=\\p{L})/(?=\\p{L})").replace(t, " ")
        t = turkishLower(t)
        return clean(t)
    }

    private val DROP = Regex("[^abcçdefgğhıijklmnoöpqrsştuüvwxyzâîû .,?!'\\-:;]")

    fun clean(s: String): String {
        var t = s.replace(Regex("[’‘`´]"), "'").replace(Regex("[–—]"), "-").replace("…", ".")
            .replace(Regex("[\\t\\n\\u00a0]"), " ")
        t = DROP.replace(t, " ")
        t = Regex("\\s+([.,?!:;])").replace(t, "$1")
        return t.replace(Regex("\\s+"), " ").trim()
    }

    // ------------------------------------------------------------------ parçalara bölme
    /** api.split_model_text karşılığı: cümleler en fazla 300 karakterlik parçalar, ilk parça ~80. */
    fun split(text: String, maxChars: Int = 300, firstChars: Int = 80): List<String> {
        val sentences = text.trim().split(Regex("(?<=[.?!])\\s+")).map { it.trim() }.filter { it.isNotEmpty() }
        val pieces = ArrayList<String>()
        for (s in sentences) {
            if (s.length > maxChars) pieces.addAll(pack(s.split(Regex("(?<=[,;:])\\s+")).filter { it.isNotEmpty() }, maxChars))
            else pieces.add(s)
        }
        if (pieces.isEmpty()) return emptyList()
        var head = ""
        var used = 0
        for (p in pieces) {
            if (head.isNotEmpty() && head.length + 1 + p.length > firstChars) break
            head = if (head.isEmpty()) p else "$head $p"
            used++
        }
        val chunks = listOf(head) + pack(pieces.subList(used, pieces.size), maxChars)
        return chunks.filter { c -> c.any { it.isLetter() } }
    }

    private fun pack(pieces: List<String>, maxChars: Int): List<String> {
        val out = ArrayList<String>()
        var cur = ""
        for (p in pieces) {
            if (p.length > maxChars) {
                if (cur.isNotEmpty()) { out.add(cur); cur = "" }
                if (' ' in p) out.addAll(pack(p.split(" "), maxChars)) else p.chunked(maxChars).forEach { out.add(it) }
                continue
            }
            if (cur.isNotEmpty() && cur.length + 1 + p.length > maxChars) { out.add(cur); cur = p }
            else cur = if (cur.isEmpty()) p else "$cur $p"
        }
        if (cur.isNotEmpty()) out.add(cur)
        return out
    }
}

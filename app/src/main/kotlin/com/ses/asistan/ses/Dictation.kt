package com.ses.asistan.ses

/**
 * Sesli not dikte kuralları (Android'e bağlı değil: JVM testiyle sınanır). Google tanıyıcısı ilk kısa sessizlikte
 * cümleyi bitirir; not bunun için tek dinlemeyle alınmaz: [Ears.dictate] tanıyıcıyı art arda açar, her parça burada
 * birikir. Not, sürücü ~3,5 sn susunca ya da "tamam / bitti / kaydet" deyince biter; "iptal / vazgeç" not almaz.
 */
object Dictation {
    sealed class Step {
        data class More(val text: String) : Step()
        data class Done(val text: String) : Step()
        object Cancel : Step()
    }

    private val TR = java.util.Locale("tr")
    /** Parçanın tamamı bitiş sözüyse not biter (not içindeki "tamam" kelimesi bitirmez). */
    private val END_ALONE = Regex("^(tamam|tamamdır|bitti|bu kadar|kaydet|tamam kaydet|not bitti|notu kaydet|tamam bitti|bitir)$")
    /** Cümlenin sonunda söylenen açık bitiş sözü. */
    private val END_TAIL = Regex("[\\s,.]+(tamam kaydet|not bitti|notu kaydet|tamam bitti|bu kadar|kaydet)[.!]?$", RegexOption.IGNORE_CASE)
    private val CANCEL = Regex("^(iptal|iptal et|vazgeç|vazgeçtim|boş ver|notu iptal et|not alma|sil)$")

    private fun norm(s: String) = s.lowercase(TR).replace(Regex("[.!?,]"), " ").replace(Regex("\\s+"), " ").trim()

    /** Birikmiş metne yeni parça. Tanıyıcı yeniden başlarken önceki parçanın son kelimesini tekrar verebilir: atılır. */
    fun join(acc: String, seg: String): String {
        val a = acc.trim()
        var s = seg.trim()
        if (a.isEmpty()) return s
        if (s.isEmpty()) return a
        val last = a.substringAfterLast(' ').trimEnd('.', ',')
        val first = s.substringBefore(' ')
        if (first.equals(last, ignoreCase = true) && s.length > first.length) s = s.substring(first.length).trim()
        else if (s.equals(last, ignoreCase = true)) return a
        return "$a $s"
    }

    /** Tanıyıcının bitirdiği bir parça geldi: not sürer mi, bitti mi, iptal mi. */
    fun feed(acc: String, seg: String?): Step {
        val s = seg?.trim().orEmpty()
        val n = norm(s)
        if (n.isEmpty()) return Step.More(acc.trim())
        if (CANCEL.matches(n)) return Step.Cancel
        if (END_ALONE.matches(n)) return Step.Done(acc.trim())
        val all = join(acc, s)
        END_TAIL.find(all)?.let { return Step.Done(all.substring(0, it.range.first).trim()) }
        return Step.More(all)
    }

    /** Biten notun son biçimi: baş harf büyük, sondaki nokta/virgül atılır. */
    fun finish(text: String): String =
        text.trim().trimEnd('.', ',', ' ').replaceFirstChar { if (it.isLowerCase()) it.titlecase(TR) else it.toString() }

    /**
     * "Hey Harita"dan hemen sonra açılan Google tanıyıcısı cümlenin başını kaçırabilir: "hey harita not al süt" →
     * "al süt" ya da "t al süt". Yalnızca uyanmadan sonraki ilk dinlemede: not sayılır, kalan metni döner (boş olabilir).
     */
    fun clippedNote(text: String): String? =
        CLIPPED.find(text.trim())?.groupValues?.get(1)?.trim()

    private val CLIPPED = Regex("^(?:[nmt]?[oaö]?t\\s+)?al(?:ır mısın|ar mısın)?(?:$|[\\s:,.]+(.*)$)", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
}

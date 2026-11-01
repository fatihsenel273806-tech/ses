package com.ses.asistan.yonlendirme

import com.ses.asistan.beceri.AcmaBecerisi
import com.ses.asistan.beceri.DevirBecerisi
import com.ses.asistan.beceri.DosyalarInfo
import com.ses.asistan.beceri.Kanallar
import com.ses.asistan.beceri.Notlar
import com.ses.asistan.beyin.Beyin
import com.ses.asistan.beyin.BeyinAyarlari
import com.ses.asistan.beyin.BeyinBecerisi
import com.ses.asistan.beceri.KanallarBecerisi
import com.ses.asistan.beceri.KanallarSozleri
import com.ses.asistan.beceri.NotlarBecerisi
import com.ses.asistan.beceri.NotlarSozleri
import com.ses.asistan.kopru.Kopru
import com.ses.asistan.ses.SesDurumu
import org.dicio.skill.context.SkillContext
import org.dicio.skill.standard.util.MatchHelper
import org.stypox.dicio.di.SkillContextInternal
import org.stypox.dicio.eval.SkillHandler
import org.stypox.dicio.eval.SkillRanker
import org.stypox.dicio.eval.SkillWithResult
import org.stypox.dicio.eval.scoreAndWrapResult

/**
 * Dicio'nun beceri seçimine kancalar ([org.stypox.dicio.eval.SkillEvaluatorImpl], `// ASISTAN:`): becerilerden önce
 * uyanma adıyla doğrudan yönlendirme, hiçbiri anlamazsa metin devri, o da yoksa Gemini (Faz 6); her etkileşimden sonra
 * konuşma belleği. Karar [Yonlendirme]'de (saf mantık).
 * Arka plan iş parçacığında çağrılır.
 */
object Yonlendirici {
    /** [Yonlendirme.ACILAN] anahtarlarının paketi ve söylenen adı. */
    private val ACILAN = mapOf(
        "harita" to ("com.harita.ankara" to "Harita"),
        Yonlendirme.KANALLAR to (Kanallar.PKG to "Kanallarım"),
        Yonlendirme.NOTLAR to (Notlar.PKG to "Notlar"),
        "dosyalar" to (DosyalarInfo.PKG to "Dosya Okuyucu"),
    )

    /**
     * 1. adım; null: Dicio becerileri (bizimkiler başta) sırayla denenir. Uyanma adı yalnız ipucudur (2026-11-01,
     * telefon: "Hey Kanallar" sonrası "not alır mısın" Kanallarım'a zorlanıp "anlamadım" oluyordu): not komutu her adla
     * Notlar becerisine, adın becerisi anlamazsa cümle diğer becerilere ve Gemini'ye gider.
     */
    fun once(ctx: SkillContext, utterances: List<String>): Pair<String, SkillWithResult<*>>? {
        val ad = SesDurumu.uyanmaAdiAl()
        Kopru.hazirla(ctx.android)
        val adaylar = Kopru.adaylar(ctx.android)
        val ilk = utterances.firstOrNull() ?: return null
        val k = Yonlendirme.once(ilk, ad, adaylar) ?: return null
        if (k is Yonlendirme.Karar.Ac) {
            val (paket, adi) = ACILAN.getValue(k.ad)
            return ilk to AcmaBecerisi(paket, adi).scoreAndWrapResult(ctx, ilk)
        }
        // tanıyıcının seçenekleri, uyanma adı atılmış; ilk uyan
        fun bul(uyar: (String) -> Boolean) = utterances.asSequence()
            .map { Yonlendirme.once(it, ad, adaylar)?.let(::kalan) ?: it }.firstOrNull(uyar)
        bul(NotlarSozleri::notMu)?.let { return ilk to NotlarBecerisi().scoreAndWrapResult(ctx, it) }
        return when (k) {
            is Yonlendirme.Karar.Uygulamaya -> ilk to DevirBecerisi(k).scoreAndWrapResult(ctx, k.metin)
            is Yonlendirme.Karar.Kanallar -> bul { KanallarSozleri.parse(it) != null }
                ?.let { ilk to KanallarBecerisi(zorla = true).scoreAndWrapResult(ctx, it) }
            is Yonlendirme.Karar.Notlar -> bul { NotlarSozleri.parse(it) != null }
                ?.let { ilk to NotlarBecerisi().scoreAndWrapResult(ctx, it) }
            is Yonlendirme.Karar.Ac -> null
        }
    }

    private fun kalan(k: Yonlendirme.Karar): String? = when (k) {
        is Yonlendirme.Karar.Uygulamaya -> k.metin
        is Yonlendirme.Karar.Kanallar -> k.metin
        is Yonlendirme.Karar.Notlar -> k.metin
        is Yonlendirme.Karar.Ac -> null
    }

    /** 3. adım: hiçbir beceri anlamadı; devri o an kabul eden uygulama varsa ona. */
    fun sonra(ctx: SkillContext, utterances: List<String>): Pair<String, SkillWithResult<*>>? {
        val ilk = utterances.firstOrNull() ?: return null
        val k = Yonlendirme.sonra(ilk, Kopru.adaylar(ctx.android)) as? Yonlendirme.Karar.Uygulamaya ?: return null
        return ilk to DevirBecerisi(k).scoreAndWrapResult(ctx, k.metin)
    }

    /**
     * 4. adım: Gemini (anahtar girildiyse; yoksa Dicio'nun yedek becerisi). Gemini asistanın becerisini isterse cümlesi
     * aynı sıralayıcıyla ([sirala]) yeniden denenir.
     */
    fun beyin(
        ctx: SkillContextInternal, utterances: List<String>, beceriler: SkillHandler, sirala: () -> SkillRanker,
    ): Pair<String, SkillWithResult<*>>? {
        val ilk = utterances.firstOrNull()?.takeIf { it.isNotBlank() } ?: return null
        if (!BeyinAyarlari.al(ctx.android).hazir()) return null
        val ornekler = beceriler.enabledSkillsInfo.value.orEmpty().map { runCatching { it.sentenceExample(ctx.android) }.getOrDefault("") }
        val beceri = BeyinBecerisi(ilk, ornekler) { cumle ->
            ctx.standardMatchHelper = MatchHelper(ctx.parserFormatter, cumle)
            try { sirala().getBest(ctx, cumle) } finally { ctx.standardMatchHelper = null }
        }
        return ilk to beceri.scoreAndWrapResult(ctx, ilk)
    }

    /** Her etkileşimin sözü ve sesli cevabı konuşma belleğine (Gemini'nin geçmişi). */
    fun hatirla(soz: String, cevap: String) = Beyin.hatirla(soz, cevap)

    /**
     * Uyanma servisi: uygulama adıyla uyanıldıysa ("Hey Harita", "Hey Kanallar") asistanın ekranı açılmaz; dinleme
     * arka planda sürer, cevap sesli gelir (harita ekranda kalır).
     */
    fun ekransiz(): Boolean = SesDurumu.uyanmaAdi().let { it != null && it != Yonlendirme.ASISTAN }
}

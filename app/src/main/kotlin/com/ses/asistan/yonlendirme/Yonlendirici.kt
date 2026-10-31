package com.ses.asistan.yonlendirme

import com.ses.asistan.beceri.DevirBecerisi
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
    /** 1. adım; null: Dicio becerileri sırayla denenir. */
    fun once(ctx: SkillContext, utterances: List<String>): Pair<String, SkillWithResult<*>>? {
        val ad = SesDurumu.uyanmaAdiAl()
        Kopru.hazirla(ctx.android)
        val adaylar = Kopru.adaylar(ctx.android)
        val ilk = utterances.firstOrNull() ?: return null
        return when (val k = Yonlendirme.once(ilk, ad, adaylar)) {
            null -> null
            is Yonlendirme.Karar.Uygulamaya -> ilk to DevirBecerisi(k).scoreAndWrapResult(ctx, k.metin)
            is Yonlendirme.Karar.Kanallar -> {
                // tanıyıcının seçeneklerinden Kanallarım komutu olan (yoksa ilki): "anlamadım" da bizim beceriden
                val metin = utterances.asSequence()
                    .map { (Yonlendirme.once(it, ad, adaylar) as? Yonlendirme.Karar.Kanallar)?.metin ?: it }
                    .firstOrNull { KanallarSozleri.parse(it) != null } ?: k.metin
                ilk to KanallarBecerisi(zorla = true).scoreAndWrapResult(ctx, metin)
            }
            is Yonlendirme.Karar.Notlar -> {
                val metin = utterances.asSequence()
                    .map { (Yonlendirme.once(it, ad, adaylar) as? Yonlendirme.Karar.Notlar)?.metin ?: it }
                    .firstOrNull { NotlarSozleri.parse(it) != null } ?: k.metin
                ilk to NotlarBecerisi(zorla = true).scoreAndWrapResult(ctx, metin)
            }
        }
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

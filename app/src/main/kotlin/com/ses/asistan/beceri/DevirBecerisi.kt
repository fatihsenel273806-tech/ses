package com.ses.asistan.beceri

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import com.ses.asistan.kopru.Kopru
import com.ses.asistan.yonlendirme.Yonlendirme
import org.dicio.skill.context.SkillContext
import org.dicio.skill.skill.FloatScore
import org.dicio.skill.skill.Score
import org.dicio.skill.skill.Skill
import org.dicio.skill.skill.SkillInfo
import org.dicio.skill.skill.SkillOutput
import org.dicio.skill.skill.Specificity

/**
 * Metin devri (PLAN §1): söylenen, uygulamanın kendi komut anlayışına verilir ("Hey Harita, Kızılay'a git" →
 * Harita `interpret`). Asistanda o uygulamanın kuralları yeniden yazılmaz; cevabı uygulama `SÖYLE` ile kendisi verir.
 * Beceri listesinde yoktur: yalnız yönlendirme ([com.ses.asistan.yonlendirme.Yonlendirici]) kurar.
 */
object DevirInfo : SkillInfo("devir") {
    override fun name(context: Context) = "Uygulamaya devir"
    override fun sentenceExample(context: Context) = "Hey Harita, Kızılay'a git"

    @Composable
    override fun icon() = rememberVectorPainter(Icons.Filled.Share)

    override fun build(ctx: SkillContext): Skill<*>? = null
}

class DevirBecerisi(private val karar: Yonlendirme.Karar.Uygulamaya) : Skill<Unit>(DevirInfo, Specificity.LOW) {
    override fun score(ctx: SkillContext, input: String): Pair<Score, Unit> = Pair(FloatScore(1f), Unit)

    override suspend fun generateOutput(ctx: SkillContext, inputData: Unit): SkillOutput {
        val ad = uygulamaAdi(ctx.android, karar.paket)
        if (!Kopru.metinVer(karar.paket, karar.metin, karar.uyanma)) return Cikti("$ad uygulamasına ulaşılamadı.")
        // cevabı uygulama kendisi söyler; ekranda yalnız nereye gittiği
        return Cikti("", "$ad: ${karar.metin}")
    }

    private fun uygulamaAdi(ctx: Context, paket: String) = runCatching {
        ctx.packageManager.getApplicationLabel(ctx.packageManager.getApplicationInfo(paket, 0)).toString()
    }.getOrDefault(paket)
}

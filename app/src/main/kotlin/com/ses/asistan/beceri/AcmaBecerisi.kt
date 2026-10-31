package com.ses.asistan.beceri

import android.content.Context
import android.content.Intent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import org.dicio.skill.context.SkillContext
import org.dicio.skill.skill.FloatScore
import org.dicio.skill.skill.Score
import org.dicio.skill.skill.Skill
import org.dicio.skill.skill.SkillInfo
import org.dicio.skill.skill.SkillOutput
import org.dicio.skill.skill.Specificity

/**
 * Bağlı uygulamayı adıyla açar ("notlar uygulamasını aç", "kanallarımı aç", "Hey Harita, aç"). Beceri listesinde
 * yoktur: yalnız yönlendirme ([com.ses.asistan.yonlendirme.Yonlendirici]) kurar. Asistan arka plandayken ekranı
 * açabilmesi "diğer uygulamaların üzerinde göster" iznine bağlı (Ayarlar › Bağlı uygulamalar).
 */
object AcmaInfo : SkillInfo("ac") {
    override fun name(context: Context) = "Uygulama aç"
    override fun sentenceExample(context: Context) = "Notlar uygulamasını aç"

    @Composable
    override fun icon() = rememberVectorPainter(Icons.AutoMirrored.Filled.OpenInNew)

    override fun build(ctx: SkillContext): Skill<*>? = null
}

class AcmaBecerisi(private val paket: String, private val ad: String) : Skill<Unit>(AcmaInfo, Specificity.HIGH) {
    override fun score(ctx: SkillContext, input: String): Pair<Score, Unit> = Pair(FloatScore(1f), Unit)

    override suspend fun generateOutput(ctx: SkillContext, inputData: Unit): SkillOutput {
        val niyet = ctx.android.packageManager.getLaunchIntentForPackage(paket)
            ?: return Cikti("$ad kurulu değil.")
        niyet.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return if (runCatching { ctx.android.startActivity(niyet) }.isSuccess) Cikti("$ad açılıyor.")
        else Cikti("$ad açılamadı. Ayarlar'da “Diğer uygulamaların üzerinde göster” iznini verin.")
    }
}

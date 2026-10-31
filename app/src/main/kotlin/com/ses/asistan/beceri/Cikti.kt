package com.ses.asistan.beceri

import androidx.compose.runtime.Composable
import org.dicio.skill.context.SkillContext
import org.dicio.skill.skill.InteractionPlan
import org.stypox.dicio.io.graphical.Headline
import org.stypox.dicio.io.graphical.HeadlineSpeechSkillOutput

/** Becerilerimizin çıktısı: [konusma] sesli söylenir (boşsa sessiz), [yazi] ekranda (boşsa konuşmanın kendisi). */
internal class Cikti(
    private val konusma: String,
    private val yazi: String = konusma,
    private val plan: InteractionPlan = InteractionPlan.FinishInteraction,
) : HeadlineSpeechSkillOutput {
    override fun getSpeechOutput(ctx: SkillContext) = konusma
    override fun getInteractionPlan(ctx: SkillContext) = plan

    @Composable
    override fun GraphicalOutput(ctx: SkillContext) = Headline(text = yazi)
}

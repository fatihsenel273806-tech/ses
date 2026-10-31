package com.ses.asistan.beyin

import android.content.Context
import android.util.Log
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import com.ses.asistan.beceri.Cikti
import com.ses.asistan.kopru.Kopru
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.dicio.skill.context.SkillContext
import org.dicio.skill.skill.FloatScore
import org.dicio.skill.skill.Score
import org.dicio.skill.skill.Skill
import org.dicio.skill.skill.SkillInfo
import org.dicio.skill.skill.SkillOutput
import org.dicio.skill.skill.Specificity
import org.stypox.dicio.eval.SkillWithResult

/**
 * Gemini yedek becerisi (PLAN Faz 6): Dicio'nun yedek becerisinin ("anlamadım") önüne geçer. Beceri listesinde yoktur:
 * yalnız yönlendirme ([com.ses.asistan.yonlendirme.Yonlendirici.beyin]) kurar, anahtar girilmemişse hiç kurulmaz.
 */
object BeyinInfo : SkillInfo("beyin") {
    override fun name(context: Context) = "Gemini"
    override fun sentenceExample(context: Context) = "Türkiye'nin en yüksek dağı hangisi?"

    @Composable
    override fun icon() = rememberVectorPainter(Icons.Filled.AutoAwesome)

    override fun build(ctx: SkillContext): Skill<*>? = null
}

/**
 * [soz]: söylenen. [ornekler]: asistanın açık becerilerinin örnek cümleleri (Gemini'nin `asistan_komutu` aracı). [yeniden]: Gemini'nin
 * sadeleştirdiği cümleyle beceri sıralayıcısı (yedek beceri olmadan); null: hiçbir beceri anlamadı.
 */
class BeyinBecerisi(
    private val soz: String,
    private val ornekler: List<String>,
    private val yeniden: (String) -> SkillWithResult<*>?,
) : Skill<Unit>(BeyinInfo, Specificity.LOW) {
    override fun score(ctx: SkillContext, input: String): Pair<Score, Unit> = Pair(FloatScore(1f), Unit)

    override suspend fun generateOutput(ctx: SkillContext, inputData: Unit): SkillOutput {
        val cevap = try {
            Beyin.sor(ctx.android, soz, ornekler).first
        } catch (e: Exception) {
            Log.w("BeyinBecerisi", "Gemini", e)
            return Cikti(if (e is ApiHatasi && e.kod == 429) "Gemini'nin bugünkü hakkı doldu." else "Gemini'ye ulaşılamadı.")
        }
        return when (cevap) {
            is Gemini.Cevap.Soz -> Cikti(cevap.metin)
            is Gemini.Cevap.Asistan -> withContext(Dispatchers.Default) { yeniden(cevap.cumle) }?.generateOutput(ctx)
                ?: Cikti("Bunu yapamadım.", "Bunu yapamadım: ${cevap.cumle}")
            is Gemini.Cevap.Devret -> {
                Kopru.hazirla(ctx.android)
                val gitti = withContext(Dispatchers.IO) { Kopru.metinVer(cevap.paket, cevap.metin, false) }
                if (!gitti) return Cikti("${cevap.ad} uygulamasına ulaşılamadı.")
                Beyin.devredildi(cevap.paket)
                // cevabı uygulama kendisi söyler
                Cikti("", "${cevap.ad}: ${cevap.metin}")
            }
            is Gemini.Cevap.Komut -> {
                val s = withContext(Dispatchers.IO) {
                    Kopru.hazirla(ctx.android)
                    Kopru.komut(cevap.paket, cevap.komut, cevap.sorgu, if (cevap.komut == "ozetle") 45_000L else 10_000L)
                } ?: return Cikti("${cevap.ad} cevap vermedi.")
                Cikti(s.metin.ifBlank { if (s.tamam) "Tamam." else "Olmadı." })
            }
            Gemini.Cevap.Bos -> Cikti("Anlamadım.")
        }
    }
}

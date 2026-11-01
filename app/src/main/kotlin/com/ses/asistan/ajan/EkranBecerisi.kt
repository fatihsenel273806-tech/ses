package com.ses.asistan.ajan

import android.accessibilityservice.AccessibilityService
import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import com.ses.asistan.ajan.EkranSozleri.Komut
import com.ses.asistan.beceri.Cikti
import com.ses.asistan.ek.EkranServisi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.dicio.skill.context.SkillContext
import org.dicio.skill.skill.AlwaysWorstScore
import org.dicio.skill.skill.FloatScore
import org.dicio.skill.skill.Score
import org.dicio.skill.skill.Skill
import org.dicio.skill.skill.SkillInfo
import org.dicio.skill.skill.SkillOutput
import org.dicio.skill.skill.Specificity

/**
 * Ekran komutları (Gemini olmadan): bağlı dört uygulamada kaydır, geri, dokun, yaz, oku ([EkranSozleri]). Riskli
 * düğmeye dokunmadan önce sesli onay. Daha karmaşık işler Gemini'nin ekran ajanında ([AjanCalistirici]).
 */
object EkranInfo : SkillInfo("ekran") {
    override fun name(context: Context) = "Ekran kontrolü"
    override fun sentenceExample(context: Context) = "Aşağı kaydır"

    @Composable
    override fun icon() = rememberVectorPainter(Icons.Filled.TouchApp)

    override fun build(ctx: SkillContext): Skill<*> = EkranBecerisi()
}

class EkranBecerisi : Skill<Komut?>(EkranInfo, Specificity.HIGH) {
    override fun score(ctx: SkillContext, input: String): Pair<Score, Komut?> {
        val k = EkranSozleri.parse(input) ?: return Pair(AlwaysWorstScore, null)
        return Pair(FloatScore(1f), k)
    }

    override suspend fun generateOutput(ctx: SkillContext, inputData: Komut?): SkillOutput {
        val k = inputData ?: return Cikti("Anlamadım.")
        val s = EkranServisi.tek ?: return Cikti("Bunun için Ayarlar, Erişilebilirlik'ten Asistan'ın ekran kontrolünü açın.")
        EkranServisi.asistaniGizle()
        val e = withContext(Dispatchers.Main) { EkranOkuyucu.oku(s) }
            ?: return Cikti("Bu ekranda çalışamam; yalnız Harita, Kanallarım, Notlar ve Dosya Okuyucu'da.")
        return withContext(Dispatchers.Main) {
            when (k) {
                is Komut.Kaydir -> if (Eylem.kaydir(s, e, null, k.yon)) Cikti("", "Kaydırıldı") else Cikti("Kaydıramadım.")
                Komut.Geri -> { s.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK); Cikti("", "Geri") }
                Komut.Oku -> Cikti(EkranSozleri.okunus(e.dugumler).ifBlank { "Ekranda yazı yok." })
                is Komut.Dokun -> {
                    val d = EkranMetni.bul(e.dugumler, k.ad) ?: return@withContext Cikti("Ekranda “${k.ad}” bulamadım.")
                    if (EkranMetni.riskli(d.metin) && !withContext(Dispatchers.Default) { AjanCalistirici.sor(ctx.android, "“${d.metin}” düğmesine basayım mı?") }) {
                        return@withContext Cikti("Tamam, basmadım.")
                    }
                    val n = e.dugum(d.no) ?: return@withContext Cikti("Dokunamadım.")
                    if (Eylem.dokun(s, n)) Cikti("", d.metin) else Cikti("Dokunamadım.")
                }
                is Komut.Yaz -> {
                    val d = EkranMetni.bul(e.dugumler.filter { it.yazilir }, k.alan) ?: e.dugumler.firstOrNull { it.yazilir }
                        ?: return@withContext Cikti("Ekranda yazı alanı bulamadım.")
                    val n = e.dugum(d.no)
                    if (n != null && Eylem.yaz(n, k.metin)) Cikti("", "Yazıldı: ${k.metin}") else Cikti("Yazamadım.")
                }
            }
        }
    }
}

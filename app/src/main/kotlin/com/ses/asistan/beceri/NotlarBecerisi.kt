package com.ses.asistan.beceri

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import com.ses.asistan.beceri.NotlarSozleri.Komut
import com.ses.asistan.ses.Dictation
import com.ses.asistan.ses.Ses
import com.ses.asistan.ses.Tones
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.dicio.skill.context.SkillContext
import org.dicio.skill.skill.AlwaysWorstScore
import org.dicio.skill.skill.FloatScore
import org.dicio.skill.skill.InteractionPlan
import org.dicio.skill.skill.Score
import org.dicio.skill.skill.Skill
import org.dicio.skill.skill.SkillInfo
import org.dicio.skill.skill.SkillOutput
import org.dicio.skill.skill.Specificity

/**
 * Notlar becerisi (PLAN Faz 4): "not al …" (dikte asistanda, not Notlar'ın "Asistan" klasörüne), "notlarımda X'i ara",
 * "son notumu oku", "son üç notum". Notlar'la bağlantı yalnız kişisel derlemesindeki yayın alıcısıyla ([Notlar]);
 * Notlar'ın kendi sesli özelliklerine dokunulmaz. Kilitli notlar ve gizli klasörler Notlar'dan hiç gelmez.
 */
object NotlarInfo : SkillInfo("notlar") {
    override fun name(context: Context) = "Notlar"
    override fun sentenceExample(context: Context) = "Not al, yarın süt alınacak"

    @Composable
    override fun icon() = rememberVectorPainter(Icons.Filled.EditNote)

    override fun build(ctx: SkillContext): Skill<*> = NotlarBecerisi(zorla = false)
}

/** [zorla]: "Hey Notlar" ile çağrıldı: anlaşılmazsa "anlamadım" denir. */
class NotlarBecerisi(private val zorla: Boolean) : Skill<Komut?>(NotlarInfo, Specificity.HIGH) {

    override fun score(ctx: SkillContext, input: String): Pair<Score, Komut?> {
        val k = NotlarSozleri.parse(input) ?: return Pair(if (zorla) FloatScore(1f) else AlwaysWorstScore, null)
        return Pair(FloatScore(1f), k)
    }

    override suspend fun generateOutput(ctx: SkillContext, inputData: Komut?): SkillOutput {
        val komut = inputData
            ?: return Cikti("Notlar için anlamadım. “Not al”, “son notumu oku”, “notlarımda X'i ara” diyebilirsiniz.")
        if (!Notlar.kurulu(ctx.android)) return Cikti("Notlar uygulaması kurulu değil.")
        return when (komut) {
            is Komut.NotAl -> notAl(ctx, komut.metin)
            Komut.SonuOku -> sorgu(ctx, emptyList(), 1) { n -> Cikti(NotlarSozleri.notCumlesi(n[0].baslik, n[0].metin), "Son not: ${NotlarSozleri.ad(n[0].baslik, n[0].metin)}") }
            is Komut.Son -> sorgu(ctx, emptyList(), komut.adet) { n -> liste("Son ${NotlarSozleri.adetSozu(n.size)} notunuz", n) }
            is Komut.Ara -> sorgu(ctx, komut.terimler, 5, bos = "“${komut.soylenen}” geçen not bulunamadı.") { n ->
                if (n.size == 1) Cikti(NotlarSozleri.notCumlesi(n[0].baslik, n[0].metin), "“${komut.soylenen}”: ${NotlarSozleri.ad(n[0].baslik, n[0].metin)}")
                else liste("“${komut.soylenen}” geçen ${NotlarSozleri.adetSozu(n.size)} not", n)
            }
        }
    }

    /** Harita `dictateNote` ile aynı akış: komutla söylenen başlangıçtır, susunca ya da "tamam" deyince yazılır. */
    private suspend fun notAl(ctx: SkillContext, metin: String): SkillOutput {
        val ses = withContext(Dispatchers.Main) { Ses.al(ctx.android) }
        if (metin.isBlank()) konusVeBekle(ses, "Ne not alayım? Bitince susun ya da tamam deyin.") else ses.isaret(Tones.Kind.LISTEN)
        val t = dikte(ctx.android, metin)
            ?: return Cikti(if (metin.isBlank()) "Not almadım." else "Not iptal edildi.")
        val not = Dictation.finish(t)
        return when (Notlar.yaz(ctx.android, not)) {
            Notlar.TAMAM -> Cikti("Not alındı.", "Not alındı · Notlar › ${Notlar.KLASOR}: $not")
            Notlar.SIRADA -> Cikti("Not alındı; bağlantı gelince Notlar'a geçecek.", "Not sırada: $not")
            else -> Cikti("Not kaydedilemedi. Notlar'ın kişisel sürümü kurulu mu?")
        }
    }

    private suspend fun sorgu(
        ctx: SkillContext, terimler: List<String>, adet: Int, bos: String = "Hiç notunuz yok.",
        cevap: (List<Notlar.Bulunan>) -> SkillOutput,
    ): SkillOutput {
        val (kod, notlar) = Notlar.sorgula(ctx.android, terimler, adet)
            ?: return Cikti("Notlar'a ulaşılamadı. Notlar'ın kişisel sürümü kurulu mu?")
        return when {
            kod == Notlar.GIRIS_YOK -> Cikti("Notlar'da oturum açık değil.")
            kod != Notlar.TAMAM -> Cikti("Notlar cevap vermedi.")
            notlar.isEmpty() -> Cikti(bos)
            else -> cevap(notlar)
        }
    }

    /** Liste sesle okunur; "ikinci", adı ya da "sonuncusu" denince o not okunur (alt etkileşim, [NotSecimBecerisi]). */
    private fun liste(baslik: String, n: List<Notlar.Bulunan>): SkillOutput {
        val adlar = n.map { NotlarSozleri.ad(it.baslik, it.metin) }
        return Cikti(Secim.oku(baslik, adlar), plan = InteractionPlan.StartSubInteraction(
            reopenMicrophone = true, nextSkills = listOf(NotSecimBecerisi(adlar, n)),
        ))
    }
}

/** Okunan not listesinden seçim: seçilen not okunur. */
class NotSecimBecerisi(private val adlar: List<String>, private val notlar: List<Notlar.Bulunan>) :
    Skill<Int?>(NotlarInfo, Specificity.HIGH) {

    override fun score(ctx: SkillContext, input: String): Pair<Score, Int?> {
        val i = Secim.sec(input, adlar) ?: return Pair(AlwaysWorstScore, null)
        return Pair(FloatScore(1f), i)
    }

    override suspend fun generateOutput(ctx: SkillContext, inputData: Int?): SkillOutput {
        val n = notlar[inputData ?: return Cikti("Seçemedim.")]
        return Cikti(NotlarSozleri.notCumlesi(n.baslik, n.metin), adlar[inputData], plan = InteractionPlan.FinishSubInteraction(reopenMicrophone = false))
    }
}

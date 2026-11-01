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
import com.ses.asistan.ek.Ekran
import com.ses.asistan.ek.Konum
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
 * "son notumu oku", "son üç notum". Not alırken söylenen ekler (2026-11-01): ekran görüntüsü ([Ekran]), konum
 * ([Konum]), video notu (Kanallarım'ın oturumuna; görsel o videonun notuna). Her uygulamadayken ve her uyanma adıyla
 * aynı akış. Notlar'la bağlantı yalnız kişisel derlemesindeki yayın alıcısıyla ([Notlar]);
 * Notlar'ın kendi sesli özelliklerine dokunulmaz. Kilitli notlar ve gizli klasörler Notlar'dan hiç gelmez.
 */
object NotlarInfo : SkillInfo("notlar") {
    override fun name(context: Context) = "Notlar"
    override fun sentenceExample(context: Context) = "Not al, yarın süt alınacak"

    @Composable
    override fun icon() = rememberVectorPainter(Icons.Filled.EditNote)

    override fun build(ctx: SkillContext): Skill<*> = NotlarBecerisi()
}

class NotlarBecerisi : Skill<Komut?>(NotlarInfo, Specificity.HIGH) {

    override fun score(ctx: SkillContext, input: String): Pair<Score, Komut?> {
        val k = NotlarSozleri.parse(input) ?: return Pair(AlwaysWorstScore, null)
        return Pair(FloatScore(1f), k)
    }

    override suspend fun generateOutput(ctx: SkillContext, inputData: Komut?): SkillOutput {
        val komut = inputData ?: return Cikti("Notlar için anlamadım.")
        if (!Notlar.kurulu(ctx.android)) return Cikti("Notlar uygulaması kurulu değil.")
        return when (komut) {
            is Komut.NotAl -> notAkisi(ctx, komut)
            Komut.SonuOku -> sorgu(ctx, emptyList(), 1) { n -> Cikti(NotlarSozleri.notCumlesi(n[0].baslik, n[0].metin), "Son not: ${NotlarSozleri.ad(n[0].baslik, n[0].metin)}") }
            is Komut.Son -> sorgu(ctx, emptyList(), komut.adet) { n -> liste("Son ${NotlarSozleri.adetSozu(n.size)} notunuz", n) }
            is Komut.Ara -> sorgu(ctx, komut.terimler, 5, bos = "“${komut.soylenen}” geçen not bulunamadı.") { n ->
                if (n.size == 1) Cikti(NotlarSozleri.notCumlesi(n[0].baslik, n[0].metin), "“${komut.soylenen}”: ${NotlarSozleri.ad(n[0].baslik, n[0].metin)}")
                else liste("“${komut.soylenen}” geçen ${NotlarSozleri.adetSozu(n.size)} not", n)
            }
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

/**
 * Not alma akışı (Harita `dictateNote` ile aynı dikte: komutla söylenen başlangıçtır, susunca ya da "tamam" deyince
 * yazılır). Ekran görüntüsü önce alınır (ekran kullanıcının baktığı haliyle kalsın), konum dikte sürerken bulunur.
 * Ek istendiyse sessiz kalmak da not sayılır (yalnız görsel / konum). Gemini'nin `not_al` aracı da buraya gelir.
 */
internal suspend fun notAkisi(ctx: SkillContext, k: Komut.NotAl): SkillOutput = coroutineScope {
    val a = ctx.android
    val ekran = if (k.ekran) Ekran.al(a) else null
    val resim = (ekran as? Ekran.Sonuc.Tamam)?.dosya
    if (ekran is Ekran.Sonuc.Hata && k.metin.isBlank() && !k.konum && !k.video) return@coroutineScope Cikti(ekran.neden)
    val konum = if (k.konum) async { Konum.al(a) } else null
    val ses = withContext(Dispatchers.Main) { Ses.al(a) }
    val soru = when {
        resim != null -> "Ekran görüntüsü alındı. Ne yazayım? Bitince susun."
        k.video -> "Videoya ne not alayım?"
        else -> "Ne not alayım? Bitince susun ya da tamam deyin."
    }
    if (k.metin.isBlank()) konusVeBekle(ses, soru) else ses.isaret(Tones.Kind.LISTEN)
    val t = dikte(a, k.metin)
    if (t == null && resim == null && !k.konum) {
        konum?.cancel()
        return@coroutineScope Cikti(if (k.metin.isBlank()) "Not almadım." else "Not iptal edildi.")
    }
    val yer = konum?.await()
    val metin = listOfNotNull(t?.let { Dictation.finish(it) }, yer?.satir()).joinToString("\n")
    val uyari = listOfNotNull((ekran as? Ekran.Sonuc.Hata)?.neden, if (k.konum && yer == null) konumUyarisi(a) else null)
    val ekler = listOfNotNull(if (resim != null) "ekran görüntüsüyle" else null, if (yer != null) "konumla" else null)
        .joinToString(" ve ").let { if (it.isEmpty()) "" else " $it" }
    if (k.video) {
        val kan = Kanallar.al(a)
        val kod = kan.notAl(metin)
        if (kod != null && kod != 0) {
            // görsel videonun notuna (Kanallarım notu yazdıktan sonra: Notlar onu bulsun)
            if (resim != null) Notlar.yaz(a, "", resim = resim, video = kan.videoKimligi())
            return@coroutineScope sonuc(if (kod == 1) Notlar.TAMAM else Notlar.SIRADA, "Videoya not alındı$ekler.", metin, uyari)
        }
        if (kod == 0) return@coroutineScope Cikti("Video notu kaydedilemedi. Notlar uygulamasını güncelleyin.")
        // Kanallarım çalmıyor: not kaybolmasın, normal not
        return@coroutineScope sonuc(
            Notlar.yaz(a, metin, resim = resim), "Kanallarım'da çalan video yok; normal not aldım$ekler.", metin, uyari,
        )
    }
    sonuc(Notlar.yaz(a, metin, resim = resim), "Not alındı$ekler.", metin, uyari)
}

private fun sonuc(kod: Int, tamam: String, metin: String, uyari: List<String>): SkillOutput {
    val ek = uyari.joinToString(" ").let { if (it.isEmpty()) "" else " $it" }
    return when (kod) {
        Notlar.TAMAM -> Cikti(tamam + ek, "$tamam · Notlar › ${Notlar.KLASOR}: $metin$ek")
        Notlar.SIRADA -> Cikti("Not alındı; bağlantı gelince Notlar'a geçecek.$ek", "Not sırada: $metin$ek")
        else -> Cikti("Not kaydedilemedi. Notlar'ın kişisel sürümü kurulu mu?")
    }
}

private fun konumUyarisi(ctx: android.content.Context) = when {
    !Konum.izinVar(ctx) -> "Konum izni yok; Asistan ayarlarından verin."
    !Konum.arkaPlanIzni(ctx) -> "Konum alınamadı; konum iznini her zaman izin ver yapın."
    else -> "Konum bulunamadı."
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

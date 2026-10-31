package com.ses.asistan.beceri

import android.content.Context
import android.media.browse.MediaBrowser
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import com.ses.asistan.beceri.KanallarSozleri.Eylem
import com.ses.asistan.beceri.KanallarSozleri.Komut
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
 * Kanallarım becerisi (PLAN Faz 2; Kanallarım'da değişiklik yok). Harita'nın Kanallarım komutları asistana taşındı:
 * sarma, baştan, "ne çalıyor", son izlenenler, harita listesi, indirilenler, oynatma listeleri, adıyla açma, videoya
 * not. Dicio'nun genel medya becerisi kalır: "durdur", "sonraki" gibi genel komutlarda bizimki yalnız Kanallarım
 * çalarken (ya da "Hey Kanallar" denince) öne geçer.
 */
object KanallarInfo : SkillInfo("kanallarim") {
    override fun name(context: Context) = "Kanallarım"
    override fun sentenceExample(context: Context) = "Hey Kanallar, geri sar"

    @Composable
    override fun icon() = rememberVectorPainter(Icons.Filled.VideoLibrary)

    override fun build(ctx: SkillContext): Skill<*> = KanallarBecerisi(zorla = false)
}

/** [zorla]: "Hey Kanallar" ile çağrıldı: genel komutlar da bizim, anlaşılmazsa "anlamadım" denir. */
class KanallarBecerisi(private val zorla: Boolean) : Skill<Komut?>(KanallarInfo, Specificity.HIGH) {

    override fun score(ctx: SkillContext, input: String): Pair<Score, Komut?> {
        val k = KanallarSozleri.parse(input) ?: return Pair(if (zorla) FloatScore(1f) else AlwaysWorstScore, null)
        if (!zorla && KanallarSozleri.genel(k) && !Kanallar.al(ctx.android).kanallarimCaliyor()) return Pair(AlwaysWorstScore, k)
        return Pair(FloatScore(1f), k)
    }

    override suspend fun generateOutput(ctx: SkillContext, inputData: Komut?): SkillOutput {
        val k = Kanallar.al(ctx.android)
        val komut = inputData ?: return Cikti("Kanallarım için anlamadım. “Geri sar”, “ne çalıyor”, “X videosunu aç” diyebilirsiniz.")
        if (!k.kurulu()) return Cikti("Kanallarım uygulaması kurulu değil.")
        return when (komut) {
            is Komut.Sar -> {
                if (!k.sar(komut.ms, komut.mutlak)) return Cikti("Çalan bir video yok.")
                // sesli onay videonun üstüne konuşur: yalnızca yazılı (Harita mediaSeek)
                Cikti(
                    "", if (komut.mutlak) (if (komut.ms == 0L) "Baştan" else "${komut.ms / 60_000}. dakika")
                    else "${if (komut.ms < 0) "−" else "+"}${kotlin.math.abs(komut.ms) / 1000} sn",
                )
            }
            is Komut.Bul -> bul(k, komut.ad)
            is Komut.Not -> not(ctx, k, komut.metin)
            is Komut.Medya -> when (komut.eylem) {
                Eylem.WHAT -> k.calan().let { Cikti(KanallarSozleri.calanCumlesi(it.baslik, it.sanatci, it.konumMs)) }
                Eylem.MAP_LIST -> liste(k, "map", "Harita listem", "Harita listen boş. Kanallarım'da Listeler → Harita için'den ekleyin.")
                Eylem.RECENT -> liste(k, "recent", "Son izlenenler", "Son izlenen video yok.")
                Eylem.DOWNLOADS -> liste(k, "dl", "İndirilenler", "İndirilen video yok.")
                Eylem.PLAYLISTS -> liste(k, "pl", "Oynatma listeleri", "Oynatma listesi yok.")
                Eylem.RESUME -> if (k.eylem(Eylem.RESUME)) Cikti("Kaldığın yerden devam ediyorum.") else Cikti("Kanallarım'a bağlanılamadı.")
                // video çalarken sesli onay üstüne konuşmasın: yalnız yazılı
                else -> if (k.eylem(komut.eylem)) Cikti("", ONAY[komut.eylem].orEmpty()) else Cikti("Çalan bir şey yok.")
            }
        }
    }

    /** Harita listesi, son izlenenler, indirilenler, favoriler ve oynatma listelerinde adıyla; en iyi eşleşen kaldığı yerden çalar. */
    private suspend fun bul(k: Kanallar, ad: String): SkillOutput {
        val found = ArrayList<MediaBrowser.MediaItem>()
        for (p in listOf("map", "recent", "dl", "fav", "pl")) found += k.cocuklar(p)
        val titles = found.map { it.description.title?.toString().orEmpty() }
        val i = KanallarSozleri.best(ad, titles)
        if (i < 0) return Cikti("Kanallarım'da “$ad” bulunamadı.")
        k.cal(found[i].mediaId!!)
        return Cikti("${titles[i]} açılıyor.")
    }

    /** Liste sesle okunur, cevap ("ikinci", adı) alt etkileşimde [SecimBecerisi]'ne gelir (Harita `offer`). */
    private suspend fun liste(k: Kanallar, id: String, baslik: String, bos: String): SkillOutput {
        val items = k.cocuklar(id).filter { it.mediaId != null }
        if (items.isEmpty()) return Cikti(bos)
        val adlar = items.map { it.description.title?.toString().orEmpty() }
        return Cikti(Secim.oku(baslik, adlar), plan = InteractionPlan.StartSubInteraction(
            reopenMicrophone = true, nextSkills = listOf(SecimBecerisi(adlar, items.map { it.mediaId!! })),
        ))
    }

    /**
     * "Videoya not al …": not tek dinlemeyle alınmaz, dikte sürer (Harita `dictateNote`): komutla söylenen kısım
     * başlangıçtır, susunca (~3,5 sn) ya da "tamam" deyince yazılır, "iptal" almaz. Not Kanallarım'ın oturumuna gider,
     * Kanallarım o anın altyazısıyla Notlar'a yazar.
     */
    private suspend fun not(ctx: SkillContext, k: Kanallar, metin: String): SkillOutput {
        val c = k.calan()
        if (!c.kanallarim || c.baslik == null) return Cikti("Kanallarım'da çalan bir video yok.")
        val ses = withContext(Dispatchers.Main) { Ses.al(ctx.android) }
        if (metin.isBlank()) konusVeBekle(ses, "Videoya ne not alayım?") else ses.isaret(Tones.Kind.LISTEN)
        val t = dikte(ctx.android, metin)
            ?: return Cikti(if (metin.isBlank()) "Not almadım." else "Not iptal edildi.")
        return when (k.notAl(Dictation.finish(t))) {
            1 -> Cikti("Not alındı.")
            2 -> Cikti("Not alındı; Notlar'a giriş yapılınca YouTube klasörüne geçecek.")
            null -> Cikti("Kanallarım'da çalan bir video yok.")
            else -> Cikti("Not kaydedilemedi. Notlar uygulamasını güncelleyin.")
        }
    }

    private companion object {
        val ONAY = mapOf(Eylem.PAUSE to "Duraklatıldı", Eylem.PLAY to "Oynatılıyor", Eylem.NEXT to "Sonraki", Eylem.PREVIOUS to "Önceki")
    }
}

/** Okunan listeden seçim: "ikinci", "sonuncusu" ya da adı; seçilen kaldığı yerden çalar. */
class SecimBecerisi(private val adlar: List<String>, private val kimlikler: List<String>) :
    Skill<Int?>(KanallarInfo, Specificity.HIGH) {

    override fun score(ctx: SkillContext, input: String): Pair<Score, Int?> {
        val i = Secim.sec(input, adlar) ?: return Pair(AlwaysWorstScore, null)
        return Pair(FloatScore(1f), i)
    }

    override suspend fun generateOutput(ctx: SkillContext, inputData: Int?): SkillOutput {
        val i = inputData ?: return Cikti("Seçemedim.")
        if (!Kanallar.al(ctx.android).cal(kimlikler[i])) return Cikti("Kanallarım'a bağlanılamadı.")
        return Cikti("", "${adlar[i]} açılıyor", plan = InteractionPlan.FinishSubInteraction(reopenMicrophone = false))
    }
}

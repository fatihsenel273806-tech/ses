package com.ses.asistan.beceri

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import com.ses.asistan.beceri.DosyalarSozleri.Komut
import com.ses.asistan.kopru.Kopru
import com.ses.asistan.kopru.Uygulamalar
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
 * Dosya Okuyucu becerisi (PLAN Faz 5): "X dosyasını aç", "son dosyayı aç", "sesli oku", "okumayı durdur", "özetle".
 * Komut Dosya Okuyucu'nun kişisel derlemesindeki köprü servisine `KOMUT` olarak gider (PROTOKOL.md › "Dosya Okuyucu
 * komutları"); arama, açma, sesli okuma (uygulamanın kendi `tts_service`'i) ve özet (uygulamanın Gemini'si) orada;
 * özet Dosya Okuyucu'nun özet sayfasında açılır.
 * Asistan yalnız cevabı söyler ve istenen ekranı açar. Genel (dağıtılan) Dosya Okuyucu'da köprü yoktur.
 */
object DosyalarInfo : SkillInfo("dosyalar") {
    const val PKG = "com.dosyaokuyucu.dosya_okuyucu"

    override fun name(context: Context) = "Dosya Okuyucu"
    override fun sentenceExample(context: Context) = "Kira sözleşmesi dosyasını aç"

    @Composable
    override fun icon() = rememberVectorPainter(Icons.Filled.Description)

    override fun build(ctx: SkillContext): Skill<*> = DosyalarBecerisi()
}

class DosyalarBecerisi : Skill<Komut?>(DosyalarInfo, Specificity.HIGH) {

    override fun score(ctx: SkillContext, input: String): Pair<Score, Komut?> {
        val k = DosyalarSozleri.parse(input) ?: return Pair(AlwaysWorstScore, null)
        return Pair(FloatScore(1f), k)
    }

    override suspend fun generateOutput(ctx: SkillContext, inputData: Komut?): SkillOutput {
        val komut = inputData ?: return Cikti("Anlamadım.")
        val sonuc = withContext(Dispatchers.IO) {
            Kopru.hazirla(ctx.android)
            if (Uygulamalar.bul(ctx.android, DosyalarInfo.PKG) == null) null
            // okuma ve özet Dosya Okuyucu'da başlar (K7): köprü yalnız dosyayı bulup bağlantıyı verir, beklemek gerekmez
            else Kopru.komut(DosyalarInfo.PKG, komut.ad, komut.sorgu, 10_000L)
                ?: Kopru.Sonuc("Dosya Okuyucu cevap vermedi.", false)
        } ?: return Cikti("Dosya Okuyucu'nun kişisel sürümü kurulu değil.")
        return Cikti(sonuc.metin.ifBlank { if (sonuc.tamam) "Tamam." else "Olmadı." })
    }
}

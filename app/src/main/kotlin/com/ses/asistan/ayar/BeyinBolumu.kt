package com.ses.asistan.ayar

import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.ses.asistan.beyin.BeyinAyarlari
import com.ses.asistan.beyin.GeminiHavuzu
import org.stypox.dicio.settings.ui.BooleanSetting
import org.stypox.dicio.settings.ui.SettingsItem

/**
 * Gemini (Faz 6): hiçbir beceri anlamayınca sorulur. Anahtar telefonda saklanır, ekranda yalnız sonu görünür
 * (Harita'daki gibi); birden çok anahtar ve model virgülle ya da satır satır verilir.
 */
@Composable
internal fun BeyinBolumu() {
    val ctx = LocalContext.current
    val a = remember { BeyinAyarlari.al(ctx) }
    var n by remember { mutableIntStateOf(0) }
    DisposableEffect(a) {
        val l = a.dinle { n++ }
        onDispose { a.birak(l) }
    }
    // ayar değişince yeniden okunur
    val acik = remember(n) { a.acik }
    val anahtarlar = remember(n) { a.anahtarlar }
    val modeller = remember(n) { a.modeller }
    var duzenle by remember { mutableStateOf<Int?>(null) }

    BooleanSetting(
        title = "Gemini",
        icon = Icons.Default.AutoAwesome,
        descriptionOn = "Hiçbir beceri anlamazsa Gemini'ye sorulur (bağlı uygulamaların komutlarını da kullanır)",
        descriptionOff = "Kapalı: anlaşılmayan söz için “anlamadım”",
    ).Render(acik) { a.acik = it }
    SettingsItem(
        modifier = Modifier.clickable { duzenle = 0 },
        title = "Gemini API anahtarı",
        icon = Icons.Default.Key,
        description = if (anahtarlar.isEmpty()) "Girilmedi (aistudio.google.com › API anahtarı)"
        else anahtarlar.joinToString(", ") { "…" + GeminiHavuzu.son(it) },
    )
    SettingsItem(
        modifier = Modifier.clickable { duzenle = 1 },
        title = "Gemini modelleri",
        icon = Icons.Default.Tune,
        description = modeller.joinToString(" › ") + " (biri dolunca sıradaki)",
    )
    when (duzenle) {
        0 -> MetinPenceresi("Gemini API anahtarı", "Birden çok anahtar virgülle", a.anahtarlarHam, { duzenle = null }) { a.anahtarlarHam = it }
        1 -> MetinPenceresi("Gemini modelleri", "Öncelik sırasıyla; boş: varsayılan", a.modellerHam, { duzenle = null }) { a.modellerHam = it }
    }
}

@Composable
private fun MetinPenceresi(baslik: String, ipucu: String, ilk: String, kapat: () -> Unit, kaydet: (String) -> Unit) {
    var metin by remember { mutableStateOf(ilk) }
    AlertDialog(
        onDismissRequest = kapat,
        confirmButton = { TextButton(onClick = { kaydet(metin); kapat() }) { Text("Kaydet") } },
        dismissButton = { TextButton(onClick = kapat) { Text("Vazgeç") } },
        title = { Text(baslik) },
        text = { OutlinedTextField(value = metin, onValueChange = { metin = it }, label = { Text(ipucu) }) },
    )
}

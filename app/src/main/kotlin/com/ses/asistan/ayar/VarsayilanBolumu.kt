package com.ses.asistan.ayar

import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.ses.asistan.giris.VarsayilanAsistan
import org.stypox.dicio.settings.ui.SettingsItem

/**
 * K6: varsayılan dijital asistan (güç tuşuna basılı tutma) ve kulaklık / araç düğmesi. Rol yalnız sistem
 * ayarlarından verilir; ekrana dönünce durum yenilenir.
 */
@Composable
internal fun VarsayilanBolumu() {
    val ctx = LocalContext.current
    var n by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) { n++; onPauseOrDispose { } }
    val rolde = remember(n) { VarsayilanAsistan.rolde(ctx) }
    SettingsItem(
        modifier = Modifier.clickable { VarsayilanAsistan.ayarlariAc(ctx) },
        title = "Varsayılan dijital asistan",
        icon = Icons.Default.PowerSettingsNew,
        description = if (rolde) "Asistan: güç tuşuna basılı tutunca dinler (kilit ekranında da)"
        else "Başka uygulama. Dokunun › “Dijital asistan uygulaması” › Asistan. HyperOS: Ayarlar › Ek ayarlar › " +
            "Hareket kısayolları › “Güç düğmesini basılı tut” › asistan",
    )
    SettingsItem(
        title = "Kulaklık ve araç düğmesi",
        icon = Icons.Default.Headset,
        description = "Araç Bluetooth'unun sesli komut düğmesi ve kulaklık düğmesine uzun basma Asistan'ı açar; " +
            "ilk seferde Android hangi uygulamayla açılacağını sorarsa Asistan › Her zaman",
    )
}

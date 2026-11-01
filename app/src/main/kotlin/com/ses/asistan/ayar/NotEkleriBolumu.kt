package com.ses.asistan.ayar

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Screenshot
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.ses.asistan.ek.EkranServisi
import com.ses.asistan.ek.Konum
import org.stypox.dicio.settings.ui.SettingsItem

/**
 * Not ekleri (2026-11-01): "ekran görüntüsü al" için erişilebilirlik servisi, "konumu da ekle" için konum izni. Asistan
 * çoğu zaman arka planda çalıştığından konumda "her zaman izin ver" gerekir. Ekrana dönünce durum yenilenir.
 */
@Composable
internal fun NotEkleriBolumu() {
    val ctx = LocalContext.current
    var n by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) { n++; onPauseOrDispose { } }
    val ekran = remember(n) { EkranServisi.acik() }
    SettingsItem(
        modifier = Modifier.clickable { EkranServisi.ayarlariAc(ctx) },
        title = "Ekran görüntüsü",
        icon = Icons.Default.Screenshot,
        description = when {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.R -> "Android 11 gerekiyor"
            ekran -> "Açık: “ekran görüntüsü al, nota ekle” çalışır"
            else -> "Kapalı: dokunun, Yüklü uygulamalar › “Asistan: ekran görüntüsü”nü açın"
        },
    )
    val konum = remember(n) { Konum.izinVar(ctx) }
    val arka = remember(n) { Konum.arkaPlanIzni(ctx) }
    val uygulamaAyari = {
        ctx.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
    val iste = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { n++ }
    SettingsItem(
        modifier = Modifier.clickable {
            // Android 11+'da "her zaman izin ver" yalnız sistem ayarından verilir
            if (!konum) iste.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            else uygulamaAyari()
        },
        title = "Nota konum",
        icon = Icons.Default.LocationOn,
        description = when {
            !konum -> "İzin yok: dokunun, konuma izin verin"
            !arka -> "Yalnız uygulama açıkken: dokunun, İzinler › Konum › “Her zaman izin ver”"
            else -> "Verildi: “konumu da ekle” her uygulamada çalışır"
        },
    )
}

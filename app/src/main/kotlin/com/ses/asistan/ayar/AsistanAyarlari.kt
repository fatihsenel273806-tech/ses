package com.ses.asistan.ayar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import com.ses.asistan.giris.UyanmaKapisi
import com.ses.asistan.giris.VoskWakeDevice
import org.stypox.dicio.io.wake.WakeService
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import com.ses.asistan.ses.Ses
import com.ses.asistan.ses.SesAyarlari
import com.ses.asistan.ses.UyanmaPolitikasi
import org.stypox.dicio.settings.datastore.InputDevice
import org.stypox.dicio.settings.datastore.SpeechOutputDevice
import org.stypox.dicio.settings.datastore.WakeDevice
import org.stypox.dicio.settings.ui.BooleanSetting
import org.stypox.dicio.settings.ui.IntSetting
import org.stypox.dicio.settings.ui.ListSetting
import org.stypox.dicio.settings.ui.SettingsCategoryTitle
import org.stypox.dicio.settings.ui.SettingsItem
import org.stypox.dicio.settings.ui.StringSetting

// Asistanın ayarları: Dicio'nun ayar ekranına iki kancayla girer (Definitions.kt'deki seçenekler ve
// MainSettingsScreen'deki asistanAyarlari() bölümü). Harita `VoicePicker`'ın karşılığı burada.

/** Ayarlarda okunan örnek cümleler: yönlendirme, uyarı ve cevap tonu bir arada duyulsun (Harita VOICE_SAMPLES). */
private val ORNEKLER = listOf(
    "300 metre sonra sağa dönün, ardından dönel kavşaktan ikinci çıkış.",
    "İleride radar var, hız sınırı 70.",
    "Kızılay'a 12 dakika, varış 18:40.",
)

fun asistanGirisi() = ListSetting.Value(
    value = InputDevice.INPUT_DEVICE_GOOGLE,
    name = "Google konuşma tanıma",
    description = "Telefonun Google tanıyıcısı; Türkçe açık metinde en doğrusu",
    icon = Icons.Default.Mic,
)

fun asistanUyanmasi() = ListSetting.Value(
    value = WakeDevice.WAKE_DEVICE_VOSK,
    name = "Hey Asistan (Vosk)",
    description = "“Hey Asistan”, “Hey Harita”, “Hey Notlar”, “Hey Kanallar”; cihazda, ses odağı almaz (36 MB, bir kez iner)",
)

fun asistanSesi() = ListSetting.Value(
    value = SpeechOutputDevice.SPEECH_OUTPUT_DEVICE_ANTALIA,
    name = "Asistan sesi (Antalia)",
    description = "Tek ses kuyruğu; Antalia hazır değilken Android'in Türkçe sesi",
    icon = Icons.Default.RecordVoiceOver,
)

/** Asistan bölümü (ses ve uyanma). */
fun LazyListScope.asistanAyarlari() {
    item { SettingsCategoryTitle("Asistan sesi") }
    item { SesBolumu() }
    item { SettingsCategoryTitle("Uyanma (“Hey Asistan”)") }
    item { UyanmaBolumu() }
}

/** Ayarları Compose durumuna bağlar: değişince ekran yenilenir. */
@Composable
private fun ayarSayaci(a: SesAyarlari): Int {
    var n by remember { mutableIntStateOf(0) }
    DisposableEffect(a) {
        val l = a.dinle { n++ }
        onDispose { a.birak(l) } // dinleyici SharedPreferences'ta zayıf tutulur; ekran açıkken referans burada
    }
    return n
}

@Composable
private fun SesBolumu() {
    val ctx = LocalContext.current
    val ses = remember { Ses.al(ctx) }
    val a = ses.ayarlar
    ayarSayaci(a)
    var ornek by remember { mutableIntStateOf(0) }
    var sesSecici by remember { mutableStateOf(false) }

    ListSetting(
        title = "Ses motoru",
        icon = Icons.Default.GraphicEq,
        possibleValues = listOf(
            ListSetting.Value(SesAyarlari.ENGINE_ANTALIA, "Antalia", "Cihazda üretilen doğal ses (~15 MB, ilk kullanımda iner)"),
            ListSetting.Value(SesAyarlari.ENGINE_ANDROID, "Android sesi", "Telefonun Türkçe metin okuma sesi"),
        ),
    ).Render(a.voiceEngine) {
        a.voiceEngine = it
        if (it == SesAyarlari.ENGINE_ANTALIA) ses.voice.ensureAntalia()
    }
    ListSetting(
        title = "Antalia kalitesi",
        icon = Icons.Default.Tune,
        possibleValues = listOf(
            ListSetting.Value(2, "Hızlı", "Sesin üretimi en kısa"),
            ListSetting.Value(4, "Dengeli"),
            ListSetting.Value(8, "En iyi", "Telefonda yavaş olabilir"),
        ),
    ).Render(a.antaliaSteps) { a.antaliaSteps = it }
    ListSetting(
        title = "Ses düzeyi",
        icon = Icons.AutoMirrored.Filled.VolumeUp,
        description = "Yalnız asistanın konuşması; telefonun sesi değişmez",
        possibleValues = listOf(
            ListSetting.Value(0, "Düşük"),
            ListSetting.Value(1, "Orta"),
            ListSetting.Value(2, "Yüksek"),
        ),
    ).Render(a.voiceVolume) { a.voiceVolume = it }
    SettingsItem(
        modifier = Modifier.clickable { sesSecici = true },
        title = "Android sesi",
        icon = Icons.Default.RecordVoiceOver,
        description = androidSesEtiketi(ses),
    )
    IntSetting(
        title = "Android sesinin hızı",
        icon = Icons.Default.Speed,
        minimum = 7, maximum = 15,
        description = { "%${it * 10}" },
    ).Render(a.ttsRate / 10) { a.ttsRate = it * 10; ses.voice.applyAndroidVoice() }
    IntSetting(
        title = "Android sesinin perdesi",
        icon = Icons.Default.Tune,
        minimum = 7, maximum = 15,
        description = { "%${it * 10} · düşük: kalın ses, yüksek: ince ses" },
    ).Render(a.ttsPitch / 10) { a.ttsPitch = it * 10; ses.voice.applyAndroidVoice() }
    SettingsItem(
        modifier = Modifier.clickable { ses.voice.sample(ORNEKLER[ornek++ % ORNEKLER.size], a.voiceEngine == SesAyarlari.ENGINE_ANDROID) },
        title = "Örnek dinle",
        icon = Icons.Default.PlayArrow,
        description = "Seçili sesle örnek bir cümle okunur",
    )
    if (sesSecici) AndroidSesSecici(ses) { sesSecici = false }
}

private fun androidSesEtiketi(ses: Ses): String {
    val list = ses.voice.androidVoices()
    val i = list.indexOfFirst { it.name == ses.ayarlar.androidVoice }
    return if (i < 0) "Varsayılan" else sesAdi(i, list[i])
}

private fun sesAdi(i: Int, v: android.speech.tts.Voice) =
    "Ses ${i + 1}" + if (v.isNetworkConnectionRequired) " · internet" else " · cihazda"

/** Türkçe seslerin listesi: dokununca seçilir ve örnek okunur (Harita showVoicePicker). */
@Composable
private fun AndroidSesSecici(ses: Ses, kapat: () -> Unit) {
    val list = remember { ses.voice.androidVoices() }
    var secili by remember { mutableStateOf(ses.ayarlar.androidVoice) }
    var n by remember { mutableIntStateOf(0) }
    val satirlar = listOf("" to "Varsayılan") + list.mapIndexed { i, v -> v.name to sesAdi(i, v) }
    AlertDialog(
        onDismissRequest = { ses.voice.stop(); kapat() },
        confirmButton = { TextButton(onClick = { ses.voice.stop(); kapat() }) { Text("Bitti") } },
        title = { Text("Android sesi") },
        text = {
            if (list.isEmpty()) {
                Text("Türkçe ses bulunamadı. Android ayarları › Metin okuma çıkışı'ndan Türkçe ses verisini indirin.")
            } else {
                LazyColumn {
                    itemsIndexed(satirlar) { _, (ad, baslik) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable {
                                secili = ad
                                ses.ayarlar.androidVoice = ad
                                ses.voice.applyAndroidVoice()
                                ses.voice.sample(ORNEKLER[n++ % ORNEKLER.size], true)
                            },
                        ) {
                            RadioButton(selected = secili == ad, onClick = null)
                            Text(baslik)
                        }
                    }
                }
            }
        },
    )
}

/**
 * Uyanmanın neden çalışıp çalışmadığı (telefonda "hiç uyanmıyor" denince ilk bakılacak yer). Ayar ekranı asistanın
 * kendi ekranı olduğu için politika ekran dışındaki hâliyle söylenir.
 */
private fun uyanmaDurumu(ctx: android.content.Context): String {
    if (!VoskWakeDevice.hazir(ctx)) return "Uyanma modeli inmedi: ana ekrandaki indirme kartına dokunun (36 MB)"
    if (androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.RECORD_AUDIO) !=
        android.content.pm.PackageManager.PERMISSION_GRANTED) return "Mikrofon izni yok"
    if (!WakeService.isRunning()) return "Uyanma servisi çalışmıyor: “Uyanma yöntemi”nde Hey Asistan (Vosk) seçili olmalı"
    return "Ekran dışında: " + UyanmaPolitikasi.neden(UyanmaKapisi.durum(ctx).copy(asistanEkranda = false))
}

@Composable
private fun UyanmaBolumu() {
    val ctx = LocalContext.current
    val a = remember { SesAyarlari.al(ctx) }
    ayarSayaci(a)
    // her saniye yenilenir: Bluetooth bağlanınca, model inince, servis başlayınca görülsün
    var durum by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { while (true) { durum = uyanmaDurumu(ctx); delay(1000) } }
    SettingsItem(title = "Uyanma şu an", icon = Icons.Default.Info, description = durum)
    ListSetting(
        title = "Ne zaman dinlensin",
        icon = Icons.Default.Hearing,
        description = "Sürekli dinleme pil harcar",
        possibleValues = listOf(
            ListSetting.Value(UyanmaPolitikasi.Kip.ARACTA_VE_ELLE, "Araçta + elle", "Araç Bluetooth'u bağlıyken ya da navigasyonda; ayrıca bildirimden elle"),
            ListSetting.Value(UyanmaPolitikasi.Kip.HER_ZAMAN, "Her zaman", "Asistan servisi çalıştıkça"),
            ListSetting.Value(UyanmaPolitikasi.Kip.YALNIZ_ELLE, "Yalnız elle", "Bildirimdeki “Dinle / bırak” ya da aşağıdaki anahtar"),
        ),
    ).Render(a.uyanmaKipi) { a.uyanmaKipi = it }
    if (a.uyanmaKipi == UyanmaPolitikasi.Kip.ARACTA_VE_ELLE) {
        StringSetting(
            title = "Aracın Bluetooth adı",
            icon = Icons.Default.DirectionsCar,
            descriptionWhenEmpty = "Boş: her Bluetooth ses aygıtı (kulaklık da) araç sayılır",
        ).Render(a.aracAdi) { a.aracAdi = it }
    }
    if (a.uyanmaKipi != UyanmaPolitikasi.Kip.HER_ZAMAN) {
        BooleanSetting(
            title = "Elle dinleme",
            icon = Icons.Default.TouchApp,
            descriptionOn = "“Hey Asistan” şimdi dinleniyor",
            descriptionOff = "Kapalı (araçta kendiliğinden açılır)",
        ).Render(a.elleAcik) { a.elleAcik = it }
    }
}

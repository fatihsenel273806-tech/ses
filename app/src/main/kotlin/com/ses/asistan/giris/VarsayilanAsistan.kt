package com.ses.asistan.giris

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.speech.RecognizerIntent

/**
 * K6 (PLAN Faz 7): asistan **varsayılan dijital asistan** (`ROLE_ASSISTANT`; güç tuşuna basılı tutma, kulaklık ve
 * araç düğmesi). Rol Dicio'nun `ACTION_ASSIST` etkinliğiyle alınır; `VoiceInteractionService` bilerek yok: o olsaydı
 * Android sistemin varsayılan konuşma tanıyıcısını da asistanınkine çevirirdi ve Notlar'ın kendi dikte'si, Harita'nın
 * yedek tanıması asistanın üzerinden geçerdi (D5; HAFIZA "Reddedilen yollar").
 *
 * Girişler (hepsi Dicio'nun mevcut "assist" yolunu açar: tanıyıcı hemen dinler):
 *  - güç tuşu / asistan hareketi → `ACTION_ASSIST` (Dicio),
 *  - araç Bluetooth'unun sesli komut düğmesi → `ACTION_VOICE_COMMAND` (Dicio),
 *  - kulaklık düğmesine uzun basma → ekran açıkken `WEB_SEARCH`, kilitliyken `VOICE_SEARCH_HANDS_FREE` (bizim ek).
 */
object VarsayilanAsistan {
    private val EK = setOf(RecognizerIntent.ACTION_WEB_SEARCH, RecognizerIntent.ACTION_VOICE_SEARCH_HANDS_FREE)
    private val KILITTE = EK + setOf(Intent.ACTION_ASSIST, Intent.ACTION_VOICE_COMMAND)

    /** Dicio'nun ASSIST / VOICE_COMMAND'ına ek assist girişi mi (kulaklık düğmesi). */
    fun ekGiris(intent: Intent?): Boolean = intent?.action in EK

    /** Kilit ekranının üstünde açılıp ekranı yaksın mı (araçta, cepte kilitli telefon). */
    fun kilitteGoster(intent: Intent?): Boolean = intent?.action in KILITTE

    /** Asistan şu an varsayılan dijital asistan mı. */
    fun rolde(ctx: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ctx.getSystemService(RoleManager::class.java)?.isRoleHeld(RoleManager.ROLE_ASSISTANT) == true
        } else {
            Settings.Secure.getString(ctx.contentResolver, "assistant")?.startsWith(ctx.packageName + "/") == true
        }

    /**
     * Varsayılan uygulamalar ekranı ("Dijital asistan uygulaması"). Asistan rolü istek kutusuyla verilemez
     * (`RoleManager.createRequestRoleIntent` bu rolde reddedilir); kullanıcı ayarlardan seçer.
     */
    fun ayarlariAc(ctx: Context) {
        val niyetler = listOf(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS, Settings.ACTION_VOICE_INPUT_SETTINGS, Settings.ACTION_SETTINGS)
        for (eylem in niyetler) {
            val i = Intent(eylem).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (runCatching { ctx.startActivity(i) }.isSuccess) return
        }
    }
}

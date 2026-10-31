package com.ses.asistan.ses

import android.content.Context
import org.dicio.skill.context.SpeechOutputDevice

/**
 * Dicio'nun ses çıkışı arayüzü → asistanın tek ses kuyruğu ([Ses]). Dicio'nun söyledikleri [SpeechQueue.Kind.CEVAP]
 * sınıfındadır; "sus" yalnız cevapları keser, yönlendirme sesi (Faz 3) sürer.
 */
class AsistanSpeechDevice(ctx: Context) : SpeechOutputDevice {
    private val ses = Ses.al(ctx)

    override fun speak(speechOutput: String) = ses.soyle(speechOutput, SpeechQueue.Kind.CEVAP)

    override fun stopSpeaking() = ses.sustur(SpeechQueue.Kind.CEVAP)

    override val isSpeaking: Boolean
        get() = ses.konusuyor

    override fun runWhenFinishedSpeaking(runnable: Runnable) = ses.bitinceCalistir(runnable)

    // Ses tek örnek ve süreç boyunca yaşar; ayar değişince bu sarmalayıcı yenilenir, ses kapanmaz
    override fun cleanup() {}
}

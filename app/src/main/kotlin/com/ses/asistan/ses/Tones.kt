package com.ses.asistan.ses

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/**
 * Kısa ses işaretleri: tehlike uyarısı (polis, kaza, kapalı şerit) ve asistanın "dinliyorum / anladım" sesi.
 * Konuşma motorunu (Antalia/TTS) beklemez: dinlemeye anında geçilir, sürücü ekrana bakmadan anlar.
 * Örnekler [ToneMath] ile bir kez üretilir; çalma arka planda ([AudioTrack] MODE_STATIC).
 */
class Tones {
    private val worker = Executors.newSingleThreadExecutor()
    private val cache = HashMap<Kind, ShortArray>()

    enum class Kind { ALERT, LISTEN, DONE }

    fun play(kind: Kind) {
        worker.execute {
            runCatching {
                val pcm = cache.getOrPut(kind) { ToneMath.pcm(kind, RATE) }
                val t = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder().setSampleRate(RATE).setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build()
                    )
                    .setBufferSizeInBytes(pcm.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
                t.write(pcm, 0, pcm.size)
                t.play()
                Thread.sleep(pcm.size * 1000L / RATE + 60)
                t.release()
            }
        }
    }

    fun shutdown() = worker.shutdownNow()

    companion object {
        const val RATE = 22_050
    }
}

/** Ses işaretlerinin örnekleri (saf hesap, bkz. ToneMathTest). */
object ToneMath {
    /** (frekans Hz, süre ms, ardından sessizlik ms) dizileri. */
    fun notes(kind: Tones.Kind): List<Triple<Double, Int, Int>> = when (kind) {
        // iki tonlu, üç kez, alçak perdede: dikkat çeker ama irkiltmez
        Tones.Kind.ALERT -> List(3) { listOf(Triple(784.0, 140, 30), Triple(587.0, 160, 120)) }.flatten()
        // yükselen iki yumuşak nota, orta perdede: "dinliyorum" (eski 660→990 Hz keskin "dıt" rahatsız ediyordu)
        Tones.Kind.LISTEN -> listOf(Triple(523.0, 110, 10), Triple(659.0, 150, 0))
        // inen iki nota: "anladım"
        Tones.Kind.DONE -> listOf(Triple(659.0, 90, 10), Triple(523.0, 120, 0))
    }

    /**
     * Genlik (0..1). Uyarı sesi çok yüksek bulundu: navigasyon sesi kanalında konuşmanın çok üstünde çalıyordu;
     * ~−12 dB kısıldı (0,6 → 0,15). Asistanın dinleme işaretleri de yüksekti (0,6): ~−10 dB, yumuşak giriş/çıkışla.
     */
    fun gain(kind: Tones.Kind) = if (kind == Tones.Kind.ALERT) 0.15 else 0.18

    /** Yumuşak giriş/çıkış (ms): asistan işaretleri zil gibi yavaş açılır ve söner, uyarı daha belirgin. */
    private fun rampMs(kind: Tones.Kind) = if (kind == Tones.Kind.ALERT) 8 else 35

    fun pcm(kind: Tones.Kind, rate: Int): ShortArray {
        val list = notes(kind)
        val amp = gain(kind) * Short.MAX_VALUE
        val total = list.sumOf { (it.second + it.third) * rate / 1000 }
        val out = ShortArray(total)
        var i = 0
        for ((f, ms, gap) in list) {
            val n = ms * rate / 1000
            // yumuşak giriş/çıkış: tıkırtı olmasın
            val ramp = min(n / 2, rate * rampMs(kind) / 1000).coerceAtLeast(1)
            for (k in 0 until n) {
                val env = min(1.0, min(k, n - 1 - k).toDouble() / ramp)
                out[i++] = (sin(2 * PI * f * k / rate) * env * amp).toInt().toShort()
            }
            i += gap * rate / 1000
        }
        return out
    }
}

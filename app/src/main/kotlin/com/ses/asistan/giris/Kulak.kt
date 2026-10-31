package com.ses.asistan.giris

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import com.ses.asistan.ses.Dictation
import com.ses.asistan.ses.Ses
import com.ses.asistan.ses.SesDurumu
import com.ses.asistan.ses.Tones

/**
 * Google tanıyıcısıyla dinleme: tek komut ([komut]) ve kesintisiz dikte ([dikte]). Harita `Ears`'ten taşındı; uyanma
 * kısmı yok (uyanma Vosk ile, [org.stypox.dicio.io.wake.WakeService] döngüsünde). Ana iş parçacığında kullanılır.
 *
 * Kurallar (HAFIZA "Taşınacak ses tuzakları"):
 *  - Tanıyıcı ilk kısa sessizlikte cümleyi bitirir, uzun oturumu desteklemez: dikte her cümle sonunda hemen yeniden
 *    açılır, parçalar [Dictation] ile birikir.
 *  - Her başlatmada bildirim kanalından bip çalar: dinlerken bildirim kanalı susturulur, çökme sonrası açılışta geri
 *    açılır; kullanıcının kendi susturmasına dokunulmaz.
 *  - İzin eksikken tanıyıcı sessizce ERROR_INSUFFICIENT_PERMISSIONS verir: önce `checkSelfPermission`.
 *  - Mikrofonu uyanma kaydı tutuyorsa tanıyıcı sessizlik duyar: önce [SesDurumu.tanimaIstiyor], kayıt durunca açılır.
 */
class Kulak private constructor(private val ctx: Context) {
    sealed interface Olay {
        data class Kismi(val metin: String) : Olay
        /** Seçenekler güvene göre sıralı (1 en iyi). */
        data class Sonuc(val secenekler: List<Pair<String, Float>>) : Olay
        data object Bos : Olay
        data class Hata(val kod: Int) : Olay
    }

    private val main = Handler(Looper.getMainLooper())
    private val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val sp = ctx.getSharedPreferences("kulak", Context.MODE_PRIVATE)
    private var rec: SpeechRecognizer? = null
    private var mode = Mode.IDLE
    private var komutCb: ((Olay) -> Unit)? = null

    /** Dinleme açıldı/kapandı (arayüz durumu). */
    var onListening: (Boolean) -> Unit = {}

    private enum class Mode { IDLE, COMMAND, DICTATE }

    // önceki oturum susturmayı açık bırakarak kapandıysa (çökme) bildirim sesi geri açılır
    init { muteBeep() }

    val available get() = SpeechRecognizer.isRecognitionAvailable(ctx)
    val dinliyor get() = mode != Mode.IDLE

    // ------------------------------------------------------------------ bip susturma (Harita Ears.muteBeep)
    /** Komut dinlenirken bildirim kanalı susturulur; kapanış bip'i de yutulsun diye biraz sonra açılır. */
    private var commandMute = false
    private val unmuteCommand = Runnable { if (mode == Mode.IDLE) { commandMute = false; muteBeep() } }
    private fun commandEnded() { main.removeCallbacks(unmuteCommand); main.postDelayed(unmuteCommand, UNMUTE_AFTER_MS) }

    private fun muteBeep() {
        if (Build.VERSION.SDK_INT < 23) return
        val want = commandMute
        val mine = sp.getBoolean(MUTED, false)
        if (want == mine) return
        if (want && am.isStreamMute(AudioManager.STREAM_NOTIFICATION)) return
        runCatching {
            am.adjustStreamVolume(AudioManager.STREAM_NOTIFICATION, if (want) AudioManager.ADJUST_MUTE else AudioManager.ADJUST_UNMUTE, 0)
        }.onSuccess { sp.edit().putBoolean(MUTED, want).commit() }
    }

    // ------------------------------------------------------------------ mikrofon devri
    private fun izinVar() = ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    /** Uyanma kaydı mikrofonu bırakınca (en çok [DEVIR_MS]) [f] çalışır. */
    private fun mikrofonAl(f: () -> Unit) {
        SesDurumu.tanimaIstiyor = true
        val bitis = SystemClock.elapsedRealtime() + DEVIR_MS
        val dene = object : Runnable {
            override fun run() {
                if (mode == Mode.IDLE) return
                if (!SesDurumu.uyanmaKaydiAcik || SystemClock.elapsedRealtime() >= bitis) f() else main.postDelayed(this, 20)
            }
        }
        dene.run()
    }

    private fun mikrofonBirak() {
        SesDurumu.tanimaIstiyor = false
    }

    private fun recognizer(): SpeechRecognizer {
        rec?.let { return it }
        val r = SpeechRecognizer.createSpeechRecognizer(ctx)
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
            override fun onPartialResults(partial: Bundle?) {
                val txt = partial?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: return
                if (mode == Mode.COMMAND) { komutCb?.invoke(Olay.Kismi(txt)); return }
                if (mode == Mode.DICTATE) {
                    if (txt.isNotBlank() && txt != dictPartial) { dictPartial = txt; lastVoiceAt = now() }
                    dictPartialCb?.invoke(Dictation.join(dictAcc, txt))
                }
            }

            override fun onResults(results: Bundle?) {
                val list = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                if (mode == Mode.DICTATE) { dictSegment(list.firstOrNull()); return }
                if (mode != Mode.COMMAND) return
                val conf = results?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)
                komutBitti(
                    if (list.isEmpty()) Olay.Bos
                    else Olay.Sonuc(list.mapIndexed { i, t -> t to (conf?.getOrNull(i)?.takeIf { it >= 0f } ?: (1f - i * 0.1f)) })
                )
            }

            override fun onError(error: Int) {
                if (mode == Mode.DICTATE) { dictError(error); return }
                if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY || error == SpeechRecognizer.ERROR_CLIENT) {
                    rec?.destroy(); rec = null
                }
                if (mode != Mode.COMMAND) return
                komutBitti(
                    if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) Olay.Bos
                    else Olay.Hata(error)
                )
            }
        })
        rec = r
        return r
    }

    private fun intent() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "tr-TR")
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, ctx.packageName)
        // komutta konuşan düşünürken kesilmesin (desteklemeyen tanıyıcı yok sayar)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1600L)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1200L)
    }

    private fun dictIntent() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "tr-TR")
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, ctx.packageName)
        // destekleyen tanıyıcıda cümle daha geç biter; desteklemeyen yok sayar (yine de dikte yeniden açılarak sürer)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 4000L)
    }

    private fun now() = SystemClock.elapsedRealtime()

    // ------------------------------------------------------------------ tek komut
    /** Tek komut dinle; olaylar ana iş parçacığında, son olay [Olay.Sonuc], [Olay.Bos] ya da [Olay.Hata]. */
    fun komut(cb: (Olay) -> Unit) {
        iptal()
        if (!izinVar()) { cb(Olay.Hata(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS)); return }
        komutCb = cb
        mode = Mode.COMMAND
        main.removeCallbacks(unmuteCommand)
        commandMute = true
        muteBeep()
        onListening(true)
        Ses.al(ctx).isaret(Tones.Kind.LISTEN)
        mikrofonAl {
            try {
                recognizer().startListening(intent())
            } catch (_: Exception) {
                komutBitti(Olay.Hata(SpeechRecognizer.ERROR_CLIENT))
            }
        }
    }

    private fun komutBitti(son: Olay) {
        if (mode != Mode.COMMAND) return
        mode = Mode.IDLE
        commandEnded()
        mikrofonBirak()
        onListening(false)
        if (son is Olay.Sonuc) Ses.al(ctx).isaret(Tones.Kind.DONE)
        val cb = komutCb; komutCb = null
        cb?.invoke(son)
    }

    // ------------------------------------------------------------------ dikte (Harita Ears.dictate)
    private var dictAcc = ""
    private var dictPartial = ""
    private var dictCb: ((String?) -> Unit)? = null
    private var dictPartialCb: ((String) -> Unit)? = null
    private var dictStarted = 0L
    private var lastVoiceAt = 0L
    private val dictQuiet = Runnable { dictCheck() }

    /**
     * Sesli not: [start] (komutla aynı cümlede söylenen kısım) üstüne söylenenler birikir. Tanıyıcı cümle sonunda
     * kapanınca hemen yeniden açılır; konuşan [DICT_PAUSE_MS] susunca (hiç başlamadıysa [DICT_FIRST_MS]), "tamam" deyince
     * ya da [DICT_MAX_MS] dolunca biter. [cb]: not metni, iptal ya da hiçbir şey duyulmadıysa null. [kismi]: canlı metin.
     */
    fun dikte(start: String, kismi: (String) -> Unit = {}, cb: (String?) -> Unit) {
        iptal()
        if (!izinVar()) { cb(null); return }
        dictAcc = start.trim()
        dictPartial = ""
        dictCb = cb
        dictPartialCb = kismi
        dictStarted = now()
        lastVoiceAt = dictStarted
        mode = Mode.DICTATE
        main.removeCallbacks(unmuteCommand)
        commandMute = true
        muteBeep()
        onListening(true)
        if (dictAcc.isNotEmpty()) kismi(dictAcc)
        mikrofonAl { dictOpen() }
    }

    private fun dictOpen() {
        if (mode != Mode.DICTATE) return
        dictPartial = ""
        main.removeCallbacks(dictQuiet)
        main.postDelayed(dictQuiet, 500)
        try {
            recognizer().startListening(dictIntent())
        } catch (_: Exception) {
            rec?.destroy(); rec = null
            main.postDelayed({ if (mode == Mode.DICTATE) runCatching { recognizer().startListening(dictIntent()) }.onFailure { dictEnd(dictAcc) } }, 300)
        }
    }

    /** Sessizlik denetimi: tanıyıcı açıkken de konuşan uzun süre sustuysa not biter (beklenen parça da eklenir). */
    private fun dictCheck() {
        if (mode != Mode.DICTATE) return
        val t = now()
        val quiet = t - lastVoiceAt
        val limit = if (dictAcc.isEmpty() && dictPartial.isEmpty()) DICT_FIRST_MS else DICT_PAUSE_MS
        if (quiet >= limit || t - dictStarted >= DICT_MAX_MS) {
            runCatching { rec?.cancel() }
            dictEnd(Dictation.join(dictAcc, dictPartial))
        } else main.postDelayed(dictQuiet, 300)
    }

    private fun dictSegment(seg: String?) {
        if (!seg.isNullOrBlank()) lastVoiceAt = now()
        when (val st = Dictation.feed(dictAcc, seg)) {
            is Dictation.Step.Cancel -> dictEnd(null)
            is Dictation.Step.Done -> dictEnd(st.text)
            is Dictation.Step.More -> {
                dictAcc = st.text
                dictPartial = ""
                // hemen yeniden aç: aradaki boşlukta söylenen kaybolmasın
                main.post { dictOpen() }
            }
        }
    }

    private fun dictError(error: Int) {
        if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY || error == SpeechRecognizer.ERROR_CLIENT) { rec?.destroy(); rec = null }
        if (dictPartial.isNotBlank()) { dictAcc = Dictation.join(dictAcc, dictPartial); dictPartial = "" }
        // izin/ağ hatası: dikte sürmez; sessizlik "konuşma yok" hatasıyla da gelir, süreyi dictCheck tartar
        val fatal = error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS || error == SpeechRecognizer.ERROR_AUDIO
        if (fatal) dictEnd(dictAcc) else main.postDelayed({ dictOpen() }, if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) 0 else 300)
    }

    private fun dictEnd(text: String?) {
        if (mode != Mode.DICTATE) return
        main.removeCallbacks(dictQuiet)
        mode = Mode.IDLE
        commandEnded()
        mikrofonBirak()
        onListening(false)
        val cb = dictCb; dictCb = null; dictPartialCb = null
        dictAcc = ""; dictPartial = ""
        cb?.invoke(text?.takeIf { it.isNotBlank() })
    }

    // ------------------------------------------------------------------
    /** Dinlemeyi bırakır; bekleyen geri çağrılar çağrılmaz. */
    fun iptal() {
        if (mode != Mode.IDLE) {
            runCatching { rec?.cancel() }
            commandEnded()
            onListening(false)
        }
        main.removeCallbacks(dictQuiet)
        dictCb = null; dictPartialCb = null
        komutCb = null
        mode = Mode.IDLE
        mikrofonBirak()
    }

    /** Komut dinlemesini erken bitirir: o ana kadar duyulan sonuç gelir (Dicio "dinlemeyi durdur"). */
    fun bitir() {
        if (mode == Mode.COMMAND) {
            runCatching { rec?.stopListening() }
        } else if (mode == Mode.DICTATE) {
            runCatching { rec?.cancel() }
            dictEnd(Dictation.join(dictAcc, dictPartial))
        }
    }

    companion object {
        private const val MUTED = "notifmuted"
        private const val UNMUTE_AFTER_MS = 900L
        /** Uyanma kaydının mikrofonu bırakması için en çok bekleme (Harita WakeWord.stop: 300 ms). */
        private const val DEVIR_MS = 400L
        /** Not dikte ederken bu kadar susunca not biter (düşünme payı); hiç başlamadıysa daha uzun beklenir. */
        private const val DICT_PAUSE_MS = 3500L
        private const val DICT_FIRST_MS = 7000L
        private const val DICT_MAX_MS = 3 * 60_000L

        @Volatile private var tek: Kulak? = null

        /** Ana iş parçacığında çağrılmalı. */
        fun al(ctx: Context): Kulak = tek ?: synchronized(this) {
            tek ?: Kulak(ctx.applicationContext).also { tek = it }
        }
    }
}

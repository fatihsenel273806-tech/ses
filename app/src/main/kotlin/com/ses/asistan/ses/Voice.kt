package com.ses.asistan.ses

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.ses.asistan.ses.tts.Antalia
import com.ses.asistan.ses.tts.AntaliaModel
import com.ses.asistan.ses.tts.SafeTensors
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Sesli çıktı. Antalia (Hugging Face: cloud0day3/antalia-mini) cihazda çalışır; ağırlıklar ilk
 * kullanımda indirilir (~15 MB). Hazır değilken ya da seçilmemişse Android'in Türkçe TTS'i konuşur.
 */
class Voice(private val ctx: Context, private val prefs: SesAyarlari) {
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    @Volatile private var antalia: Antalia? = null
    @Volatile private var loading = false
    @Volatile private var generation = 0
    @Volatile var speaking = false
        private set
    var onStatus: (String) -> Unit = {}

    private val dir = File(ctx.filesDir, "antalia").apply { mkdirs() }
    private val cacheDir = File(ctx.cacheDir, "tts").apply { mkdirs() }

    init {
        tts = TextToSpeech(ctx) { st ->
            ttsReady = st == TextToSpeech.SUCCESS
            if (ttsReady) { tts?.language = Locale("tr", "TR"); applyAndroidVoice() }
        }
        // yükleme (~15 MB ayrıştırma) arka planda; hazır olana kadar Android sesi konuşur
        if (prefs.voiceEngine == SesAyarlari.ENGINE_ANTALIA && modelFilesPresent()) ensureAntalia()
    }

    fun modelFilesPresent() = FILES.all { (n, _) -> File(dir, n).exists() }

    /** Konuş, bitince done() ana iş parçacığında çağrılır. */
    fun speak(text: String, done: () -> Unit = {}) {
        stop()
        // başka uygulama çalıyorsa (odak henüz bizde değil) konuşmadan önce kısa bekleme: video önce dursun
        val lead = if (!focusHeld && am.isMusicActive) { othersSeen = SystemClock.elapsedRealtime(); LEAD_MS } else 0L
        val gen = ++generation
        speaking = true
        val finish: () -> Unit = {
            main.post {
                if (gen == generation) {
                    speaking = false
                    releaseFocusLater()
                    done()
                }
            }
        }
        val engine = antalia
        if (prefs.voiceEngine == SesAyarlari.ENGINE_ANTALIA && engine != null) {
            // odak (videonun durması) ses üretilip çalmaya hazır olunca istenir: üretim sürerken video boşuna durmaz
            worker.execute {
                try {
                    playAntalia(engine, text, gen, lead)
                } catch (e: Exception) {
                    main.post { onStatus("Antalia hatası: ${e.message}") }
                }
                finish()
            }
        } else {
            if (prefs.voiceEngine == SesAyarlari.ENGINE_ANTALIA && !loading) ensureAntalia()
            requestFocus()
            main.postDelayed({ if (gen == generation) speakAndroid(text, finish) }, lead)
        }
    }

    /** Ayarlarda örnek: [android] ise seçili motordan bağımsız Android sesiyle okunur. */
    fun sample(text: String, android: Boolean) {
        if (!android) { speak(text); return }
        stop()
        val gen = ++generation
        speaking = true
        requestFocus()
        speakAndroid(text) { main.post { if (gen == generation) { speaking = false; releaseFocusLater() } } }
    }

    fun stop() {
        generation++
        speaking = false
        tts?.stop()
        track?.let { runCatching { it.pause(); it.flush() } }
        releaseFocusLater() // ardından speak gelirse odak bırakılmadan sürer
    }

    // ------------------------------------------------------------------ Android TTS
    /** Android motorunun Türkçe sesleri (adına göre sıralı); motor hazır değilse boş. */
    fun androidVoices(): List<android.speech.tts.Voice> {
        val t = tts ?: return emptyList()
        if (!ttsReady) return emptyList()
        return runCatching { t.voices.orEmpty() }.getOrDefault(emptySet())
            .filter { it.locale.language == "tr" && !it.features.orEmpty().contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) }
            .sortedWith(compareBy({ it.isNetworkConnectionRequired }, { it.name }))
    }

    /** Seçilen ses, hız ve perde (ayar değişince ve motor açılınca). */
    fun applyAndroidVoice() {
        val t = tts ?: return
        if (!ttsReady) return
        val name = prefs.androidVoice
        val v = if (name.isEmpty()) null else androidVoices().firstOrNull { it.name == name }
        if (v != null) runCatching { t.voice = v } else t.language = Locale("tr", "TR")
        t.setSpeechRate(prefs.ttsRate / 100f)
        t.setPitch(prefs.ttsPitch / 100f)
    }

    /** Ses düzeyi ayarı (Prefs.voiceVolume) → konuşmanın kazancı. */
    private fun gain() = VOLUMES[prefs.voiceVolume.coerceIn(0, 2)]

    private fun speakAndroid(text: String, finish: () -> Unit) {
        val t = tts
        if (t == null || !ttsReady) {
            finish(); return
        }
        val id = "u${System.nanoTime()}"
        t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) { if (utteranceId == id) finish() }
            @Deprecated("eski API")
            override fun onError(utteranceId: String?) { if (utteranceId == id) finish() }
        })
        // kısa sessizlik: ses yolu uyanırken ilk kelime yutulmasın
        t.playSilentUtterance(LEAD_IN_MS, TextToSpeech.QUEUE_FLUSH, null)
        t.speak(text, TextToSpeech.QUEUE_ADD, Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, gain()) }, id)
    }

    // ------------------------------------------------------------------ Antalia
    @Volatile private var track: AudioTrack? = null

    private fun audioTrack(): AudioTrack {
        track?.let { return it }
        val min = AudioTrack.getMinBufferSize(Antalia.SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT)
        val t = AudioTrack.Builder()
            .setAudioAttributes(attrs())
            .setAudioFormat(
                AudioFormat.Builder().setSampleRate(Antalia.SAMPLE_RATE).setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build()
            )
            .setBufferSizeInBytes(maxOf(min, Antalia.SAMPLE_RATE * 4 / 2))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        track = t
        return t
    }

    private fun playAntalia(engine: Antalia, text: String, gen: Int, lead: Long) {
        engine.steps = prefs.antaliaSteps
        val key = md5("${engine.steps}|$text")
        val cached = File(cacheDir, key)
        val t = audioTrack()
        t.setVolume(gain())
        t.play()
        val head0 = t.playbackHeadPosition
        var written = 0
        var started = false
        fun write(a: FloatArray) {
            var off = 0
            while (off < a.size && gen == generation) {
                val n = t.write(a, off, minOf(4800, a.size - off), AudioTrack.WRITE_BLOCKING)
                if (n <= 0) break
                off += n
                written += n
            }
        }
        fun speech(a: FloatArray) {
            if (!started && gen == generation) {
                started = true
                main.post { if (gen == generation) requestFocus() }
                if (lead > 0) Thread.sleep(lead) // video önce dursun, sesler üst üste binmesin
                // ses yolu (Bluetooth, araç hoparlörü) ve diğer seslerin kısılması uyanırken ilk hece yutulmasın
                write(FloatArray(LEAD_IN))
            }
            write(a)
        }
        if (cached.exists()) {
            speech(readFloats(cached))
        } else {
            val parts = ArrayList<FloatArray>()
            engine.stream(text) { chunk ->
                if (gen == generation) {
                    parts.add(chunk); speech(chunk)
                }
            }
            if (gen == generation && text.length <= 160) {
                writeFloats(cached, parts)
                trimCache()
            }
        }
        // tampondaki son hecelerin çalınmasını bekle (yarım saniyelik tampon pause ile atılırsa son kelime kesilir)
        write(FloatArray(Antalia.SAMPLE_RATE / 10))
        val until = SystemClock.elapsedRealtime() + 3000
        while (gen == generation && t.playbackHeadPosition - head0 < written && SystemClock.elapsedRealtime() < until) Thread.sleep(20)
        t.pause()
        t.flush()
    }

    /**
     * Birazdan söylenecek cümleleri boştayken önceden sentezler (Antalia telefonda yavaş: talimat geç kalmasın).
     * Konuşma başlarsa sıradaki cümleye geçmeden bırakır.
     */
    fun prefetch(texts: List<String>) {
        val engine = antalia ?: return
        if (prefs.voiceEngine != SesAyarlari.ENGINE_ANTALIA || texts.isEmpty()) return
        worker.execute {
            engine.steps = prefs.antaliaSteps
            for (text in texts) {
                if (speaking) return@execute
                if (text.length > 160) continue
                val f = File(cacheDir, md5("${engine.steps}|$text"))
                if (f.exists()) { f.setLastModified(System.currentTimeMillis()); continue }
                val parts = ArrayList<FloatArray>()
                if (runCatching { engine.stream(text) { parts.add(it) } }.isSuccess) { writeFloats(f, parts); trimCache() }
            }
        }
    }

    /** Ağırlıkları indir (SHA-256 doğrulamalı) ve modeli yükle. */
    fun ensureAntalia(onReady: () -> Unit = {}) {
        if (antalia != null) { onReady(); return }
        if (loading) return
        loading = true
        worker.execute {
            try {
                for ((name, sha) in FILES) {
                    val f = File(dir, name)
                    if (f.exists() && sha256(f) == sha) continue
                    main.post { onStatus("Antalia sesi indiriliyor: $name") }
                    download("https://huggingface.co/cloud0day3/antalia-mini/resolve/main/$name", f)
                    if (sha256(f) != sha) {
                        f.delete(); throw IllegalStateException("$name doğrulanamadı")
                    }
                }
                loadAntalia()
                main.post { onStatus("Antalia sesi hazır"); onReady() }
            } catch (e: Exception) {
                main.post { onStatus("Antalia indirilemedi: ${e.message}") }
            } finally {
                loading = false
            }
        }
    }

    private fun loadAntalia() {
        if (antalia != null) return
        val threads = Runtime.getRuntime().availableProcessors().coerceIn(2, 4)
        val model = AntaliaModel(
            SafeTensors.read(File(dir, FILES[0].first).readBytes()),
            SafeTensors.read(File(dir, FILES[1].first).readBytes()),
            threads,
        )
        antalia = Antalia(model)
    }

    private fun download(url: String, out: File) {
        val tmp = File(out.path + ".part")
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 20_000
        c.readTimeout = 60_000
        c.instanceFollowRedirects = true
        try {
            if (c.responseCode !in 200..299) throw IllegalStateException("HTTP ${c.responseCode}")
            c.inputStream.use { inp -> tmp.outputStream().use { inp.copyTo(it, 1 shl 16) } }
        } finally {
            c.disconnect()
        }
        if (!tmp.renameTo(out)) throw IllegalStateException("dosya yazılamadı")
    }

    private fun trimCache() {
        val files = cacheDir.listFiles()?.sortedByDescending { it.lastModified() } ?: return
        files.drop(80).forEach { it.delete() }
    }

    private fun readFloats(f: File): FloatArray {
        val bb = ByteBuffer.wrap(f.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
        return FloatArray(bb.remaining() / 4) { bb.getFloat() }
    }

    private fun writeFloats(f: File, parts: List<FloatArray>) {
        val n = parts.sumOf { it.size }
        val bb = ByteBuffer.allocate(n * 4).order(ByteOrder.LITTLE_ENDIAN)
        parts.forEach { p -> p.forEach { bb.putFloat(it) } }
        f.writeBytes(bb.array())
    }

    // ------------------------------------------------------------------ ses odağı (Google Maps sesi kısılsın)
    // Video/müzik çalan uygulama odak gidince durur, dönünce sürer. Art arda cümleler arasında odak bırakılmaz
    // (video dur-kalk yapmaz); son cümleden [HOLD_MS] sonra bırakılır.
    private var focus: AudioFocusRequest? = null
    @Volatile private var focusHeld = false
    private val release = Runnable { abandonFocus() }
    private var othersSeen = 0L
    private var othersChecked = 0L

    /**
     * Video/müzik açık mı: son [OTHERS_WINDOW_MS] içinde başka uygulama çalarken görüldüyse evet. Odak bizdeyken
     * video durmuş olur; bu yüzden son görülen durum geçerlidir. Açıkken yönlendirme yalnızca önemli sesleri söyler.
     */
    fun othersPlaying(): Boolean {
        val now = SystemClock.elapsedRealtime()
        // konum başına birkaç kez sorulur: sistem çağrısı saniyede en çok bir kez
        if (now - othersChecked >= 1000) {
            othersChecked = now
            if (!focusHeld && !speaking && am.isMusicActive) othersSeen = now
        }
        return othersSeen != 0L && now - othersSeen < OTHERS_WINDOW_MS
    }

    private fun releaseFocusLater() {
        main.removeCallbacks(release)
        if (focusHeld) main.postDelayed(release, HOLD_MS)
    }

    private fun attrs() = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()

    @Suppress("DEPRECATION")
    private fun requestFocus() {
        main.removeCallbacks(release)
        if (focusHeld) return
        focusHeld = true
        if (Build.VERSION.SDK_INT >= 26) {
            val r = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK).setAudioAttributes(attrs()).build()
            focus = r
            am.requestAudioFocus(r)
        } else {
            am.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        }
    }

    @Suppress("DEPRECATION")
    private fun abandonFocus() {
        if (!focusHeld) return
        focusHeld = false
        if (Build.VERSION.SDK_INT >= 26) focus?.let { am.abandonAudioFocusRequest(it) } else am.abandonAudioFocus(null)
    }

    /** Bellek sıkışınca Antalia ağırlıklarını bırak; bir sonraki konuşmada Android sesi konuşur ve model arka planda yeniden yüklenir. */
    fun trimMemory() {
        if (!speaking && !loading) antalia = null
    }

    fun shutdown() {
        stop()
        main.removeCallbacks(release)
        abandonFocus()
        tts?.shutdown()
        worker.shutdownNow()
        track?.release()
    }

    companion object {
        /** Ses düzeyi: düşük, orta, yüksek. */
        private val VOLUMES = floatArrayOf(0.45f, 0.75f, 1f)
        /** Art arda cümleler arasında odak tutulur; son cümleden bu kadar sonra video sürer. */
        private const val HOLD_MS = 1500L
        /** Video durması için konuşmadan önceki bekleme. */
        private const val LEAD_MS = 350L
        /** Konuşma öncesi sessizlik (ms / örnek). */
        private const val LEAD_IN_MS = 200L
        private const val LEAD_IN = (Antalia.SAMPLE_RATE * LEAD_IN_MS / 1000).toInt()
        /** Video bu süre içinde çalarken görüldüyse açık sayılır (odak bizdeyken durmuş görünür). */
        private const val OTHERS_WINDOW_MS = 30_000L

        /** config.json'daki SHA-256 değerleri (sürüm 1.0.1). */
        val FILES = listOf(
            "acoustic.fp16.safetensors" to "c94d5cbe11df77dbc293a85782c28eac377a5f9b8027ca2270cc9b2b51f35d87",
            "vocoder.fp16.safetensors" to "a5ba4b559daccf93c0ac888d50a198c810ab95554b3be6df937dac690d6fbcee",
        )

        fun sha256(f: File): String {
            val md = MessageDigest.getInstance("SHA-256")
            f.inputStream().use { inp ->
                val buf = ByteArray(1 shl 16)
                while (true) {
                    val n = inp.read(buf); if (n < 0) break; md.update(buf, 0, n)
                }
            }
            return md.digest().joinToString("") { "%02x".format(it) }
        }

        private fun md5(s: String) = MessageDigest.getInstance("MD5").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}

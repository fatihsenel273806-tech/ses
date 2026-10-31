package com.ses.asistan.giris

import android.content.Context
import android.util.Log
import com.ses.asistan.ses.SesDurumu
import com.ses.asistan.ses.Voice
import com.ses.asistan.ses.WakeGrammar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import org.stypox.dicio.io.wake.WakeDevice
import org.stypox.dicio.io.wake.WakeState
import org.stypox.dicio.ui.util.Progress
import org.stypox.dicio.util.FileToDownload
import org.stypox.dicio.util.downloadBinaryFilesWithPartial
import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.io.IOException
import java.util.zip.ZipInputStream

/**
 * "Hey Asistan", "Hey Harita", "Hey Notlar", "Hey Kanallar" (PLAN D4): Vosk'un küçük Türkçe modeli, yalnız
 * [WakeGrammar] dilbilgisiyle. Harita `WakeWord`'den taşındı; mikrofonu Dicio'nun [org.stypox.dicio.io.wake.WakeService]
 * döngüsü `AudioRecord` ile okur (ses odağı almaz, video durmaz, bip yok), kareler [processFrame]'e gelir.
 *
 * Hangi adın duyulduğu [SesDurumu.sonUyanmaAdi]'na yazılır (Dicio arayüzü yalnız "duyuldu" der; yönlendirme Faz 2).
 * Asistan konuşurken duyulan atılır ([SesDurumu.konusuyor], Harita `WakeWord.deaf`).
 */
class VoskWakeDevice(
    private val appContext: Context,
    private val okHttpClient: OkHttpClient,
) : WakeDevice {
    private val dir = modelDir(appContext)
    private val zip = FileToDownload(MODEL_URL, File(appContext.cacheDir, "vosk-tr.zip"))
    private val _state = MutableStateFlow<WakeState>(if (hazir(appContext)) WakeState.NotLoaded else WakeState.NotDownloaded)
    override val state: StateFlow<WakeState> = _state
    private val scope = CoroutineScope(Dispatchers.IO)

    private var model: Model? = null
    private var rec: Recognizer? = null
    private val decide = WakeGrammar.Decider()
    private var wasDeaf = false

    override fun download() {
        _state.value = WakeState.Downloading(Progress.UNKNOWN)
        scope.launch {
            try {
                downloadBinaryFilesWithPartial(listOf(zip), okHttpClient, appContext.cacheDir) {
                    _state.value = WakeState.Downloading(it)
                }
                kur()
                _state.value = WakeState.NotLoaded
            } catch (e: Throwable) {
                Log.e(TAG, "Vosk uyanma modeli indirilemedi", e)
                _state.value = WakeState.ErrorDownloading(e)
            } finally {
                zip.file.delete()
                zip.lastDownloadedUrlFile.delete()
            }
        }
    }

    /** Arşivi SHA-256 ile doğrular, açar (önce geçici klasöre: yarım kalan kurulum hazır sanılmaz). Harita WakeWord.install. */
    private fun kur() {
        if (Voice.sha256(zip.file) != MODEL_SHA256) throw IOException("model doğrulanamadı")
        val tmp = File(appContext.filesDir, "vosk-tr.part").apply { deleteRecursively(); mkdirs() }
        ZipInputStream(zip.file.inputStream().buffered()).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                // üst klasör (vosk-model-small-tr-0.3/) atlanır; arşiv dışına yazma (../) engellenir
                val rel = e.name.substringAfter('/', "")
                if (rel.isEmpty() || rel.contains("..")) continue
                val out = File(tmp, rel)
                if (e.isDirectory) out.mkdirs() else { out.parentFile?.mkdirs(); out.outputStream().use { z.copyTo(it, 1 shl 16) } }
            }
        }
        dir.deleteRecursively()
        if (!tmp.renameTo(dir)) throw IOException("model yazılamadı")
    }

    override fun processFrame(audio16bitPcm: ShortArray): Boolean {
        val r = rec ?: yukle()
        if (SesDurumu.konusuyor) { wasDeaf = true; return false }
        if (wasDeaf) { wasDeaf = false; r.reset(); decide.reset() } // kendi sesimizin yarısı sonuca karışmasın
        val heard = if (r.acceptWaveForm(audio16bitPcm, audio16bitPcm.size)) decide.feed(r.result, final = true)
        else decide.feed(r.partialResult, final = false)
        if (!heard) return false
        SesDurumu.sonUyanmaAdi = decide.name
        r.reset()
        decide.reset()
        return true
    }

    private fun yukle(): Recognizer {
        if (_state.value.let { it != WakeState.NotLoaded && it !is WakeState.ErrorLoading && it != WakeState.Loaded }) {
            throw IOException("Vosk uyanma modeli henüz inmedi")
        }
        try {
            _state.value = WakeState.Loading
            LibVosk.setLogLevel(LogLevel.WARNINGS)
            val m = model ?: Model(dir.path).also { model = it }
            val r = Recognizer(m, RATE.toFloat(), WakeGrammar.GRAMMAR)
            r.setWords(true)
            r.setPartialWords(true)
            rec = r
            _state.value = WakeState.Loaded
            return r
        } catch (t: Throwable) {
            Log.e(TAG, "Vosk uyanma modeli açılamadı", t)
            _state.value = WakeState.ErrorLoading(t)
            throw t
        }
    }

    override fun frameSize(): Int = CHUNK

    override fun isOccupyingResources(): Boolean = model != null

    override fun destroy() {
        rec?.close(); rec = null
        model?.close(); model = null
        scope.cancel()
    }

    /** Bildirimde "Hey Dicio" yazmasın (özel söz metni asistanın kaynaklarında "Hey Asistan"a çevrildi). */
    override fun isHeyDicio(): Boolean = false

    companion object {
        private val TAG = VoskWakeDevice::class.simpleName
        private const val RATE = 16000
        /** 100 ms: kısmi sonuç sıklığı ve tepki süresi (Harita WakeWord.CHUNK). */
        private const val CHUNK = RATE / 10
        private const val MODEL_URL = "https://alphacephei.com/vosk/models/vosk-model-small-tr-0.3.zip"
        private const val MODEL_SHA256 = "8c8d07cec1bce31add14967c16891c84152cdf76d391e33c08278119b9ea96e5"

        private fun modelDir(ctx: Context) = File(ctx.filesDir, "vosk-tr")
        fun hazir(ctx: Context) = File(modelDir(ctx), "final.mdl").exists()
    }
}

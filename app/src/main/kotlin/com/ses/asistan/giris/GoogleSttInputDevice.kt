package com.ses.asistan.giris

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.SpeechRecognizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.stypox.dicio.io.input.InputEvent
import org.stypox.dicio.io.input.SttInputDevice
import org.stypox.dicio.io.input.SttState

/**
 * Dicio'nun konuşma girişi arayüzü → Google tanıyıcısı ([Kulak]). Açık metinde Türkçe doğruluğu yüksek (HAFIZA:
 * Vosk küçük model yer adı, not gibi açık uçlu metinde zayıf); Dicio'nun Vosk girişi çevrimdışı yedek olarak kalır.
 * Dicio bunu herhangi bir iş parçacığından çağırabilir (WakeService); tanıyıcı ana iş parçacığında açılır.
 */
class GoogleSttInputDevice(ctx: Context) : SttInputDevice {
    private val app = ctx.applicationContext
    private val main = Handler(Looper.getMainLooper())

    private val _uiState = MutableStateFlow<SttState>(
        if (SpeechRecognizer.isRecognitionAvailable(app)) SttState.Loaded else SttState.NotAvailable
    )
    override val uiState: StateFlow<SttState> = _uiState

    private fun ana(f: () -> Unit) { if (Looper.myLooper() === main.looper) f() else main.post(f) }

    override fun tryLoad(thenStartListeningEventListener: ((InputEvent) -> Unit)?): Boolean {
        if (_uiState.value == SttState.NotAvailable) return false
        if (thenStartListeningEventListener != null) ana { dinle(thenStartListeningEventListener) }
        return true
    }

    override fun onClick(eventListener: (InputEvent) -> Unit) {
        ana {
            val kulak = Kulak.al(app)
            if (kulak.dinliyor) kulak.bitir() else tryLoad(eventListener)
        }
    }

    override fun stopListening() {
        ana { Kulak.al(app).bitir() }
    }

    private fun dinle(listener: (InputEvent) -> Unit) {
        val kulak = Kulak.al(app)
        kulak.komut { olay ->
            when (olay) {
                is Kulak.Olay.Kismi -> listener(InputEvent.Partial(olay.metin))
                is Kulak.Olay.Sonuc -> { _uiState.value = SttState.Loaded; listener(InputEvent.Final(olay.secenekler)) }
                Kulak.Olay.Bos -> { _uiState.value = SttState.Loaded; listener(InputEvent.None) }
                is Kulak.Olay.Hata -> {
                    _uiState.value = SttState.Loaded
                    listener(InputEvent.Error(GoogleTanimaHatasi(olay.kod)))
                }
            }
        }
        // Dicio "dinliyor" durumunda kendi sesini bildirim kanalından çalar: kanal Kulak'ta susturulduktan sonra
        if (kulak.dinliyor) _uiState.value = SttState.Listening
    }

    override suspend fun destroy() {
        ana { Kulak.al(app).iptal() }
    }
}

class GoogleTanimaHatasi(val kod: Int) : Exception("Google konuşma tanıma hatası $kod")

package com.ses.asistan.giris

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioRecord
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AudioEffect
import android.media.audiofx.NoiseSuppressor
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.ses.asistan.kopru.Kopru
import com.ses.asistan.ses.SesAyarlari
import com.ses.asistan.ses.SesDurumu
import com.ses.asistan.ses.UyanmaPolitikasi

/**
 * Dicio'nun uyanma döngüsündeki tek kanca ([org.stypox.dicio.io.wake.WakeService.listenForWakeWord], `// ASISTAN:`):
 * her karede kayıt sürsün mü kararı. Kayıt durur:
 *  - Google tanıyıcısı mikrofonu isteyince ([SesDurumu.tanimaIstiyor]) — yoksa tanıyıcı sessizlik duyar,
 *  - K3 politikası kapalıyken ([UyanmaPolitikasi]: araçta + elle) — pil ve gizlilik işareti,
 *  - telefon görüşmesinde (ses kipi IN_CALL / IN_COMMUNICATION; izin gerektirmez).
 * Servis bu sürede çalışmayı sürdürür; koşul kalkınca kayıt aynı servisten yeniden başlar.
 */
object UyanmaKapisi {
    private const val TAG = "UyanmaKapisi"
    /** Kayıt dururken döngünün bekleme adımı (saniyede 10 uyanma pil harcıyordu; tepki ≤0,5 sn). */
    private const val BEKLE_MS = 500L
    /** Görüşme ve Bluetooth durumunun yeniden okunma aralığı (sistem çağrısı her karede yapılmaz). */
    private const val YOKLA_MS = 1000L

    @Volatile private var am: AudioManager? = null
    private lateinit var ayarlar: SesAyarlari
    @Volatile private var bluetooth: List<String> = emptyList()
    @Volatile private var gorusmede = false
    @Volatile private var ilgiliOnde: String? = null
    @Volatile private var ekranAcik = true
    @Volatile private var calan = false
    private var pm: android.os.PowerManager? = null
    private var yoklandi = 0L

    @Synchronized
    private fun hazirla(ctx: Context) {
        if (am != null) return
        val a = ctx.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        ayarlar = SesAyarlari.al(ctx)
        pm = ctx.applicationContext.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        bluetooth = bluetoothAdlari(a)
        if (Build.VERSION.SDK_INT >= 23) {
            a.registerAudioDeviceCallback(object : AudioDeviceCallback() {
                override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>?) { bluetooth = bluetoothAdlari(a) }
                override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>?) { bluetooth = bluetoothAdlari(a) }
            }, Handler(Looper.getMainLooper()))
        }
        am = a
    }

    private fun bluetoothAdlari(a: AudioManager): List<String> {
        if (Build.VERSION.SDK_INT < 23) return emptyList()
        return a.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .filter { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO }
            .map { it.productName?.toString().orEmpty().ifEmpty { "bluetooth" } }
    }

    /** Uyanma politikasının o anki girdileri (ayar ekranında da gösterilir). */
    fun durum(ctx: Context): UyanmaPolitikasi.Durum {
        hazirla(ctx)
        val a = am!!
        val now = SystemClock.elapsedRealtime()
        if (now - yoklandi >= YOKLA_MS) {
            yoklandi = now
            gorusmede = a.mode == AudioManager.MODE_IN_CALL || a.mode == AudioManager.MODE_IN_COMMUNICATION
            ekranAcik = pm?.isInteractive ?: true
            calan = a.isMusicActive
            // yalnız bu kipte: kullanım verisi sorgusu boşuna yapılmasın
            ilgiliOnde = if (ayarlar.uyanmaKipi == UyanmaPolitikasi.Kip.UYGULAMALAR_ACIKKEN) ilgiliUygulama(ctx) else null
        }
        return UyanmaPolitikasi.Durum(
            kip = ayarlar.uyanmaKipi,
            elleAcik = ayarlar.elleAcik,
            bluetoothAdlari = bluetooth,
            aracAdi = ayarlar.aracAdi,
            navigasyonda = SesDurumu.navigasyonda,
            asistanEkranda = org.stypox.dicio.MainActivity.isInForeground > 0,
            ilgiliOnde = ilgiliOnde,
            uygulamaIstiyor = SesDurumu.uygulamaUyanmaIstiyor,
            gorusmede = gorusmede,
            ekranAcik = ekranAcik,
            sesCaliyor = calan,
        )
    }

    /** Önde ilgili uygulama: önce köprünün "önde" bildirimi (Harita), sonra kullanım erişimi (Kanallarım, Notlar…). */
    private fun ilgiliUygulama(ctx: Context): String? {
        val p = Kopru.ondeOlan() ?: OndekiUygulama.paket(ctx)?.takeIf { it in UyanmaPolitikasi.ILGILI } ?: return null
        return UyanmaPolitikasi.ILGILI[p] ?: p
    }

    fun kaydetmeli(ctx: Context): Boolean = !SesDurumu.tanimaIstiyor && UyanmaPolitikasi.dinlemeli(durum(ctx))

    /**
     * Uyanma döngüsü her kareden önce çağırır. true: bu kare atlanır (kayıt durdurulmuştur, kısa beklendi);
     * false: kayıt sürüyor, kare okunur.
     */
    fun beklet(ctx: Context, ar: AudioRecord): Boolean {
        val kayitta = ar.recordingState == AudioRecord.RECORDSTATE_RECORDING
        if (!kaydetmeli(ctx)) {
            if (kayitta) runCatching { ar.stop() }
            SesDurumu.uyanmaKaydiAcik = false
            Thread.sleep(BEKLE_MS)
            return true
        }
        if (!kayitta) {
            val ok = runCatching { ar.startRecording() }.isSuccess && ar.recordingState == AudioRecord.RECORDSTATE_RECORDING
            if (!ok) {
                // mikrofon başka uygulamada (görüşme, kayıt): biraz sonra yeniden denenir
                Log.w(TAG, "uyanma kaydı yeniden başlatılamadı")
                Thread.sleep(YOKLA_MS)
                return true
            }
        }
        SesDurumu.uyanmaKaydiAcik = true
        return false
    }

    @Volatile private var efektler: List<AudioEffect> = emptyList()

    /**
     * Yankı giderici ve gürültü bastırıcı (aygıtta varsa). Ölçüm (HAFIZA, 2026-10-31): arkada video konuşmasıyla "hey
     * asistan" 1/4, "hey notlar" 0/4 duyuluyordu (telefon: "Kanallarım'dayken yalnız Hey Kanallar uyanıyor"). Telefonun
     * kendi hoparlöründen çalan video yankı gidericiyle kayıttan çıkarılır; aygıt desteklemezse hiçbir şey değişmez.
     * Telefonda ölçülecek.
     */
    fun yankiGider(ar: AudioRecord) {
        val id = ar.audioSessionId
        efektler = listOfNotNull(
            if (AcousticEchoCanceler.isAvailable()) runCatching { AcousticEchoCanceler.create(id) }.getOrNull() else null,
            if (NoiseSuppressor.isAvailable()) runCatching { NoiseSuppressor.create(id) }.getOrNull() else null,
        ).onEach { runCatching { it.enabled = true } }
        Log.i(TAG, "uyanma efektleri: ${efektler.joinToString { it.descriptor.name }}")
    }

    /** Döngü bitince (servis durdu): mikrofon artık bizde değil. */
    fun bitti() {
        SesDurumu.uyanmaKaydiAcik = false
        efektler.forEach { runCatching { it.release() } }
        efektler = emptyList()
    }
}

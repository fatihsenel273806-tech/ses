package com.ses.asistan.kopru

import android.Manifest
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.datastore.core.DataStore
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.stypox.dicio.di.WakeDeviceWrapper
import org.stypox.dicio.io.wake.WakeService
import org.stypox.dicio.io.wake.WakeState
import org.stypox.dicio.settings.datastore.UserSettings
import org.stypox.dicio.settings.datastore.WakeDevice
import javax.inject.Inject

/**
 * Asistanın ses servisi (eylem `com.ses.asistan.SES`): bağlı uygulamalar buna bağlanır. Yalnız bilinen sertifikalı
 * uygulamalar bağlanabilir (`com.ses.asistan.izin.SES`, `signature|knownSigner`). Asıl iş [Kopru]'de.
 *
 * Uygulama ekrandayken `BIND_INCLUDE_CAPABILITIES` ile bağlanır: mikrofon yetkisi bağlı servise geçer, asistan hiç
 * açılmadan uyanma dinlemesini başlatır (PLAN §5; telefonda doğrulanır).
 */
@AndroidEntryPoint
class SesServisi : Service() {
    @Inject lateinit var wakeDevice: WakeDeviceWrapper
    @Inject lateinit var dataStore: DataStore<UserSettings>

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        Kopru.hazirla(this)
        Kopru.uyanmayiBaslat = {
            // bağlı uygulama ekrandayken başlatılır; arka plandan yasaksa (Android 12+) sessizce geçilir
            runCatching { WakeService.start(applicationContext) }.onFailure { Log.w(TAG, "uyanma servisi başlatılamadı", it) }
        }
        scope.launch {
            // yalnız bizim Vosk uyanmamız uygulama adlarını duyar ("hey harita"); Dicio'nun "Hey Dicio"su değil
            wakeDevice.state.combine(dataStore.data) { st, ayar ->
                val vosk = ayar.wakeDevice == WakeDevice.WAKE_DEVICE_VOSK || ayar.wakeDevice == WakeDevice.WAKE_DEVICE_UNSET
                vosk && (st == WakeState.NotLoaded || st == WakeState.Loading || st == WakeState.Loaded) &&
                    ContextCompat.checkSelfPermission(this@SesServisi, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            }.distinctUntilChanged().collect { Kopru.uyanmaHazirDegisti(it) }
        }
    }

    override fun onBind(intent: Intent?): IBinder = Kopru.gelen.binder

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val TAG = "SesServisi"
    }
}

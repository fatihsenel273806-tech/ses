package com.ses.asistan.ek

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.view.Display
import androidx.annotation.RequiresApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import kotlin.coroutines.resume

/**
 * Ekran görüntüsü ([EkranServisi] ile). Asistanın kendi ekranı öndeyse ("Hey Asistan" ile uyanıldı) önce geri
 * düğmesiyle arkaya alınır: görüntü kullanıcının baktığı uygulamanın olsun. Görüntü JPEG olarak önbellekte
 * (`cache/ekran/`) durur; Notlar'a FileProvider adresiyle verilir ([com.ses.asistan.beceri.Notlar.yaz]).
 */
object Ekran {
    sealed interface Sonuc {
        class Tamam(val dosya: File) : Sonuc
        /** [neden] kullanıcıya söylenir. */
        class Hata(val neden: String) : Sonuc
    }

    const val KLASOR = "ekran"
    private const val KALITE = 85
    private const val SURE_MS = 5_000L
    /** Önbellekte en çok bu kadar görüntü kalır (Notlar kendi kopyasını alır). */
    private const val SAKLA = 5

    suspend fun al(ctx: Context): Sonuc {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return Sonuc.Hata("Ekran görüntüsü için Android 11 gerekiyor.")
        val s = EkranServisi.tek
            ?: return Sonuc.Hata("Ekran görüntüsü için Ayarlar, Erişilebilirlik, Asistan'ı açın.")
        EkranServisi.asistaniGizle()
        val bmp = withTimeoutOrNull(SURE_MS) { cek(s) } ?: return Sonuc.Hata("Ekran görüntüsü alınamadı.")
        return withContext(Dispatchers.IO) {
            val dir = File(ctx.cacheDir, KLASOR).apply { mkdirs() }
            val f = File(dir, "ekran_${System.currentTimeMillis()}.jpg")
            runCatching { f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, KALITE, it) } }
            bmp.recycle()
            dir.listFiles()?.sortedByDescending { it.lastModified() }?.drop(SAKLA)?.forEach { it.delete() }
            if (f.length() > 0) Sonuc.Tamam(f) else Sonuc.Hata("Ekran görüntüsü kaydedilemedi.")
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private suspend fun cek(s: AccessibilityService): Bitmap? = suspendCancellableCoroutine { k ->
        s.takeScreenshot(Display.DEFAULT_DISPLAY, s.mainExecutor, object : AccessibilityService.TakeScreenshotCallback {
            override fun onSuccess(r: AccessibilityService.ScreenshotResult) {
                val hw = r.hardwareBuffer
                // donanım bitmap'i JPEG'e doğrudan yazılamaz: yazılımsal kopya
                val b = runCatching { Bitmap.wrapHardwareBuffer(hw, r.colorSpace)?.copy(Bitmap.Config.ARGB_8888, false) }.getOrNull()
                hw.close()
                if (k.isActive) k.resume(b)
            }

            override fun onFailure(errorCode: Int) { if (k.isActive) k.resume(null) }
        })
    }
}

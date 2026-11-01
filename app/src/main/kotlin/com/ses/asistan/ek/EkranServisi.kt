package com.ses.asistan.ek

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent

/**
 * Erişilebilirlik servisi (2026-11-01): ekran görüntüsü ("SS alabilmeli nota ekleyebilmeli"; MediaProjection her seferinde
 * izin sorar) ve ekran ajanı ("ilgili uygulamalarda A'dan Z'ye"): öndeki pencereyi okuyup dokunma, yazma, kaydırma
 * ([com.ses.asistan.ajan]). Pencere yalnız bağlı dört uygulamada okunur ve onlara dokunulur (kullanıcı kararı;
 * [com.ses.asistan.ajan.EkranOkuyucu.izinli]). Olay dinlenmez. Kullanıcı Ayarlar › Erişilebilirlik › Asistan'dan bir kez açar.
 */
class EkranServisi : AccessibilityService() {
    override fun onServiceConnected() { tek = this }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
    override fun onDestroy() { if (tek === this) tek = null; super.onDestroy() }
    override fun onUnbind(intent: Intent?): Boolean { if (tek === this) tek = null; return super.onUnbind(intent) }

    companion object {
        /** Bağlı servis; null: erişilebilirlikte kapalı. */
        @Volatile var tek: EkranServisi? = null
            private set

        fun acik() = tek != null

        /** Asistanın kendi ekranı öndeyse ("Hey Asistan") geri düğmesiyle arkaya alınır: iş alttaki uygulamada. */
        suspend fun asistaniGizle() {
            val s = tek ?: return
            if (org.stypox.dicio.MainActivity.isInForeground <= 0) return
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { s.performGlobalAction(GLOBAL_ACTION_BACK) }
            kotlinx.coroutines.delay(GIZLE_MS)
        }

        /** Geri düğmesinden sonra asistanın ekranının kapanma animasyonu. */
        private const val GIZLE_MS = 700L

        /** Sistem erişilebilirlik ekranı (servis orada açılır). */
        fun ayarlariAc(ctx: Context) {
            ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}

package com.ses.asistan.giris

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.Process

/**
 * Önde hangi uygulama var ("İlgili uygulamalar açıkken" kipi). Kanallarım'a dokunulmadığı için onun "öndeyim"
 * bildirimi yok: kullanım erişimiyle (Ayarlar › Özel erişim › Kullanım verilerine erişim) son öne gelen etkinlik
 * okunur. İzin yoksa null; bağlı uygulamalar (Harita) "önde" durumunu köprüden zaten bildirir.
 *
 * Uyanma döngüsü saniyede bir sorar ([UyanmaKapisi]); her sorguda yalnız son sorgudan bu yana olan olaylar okunur.
 */
object OndekiUygulama {
    private var son: String? = null
    private var sonSorgu = 0L

    fun izinVar(ctx: Context): Boolean {
        val ops = ctx.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mod = if (Build.VERSION.SDK_INT >= 29) ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        else @Suppress("DEPRECATION") ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        return mod == AppOpsManager.MODE_ALLOWED
    }

    @Synchronized
    fun paket(ctx: Context): String? {
        if (!izinVar(ctx)) { sonSorgu = 0; son = null; return null }
        val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val simdi = System.currentTimeMillis()
        val bas = if (sonSorgu == 0L) simdi - ILK_PENCERE_MS else sonSorgu
        sonSorgu = simdi
        val olaylar = runCatching { usm.queryEvents(bas, simdi) }.getOrNull() ?: return son
        val e = UsageEvents.Event()
        while (olaylar.getNextEvent(e)) {
            @Suppress("DEPRECATION")
            when (e.eventType) {
                UsageEvents.Event.MOVE_TO_FOREGROUND -> son = e.packageName
                // ekran kapandı ya da uygulama kapandı: önde bilinen bir şey yok
                UsageEvents.Event.MOVE_TO_BACKGROUND -> if (son == e.packageName) son = null
            }
        }
        return son
    }

    private const val ILK_PENCERE_MS = 30 * 60_000L
}

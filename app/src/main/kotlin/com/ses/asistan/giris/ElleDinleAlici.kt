package com.ses.asistan.giris

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.ses.asistan.ses.SesAyarlari
import com.ses.asistan.ses.UyanmaPolitikasi
import org.stypox.dicio.R

/**
 * K3 "elle": uyanma bildirimindeki "Dinle / bırak" düğmesi. Elle dinlemeyi açar ya da kapatır; durum kısa bir
 * yazıyla söylenir (bildirim Dicio'nun, metni değişmez).
 */
class ElleDinleAlici : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val a = SesAyarlari.al(context)
        a.elleAcik = !a.elleAcik
        val metin = when {
            a.elleAcik -> "“Hey Asistan” dinleniyor"
            UyanmaPolitikasi.dinlemeli(UyanmaKapisi.durum(context)) -> "Elle dinleme kapandı (araçta dinleme sürüyor)"
            else -> "“Hey Asistan” dinlenmiyor"
        }
        Toast.makeText(context, metin, Toast.LENGTH_SHORT).show()
    }

    companion object {
        /** WakeService bildirimine düğme ekler (`// ASISTAN:` kancası). */
        fun dugmeEkle(context: Context, b: NotificationCompat.Builder): NotificationCompat.Builder = b.addAction(
            NotificationCompat.Action(
                R.drawable.ic_hearing_white,
                "Dinle / bırak",
                PendingIntent.getBroadcast(
                    context, 0, Intent(context, ElleDinleAlici::class.java), PendingIntent.FLAG_IMMUTABLE,
                ),
            )
        )
    }
}

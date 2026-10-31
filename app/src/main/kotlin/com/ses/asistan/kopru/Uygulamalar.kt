package com.ses.asistan.kopru

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.XmlResourceParser
import android.util.Log
import com.ses.asistan.yonlendirme.Yonlendirme
import org.xmlpull.v1.XmlPullParser

/**
 * Köprüsü olan uygulamaları bulur (PROTOKOL.md "Keşif"): `com.ses.asistan.KOPRU` eylemli servis ve onun
 * `com.ses.asistan.yetenekler` meta-data'sındaki `ses_yetenekler.xml`. Sonuç paketin güncellenme anına göre önbellekte
 * tutulur: uygulama kurulunca ya da güncellenince kendiliğinden yeniden okunur. Ana iş parçacığında çağrılmaz.
 */
object Uygulamalar {
    private const val TAG = "Uygulamalar"

    /** [komutlar]: Faz 6'da Gemini araç bildirimi olur (ad → Türkçe açıklama). */
    data class Yetenek(
        val paket: String,
        val servis: ComponentName,
        val uyanma: String?,
        val devir: Yonlendirme.Devir,
        val komutlar: Map<String, String> = emptyMap(),
    )

    private class Kayit(val guncellendi: Long, val yetenek: Yetenek?)
    private val onbellek = HashMap<String, Kayit>()

    @Synchronized
    fun hepsi(ctx: Context): List<Yetenek> {
        val pm = ctx.packageManager
        val bulunan = runCatching {
            @Suppress("DEPRECATION")
            pm.queryIntentServices(Intent(Protokol.EYLEM_KOPRU), PackageManager.GET_META_DATA)
        }.getOrDefault(emptyList())
        val out = ArrayList<Yetenek>()
        for (ri in bulunan) {
            val si = ri.serviceInfo ?: continue
            if (si.packageName == ctx.packageName) continue
            val guncel = runCatching { pm.getPackageInfo(si.packageName, 0).lastUpdateTime }.getOrDefault(0L)
            val k = onbellek[si.packageName]?.takeIf { it.guncellendi == guncel }
                ?: Kayit(guncel, oku(ctx, si)).also { onbellek[si.packageName] = it }
            k.yetenek?.let { out += it }
        }
        return out
    }

    fun bul(ctx: Context, paket: String) = hepsi(ctx).firstOrNull { it.paket == paket }

    private fun oku(ctx: Context, si: android.content.pm.ServiceInfo): Yetenek? {
        val servis = ComponentName(si.packageName, si.name)
        val xml: XmlResourceParser = si.loadXmlMetaData(ctx.packageManager, Protokol.META_YETENEKLER)
            ?: return Yetenek(si.packageName, servis, null, Yonlendirme.Devir.HAYIR)
        return try {
            var uyanma: String? = null
            var devir = Yonlendirme.Devir.HAYIR
            val komutlar = LinkedHashMap<String, String>()
            while (xml.next() != XmlPullParser.END_DOCUMENT) {
                if (xml.eventType != XmlPullParser.START_TAG) continue
                when (xml.name) {
                    "asistan" -> {
                        uyanma = xml.getAttributeValue(null, "uyanma")?.trim()?.lowercase(java.util.Locale("tr"))?.ifEmpty { null }
                        devir = Yonlendirme.Devir.oku(xml.getAttributeValue(null, "devir"))
                    }
                    "komut" -> xml.getAttributeValue(null, "ad")?.let { komutlar[it] = xml.getAttributeValue(null, "aciklama").orEmpty() }
                }
            }
            Yetenek(si.packageName, servis, uyanma, devir, komutlar)
        } catch (e: Exception) {
            Log.w(TAG, "${si.packageName} ses_yetenekler okunamadı", e)
            null
        } finally {
            xml.close()
        }
    }

    /**
     * İleti gönderen uid'in paketi. Ses servisine bağlanabilen zaten izinlidir (`knownSigner`); paylaşılan uid'de köprüsü
     * olan paket seçilir.
     */
    fun paketi(ctx: Context, uid: Int): String? {
        val pkgs = ctx.packageManager.getPackagesForUid(uid) ?: return null
        val bilinen = hepsi(ctx).map { it.paket }.toSet()
        return pkgs.firstOrNull { it in bilinen } ?: pkgs.firstOrNull()
    }
}

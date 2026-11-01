package com.ses.asistan.ajan

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import com.ses.asistan.ses.UyanmaPolitikasi
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Öndeki pencerenin öğeleri ([EkranMetni.Dugum]) ve onlara eylem (dokun, yaz, kaydır). Yalnız bağlı dört uygulamada
 * ve asistanın kendisinde okunur (kullanıcı kararı 2026-11-01: başka uygulamanın ekranı okunmaz, dokunulmaz).
 */
class EkranOkuyucu private constructor(
    val paket: String,
    val dugumler: List<EkranMetni.Dugum>,
    private val kaynak: List<AccessibilityNodeInfo>,
) {
    val uygulama: String get() = UyanmaPolitikasi.ILGILI[paket] ?: "Asistan"

    fun metin(): String = EkranMetni.yaz(uygulama, dugumler)

    fun dugum(no: Int): AccessibilityNodeInfo? = kaynak.getOrNull(no)

    /** Öğenin etiketi (onay sorusunda okunur). */
    fun etiket(no: Int): String = dugumler.getOrNull(no)?.metin.orEmpty()

    companion object {
        /** Ağaçta gezilen en çok düğüm (çok büyük listelerde döngü uzamasın). */
        private const val MAKS_DUGUM = 600

        fun izinli(paket: String?, kendi: String) = paket != null && (paket in UyanmaPolitikasi.ILGILI || paket == kendi)

        /** null: pencere yok ya da izinli uygulama değil. */
        fun oku(s: AccessibilityService): EkranOkuyucu? {
            val kok = s.rootInActiveWindow ?: return null
            val paket = kok.packageName?.toString()
            if (!izinli(paket, s.packageName)) return null
            val dugumler = ArrayList<EkranMetni.Dugum>()
            val kaynak = ArrayList<AccessibilityNodeInfo>()
            val yigin = ArrayDeque<AccessibilityNodeInfo>().apply { add(kok) }
            var gezilen = 0
            while (yigin.isNotEmpty() && gezilen < MAKS_DUGUM && dugumler.size < EkranMetni.MAKS_SATIR) {
                val n = yigin.removeFirst()
                gezilen++
                if (!n.isVisibleToUser) continue
                val etiket = etiket(n)
                val yazilir = n.isEditable
                val tiklanir = n.isClickable || n.isLongClickable
                val kaydirilir = n.isScrollable
                if (etiket.isNotBlank() || tiklanir || yazilir || kaydirilir) {
                    // tıklanır öğe kendi yazısızsa çocuklarının yazısıyla anılır
                    val ad = if (etiket.isBlank() && tiklanir) cocukYazisi(n) else etiket
                    if (ad.isNotBlank() || yazilir || kaydirilir) {
                        dugumler += EkranMetni.Dugum(
                            dugumler.size, EkranMetni.tur(n.className?.toString()), ad,
                            tiklanir, yazilir, kaydirilir, n.isChecked || n.isSelected,
                        )
                        kaynak += n
                    }
                }
                for (i in 0 until n.childCount) n.getChild(i)?.let { yigin.add(it) }
            }
            return EkranOkuyucu(paket!!, dugumler, kaynak)
        }

        private fun etiket(n: AccessibilityNodeInfo): String {
            val t = n.text?.toString().orEmpty().trim()
            val d = n.contentDescription?.toString().orEmpty().trim()
            val ipucu = if (Build.VERSION.SDK_INT >= 26) n.hintText?.toString().orEmpty().trim() else ""
            return listOf(t, d, ipucu).filter { it.isNotEmpty() }.distinct().joinToString(" · ")
        }

        private fun cocukYazisi(n: AccessibilityNodeInfo): String {
            val parcalar = ArrayList<String>()
            val yigin = ArrayDeque<AccessibilityNodeInfo>().apply { for (i in 0 until n.childCount) n.getChild(i)?.let { add(it) } }
            var gezilen = 0
            while (yigin.isNotEmpty() && parcalar.size < 3 && gezilen++ < 30) {
                val c = yigin.removeFirst()
                etiket(c).takeIf { it.isNotBlank() }?.let { parcalar += it }
                for (i in 0 until c.childCount) c.getChild(i)?.let { yigin.add(it) }
            }
            return parcalar.joinToString(" ")
        }
    }
}

/** Erişilebilirlik eylemleri; olmazsa dokunma hareketi. */
object Eylem {
    suspend fun dokun(s: AccessibilityService, n: AccessibilityNodeInfo): Boolean {
        var t: AccessibilityNodeInfo? = n
        while (t != null && !t.isClickable) t = t.parent
        if (t != null && t.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
        val r = Rect().also { n.getBoundsInScreen(it) }
        return hareket(s, r.exactCenterX(), r.exactCenterY(), r.exactCenterX(), r.exactCenterY(), 60)
    }

    fun yaz(n: AccessibilityNodeInfo, metin: String): Boolean {
        n.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val b = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, metin) }
        return n.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, b)
    }

    /** [yon]: asagi, yukari, sola, saga. [n] yoksa ekranın ilk kaydırılır öğesi, o da yoksa ekranda kaydırma hareketi. */
    suspend fun kaydir(s: AccessibilityService, e: EkranOkuyucu?, n: AccessibilityNodeInfo?, yon: String): Boolean {
        val ileri = yon == "asagi" || yon == "saga"
        val hedef = n ?: e?.let { o -> o.dugumler.firstOrNull { it.kaydirilir }?.let { o.dugum(it.no) } }
        val eylem = if (ileri) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        if (hedef?.performAction(eylem) == true) return true
        val dm = s.resources.displayMetrics
        val w = dm.widthPixels.toFloat()
        val h = dm.heightPixels.toFloat()
        return when (yon) {
            "asagi" -> hareket(s, w / 2, h * 0.75f, w / 2, h * 0.3f, 300)
            "yukari" -> hareket(s, w / 2, h * 0.3f, w / 2, h * 0.75f, 300)
            "saga" -> hareket(s, w * 0.8f, h / 2, w * 0.2f, h / 2, 300)
            else -> hareket(s, w * 0.2f, h / 2, w * 0.8f, h / 2, 300)
        }
    }

    private suspend fun hareket(s: AccessibilityService, x1: Float, y1: Float, x2: Float, y2: Float, ms: Long): Boolean {
        if (Build.VERSION.SDK_INT < 24) return false
        val yol = Path().apply { moveTo(x1, y1); lineTo(x2, y2) }
        val g = GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(yol, 0, ms)).build()
        return suspendCancellableCoroutine { k ->
            val gitti = s.dispatchGesture(g, object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(d: GestureDescription?) { if (k.isActive) k.resume(true) }
                override fun onCancelled(d: GestureDescription?) { if (k.isActive) k.resume(false) }
            }, null)
            if (!gitti && k.isActive) k.resume(false)
        }
    }
}

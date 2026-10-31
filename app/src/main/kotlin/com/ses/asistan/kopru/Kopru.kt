package com.ses.asistan.kopru

import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.util.Log
import com.ses.asistan.giris.Kulak
import com.ses.asistan.ses.Ses
import com.ses.asistan.ses.SesDurumu
import com.ses.asistan.ses.SpeechQueue
import com.ses.asistan.yonlendirme.Yonlendirme
import java.util.concurrent.ConcurrentHashMap

/**
 * Bağlı uygulamalarla konuşan taraf (PROTOKOL.md). Gelen iletiler kendi iş parçacığında işlenir (paket adı, XML
 * okuma ana iş parçacığında yapılmaz); ses ve mikrofon işleri ana iş parçacığına geçer ([Ses], [Kulak]).
 *
 *  - Uygulama `MERHABA` ile kaydolur ([SesServisi]'ne bağlanarak); bağlantısı ölünce kaydı düşer.
 *  - `SÖYLE` tek ses kuyruğuna kaynağıyla girer: asistanın cevabı dönüş talimatının üstüne binmez (D3).
 *  - `DİNLE`/`DİKTE`'de duyulan doğrudan o uygulamaya gider (Dicio becerilerine uğramaz).
 *  - Uygulama kapalıyken metin devri: asistan uygulamanın köprü servisine bağlanır, `METİN`'i verir; uygulama ekran
 *    açmak isterse `AÇ` ile PendingIntent yollar.
 */
object Kopru {
    private const val TAG = "Kopru"
    /** Uygulama kapalıyken açılan köprü bağlantısı bu kadar sonra bırakılır (`AÇ` cevabı gelmiş olur). */
    private const val BIRAK_MS = 15_000L

    class Bagli(val paket: String, val messenger: Messenger) {
        @Volatile var navigasyon = false
        @Volatile var onde = false
        @Volatile var uyanma = false
        var olum: IBinder.DeathRecipient? = null
    }

    private lateinit var app: Context
    private val main = Handler(Looper.getMainLooper())
    private val isci: Handler by lazy { Handler(HandlerThread("kopru").apply { start() }.looper) { m -> isle(m); true } }
    /** Asistanın uygulamalara verdiği Messenger (ses servisinin binder'ı ve metin devrindeki `replyTo`). */
    val gelen: Messenger by lazy { Messenger(isci) }

    private val baglilar = ConcurrentHashMap<String, Bagli>()
    /** Şu an kimin için dinleniyor (`DİNLE`/`DİKTE`); null: asistanın kendisi. */
    @Volatile private var dinleyen: String? = null

    /** Uyanma dinlemesi hazır mı ([SesServisi] Dicio'nun uyanma aygıtından günceller). */
    @Volatile var uyanmaHazir = false
        private set
    /** Uyanma servisini başlatır ([SesServisi] verir). */
    @Volatile var uyanmayiBaslat: () -> Unit = {}

    @Synchronized
    fun hazirla(ctx: Context) {
        if (!::app.isInitialized) app = ctx.applicationContext
    }

    fun bagli(paket: String) = baglilar.containsKey(paket)
    fun navigasyonda(paket: String) = baglilar[paket]?.navigasyon == true
    fun onde(paket: String) = baglilar[paket]?.onde == true

    /** Yönlendirmenin adayları: köprüsü olan uygulamalar ve bağlıysa bildirdikleri durum. Ana iş parçacığında değil. */
    fun adaylar(ctx: Context): List<Yonlendirme.Aday> = Uygulamalar.hepsi(ctx).map {
        Yonlendirme.Aday(it.paket, it.uyanma, it.devir, navigasyonda(it.paket), onde(it.paket))
    }

    fun uyanmaHazirDegisti(h: Boolean) {
        if (uyanmaHazir == h) return
        uyanmaHazir = h
        if (h && baglilar.isNotEmpty()) uyanmayiBaslat()
        baglilar.values.forEach { hosgeldin(it.messenger) }
    }

    // ------------------------------------------------------------------ gelen
    private fun isle(m: Message) {
        if (Build.VERSION.SDK_INT < 22) return
        val paket = Uygulamalar.paketi(app, m.sendingUid) ?: return
        val d = m.data ?: Bundle.EMPTY
        // metin devrinde uygulama kaydolmadan da cevap verebilir (replyTo ile)
        val cevap = m.replyTo ?: baglilar[paket]?.messenger
        when (m.what) {
            Protokol.MERHABA -> m.replyTo?.let { kaydet(paket, it) }
            Protokol.SOYLE -> {
                val kimlik = d.getInt(Protokol.KIMLIK)
                val metin = d.getString(Protokol.METIN_ALANI).orEmpty()
                val tur = tur(d.getString(Protokol.TUR)) ?: SpeechQueue.Kind.CEVAP
                val isaret = d.getBoolean(Protokol.ISARET, true)
                main.post {
                    Ses.al(app).soyle(metin, tur, kaynak = paket, isaret = isaret) { bayat ->
                        gonder(cevap, Protokol.SOYLENDI) { putInt(Protokol.KIMLIK, kimlik); putBoolean(Protokol.BAYAT, bayat) }
                    }
                }
            }
            Protokol.SUSTUR -> {
                val tur = tur(d.getString(Protokol.TUR))
                main.post { Ses.al(app).sustur(tur, kaynak = paket) }
            }
            Protokol.DINLE -> main.post { dinle(paket, cevap) }
            Protokol.DIKTE -> {
                val kimlik = d.getInt(Protokol.KIMLIK)
                val bas = d.getString(Protokol.BASLANGIC).orEmpty()
                main.post { dikte(paket, cevap, kimlik, bas) }
            }
            Protokol.DINLEME_IPTAL -> main.post { if (dinleyen == paket) { Kulak.al(app).iptal(); dinleyen = null } }
            Protokol.DURUM -> durum(paket, d.getString(Protokol.ANAHTAR), d.getBoolean(Protokol.DEGER))
            Protokol.AC -> @Suppress("DEPRECATION") (d.getParcelable<PendingIntent>(Protokol.PENDING_INTENT))?.let { ac(it) }
        }
    }

    private fun tur(s: String?) = when (s) {
        Protokol.T_TALIMAT -> SpeechQueue.Kind.TALIMAT
        Protokol.T_UYARI -> SpeechQueue.Kind.UYARI
        Protokol.T_CEVAP -> SpeechQueue.Kind.CEVAP
        Protokol.T_BILGI -> SpeechQueue.Kind.BILGI
        else -> null
    }

    private fun kaydet(paket: String, messenger: Messenger) {
        baglilar.remove(paket)?.let { birak(it) }
        val b = Bagli(paket, messenger)
        val olum = IBinder.DeathRecipient { if (baglilar[paket] === b) { baglilar.remove(paket); main.post { ayrildi(paket) } } }
        if (runCatching { messenger.binder.linkToDeath(olum, 0) }.isFailure) return
        b.olum = olum
        baglilar[paket] = b
        Log.d(TAG, "$paket bağlandı")
        hosgeldin(messenger)
        if (uyanmaHazir) uyanmayiBaslat()
    }

    private fun birak(b: Bagli) {
        b.olum?.let { runCatching { b.messenger.binder.unlinkToDeath(it, 0) } }
    }

    /** Uygulama gitti (çöktü, kapandı): sesleri kesilir, durumu düşer, onun için dinleniyorsa bırakılır. */
    private fun ayrildi(paket: String) {
        Ses.al(app).sustur(kaynak = paket)
        if (dinleyen == paket) { Kulak.al(app).iptal(); dinleyen = null }
        ozetle()
    }

    private fun durum(paket: String, anahtar: String?, deger: Boolean) {
        val b = baglilar[paket] ?: return
        when (anahtar) {
            Protokol.D_NAVIGASYON -> b.navigasyon = deger
            Protokol.D_ONDE -> b.onde = deger
            Protokol.D_UYANMA -> b.uyanma = deger
        }
        ozetle()
    }

    /** K3 politikasının girdileri: herhangi bir bağlı uygulama navigasyonda / uyanma istiyor. */
    private fun ozetle() {
        SesDurumu.navigasyonda = baglilar.values.any { it.navigasyon }
        SesDurumu.uygulamaUyanmaIstiyor = baglilar.values.any { it.uyanma }
    }

    private fun hosgeldin(m: Messenger) = gonder(m, Protokol.HOSGELDIN) {
        putInt(Protokol.SURUM_ALANI, Protokol.SURUM)
        putBoolean(Protokol.UYANMA_HAZIR, uyanmaHazir)
    }

    // ------------------------------------------------------------------ dinleme (ana iş parçacığı)
    private fun dinle(paket: String, cevap: Messenger?) {
        dinleyen = paket
        Kulak.al(app).komut { olay ->
            when (olay) {
                is Kulak.Olay.Kismi -> gonder(cevap, Protokol.KISMI) { putString(Protokol.METIN_ALANI, olay.metin) }
                is Kulak.Olay.Sonuc -> {
                    if (dinleyen == paket) dinleyen = null
                    gonder(cevap, Protokol.METIN) { putString(Protokol.METIN_ALANI, olay.secenekler.first().first); putBoolean(Protokol.UYANMA, false) }
                }
                Kulak.Olay.Bos, is Kulak.Olay.Hata -> {
                    if (dinleyen == paket) dinleyen = null
                    gonder(cevap, Protokol.DUYULMADI) {}
                }
            }
        }
    }

    private fun dikte(paket: String, cevap: Messenger?, kimlik: Int, bas: String) {
        dinleyen = paket
        Kulak.al(app).dikte(bas, kismi = { t -> gonder(cevap, Protokol.KISMI) { putString(Protokol.METIN_ALANI, t) } }) { t ->
            if (dinleyen == paket) dinleyen = null
            gonder(cevap, Protokol.DIKTE_SONUC) { putInt(Protokol.KIMLIK, kimlik); putString(Protokol.METIN_ALANI, t) }
        }
    }

    // ------------------------------------------------------------------ giden
    private fun gonder(m: Messenger?, what: Int, f: Bundle.() -> Unit): Boolean {
        m ?: return false
        val msg = Message.obtain(null, what).apply { data = Bundle().apply(f); replyTo = gelen }
        return runCatching { m.send(msg) }.onFailure { Log.w(TAG, "ileti gönderilemedi ($what)", it) }.isSuccess
    }

    private fun metinIletisi(metin: String, uyanma: Boolean) = Message.obtain(null, Protokol.METIN).apply {
        data = Bundle().apply { putString(Protokol.METIN_ALANI, metin); putBoolean(Protokol.UYANMA, uyanma) }
        replyTo = gelen
    }

    /**
     * Metin devri: bağlıysa doğrudan, değilse uygulamanın köprü servisine bağlanılıp verilir (uygulama kapalıyken;
     * ekranı `AÇ` ile kendisi açtırır). false: uygulamaya ulaşılamadı (kurulu değil, izin yok).
     */
    fun metinVer(paket: String, metin: String, uyanma: Boolean): Boolean {
        baglilar[paket]?.let { b -> if (runCatching { b.messenger.send(metinIletisi(metin, uyanma)) }.isSuccess) return true }
        val y = Uygulamalar.bul(app, paket) ?: return false
        val conn = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                val ok = binder != null && runCatching { Messenger(binder).send(metinIletisi(metin, uyanma)) }.isSuccess
                if (!ok) Log.w(TAG, "$paket köprüsüne metin verilemedi")
                main.postDelayed({ runCatching { app.unbindService(this) } }, BIRAK_MS)
            }

            override fun onServiceDisconnected(name: ComponentName?) {}
        }
        return runCatching { app.bindService(Intent().setComponent(y.servis), conn, Context.BIND_AUTO_CREATE) }
            .onFailure { Log.w(TAG, "$paket köprüsüne bağlanılamadı", it) }
            .getOrDefault(false)
    }

    /**
     * Uygulamanın istediği ekranı açar. Arka plandan Activity açmak için asistanın "diğer uygulamaların üzerinde
     * göster" izni gerekir (HyperOS'ta ayrıca "arka planda açılır pencere"); Android 14+ izin gönderende açıkça verilir.
     */
    private fun ac(pi: PendingIntent) {
        val opts = when {
            Build.VERSION.SDK_INT >= 36 -> ActivityOptions.makeBasic()
                .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS).toBundle()
            Build.VERSION.SDK_INT >= 34 -> ActivityOptions.makeBasic()
                .setPendingIntentBackgroundActivityStartMode(@Suppress("DEPRECATION") ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED).toBundle()
            else -> null
        }
        runCatching { pi.send(app, 0, null, null, null, null, opts) }.onFailure { Log.w(TAG, "ekran açılamadı", it) }
    }
}

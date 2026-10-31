package com.ses.asistan.beceri

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.browse.MediaBrowser
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ResultReceiver
import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.stypox.dicio.skills.media.MediaNotificationListener
import kotlin.coroutines.resume

/**
 * Kanallarım köprüsü (Harita `Media.kt`'den taşındı; Kanallarım'da değişiklik yok): Android'in MediaBrowser'ı ile
 * `com.kanallarim.app/.PlaybackService`'e bağlanılır, çalma onun servisinde sürer (kaldığı yer orada). Kurulu değilse
 * hiçbir şey yapmaz. Çalan oturum (YouTube, müzik…) bildirim erişimi verilmişse Dicio'nun bildirim dinleyicisiyle
 * okunur; verilmemişse yalnız Kanallarım.
 *
 * MediaBrowser geri çağrıları ana iş parçacığında gelir; dışarıya askıya alınan işlevler verilir (beceri arka planda).
 * Eski bağlantının geç gelen haberi yeni tarayıcıyı okumaz (Harita'da çöküyordu).
 */
class Kanallar private constructor(private val ctx: Context) {
    private val main = Handler(Looper.getMainLooper())
    private var browser: MediaBrowser? = null
    private var controller: MediaController? = null
    private val waiting = ArrayList<(MediaController?) -> Unit>()
    private val sessions by lazy { ctx.getSystemService(MediaSessionManager::class.java) }
    private val listenerName by lazy { ComponentName(ctx, MediaNotificationListener::class.java) }

    /** Çalan: başlık, uygulama (Kanallarım değilse), oynuyor mu, konum (ms, ilerletilmiş), sanatçı (videoda kanal). */
    class Calan(val baslik: String?, val uygulama: String?, val oynuyor: Boolean, val konumMs: Long, val sanatci: String?, val kanallarim: Boolean)

    fun kurulu() = runCatching { ctx.packageManager.getPackageInfo(PKG, 0) }.isSuccess

    /** Ana iş parçacığında; bağlanamazsa [block] null alır. */
    private fun withController(block: (MediaController?) -> Unit) {
        controller?.let { return block(it) }
        if (!kurulu()) return block(null)
        waiting += block
        if (browser != null) return // bağlanıyor
        lateinit var b: MediaBrowser
        b = MediaBrowser(ctx, ComponentName(PKG, "$PKG.PlaybackService"), object : MediaBrowser.ConnectionCallback() {
            override fun onConnected() {
                if (browser !== b) return
                val c = runCatching { MediaController(ctx, b.sessionToken) }.getOrNull()
                controller = c
                waiting.toList().also { waiting.clear() }.forEach { it(c) }
            }

            override fun onConnectionSuspended() { if (browser === b) { controller = null; browser = null } }

            override fun onConnectionFailed() {
                if (browser !== b) return
                browser = null
                waiting.toList().also { waiting.clear() }.forEach { it(null) }
            }
        }, null)
        browser = b
        b.connect()
    }

    private suspend fun <T> anada(f: ((T) -> Unit) -> Unit): T = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { k -> f { if (k.isActive) k.resume(it) } }
    }

    /** Kanallarım'a bağlı denetleyici (bağlanamazsa null). */
    suspend fun baglan(): MediaController? = withTimeoutOrNull(BAGLANTI_MS) { anada { cb -> withController(cb) } }

    /** dl = indirilenler, pl = oynatma listeleri, map = harita listesi, recent = son izlenenler. */
    suspend fun cocuklar(parent: String): List<MediaBrowser.MediaItem> {
        baglan() ?: return emptyList()
        return withTimeoutOrNull(BAGLANTI_MS) {
            anada<List<MediaBrowser.MediaItem>> { done ->
                val b = browser
                if (b == null) done(emptyList()) else b.subscribe(parent, object : MediaBrowser.SubscriptionCallback() {
                    override fun onChildrenLoaded(parentId: String, children: List<MediaBrowser.MediaItem>) {
                        b.unsubscribe(parentId); done(children)
                    }

                    override fun onError(parentId: String) { b.unsubscribe(parentId); done(emptyList()) }
                })
            }
        }.orEmpty()
    }

    /** "resume" son çalınan sırayı kaldığı yerden; "pl/<no>" listeyi kaldığı yerden; "map" harita listesinin seçilisi. */
    suspend fun cal(mediaId: String): Boolean {
        val c = baglan() ?: return false
        withContext(Dispatchers.Main) { c.transportControls.playFromMediaId(mediaId, null) }
        return true
    }

    /** Çalan oturum, yoksa en son etkin olan (duraklatılmış YouTube da sürdürülebilsin), yoksa Kanallarım. */
    private fun hedef(): MediaController? {
        val active = runCatching { sessions.getActiveSessions(listenerName) }.getOrNull().orEmpty()
        return active.firstOrNull { it.oynuyor() } ?: active.firstOrNull() ?: controller
    }

    /** Hedef oturum Kanallarım'ın boş (bir şey yüklenmemiş) oturumu değil mi. */
    private fun MediaController.dolu() = packageName != PKG || metadata != null

    /** Kanallarım şu an çalıyor (genel "durdur/sonraki" komutlarında bizim beceri öne geçer). */
    fun kanallarimCaliyor(): Boolean {
        val t = hedef() ?: return false
        return t.packageName == PKG && t.oynuyor()
    }

    suspend fun eylem(e: KanallarSozleri.Eylem): Boolean {
        val c = baglan()
        return withContext(Dispatchers.Main) {
            val t = hedef()?.takeIf { it.dolu() }
            when (e) {
                // sesli "durdur": hangi uygulama çalıyorsa o duraklar
                KanallarSozleri.Eylem.PAUSE -> (t ?: c)?.transportControls?.pause()
                // sesli "oynat": çalan ya da son uygulamayı sürdürür; yoksa Kanallarım'da kaldığı yerden
                KanallarSozleri.Eylem.PLAY -> if (t != null) t.transportControls.play() else c?.transportControls?.playFromMediaId("resume", null)
                KanallarSozleri.Eylem.NEXT -> (t ?: c)?.transportControls?.skipToNext()
                KanallarSozleri.Eylem.PREVIOUS -> (t ?: c)?.transportControls?.skipToPrevious()
                KanallarSozleri.Eylem.RESUME -> c?.transportControls?.playFromMediaId("resume", null)
                else -> Unit
            } != null
        }
    }

    /**
     * Çalanı sarar: [ms] kadar ileri/geri ya da [mutlak] ise o ana. Çalan oturum yoksa false. Süre biliniyorsa sonun
     * 1 sn öncesinde durur (bir sonraki videoya atlamasın).
     */
    suspend fun sar(ms: Long, mutlak: Boolean): Boolean {
        if (hedef() == null && kurulu()) baglan()
        return withContext(Dispatchers.Main) {
            val t = hedef()?.takeIf { it.dolu() } ?: return@withContext false
            val dur = t.metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION)?.coerceAtLeast(0) ?: 0L
            var to = (if (mutlak) ms else t.konum() + ms).coerceAtLeast(0)
            if (dur > 1000) to = to.coerceAtMost(dur - 1000)
            t.transportControls.seekTo(to)
            true
        }
    }

    suspend fun calan(): Calan {
        if (hedef() == null && kurulu()) baglan()
        return withContext(Dispatchers.Main) {
            val t = hedef() ?: return@withContext Calan(null, null, false, 0, null, false)
            val md = t.metadata
            val title = md?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: md?.description?.title?.toString()
            val dur = md?.getLong(MediaMetadata.METADATA_KEY_DURATION)?.coerceAtLeast(0) ?: 0L
            val pos = t.konum()
            Calan(
                title, if (t.packageName == PKG) null else uygulamaAdi(t.packageName), t.oynuyor(),
                if (dur > 0) pos.coerceAtMost(dur) else pos, md?.getString(MediaMetadata.METADATA_KEY_ARTIST), t.packageName == PKG,
            )
        }
    }

    /**
     * "Videoya not al": çalan Kanallarım'sa `not_al` komutu onun oturumuna gider, not orada (o anın altyazısıyla)
     * Notlar'a yazılır. Dönüş: Notlar'ın cevap kodu (1 yazıldı, 2 sıraya alındı, 0 olmadı); null: Kanallarım çalmıyor.
     * Komutu tanımayan eski Kanallarım hiç cevap vermez; [NOT_MS] sonra olmadı sayılır.
     */
    suspend fun notAl(metin: String): Int? {
        if (hedef() == null && kurulu()) baglan()
        return withContext(Dispatchers.Main) {
            val t = hedef()
            if (t == null || t.packageName != PKG || t.metadata == null) return@withContext null
            withTimeoutOrNull(NOT_MS) {
                suspendCancellableCoroutine { k ->
                    // Kanallarım "text"i notun gövdesine yazar (sesle dikte edilen), altına o anın altyazısı
                    t.sendCommand(NOTE, Bundle().apply { if (metin.isNotBlank()) putString("text", metin) }, object : ResultReceiver(main) {
                        override fun onReceiveResult(resultCode: Int, data: Bundle?) { if (k.isActive) k.resume(data?.getInt("durum") ?: 0) }
                    })
                }
            } ?: 0
        }
    }

    /** Oturumun bildirdiği konum, son bildirimden bu yana geçen süreyle ilerletilmiş (oturum konumu seyrek bildirir). */
    private fun MediaController.konum(): Long {
        val s = playbackState ?: return 0L
        val p = s.position
        return if (p < 0) 0L else if (oynuyor() && s.lastPositionUpdateTime > 0) p + ((SystemClock.elapsedRealtime() - s.lastPositionUpdateTime) * s.playbackSpeed).toLong() else p
    }

    private fun MediaController.oynuyor() = playbackState?.state.let { it == PlaybackState.STATE_PLAYING || it == PlaybackState.STATE_BUFFERING }

    private fun uygulamaAdi(pkg: String) = KNOWN[pkg] ?: runCatching {
        ctx.packageManager.getApplicationLabel(ctx.packageManager.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg.substringAfterLast('.'))

    companion object {
        const val PKG = "com.kanallarim.app"
        /** Kanallarım'ın oturum komutu (PlaybackService.NOTE ile aynı ad). */
        private const val NOTE = "not_al"
        private const val BAGLANTI_MS = 5_000L
        private const val NOT_MS = 20_000L
        private val KNOWN = mapOf(
            "com.google.android.youtube" to "YouTube", "com.google.android.apps.youtube.music" to "YouTube Music",
            "com.spotify.music" to "Spotify",
        )

        @Volatile private var tek: Kanallar? = null
        fun al(ctx: Context): Kanallar = tek ?: synchronized(this) { tek ?: Kanallar(ctx.applicationContext).also { tek = it } }
    }
}

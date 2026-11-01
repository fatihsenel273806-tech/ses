package com.ses.asistan.ek

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Nota eklenen konum ("konumu da ekle"). Asistan çoğu zaman arka plandadır (Kanallarım, Harita önde): konum için
 * "her zaman izin ver" gerekir (ayarlardan). Önce taze konum (en çok [SURE_MS]), olmazsa son bilinen; adres
 * Geocoder'dan (ağ yoksa yalnız koordinat).
 */
object Konum {
    private const val SURE_MS = 8_000L
    /** Son bilinen konum bundan eskiyse taze konum beklenir. */
    private const val TAZE_MS = 2 * 60_000L
    private val TR = Locale("tr")

    class Yer(val enlem: Double, val boylam: Double, val adres: String?) {
        /** Notun altına yazılan satır: adres (yoksa koordinat) ve haritada açan bağlantı. */
        fun satir(): String {
            val k = String.format(Locale.ROOT, "%.5f,%.5f", enlem, boylam)
            return "📍 ${adres ?: k} — https://maps.google.com/?q=$k"
        }
    }

    private fun izin(ctx: Context, ad: String) = ContextCompat.checkSelfPermission(ctx, ad) == PackageManager.PERMISSION_GRANTED

    fun izinVar(ctx: Context) = izin(ctx, Manifest.permission.ACCESS_FINE_LOCATION) || izin(ctx, Manifest.permission.ACCESS_COARSE_LOCATION)

    fun arkaPlanIzni(ctx: Context) = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
        izin(ctx, Manifest.permission.ACCESS_BACKGROUND_LOCATION)

    /** null: izin yok ya da konum bulunamadı. */
    suspend fun al(ctx: Context): Yer? {
        if (!izinVar(ctx)) return null
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val son = sonBilinen(lm)
        val loc = son?.takeIf { System.currentTimeMillis() - it.time < TAZE_MS } ?: taze(ctx, lm) ?: son ?: return null
        val adres = withContext(Dispatchers.IO) { adres(ctx, loc) }
        return Yer(loc.latitude, loc.longitude, adres)
    }

    @SuppressLint("MissingPermission")
    private fun sonBilinen(lm: LocationManager): Location? = runCatching {
        lm.getProviders(true).mapNotNull { lm.getLastKnownLocation(it) }.maxByOrNull { it.time }
    }.getOrNull()

    @SuppressLint("MissingPermission")
    private suspend fun taze(ctx: Context, lm: LocationManager): Location? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        // "fused" = LocationManager.FUSED_PROVIDER (API 31 sabiti; daha eski aygıtta etkin görünmez, atlanır)
        val saglayici = listOf("fused", LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .firstOrNull { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) } ?: return null
        return withTimeoutOrNull(SURE_MS) {
            suspendCancellableCoroutine { k ->
                val iptal = android.os.CancellationSignal()
                k.invokeOnCancellation { iptal.cancel() }
                runCatching {
                    lm.getCurrentLocation(saglayici, iptal, ctx.mainExecutor) { if (k.isActive) k.resume(it) }
                }.onFailure { if (k.isActive) k.resume(null) }
            }
        }
    }

    @Suppress("DEPRECATION") // eşzamanlı sürüm: zaten IO iş parçacığındayız
    private fun adres(ctx: Context, l: Location): String? = runCatching {
        if (!Geocoder.isPresent()) return null
        Geocoder(ctx, TR).getFromLocation(l.latitude, l.longitude, 1)?.firstOrNull()?.getAddressLine(0)
    }.getOrNull()
}

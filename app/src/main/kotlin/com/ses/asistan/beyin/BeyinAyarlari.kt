package com.ses.asistan.beyin

import android.content.Context
import android.content.SharedPreferences

/**
 * Gemini ayarları. Anahtar yalnız telefonda, ayarlardan girilir; depoya girmez (CLAUDE.md). Birden çok anahtar ve model
 * verilebilir: [GeminiHavuzu] hakkı biteni atlar.
 */
class BeyinAyarlari(ctx: Context) {
    private val sp: SharedPreferences = ctx.getSharedPreferences("asistan_beyin", Context.MODE_PRIVATE)

    var acik: Boolean
        get() = sp.getBoolean("acik", true)
        set(v) = sp.edit().putBoolean("acik", v).apply()

    /** Ham metin ("k1, k2" ya da satır satır). */
    var anahtarlarHam: String
        get() = sp.getString("anahtarlar", "") ?: ""
        set(v) { sp.edit().putString("anahtarlar", v.trim()).apply(); havuz.sifirla() }

    val anahtarlar: List<String> get() = GeminiHavuzu.bol(anahtarlarHam)

    /** Model sırası; boşsa [VARSAYILAN_MODELLER]. */
    var modellerHam: String
        get() = sp.getString("modeller", "") ?: ""
        set(v) { sp.edit().putString("modeller", v.trim()).apply(); havuz.sifirla() }

    val modeller: List<String> get() = GeminiHavuzu.bol(modellerHam).ifEmpty { VARSAYILAN_MODELLER }

    val havuz = GeminiHavuzu({ sp.getString("bekleyen", "") ?: "" }, { sp.edit().putString("bekleyen", it).apply() })

    fun hazir() = acik && anahtarlar.isNotEmpty()

    fun dinle(f: () -> Unit): SharedPreferences.OnSharedPreferenceChangeListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> f() }.also { sp.registerOnSharedPreferenceChangeListener(it) }

    fun birak(l: SharedPreferences.OnSharedPreferenceChangeListener) = sp.unregisterOnSharedPreferenceChangeListener(l)

    companion object {
        /** Önce düşünmesi kapatılabilen hızlı model; o kalkarsa (404) Google'ın güncel Flash takma adı. */
        val VARSAYILAN_MODELLER = listOf("gemini-2.5-flash", "gemini-flash-latest")

        @Volatile private var tek: BeyinAyarlari? = null
        fun al(ctx: Context): BeyinAyarlari = tek ?: synchronized(this) {
            tek ?: BeyinAyarlari(ctx.applicationContext).also { tek = it }
        }
    }
}

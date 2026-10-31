package com.ses.asistan.ses

import android.content.Context
import android.content.SharedPreferences

/**
 * Asistanın ses ve uyanma ayarları. Ses ayarlarının adları ve varsayılanları Harita `Prefs`'tekiyle aynı (Voice
 * birebir taşınabilsin diye); uyanma ayarları K3 politikasının girdileri.
 */
class SesAyarlari(ctx: Context) {
    private val sp: SharedPreferences = ctx.getSharedPreferences("asistan_ses", Context.MODE_PRIVATE)

    // ---------------------------------------------------------------- ses (Harita Prefs)
    var voiceEngine: Int
        get() = sp.getInt("engine", ENGINE_ANTALIA)
        set(v) = sp.edit().putInt("engine", v).apply()

    /** Android sesinde seçilen ses (TextToSpeech Voice adı); boşsa motorun varsayılan Türkçe sesi. */
    var androidVoice: String
        get() = sp.getString("ttsvoice", "") ?: ""
        set(v) = sp.edit().putString("ttsvoice", v).apply()

    /** Android sesinin hızı ve perdesi (yüzde, 100 = normal). */
    var ttsRate: Int
        get() = sp.getInt("ttsrate", 100)
        set(v) = sp.edit().putInt("ttsrate", v).apply()
    var ttsPitch: Int
        get() = sp.getInt("ttspitch", 100)
        set(v) = sp.edit().putInt("ttspitch", v).apply()

    /** Antalia akış adımı: 2 hızlı, 4 dengeli, 8 en iyi kalite. */
    var antaliaSteps: Int
        get() = sp.getInt("steps", 4)
        set(v) = sp.edit().putInt("steps", v).apply()

    /** Konuşmanın ses düzeyi: 0 düşük, 1 orta, 2 yüksek (yalnızca bizim konuşmamız; telefon sesi değişmez). */
    var voiceVolume: Int
        get() = sp.getInt("voice_volume", 2)
        set(v) = sp.edit().putInt("voice_volume", v).apply()

    // ---------------------------------------------------------------- uyanma (K3)
    var uyanmaKipi: UyanmaPolitikasi.Kip
        get() = UyanmaPolitikasi.Kip.values().getOrElse(sp.getInt("uyanma_kipi", 0)) { UyanmaPolitikasi.Kip.ARACTA_VE_ELLE }
        set(v) = sp.edit().putInt("uyanma_kipi", v.ordinal).apply()

    var elleAcik: Boolean
        get() = sp.getBoolean("elle_acik", false)
        set(v) = sp.edit().putBoolean("elle_acik", v).apply()

    /** Aracın Bluetooth adı (bir parçası yeter); boşsa her Bluetooth ses aygıtı araç sayılır. */
    var aracAdi: String
        get() = sp.getString("arac_adi", "") ?: ""
        set(v) = sp.edit().putString("arac_adi", v.trim()).apply()

    fun dinle(f: () -> Unit): SharedPreferences.OnSharedPreferenceChangeListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> f() }.also { sp.registerOnSharedPreferenceChangeListener(it) }

    fun birak(l: SharedPreferences.OnSharedPreferenceChangeListener) = sp.unregisterOnSharedPreferenceChangeListener(l)

    companion object {
        const val ENGINE_ANTALIA = 0
        const val ENGINE_ANDROID = 1

        @Volatile private var tek: SesAyarlari? = null
        fun al(ctx: Context): SesAyarlari = tek ?: synchronized(this) {
            tek ?: SesAyarlari(ctx.applicationContext).also { tek = it }
        }
    }
}

package com.ses.asistan.giris

/**
 * Google tanıyıcısının hangi tanıma servisiyle açılacağı (saf; JVM testli).
 *
 * Kulak normalde sistemin varsayılan tanıyıcısını kullanır (telefonda Google'ınki). Varsayılan asistanın kendisi
 * olursa (kullanıcı "Ses girişi"nde Asistan'ı seçerse) Kulak kendi `SttService`'ine, o da yeniden Kulak'a gider:
 * döngü. O durumda ya da varsayılan yoksa Google'ın tanıma servisi açıkça seçilir.
 *
 * Bileşenler "paket/sınıf" biçiminde (`ComponentName.flattenToString`).
 */
object TaniyiciSecimi {
    /** Öncelik sırası: Xiaomi'de Google'ın tanıyıcısı `com.google.android.tts`'te (HAFIZA), eskisi Google uygulamasında. */
    private val GOOGLE = listOf("com.google.android.tts", "com.google.android.googlequicksearchbox")

    /**
     * Seçilecek bileşen; null = sistemin varsayılanı kullanılsın. [adaylar] yalnız varsayılan işe yaramazsa sorulur
     * (paket yöneticisine gereksiz çağrı yok).
     */
    fun sec(varsayilan: String?, kendiPaket: String, adaylar: () -> List<String>): String? {
        if (!varsayilan.isNullOrBlank() && paket(varsayilan) != kendiPaket) return null
        val digerleri = adaylar().filter { paket(it) != kendiPaket }
        return GOOGLE.firstNotNullOfOrNull { g -> digerleri.firstOrNull { paket(it) == g } } ?: digerleri.firstOrNull()
    }

    private fun paket(bilesen: String) = bilesen.substringBefore('/')
}

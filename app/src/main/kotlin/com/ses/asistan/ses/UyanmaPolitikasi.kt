package com.ses.asistan.ses

/**
 * Uyanma dinlemesi ne zaman açık (PLAN K3, 2026-10-31: "araçta + elle"). Android'e bağlı değil: JVM testiyle sınanır.
 *
 * Mikrofon yalnız gerektiği sürece açık (pil): kapalıyken uyanma servisi çalışır ama kayıt durur (gizlilik işareti
 * de söner). "Araçta": Bluetooth ses aygıtı bağlı (ayarda araç adı verildiyse yalnız o) ya da bağlı uygulama
 * navigasyonda (Harita, Faz 3 `DURUM`). "Elle": bildirimden ya da ayarlardan açılır/kapanır.
 */
object UyanmaPolitikasi {
    enum class Kip { ARACTA_VE_ELLE, HER_ZAMAN, YALNIZ_ELLE }

    data class Durum(
        val kip: Kip = Kip.ARACTA_VE_ELLE,
        /** Elle açıldı mı (bildirimdeki "Dinle" düğmesi). */
        val elleAcik: Boolean = false,
        /** Bağlı Bluetooth ses aygıtlarının adları. */
        val bluetoothAdlari: List<String> = emptyList(),
        /** Ayardaki araç adı (boş: her Bluetooth ses aygıtı araç sayılır). */
        val aracAdi: String = "",
        val navigasyonda: Boolean = false,
        /** Telefon görüşmesi sürüyor: mikrofon görüşmenindir. */
        val gorusmede: Boolean = false,
    )

    fun aracta(bluetoothAdlari: List<String>, aracAdi: String): Boolean {
        val ad = aracAdi.trim()
        if (ad.isEmpty()) return bluetoothAdlari.isNotEmpty()
        return bluetoothAdlari.any { it.contains(ad, ignoreCase = true) }
    }

    fun dinlemeli(d: Durum): Boolean {
        if (d.gorusmede) return false
        return when (d.kip) {
            Kip.HER_ZAMAN -> true
            Kip.YALNIZ_ELLE -> d.elleAcik
            Kip.ARACTA_VE_ELLE -> d.elleAcik || d.navigasyonda || aracta(d.bluetoothAdlari, d.aracAdi)
        }
    }
}

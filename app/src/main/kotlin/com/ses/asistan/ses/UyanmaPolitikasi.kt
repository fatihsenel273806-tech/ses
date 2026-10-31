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
        /** Asistanın kendi ekranı önde: elle açılmış sayılır (açıp "Hey Asistan" demek de çalışsın). */
        val asistanEkranda: Boolean = false,
        /** Bağlı uygulama kendi uyanma ayarıyla şimdi dinleme istiyor (araçta + elle kipinde sayılır). */
        val uygulamaIstiyor: Boolean = false,
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
            Kip.YALNIZ_ELLE -> d.elleAcik || d.asistanEkranda
            Kip.ARACTA_VE_ELLE -> d.elleAcik || d.asistanEkranda || d.navigasyonda || d.uygulamaIstiyor || aracta(d.bluetoothAdlari, d.aracAdi)
        }
    }

    /** Ayarlarda gösterilen gerekçe: şu an neden dinleniyor ya da dinlenmiyor. */
    fun neden(d: Durum): String = when {
        d.gorusmede -> "Dinlenmiyor: telefon görüşmesi sürüyor"
        d.kip == Kip.HER_ZAMAN -> "Dinleniyor (her zaman)"
        d.elleAcik -> "Dinleniyor (elle açıldı)"
        d.asistanEkranda -> "Dinleniyor (asistan ekranda)"
        d.kip == Kip.YALNIZ_ELLE -> "Dinlenmiyor: yalnız elle (bildirimdeki “Dinle / bırak” ya da aşağıdaki anahtar)"
        d.navigasyonda -> "Dinleniyor (navigasyon)"
        d.uygulamaIstiyor -> "Dinleniyor (bağlı uygulama istedi)"
        aracta(d.bluetoothAdlari, d.aracAdi) -> "Dinleniyor (araçta: ${d.bluetoothAdlari.joinToString()})"
        d.bluetoothAdlari.isNotEmpty() -> "Dinlenmiyor: bağlı Bluetooth (${d.bluetoothAdlari.joinToString()}) araç adıyla eşleşmiyor"
        else -> "Dinlenmiyor: araçta değil (Bluetooth yok). Elle açın ya da “Her zaman” seçin"
    }
}

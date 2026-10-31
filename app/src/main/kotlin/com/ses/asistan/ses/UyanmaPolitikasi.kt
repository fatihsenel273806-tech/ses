package com.ses.asistan.ses

/**
 * Uyanma dinlemesi ne zaman açık (PLAN K3, 2026-10-31: "araçta + elle"). Android'e bağlı değil: JVM testiyle sınanır.
 *
 * Mikrofon yalnız gerektiği sürece açık (pil): kapalıyken uyanma servisi çalışır ama kayıt durur (gizlilik işareti
 * de söner). "Araçta": Bluetooth ses aygıtı bağlı (ayarda araç adı verildiyse yalnız o) ya da bağlı uygulama
 * navigasyonda (Harita, Faz 3 `DURUM`). "Elle": bildirimden ya da ayarlardan açılır/kapanır.
 */
object UyanmaPolitikasi {
    /** Ayarda sırayla saklanır (ordinal): yeni kip sona eklenir. */
    enum class Kip { ARACTA_VE_ELLE, HER_ZAMAN, YALNIZ_ELLE, UYGULAMALAR_ACIKKEN }

    /** "İlgili uygulamalar açıkken" kipinde önde olunca dinlenen uygulamalar (bağlı olsun olmasın). */
    val ILGILI = mapOf(
        "com.harita.ankara" to "Harita", "com.kanallarim.app" to "Kanallarım",
        "dev.kaimau.notlar" to "Notlar", "com.dosyaokuyucu.dosya_okuyucu" to "Dosya Okuyucu",
    )

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
        /** Önde olan ilgili uygulamanın adı ([ILGILI]; bağlı uygulamanın "önde" bildirimi ya da kullanım erişimi). */
        val ilgiliOnde: String? = null,
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
            // ilgili uygulamalarla birlikte çalışır, boştayken kayıt durur (kullanıcı isteği 2026-10-31)
            Kip.UYGULAMALAR_ACIKKEN -> d.elleAcik || d.asistanEkranda || d.navigasyonda || d.ilgiliOnde != null
        }
    }

    /** Ayarlarda gösterilen gerekçe: şu an neden dinleniyor ya da dinlenmiyor. */
    fun neden(d: Durum): String = when {
        d.gorusmede -> "Dinlenmiyor: telefon görüşmesi sürüyor"
        d.kip == Kip.HER_ZAMAN -> "Dinleniyor (her zaman)"
        d.elleAcik -> "Dinleniyor (elle açıldı)"
        d.asistanEkranda -> "Dinleniyor (asistan ekranda)"
        d.navigasyonda && d.kip == Kip.UYGULAMALAR_ACIKKEN -> "Dinleniyor (navigasyon)"
        d.kip == Kip.UYGULAMALAR_ACIKKEN -> d.ilgiliOnde?.let { "Dinleniyor ($it açık)" }
            ?: "Dinlenmiyor: ilgili uygulama (Harita, Kanallarım, Notlar, Dosya Okuyucu) açık değil"
        d.kip == Kip.YALNIZ_ELLE -> "Dinlenmiyor: yalnız elle (bildirimdeki “Dinle / bırak” ya da aşağıdaki anahtar)"
        d.navigasyonda -> "Dinleniyor (navigasyon)"
        d.uygulamaIstiyor -> "Dinleniyor (bağlı uygulama istedi)"
        aracta(d.bluetoothAdlari, d.aracAdi) -> "Dinleniyor (araçta: ${d.bluetoothAdlari.joinToString()})"
        d.bluetoothAdlari.isNotEmpty() -> "Dinlenmiyor: bağlı Bluetooth (${d.bluetoothAdlari.joinToString()}) araç adıyla eşleşmiyor"
        else -> "Dinlenmiyor: araçta değil (Bluetooth yok). Elle açın ya da “Her zaman” seçin"
    }
}

package com.ses.asistan.ses

/**
 * Giriş ve çıkış parçalarının paylaştığı anlık durum (süreç içi; Android'e bağlı değil).
 *
 * Mikrofonu aynı anda tek kayıt alır: uyanma kaydı ([uyanmaKaydiAcik], WakeService) açıkken Google tanıyıcısı sessizlik
 * duyar. Tanıyıcı dinlemek isteyince [tanimaIstiyor] konur, uyanma döngüsü kaydı durdurur, tanıyıcı ancak ondan sonra
 * açılır (Harita `WakeWord.stop` + `Ears.listen` sırası).
 */
object SesDurumu {
    /** Google tanıyıcısı mikrofonu istiyor ya da dinliyor (komut, dikte). */
    @Volatile var tanimaIstiyor = false

    /** Uyanma döngüsünün AudioRecord'u kayıtta. */
    @Volatile var uyanmaKaydiAcik = false

    /** Asistan konuşuyor: uyanma kendi sesini duymasın (Harita `WakeWord.deaf`). */
    @Volatile var konusuyor = false

    /** Son duyulan uyanma adı ([WakeGrammar.NAMES]); yönlendirme Faz 2'de bunu okur. */
    @Volatile var sonUyanmaAdi: String? = null

    /** Bağlı uygulama navigasyonda (Faz 3: Harita `DURUM`); K3 politikasında "araçta" sayılır. */
    @Volatile var navigasyonda = false
}

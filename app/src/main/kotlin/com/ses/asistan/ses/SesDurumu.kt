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

    /** Son duyulan uyanma adı ([WakeGrammar.NAMES]); yönlendirme [uyanmaAdiAl] ile bir kez okur. */
    @Volatile var sonUyanmaAdi: String? = null
        set(v) { field = v; uyanmaAni = System.nanoTime() / 1_000_000 }
    @Volatile private var uyanmaAni = 0L

    /**
     * Uyanmadan sonraki ilk komutun yönlendirmesi için duyulan ad; bir kez verilir. Tanıyıcı hiçbir şey duymadıysa
     * eski ad [UYANMA_GECERLI_MS] sonra geçersizdir (sonraki elle dinleme yanlış uygulamaya gitmesin).
     */
    fun uyanmaAdiAl(): String? {
        val ad = uyanmaAdi()
        sonUyanmaAdi = null
        return ad
    }

    /** Uyanma adı hâlâ geçerli mi (okumadan; uyanma servisinin ekran açıp açmama kararı). */
    fun uyanmaAdi(): String? = sonUyanmaAdi?.takeIf { System.nanoTime() / 1_000_000 - uyanmaAni <= UYANMA_GECERLI_MS }

    private const val UYANMA_GECERLI_MS = 20_000L

    /** Bağlı uygulama navigasyonda (Harita `DURUM`); K3 politikasında "araçta" sayılır. */
    @Volatile var navigasyonda = false

    /** Bağlı uygulamanın kendi ayarı uyanma dinlemesi istiyor (Harita "her zaman" kipi, ekrandayken). */
    @Volatile var uygulamaUyanmaIstiyor = false
}

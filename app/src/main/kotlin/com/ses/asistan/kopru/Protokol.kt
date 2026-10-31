package com.ses.asistan.kopru

/**
 * Köprü protokolü sabitleri (sürüm 1). Sözleşmenin kendisi depodaki `PROTOKOL.md`'dedir; bağlı uygulamalar bu dosyayı
 * kopyalamaz, sabitleri belgeden kendi kodlarına yazar (GPL: kod paylaşılmaz, bağlantı süreçler arası).
 *
 * Kanal: [android.os.Messenger] (bağlı servis). İleti türü `Message.what`, alanlar `Message.data` (Bundle).
 */
object Protokol {
    const val SURUM = 1

    const val ASISTAN_PAKET = "com.ses.asistan"
    /** Asistanın ses servisi: uygulamalar buna bağlanır ([SesServisi]). */
    const val EYLEM_SES = "com.ses.asistan.SES"
    /** Uygulamanın köprü servisi: asistan buna bağlanır (uygulama kapalıyken metin devri). */
    const val EYLEM_KOPRU = "com.ses.asistan.KOPRU"
    /** Köprü servisindeki `meta-data` adı → `@xml/ses_yetenekler`. */
    const val META_YETENEKLER = "com.ses.asistan.yetenekler"

    // ------------------------------------------------------------------ uygulama → asistan
    /** Kayıt; `replyTo` uygulamanın Messenger'ı. Cevap [HOSGELDIN]. */
    const val MERHABA = 1
    /** [METIN_ALANI], [TUR], [KIMLIK], [ISARET]. Okununca ya da atılınca [SOYLENDI]. */
    const val SOYLE = 2
    /** Bu uygulamanın bekleyen ve süren seslerini keser; [TUR] verilirse yalnız o sınıf. */
    const val SUSTUR = 3
    /** Tek komut dinle; duyulan [METIN] olarak yalnız bu uygulamaya gelir, duyulmazsa [DUYULMADI]. */
    const val DINLE = 4
    /** Kesintisiz dikte: [KIMLIK], [BASLANGIC]. Bitince [DIKTE_SONUC]. */
    const val DIKTE = 5
    /** Durum bildirimi: [ANAHTAR], [DEGER] (Boolean). Anahtarlar [D_NAVIGASYON], [D_ONDE], [D_UYANMA]. */
    const val DURUM = 6
    /** [PENDING_INTENT]'i asistan başlatır (arka plandan ekran açma; Android 14+ izin açıkça verilir). */
    const val AC = 7
    /** Dinleme ya da dikte sürüyorsa bırakılır (uygulama kendi kartını kapattı). */
    const val DINLEME_IPTAL = 8

    // ------------------------------------------------------------------ asistan → uygulama
    /**
     * [SURUM_ALANI], [UYANMA_HAZIR]: asistan uyanma sözlerini dinleyebiliyor mu (true ise uygulama kendi dinlemesini
     * kapatır). Hazırlık değişince (model indi, uyanma kapatıldı) yeniden gelir.
     */
    const val HOSGELDIN = 101
    /** [KIMLIK]; [BAYAT] true ise okunamadan bayatladı (uygulama tazesini yeniden yollayabilir). */
    const val SOYLENDI = 102
    /** Kullanıcının söylediği: [METIN_ALANI], [UYANMA] (uyanma sözünden hemen sonra mı: ilk hece kaçmış olabilir). */
    const val METIN = 103
    const val DUYULMADI = 104
    /** [KIMLIK], [METIN_ALANI] (null: iptal ya da hiçbir şey söylenmedi). */
    const val DIKTE_SONUC = 105
    /** Dinlerken canlı metin: [METIN_ALANI] (uygulama kendi kartında gösterir). */
    const val KISMI = 106

    // ------------------------------------------------------------------ alanlar
    const val SURUM_ALANI = "surum"
    const val METIN_ALANI = "metin"
    const val TUR = "tur"
    const val KIMLIK = "kimlik"
    /** UYARI sınıfında ses işareti çalınsın mı (uygulama kendi işaretini çalıyorsa false). Varsayılan true. */
    const val ISARET = "isaret"
    const val BAYAT = "bayat"
    const val UYANMA = "uyanma"
    const val UYANMA_HAZIR = "uyanma_hazir"
    const val BASLANGIC = "baslangic"
    const val ANAHTAR = "anahtar"
    const val DEGER = "deger"
    const val PENDING_INTENT = "pi"

    // ------------------------------------------------------------------ ses sınıfları (SpeechQueue.Kind)
    const val T_TALIMAT = "talimat"
    const val T_UYARI = "uyari"
    const val T_CEVAP = "cevap"
    const val T_BILGI = "bilgi"

    // ------------------------------------------------------------------ durum anahtarları
    /** Uygulama navigasyonda: K3 politikasında "araçta" sayılır, `devir="navigasyonda"` metin devrini açar. */
    const val D_NAVIGASYON = "navigasyon"
    /** Uygulama ekranda: `devir="onde"` metin devrini açar. */
    const val D_ONDE = "onde"
    /** Uygulamanın kendi ayarı şimdi uyanma dinlemesi istiyor (Harita "her zaman" kipi, ekrandayken). */
    const val D_UYANMA = "uyanma"
}

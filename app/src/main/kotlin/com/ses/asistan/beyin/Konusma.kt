package com.ses.asistan.beyin

/**
 * Asistanın kısa belleği (Harita `Conversation`'dan birebir): son birkaç söz ve cevap. Gemini'ye geçmiş olarak
 * gönderilir; "oraya git", "onu durak ekle", "peki ikincisi?" gibi bağlama dayanan cümleler anlaşılır. [BOSTA_MS]
 * sessizlikten sonra konu kapanmış sayılır (eski bağlam yeni isteği yanıltmasın). Saf sınıf (bkz. KonusmaTest).
 */
class Konusma(private val simdi: () -> Long = System::currentTimeMillis) {
    class Tur(val soz: String, val cevap: String)

    private val turlar = ArrayDeque<Tur>()
    private var son = 0L

    fun ekle(soz: String, cevap: String) {
        if (soz.isBlank()) return
        bayatla()
        turlar.addLast(Tur(soz.trim(), cevap.trim()))
        while (turlar.size > EN_COK_TUR) turlar.removeFirst()
        son = simdi()
    }

    /** Cevabı sonradan gelen (metin devredilen uygulamanın `SÖYLE` ile verdiği) son söz için. */
    fun cevap(metin: String) {
        val t = turlar.removeLastOrNull() ?: return
        turlar.addLast(Tur(t.soz, if (t.cevap.isEmpty()) metin.trim() else "${t.cevap} ${metin.trim()}".take(300)))
        son = simdi()
    }

    fun gecmis(): List<Tur> { bayatla(); return turlar.toList() }

    fun temizle() = turlar.clear()

    private fun bayatla() { if (turlar.isNotEmpty() && simdi() - son > BOSTA_MS) turlar.clear() }

    companion object {
        const val EN_COK_TUR = 6
        const val BOSTA_MS = 3 * 60_000L
    }
}

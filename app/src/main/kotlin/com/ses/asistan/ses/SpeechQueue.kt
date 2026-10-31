package com.ses.asistan.ses

/**
 * Asistanın tek ses kuyruğu (Harita `SpeechQueue`'dan taşındı, kuralları birebir). Bir cümle sürerken gelen ikincisi
 * kesmez, sıraya girer. Harita'da eskiden kuyruk düz metindi: radar cümlesi sürerken gelen "300 metre sonra sağa" bir
 * sonraki cümleden sonra, araç çoktan ilerlemişken okunuyordu.
 *  - Dönüş talimatı ([Kind.TALIMAT]) hepsinin önüne geçer; yeni talimat eskisinin yerini alır (eskisi artık geçersiz).
 *  - Okunacağı an [STALE_MS]'den eskiyse talimat [Item.again] ile o anki mesafeyle yeniden üretilir (null: atlanır).
 *  - Uyarı ve bilgi [INFO_STALE_MS]'den eskiyse atlanır. Asistanın cevabı ([Kind.CEVAP]) bayatlamaz.
 *  - Öncelik (PLAN §1): talimat > uyarı > cevap > bilgi; aynı sınıfta geliş sırası.
 *  - Okunmadan çıkan her öğe (aynısı sırada, yerini yenisi aldı, taşma, bayat, temizlendi) [onDrop] ile bildirilir:
 *    bağlı uygulama ([Item.kaynak]) her sesinin sonucunu öğrenir, kendi zinciri askıda kalmaz.
 */
class SpeechQueue(private val max: Int = 3, private val onDrop: (Item, Boolean) -> Unit = { _, _ -> }) {
    /** Öncelik sırasıyla (küçük önce). */
    enum class Kind { TALIMAT, UYARI, CEVAP, BILGI }

    /**
     * [kaynak]: söyleten bağlı uygulama (null: asistanın kendisi). [isaret]: uyarıdan önce ses işareti çalınsın mı.
     * [bitti]: okununca ya da atılınca bir kez (true: bayatladığı için okunmadı).
     */
    class Item(
        val text: String, val kind: Kind, val atMs: Long, val again: (() -> String?)? = null,
        val kaynak: String? = null, val isaret: Boolean = true, val bitti: ((Boolean) -> Unit)? = null,
    ) {
        /** Harita'daki biçim: [turn] talimat mı, değilse uyarı. */
        constructor(text: String, turn: Boolean, atMs: Long, again: (() -> String?)? = null) :
            this(text, if (turn) Kind.TALIMAT else Kind.UYARI, atMs, again)

        val turn get() = kind == Kind.TALIMAT
    }

    private val items = ArrayDeque<Item>()

    val size get() = items.size

    @Synchronized
    fun push(it: Item) {
        if (items.any { o -> o.text == it.text }) { onDrop(it, false); return }
        if (it.turn) {
            removeWhere(false) { o -> o.turn }
            items.addFirst(it)
        } else {
            // aynı ya da daha önemli sınıfın arkasına
            val at = items.indexOfFirst { o -> o.kind > it.kind }
            if (at < 0) items.addLast(it) else items.add(at, it)
        }
        while (items.size > max) onDrop(items.removeLast(), false)
    }

    private fun removeWhere(stale: Boolean, p: (Item) -> Boolean) {
        val out = items.filter(p)
        if (out.isEmpty()) return
        items.removeAll(p)
        out.forEach { onDrop(it, stale) }
    }

    /** Sıradaki okunacak öğe; bayat olanlar yeniden üretilir ya da atlanır. */
    @Synchronized
    fun nextItem(now: Long): Item? {
        while (true) {
            val it = items.removeFirstOrNull() ?: return null
            val age = now - it.atMs
            when (it.kind) {
                Kind.TALIMAT -> {
                    if (age <= STALE_MS) return it
                    val fresh = it.again?.invoke()
                    if (!fresh.isNullOrBlank()) return Item(fresh, Kind.TALIMAT, now, it.again, it.kaynak, it.isaret, it.bitti)
                    onDrop(it, true)
                }
                Kind.CEVAP -> return it
                Kind.UYARI, Kind.BILGI -> if (age <= INFO_STALE_MS) return it else onDrop(it, true)
            }
        }
    }

    /** Sıradaki okunacak metin (Harita'daki arayüz). */
    fun next(now: Long): String? = nextItem(now)?.text

    /** Bir sınıfın bekleyenlerini atar (ör. kullanıcı asistanı susturunca yalnız cevaplar). */
    @Synchronized
    fun drop(kind: Kind) = removeWhere(false) { it.kind == kind }

    /** Bir uygulamanın bekleyenlerini atar ([kind] verilirse yalnız o sınıfı). */
    @Synchronized
    fun drop(kaynak: String, kind: Kind?) = removeWhere(false) { it.kaynak == kaynak && (kind == null || it.kind == kind) }

    @Synchronized
    fun clear() = removeWhere(false) { true }

    companion object {
        const val STALE_MS = 4_000L
        const val INFO_STALE_MS = 15_000L
    }
}

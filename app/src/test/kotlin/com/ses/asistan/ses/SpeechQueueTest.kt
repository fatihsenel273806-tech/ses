package com.ses.asistan.ses

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpeechQueueTest {
    @Test fun turnJumpsAheadAndReplacesOlderTurn() {
        val q = SpeechQueue()
        q.push(SpeechQueue.Item("500 metre ileride hız radarı.", false, 0))
        q.push(SpeechQueue.Item("300 metre sonra sağa dönün", true, 0))
        q.push(SpeechQueue.Item("100 metre sonra sağa dönün", true, 0))
        assertEquals(2, q.size)
        assertEquals("100 metre sonra sağa dönün", q.next(1000))
        assertEquals("500 metre ileride hız radarı.", q.next(1000))
        assertNull(q.next(1000))
    }

    @Test fun staleTurnIsRestatedOrDropped() {
        val q = SpeechQueue()
        q.push(SpeechQueue.Item("300 metre sonra sağa", true, 0) { "100 metre sonra sağa" })
        assertEquals("100 metre sonra sağa", q.next(SpeechQueue.STALE_MS + 1))
        q.push(SpeechQueue.Item("300 metre sonra sola", true, 0) { null })
        q.push(SpeechQueue.Item("eski uyarı", false, 0))
        // talimat geçildi (null), uyarı da bayat: hiçbiri okunmaz
        assertNull(q.next(SpeechQueue.INFO_STALE_MS + 1))
    }

    // ---- asistan: öncelik sınıfları (PLAN §1)

    @Test fun oncelikTalimatUyariCevapBilgi() {
        val q = SpeechQueue(max = 6)
        q.push(SpeechQueue.Item("bilgi", SpeechQueue.Kind.BILGI, 0))
        q.push(SpeechQueue.Item("cevap", SpeechQueue.Kind.CEVAP, 0))
        q.push(SpeechQueue.Item("uyarı 1", SpeechQueue.Kind.UYARI, 0))
        q.push(SpeechQueue.Item("talimat", SpeechQueue.Kind.TALIMAT, 0))
        q.push(SpeechQueue.Item("uyarı 2", SpeechQueue.Kind.UYARI, 0))
        assertEquals(listOf("talimat", "uyarı 1", "uyarı 2", "cevap", "bilgi"), List(5) { q.next(0) })
    }

    @Test fun cevapBayatlamazBilgiBayatlar() {
        val q = SpeechQueue(max = 6)
        q.push(SpeechQueue.Item("eski bilgi", SpeechQueue.Kind.BILGI, 0))
        q.push(SpeechQueue.Item("uzun cevap", SpeechQueue.Kind.CEVAP, 0))
        assertEquals("uzun cevap", q.next(SpeechQueue.INFO_STALE_MS + 1))
        assertNull(q.next(SpeechQueue.INFO_STALE_MS + 1))
    }

    @Test fun tasmadaEnOnemsizAtilir() {
        val q = SpeechQueue(max = 2)
        q.push(SpeechQueue.Item("bilgi", SpeechQueue.Kind.BILGI, 0))
        q.push(SpeechQueue.Item("cevap", SpeechQueue.Kind.CEVAP, 0))
        q.push(SpeechQueue.Item("uyarı", SpeechQueue.Kind.UYARI, 0))
        assertEquals(listOf("uyarı", "cevap", null), List(3) { q.next(0) })
    }

    @Test fun sinifTemizlenir() {
        val q = SpeechQueue(max = 6)
        q.push(SpeechQueue.Item("cevap", SpeechQueue.Kind.CEVAP, 0))
        q.push(SpeechQueue.Item("uyarı", SpeechQueue.Kind.UYARI, 0))
        q.drop(SpeechQueue.Kind.CEVAP)
        assertEquals(1, q.size)
    }

    // ---- köprü: okunmadan çıkan her öğe bildirilir (bağlı uygulamanın zinciri askıda kalmasın)

    @Test fun atilanlarBildirilir() {
        val atilan = ArrayList<Pair<String, Boolean>>()
        val q = SpeechQueue(max = 2) { it, bayat -> atilan += it.text to bayat }
        q.push(SpeechQueue.Item("300 metre sonra sağa", SpeechQueue.Kind.TALIMAT, 0))
        q.push(SpeechQueue.Item("300 metre sonra sağa", SpeechQueue.Kind.TALIMAT, 0)) // aynısı sırada
        q.push(SpeechQueue.Item("100 metre sonra sağa", SpeechQueue.Kind.TALIMAT, 0)) // eskisinin yerini alır
        q.push(SpeechQueue.Item("cevap", SpeechQueue.Kind.CEVAP, 0))
        q.push(SpeechQueue.Item("bilgi", SpeechQueue.Kind.BILGI, 0)) // taşma
        assertEquals(listOf("300 metre sonra sağa" to false, "300 metre sonra sağa" to false, "bilgi" to false), atilan)
        atilan.clear()
        // bağlı uygulamanın bayat talimatı yeniden üretilmez (again yok): bayat diye bildirilir, uygulama tazesini yollar
        assertEquals("cevap", q.next(SpeechQueue.STALE_MS + 1))
        assertEquals(listOf("100 metre sonra sağa" to true), atilan)
    }

    @Test fun kaynagaGoreSusturulur() {
        val atilan = ArrayList<String>()
        val q = SpeechQueue(max = 6) { it, _ -> atilan += it.text }
        q.push(SpeechQueue.Item("harita uyarı", SpeechQueue.Kind.UYARI, 0, kaynak = "com.harita.ankara"))
        q.push(SpeechQueue.Item("harita bilgi", SpeechQueue.Kind.BILGI, 0, kaynak = "com.harita.ankara"))
        q.push(SpeechQueue.Item("asistan cevap", SpeechQueue.Kind.CEVAP, 0))
        q.drop("com.harita.ankara", SpeechQueue.Kind.BILGI)
        assertEquals(listOf("harita bilgi"), atilan)
        q.drop("com.harita.ankara", null)
        assertEquals(listOf("harita bilgi", "harita uyarı"), atilan)
        assertEquals("asistan cevap", q.next(0))
    }
}

package com.ses.asistan.beceri

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SecimTest {
    private val liste = listOf("Uzay belgeseli", "Osmanlı Tarihi 3. Bölüm", "Yemek tarifleri")

    @Test fun sirayla() {
        assertEquals(1, Secim.sec("ikinci", liste))
        assertEquals(0, Secim.sec("ilki", liste))
        assertEquals(2, Secim.sec("sonuncusu", liste))
        assertEquals(2, Secim.sec("3", liste))
        // listede olmayan sıra
        assertNull(Secim.sec("beşinci", liste))
    }

    @Test fun adiyla() {
        assertEquals(1, Secim.sec("osmanlı olanı", liste))
        assertEquals(2, Secim.sec("yemek tarifleri", liste))
        assertNull(Secim.sec("futbol", liste))
    }

    @Test fun okunus() {
        assertEquals("Harita listem: 1, A. 2, B. Hangisi?", Secim.oku("Harita listem", listOf("A", "B")))
    }
}

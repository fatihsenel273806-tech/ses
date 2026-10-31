package com.ses.asistan.ses

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WakeGrammarTest {
    private fun words(vararg w: Pair<String, Double>) =
        w.joinToString(",", "[", "]") { (word, conf) -> """{"word":"$word","conf":$conf}""" }

    private fun final(vararg w: Pair<String, Double>) = """{"result":${words(*w)},"text":""}"""
    private fun partial(vararg w: Pair<String, Double>) = """{"partial":"","partial_result":${words(*w)}}"""

    @Test fun heyHaritaFinal() {
        assertTrue(WakeGrammar.Decider().feed(final("hey" to 0.9, "harita" to 0.95), final = true))
        assertTrue(WakeGrammar.Decider().feed(final("ev" to 0.7, "ey" to 0.8, "harita" to 0.7, "eve" to 1.0), final = true))
    }

    @Test fun haritaAloneOrRivalDoesNotWake() {
        val d = WakeGrammar.Decider()
        assertFalse(d.feed(final("harita" to 1.0), final = true)) // videoda tek başına "harita" geçebilir
        assertFalse(d.feed(final("çok" to 1.0, "harita" to 1.0), final = true))
        assertFalse(d.feed(final("hey" to 1.0, "harika" to 1.0), final = true))
        assertFalse(d.feed(final("hey" to 1.0, "harita" to 0.4), final = true)) // düşük güven
    }

    @Test fun partialNeedsTwoInARow() {
        val d = WakeGrammar.Decider()
        assertFalse(d.feed(partial("hey" to 1.0, "harita" to 0.9), final = false))
        assertTrue(d.feed(partial("hey" to 1.0, "harita" to 0.9), final = false))
        val e = WakeGrammar.Decider()
        assertFalse(e.feed(partial("hey" to 1.0, "harita" to 0.9), final = false))
        assertFalse(e.feed(partial("hey" to 1.0, "ahmet" to 0.9), final = false)) // dizi bozuldu
        assertFalse(e.feed(partial("hey" to 1.0, "harita" to 0.9), final = false))
    }

    @Test fun badJsonIsIgnored() {
        assertFalse(WakeGrammar.Decider().feed("", final = true))
        assertFalse(WakeGrammar.Decider().feed("""{"partial":"hey harita"}""", final = false))
    }

    @Test fun grammarHasPhrasesAndRivals() {
        val g = JSONArray(WakeGrammar.GRAMMAR)
        val all = (0 until g.length()).map { g.getString(it) }.toSet()
        assertTrue("hey harita" in all && "[unk]" in all && "harika" in all && "çarşıya" in all)
        assertFalse(all.any { it.isBlank() })
    }

    // ---- asistan: birden çok uyanma adı (PLAN D4)

    @Test fun hangiAdDuyulduBildirilir() {
        val d = WakeGrammar.Decider()
        assertTrue(d.feed(final("hey" to 0.9, "asistan" to 0.9), final = true))
        assertEquals("asistan", d.name)
        assertTrue(d.feed(final("ey" to 0.9, "notlar" to 0.8), final = true))
        assertEquals("notlar", d.name)
        assertTrue(d.feed(final("hey" to 0.9, "kanallar" to 0.8), final = true))
        assertEquals("kanallar", d.name)
    }

    @Test fun yeniAdlarYalnizCumleBitinceUyanir() {
        val d = WakeGrammar.Decider()
        // kısmi sonuçta "hey notlar" ne kadar sürse de uyanmaz (ölçüm: kısmi sonuç sonradan düzeliyor)
        repeat(5) { assertFalse(d.feed(partial("hey" to 1.0, "notlar" to 0.9), final = false)) }
        assertTrue(d.feed(final("hey" to 1.0, "notlar" to 0.9), final = true))
        // araya giren kısmi "hey notlar" haritanın sayacını bozar
        val e = WakeGrammar.Decider()
        assertFalse(e.feed(partial("hey" to 1.0, "harita" to 0.9), final = false))
        assertFalse(e.feed(partial("hey" to 1.0, "notlar" to 0.9), final = false))
        assertFalse(e.feed(partial("hey" to 1.0, "harita" to 0.9), final = false))
        assertTrue(e.feed(partial("hey" to 1.0, "harita" to 0.9), final = false))
        assertEquals("harita", e.name)
    }

    @Test fun yeniAdlarTekBasinaYaDaRakipleUyanmaz() {
        val d = WakeGrammar.Decider()
        assertFalse(d.feed(final("asistan" to 1.0), final = true))
        assertFalse(d.feed(final("hey" to 1.0, "hasta" to 1.0), final = true))
        assertFalse(d.feed(final("hey" to 1.0, "not" to 1.0), final = true))
        assertFalse(d.feed(final("hey" to 1.0, "kanal" to 1.0), final = true))
    }

    @Test fun dilbilgisindeHerAdIcinIkiSozVar() {
        val g = JSONArray(WakeGrammar.GRAMMAR)
        val all = (0 until g.length()).map { g.getString(it) }.toSet()
        for (n in WakeGrammar.NAMES) assertTrue(n, "hey $n" in all && "ey $n" in all)
        // Türkçe harfler kaçışsız gider ("\u0131" yazılırsa Vosk kelimeyi tanımaz)
        assertFalse(WakeGrammar.GRAMMAR.contains("\\u"))
    }
}


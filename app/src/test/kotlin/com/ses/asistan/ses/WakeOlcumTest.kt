package com.ses.asistan.ses

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Uyanma ölçümü: gerçek Vosk çıktıları (tool/uyanma_olcum.py; gTTS sesi, hız, gürültü, arkada video konuşması)
 * [WakeGrammar.Decider]'a WakeWord'deki sırayla verilir. Olumsuz örneklerde hiç tetiklenme olmamalı; olumlu
 * örneklerde doğru ad duyulmalı. Dilbilgisi değişirse fikstür bayatlar: önce `UYANMA_YAZ=1` ile dilbilgisi
 * dosyası yazılır, sonra betik yeniden çalıştırılır.
 */
class WakeOlcumTest {
    private val dir = File("src/test/resources/uyanma")

    private class Sonuc(val metin: String, val varyant: String, val beklenen: String?, val duyulan: String?)

    private fun olc(): List<Sonuc> = File(dir, "olcum.jsonl").readLines().filter { it.isNotBlank() }.map { line ->
        val o = JSONObject(line)
        val d = WakeGrammar.Decider()
        var duyulan: String? = null
        val olaylar: JSONArray = o.getJSONArray("olaylar")
        loop@ for (i in 0 until olaylar.length()) {
            val e = olaylar.getJSONArray(i)
            repeat(e.getInt(2)) {
                if (duyulan == null && d.feed(e.getString(1), final = e.getBoolean(0))) duyulan = d.name
            }
            if (duyulan != null) break@loop
        }
        Sonuc(o.getString("metin"), o.getString("varyant"), o.optString("beklenen").ifEmpty { null }, duyulan)
    }

    @Test fun dilbilgisiFikstureUyar() {
        val f = File(dir, "dilbilgisi.json")
        if (System.getenv("UYANMA_YAZ") != null) f.writeText(WakeGrammar.GRAMMAR + "\n")
        assertEquals("dilbilgisi değişti: fikstürü yeniden üretin (bkz. sınıf açıklaması)", WakeGrammar.GRAMMAR, f.readText().trim())
    }

    /**
     * Bilinen sınırlar (2026-10-31 ölçümü, 830 olumsuz kayıt): hızlı söylenen "hey harika" → harita (Harita'daki
     * kural, birebir) ve gürültüde "ey nota" → notlar. Başka yanlış tetiklenme çıkarsa test kırılır.
     */
    private val BILINEN = setOf("hey harika (hizli)", "ey nota (gurultu)")

    @Test fun olumsuzOrneklerdeYanlisTetiklenmeYok() {
        val yanlis = olc().filter { it.beklenen == null && it.duyulan != null }.map { "${it.metin} (${it.varyant})" }
        val yeni = yanlis.filter { it !in BILINEN }
        assertTrue("yanlış tetiklenme: " + yeni.joinToString(), yeni.isEmpty())
    }

    @Test fun olumluOrneklerdeDogruAd() {
        val sonuc = olc().filter { it.beklenen != null }
        // başka ada uyanmak hiç olmamalı
        val karisan = sonuc.filter { it.duyulan != null && it.duyulan != it.beklenen }
        assertTrue("yanlış ad: " + karisan.joinToString { "${it.metin} (${it.varyant}) → ${it.duyulan}" }, karisan.isEmpty())
        val tablo = StringBuilder("uyanma ölçümü (duyulan/örnek):\n")
        for (ad in WakeGrammar.NAMES) {
            val satir = sonuc.filter { it.beklenen == ad }.groupBy { it.varyant }
                .map { (v, l) -> "$v ${l.count { it.duyulan == ad }}/${l.size}" }
            tablo.append("  $ad: ").append(satir.joinToString(", ")).append('\n')
            // temiz seste her ad duyulmalı
            val temiz = sonuc.filter { it.beklenen == ad && it.varyant == "temiz" }
            assertTrue("$ad temiz seste kaçtı", temiz.all { it.duyulan == ad })
        }
        val olumsuz = olc().count { it.beklenen == null }
        println(tablo.append("  olumsuz örnek: $olumsuz"))
    }
}

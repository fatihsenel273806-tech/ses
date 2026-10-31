package com.ses.asistan.ses.tts

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Random
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Kotlin portunu orijinal PyTorch paketinin çıktılarıyla karşılaştırır.
 * Referans dosyaları (scripts/antalia_ref.py ile üretilir) ve ağırlıklar büyük olduğu için depoda yok;
 * ANTALIA_REF=<klasör> verilmezse test atlanır. Klasörde: acoustic.fp16.safetensors,
 * vocoder.fp16.safetensors, ref.json ve r{k}.noise / .mel / .wav / .edged / .final.
 */
class AntaliaRefTest {
    private val dir = System.getenv("ANTALIA_REF")?.let { File(it) }

    private fun floats(f: File): FloatArray {
        val bb = ByteBuffer.wrap(f.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
        return FloatArray(bb.remaining() / 4) { bb.getFloat() }
    }

    private fun relErr(a: FloatArray, b: FloatArray): Double {
        var num = 0.0
        var den = 0.0
        for (i in a.indices) {
            val d = (a[i] - b[i]).toDouble(); num += d * d; den += b[i].toDouble() * b[i]
        }
        return sqrt(num / den)
    }

    @Test
    fun matchesPytorchReference() {
        assumeTrue("ANTALIA_REF yok, atlanıyor", dir != null && File(dir, "ref.json").exists())
        val d = dir!!
        val model = AntaliaModel(
            SafeTensors.read(File(d, "acoustic.fp16.safetensors").readBytes()),
            SafeTensors.read(File(d, "vocoder.fp16.safetensors").readBytes()),
        )
        val tts = Antalia(model)
        val refs = JSONArray(File(d, "ref.json").readText())
        for (k in 0 until refs.length()) {
            val r: JSONObject = refs.getJSONObject(k)
            val text = r.getString("text")
            assertEquals(r.getString("model_text"), Text.toModelText(text))
            val chunk = r.getString("chunk")
            val ids = Text.encode(
                (if (chunk[0] in "öı") "ee, " else "") + chunk + " ee."
            )
            val want = r.getJSONArray("ids").let { a -> IntArray(a.length()) { a.getInt(it) } }
            assertArrayEquals(want, ids)

            val noise = floats(File(d, "r$k.noise"))
            val t0 = System.nanoTime()
            val ac = model.acoustic(ids, noise = noise)
            val t1 = System.nanoTime()
            val refMel = floats(File(d, "r$k.mel")) // (128, n)
            val n = r.getInt("n")
            assertEquals(n, ac.frames)
            val mel = FloatArray(128 * n) { i -> ac.mel[(i % n) * 128 + i / n] } // (T,128) → (128,T)
            val eMel = relErr(mel, refMel)
            val wav = model.vocode(ac.mel, ac.frames)
            val t2 = System.nanoTime()
            val eWav = relErr(wav, floats(File(d, "r$k.wav")))
            val edged = tts.chunk(chunk, Random(0), noise)
            val eEdge = relErr(edged, floats(File(d, "r$k.edged")))
            val fin = OutputChain(48000).process(edged)
            val refFin = floats(File(d, "r$k.final"))
            val eFin = relErr(fin, refFin)
            var maxAbs = 0f
            for (i in fin.indices) maxAbs = maxOf(maxAbs, abs(fin[i] - refFin[i]))
            println(
                "r$k: mel rel %.2e, wav rel %.2e, edged rel %.2e, final rel %.2e (max %.2e); acoustic %d ms, vocoder %d ms, ses %.2f s"
                    .format(eMel, eWav, eEdge, eFin, maxAbs, (t1 - t0) / 1_000_000, (t2 - t1) / 1_000_000, wav.size / 48000.0)
            )
            assertTrue("mel farkı $eMel", eMel < 1e-3)
            assertTrue("wav farkı $eWav", eWav < 1e-2)
            assertTrue("son çıktı farkı $eFin", eFin < 1e-2)
        }
        model.close()
    }
}

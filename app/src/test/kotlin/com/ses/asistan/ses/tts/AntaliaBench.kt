package com.ses.asistan.ses.tts

import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.Random

class AntaliaBench {
    @Test
    fun bench() {
        val d = System.getenv("ANTALIA_REF")?.let { File(it) }
        assumeTrue(d != null && System.getenv("ANTALIA_BENCH") != null)
        val model = AntaliaModel(
            SafeTensors.read(File(d, "acoustic.fp16.safetensors").readBytes()),
            SafeTensors.read(File(d, "vocoder.fp16.safetensors").readBytes()), threads = 4,
        )
        val tts = Antalia(model)
        val text = "Kızılay'a 12 dakika kaldı, öndeki ışıkta bekleyeceksiniz."
        repeat(2) { tts.say(text, 1) }
        for (steps in listOf(8, 4, 2)) {
            tts.steps = steps
            val t0 = System.nanoTime()
            val w = tts.say(text, 1)
            val ms = (System.nanoTime() - t0) / 1_000_000
            println("steps=$steps: $ms ms, ses ${"%.2f".format(w.size / 48000.0)} s, RTF ${"%.2f".format(ms / 1000.0 / (w.size / 48000.0))}")
            File(d, "kotlin_steps$steps.raw").writeBytes(java.nio.ByteBuffer.allocate(w.size * 4).order(java.nio.ByteOrder.LITTLE_ENDIAN).also { b -> w.forEach { b.putFloat(it) } }.array())
        }
        model.close()
    }
}

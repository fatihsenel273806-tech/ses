package com.ses.asistan.ses.tts

import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh

/**
 * Metin → 48 kHz ses. Orijinal Antalia.say() akışı: normalleştir, parçala, her parçayı
 * "ee." ekiyle (ve ö/ı ile başlıyorsa "ee, " önekiyle) sentezle, ekleri kes, parçalar arasına
 * sessizlik koy, çıkış EQ'su + de-esser + kazanç + yumuşak sınırlayıcıdan geçir.
 */
class Antalia(private val model: AntaliaModel) {
    var steps = 8
    var cfg = 2f
    var speed = 0.95f
    var temperature = 0.6f

    private val prefix = "ee, "
    private val suffix = " ee."
    private val prefixInitials = "öı"
    private val hop = 512

    /** Parçaları sırayla üretir (ilk cümle önce çalınabilsin diye). */
    fun stream(text: String, seed: Long = System.nanoTime(), out: (FloatArray) -> Unit) {
        val chunks = Text.split(Text.toModelText(text))
        val chain = OutputChain(SAMPLE_RATE)
        val rng = Random(seed)
        chunks.forEachIndexed { k, c ->
            var w = chunk(c, rng)
            if (k < chunks.size - 1) w = w + FloatArray(((if ((c.trimEnd().lastOrNull() ?: ' ') in ".?!") 0.12 else 0.06) * SAMPLE_RATE).toInt())
            out(chain.process(w))
        }
    }

    fun say(text: String, seed: Long = System.nanoTime()): FloatArray {
        val parts = ArrayList<FloatArray>()
        stream(text, seed) { parts.add(it) }
        val out = FloatArray(parts.sumOf { it.size })
        var o = 0
        for (p in parts) { System.arraycopy(p, 0, out, o, p.size); o += p.size }
        return out
    }

    /** Tek parça: önek/sonek ile sentez, sonra onların sesini kes (5 ms geçişle). */
    fun chunk(c: String, rng: Random, noise: FloatArray? = null): FloatArray {
        val pre = if (c.isNotEmpty() && c[0] in prefixInitials) prefix else ""
        val nPre = if (pre.isEmpty()) 0 else Text.encode(pre).size - 2
        val nSuf = Text.encode(suffix).size - 2
        val ids = Text.encode(pre + c + suffix)
        val ac = model.acoustic(ids, steps, cfg, speed, temperature, rng = rng, noise = noise)
        val wav = model.vocode(ac.mel, ac.frames)
        val cum = IntArray(ids.size)
        var s = 0
        for (j in ids.indices) { s += ac.dur[j]; cum[j] = s }
        val a = cum[nPre]
        val b = cum[ids.size - 2 - nSuf]
        val s0 = if (pre.isNotEmpty()) a * hop else 0
        val s1 = minOf(wav.size, b * hop)
        val w = wav.copyOfRange(s0, maxOf(s0, s1))
        val fade = (0.005 * SAMPLE_RATE).toInt()
        if (w.size > 2 * fade) {
            if (pre.isNotEmpty()) for (i in 0 until fade) w[i] *= i / (fade - 1f)
            for (i in 0 until fade) w[w.size - fade + i] *= 1f - i / (fade - 1f)
        }
        return w
    }

    companion object {
        const val SAMPLE_RATE = 48000
    }
}

/** post.OutputChain (48 kHz, sürüm 1.0.1 "clear" eğrisi). Durum parçalar arasında korunur. */
class OutputChain(fs: Int) {
    private val sections = listOf(
        highpass(80.0, 0.707, fs), lowshelf(200.0, -4.0, 0.707, fs), peaking(350.0, -2.5, 1.2, fs),
        peaking(3200.0, 3.0, 0.9, fs), highshelf(8000.0, 2.0, 0.707, fs),
    )
    private val state = Array(sections.size) { DoubleArray(4) }
    private val deess = DeEsser(fs, 5500.0, -34.0, 3.0)
    private val gain = Math.pow(10.0, 1.6 / 20)

    fun process(x: FloatArray): FloatArray {
        val y = DoubleArray(x.size) { x[it].toDouble() }
        sections.forEachIndexed { k, sec ->
            val st = state[k]
            val (b0, b1, b2, a1, a2) = sec
            for (n in y.indices) {
                val v = y[n]
                val o = b0 * v + b1 * st[0] + b2 * st[1] - a1 * st[2] - a2 * st[3]
                st[1] = st[0]; st[0] = v; st[3] = st[2]; st[2] = o
                y[n] = o
            }
        }
        val d = deess.process(y)
        val thr = Math.pow(10.0, -1.0 / 20)
        val ceil = Math.pow(10.0, -0.1 / 20)
        val room = ceil - thr
        return FloatArray(d.size) {
            val v = d[it] * gain
            val av = abs(v)
            (if (av > thr) sign(v) * (thr + room * tanh((av - thr) / room)) else v).toFloat()
        }
    }

    private data class Bq(val b0: Double, val b1: Double, val b2: Double, val a1: Double, val a2: Double)

    private fun norm(b0: Double, b1: Double, b2: Double, a0: Double, a1: Double, a2: Double) =
        Bq(b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0)

    private fun highpass(f0: Double, q: Double, fs: Int): Bq {
        val w0 = 2 * PI * f0 / fs; val al = sin(w0) / (2 * q); val c = cos(w0)
        return norm((1 + c) / 2, -(1 + c), (1 + c) / 2, 1 + al, -2 * c, 1 - al)
    }

    private fun lowshelf(f0: Double, db: Double, q: Double, fs: Int): Bq {
        val A = Math.pow(10.0, db / 40); val w0 = 2 * PI * f0 / fs; val al = sin(w0) / (2 * q); val c = cos(w0)
        val s = 2 * sqrt(A) * al
        return norm(
            A * ((A + 1) - (A - 1) * c + s), 2 * A * ((A - 1) - (A + 1) * c), A * ((A + 1) - (A - 1) * c - s),
            (A + 1) + (A - 1) * c + s, -2 * ((A - 1) + (A + 1) * c), (A + 1) + (A - 1) * c - s,
        )
    }

    private fun highshelf(f0: Double, db: Double, q: Double, fs: Int): Bq {
        val A = Math.pow(10.0, db / 40); val w0 = 2 * PI * f0 / fs; val al = sin(w0) / (2 * q); val c = cos(w0)
        val s = 2 * sqrt(A) * al
        return norm(
            A * ((A + 1) + (A - 1) * c + s), -2 * A * ((A - 1) + (A + 1) * c), A * ((A + 1) + (A - 1) * c - s),
            (A + 1) - (A - 1) * c + s, 2 * ((A - 1) - (A + 1) * c), (A + 1) - (A - 1) * c - s,
        )
    }

    private fun peaking(f0: Double, db: Double, q: Double, fs: Int): Bq {
        val A = Math.pow(10.0, db / 40); val w0 = 2 * PI * f0 / fs; val al = sin(w0) / (2 * q); val c = cos(w0)
        return norm(1 + al * A, -2 * c, 1 - al * A, 1 + al / A, -2 * c, 1 - al / A)
    }
}

/** post.DeEsser: doğrusal fazlı yüksek geçiren bant, 2 ms bloklarla izlenip yalnızca yüksekken kısılır. */
class DeEsser(fs: Int, f0: Double, thresholdDb: Double, private val ratio: Double, attackMs: Double = 1.5, releaseMs: Double = 60.0) {
    private val taps = if (fs >= 32000) 127 else 63
    private val delay = (taps - 1) / 2
    private val h = DoubleArray(taps).also { hp ->
        var sum = 0.0
        val lp = DoubleArray(taps) { i ->
            val n = i - (taps - 1) / 2.0
            val x = 2 * f0 / fs * n
            val sinc = if (x == 0.0) 1.0 else sin(PI * x) / (PI * x)
            val win = 0.5 - 0.5 * cos(2 * PI * i / (taps - 1))
            (2 * f0 / fs) * sinc * win
        }
        for (v in lp) sum += v
        for (i in 0 until taps) hp[i] = -lp[i] / sum
        hp[delay] += 1.0
    }
    private val hist = DoubleArray(taps - 1)
    private val line = DoubleArray(delay)
    private val thr = Math.pow(10.0, thresholdDb / 20)
    private val block = maxOf(1, (0.002 * fs).toInt())
    private val att = exp(-block / (attackMs * 1e-3 * fs))
    private val rel = exp(-block / (releaseMs * 1e-3 * fs))
    private var env = 0.0
    private var g = 1.0

    fun process(y: DoubleArray): DoubleArray {
        val n = y.size
        if (n == 0) return y
        val ext = DoubleArray(hist.size + n)
        System.arraycopy(hist, 0, ext, 0, hist.size)
        System.arraycopy(y, 0, ext, hist.size, n)
        val s = DoubleArray(n)
        for (i in 0 until n) {
            var acc = 0.0
            for (k in 0 until taps) acc += h[k] * ext[i + taps - 1 - k]
            s[i] = acc
        }
        System.arraycopy(ext, ext.size - hist.size, hist, 0, hist.size)
        val extL = DoubleArray(delay + n)
        System.arraycopy(line, 0, extL, 0, delay)
        System.arraycopy(y, 0, extL, delay, n)
        val delayed = extL.copyOfRange(0, n)
        System.arraycopy(extL, n, line, 0, delay)
        val out = DoubleArray(n)
        var i = 0
        while (i < n) {
            val m = minOf(block, n - i)
            var e = 0.0
            for (j in i until i + m) e += s[j] * s[j]
            val level = sqrt(e / m)
            val k = if (level > env) att else rel
            env = k * env + (1 - k) * level
            val target = if (env <= thr) 1.0 else Math.pow(thr / env, 1 - 1 / ratio)
            for (j in 0 until m) {
                val gj = if (m > 1) g + (target - g) * j / m else target
                out[i + j] = delayed[i + j] + (gj - 1.0) * s[i + j]
            }
            g = target
            i += m
        }
        return out
    }
}

package com.ses.asistan.ses.tts

import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Random
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Antalia-2 Mini (cloud0day3/antalia-mini, Apache-2.0) Türkçe TTS'in saf Kotlin çıkarımı.
 * PyTorch/ONNX gerekmez; Hugging Face'teki safetensors ağırlıkları doğrudan okunur.
 *
 *   karakterler → TextEncoder → süre tahmini → kare koşulları
 *   gürültü → Generator (flow matching, K adım, CFG) → 128 bant log-mel (93.75 Hz)
 *   mel → Vocoder (ConvNeXt + iSTFT) → 48 kHz ses
 *
 * Mimari orijinal paketin model.py / modules.py / vocoder.py dosyalarının birebir karşılığıdır;
 * doğruluğu PyTorch referans çıktılarıyla birim testte karşılaştırılır.
 */
class AntaliaModel(acoustic: Map<String, FloatArray>, vocoder: Map<String, FloatArray>, threads: Int = 4) {
    private val a = acoustic
    private val v = vocoder
    private val nn = NN(threads)

    // config.json (sürüm 1.0.1) değerleri
    private val dText = 192
    private val heads = 4
    private val nMels = 128
    private val stack = 2
    private val dGen = 192
    private val dTime = 128
    private val genBlocks = 5
    private val melMean = -5.9f
    private val melStd = 2.6f
    private val maxDur = 60

    class Acoustic(val mel: FloatArray, val frames: Int, val dur: IntArray)

    /** ids: BOS + semboller + EOS. noise: (frames/2)×256 birim Gauss; null ise rng ile üretilir. */
    fun acoustic(
        ids: IntArray, steps: Int = 8, cfg: Float = 2f, speed: Float = 0.95f, temperature: Float = 0.6f,
        gemStretch: Float = 1.3f, rng: Random = Random(), noise: FloatArray? = null,
    ): Acoustic {
        val L = ids.size
        val h = encoder(ids)
        val logd = duration(h, L)
        if (gemStretch != 1f) {
            val g = ln(gemStretch)
            val sp = Text.SPACE
            for (j in 2 until L - 1) {
                if (ids[j] == ids[j - 1] && ids[j] != sp) logd[j] += g
                else if (ids[j - 1] == sp && ids[j] == ids[j - 2]) logd[j - 1] += g
            }
        }
        val dur = IntArray(L) { j -> Math.rint(exp(logd[j].toDouble()) / speed).toInt().coerceIn(1, maxDur) }
        var total = dur.sum()
        total = (total + stack - 1) / stack * stack
        val T = total
        val N = T / stack

        // expand: her sembolün vektörü süresi kadar tekrarlanır (fazla kare son sembolü alır)
        val cond = FloatArray(T * dText)
        var f = 0
        for (j in 0 until L) for (r in 0 until dur[j]) {
            if (f < T) System.arraycopy(h, j * dText, cond, f * dText, dText)
            f++
        }
        while (f < T) {
            System.arraycopy(h, (L - 1) * dText, cond, f * dText, dText); f++
        }
        val ctok = prepCond(cond, T)
        val guided = cfg != 1f
        val cnull = if (guided) {
            val nc = a.getValue("generator.null_cond")
            val c0 = FloatArray(T * dText)
            for (t in 0 until T) System.arraycopy(nc, 0, c0, t * dText, dText)
            prepCond(c0, T)
        } else null

        val width = nMels * stack
        val x = FloatArray(N * width)
        for (i in x.indices) x[i] = (noise?.get(i) ?: rng.nextGaussian().toFloat()) * temperature
        val dt = 1f / steps
        val rope = nn.rope(N, dGen / heads)
        for (i in 0 until steps) {
            val t = i * dt
            val d = if (steps > 1) dt else 1f
            val mod = timeMod(t, d)
            val vc = generator(x, N, ctok, mod, rope)
            if (cnull != null) {
                val vu = generator(x, N, cnull, mod, rope)
                for (k in x.indices) x[k] += (vc[k] + (cfg - 1f) * (vc[k] - vu[k])) * dt
            } else {
                for (k in x.indices) x[k] += vc[k] * dt
            }
        }
        // unstack: (N, 2·128) bellekte zaten (2N, 128) ile aynı; denorm
        for (k in x.indices) x[k] = x[k] * melStd + melMean
        return Acoustic(x, T, dur)
    }

    // ------------------------------------------------------------------ text encoder
    private fun encoder(ids: IntArray): FloatArray {
        val L = ids.size
        val emb = a.getValue("encoder.emb.weight")
        var x = FloatArray(L * dText)
        for (j in 0 until L) System.arraycopy(emb, ids[j] * dText, x, j * dText, dText)
        for (k in 0 until 3) x = convNext(a, "encoder.convs.$k", x, L, dText, 384, 5)
        val rope = nn.rope(L, dText / heads)
        val p = "encoder.attn.0"
        val n1 = nn.layerNorm(x, L, dText, a["$p.n1.weight"], a["$p.n1.bias"])
        nn.addInPlace(x, attention(a, "$p.attn", n1, L, dText, rope))
        val n2 = nn.layerNorm(x, L, dText, a["$p.n2.weight"], a["$p.n2.bias"])
        nn.addInPlace(x, swiglu(a, "$p.ff", n2, L, dText, 384))
        return nn.layerNorm(x, L, dText, a["encoder.norm.weight"], a["encoder.norm.bias"])
    }

    private fun duration(h: FloatArray, L: Int): FloatArray {
        var x = nn.linear(h, L, dText, a.getValue("duration.inp.weight"), a["duration.inp.bias"], 128)
        for (k in 0 until 3) {
            x = nn.conv1d(x, L, 128, a.getValue("duration.convs.$k.weight"), a["duration.convs.$k.bias"], 128, 3)
            x = nn.layerNorm(x, L, 128, a["duration.norms.$k.weight"], a["duration.norms.$k.bias"])
            nn.geluInPlace(x)
        }
        return nn.linear(x, L, 128, a.getValue("duration.out.weight"), a["duration.out.bias"], 1)
    }

    // ------------------------------------------------------------------ generator
    private fun prepCond(cond: FloatArray, T: Int): FloatArray {
        val x = convNext(a, "generator.cond_conv", cond, T, dText, 384, 7)
        val N = T / stack
        val out = FloatArray(N * dText)
        for (n in 0 until N) for (c in 0 until dText) {
            out[n * dText + c] = (x[(2 * n) * dText + c] + x[(2 * n + 1) * dText + c]) / 2f
        }
        return out
    }

    /** adaLN-single: (t, d) gömmesi → 6×192 kaydırma/ölçek/kapı. */
    private fun timeMod(t: Float, d: Float): FloatArray {
        val e = FloatArray(2 * dTime)
        sinusoidal(t, e, 0)
        sinusoidal(d, e, dTime)
        val te = nn.linear(e, 1, 2 * dTime, a.getValue("generator.t_mlp.0.weight"), a["generator.t_mlp.0.bias"], dTime)
        for (i in te.indices) te[i] = silu(te[i])
        return nn.linear(te, 1, dTime, a.getValue("generator.mod.weight"), a["generator.mod.bias"], 6 * dGen)
    }

    private fun sinusoidal(t: Float, out: FloatArray, off: Int) {
        val half = dTime / 2
        for (k in 0 until half) {
            val f = exp(-ln(10000.0) * k / half).toFloat()
            val arg = 1000f * t * f
            out[off + k] = sin(arg)
            out[off + half + k] = cos(arg)
        }
    }

    private fun generator(x: FloatArray, N: Int, ctok: FloatArray, mod: FloatArray, rope: NN.Rope): FloatArray {
        val width = nMels * stack
        val inp = FloatArray(N * (width + dText))
        for (n in 0 until N) {
            System.arraycopy(x, n * width, inp, n * (width + dText), width)
            System.arraycopy(ctok, n * dText, inp, n * (width + dText) + width, dText)
        }
        var hh = nn.linear(inp, N, width + dText, a.getValue("generator.inp.weight"), a["generator.inp.bias"], dGen)
        for (b in 0 until genBlocks) hh = adaBlock("generator.blocks.$b", hh, N, mod, rope)
        val fm = a.getValue("generator.final_mod")
        val nrm = nn.layerNorm(hh, N, dGen, null, null)
        for (n in 0 until N) for (c in 0 until dGen) {
            val i = n * dGen + c
            nrm[i] = nrm[i] * (1f + fm[c]) + fm[dGen + c]
        }
        return nn.linear(nrm, N, dGen, a.getValue("generator.out.weight"), a["generator.out.bias"], width)
    }

    private fun adaBlock(p: String, x: FloatArray, N: Int, mod: FloatArray, rope: NN.Rope): FloatArray {
        val tab = a.getValue("$p.table")
        fun m(row: Int, c: Int) = mod[row * dGen + c] + tab[row * dGen + c]
        val h = nn.layerNorm(x, N, dGen, null, null)
        for (n in 0 until N) for (c in 0 until dGen) {
            val i = n * dGen + c
            h[i] = h[i] * (1f + m(0, c)) + m(1, c)
        }
        val at = attention(a, "$p.attn", h, N, dGen, rope)
        for (n in 0 until N) for (c in 0 until dGen) x[n * dGen + c] += m(2, c) * at[n * dGen + c]
        val h2 = nn.layerNorm(x, N, dGen, null, null)
        for (n in 0 until N) for (c in 0 until dGen) {
            val i = n * dGen + c
            h2[i] = h2[i] * (1f + m(3, c)) + m(4, c)
        }
        val ff = swiglu(a, "$p.ff", h2, N, dGen, 512)
        for (n in 0 until N) for (c in 0 until dGen) x[n * dGen + c] += m(5, c) * ff[n * dGen + c]
        return x
    }

    // ------------------------------------------------------------------ vocoder
    /** mel: (T, 128) satır düzeni → T·512 örnek, 48 kHz. */
    fun vocode(mel: FloatArray, T: Int): FloatArray {
        val dim = 256
        var x = nn.conv1d(mel, T, nMels, v.getValue("inp.weight"), v["inp.bias"], dim, 7)
        x = nn.layerNorm(x, T, dim, v["norm0.weight"], v["norm0.bias"])
        for (b in 0 until 8) x = convNext(v, "blocks.$b", x, T, dim, 768, 7)
        x = nn.layerNorm(x, T, dim, v["norm1.weight"], v["norm1.bias"])
        val nFft = 2048
        val bins = nFft / 2 + 1
        val spec = nn.linear(x, T, dim, v.getValue("head.weight"), v["head.bias"], 2 * bins)
        return Istft(nFft, 512).run(spec, T, bins)
    }

    // ------------------------------------------------------------------ building blocks
    private fun convNext(w: Map<String, FloatArray>, p: String, x: FloatArray, T: Int, C: Int, hidden: Int, k: Int): FloatArray {
        val dwW = w.getValue("$p.dw.weight")
        val dwB = w.getValue("$p.dw.bias")
        val pad = k / 2
        val y = FloatArray(T * C)
        for (t in 0 until T) for (c in 0 until C) {
            var s = dwB[c]
            for (j in 0 until k) {
                val tt = t + j - pad
                if (tt in 0 until T) s += dwW[c * k + j] * x[tt * C + c]
            }
            y[t * C + c] = s
        }
        val n = nn.layerNorm(y, T, C, w["$p.norm.weight"], w["$p.norm.bias"])
        val h = nn.linear(n, T, C, w.getValue("$p.pw1.weight"), w["$p.pw1.bias"], hidden)
        nn.geluInPlace(h)
        val o = nn.linear(h, T, hidden, w.getValue("$p.pw2.weight"), w["$p.pw2.bias"], C)
        val g = w.getValue("$p.gamma")
        val out = x.copyOf()
        for (t in 0 until T) for (c in 0 until C) out[t * C + c] += g[c] * o[t * C + c]
        return out
    }

    private fun attention(w: Map<String, FloatArray>, p: String, x: FloatArray, T: Int, C: Int, rope: NN.Rope): FloatArray {
        val qkv = nn.linear(x, T, C, w.getValue("$p.qkv.weight"), null, 3 * C)
        val y = nn.attention(qkv, T, C, heads, rope)
        return nn.linear(y, T, C, w.getValue("$p.out.weight"), null, C)
    }

    private fun swiglu(w: Map<String, FloatArray>, p: String, x: FloatArray, T: Int, C: Int, hidden: Int): FloatArray {
        val ab = nn.linear(x, T, C, w.getValue("$p.w12.weight"), null, 2 * hidden)
        val g = FloatArray(T * hidden)
        for (t in 0 until T) for (i in 0 until hidden) {
            g[t * hidden + i] = silu(ab[t * 2 * hidden + i]) * ab[t * 2 * hidden + hidden + i]
        }
        return nn.linear(g, T, hidden, w.getValue("$p.w3.weight"), null, C)
    }

    fun close() = nn.close()

    companion object {
        fun silu(x: Float) = x / (1f + exp(-x))
    }
}

/** Ağ işlemleri; matris çarpımı satır blokları halinde iş parçacıklarına bölünür. */
class NN(threads: Int) {
    private val n = max(1, threads)
    private val pool: ExecutorService? = if (n > 1) Executors.newFixedThreadPool(n) { r ->
        Thread(r, "antalia").apply { isDaemon = true }
    } else null

    fun close() {
        pool?.shutdownNow()
    }

    private fun parallel(count: Int, body: (Int, Int) -> Unit) {
        if (pool == null || count < 8) {
            body(0, count); return
        }
        val chunk = (count + n - 1) / n
        val tasks = (0 until n).mapNotNull { k ->
            val s = k * chunk
            val e = min(count, s + chunk)
            if (s >= e) null else Callable { body(s, e) }
        }
        pool.invokeAll(tasks).forEach { it.get() }
    }

    /** y = x·Wᵀ + b; x (T, inD), w (outD, inD) PyTorch düzeni. */
    fun linear(x: FloatArray, T: Int, inD: Int, w: FloatArray, b: FloatArray?, outD: Int): FloatArray {
        val y = FloatArray(T * outD)
        parallel(T) { t0, t1 ->
            var t = t0
            // dört satır birlikte: W satırı bir kez okunup dört çarpımda kullanılır
            while (t + 3 < t1) {
                val x0 = t * inD
                val x1 = x0 + inD
                val x2 = x1 + inD
                val x3 = x2 + inD
                for (o in 0 until outD) {
                    val wo = o * inD
                    var s0 = 0f; var s1 = 0f; var s2 = 0f; var s3 = 0f
                    var u0 = 0f; var u1 = 0f; var u2 = 0f; var u3 = 0f
                    var i = 0
                    while (i + 1 < inD) {
                        val w0 = w[wo + i]; val w1 = w[wo + i + 1]
                        s0 += x[x0 + i] * w0; u0 += x[x0 + i + 1] * w1
                        s1 += x[x1 + i] * w0; u1 += x[x1 + i + 1] * w1
                        s2 += x[x2 + i] * w0; u2 += x[x2 + i + 1] * w1
                        s3 += x[x3 + i] * w0; u3 += x[x3 + i + 1] * w1
                        i += 2
                    }
                    if (i < inD) {
                        val w0 = w[wo + i]
                        s0 += x[x0 + i] * w0; s1 += x[x1 + i] * w0; s2 += x[x2 + i] * w0; s3 += x[x3 + i] * w0
                    }
                    val bb = b?.get(o) ?: 0f
                    y[t * outD + o] = s0 + u0 + bb
                    y[(t + 1) * outD + o] = s1 + u1 + bb
                    y[(t + 2) * outD + o] = s2 + u2 + bb
                    y[(t + 3) * outD + o] = s3 + u3 + bb
                }
                t += 4
            }
            while (t < t1) {
                val x0 = t * inD
                for (o in 0 until outD) {
                    val wo = o * inD
                    var s = 0f
                    for (i in 0 until inD) s += x[x0 + i] * w[wo + i]
                    y[t * outD + o] = s + (b?.get(o) ?: 0f)
                }
                t++
            }
        }
        return y
    }

    /** Tam Conv1d, "same" dolgu; w (out, in, k). */
    fun conv1d(x: FloatArray, T: Int, inC: Int, w: FloatArray, b: FloatArray?, outC: Int, k: Int): FloatArray {
        val pad = k / 2
        // (out, in, k) → k adet (out, in) matris
        val wj = Array(k) { j -> FloatArray(outC * inC) { idx -> w[idx * k + j] } }
        val y = FloatArray(T * outC)
        if (b != null) for (t in 0 until T) System.arraycopy(b, 0, y, t * outC, outC)
        for (j in 0 until k) {
            val shift = j - pad
            val wm = wj[j]
            parallel(T) { t0, t1 ->
                for (t in t0 until t1) {
                    val tt = t + shift
                    if (tt < 0 || tt >= T) continue
                    val xo = tt * inC
                    for (o in 0 until outC) {
                        val wo = o * inC
                        var s0 = 0f; var s1 = 0f
                        var i = 0
                        while (i + 1 < inC) {
                            s0 += x[xo + i] * wm[wo + i]; s1 += x[xo + i + 1] * wm[wo + i + 1]; i += 2
                        }
                        if (i < inC) s0 += x[xo + i] * wm[wo + i]
                        y[t * outC + o] += s0 + s1
                    }
                }
            }
        }
        return y
    }

    fun layerNorm(x: FloatArray, T: Int, C: Int, w: FloatArray?, b: FloatArray?, eps: Float = 1e-5f): FloatArray {
        val y = FloatArray(T * C)
        for (t in 0 until T) {
            val o = t * C
            var mean = 0.0
            for (c in 0 until C) mean += x[o + c]
            mean /= C
            var vr = 0.0
            for (c in 0 until C) {
                val d = x[o + c] - mean; vr += d * d
            }
            vr /= C
            val inv = (1.0 / sqrt(vr + eps)).toFloat()
            val m = mean.toFloat()
            for (c in 0 until C) {
                var z = (x[o + c] - m) * inv
                if (w != null) z = z * w[c] + (b?.get(c) ?: 0f)
                y[o + c] = z
            }
        }
        return y
    }

    fun addInPlace(x: FloatArray, y: FloatArray) {
        for (i in x.indices) x[i] += y[i]
    }

    fun geluInPlace(x: FloatArray) {
        for (i in x.indices) {
            val z = x[i]
            x[i] = (0.5 * z * (1.0 + erf(z / 1.4142135623730951))).toFloat()
        }
    }

    class Rope(val cos: FloatArray, val sin: FloatArray, val half: Int)

    fun rope(T: Int, hd: Int): Rope {
        val half = hd / 2
        val c = FloatArray(T * half)
        val s = FloatArray(T * half)
        for (p in 0 until half) {
            val inv = 1.0 / Math.pow(10000.0, (2.0 * p) / hd)
            for (t in 0 until T) {
                val f = (t.toFloat() * inv.toFloat()).toDouble()
                c[t * half + p] = cos(f).toFloat()
                s[t * half + p] = sin(f).toFloat()
            }
        }
        return Rope(c, s, half)
    }

    /** qkv (T, 3C) → RoPE'li çok başlı öz-dikkat (maskesiz, tek dizi) → (T, C). */
    fun attention(qkv: FloatArray, T: Int, C: Int, H: Int, r: Rope): FloatArray {
        val hd = C / H
        val q = FloatArray(H * T * hd)
        val k = FloatArray(H * T * hd)
        val vv = FloatArray(H * T * hd)
        for (t in 0 until T) for (h in 0 until H) {
            val src = t * 3 * C + h * hd
            val dst = (h * T + t) * hd
            for (p in 0 until r.half) {
                val cs = r.cos[t * r.half + p]
                val sn = r.sin[t * r.half + p]
                val q1 = qkv[src + 2 * p]; val q2 = qkv[src + 2 * p + 1]
                q[dst + 2 * p] = q1 * cs - q2 * sn; q[dst + 2 * p + 1] = q1 * sn + q2 * cs
                val k1 = qkv[src + C + 2 * p]; val k2 = qkv[src + C + 2 * p + 1]
                k[dst + 2 * p] = k1 * cs - k2 * sn; k[dst + 2 * p + 1] = k1 * sn + k2 * cs
            }
            System.arraycopy(qkv, src + 2 * C, vv, dst, hd)
        }
        val scale = (1.0 / sqrt(hd.toDouble())).toFloat()
        val y = FloatArray(T * C)
        parallel(H * T) { s0, s1 ->
            val sc = FloatArray(T)
            for (ht in s0 until s1) {
                val h = ht / T
                val t = ht % T
                val qo = (h * T + t) * hd
                var mx = Float.NEGATIVE_INFINITY
                for (s in 0 until T) {
                    val ko = (h * T + s) * hd
                    var d = 0f
                    for (j in 0 until hd) d += q[qo + j] * k[ko + j]
                    d *= scale
                    sc[s] = d
                    if (d > mx) mx = d
                }
                var sum = 0f
                for (s in 0 until T) {
                    val e = exp(sc[s] - mx); sc[s] = e; sum += e
                }
                val yo = t * C + h * hd
                for (s in 0 until T) {
                    val p = sc[s] / sum
                    val vo = (h * T + s) * hd
                    for (j in 0 until hd) y[yo + j] += p * vv[vo + j]
                }
            }
        }
        return y
    }

    companion object {
        /** erf; Numerical Recipes erfc Chebyshev yaklaşımı (mutlak hata < 1.2e-7, float için yeterli). */
        fun erf(x: Double): Double {
            val z = kotlin.math.abs(x)
            val t = 1.0 / (1.0 + 0.5 * z)
            val erfc = t * exp(
                -z * z - 1.26551223 + t * (1.00002368 + t * (0.37409196 + t * (0.09678418 +
                    t * (-0.18628806 + t * (0.27886807 + t * (-1.13520398 + t * (1.48851587 +
                        t * (-0.82215223 + t * 0.17087277))))))))
            )
            return if (x >= 0) 1.0 - erfc else erfc - 1.0
        }
    }
}

/** torch.istft(center=True, hann penceresi) karşılığı. */
class Istft(private val nFft: Int, private val hop: Int) {
    private val window = DoubleArray(nFft) { 0.5 - 0.5 * cos(2 * PI * it / nFft) }
    private val fft = Fft(nFft)

    /** spec: (T, 2·bins) satırları [log-büyüklük…, faz…]; çıktı T·hop örnek. */
    fun run(spec: FloatArray, T: Int, bins: Int): FloatArray {
        val total = nFft + hop * (T - 1)
        val ola = DoubleArray(total)
        val env = DoubleArray(total)
        val re = DoubleArray(nFft)
        val im = DoubleArray(nFft)
        for (f in 0 until T) {
            val o = f * 2 * bins
            for (kk in 0 until bins) {
                val mag = min(exp(spec[o + kk].toDouble()), 100.0)
                val ph = spec[o + bins + kk].toDouble()
                re[kk] = mag * cos(ph)
                im[kk] = mag * sin(ph)
            }
            // DC ve Nyquist'in sanal kısmı C2R dönüşümde yok sayılır
            im[0] = 0.0
            im[nFft / 2] = 0.0
            for (kk in 1 until nFft / 2) {
                re[nFft - kk] = re[kk]; im[nFft - kk] = -im[kk]
            }
            fft.inverse(re, im)
            val start = f * hop
            for (n in 0 until nFft) {
                val w = window[n]
                ola[start + n] += re[n] / nFft * w
                env[start + n] += w * w
            }
        }
        val out = FloatArray(T * hop)
        val off = nFft / 2
        val len = hop * (T - 1)
        for (i in 0 until len) {
            val e = env[off + i]
            out[i] = if (e > 1e-11) (ola[off + i] / e).toFloat() else 0f
        }
        return out
    }
}

/** Yerinde radix-2 karmaşık FFT. */
class Fft(private val n: Int) {
    private val cosT = DoubleArray(n / 2) { cos(2 * PI * it / n) }
    private val sinT = DoubleArray(n / 2) { sin(2 * PI * it / n) }
    private val rev = IntArray(n).also { r ->
        val bits = Integer.numberOfTrailingZeros(n)
        for (i in 0 until n) r[i] = Integer.reverse(i) ushr (32 - bits)
    }

    /** Ters yön (e^{+i}), ölçeksiz. */
    fun inverse(re: DoubleArray, im: DoubleArray) {
        for (i in 0 until n) {
            val j = rev[i]
            if (j > i) {
                var t = re[i]; re[i] = re[j]; re[j] = t
                t = im[i]; im[i] = im[j]; im[j] = t
            }
        }
        var size = 2
        while (size <= n) {
            val half = size / 2
            val step = n / size
            var i = 0
            while (i < n) {
                var k = 0
                for (j in i until i + half) {
                    val wr = cosT[k]
                    val wi = sinT[k]
                    val l = j + half
                    val tr = re[l] * wr - im[l] * wi
                    val ti = re[l] * wi + im[l] * wr
                    re[l] = re[j] - tr; im[l] = im[j] - ti
                    re[j] += tr; im[j] += ti
                    k += step
                }
                i += size
            }
            size *= 2
        }
    }
}

/** safetensors okuyucu (F32 ve F16; F16 yüklenirken F32'ye açılır). */
object SafeTensors {
    fun read(bytes: ByteArray): Map<String, FloatArray> {
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val hl = bb.getLong(0).toInt()
        val header = JSONObject(String(bytes, 8, hl, Charsets.UTF_8))
        val base = 8 + hl
        val out = HashMap<String, FloatArray>()
        for (key in header.keys()) {
            if (key == "__metadata__") continue
            val o = header.getJSONObject(key)
            val offs = o.getJSONArray("data_offsets")
            val s = base + offs.getLong(0).toInt()
            val e = base + offs.getLong(1).toInt()
            out[key] = when (val dt = o.getString("dtype")) {
                "F32" -> FloatArray((e - s) / 4) { bb.getFloat(s + it * 4) }
                "F16" -> FloatArray((e - s) / 2) { half(bb.getShort(s + it * 2).toInt() and 0xffff) }
                else -> error("desteklenmeyen dtype $dt")
            }
        }
        return out
    }

    private fun half(h: Int): Float {
        val sign = (h ushr 15) and 1
        val exp = (h ushr 10) and 0x1f
        val mant = h and 0x3ff
        val bits = when {
            exp == 0 && mant == 0 -> sign shl 31
            exp == 0 -> { // alt-normal
                var m = mant
                var e = -1
                do { e++; m = m shl 1 } while (m and 0x400 == 0)
                (sign shl 31) or ((127 - 15 - e) shl 23) or ((m and 0x3ff) shl 13)
            }
            exp == 31 -> (sign shl 31) or (0xff shl 23) or (mant shl 13)
            else -> (sign shl 31) or ((exp - 15 + 127) shl 23) or (mant shl 13)
        }
        return java.lang.Float.intBitsToFloat(bits)
    }
}

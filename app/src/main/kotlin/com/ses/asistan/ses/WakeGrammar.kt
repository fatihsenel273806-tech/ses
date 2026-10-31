package com.ses.asistan.ses

import org.json.JSONArray
import org.json.JSONObject

/**
 * Cihazdaki uyanma tanıyıcısının (Vosk) dilbilgisi ve kararı. Android'e bağlı değil: JVM testiyle sınanır.
 * Harita'nın `WakeGrammar`'ından taşındı; tek söz ("hey harita") yerine birkaç uyanma adı dinlenir (PLAN D4).
 *
 * Tanıyıcı yalnızca bu kelimeleri bilir. "hey harita" tek başına verilirse her ses ona benzetilir ("harika",
 * "çarşıya" bile); bu yüzden benzer ve sık kelimeler rakip olarak eklenir, onlar kendi adıyla çıkar. Karar için
 * addan hemen önce "hey/ey" gerekir (tek başına "harita" videoda da geçer). Harita'daki ölçüm (gTTS sesi, gürültü,
 * arkada video konuşması): 203 olumsuz örnekte yanlış tetiklenme yok, temiz seste 7/7, gürültüde 5/7. Asistanın
 * ölçümü `WakeOlcumTest`'te (gerçek Vosk çıktıları, `tool/uyanma_olcum.py`; 2026-10-31): 830 olumsuz kayıtta 2 yanlış
 * tetiklenme (hızlı "hey harika", gürültüde "ey nota"), temiz seste her ad 4/4; ağır gürültüde ve arkada konuşmada
 * zayıf (sonuçlar HAFIZA'da). gTTS sesi gerçek konuşma değildir: telefonda ayrıca denenir.
 *
 * Adlar Vosk'un küçük Türkçe modelinin sözlüğünde bulunmalı: "kanallarım", "asistanı", "notlarım" sözlükte yok
 * (tanıyıcı sessizce atar), bu yüzden Kanallarım'ın uyanma adı "kanallar".
 */
object WakeGrammar {
    /** Uyanma adları; ilk sıradaki genel asistan adı. Uygulamaya eşleme Faz 2'de (köprü keşfi). */
    val NAMES = listOf("asistan", "harita", "notlar", "kanallar")

    private val PHRASES = NAMES.flatMap { listOf("hey $it", "ey $it") } + "[unk]"
    private val RIVALS = (
        // Harita'dan (birebir)
        "harika haritada haritayı haritalar haritası haritacı harikasın harikalar hatıra arıza kahire sarı sarıyer hadi " +
            "haydi hayırlı hayat hata hala hani hari harç harf hakkı kariyer arif arıtma kahraman çarşı çarşıya arkadaş " +
            "arkadaşlar araba arabayı ara arayı arada ayrıca bir bu şu o ve ile için çok daha en gibi ama ne var yok evet " +
            "tamam değil olarak kadar sonra önce şimdi bugün yarın burada orada nasıl neden niye hangi kim ben sen biz siz " +
            "onlar benim senin bizim merhaba selam teşekkürler lütfen günaydın iyi kötü güzel büyük küçük yeni eski git gel " +
            "gidelim geldi gitti yap yaptı oldu olur olsun bak bakalım söyle dedi diyor video videoyu kanal kanalıma abone " +
            "beğen yorum izle müzik şarkı yol yolu sağa sola dön dönün metre kilometre devam düz ileri geri ev eve iş işe " +
            "okul anne annemi baba hey ey ay hay he " +
            // "asistan"a benzeyenler
            "asit istasyon sistem asker aslan hastane istiyorum astsubay hasta hastalar asla asır astar esir esnaf nişan " +
            "nisan hasan kasa kasaba " +
            // "notlar"a benzeyenler
            "not notu nota nokta motorlar notalar noter okullar kollar yollar notlarda notlardan nutuk notunu " +
            // "kanallar"a benzeyenler
            "kanala kanalları kanalı kanallara kalanlar anlar kanallarda alanlar kanat kanallardan kanalda kanalın canlar " +
            "falan"
        ).split(' ')

    /** Tanıyıcıya verilen dilbilgisi (JSON dizi). */
    val GRAMMAR: String = JSONArray(PHRASES + RIVALS).toString()

    /** Uyanma adının en düşük güveni. */
    private const val MIN_CONF = 0.6

    /** Kısmi sonuçta art arda bu kadar görülmeli (tek kısmi sonuç "hey ahmet"i de "hey harita" sanabiliyor). */
    private const val PARTIAL_HITS = 2

    /**
     * Kısmi sonuçla da uyanan adlar (çabuk tepki). Yalnız Harita'da ölçülmüş "harita"; yeni adlar yalnız cümle
     * bitince (Result) karar verir. Ölçüm (2026-10-31): kısmi sonuçta uzun süre "hey kanallar" görünüp sonra
     * düzelen cümleler yanlış tetikliyordu ("hey kanal", "ekmek alalım", arkada konuşmayla "sistem çöktü" →
     * "hey asistan"); son sonuç bunları düzeltiyor.
     */
    private val PARTIAL_NAMES = setOf("harita")

    /** Bir konuşmanın sonuçlarını izler; [feed] true dönerse bir uyanma sözü duyulmuştur, hangisi: [name]. */
    class Decider {
        private var hits = 0
        private var last: String? = null

        /** Son duyulan uyanma adı ([NAMES]'ten); [feed] true dönünce geçerli. */
        var name: String? = null
            private set

        /** [json] tanıyıcının sonucu; [final] cümle bitti (Result) mı, sürüyor (PartialResult) mu. */
        fun feed(json: String, final: Boolean): Boolean {
            val words = runCatching {
                val o = JSONObject(json)
                o.optJSONArray(if (final) "result" else "partial_result")
            }.getOrNull()
            val heard = if (words != null) wakeName(words) else null
            if (final) {
                hits = 0; last = null
                if (heard != null) name = heard
                return heard != null
            }
            val h = heard?.takeIf { it in PARTIAL_NAMES }
            hits = if (h != null && (hits == 0 || h == last)) hits + 1 else if (h != null) 1 else 0
            last = h
            if (hits >= PARTIAL_HITS) { name = h; return true }
            return false
        }

        fun reset() { hits = 0; last = null }
    }

    private fun wakeName(words: JSONArray): String? {
        for (k in 1 until words.length()) {
            val w = words.optJSONObject(k) ?: continue
            val word = w.optString("word")
            if (word !in NAMES || w.optDouble("conf", 0.0) < MIN_CONF) continue
            val prev = words.optJSONObject(k - 1)?.optString("word")
            if (prev == "hey" || prev == "ey") return word
        }
        return null
    }
}

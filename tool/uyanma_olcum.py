#!/usr/bin/env python3
"""Uyanma sözlerinin ölçümü (PLAN Faz 1; Harita'daki yöntem: gTTS sesi, gürültü, arkada konuşma, olumsuz örnek seti).

Örnek sesler gTTS ile üretilir, ffmpeg ile hız/gürültü/arka konuşma eklenir, Vosk'un küçük Türkçe modeli
WakeGrammar'ın dilbilgisiyle 100 ms'lik parçalarla (WakeWord'deki gibi) dinler. Tanıyıcının her çıktısı
fikstüre yazılır; karar Kotlin'deki WakeGrammar.Decider'ın kendisiyle WakeOlcumTest'te verilir.

Kurulum (depo dışında, ör. /tmp/olcum):  python3 -m venv venv && venv/bin/pip install vosk gTTS
Çalıştırma:  venv/bin/python -I tool/uyanma_olcum.py <vosk-model-small-tr-0.3 klasörü> <çalışma klasörü>
Dilbilgisi app/src/test/resources/uyanma/dilbilgisi.json'dan okunur; WakeOlcumTest o dosyanın
WakeGrammar.GRAMMAR ile aynı olduğunu denetler (dilbilgisi değişince bu betik yeniden çalıştırılır).
"""
import json
import os
import subprocess
import sys

import vosk
from gtts import gTTS

KOK = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
KAYNAK = os.path.join(KOK, "app/src/test/resources/uyanma")

OLUMLU = {
    "asistan": ["hey asistan", "ey asistan", "hey asistan saat kaç", "hey asistan beş dakikalık zamanlayıcı kur"],
    "harita": ["hey harita", "ey harita", "hey harita kızılaya git", "hey harita eve gidelim"],
    "notlar": ["hey notlar", "ey notlar", "hey notlar süt al", "hey notlar son notumu oku"],
    "kanallar": ["hey kanallar", "ey kanallar", "hey kanallar geri sar", "hey kanallar ne çalıyor"],
}

# Rakip ve sık kelimeler, uyanma adlarına benzeyen cümleler, gündelik konuşma ve video/yol sesi.
OLUMSUZ = [
    # adlara benzeyenler
    "hey hasta mısın", "hastaneye gidelim", "istasyona kadar git", "sistem çöktü yine", "asker yolda",
    "aslan gibi adam", "hasan nerede", "asit yağmuru yağdı", "asla vazgeçme", "astsubay geldi",
    "kasaba çok uzak", "nişan töreni yarın", "esnaf kapattı", "hey asker", "ey hasan",
    "notu verdin mi", "nota bakarak çal", "nokta koymayı unutma", "motorlar çalışıyor", "noter randevusu var",
    "okullar açıldı", "kollar yukarı", "yollar kapalı", "notlarda yazıyor", "notlardan birini sil",
    "hey not", "ey nota", "notalar karışık", "notunu düzelt", "nutuk attı",
    "kanalı değiştir", "kanala abone ol", "kanalları sırala", "kalanlar burada", "alanlar dolu",
    "kanat çırptı", "kanallardan biri kapandı", "kanalda yayın yok", "hey kanal", "ey kanat",
    "canlar yandı", "falan filan", "kanallara bak", "anlar geçti", "kanallarda reklam çok",
    "harika bir gün", "hey harika", "haritada göster", "haritayı aç", "çarşıya gidelim",
    "hatıra fotoğrafı", "arıza var", "kahire çok sıcak", "sarıyer yolu", "hadi gidelim",
    "haydi bakalım", "hayırlı olsun", "hata verdi", "hala gelmedi", "hani söz vermiştin",
    # gündelik konuşma
    "bugün hava çok güzel", "yarın sabah erken kalkacağım", "akşam yemeğinde ne var", "çocuklar okula gitti",
    "markete uğrayıp ekmek alalım", "telefonun şarjı bitti", "toplantı saat üçte", "kahve içelim mi",
    "annemi aramam lazım", "bu film çok uzun", "kitabı bitirdim", "kapıyı kapat lütfen",
    "ışıkları kapattın mı", "su ısıtıcısı bozuldu", "anahtarları unuttum", "otobüs geç kaldı",
    "trafik çok yoğun", "benzin almamız lazım", "sağdan devam et", "soldaki şeride geç",
    "yavaşla önde kaza var", "hız sınırı yetmiş", "köprüden sonra sağa dön", "otoparkta yer yok",
    "hesabı ben öderim", "teşekkür ederim", "günaydın nasılsın", "iyi geceler", "görüşürüz",
    "ne zaman geleceksin", "neden bu kadar geç kaldın", "kim aradı", "hangi yoldan gidelim",
    "biraz müzik aç", "sesi kıs", "radyoyu kapat", "şarkının adı neydi", "bu şarkıyı sevdim",
    "bir saat sonra uyar", "doktora randevu al", "ilaçlarımı içtim", "spor salonuna gidiyorum",
    "hafta sonu pikniğe gidelim", "tatil planı yapalım", "bilet fiyatları arttı", "uçak rötar yaptı",
    "valizleri hazırla", "pasaportu unutma", "otel rezervasyonu tamam", "deniz çok güzeldi",
    # video / yayın sesi
    "merhaba arkadaşlar kanalıma hoş geldiniz", "videoyu beğenmeyi unutmayın", "abone olmayı unutmayın",
    "yorumlarda görüşlerinizi yazın", "bugünkü videomuzda tarif yapacağız", "malzemeleri hazırlayalım",
    "fırını önceden ısıtın", "iki su bardağı un ekleyin", "şimdi karıştırıyoruz", "afiyet olsun",
    "maçın ilk yarısı bitti", "gol oldu", "hakem penaltı verdi", "seyirciler ayakta", "transfer haberleri",
    "hava durumu raporuna geçiyoruz", "yarın yağmur bekleniyor", "sıcaklık yirmi derece", "rüzgar kuzeyden",
    "ekonomi haberleri", "dolar yükseldi", "borsa günü düşüşle kapattı", "faiz kararı açıklandı",
    "bu bölümde tarihi yerleri geziyoruz", "kale on ikinci yüzyılda yapılmış", "müzeye giriş ücretsiz",
    "şimdi sizlere yeni telefonu tanıtacağım", "kamerası gerçekten çok iyi", "pil ömrü uzun",
    "oyunun yeni bölümü çıktı", "bu boss çok zor", "seviye atladık", "hadi bir tur daha",
    "evet arkadaşlar bugünlük bu kadar", "bir sonraki videoda görüşmek üzere", "hoşça kalın",
    # yönlendirme sesi (Harita'nın kendi sesi uyanmayı tetiklememeli)
    "üç yüz metre sonra sağa dönün", "dönel kavşaktan ikinci çıkış", "ileride radar var",
    "hedefinize ulaştınız", "rota yeniden hesaplanıyor", "yüz metre sonra sola dönün",
    "kızılaya on iki dakika", "varış on sekiz kırk", "öndeki ışıkta bekleyeceksiniz", "polis kontrolü bildirildi",
    # tek başına adlar (videoda geçebilir)
    "asistan", "harita", "notlar", "kanallar", "yeni asistan geldi", "harita uygulaması",
    "notlar defterde", "kanallar listesi", "bu asistan çok iyi", "şu harita eski",
]

VARYANTLAR = [
    ("temiz", []),
    ("hizli", ["-af", "atempo=1.15"]),
    ("yavas", ["-af", "atempo=0.9"]),
]
GURULTU = ("gurultu", 0.25)  # beyaz gürültü genliği (kabaca 10 dB SNR)
ARKA = "evet arkadaşlar bugünkü videomuzda yeni bir tarif deneyeceğiz malzemeler açıklamada"


def tts(metin, yol):
    if not os.path.exists(yol):
        gTTS(metin, lang="tr").save(yol)


def wav(mp3, cikis, ek):
    subprocess.run(["ffmpeg", "-loglevel", "error", "-y", "-i", mp3, *ek, "-ar", "16000", "-ac", "1",
                    "-f", "s16le", cikis], check=True)


def gurultulu(mp3, cikis, genlik):
    subprocess.run(["ffmpeg", "-loglevel", "error", "-y", "-i", mp3, "-f", "lavfi", "-i",
                    f"anoisesrc=color=pink:amplitude={genlik}", "-filter_complex",
                    "[0:a]aresample=16000,apad=pad_dur=0.6[a];[1:a]aresample=16000[n];[a][n]amix=inputs=2:duration=first:normalize=0",
                    "-ar", "16000", "-ac", "1", "-f", "s16le", cikis], check=True)


def arkali(mp3, arka_mp3, cikis):
    subprocess.run(["ffmpeg", "-loglevel", "error", "-y", "-i", mp3, "-i", arka_mp3, "-filter_complex",
                    "[0:a]aresample=16000,apad=pad_dur=0.6[a];[1:a]aresample=16000,volume=0.35[b];[a][b]amix=inputs=2:duration=first:normalize=0",
                    "-ar", "16000", "-ac", "1", "-f", "s16le", cikis], check=True)


def dinle(model, dilbilgisi, pcm):
    rec = vosk.KaldiRecognizer(model, 16000, dilbilgisi)
    rec.SetWords(True)
    rec.SetPartialWords(True)
    olaylar = []
    parca = 3200  # 100 ms, 16 bit
    # başa ve sona sessizlik: gerçek dinlemedeki gibi cümle tanıyıcı açıkken başlar ve biter
    sessiz = b"\0" * (parca * 3)
    veri = sessiz + pcm + sessiz * 3
    for i in range(0, len(veri), parca):
        if rec.AcceptWaveform(veri[i:i + parca]):
            olay = [True, rec.Result()]
        else:
            olay = [False, rec.PartialResult()]
        # art arda aynı çıktı sayılarak saklanır (kısmi sonuç tekrarları kararda önemli)
        if olaylar and olaylar[-1][0] == olay[0] and olaylar[-1][1] == olay[1]:
            olaylar[-1][2] += 1
        else:
            olaylar.append([olay[0], olay[1], 1])
    return olaylar


def main():
    model_yolu, calisma = sys.argv[1], sys.argv[2]
    os.makedirs(calisma, exist_ok=True)
    vosk.SetLogLevel(-1)
    model = vosk.Model(model_yolu)
    with open(os.path.join(KAYNAK, "dilbilgisi.json"), encoding="utf-8") as f:
        dilbilgisi = f.read().strip()
    arka_mp3 = os.path.join(calisma, "arka.mp3")
    tts(ARKA, arka_mp3)

    ornekler = [(ad, m) for ad, liste in OLUMLU.items() for m in liste] + [(None, m) for m in OLUMSUZ]
    kayitlar = []
    for k, (beklenen, metin) in enumerate(ornekler):
        mp3 = os.path.join(calisma, f"o{k}.mp3")
        tts(metin, mp3)
        pcm_yolu = os.path.join(calisma, "o.raw")
        varyantlar = [(v, lambda ek=ek: wav(mp3, pcm_yolu, ek)) for v, ek in VARYANTLAR]  # ek=ek: geç bağlanmasın
        varyantlar.append((GURULTU[0], lambda: gurultulu(mp3, pcm_yolu, GURULTU[1])))
        varyantlar.append(("arka", lambda: arkali(mp3, arka_mp3, pcm_yolu)))
        for ad, uret in varyantlar:
            uret()
            with open(pcm_yolu, "rb") as f:
                pcm = f.read()
            kayitlar.append({"metin": metin, "varyant": ad, "beklenen": beklenen, "olaylar": dinle(model, dilbilgisi, pcm)})
        print(f"{k + 1}/{len(ornekler)} {metin}", file=sys.stderr)

    with open(os.path.join(KAYNAK, "olcum.jsonl"), "w", encoding="utf-8") as f:
        for r in kayitlar:
            f.write(json.dumps(r, ensure_ascii=False) + "\n")


if __name__ == "__main__":
    main()

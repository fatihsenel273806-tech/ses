# ses — ortak sesli asistan planı (2026-10-31)

Kaynak: Gemini araştırması ("Mobil Android Projeleri İçin Açık Kaynaklı ve Türkçe Destekli Sesli Asistan
Mimarileri") + dört deponun kodu (Harita, Notlar, Kanallarım, Dosya Okuyucu).

## 0. Kısa cevap

- **Ayrı depo mantıklı: EVET.** Ama ayrı bir "asistan uygulaması" olarak değil, her uygulamanın içine giren
  **ortak ses kütüphanesi** olarak. Kod Kotlin'de bir kez yazılır.
- **Projelere entegre etmek mümkün:** Kotlin uygulamalar (Harita, Kanallarım) Gradle bağımlılığı olarak alır.
  Flutter uygulamalar (Notlar, Dosya Okuyucu) aynı Kotlin kodunu bir Flutter eklentisi üzerinden kullanır.
- **Gemini raporu:** genel çerçevesi doğru (uyanma → tanıma → niyet → eylem → seslendirme). Ama ana önerisi bizim
  için geri adım olur: Dicio'yu almak ya da Vosk'u ana konuşma tanıyıcı yapmak. Ayrıca asıl zor kısmı hiç ele
  almıyor: iki Flutter uygulaması, uygulamalar arası mikrofon paylaşımı, dağıtım ve lisans (bkz. §2).

## 1. Bugünkü durum (kodda bulunan)

| | Uyanma | Konuşma tanıma | Seslendirme | Komut anlama | AppFunctions |
|---|---|---|---|---|---|
| **Harita** | Vosk "Hey Harita" (`WakeWord`, `WakeGrammar`, ölçülmüş) | Google tanıyıcısı + kesintisiz dikte (`Ears`, `Dictation`) | **Antalia, saf Kotlin (1.095 satır)** + Android TTS, `SpeechQueue`, `Tones`, `NavPhrase` | `RuleParser` → Gemini işlev çağırma, `Conversation` | `Agent.kt` |
| **Notlar** | yok | Google tanıyıcısı: `MainActivity` içinde ~300 satır köprü, bip susturma, `speech_text.dart` (bölüm tekrarı) | **Antalia, Dart + ONNX (2.308 satır)** + `flutter_tts` (`speech_output.dart`) | Gemini (firebase_ai) sesli not asistanı | `Ajan.kt` |
| **Kanallarım** | yok | yok | yok | (Harita'dan medya oturumu komutu alır) | `Ajan.kt` |
| **Dosya Okuyucu** | yok | yok | `flutter_tts` (sesli kitap, `tts_service.dart` 487 satır) | Gemini REST | yok |

**Sonuç:** aynı Türkçe ses modeli iki kez yazılmış (Antalia, toplam ~3.400 satır). Google tanıyıcısının aynı
tuzakları da iki uygulamada ayrı ayrı çözülmüş: kısa sessizlikte kesme, yeniden başlatma bip'i, bölüm sınırında
kelime tekrarı. Ortak depo tam olarak bu tekrarı bitirir; sonraki her düzeltme dört uygulamaya birden gider.

## 2. Gemini raporunun değerlendirmesi

### Alınacaklar
- **Vosk + dinamik dilbilgisi** (tanıyıcıya JSON kelime listesi verme): Harita'nın uyanmasında zaten var.
  İnternet yokken çalışan sabit komut kümesine de genişletilir: "iptal", "ikinci", "yola çık", "durdur".
- **Katmanlı boru hattı** (uyanma → tanıma → niyet → eylem → seslendirme): ortak kütüphanenin iskeleti bu olur.
- **Önce kural/slot ayrıştırma, sonra ağır model:** Harita'daki `RuleParser` → Gemini sırası zaten böyle.
  Ortak niyet motoruna taşınır.
- **Piper Türkçe sesi (sherpa-onnx üzerinden):** Antalia'ya alternatif olarak ölçülecek bir aday. Zorunlu değil.

### Alınmayacaklar
- **Dicio'yu bütün olarak almak.**
  - Lisansı GPL-3.0: kodu kopyalanırsa uygulamalarımız da GPL olur. Bu, Dosya Okuyucu'nun "piyasadaki
    programlara alternatif" hedefiyle çelişir.
  - Uyanma sözü "Hey Dicio" (İngilizce openWakeWord modeli). Türkçe bir söz için model eğitmek gerekir.
  - Harita'nın Vosk uyanması zaten ölçülmüş durumda: 203 olumsuz örnekte yanlış tetiklenme yok.
  - Hazır yetenekleri (fener, alarm, hesap makinesi) bu uygulamaların işi değil.
  - Sonuç: fikir olarak bakılır (bildirimsel cümle kalıpları), kodu alınmaz.
- **Vosk'u ana konuşma tanıyıcı yapmak.** Küçük Türkçe modelin açık uçlu metinde (yer adı, not dikte) hata oranı
  yüksek; raporun kendisi de bunu kabul ediyor. Harita Vosk'u bu yüzden yalnız uyanmada kullanıyor. Açık metin
  Google tanıyıcısında kalır. Cihazda Türkçe paketi varsa `createOnDeviceSpeechRecognizer` (Android 13+) ile
  çevrimdışı tanıma denenir.
- **Rapordaki regex tablosu.** Türkçe eklerde kırılır: "annemi ara", "Ahmet'i arar mısın", "feneri yak". Mevcut
  ek temizleyici bundan daha olgun.
- **Whisper (sherpa-onnx).** Akış halinde çalışmıyor, pil ve CPU açısından ağır. Canlı komut için uygun değil.

### Raporda hiç olmayanlar (asıl zor kısım)
- **İki uygulama Flutter.** Ortak kod Kotlin'de durup Flutter'a bir eklentiyle verilmeli.
- **Mikrofon paylaşımı.** Android 10+'da iki uygulama aynı anda dinlerse sesi yalnız öndeki alır, diğerine
  sessizlik gider. Bu yüzden her uygulamanın kendi "Hey X" sözü ancak o uygulama açıkken çalışır.
- **Arka planda sürekli dinleme.** Android 14+'ta mikrofon ön plan servisi arka plandan başlatılamaz ve kalıcı
  bildirim ister. Pil harcar, HyperOS da arka plandaki uygulamayı öldürür.
- **AppFunctions ile uygulamalar arası çağrı.** `EXECUTE_APP_FUNCTIONS` izni çoğu Android 16 cihazında yalnız
  sistem uygulamalarına veriliyor. Yani merkezi bir asistan uygulaması diğerlerini AppFunctions ile yönetemez;
  ayrıca bir köprü yazmak gerekir. Telefonda `adb shell pm list permissions -f` ile doğrulanabilir.
- **Dağıtım (Maven/pub), sürümleme ve lisans.**

## 3. Neden kütüphane, neden şimdilik merkezi asistan uygulaması değil

- Harita'nın sesi sürüşe sıkı bağlı: dönüş talimatı öne geçer (`SpeechQueue`), görüşmede mikrofon bırakılır,
  mola ve PiP durumları sesi etkiler. Bunu süreçler arasına taşımak gecikme ve yeni hata yolları getirir.
- Her uygulamanın "beyni" kendine özgü: rota, video, not. Ortak olan kısım ses giriş/çıkışı ve Türkçe dil araçları.
- Modeller her uygulamada ayrı iner: Vosk 36 MB, Antalia 15 MB. Bu bedel kabul edilebilir; modeli tek yerde
  tutmak ayrı bir köprü ister (Faz 6).
- Merkezi uygulama (tek "Hey Asistan", uygulama kapalıyken de çalışan) Faz 6'da isteğe bağlı. Kütüphane hazır
  olunca onun üstüne ince bir uygulama olarak kurulur; sıfırdan başlamak gerekmez.

## 4. Depo yapısı ve entegrasyon

```
ses/
├─ cekirdek/     saf Kotlin/JVM, JVM testleriyle:
│                Türkçe normalleştirme ve ek temizliği, sayılar (yazıyla ↔ rakam), dikte kuralları,
│                uyanma dilbilgisi (uyanma sözü parametre), SpeechQueue, ToneMath, konuşma belleği,
│                niyet motoru (kalıp + slot)
├─ android/      Android kütüphaneleri (AAR), her biri ayrı modül:
│   ├─ kulak     SpeechRecognizer: tek komut, kesintisiz dikte, bip susturma, hata/yeniden deneme
│   ├─ uyanma    Vosk, model indirme (yalnız isteyen uygulama alır, yerel kitaplık eklenmez)
│   └─ agiz      Antalia + Android TTS yedeği, ses kuyruğu, ses işaretleri (tonlar)
├─ flutter/ses_asistan/   Flutter eklentisi (Pigeon): Android'de android/ modüllerini çağırır,
│                web'de bugünkü yollara düşer (flutter_tts / tarayıcı)
├─ ornek/        küçük deneme uygulaması: modüller telefonda tek başına sınanır
└─ CLAUDE.md · HAFIZA.md · PLAN.md · LICENSE
```

- **Kotlin uygulamalar (Harita, Kanallarım):** sürümlü bağımlılık (`com.github.fatihsenel273806-tech.ses:kulak:v0.3`). Geliştirirken
  `includeBuild("../ses")` ile kaynaktan derlenir: iki depo birlikte düzenlenir, yayın beklenmez.
- **Flutter uygulamalar (Notlar, Dosya Okuyucu):** pubspec'e
  `ses_asistan: {git: {url: https://github.com/fatihsenel273806-tech/ses, path: flutter/ses_asistan, ref: v0.3}}` eklenir.
- **Sürüm:** git etiketi (v0.1, v0.2…). Her uygulama kullandığı etiketi sabitler; kırıcı bir değişiklik bir
  uygulamayı kendiliğinden bozmaz.
- **Görünürlük (K1, karar verildi: AÇIK):** JitPack ve pub git anahtarsız çalışır, dört CI'ya sır eklenmez. Lisans
  Apache-2.0. Bu yüzden depoya **sır, anahtar, kişisel veri ve uygulamaya özel iş mantığı girmez**; modeller
  çalışma anında iner.

## 5. Fazlar

Protokol diğer depolardakiyle aynı: "Faz N'i yap" denince yalnız o faz yapılır. KARAR maddelerinde önce sorulur.
Bitince maddeler `[x]` yapılır, HAFIZA güncellenir, commit ve push edilir.

- [ ] **Faz 0 — Depo ve iskelet.** Dizinler, Gradle (Harita ile aynı AGP/Kotlin), CI (JVM testleri + AAR derleme +
  Flutter analyze), CLAUDE.md, HAFIZA.md, lisans, ilk etiket. Hiçbir uygulamaya dokunulmaz.
- [ ] **Faz 1 — Saf mantık Harita'dan taşınır.** `Dictation`, `WakeGrammar` (parametrik), `TrNumber`, `SpeechQueue`,
  `ToneMath`, `Conversation` ve ek temizliği, testleriyle birlikte. Harita bunları kütüphaneden kullanır; davranış
  birebir aynı kalır, Harita testleri yeşil.
- [ ] **Faz 2 — Android motorları.** `Ears` → kulak, `WakeWord` → uyanma, `Voice` + `tts/Antalia` → agiz, `Tones`.
  Harita'ya geri bağlanır ve telefonda ölçülür: CPU tabanı ~%4 kalmalı, video durmamalı, bip çıkmamalı.
- [ ] **Faz 3 — Flutter eklentisi + Notlar.** `MainActivity`'deki tanıyıcı köprüsü ve `speech_text.dart` eklentiye
  taşınır. Antalia'nın hangi uygulaması kalacak (KARAR K4): Kotlin mi (Harita), ONNX mu (Notlar)? Telefonda hız
  ve kalite ölçülür, kazanan tek kalır. Kotlin kazanırsa `flutter_onnxruntime` kaldırılır (APK küçülür).
- [ ] **Faz 4 — Kanallarım ve Dosya Okuyucu.**
  - Kanallarım: sesli arama ve komut (arama çubuğunda mikrofon; oynatıcıda "geri sar", "sonraki"). Uyanma sözü
    gerekip gerekmediği KARAR K5.
  - Dosya Okuyucu: sesli okuma Antalia'ya geçer (cihaz sesi yedek kalır). Sesli komut isteğe bağlı.
- [ ] **Faz 5 — Ortak niyet motoru + AI beyni.** Önce kurallar (anında, çevrimdışı), sonra LLM işlev çağırma. Her
  uygulama kendi araç listesini verir; Gemini/Claude istemcisi uygulamada kalır, arayüz ortaktır. Vosk
  dilbilgisiyle çevrimdışı sabit komutlar eklenir.
- [ ] **Faz 6 — (isteğe bağlı, deneysel) Uygulamalar arası köprü.**
  - Tek uyanma: "Hey …" sözü hangi uygulamayı istiyorsa komut ona gider.
  - Komut yayını `knownSigner` korumalı izinle yapılır. Dört uygulama farklı anahtarla imzalı olduğu için
    signature izni işe yaramaz; `knownCerts` listesi gerekir.
  - Model tek yerde tutulur.

## 6. Karar gerekenler

- [x] **K1** Depo görünürlüğü → **açık**, Apache-2.0 (2026-10-31).
- [x] **K2** Depo adı ve sahibi → **`fatihsenel273806-tech/ses`** (2026-10-31).
- [x] **K3** İlk adım → **önce kütüphane**; merkezi uygulama isteğe bağlı Faz 6 (2026-10-31).
- [ ] **K4** Antalia'nın Kotlin mi ONNX mu kalacağı: Faz 3'te telefonda ölçülerek karar verilir.
- [ ] **K5** Kanallarım'a uyanma sözü gerekir mi, yoksa mikrofon düğmesi yeter mi: Faz 4'te.

## 7. Riskler

- Harita'daki ses tuzaklarından biri taşınırken kaybolursa sürüş sırasında sorun çıkar. Bu yüzden Faz 1–2'de
  davranış birebir korunur; Harita'nın mevcut testleri ve telefonda ölçüm şarttır.
- İki depoda aynı anda değişiklik gerekir. Yerelde `includeBuild` ile birlikte derlenir; CI etiketli sürüm ister.

# Asistan: kişisel merkezi sesli asistan planı (2026-10-31)

Tek bir asistan uygulaması (bu depo, **Dicio çatalı**) mikrofonu, uyanma sözünü, konuşma tanımayı ve sesi
yönetir. Diğer uygulamalar ona bağlanır. **Yalnız kişisel kullanım içindir.** Dağıtılacak iki uygulama (Notlar,
Dosya Okuyucu) kendi ses özellikleriyle devam eder; onlardaki asistan köprüsü yalnız kişisel derlemeye girer.

| Faz | İçerik | Durum |
|---|---|---|
| 0 | Çatal: Dicio içe aktarılır, ad/kimlik, CI, telefonda Türkçe çalışıyor | 🟨 telefonda deneme bekliyor |
| 1 | Ses çekirdeği: Google tanıma + dikte, Vosk uyanma (çok söz), Antalia + tek ses kuyruğu | 🟨 telefonda ölçüm bekliyor |
| 2 | Köprü protokolü + Kanallarım becerisi (Kanallarım'da değişiklik yok) | 🟨 telefonda deneme bekliyor |
| 3 | Harita geçişi: ses asistanda, yönlendirme mantığı Harita'da | 🟨 telefonda ölçüm bekliyor; Harita'nın kendi ses kodu ölçümden sonra silinir |
| 4 | Notlar köprüsü (yalnız kişisel derleme) | 🟨 telefonda deneme bekliyor |
| 5 | Dosya Okuyucu köprüsü (yalnız kişisel derleme) | 🟨 asistan tarafı main'de; Dosya Okuyucu yaması push izni bekliyor (`yamalar/`), sesli oku/özet K7 |
| 6 | Beyin: Gemini yedek beceri, bağlı uygulamaların araçları, konuşma belleği | ⬜ |
| 7 | İsteğe bağlı: openWakeWord, araç/Bluetooth düğmesi, varsayılan asistan, Dicio güncellemeleri | ⬜ |

**Otomatik zincir (kullanıcı 2026-10-31):** "1 faz yap, main'e pushla, sonra yeni oturumda diğer fazı başlat, o da
bitirip main'e merge etsin; kalan işler bitinceye kadar otomatik." Her oturum bir faz yapar, `main`'e alır ve bir
sonraki fazı yeni oturumda başlatır. Telefonda ölçüm gereken maddeler 🟨 bırakılıp geçilir (kullanıcı kararı).
Harita'nın ses kodu ölçüm geçmeden silinmez (§8).

**Protokol:** kullanıcı "Faz N'i yap" deyince yalnız o faz yapılır. KARAR maddelerinde önce sorulur. Bitince
maddeler `[x]`, Durum tablosu ✅, HAFIZA güncellenir, commit + push. Kendiliğinden sonraki faza geçilmez. Başka
depoya dokunan fazda o deponun CLAUDE.md kuralları da geçerlidir (Harita: telefonda ölçüm, satır sınırı, graphify).

## 0. Verilmiş kararlar (2026-10-31)

- **D1 — Merkezi uygulama, kütüphane değil.** Bütün uygulamalar asistana bağlı. Önceki "ortak kütüphane" planından
  vazgeçildi (bkz. HAFIZA).
- **D2 — Temel: Dicio çatalı** ([DicioTeam/dicio-android](https://github.com/DicioTeam/dicio-android), GPL-3.0,
  Kotlin + Compose + Hilt, ~21 bin satır, Türkçe cümle dosyaları var). Uygulama kişisel olduğu için GPL bir yükümlülük
  getirmez. Depo lisansı GPL-3.0'dır (Dicio'nunki).
- **D3 — Harita'nın yönlendirme sesi:** ne zaman ne söyleneceğini Harita belirler (`SpeechQueue`, `NavPhrase`,
  `HazardVoice`), sesi asistan üretir. Bütün sesler tek kuyruktan geçtiği için asistanın cevabı dönüş talimatının
  üstüne binmez. Asistana ulaşılamazsa Harita Android sesine düşer.
- **D4 — Uyanma:** genel söz "Hey Asistan", ayrıca uygulama adlarıyla doğrudan sözler ("Hey Harita", "Hey Notlar"…).
  Hepsi tek Vosk dilbilgisinde dinlenir; her söz yanlış tetiklenmeye karşı ayrı ölçülür.
- **D5 — Dağıtım ayrımı:** Notlar ve Dosya Okuyucu dağıtılacak. Onların kendi ses özelliklerine (Notlar:
  `DeviceSpeech`, Dart Antalia, `flutter_tts`; Dosya Okuyucu: `tts_service`) **dokunulmaz**. Asistan köprüsü
  ek, ayrık ve kişisel derlemeye özgüdür; dağıtılan APK'da köprü yoktur.
- **D6 — Kişisel kullanım:** asistan hiçbir mağazada yayınlanmaz.

## 1. Mimari

```
            ┌──────────────────── Asistan (bu depo, Dicio çatalı) ────────────────────┐
mikrofon ─► │ Uyanma: Vosk dilbilgisi ("hey asistan", "hey harita", "hey notlar"…)     │
            │   → Tanıma: Google SpeechRecognizer (çevrimdışı yedek: Dicio'nun Vosk'u) │
            │   → Yönlendirme (sırayla):                                               │
            │       1. uyanma adı: "Hey Harita …" → doğrudan Harita                    │
            │       2. Dicio becerileri (Türkçe cümle dosyaları; bizim beceriler dahil)│
            │       3. metin devri: etkin uygulama (ör. navigasyondaki Harita)         │
            │       4. Gemini (Faz 6): araçları bağlı uygulamaların bildirdikleri      │
            │   → Ses: tek kuyruk, öncelik sınıfları; Antalia, yedek Android TTS       │
            └──────┬─────────────────┬──────────────────┬──────────────────┬──────────┘
          köprü (bağlı servis)  MediaBrowser +      mevcut sıralı      köprü (kişisel)
          Harita                oturum komutu       yayın + köprü      Dosya Okuyucu
                                Kanallarım          Notlar (kişisel)
```

- **Metin devri:** Harita'nın olgun komut anlayışı (`RuleParser` → Gemini, `offer`/"ikinci" diyaloğu) Harita'da
  kalır. Asistan söyleneni metin olarak `interpret(text, byVoice = true)`'ye verir; Harita'da ikinci bir yorumlayıcı
  yazılmaz, asistanda da Harita kuralları yeniden yazılmaz.
- **Tek ses kuyruğu:** öncelik sırası `talimat` (dönüş; yenisi eskisinin yerini alır, bayatsa yeniden üretilir),
  `uyarı` (radar/polis/kaza; ses işareti + cümle), `cevap` (asistan konuşması), `bilgi`. Harita'nın `SpeechQueue`
  kuralları asistandaki kuyruğa taşınır; Harita ne söyleneceğini ve türünü yollar.

## 2. Uygulamalar ve bağlantı

| Uygulama | Bugün | Asistanla | Uygulamada değişiklik | Dağıtım |
|---|---|---|---|---|
| **Harita** | Kendi "Hey Harita"sı (Vosk), Google tanıma, dikte, Antalia, Gemini | Köprü: metin devri, `SÖYLE` (yönlendirme sesi), `SOR` (seçenek), `DURUM` (navigasyon) | Büyük (Faz 3): ses girişi ve Antalia kalkar, Android TTS yedek kalır | Kişisel |
| **Kanallarım** | Ses yok; Harita onu MediaBrowser + `not_al` oturum komutuyla yönetiyor | Aynı yol: Harita'nın `Media.kt`/`MediaWords` mantığı asistan becerisine taşınır | **Yok** (Faz 2) | Kişisel |
| **Notlar** | `HaritaNotAlici` (sıralı yayın) Harita'dan not alıyor; kendi dikte/TTS'i | "Not al": mevcut alıcıya aynı yayın. "Ara / oku / son notlar": yeni kişisel köprü (`Ajan.kt` mantığı) | Küçük, ayrık (Faz 4) | **Dağıtılacak** → köprü yalnız kişisel derlemede |
| **Dosya Okuyucu** | Ses komutu yok; sesli kitap `flutter_tts` | Köprü: aç, son dosya, sesli oku/durdur, özetle | Küçük, `ci/` yamasıyla (Faz 5) | **Dağıtılacak** → köprü yalnız kişisel derlemede |

## 3. Köprü protokolü (taslak; Faz 2'de `PROTOKOL.md` olarak kesinleşir)

- **Keşif:** her uygulama manifestinde dışa açık bir servis bildirir: eylem `…KOPRU`, `meta-data` olarak bir
  `ses_yetenekler.xml`. XML'de şunlar bulunur: uyanma adı ("harita"), metin devri kabul edip etmediği ve ne zaman
  öncelikli olduğu (ön planda / navigasyonda), yapılandırılmış komutlar (ad, Türkçe açıklama, parametreler, örnek
  cümleler). Asistan bunları `PackageManager` ile okur; uygulama kurulunca ya da güncellenince yeniler. Komut
  açıklamaları Faz 6'da Gemini araç bildirimi olur.
- **Kanal:** bağlı servis + `Messenger`, iki yönlü (binder, milisaniye düzeyi).
  - Asistan → uygulama: `METIN(metin)`, `KOMUT(ad, parametreler)`, `CEVAP(istek, metin|seçim)`.
  - Uygulama → asistan: `SÖYLE(metin, tür, kimlik)`, `SOR(soru, seçenekler)`, `DİKTE(amaç)`,
    `DURUM(anahtar, değer)`, `AÇ(PendingIntent)`.
- **Güvenlik:** dört uygulama farklı anahtarla imzalı olduğu için signature izni işe yaramaz. İzinler
  `signature|knownSigner` ile korunur (`knownCerts`: karşı tarafın sertifika SHA-256'sı; Android 12+). Uygulamanın
  köprü servisi yalnız asistanın, asistanın ses servisi yalnız bağlı uygulamaların sertifikasını kabul eder. Notlar'ın
  bugünkü `HaritaNotAlici`'si korumasız dışa açık; Faz 4'te aynı izinle korunur.
- **AppFunctions kullanılmaz:** başka uygulamanın işlevini çağırma izni (`EXECUTE_APP_FUNCTIONS`) çoğu Android 16
  cihazında yalnız sistem uygulamalarına veriliyor. Uygulamalardaki `Ajan.kt`/`Agent.kt` Gemini için kalır.

## 4. Çatal kuralları

- `upstream` uzak deposu Dicio'dur. Güncellemeler istenirse birleştirilir (Faz 7 rutini). Birleştirmeyi kolay
  tutmak için:
  - **Yeni kod ayrı pakette** (`…/asistan/` altında): bizim girişler, beceriler, köprü, ses kuyruğu.
  - Dicio dosyasında değişiklik gerekirse küçük bir kanca olarak yapılır ve `// ASISTAN:` yorumuyla işaretlenir.
  - Dicio dosyası silinmez; kullanılmayan beceri ayarlardan kapatılır.
- Yeni kodda yorumlar Türkçe; Dicio'nun kendi kodu İngilizce kalır.
- Uygulama kimliği ve adı Dicio'dan ayrılır (K2). Dicio telefonda ayrıca kurulu olsa bile çakışmaz.
- README'de Dicio'ya atıf ve GPL-3.0 kalır.

## 5. Android kısıtları (bilinen, Faz 1'de telefonda doğrulanır)

- **Asistanın ekranı açık olmak zorunda değil.** Dinleme bir ön plan servisinde (mikrofon türü, kalıcı bildirim)
  sürer. Harita öndeyken ya da ekran kilitliyken (navigasyon) de çalışır. Şart: servis bir kez "ön plandayken"
  başlamış olmalı.
- **Açılışta mikrofon servisi başlatılamaz** (Android 11+/15: `BOOT_COMPLETED` → mikrofon ön plan servisi yasak).
  Dicio bunu "başlatmak için dokun" bildirimiyle çözüyor; yedek olarak kalır. Asıl yol: **bağlı uygulama asistanı
  kendisi başlatır.** Harita ekrandayken asistanın köprü servisine `Context.BIND_INCLUDE_CAPABILITIES` ile bağlanır.
  Öndeki uygulamanın mikrofon yetkisi bu bayrakla bağlı servise geçer (Android 11+ hedefte bayrak açıkça
  verilmeli), asistan da kendi mikrofon servisini başlatır. Böylece telefon yeniden başlasa bile asistanı ayrıca açmak
  gerekmez. Ekran kilitlenince Harita'nın elinde yalnız konum yetkisi kalır, ama asistanın servisi zaten başlamış
  olduğu için dinleme sürer. Faz 3'te telefonda doğrulanır.
- **Arka plandan ekran açma:** "Hey Harita, Kızılay'a git" Harita kapalıyken haritayı açmalı. Android 10+ arka
  plandan Activity açmayı engeller. İstisna "diğer uygulamaların üzerinde göster" (`SYSTEM_ALERT_WINDOW`) izni.
  HyperOS'ta ayrıca "arka planda açılır pencere göster" izni gerekir. Uygulama `AÇ` ile PendingIntent verir (Harita
  `Agent.kt`'deki kalıp); asistan bunu başlatır (Android 14+: arka plan başlatma izni açıkça verilir).
- **Mikrofonu aynı anda tek uygulama alır** (Android 10+: öndeki uygulama alır, diğerine sessizlik gider). Faz 3'e
  kadar Harita açıkken kendi "Hey Harita"sı mikrofonu alır, asistan sessizlik duyar. Bu bir geçiş durumu; Faz 3'te
  Harita mikrofonu bırakır.
- **HyperOS arka planı öldürür:** pil "kısıtlama yok", otomatik başlatma açık, ön plan servisi + kalıcı bildirim.
- **Telefon görüşmesi:** görüşmede uyanma beklemede. Durum yayını OFFHOOK da verir (Harita'daki kural).

## 6. Fazlar

### Faz 0 — Çatal ve iskelet
- [x] KARAR K1 (görünürlük) ve K2 (kimlik/ad) sorulur. (K1: açık + anahtar depoda; K2: `com.ses.asistan`, "Asistan")
- [x] Dicio geçmişiyle içe aktarılır: `git remote add upstream https://github.com/DicioTeam/dicio-android`,
  `git merge --allow-unrelated-histories upstream/main` (Dicio'nun ana dalı `main`, `3d1c154`). Bu depodaki belgeler korunur; çakışan README birleştirilir.
- [x] Uygulama kimliği (`applicationId`, ad, simge) değişir; dal adına göre sonek ekleyen derleme kuralı sadeleşir.
- [x] CI: push'ta imzalı APK → Release `son-surum` (diğer depolardaki düzen); sürüm kodu = çalıştırma numarası. İmza
  anahtarı depo açıksa Secrets'ta, özelse diğer depolar gibi depoda. (K1 gereği depoda: `app/asistan.keystore`,
  `.github/workflows/apk.yml`; Dicio'nun `ci.yml`'si yalnız elle.)
- [x] Bulut oturumunda derleme (`/opt/android-sdk`, `local.properties`). Dicio derlemede iki git bağımlılığını
  (`dicio-numbers`, `dicio-sentences-compiler`) indirir; ağ izni gerekir.
- [ ] Bitti sayılır: APK telefonda kurulu, Türkçe seçiliyken Dicio'nun kendi becerileri (saat, zamanlayıcı,
  hesap) sesle çalışıyor.

### Faz 1 — Ses çekirdeği (Harita'dan taşınır)
- [x] **Google tanıma girişi** (`SttInputDevice`): Harita `Ears` → tek komut, kesintisiz dikte (`Dictation`),
  bip susturma (çökmede geri açma), izin denetimi (Notlar `ensureMicPermission` dersi), bölüm tekrarı temizliği
  (Notlar `speech_text.dart` dersi). Dicio'nun Vosk girişi çevrimdışı yedek olarak kalır.
  (`giris/Kulak.kt` + `GoogleSttInputDevice`; bölüm sınırındaki tekrar `Dictation.join`'de. Mikrofon devri:
  tanıyıcı açılmadan önce uyanma kaydı durur, `SesDurumu.tanimaIstiyor`.)
- [x] **Vosk uyanma** (`WakeDevice`): Harita `WakeWord` + `WakeGrammar`. Dicio'nun `processFrame` arayüzü yalnız
  "duyuldu" der; hangi sözün duyulduğu ("harita", "notlar") yönlendirmeye taşınacak şekilde genişletilir
  (`// ASISTAN:` kancası). openWakeWord seçenek olarak kalır. (`giris/VoskWakeDevice.kt`; duyulan ad arayüz
  değişmeden `SesDurumu.sonUyanmaAdi`'na yazılır. WakeService döngüsünde tek kanca: `UyanmaKapisi.beklet`.)
- [x] Uyanma sözleri: "hey asistan" + uygulama adları. Her söz için rakip kelime listesi ve ölçüm (Harita'daki
  yöntem: olumsuz örnek seti, gürültü, arkada video konuşması). (Adlar: asistan, harita, notlar, **kanallar**
  — "kanallarım" Vosk sözlüğünde yok. Ölçüm `tool/uyanma_olcum.py` → `WakeOlcumTest`; sonuçlar HAFIZA'da.)
- [x] **Ses kuyruğu + Antalia** (`SpeechOutputDevice`): Harita `Voice` + `tts/` (Antalia, saf Kotlin) + `Tones` +
  `SpeechQueue` öncelik sınıfları (§1). Yedek: Android TTS. Ses ayarları ve örnek dinleme asistana gelir
  (Harita `VoicePicker`). (`ses/Ses.kt`, `Voice.kt`, `AsistanSpeechDevice`; ayarlar `ayar/AsistanAyarlari.kt`.)
- [x] KARAR K3 (uyanma ne zaman açık) sorulur. (Araçta + elle; `UyanmaPolitikasi`.)
- [x] Harita'daki saf mantık testleri (WakeGrammar, Dictation, SpeechQueue, ToneMath, Antalia referans testi)
  asistana gelir ve yeşil kalır. (Antalia referans testi referans dosyası ister, atlanır; gerçek ağırlıklarla
  JVM'de üretilen ses Vosk'ta kelimesi kelimesine tanındı.)
- [ ] Bitti sayılır (telefonda ölçülerek):
  - boşta dinlerken CPU ve bir saatlik pil tüketimi kaydedilir,
  - çalan video durmuyor (uyanma ses odağı almıyor), bip çıkmıyor,
  - olumsuz örneklerde yanlış tetiklenme yok,
  - dikte cümle arasında kesilmiyor.

### Faz 2 — Köprü protokolü + Kanallarım
- [x] `PROTOKOL.md` (§3 kesinleşir) ve asistan tarafı: keşif, bağlı servis, izinler, `knownCerts` (dört sertifika
  SHA-256'sı toplanır; Harita: `d03a9766…359c`). (`kopru/`; Harita ve Kanallarım sertifikaları depolardaki
  anahtarlardan. Notlar ve Dosya Okuyucu'nunkiler Secrets'ta: Faz 4/5'te telefondaki APK'dan alınır.)
- [x] Yönlendirme sırası (§1) ve uyanma adıyla doğrudan yönlendirme. (`yonlendirme/`, SkillEvaluator'da iki
  `// ASISTAN:` kancası; uygulama adıyla uyanınca asistanın ekranı açılmaz.)
- [x] **Kanallarım becerisi**: Harita `Media.kt` (MediaBrowser, oturum komutu) ve `MediaWords` (sarma, baştan,
  ne çalıyor, son izlenenler, harita listesi, adıyla video/liste açma, videoya not) asistana taşınır. Dicio'nun
  genel medya becerisi kalır; Kanallarım çalarken bizimki öne geçer. (`beceri/`: KanallarSozleri, Kanallar,
  KanallarBecerisi, liste okunup "ikinci"/adıyla seçilir; Harita `MediaWordsTest` taşındı, yeşil.)
- [ ] Bitti sayılır: Kanallarım deposuna dokunulmadan "Hey Kanallar, geri sar" (uyanma adı "kanallar", bkz. HAFIZA), "ne çalıyor", "X videosunu aç"
  ve "videoya not al" çalışıyor (not Notlar'a ulaşıyor).

### Faz 3 — Harita geçişi
- [x] Harita'da köprü servisi + `ses_yetenekler.xml`: `METIN` → `interpret`; `offer` seçenekleri `SOR` ile
  (cevabı asistan dinler); navigasyon başladı/bitti `DURUM` ile. (`AssistantBridge.kt`, `AssistantBridgeService.kt`;
  `SOR` ayrı ileti olmadı: Harita soruyu `SÖYLE` ile okutur, cevabı `DİNLE` ile alır, "ikinci"yi kendisi anlar.)
- [x] Harita açılınca asistana `BIND_INCLUDE_CAPABILITIES` ile bağlanır; asistan hiç açılmadan dinlemeye başlar
  (§5). Asistan kurulu değilse ya da bağlanamazsa Harita kendi dinlemesiyle sürer (mikrofon düğmesi dahil).
- [x] Yönlendirme sesi `SÖYLE(tür = talimat|uyarı)` ile asistana gider. Bayat talimat kuralı korunur: asistan
  okuyamadan bayatlarsa `SÖYLENDİ(bayat)` döner, Harita `Navigator.restate` ile tazesini yollar. Asistana
  ulaşılamazsa (ya da bağlantı koparsa) Harita'nın kendi sesine düşer; bekleyen cümle orada yeniden okunur.
- [ ] Harita'dan kalkanlar (**ölçüm geçince**; §8 "ölçüm geçmeden Harita'dan ses kodu silinmez"; şimdilik asistan
  yokken yedek yol olarak duruyor, bağlıyken çalışmıyor): `WakeWord`, `WakeGrammar`, `Ears` (dikte), `Dictation`, Vosk + JNA bağımlılıkları,
  `tts/` (Antalia), `VoicePicker`, `MediaWords`, `NoteBridge`'in sesli dikte kısmı. Kalanlar: mikrofon düğmesi
  (asistana "dinle" der), medya çubuğu (`MediaBar`, görsel), Android TTS yedeği. `CLAUDE.md`'deki ses tuzakları
  bu deponun HAFIZA'sına işaret eder.
- [ ] Bitti sayılır (telefonda, Harita CLAUDE.md'deki ölçüm yöntemleriyle):
  - dönüş talimatı gecikmesi bugünkünden belirgin artmıyor,
  - navigasyonda dururken CPU tabanı ~%4 korunuyor,
  - asistan zorla durdurulunca (`am force-stop`) yönlendirme Android sesiyle sürüyor,
  - görüşmede mikrofon bırakılıyor,
  - "Hey Harita, Kızılay'a git" Harita kapalıyken haritayı açıp rotayı kuruyor,
  - telefon yeniden başladıktan sonra asistan hiç açılmadan, yalnız Harita açılınca "Hey Harita" çalışıyor
    (ekran kilitliyken de).

### Faz 4 — Notlar köprüsü (yalnız kişisel derleme)
- [x] KARAR K4 (köprünün dağıtılan APK'dan nasıl çıkacağı) sorulur. (**Derleme bayrağı**: `NOTLAR_KISISEL=1` ya da
  `-Pkisisel=true` → release'e `android/app/src/kisisel` eklenir; Dosya Okuyucu'da da aynı yöntem.)
- [x] "Not al" (dikte asistanda) → `HaritaNotAlici` yayını (klasör/başlık/sabitle ekleri). Alıcı
  `knownSigner` izniyle korunur. (Alıcı Notlar'da hiç yoktu — Harita ve Kanallarım karşılıksız yayın atıyordu, bkz.
  HAFIZA; Faz 4'te yazıldı. Asistan: `beceri/NotlarBecerisi`, notlar "Asistan" klasörüne. Harita ve Kanallarım
  manifestine `dev.kaimau.notlar.izin.ASISTAN` eklendi.)
- [x] "Notlarımda X'i ara", "son notumu oku", "son notlar": yeni köprü (`Ajan.kt` + `YerelNot` mantığı; Flutter
  motoru açılmaz). Kilitli ve gizli klasördeki notlar asla verilmez. (`Ajan.kt`/`YerelNot` Notlar'da yok; köprü
  `ASISTAN_SORGU` yayını, alıcı uygulamanın Firebase Android SDK'sıyla doğrudan okur — `NotDeposu.kt`. Liste okunur,
  "ikinci"/adıyla seçilen not okunur. "Hey Notlar …" asistanın Notlar becerisine gider.)
- [ ] Bitti sayılır: kişisel APK'da komutlar çalışıyor; genel (dağıtılan) APK'da köprü yok (manifest denetimi) ve
  Notlar'ın kendi sesli özellikleri değişmemiş. (Genel APK manifest denetimi CI'da; Notlar'ın Dart koduna
  dokunulmadı. Telefonda denenecek: kişisel APK `kisisel` sürümünden, Notlar'da başlıkta `[kisisel]` ile derlenir.)

### Faz 5 — Dosya Okuyucu köprüsü (yalnız kişisel derleme)
- [x] Köprü Kotlin'i `ci/` yaması olarak (`prepare_android.sh`), kişisel bayrakla; `tool/check_kotlin.sh` koşulur.
  (`ci/kisisel/AsistanKopru.kt`, `DOSYA_KISISEL=1`; CI'da elle `kisisel: true`, o koşu Release yayınlamaz. Arama/kapsam
  `Ajan.kt`'den `ci/DosyaArama.kt`'ye taşındı. `check_kotlin.sh` gerçek jar'la temiz. **Dosya Okuyucu'ya bu oturumda
  push izni verilmedi** (yalnız okuma): commit `yamalar/dosya-okuyucu-faz5.patch`'te; o depoda `git am` ile
  uygulanıp `main`'e push edilir — açık iş, aşağıda.)
- [x] Komutlar, asistan tarafı: `beceri/DosyalarSozleri` (JVM testli) + `DosyalarBecerisi`; protokole `KOMUT`(107) /
  `SONUÇ`(9) eklendi (`Kopru.komut`, cevaptaki PendingIntent'i asistan açar). "X dosyasını aç" ve "son dosyayı aç"
  uçtan uca yazıldı.
- [ ] "Sesli oku / durdur" (uygulamanın kendi `TtsService`'i) ve "özetle" (uygulamanın Gemini'si): Dosya Okuyucu'da
  ikisi de görüntüleyici ekranının Dart durumunda; asistandan başlatmak için Dart'ta kanca gerekir → **K7 (açık)**.
  O zamana kadar köprü "henüz bağlı değil" der.
- [ ] Bitti sayılır: Faz 4 ile aynı denetim (genel APK'da köprü yok). (Denetim `prepare_android.sh`'te yazıldı;
  yama uygulanıp CI'da koşunca ve telefonda kişisel APK'da "dosyasını aç" denenince.)

### Faz 6 — Beyin
- [ ] Gemini yedek becerisi: Dicio'nun yedek becerisinin önüne geçer. Araçlar bağlı uygulamaların
  `ses_yetenekler.xml`'inden ve asistanın kendi becerilerinden gelir. Anahtar ayarlardan girilir, depoya girmez.
- [ ] Konuşma belleği (Harita `Conversation`): "oraya git", "ikincisi" gibi bağlama dayanan cümleler anlaşılır.
- [ ] Bağlam: `DURUM`'dan (navigasyon, çalan video) Gemini'ye kısa özet gider.
- [ ] Çift yorum yok: metin devredilen uygulama (Harita) kendi Gemini'sini kullanır, asistan ayrıca sormaz.

### Faz 7 — İsteğe bağlı
- [ ] openWakeWord ile Türkçe söz eğitimi (Piper Türkçe sesleriyle yapay örnek); Vosk ile CPU/pil karşılaştırması
  (K5).
- [ ] Araç ve kulaklık düğmesi: Dicio `VOICE_COMMAND` eylemini zaten karşılıyor; araçta denenir.
- [ ] Varsayılan dijital asistan (güç tuşuna basılı tutma) (K6).
- [ ] Dicio güncellemelerini birleştirme rutini.

## 7. Açık kararlar

- [x] **K1** (Faz 0) Depo görünürlüğü. **Karar (2026-10-31): açık kalır, imza anahtarı yine de depoda**
  (kullanıcı: "çok önemli bir proje değil"). Eski öneri: **Öneri: özel.** Kişisel uygulama, uygulamalara özel beceriler içerecek;
  imza anahtarı diğer depolardaki gibi tutulabilir. Açık kalırsa GPL kaynak yayını sorunsuzdur ama anahtar
  Secrets'a taşınır. Değişikliği GitHub ayarlarından siz yaparsınız.
- [x] **K2** (Faz 0) Uygulama kimliği ve adı. **Karar: `com.ses.asistan`, "Asistan".**
- [x] **K3** (Faz 1) **Karar (2026-10-31): araçta + elle.** Uyanma ne zaman açık: her zaman / araçta (araç Bluetooth'u bağlı ya da Harita navigasyonda)
  + elle / yalnız elle. Öneri: araçta + elle. Sürekli dinleme pil yer; Faz 1 ölçümüne göre karar verilir.
- [x] **K4** (Faz 4) Köprünün dağıtılan APK'dan çıkışı. **Karar (2026-10-31): derleme bayrağı** (ürün çeşidi değil).
  İki uygulamada aynı yöntem kullanılır.
- [x] **Uyanma kipi "İlgili uygulamalar açıkken"** (kullanıcı 2026-10-31: "ilgili uygulamalar açıkken seçeneği olsun,
  onlarla beraber çalışsın, boşta durmuş olur"): K3'ün yanına dördüncü kip.
- [ ] **K5** (Faz 7) Uyanma motoru: Vosk dilbilgisi mi openWakeWord mü (ölçümle). (Kullanıcı 2026-10-31: ölçüm
  bulutta yapılamadığı için şimdilik Vosk kalır; telefon ölçümü bekleyen madde olarak durur.)
- [ ] **K7** (Faz 5, açık — kullanıcıya sorulacak) Dosya Okuyucu'da "sesli oku / durdur / özetle" asistandan
  başlatılsın mı? Gereken: Dosya Okuyucu'nun **Dart** koduna (görüntüleyici) küçük bir kanca — ör. `oku:`/`ozet:`
  bağlantısı ve açık görüntüleyicinin sesli okumasını başlatıp durduran bir dinleyici; kişisel derlemenin köprüsü
  bunu tetikler. Kanca genel APK'da da bulunur ama etkisizdir (tetikleyen köprü yok). D5 "kendi ses özelliklerine
  dokunulmaz" der: `TtsService`'in kendisi değişmez, yalnız başlatılır. Seçenekler: (a) kanca eklensin (öneri),
  (b) yalnız "aç" kalsın.
- [ ] **Açık iş (Faz 5)**: Dosya Okuyucu'ya push izni. `yamalar/dosya-okuyucu-faz5.patch` o depoda
  `git am` ile uygulanır, `bash tool/check_kotlin.sh` + CI yeşil, `main`'e push. Sonra telefondaki Dosya Okuyucu
  kişisel APK'yla (CI elle, `kisisel: true`) güncellenir; asistan izni tanımlayan uygulamadan önce kurulduysa
  asistan yeniden kurulur (PROTOKOL tuzağı).
- [x] **K6** (Faz 7) Asistan varsayılan dijital asistan olsun mu? **Karar (2026-10-31): evet.** Gemini'nin yerini
  alır; uygulamalardaki Gemini AppFunctions'ı etkilenmez.

## 8. Riskler

- **Sürüş güvenliği (Faz 3):** yönlendirme sesi süreç dışına çıkıyor. Önlem: Android TTS yedeği, bayat talimat
  kuralı, zorla durdurma testi. Ölçüm geçmeden Harita'dan ses kodu silinmez.
- **Devralınan büyük kod:** Dicio ~21 bin satır, Hilt/proto/cümle derleyicisi. Önlem: yeni kod ayrı pakette,
  Dicio'ya küçük kancalar.
- **HyperOS:** arka plan öldürme ve açılır pencere izinleri. Önlem: Faz 1'de kurulum ekranında izin listesi.
- **Pil:** sürekli uyanma dinlemesi. Önlem: K3 politikası, Faz 1 ölçümü.

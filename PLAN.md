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
| 5 | Dosya Okuyucu köprüsü (yalnız kişisel derleme) | 🟨 kod iki depoda main'de (aç, son dosya, sesli oku/durdur, özet — K7); telefonda deneme bekliyor |
| 6 | Beyin: Gemini yedek beceri, bağlı uygulamaların araçları, konuşma belleği | 🟨 kod main'de; telefonda Gemini anahtarıyla deneme bekliyor |
| 7 | İsteğe bağlı: openWakeWord, araç/Bluetooth düğmesi, varsayılan asistan, Dicio güncellemeleri | 🟨 kod main'de (varsayılan asistan, kulaklık/araç düğmesi, güncelleme rutini); openWakeWord ve düğmeler telefonda |
| 8 | Telefon geri bildirimi 2: not akışı her yerde (SS, konum, video notu), uyanma adı ipucu, ekran ajanı (Gemini) + kurallı ekran komutları | 🟨 kod dalda (`ccr-46f484a9-x4howd`), Notlar alıcısı `kaimau1/not` main'de; telefonda deneme bekliyor |

**Otomatik zincir (kullanıcı 2026-10-31):** "1 faz yap, main'e pushla, sonra yeni oturumda diğer fazı başlat, o da
bitirip main'e merge etsin; kalan işler bitinceye kadar otomatik." Her oturum bir faz yapar, `main`'e alır ve bir
sonraki fazı yeni oturumda başlatır. Telefonda ölçüm gereken maddeler 🟨 bırakılıp geçilir (kullanıcı kararı).
Harita'nın ses kodu ölçüm geçmeden silinmez (§8).

**Protokol:** kullanıcı "Faz N'i yap" deyince yalnız o faz yapılır. KARAR maddelerinde önce sorulur. Bitince
maddeler `[x]`, Durum tablosu ✅, HAFIZA güncellenir, commit + push. Kendiliğinden sonraki faza geçilmez. Başka
depoya dokunan fazda o deponun CLAUDE.md kuralları da geçerlidir (Harita: telefonda ölçüm, satır sınırı, graphify).

## Telefonda yapılacaklar (zincir bitti, 2026-11-01)

Bulutta yapılabilen kod işi bitti; kalan her madde telefonda deneme ya da ölçüm ister. Otomatik zincir burada durdu.

**APK'lar ve kurulum sırası.** Köprü izinleri `knownSigner`: izni kullanan uygulama, izni tanımlayan uygulamadan
SONRA kurulmalı. Harita ile asistan birbirinin iznini kullandığı için sıra: önce hepsi bir kez, sonra 3–5 bir kez
daha (üzerine güncelleme; ayarlar kalır).
1. **Notlar** (kişisel): `kaimau1/not` Release (CI `NOTLAR_KISISEL=1` ile derliyor) — `dev.kaimau.notlar.izin.ASISTAN`.
2. **Dosya Okuyucu** (kişisel): `kaimau1/dosya-okuyucu` › Actions › "APK Derle & Release" › elle çalıştır,
   `kisisel: true` → artifact (Release'e girmez; `nightly` genel sürümdür, köprüsüzdür).
3. **Asistan**: `fatihsenel273806-tech/ses` Release `son-surum`.
4. **Harita**: kendi Release'i.
5. **Kanallarım**: kendi Release'i.

**Asistan'da ilk ayarlar** (Ayarlar › Asistan): uyanma modeli (36 MB) insin, mikrofon izni; "Diğer uygulamaların
üzerinde göster" + HyperOS "arka planda açılır pencere"; bildirim erişimi; "İlgili uygulamalar açıkken" kipi için
kullanım erişimi; "Varsayılan asistan" › Asistan (HyperOS: güç düğmesini basılı tut › asistan); Gemini anahtarı.
HyperOS: pil "kısıtlama yok", otomatik başlatma.

**Denenecekler** (sonucu HAFIZA'ya; 🟨 maddeler buna göre kapanır):
- Faz 0: saat, zamanlayıcı, hesap sesle.
- Faz 1 (ölçüm): boşta CPU (`adb shell top -b -n 1 -p $(adb shell pidof com.ses.asistan)`) ve bir saatlik pil;
  çalan video durmuyor, bip yok; olumsuz örneklerde yanlış tetiklenme yok; dikte cümle arasında kesilmiyor.
- Uygulama açma (2026-11-01 düzeltmesi): "notlar uygulamasını aç", "kanallarımı aç", "haritayı aç", "dosya
  okuyucuyu aç", "Hey Notlar, aç"; "not al" dikteye geçiyor (yol tarifi değil).
- Faz 2: "Hey Kanallar, geri sar", "ne çalıyor", "X videosunu aç", "videoya not al" (not Notlar'a ulaşıyor).
- Faz 3 (ölçüm, Harita CLAUDE.md yöntemiyle): dönüş talimatı gecikmesi, navigasyonda CPU ~%4, `am force-stop
  com.ses.asistan` sonrası yönlendirme Android sesiyle sürüyor, görüşmede mikrofon bırakılıyor, Harita kapalıyken
  "Hey Harita, Kızılay'a git", yeniden başlatma sonrası yalnız Harita açılınca "Hey Harita" (kilitliyken de).
  **Geçerse** Harita'nın kendi ses kodu silinir (Faz 3 "Harita'dan kalkanlar"; ölçümden önce silinmez, §8).
- Faz 4: "not al …", "son notumu oku", "notlarımda X'i ara"; Notlar'ın kendi sesli özellikleri değişmemiş.
- Faz 5: "kira sözleşmesi dosyasını aç", "son dosyayı aç", "bu dosyayı sesli oku", "okumayı durdur", "bu dosyayı özetle".
- Faz 6: Gemini anahtarıyla "Türkiye'nin en yüksek dağı hangisi?", "beş dakikalık bir şey kur", Harita açıkken
  "Kızılay'a nasıl giderim" → "peki oraya ne kadar sürer".
- Faz 8: Ayarlar › Asistan › "Not ekleri": ekran kontrolü (erişilebilirlik) ve konum "her zaman"; sonra Faz 8'in 🟨
  maddesindeki cümleler.
- Faz 7: güç tuşuna basılı tutma (kilitliyken de), araç Bluetooth'unun sesli komut düğmesi, kulaklık düğmesine uzun
  basma (ilk seferde "Asistan › Her zaman"). openWakeWord/Vosk karşılaştırması (K5) istenirse ölçümle.

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
| **Notlar** (`kaimau1/not`) | `HaritaNotAlici` (sıralı yayın) Harita'dan not alıyor; kendi dikte/TTS'i | "Not al": mevcut alıcıya aynı yayın. "Ara / oku / son notlar": yeni kişisel köprü (`Ajan.kt` mantığı) | Küçük, ayrık (Faz 4) | **Dağıtılacak** → köprü yalnız kişisel derlemede |
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
  `knownSigner` izniyle korunur. (Notlar deposu **`kaimau1/not`** — ilk deneme yanlış kopyada yapılmıştı, bkz. HAFIZA.
  Alıcı `HaritaNotlari.kt` orada zaten vardı; `android/app/src/kisisel/`'e taşındı, izinle korundu. Asistan:
  `beceri/NotlarBecerisi`, notlar "Asistan" klasörüne. Harita ve Kanallarım manifestinde
  `dev.kaimau.notlar.izin.ASISTAN` var.)
- [x] "Notlarımda X'i ara", "son notumu oku", "son notlar": yeni köprü (`Ajan.kt` + `YerelNot` mantığı; Flutter
  motoru açılmaz). Kilitli ve gizli klasördeki notlar asla verilmez. (Köprü `ASISTAN_SORGU` yayını; alıcı ve
  `Ajan.kt` ortak `YerelNot.gorunurNotlar`/`ara`'yı kullanır. Liste okunur,
  "ikinci"/adıyla seçilen not okunur. "Hey Notlar …" asistanın Notlar becerisine gider.)
- [ ] Bitti sayılır: kişisel APK'da komutlar çalışıyor; genel (dağıtılan) APK'da köprü yok (manifest denetimi) ve
  Notlar'ın kendi sesli özellikleri değişmemiş. (Genel APK manifest denetimi CI'da; Notlar'ın Dart koduna
  dokunulmadı. CI telefona giden APK'yı `NOTLAR_KISISEL=1` ile derler; telefonda denenecek: Notlar güncellenince
  Harita ve Kanallarım yeniden kurulur — izni tanımlayan Notlar onlardan sonra kuruldu.)

### Faz 5 — Dosya Okuyucu köprüsü (yalnız kişisel derleme)
- [x] Köprü Kotlin'i `ci/` yaması olarak (`prepare_android.sh`), kişisel bayrakla; `tool/check_kotlin.sh` koşulur.
  (`ci/kisisel/AsistanKopru.kt`, `DOSYA_KISISEL=1`; CI'da elle `kisisel: true`, o koşu Release yayınlamaz. Arama/kapsam
  `Ajan.kt`'den `ci/DosyaArama.kt`'ye taşındı. `check_kotlin.sh` gerçek jar'la temiz. İlk oturumda push izni yoktu;
  Faz 7 oturumunda yama `git am` ile uygulanıp `kaimau1/dosya-okuyucu` main'ine alındı.)
- [x] Komutlar, asistan tarafı: `beceri/DosyalarSozleri` (JVM testli) + `DosyalarBecerisi`; protokole `KOMUT`(107) /
  `SONUÇ`(9) eklendi (`Kopru.komut`, cevaptaki PendingIntent'i asistan açar). "X dosyasını aç" ve "son dosyayı aç"
  uçtan uca yazıldı.
- [x] "Sesli oku / durdur" (uygulamanın kendi `TtsService`'i) ve "özetle" (uygulamanın Gemini'si): Dosya Okuyucu'da
  ikisi de görüntüleyici ekranının Dart durumunda; asistandan başlatmak için Dart'ta kanca gerekir → **K7: kanca
  eklendi.** (Köprü dosyayı bulup `oku:/yol`, `ozet:/yol`, `durdur:` bağlantısı verir; Dart'ta
  `lib/services/asistan_komutlari.dart` açık görüntüleyicinin kendi `_toggleSpeech`'ini başlatır/duraklatır, özet
  mevcut `showAiSummary`. `TtsService` değişmedi. Özet asistanda okunmaz, Dosya Okuyucu'da açılır.)
- [ ] Bitti sayılır: Faz 4 ile aynı denetim (genel APK'da köprü yok). (Denetim `prepare_android.sh`'te yazıldı;
  yama uygulanıp CI'da koşunca ve telefonda kişisel APK'da "dosyasını aç" denenince.)

### Faz 6 — Beyin
- [x] Gemini yedek becerisi: Dicio'nun yedek becerisinin önüne geçer. Araçlar bağlı uygulamaların
  `ses_yetenekler.xml`'inden ve asistanın kendi becerilerinden gelir. Anahtar ayarlardan girilir, depoya girmez.
  (`beyin/`: `Gemini` istek/araç/cevap (saf, JVM testli), `GeminiHavuzu` (Harita `GeminiPool` birebir: çok anahtar +
  model sırası, dolanı atlar), `Beyin` (HTTP, bağlam, bellek), `BeyinBecerisi`; SkillEvaluator'da `// ASISTAN:` kanca
  `Yonlendirici.beyin` — devirden sonra, Dicio'nun yedeğinden önce. Araçlar: `asistan_komutu` (cümle sadeleşip beceri
  sıralayıcısına döner), `uygulamaya_<ad>` (`METİN`), `<ad>_<komut>` (`KOMUT`). Ayarlar › "Beyin (Gemini)": aç/kapa,
  anahtar(lar) — ekranda yalnız sonu —, model sırası (varsayılan `gemini-2.5-flash` › `gemini-flash-latest`).)
- [x] Konuşma belleği (Harita `Conversation`): "oraya git", "ikincisi" gibi bağlama dayanan cümleler anlaşılır.
  (`Konusma`, birebir: 6 tur, 3 dk sessizlikte unutur; her etkileşim yazılır — Dicio becerilerininki de —, devredilen
  sözün cevabı uygulamanın `SÖYLE`'sinden gelir.)
- [x] Bağlam: `DURUM`'dan (navigasyon, çalan video) Gemini'ye kısa özet gider. (`Baglam`: zaman, navigasyondaki ve
  ekrandaki uygulama, çalan video — başlık, konum —; Kanallarım'a bağlanma en çok 1,5 sn beklenir.)
- [x] Çift yorum yok: metin devredilen uygulama (Harita) kendi Gemini'sini kullanır, asistan ayrıca sormaz.
  (Uyanma adıyla ve navigasyondaki devirle giden metin Gemini'ye uğramaz. Gemini uygulamaya devrederse yalnız hedefi
  seçer, sözü aynen verir — "oraya" gibi göndermeleri geçmişteki adla açar —; yorum uygulamada.)
- [ ] Bitti sayılır (telefonda): Ayarlar'a Gemini anahtarı girilince "Türkiye'nin en yüksek dağı hangisi?" sesli
  cevaplanıyor; "beş dakikalık bir şey kur" gibi beceri dışı söz `asistan_komutu` ile zamanlayıcıya dönüyor; Harita
  açıkken (navigasyonda değilken) "Kızılay'a nasıl giderim" Harita'ya veriliyor; ardından "peki oraya ne kadar sürer"
  bağlamla anlaşılıyor.

### Faz 7 — İsteğe bağlı
- [ ] 🟨 openWakeWord ile Türkçe söz eğitimi (Piper Türkçe sesleriyle yapay örnek); Vosk ile CPU/pil karşılaştırması
  (K5). Karar: Vosk kalır; karşılaştırma telefonda ölçülmeden openWakeWord eklenmez (bulutta CPU/pil ölçülemez).
  Yöntem: aynı `tool/uyanma_olcum.py` olumsuz/olumlu seti + telefonda `top` ve bir saatlik pil (Faz 1 ölçümüyle).
- [x] Araç ve kulaklık düğmesi: Dicio `VOICE_COMMAND`'ı (araç Bluetooth'unun sesli komut düğmesi) zaten karşılıyor;
  kulaklık düğmesine uzun basma için `WEB_SEARCH` / `VOICE_SEARCH_HANDS_FREE` eklendi (`giris/VarsayilanAsistan.kt`,
  `// ASISTAN:` MainActivity). Hepsi kilit ekranında da açılır. 🟨 araçta ve kulaklıkla denenecek.
- [x] Varsayılan dijital asistan (güç tuşuna basılı tutma) (K6). Rol Dicio'nun `ACTION_ASSIST` etkinliğiyle;
  `VoiceInteractionService` bilerek yok (sistemin varsayılan tanıyıcısını da değiştirirdi → Notlar'ın kendi dikte'si
  asistandan geçerdi, D5; HAFIZA). Ayarlar › "Varsayılan asistan" durumu gösterir, dokununca sistem ekranı açılır.
  Kulak, varsayılan tanıyıcı asistanın kendisi olursa Google'ınkini açıkça seçer (`TaniyiciSecimi`, döngü olmasın).
  🟨 telefonda: HyperOS güç tuşu ayarı.
- [x] Dicio güncellemelerini birleştirme rutini: `bash tool/dicio_guncelle.sh` (yeni commit'leri ve kancalı dosyaları
  listeler; `--birlestir` ayrı dalda birleştirir). 2026-10-31'de upstream'de yeni commit yok (`3d1c154`).
- [x] Telefon geri bildirimi (kullanıcı 2026-11-01): "notlar uygulamasını aç" Notlar becerisine, "not al" yol
  tarifine ("not'e gidiliyor"), "kanallarımı aç" kaldığı yerden sürdürmeye düşüyordu. Düzeltme: yönlendirmenin 0.
  adımı adıyla uygulama açma (`Yonlendirme.acilacak` → `AcmaBecerisi`: Harita, Kanallarım, Notlar, Dosya Okuyucu;
  "Hey Notlar, aç" da); bizim beceriler Dicio'nunkilerin önünde (eşit puanda öndeki kazanır).

### Faz 8 — Telefon geri bildirimi 2 (2026-11-01)
Kullanıcı: "notlar uygulamamıza not alamıyor; kanallarda iken sadece hey kanallar ile uyanıyor … ben kanallarda video
izlerken notlara not aldırabilmeliyim, haritada, navigasyonda, dosya yöneticisinde; SS alabilmeli nota ekleyebilmeli
… asistan ilgili uygulamalarda A'dan Z'ye her şeyi yapabilsin; Gemini aktifken her şeyi, değilken olabildiğince çok".
Kararlar (soruldu): uyanma kipi "ilgili uygulamalar açıkken" kalır; SS erişilebilirlik servisiyle; ek ne söylenirse
o ("SS ekle", "konum ekle", "video notu"; söylenmezse düz not); yalnız "ekran görüntüsü al" → görüntü + dikte edilen
açıklamayla yeni not; ekran ajanı yalnız bağlı dört uygulamada; geri alınamayan işte önce sesli onay.
- [x] Uyanma adı ipucu: not komutu her adla Notlar'a; adın becerisi anlamazsa diğer beceriler ve Gemini
  (`Yonlendirici.once`). Video notu Kanallarım becerisinden Notlar becerisine taşındı.
- [x] Not kuralları genişledi (`NotlarSozleri.notAl`): "X diye not al", "yeni bir not aç içine X yaz", "şunu not al",
  ekler (ekran, konum, video). Gemini `not_al` aracı aynı akışa gider (`notAkisi`).
- [x] Ekran görüntüsü (`ek/EkranServisi`, `ek/Ekran`), konum (`ek/Konum`, "her zaman" izni), Notlar'a görsel eki
  (FileProvider + `image`/`video` ekleri; Notlar alıcısı görseli notun eki yapar, UploadQueue yükler).
- [x] Ekran ajanı (`ajan/`): Gemini `ekranda_yap` → her adımda ekran metni, tek eylem (tıkla, yaz, kaydır, geri,
  uygulama aç, onay iste, bitir), en çok 15 adım; riskli düğmede çalıştırıcı da onay sorar. Gemini'siz: "aşağı
  kaydır", "geri git", "X'e dokun", "X kutusuna Y yaz", "ekranda ne yazıyor" (`EkranBecerisi`).
- [x] Video sesi açıkken uyanma: uyanma kaydına yankı giderici + gürültü bastırıcı (`UyanmaKapisi.yankiGider`).
- [x] Pil (telefon: Asistan %42): sessizlikte Vosk çalışmaz (`SesEsigi`), ekran kapalıyken yalnız ses çalarken /
  navigasyonda dinlenir, bekleme döngüsü seyrek. 🟨 telefonda pil ekranı ve "hey" kaçıyor mu.
- [ ] 🟨 Telefonda: Kanallarım'da video çalarken "Hey Notlar/Asistan" (yankı giderici işe yarıyor mu), "not alır mısın
  deneme diye", "ekran görüntüsü al", "not al park yeri konumu da ekle", "video notu al önemli ekran görüntüsü de ekle",
  Harita navigasyonda not; Gemini ile "Notlar'da market notunu aç ve sonuna yumurta ekle", "Harita'da ayarları aç".

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
- [x] **K7** (Faz 5) **Karar (2026-10-31): (a) kanca eklendi** (otomatik zincir, önerilen seçenek). Dosya Okuyucu'da "sesli oku / durdur / özetle" asistandan
  başlatılsın mı? Gereken: Dosya Okuyucu'nun **Dart** koduna (görüntüleyici) küçük bir kanca — ör. `oku:`/`ozet:`
  bağlantısı ve açık görüntüleyicinin sesli okumasını başlatıp durduran bir dinleyici; kişisel derlemenin köprüsü
  bunu tetikler. Kanca genel APK'da da bulunur ama etkisizdir (tetikleyen köprü yok). D5 "kendi ses özelliklerine
  dokunulmaz" der: `TtsService`'in kendisi değişmez, yalnız başlatılır. Seçenekler: (a) kanca eklensin (öneri),
  (b) yalnız "aç" kalsın.
- [x] **Açık iş (Faz 5)** (Faz 7 oturumunda yapıldı: yama uygulandı, `check_kotlin.sh` + `flutter analyze/test` temiz, main'e push): Dosya Okuyucu'ya push izni. `yamalar/dosya-okuyucu-faz5.patch` o depoda
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

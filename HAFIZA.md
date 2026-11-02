# HAFIZA — Asistan (ses deposu)

Geçerli kararlar, taşınan tuzaklar, reddedilen yollar. Değişen kural güncellenir; tarih yazılır.

## Kararlar

- **2026-10-31 — Merkezi asistan uygulaması, Dicio çatalı** (PLAN D1, D2). Kullanıcı: "Bunu sadece ben kullanacağım,
  ayrı merkezi asistan uygulaması olarak yapalım baştan ve hepsine oradan bağlı olsun." Uygulama kişisel olduğu için
  Dicio'nun GPL-3.0 lisansı yükümlülük getirmiyor; depo lisansı GPL-3.0. Dicio: Kotlin + Compose + Hilt, 245 Kotlin
  dosyası / ~21 bin satır (2026-10-23, `3d1c154`). Türkçe cümle dosyaları var (`app/src/main/sentences/tr`).
  Tanıma (`SttInputDevice`), uyanma (`WakeDevice`) ve ses çıkışı (`SpeechOutputDevice`) arayüz olduğu için Google
  tanıma, Vosk uyanma ve Antalia buraya yeni parça olarak takılır.
- **2026-10-31 — Harita'nın yönlendirme sesi: mantık Harita'da, ses asistanda** (D3). Tek ses kuyruğu: asistanın cevabı
  dönüş talimatının üstüne binmez. Asistana ulaşılamazsa Harita Android sesine düşer.
- **2026-10-31 — Uyanma: "Hey Asistan" + uygulama adları** (D4), tek Vosk dilbilgisinde. Her söz ayrı ölçülür.
- **2026-10-31 — Notlar ve Dosya Okuyucu dağıtılacak** (D5): kendi ses özellikleri korunur, asistan köprüsü yalnız
  kişisel derlemede. Bu depodan onlara kod kopyalanmaz (GPL); bağlantı süreçler arası.
- **2026-10-31 — Açık metin Google tanıyıcısında, Vosk yalnız dilbilgisiyle** (uyanma; Dicio'nun Vosk tanıması
  çevrimdışı yedek). Vosk küçük Türkçe modeli (`vosk-model-small-tr-0.3`) açık uçlu metinde (yer adı, not) zayıf.
- **2026-10-31 — K1: depo açık, imza anahtarı depoda** (`app/asistan.keystore`, parola `android`, takma ad
  `asistan`). Kullanıcı: "Hem açık olsun hem de keystore olsun, çok önemli bir proje değil." Her CI sürümü aynı
  anahtarla imzalanır, telefonda öncekinin üzerine kurulur. Sertifika (köprü `knownCerts`'i için):
  SHA-256 `9f9ba7d6e0465f41e500744010cdb5e8428a09a5ec6d6e33ccd6510ff4209551`,
  SHA-1 `dff3aac966843201ab290c71b220e3591bceb488`. Anahtar değişirse telefondaki uygulama kaldırılıp kurulur ve
  bağlı uygulamalardaki `knownCerts` güncellenir; o yüzden değiştirilmez.
- **2026-10-31 — K2: kimlik `com.ses.asistan`, ad "Asistan"**. `namespace` Dicio'nunki (`org.stypox.dicio`) kalır
  (kod paketleri değişmez, birleştirme kolay). Ad ve simge `app/src/asistan/res`'ten gelir; bu klasör debug ve
  release kaynak kümelerine eklenir, derleme türü kaynakları main'i ezdiği için Dicio dosyasına dokunulmaz.
- **2026-10-31 — Derleme (Faz 0)**: Dicio'nun ana dalı `master` değil `main`. Sürüm kodu `GITHUB_RUN_NUMBER`
  (yerelde 1; yerel derleme telefondaki CI sürümünün üzerine kurulmaz, önce kaldırmak gerekir). Dal adına göre
  kimlik/ad soneki (Dicio `gitBranch()`) kaldırıldı. CI: `apk.yml` (JDK 21, testler → release → imza denetimi →
  `son-surum`); Dicio'nun `ci.yml`'si yalnız elle. Bulutta SDK: `/opt/android-sdk` (cmdline-tools, platform 36,
  build-tools 35), `local.properties`'te `sdk.dir`.
- **Derleme tuzakları (Faz 0)**: `dicio-numbers` Java 17 araç zinciri ister (JDK 21 yetmez; bulutta
  `apt-get install openjdk-17-jdk-headless`, CI'da setup-java 17+21). Bulut oturumunda Maven Central 429 verebilir;
  çözüm yalnız oturuma özgü `~/.gradle/init.d/ayna.gradle` ile Google aynası
  (`maven-central.storage-download.googleapis.com/maven2`), depoya girmez. İlk tam derleme ~13 dk.
- **2026-10-31 — K3: uyanma "araçta + elle"** (`UyanmaPolitikasi`). Araçta = Bluetooth ses aygıtı bağlı (ayarda araç
  adı verildiyse yalnız o; ad `AudioDeviceInfo.productName`'den, izin gerekmez) ya da navigasyonda (Faz 3 `DURUM`).
  Elle = ayardaki anahtar ya da bildirimdeki "Dinle / bırak". Kapalıyken WakeService çalışır ama `AudioRecord`
  durur; koşul gelince aynı servisten yeniden başlar (telefonda doğrulanacak: arka planda kalmış mikrofon ön plan
  servisinin kaydı yeniden başlatabilmesi).
- **2026-10-31 — Faz 1 düzeni**: yeni kod `com.ses.asistan` (`ses/` saf mantık + Antalia + kuyruk, `giris/` Google
  tanıma + Vosk uyanma, `ayar/`). Dicio'ya kancalar: üç proto değeri (`INPUT_DEVICE_GOOGLE`, `WAKE_DEVICE_VOSK`,
  `SPEECH_OUTPUT_DEVICE_ANTALIA`; varsayılan = UNSET bunlara gider), üç sarmalayıcıda `when` dalı, `Definitions.kt`
  seçenekleri, ayar ekranında `asistanAyarlari()`, `WakeService` döngüsünde `UyanmaKapisi.beklet` + bildirim
  düğmesi. Manifest eki `app/src/asistan/AndroidManifest.xml` (debug/release kaynak kümesi), Türkçe uyanma metinleri
  `app/src/asistan/res/values-tr`. Harita'nın JUnit 4 testleri `junit-vintage-engine` ile kotest'in yanında koşar.
- **2026-10-31 — Uyanma adları ve ölçümü** (`tool/uyanma_olcum.py` → `app/src/test/resources/uyanma/` →
  `WakeOlcumTest`). gTTS sesi; temiz, %15 hızlı, %10 yavaş, pembe gürültü (genlik 0,25, Harita'nınkinden ağır),
  arkada video konuşması. 182 cümle × 5 = 910 kayıt, gerçek Vosk (Python, aynı model ve dilbilgisi) çıktısı Kotlin
  `Decider`'a verilir:
  - asistan: temiz 4/4, hızlı 3/4, yavaş 2/4, gürültü 0/4, arka 1/4
  - harita: temiz 4/4, hızlı 4/4, yavaş 4/4, gürültü 0/4, arka 4/4
  - notlar: temiz 4/4, hızlı 3/4, yavaş 1/4, gürültü 3/4, arka 0/4
  - kanallar: temiz 4/4, hızlı 2/4, yavaş 4/4, gürültü 2/4, arka 3/4
  - 830 olumsuz kayıtta 2 yanlış tetiklenme: hızlı "hey harika" → harita (Harita'nın kuralı, birebir), gürültüde
    "ey nota" → notlar. Test yeni bir yanlış tetiklenmede kırılır.
  Sonuç: temiz seste dört ad da güvenilir; gürültü ve arkada konuşmada zayıf (araç içi gerçek sesle telefonda
  denenecek). Dilbilgisi değişince fikstür yeniden üretilir (`UYANMA_YAZ=1` + betik).
- **2026-10-31 — Antalia portu doğrulandı**: gerçek ağırlıklarla JVM'de (`AntaliaBench`) üretilen "Kızılay'a 12
  dakika kaldı, öndeki ışıkta bekleyeceksiniz." Vosk'ta kelimesi kelimesine tanındı. Bulut CPU'sunda 4 sn ses:
  adım 8 → 6,9 sn, 4 → 4,2 sn, 2 → 2,6 sn (telefonda ayrıca ölçülür; varsayılan adım 4, akış parça parça çalar).

- **2026-10-31 — Telefonda "hiç uyanmıyor" (Faz 1 APK'sı)**. Kullanıcı: "hey asistan hey notlar … hiçbir şekilde
  uyanmıyor; ne uygulamaların içinde ne kendi içinde ne ana ekranda". Mikrofon düğmesiyle tanıma çalışıyordu (hava
  durumu). Kodda bulunan nedenler: (1) K3 "araçta + elle" varsayılanında Bluetooth ses aygıtı / navigasyon / elle açma
  yoksa kayıt bilerek durur ve bunu gösteren hiçbir yer yoktu; asistanın kendi ekranı açıkken bile dinlemiyordu.
  (2) Dicio uyanma servisini mikrofon + bildirim (+ Android 14'te tam ekran) izinleri birlikte verilmeden hiç
  başlatmıyordu. Düzeltme: asistan ekrandayken elle açılmış sayılır (`asistanEkranda`); servis yalnız mikrofon iznine
  bağlı (`// ASISTAN:` MainActivity); ayarlarda "Uyanma şu an" satırı nedeni söyler (model inmedi / mikrofon izni yok /
  servis çalışmıyor / araçta değil / Bluetooth adı eşleşmiyor). Telefonda önce bu satıra bakılır.

- **2026-10-31 — Faz 2/3 düzeni (köprü)**. Asistan: `kopru/` (Protokol, Uygulamalar keşfi, Kopru, SesServisi),
  `yonlendirme/` (saf karar + SkillEvaluator kancası), `beceri/` (Kanallarım, devir). Harita: `AssistantBridge`
  (bağlantı), `AssistantBridgeService` (Harita kapalıyken `AÇ` → `AgentEntry`), `Voice.speakAs`/`Ears` içinde
  köprüye devir; Harita'nın geri kalanı değişmedi (`say`, `navSpeak`, `offer` aynı). Bağlıyken: ses asistanda (tek
  kuyruk), dinleme/dikte asistanda, "Hey Harita" asistanda (Harita'nın Vosk'u kapalı). Asistan yoksa/koparsa Harita'nın
  eski yolu birebir. Harita'nın kendi ses kodu (WakeWord, Ears dikte, Antalia, MediaWords) telefonda ölçüm geçince
  silinecek (PLAN §8). İki tarafın sesi çift çalmasın: bağlıyken Harita "dinliyorum/anladım" işaretini çalmaz
  (asistanın Kulak'ı çalıyor), uyarı işaretini Harita kendisi çalar (`isaret=false`).
- **2026-10-31 — Uyanma kipi "İlgili uygulamalar açıkken"** (kullanıcı isteği). Harita köprüden `onde` bildirir;
  Kanallarım'a dokunulmadığı için onun (ve Notlar, Dosya Okuyucu) önde olduğu kullanım erişimiyle
  (`PACKAGE_USAGE_STATS`, ayarlardan verilir, `OndekiUygulama`) anlaşılır. Kip sırası ordinal saklanıyor: yeni kip sona.
  Doğrulanacak: arka planda kalmış mikrofon ön plan servisinin `AudioRecord`'u yeniden başlatabilmesi (uygulama
  açılınca kayıt yeniden başlıyor).
- **Derleme tuzağı (bulut, Harita)**: Dicio için gereken "aynayı eklenti depolarının en önüne koy" init betiği
  Harita'da AGP eklentisini bulunamaz yapıyor; Harita'da yalnız Central adresini aynaya çevirmek yetiyor
  (init betiği kök yola göre ayırıyor). Harita compileSdk 37: `platforms;android-37.0`.

- **2026-10-31 — Otomatik faz zinciri** (kullanıcı): her oturum bir faz yapar, `main`'e alır, sonraki fazı yeni
  oturumda başlatır; kalan işler bitene kadar. Kararlar önden soruldu: **K4 derleme bayrağı**, **K6 evet (varsayılan
  dijital asistan)**, telefonda ölçüm gereken maddeler 🟨 bırakılıp geçilir, **K5 şimdilik Vosk** (ölçüm telefonda).
- **2026-10-31 — Notlar'ın geçerli deposu `kaimau1/not`** (kullanıcı: "kişisel notlar yanlış hesapta yapılmış,
  kaimau1 hesabındaki notlarda çalışıyoruz"). Faz 4 önce `fatihsenel273806-tech/not`'ta yapılmıştı; oradaki
  "`HaritaNotAlici` hiç yok, `Ajan.kt`/`YerelNot` yok" tespiti YANLIŞ DEPODAN geliyordu (o kopya eski). kaimau1/not'ta
  alıcı (`HaritaNotlari.kt`: Harita ve Kanallarım'ın notları, video notu, oturumsuz sıra), `YerelNot.kt` (Flutter'sız
  Firestore) ve `Ajan.kt` (Gemini AppFunctions) zaten çalışıyordu — ama dışa açık, korumasız ve genel APK'daydı.
  Ders: başka depoya dokunmadan önce `list_repos` ile aynı adlı kopyalar denetlenir; hangisinin canlı olduğu
  (son push, CI sürümü) doğrulanır. fatihsenel273806-tech/not'taki Faz 4 işi kullanılmaz (bu oturum oraya erişemedi:
  aynı adlı iki depo tek oturuma eklenemiyor).
- **2026-10-31 — Notlar köprüsü kaimau1/not'ta** (Faz 4 yeniden). Sıralı yayın (bağlı servis değil: konuşma kısa,
  Notlar'ın asistana bağlanması gerekmiyor). `HaritaNotlari.kt` → `android/app/src/kisisel/` (+ `KisiselKopru`,
  manifest, sertifikalar); `src/genel/KisiselKopru` boş. Bayrak `NOTLAR_KISISEL=1` / `-Pkisisel=true` yalnız release'i
  etkiler (debug/profile genel). CI telefona giden APK'yı bayrakla derler ("şimdilik release'de" kararı korunur),
  "genel kaynakta köprü yok" ve "alıcı izinle korunuyor" denetimleri CI'da. Alıcı `dev.kaimau.notlar.izin.ASISTAN`
  (knownSigner: asistan, Harita, Kanallarım) ile korunur; yeni eylem `ASISTAN_SORGU`. Sorgu ile Gemini işlevleri ortak
  `YerelNot.gorunurNotlar`/`ara` (kilitli not, gizli klasör asla). Firebase: alıcının süreci google-services'in
  otomatik `FirebaseApp`'iyle açılır; `YerelNot.firestore()` Dart'ın önbellek ayarının aynısını verir (farklıysa
  FlutterFire istisna atar). Asistan dikteyi kendisi yapar, notlar "Asistan" klasörüne.
- **Bulut derleme tuzağı (Notlar, 2026-10-31)**: compileSdk 37'de sembolik bağ yetmedi (AGP `package.xml`'den
  okur): `platforms/android-37.0` → `android-37` kopyalanıp `package.xml`'de yol `platforms;android-37` ve
  `<api-level>37</api-level>` yapılır. Ayna: Notlar'da ayna eklenti depolarının önüne (portal Central'a yönlendirir)
  + diğer depolarda yalnız Central adresi aynaya çevrilir; Dicio'da (ses) ayna her yerde önde.
- **Derleme tuzağı (bulut, Flutter)**: Flutter'ın `flutter_tools/gradle` dahil derlemesinin `pluginManagement`'ı boş;
  init betiği oraya yalnız aynayı koyarsa Gradle varsayılan eklenti portalını kaybeder (`kotlin-dsl` bulunamaz). Boşsa
  önce `gradlePluginPortal()` + `google()` eklenir; eklenti portalı kullanan projelerde (Dicio'nun `sentences-compiler-plugin`'i)
  ayna proje depolarının da en önüne. Flutter 3.44.6 `/opt/flutter`, NDK 28.2 ilk derlemede iner. Notlar compileSdk 37:
  sdkmanager `platforms;android-37.0`'ı `android-37.0` klasörüne kuruyor, AGP `android-37` arıyor → yalnız oturumda
  `ln -s android-37.0 android-37`.

- **2026-10-31 — Faz 5: Dosya Okuyucu köprüsü** (bağlı servis, yalnız kişisel derleme). Dosya Okuyucu'nun PLAN'da
  varsayılan parçaları gerçekten var: `ci/prepare_android.sh`, `tool/check_kotlin.sh`, `ci/Ajan.kt` (Gemini
  AppFunctions: `recentFiles`/`searchFiles`/`openFile`, AI kapsamı `AjanKapsam`), `Extras.linkIntent` (`file:`/`app:`
  bağlantısıyla açan PendingIntent). Yapılan: asistan `KOMUT`(107) → uygulama `SONUÇ`(9) (`replyTo`; Dosya Okuyucu
  asistanın ses servisine bağlanmadığı için sertifikası gerekmiyor); `Kopru.komut` cevabı bekler, `pi`'yi açar. Köprü
  `ci/kisisel/AsistanKopru.kt`, bayrak `DOSYA_KISISEL=1`; arama/kapsam `ci/DosyaArama.kt`'ye taşındı (Ajan ile ortak,
  davranış aynı). Sürüm 1 kaldı (ekler; eski uygulamalar bilinmeyen `what`'ı yok sayar). **Sesli oku / özet
  bağlanmadı**: Dosya Okuyucu'nun sesli okuması (`TtsService`) ve özeti görüntüleyici ekranının Dart durumunda
  (`viewer_screen.dart` `_tts`, `reader_screen.dart`); Kotlin'den başlatılamaz, Dart kancası gerekir → PLAN K7 (açık).
  Cümleler `DosyalarSozleri`: "dosya/belge/pdf…" sözcüğü şart (yoksa "haritayı aç", "son notumu oku" buraya düşerdi).
- **2026-10-31 — Bu oturumda Dosya Okuyucu'ya push izni verilmedi** (`add_repo` push reddedildi; okuma açık depo
  olarak var). İş yerel commit olarak `yamalar/dosya-okuyucu-faz5.patch`'teydi (Faz 7'de uygulanıp silindi): o depoda `git am`, `tool/check_kotlin.sh`
  (`ANDROID_ALL_JAR` ile), push. Yama yalnız Dosya Okuyucu için yazılmış yeni koddur; bu deponun (GPL) kodundan
  türetilmedi.
- **Bulut derleme tuzağı (init betiği)**: `settingsEvaluated` Dicio'nun settings eklentisini (`includegit`, jgit) çözmek
  için geç kalıyor — Central 429. Ayna `beforeSettings`'te `buildscript` ve `pluginManagement` depolarının önüne;
  `r.maven{}` depoyu zaten ekler, `add(0, …)` yinelenen ad hatası verir (önce `remove`). Dicio
  `FAIL_ON_PROJECT_REPOS` kullandığı için proje depolarına ayna eklenmez. Kotlin denetimi için kotlinc
  (`github.com/JetBrains/kotlin/releases`) ve Robolectric `android-all` jar'ı Google aynasından iner.

- **2026-10-31 — Faz 6: beyin (Gemini)**. Yönlendirmenin 4. adımı `Yonlendirici.beyin` (SkillEvaluator kancası,
  devirden sonra, Dicio'nun yedeğinden önce); anahtar yoksa ya da kapalıysa hiç kurulmaz, Dicio'nun "anlamadım"ı kalır.
  Harita'dan birebir taşınanlar: `Conversation` → `Konusma` (6 tur, 3 dk), `GeminiPool` → `GeminiHavuzu` (model önce,
  dolan çift Pasifik gece yarısına / Google'ın dediği süreye kadar atlanır), istek biçimi (`GeminiClient.interpret`:
  geçmiş + "Durum: … Kullanıcı: …", `fastThinking`). Tek tur function calling (sonuç Gemini'ye geri gönderilmez:
  uygulamanın cevabını uygulama ya da `SONUÇ` söyler). Araçlar: `asistan_komutu(cumle)` — Gemini cümleyi sadeleştirir,
  aynı `SkillRanker.getBest` yeniden dener (yedek beceri olmadan, döngü yok); `uygulamaya_<uyanma|ad>(metin)` devir
  kabul eden ya da uyanma adı olan uygulamaya; `<ad>_<komut>(sorgu)` `devir="hayir"` uygulamanın komutlarına (Dosya
  Okuyucu). Fonksiyon adı ASCII (`[a-z0-9_]`), çakışırsa `_2`. Bellek: her etkileşimin sözü + sesli cevabı (Dicio
  becerileri dahil); devredilen sözün cevabı uygulamanın 30 sn içindeki `SÖYLE(cevap|bilgi)`'sinden.
  Bulutta gerçek Gemini çağrısı yapılmadı (anahtar yok): istek/cevap biçimi JVM testinde, telefonda denenecek.

- **2026-10-31 — Faz 7**. **K6 varsayılan asistan, Dicio'nun `ACTION_ASSIST` etkinliğiyle** (VoiceInteractionService
  yok; aşağıda reddedilen yol). Rol uygunluğu: PermissionController `ACTION_ASSIST` etkinliği olan paketi de aday
  sayar; SystemUI asistan bileşeni etkinlikse `startAssistActivity` ile açar (güç tuşu). Girişler: `ASSIST`,
  `VOICE_COMMAND` (araç Bluetooth'u, Dicio), kulaklık uzun basma `WEB_SEARCH` / `VOICE_SEARCH_HANDS_FREE` (bizim ek,
  `giris/VarsayilanAsistan.kt`); hepsi kilit ekranında açılır (MainActivity'de `// ASISTAN:` iki kanca). Rol istek
  kutusuyla verilmez, Ayarlar › "Varsayılan asistan" sistem ekranını açar. **Kulak artık varsayılan tanıyıcı
  asistanın kendisiyse Google'ınkini açıkça seçer** (`TaniyiciSecimi`; yoksa Kulak → Dicio `SttService` → Kulak döngüsü).
  **K7 (a)**: Dosya Okuyucu'ya Dart kancası (`oku:`/`ozet:`/`durdur:` bağlantıları, `asistan_komutlari.dart`);
  Faz 5 yaması da uygulandı, ikisi `kaimau1/dosya-okuyucu` main'inde. **K5**: Vosk kalır (telefonda ölçüm). Dicio
  güncelleme rutini `tool/dicio_guncelle.sh`; upstream 2026-10-31'de `3d1c154`'te, yeni commit yok.
- **2026-11-01 — Telefon geri bildirimi: uygulama açılamıyordu** (kullanıcı: "Notları açamıyor … kanallarımı
  açamıyor, sadece arka planda son videoyu devam ettiriyor; ne dersem yapabilmeli"). Kök nedenler: (1) cümlenin
  başındaki "notlar" uyanma adı sayılıp kalan "uygulamasını aç" zorla Notlar becerisine gidiyordu ("anlamadım");
  (2) "not al": Dicio'nun Türkçe yol tarifi cümlesi `.where. … al` "not"u yer adı sandı; puanlar eşit, Dicio sıralayıcısı
  eşitlikte listede önce geleni seçer ve bizim beceriler sondaydı; (3) "kanallarımı aç": Harita `MediaWords`'ten
  birebir taşınan RESUME kuralı (`kanallarım… aç` → sürdür). Düzeltme: yönlendirmenin 0. adımı adıyla açma
  (`Yonlendirme.acilacak`, `AcmaBecerisi`, launcher niyeti; arka planda "üzerinde göster" izni gerekir), bizim
  beceriler `SkillHandler` listesinin başında. Ders: Dicio'nun genel cümleleri (`.where.`, `.what.` yakalayıcıları)
  kısa Türkçe komutları yutar; yeni beceri eklerken Dicio'nun cümle dosyalarıyla çakışma denetlenir.

- **2026-11-02 — Merkezi asistan bırakıldı** (kullanıcı: "çok fazla pil tüketiyor … mantığı bağlı uygulamaların
  içerisine vermek istiyorum"). Sürekli dinleyen WakeService + Vosk pil yiyordu. Yeni düzen: Harita kendi ses yığınıyla
  (Vosk yalnız navigasyonda), Kanallarım ve Dosya Okuyucu'da (kişisel) düğmeyle dikte → Notlar; cevaplar toast,
  navigasyonda kısa sesli onay. Köprü kodu Harita, Notlar, Dosya Okuyucu'dan silindi (2026-11-02). Depo arşivlenir.

## Reddedilen / değişen yollar

- **Ortak kütüphane (her uygulamaya giren Kotlin AAR + Flutter eklentisi)** — 2026-10-31 sabah planlandı, aynı gün
  merkezi uygulama kararıyla bırakıldı. Gerekçe: kullanıcı tek asistan istiyor; dağıtılan iki uygulama zaten kendi
  sesiyle devam edecek, ortak kütüphanenin en büyük kazancı (Notlar'daki tekrarı bitirmek) o yüzden gerekmiyor.
  Depo o plana göre açık + Apache-2.0 açılmıştı; şimdi lisans GPL-3.0, görünürlük PLAN K1.
- **Uygulamalar arası AppFunctions**: `EXECUTE_APP_FUNCTIONS` izni çoğu Android 16 cihazında yalnız sistem
  uygulamalarına veriliyor; normal bir uygulama başka uygulamanın işlevini çağıramaz (kendi işlevleri serbest).
  Doğrulamak için: `adb shell pm list permissions -f | grep -A3 EXECUTE_APP_FUNCTIONS`. Yerine köprü protokolü
  (`signature|knownSigner` izinli bağlı servis); dört uygulama farklı anahtarla imzalı, signature izni işe yaramaz.
- **Whisper (sherpa-onnx) ile canlı komut**: akış halinde çalışmıyor, pil ve CPU açısından ağır.
- **Gemini raporundaki regex tablosu**: Türkçe eklerde kırılır ("annemi ara", "Ahmet'i arar mısın"); Dicio'nun
  Türkçe cümle dosyaları ekleri isteğe bağlı gruplarla karşılıyor (`şarkı<yı|ya?>`).

- **"Hey Kanallarım" uyanma sözü**: Vosk küçük Türkçe modelinin sözlüğünde "kanallarım" (ve "asistanı",
  "notlarım", "kanalım") yok; dilbilgisine verilen sözlük dışı kelime sessizce atılır
  (`Ignoring word missing in vocabulary`). Kanallarım'ın uyanma adı "kanallar". Yeni ad eklemeden önce sözlük
  denetlenir (tanıyıcı uyarı günlüğü). Harita'nın rakip listesindeki "haritacı", "hari", "arayı", "kanalıma" da
  sözlükte yok (zararsız, birebir korundu).
- **Yeni uyanma adlarında kısmi sonuçla karar**: kısmi sonuçta uzun süre "hey kanallar" görünüp son sonuçta
  düzelen cümleler ("hey kanal", "ekmek alalım", arkada konuşmayla "sistem çöktü" → "hey asistan") yanlış
  tetikliyordu. Yeni adlar yalnız son sonuçla karar verir; "harita" Harita'daki gibi kısmi sonuçla da (ölçülmüş).
- **Ölçüm betiğinde geç bağlanan lambda** (bulundu, düzeltildi): Python'da döngüde kurulan `lambda: wav(..., ek)`
  son `ek`'i kullanır; üç varyant da aynı (yavaş) sesle ölçülmüştü, "hey asistan" kaçıyor sanıldı. `lambda ek=ek:`.

- **VoiceInteractionService ile varsayılan asistan (K6)** — 2026-10-31 düşünüldü, yapılmadı: asistan "ses etkileşim
  servisi" olunca Android `VOICE_RECOGNITION_SERVICE`'i de onun `recognitionService`'ine (Dicio `SttService`) çevirir;
  Notlar'ın kendi `DeviceSpeech`'i ve Harita'nın yedek tanıması varsayılan tanıyıcıyı kullandığından asistandan
  geçerdi (D5 ihlali). Kilit ekranı ve güç tuşu `ACTION_ASSIST` etkinliğiyle de çalışıyor.

## Taşınacak ses tuzakları (kanıtlı; kaynağı Harita/Notlar CLAUDE.md ve kodu)

- **Google tanıyıcısı uzun oturumu desteklemez** (Xiaomi, `com.google.android.tts`): `EXTRA_SEGMENTED_SESSION` yok
  sayılır, oturum ~8,6 sn'de "konuşma yok" ile biter. Sessizlik süresi ayarlarını da yok sayar, ilk kısa
  sessizlikte cümleyi bitirir. Kesintisiz dikte için tanıyıcı her cümle sonunda hemen yeniden açılır, parçalar
  biriktirilir (Harita `Ears.dictate` + `Dictation`; Notlar `MainActivity` köprüsü).
- **Yeniden başlatma bip'i**: tanıyıcı her başlatmada bildirim kanalından iki kısa ses çalar. Dinlerken bildirim
  kanalı susturulur; uygulama çökerse açılışta geri açılır (Harita `Ears.muteBeep`, Notlar `restoreBeepsFromPrefs`).
  Kullanıcının kendi susturmasına dokunulmaz.
- **Ses odağı**: Google tanıyıcısı her başlatmada ses odağını alır; çalan video (Kanallarım/YouTube) durur. Uyanma bu
  yüzden `AudioRecord` + Vosk ile dinlenir (odak almaz). Model inene kadar video çalıyorsa eski yol beklemede.
- **Uyanma dilbilgisi**: "hey harita" tek başına verilirse her ses ona benzetilir ("harika", "çarşıya"). Benzer ve
  sık kelimeler rakip olarak eklenir, ayrıca "hey/ey" ön eki şarttır. Ölçüm: 203 olumsuz örnekte yanlış tetiklenme
  yok; temiz seste 7/7, gürültüde 5/7 (Harita `WakeGrammar`).
- **Uyanmadan sonra ilk hece kaçabilir**: Vosk'tan Google'a geçerken "not al"ın "not"u kaçar; ilk dinlemedeki "al …"
  not sayılır (Harita `Dictation.clippedNote`).
- **Bölüm sınırında kelime tekrarı**: tanıyıcı kısa duraklamada oturumu kapatıp yeniden açınca köprü, emin olamadığı
  yerde "devamı değil" der (kelime kaybetmektense tekrar yeğ). Tekrar satır sınırında temizlenir; cümle ortasındaki
  gerçek tekrara ("çok çok iyi") dokunulmaz (Notlar `speech_text.dart`).
- **Mikrofon izni**: `SpeechRecognizer` izin eksikken kendi kutusunu açmaz, sessizce
  `ERROR_INSUFFICIENT_PERMISSIONS` ile düşer. Önce `checkSelfPermission` okunur; izin gerçekten yoksa istenir.
  Eklentinin `hasPermission`'ı her hatayı "izin yok"a katlıyordu (Notlar `DeviceSpeech.ensureMicPermission`).
- **Konuşurken kendi sesini duyma**: TTS konuşurken uyanma mikrofonu açık kalır ama duyulan atılır (`WakeWord.deaf`);
  yeniden başlatma yok.
- **Telefon görüşmesi**: görüşme sürerken mikrofon görüşmenindir, uyanma beklemede. Durum yayını yalnız RINGING
  değil OFFHOOK da verir. Asistanda yayın yerine ses kipi okunur (`AudioManager.mode` IN_CALL/IN_COMMUNICATION,
  saniyede bir; READ_PHONE_STATE izni gerekmez; WhatsApp gibi internet aramalarını da yakalar).
- **Mikrofon paylaşımı (Android 10+)**: iki uygulama aynı anda dinlerse sesi yalnız öndeki alır. Faz 3'e kadar Harita
  açıkken kendi "Hey Harita"sı mikrofonu alır, asistan sessizlik duyar (geçiş durumu).
- **Açılışta mikrofon servisi** (Android 11+/15): `BOOT_COMPLETED`'tan mikrofon ön plan servisi başlatılamaz; Dicio
  "başlatmak için dokun" bildirimi gösteriyor (`BootBroadcastReceiver`).

## Köprü protokolü değişiklikleri

- 2026-10-31 sürüm 1 (`PROTOKOL.md`). Asistan ve Harita uyguluyor. Dosya Okuyucu Faz 5'te: kendi iznini
  (`<paket>.izin.ASISTAN`, knownCerts = asistan) tanımlar, asistan manifestine `uses-permission` ve
  `kopru_sertifikalari`'na sertifikası eklenir.
- 2026-10-31 "KOMUT/SONUÇ" (Faz 5; sürüm 1 kaldı, ek iletiler): asistan → uygulama `KOMUT` (107: `kimlik`, `ad`,
  `sorgu`), uygulama → asistan `SONUÇ` (9: `kimlik`, `metin`, `tamam`, `pi`). İzin
  `com.dosyaokuyucu.dosya_okuyucu.izin.ASISTAN` (knownCerts: asistan). Değişen: asistan (`Kopru.komut`,
  `uses-permission`, Dosyalar becerisi), Dosya Okuyucu (kişisel köprü; Faz 7'de main'de). Harita, Kanallarım, Notlar
  etkilenmez.
- 2026-10-31 "Notlar yayınları" (Faz 4; sürüm değişmedi, Messenger kanalına dokunulmadı): `HARITA_NOT` +
  `ASISTAN_SORGU`, izin `dev.kaimau.notlar.izin.ASISTAN` (knownCerts: asistan, Harita, Kanallarım). Değişen:
  Notlar (kişisel kaynak kümesi), asistan (`uses-permission`, Notlar becerisi), Harita ve Kanallarım (`uses-permission`).
- 2026-10-31 "Dosya Okuyucu oku/ozetle/durdur" (K7; sürüm değişmedi): `SONUÇ.pi` artık `oku:/yol`, `ozet:/yol`,
  `durdur:` bağlantısı da olabilir (Dosya Okuyucu'nun Dart kancası karşılar). Değişen: Dosya Okuyucu (köprü + Dart),
  asistan (özet için 45 sn bekleme kalktı). Harita, Kanallarım, Notlar etkilenmez.

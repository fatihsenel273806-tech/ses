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

(henüz yok; `PROTOKOL.md` Faz 2'de yazılır. Mesaj ya da izin değişirse hangi uygulamada neyin değişeceği buraya.)

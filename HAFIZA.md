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
  değil OFFHOOK da verir.
- **Mikrofon paylaşımı (Android 10+)**: iki uygulama aynı anda dinlerse sesi yalnız öndeki alır. Faz 3'e kadar Harita
  açıkken kendi "Hey Harita"sı mikrofonu alır, asistan sessizlik duyar (geçiş durumu).
- **Açılışta mikrofon servisi** (Android 11+/15): `BOOT_COMPLETED`'tan mikrofon ön plan servisi başlatılamaz; Dicio
  "başlatmak için dokun" bildirimi gösteriyor (`BootBroadcastReceiver`).

## Köprü protokolü değişiklikleri

(henüz yok; `PROTOKOL.md` Faz 2'de yazılır. Mesaj ya da izin değişirse hangi uygulamada neyin değişeceği buraya.)

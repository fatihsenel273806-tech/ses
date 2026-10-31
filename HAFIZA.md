# HAFIZA — ses

Geçerli kararlar, taşınan tuzaklar, reddedilen yollar. Değişen kural güncellenir; tarih yazılır.

## Kararlar

- **2026-10-31 — Ayrı depo, açık, Apache-2.0, adı `ses`** (K1, K2). Gerekçe: dört uygulama, iki dil (Kotlin, Flutter);
  aynı Türkçe seslendirme (Antalia) iki kez yazılmıştı (Harita'da saf Kotlin 1.095 satır, Notlar'da Dart + ONNX
  2.308 satır). Google tanıyıcısının aynı tuzakları da iki uygulamada ayrı ayrı çözülmüştü. Açık depo: JitPack ve
  pub git anahtarsız çalışır, CI'lara sır eklenmez (Dosya Okuyucu başka hesapta: `kaimau1`).
- **2026-10-31 — Önce kütüphane, merkezi asistan uygulaması değil** (K3). Her uygulama kütüphaneyi kendi içinde
  kullanır. Merkezi uygulama (tek "Hey Asistan") isteğe bağlı Faz 6 olarak kütüphanenin üstüne kurulur.
- **2026-10-31 — Açık metin Google tanıyıcısında kalır; Vosk yalnız dilbilgisiyle** (uyanma + çevrimdışı sabit
  komutlar). Vosk küçük Türkçe modeli (`vosk-model-small-tr-0.3`) açık uçlu metinde (yer adı, not) zayıf.

## Reddedilen yollar

- **Dicio'yu almak** (Gemini araştırmasının ana önerisi, 2026-10-31): GPL-3.0 (kullanan uygulamaları GPL yapar).
  Uyanma sözü İngilizce openWakeWord modeli; Türkçe söz için model eğitmek gerekir. Harita'nın Vosk uyanması zaten
  ölçülmüş durumda. Hazır yetenekleri (fener, alarm, hesap makinesi) bu uygulamaların işi değil.
- **Uygulamalar arası AppFunctions ile merkezi asistan**: `EXECUTE_APP_FUNCTIONS` izni çoğu Android 16 cihazında
  yalnız sistem uygulamalarına veriliyor. Normal bir uygulama başka uygulamanın AppFunctions'ını çağıramaz
  (kendi işlevleri serbest). Telefonda doğrulamak için: `adb shell pm list permissions -f | grep -A3 EXECUTE_APP_FUNCTIONS`.
  Köprü gerekirse `knownSigner` korumalı izinle yayın kullanılır (dört uygulama farklı anahtarla imzalı, signature
  izni işe yaramaz).
- **Whisper (sherpa-onnx) ile canlı komut**: akış halinde çalışmıyor, pil ve CPU açısından ağır.
- **Gemini raporundaki regex tablosu**: Türkçe eklerde kırılır ("annemi ara", "Ahmet'i arar mısın").

## Taşınacak tuzaklar (kanıtlı; kaynağı uygulamanın CLAUDE.md/HAFIZA'sı)

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
- **Mikrofon paylaşımı (Android 10+)**: iki uygulama aynı anda dinlerse sesi yalnız öndeki alır. Uygulama başına
  "Hey X" ancak o uygulama açıkken çalışır.

## Kırıcı değişiklikler

(henüz sürüm yok)

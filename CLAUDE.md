# Asistan (ses deposu) — geliştirme kuralları

Kişisel merkezi sesli asistan: mikrofon, uyanma sözü, konuşma tanıma, dikte ve ses tek uygulamada; diğer
uygulamalar ona bağlanır. **Dicio çatalı** (GPL-3.0, Kotlin + Jetpack Compose + Hilt). **Yalnız kişisel
kullanım**, hiçbir mağazada yayınlanmaz. Arayüz metinleri ve yeni kodun yorumları **Türkçe**.

Bağlanan uygulamalar:

| Uygulama | Depo | Bağlantı | Dağıtım |
|---|---|---|---|
| Harita Ankara | `fatihsenel273806-tech/harita` | köprü servisi (metin devri, yönlendirme sesi) | kişisel |
| Kanallarım | `fatihsenel273806-tech/YouTube` | MediaBrowser + `not_al` oturum komutu | kişisel |
| Notlar | `kaimau1/not` (fatihsenel273806-tech/not eski/yanlış kopya, kullanılmaz) | sıralı yayın + köprü, **yalnız kişisel derlemede** | dağıtılacak |
| Dosya Okuyucu | `kaimau1/dosya-okuyucu` | köprü (`ci/` yaması), **yalnız kişisel derlemede** | dağıtılacak |

## Plan ve hafıza

- **`PLAN.md`**: Faz 0–7, verilmiş kararlar (D1–D6), açık kararlar (K1–K6). Kullanıcı **"Faz N'i yap"** dediğinde
  yalnız o faz yapılır; KARAR maddelerinde önce sorulur; bitince maddeler `[x]`, Durum tablosu, HAFIZA, commit + push.
  Kendiliğinden sonraki faza geçilmez.
- **`HAFIZA.md`**: geçerli kararlar, taşınan ses tuzakları, reddedilen yollar. İş başında oku; kalıcı karar, bug kök
  nedeni ya da yanlış çıkan yol oluşunca iş bitmeden yaz.
- Başka depoya dokunan fazda o deponun `CLAUDE.md`'si de geçerlidir (Harita: telefonda ölçüm, 600 satır sınırı,
  graphify; Notlar: `dart format lib` yok; Dosya Okuyucu: tek dal `main`, `ci/*.kt` için `tool/check_kotlin.sh`).

## Çatal kuralları (Dicio)

- `upstream` = `https://github.com/DicioTeam/dicio-android`. Güncelleme birleştirmesini kolay tutmak için:
  - **Yeni kod ayrı pakette** (`…/asistan/`): girişler (Google tanıma, Vosk uyanma), ses kuyruğu, Antalia,
    beceriler, köprü.
  - Dicio dosyasındaki zorunlu değişiklik küçük bir kancadır ve `// ASISTAN:` yorumuyla işaretlenir.
  - Dicio dosyası silinmez; istenmeyen beceri ayarlardan kapatılır.
- Dicio'nun kendi kodu ve yorumları İngilizce kalır; bizim kodumuz Türkçe.
- Lisans GPL-3.0 (Dicio'nunki), README'de Dicio'ya atıf kalır.

## Dağıtım ayrımı (Notlar, Dosya Okuyucu)

- O iki uygulamanın **kendi ses özelliklerine dokunulmaz** (Notlar: `DeviceSpeech`, Dart Antalia, `flutter_tts`;
  Dosya Okuyucu: `tts_service`). Dağıtıldıklarında onlarla devam ederler.
- Asistan köprüsü onlarda **ek ve ayrıktır**, yalnız kişisel derlemeye girer. Genel APK'da köprü bulunmadığı
  manifest denetimiyle doğrulanır.
- Bu depodan o uygulamalara kod kopyalanmaz (GPL): bağlantı yalnız süreçler arası (intent, bağlı servis), sözleşme
  `PROTOKOL.md`'de.

## Kod kuralları

- Saf mantık (dilbilgisi kararı, dikte kuralları, ses kuyruğu, ton hesabı, sayılar) Android'e bağımsız tutulur ve
  JVM testiyle sınanır; Harita'dan taşınan testler yeşil kalır.
- Taşınan davranış **birebir** korunur; Harita'daki ses tuzakları (HAFIZA) bilerek kurulmuş kurallardır, "sadeleştirme"
  diye kaldırılmaz.
- Ana iş parçacığında ağ, disk, model yükleme yok. Döngüde nesne üretme; Regex ve tablolar bir kez derlenir.
- Pil: mikrofon yalnız gerektiği sürece; uyanma `AudioRecord` + Vosk ile (ses odağı almaz). WakeLock yok.
- Depoya sır girmez: Gemini anahtarı ayarlardan, modeller (Vosk, Antalia) çalışma anında iner.
- Kopyala-yapıştır yok; kaldırılan kod aynı işte silinir.

## Derleme ve telefon

- Faz 0'da kurulur. Bulut oturumunda Android SDK `/opt/android-sdk`'ya kurulup `local.properties`'e yazılır.
  Dicio derlemede `dicio-numbers` ve `dicio-sentences-compiler`'ı git'ten çeker.
- Kullanıcının telefonu: Xiaomi (HyperOS), Android 16, 120 Hz. HyperOS arka planı öldürür: pil "kısıtlama yok",
  otomatik başlatma, "arka planda açılır pencere göster" izni.
- Ölçüm (adb): CPU `adb shell top -b -n 1 -p $(adb shell pidof <paket>)`, ses `adb shell dumpsys audio`,
  çökme `adb logcat -b crash -d`.

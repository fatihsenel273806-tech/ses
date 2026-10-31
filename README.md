# ses

Android uygulamaları için ortak Türkçe ses kütüphanesi: uyanma sözü, konuşma tanıma, kesintisiz dikte,
seslendirme, ses işaretleri, Türkçe dil araçları ve niyet motoru. Kotlin'de yazılır; Flutter uygulamalar aynı
kodu `flutter/ses_asistan` eklentisi üzerinden kullanır.

Durum: **planlama** (Faz 0 henüz yapılmadı). Mimari, fazlar ve kararlar için bkz. [`PLAN.md`](PLAN.md).

## Modüller (planlanan)

| Modül | İçerik |
|---|---|
| `cekirdek` | Saf Kotlin: Türkçe normalleştirme ve ek temizliği, sayılar, dikte kuralları, uyanma dilbilgisi, ses kuyruğu, niyet motoru |
| `kulak` | Android `SpeechRecognizer`: tek komut, kesintisiz dikte, bip susturma |
| `uyanma` | Cihazda uyanma sözü (Vosk + dilbilgisi); ses odağı almaz |
| `agiz` | Seslendirme: Antalia (cihazda Türkçe) + Android TTS yedeği, ses işaretleri |
| `flutter/ses_asistan` | Flutter eklentisi (Android'de yukarıdaki modüller) |

## Lisans

Apache-2.0, bkz. [`LICENSE`](LICENSE).

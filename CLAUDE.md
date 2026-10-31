# ses — geliştirme kuralları

Dört uygulamanın ortak Türkçe ses kütüphanesi: uyanma sözü, konuşma tanıma, kesintisiz dikte, seslendirme
(Antalia + Android TTS), ses işaretleri, Türkçe dil araçları ve niyet motoru. Kod Kotlin'de bir kez yazılır;
Flutter uygulamalar aynı kodu eklenti üzerinden kullanır. Yorumlar ve arayüz metinleri **Türkçe**.

Kullanan uygulamalar:

| Uygulama | Depo | Dil | Nasıl alır |
|---|---|---|---|
| Harita Ankara | `fatihsenel273806-tech/harita` | Kotlin (View) | Gradle (JitPack) / `includeBuild` |
| Kanallarım | `fatihsenel273806-tech/YouTube` | Kotlin (Compose) | Gradle (JitPack) / `includeBuild` |
| Notlar | `fatihsenel273806-tech/not` | Flutter | `flutter/ses_asistan` eklentisi (pub git) |
| Dosya Okuyucu | `kaimau1/dosya-okuyucu` | Flutter | `flutter/ses_asistan` eklentisi (pub git) |

## Plan ve hafıza

- **`PLAN.md`**: Faz 0–6 ve kararlar (K1–K5). Kullanıcı **"Faz N'i yap"** dediğinde yalnız o faz yapılır; KARAR
  maddelerinde önce sorulur; bitince maddeler `[x]`, HAFIZA güncellenir, commit + push. Kendiliğinden sonraki faza geçilmez.
- **`HAFIZA.md`**: geçerli kararlar, taşınan tuzaklar, reddedilen yollar. İş başında oku; kalıcı karar, bug kök
  nedeni ya da yanlış çıkan yol oluşunca iş bitmeden yaz.

## Depo AÇIK (Apache-2.0)

- Depoya **sır, API anahtarı, kişisel veri** girmez. Modeller (Vosk, Antalia) çalışma anında indirilir, depoya konmaz.
- **Uygulamaya özel iş mantığı girmez** (rota, video, not kuralları; Yandex; "not al" köprüsü). Burada yalnız ses
  giriş/çıkışı ve genel Türkçe dil araçları durur. Uygulamaya özel olan, uygulamanın verdiği bir arayüzle bağlanır.
- GPL kodu alınmaz (Dicio dahil): kopyalanırsa kullanan uygulamalar da GPL olur. Fikir alınabilir, kod alınmaz.

## Kod kuralları

- **Saf mantık `cekirdek/` içinde, Android'e bağımsız**: JVM testiyle sınanır. Yeni saf mantığa test eklenir.
- Android modülleri ayrı (`kulak`, `uyanma`, `agiz`): uygulama yalnız ihtiyacını alır. Vosk'un yerel kitaplığı
  yalnız `uyanma`'yı alan uygulamaya girer.
- Ana iş parçacığında ağ, disk, model yükleme yok. Döngüde nesne üretme; Regex ve tablolar bir kez derlenir.
- Pil: mikrofon yalnız gerektiği sürece açık; Google tanıyıcısı ses odağı alır (video durur), uyanma bu yüzden
  `AudioRecord` + Vosk ile dinlenir. WakeLock yok.
- Kopyala-yapıştır yok; kaldırılan kod aynı işte silinir.

## Uyumluluk (kırıcı değişiklik)

- Sürüm = git etiketi (`v0.1`, `v0.2`…). Uygulamalar etiketi sabitler; yeni etiket onları kendiliğinden değiştirmez.
- Genel API (public sınıf/işlev, eklenti kanalı) değişirse etiketin küçük numarası artar (`v0.3` → `v0.4`) ve
  `HAFIZA.md`'de "Kırıcı değişiklikler" bölümüne hangi uygulamada neyin değişeceği yazılır.
- Harita'dan taşınan bir davranış **birebir** korunur (sürüş sırasında kullanılıyor): taşımadan önce Harita'daki
  testler buraya gelir, Harita kütüphaneye geçtikten sonra da yeşil kalır.

## Derleme

- Faz 0'da kurulacak (Harita ile aynı AGP/Kotlin sürümleri). Bulut oturumunda Android SDK `/opt/android-sdk`'ya
  kurulup `local.properties`'e yazılabilir; Flutter, Notlar'ın CI sürümüyle (bkz. o deponun CLAUDE.md'si) indirilir.
- Bir uygulamayla birlikte geliştirirken: o uygulamanın `settings.gradle.kts`'ine geçici `includeBuild("../ses")`;
  commit'lenmez, CI etiketli sürümü kullanır.

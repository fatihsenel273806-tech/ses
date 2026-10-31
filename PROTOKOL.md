# Asistan köprü protokolü (sürüm 1)

Asistan (`com.ses.asistan`) ile bağlı uygulamalar arasındaki sözleşme. Bağlantı yalnız süreçler arasıdır (bağlı
servis + `Messenger`): bu depodan uygulamalara kod kopyalanmaz (GPL), her uygulama sabitleri bu belgeden kendi koduna
yazar. Asistan tarafı: `app/src/main/kotlin/com/ses/asistan/kopru/`. Harita tarafı: `AssistantBridge.kt`,
`AssistantBridgeService.kt`.

## Keşif

- Uygulama manifestinde dışa açık bir **köprü servisi** bildirir: eylem `com.ses.asistan.KOPRU`, `meta-data`
  `com.ses.asistan.yetenekler` → `@xml/ses_yetenekler`.
- `ses_yetenekler.xml`:

  ```xml
  <asistan uyanma="harita" devir="navigasyonda" surum="1">
      <komut ad="git" aciklama="Bir yere rota kurup yola çıkar" ornek="Kızılay'a git" />
  </asistan>
  ```

  - `uyanma`: doğrudan uyanma adı ("Hey Harita …"). Vosk küçük Türkçe modelinin sözlüğünde olmalı (HAFIZA).
  - `devir`: asistanın hiçbir becerisi anlamadığında metnin bu uygulamaya ne zaman verileceği: `hayir`,
    `navigasyonda`, `onde` (ekrandayken ya da navigasyonda), `her_zaman`.
  - `komut`: Faz 6'da Gemini araç bildirimi olur (ad → Türkçe açıklama).
- Asistan bunları `PackageManager` ile okur. Sonuç paketin güncellenme anına göre önbellekte tutulur: uygulama kurulunca
  ya da güncellenince kendiliğinden yeniden okunur.

## Güvenlik

Dört uygulama farklı anahtarla imzalı olduğu için `signature` izni işe yaramaz; `signature|knownSigner` (Android 12+):

| İzin | Tanımlayan | `knownCerts` | Koruduğu |
|---|---|---|---|
| `com.ses.asistan.izin.SES` | asistan | Harita, Kanallarım (Dosya Okuyucu Faz 5; Notlar'ın gerekmiyor, yayınla konuşur) | asistanın ses servisi |
| `com.harita.ankara.izin.ASISTAN` | Harita | asistan | Harita'nın köprü servisi |
| `dev.kaimau.notlar.izin.ASISTAN` | Notlar (yalnız kişisel derleme) | asistan, Harita, Kanallarım | Notlar'ın yayın alıcısı |
| `com.dosyaokuyucu.dosya_okuyucu.izin.ASISTAN` | Dosya Okuyucu (yalnız kişisel derleme) | asistan | Dosya Okuyucu'nun köprü servisi |

Sertifikalar (SHA-256, iki nokta üst üste olmadan, küçük harf):

| Uygulama | SHA-256 |
|---|---|
| Asistan (`app/asistan.keystore`) | `9f9ba7d6e0465f41e500744010cdb5e8428a09a5ec6d6e33ccd6510ff4209551` |
| Harita (`app/debug.keystore`) | `d03a97662ef327430eae1debfd7632c29deacd156a146f0e9bc7236177b3359c` |
| Kanallarım (`app/sabit.keystore`) | `ccfa3c3353148fb89f2d6860c2359e15498c64022c910d75fbc5b2483d70a1c7` |
| Notlar | gerekmiyor (asistana bağlanmıyor); kişisel CI işi `apksigner verify --print-certs` ile yazdırır |
| Dosya Okuyucu | gerekmiyor (asistanın ses servisine bağlanmıyor; `SONUÇ`'u `replyTo` ile verir) |

Tuzak: izni kullanan uygulama, izni tanımlayan uygulamadan önce kurulduysa ve köprü çalışmıyorsa kullanan uygulama
yeniden kurulur.

## Kanal

- **Uygulama açıkken**: uygulama asistanın ses servisine bağlanır (eylem `com.ses.asistan.SES`, paket
  `com.ses.asistan`, `BIND_AUTO_CREATE | BIND_INCLUDE_CAPABILITIES`). Ekrandayken bağlandığı için mikrofon yetkisi
  geçer, asistan hiç açılmadan uyanma servisini başlatır. İlk ileti `MERHABA`'dır, `replyTo` uygulamanın Messenger'ı.
  Bağlantı ölünce (`linkToDeath`) asistan o uygulamanın seslerini keser, durumunu düşürür.
- **Uygulama kapalıyken** (metin devri): asistan uygulamanın köprü servisine bağlanır, `METİN`'i verir (`replyTo`
  asistanın Messenger'ı). Uygulama ekran açmak isterse `AÇ` ile PendingIntent döner; asistan 15 sn sonra bağlantıyı bırakır.
- İleti türü `Message.what`, alanlar `Message.data` (Bundle). Gönderen `Message.sendingUid` ile tanınır.

### Uygulama → asistan

| what | Ad | Alanlar | Anlamı |
|---|---|---|---|
| 1 | MERHABA | — | kayıt; cevap `HOŞGELDİN` |
| 2 | SÖYLE | `metin`, `tur`, `kimlik` (Int), `isaret` (Boolean, vars. true) | tek ses kuyruğuna; okununca ya da atılınca `SÖYLENDİ` |
| 3 | SUSTUR | `tur` (ops.) | yalnız bu uygulamanın bekleyen ve süren sesleri |
| 4 | DİNLE | — | tek komut dinlenir; duyulan `METİN` olarak yalnız bu uygulamaya, duyulmazsa `DUYULMADI` |
| 5 | DİKTE | `kimlik`, `baslangic` | kesintisiz dikte; bitince `DİKTE_SONUÇ` |
| 6 | DURUM | `anahtar`, `deger` (Boolean) | `navigasyon`, `onde`, `uyanma` |
| 7 | AÇ | `pi` (PendingIntent) | asistan ekranı açar (arka plandan) |
| 8 | DİNLEME_İPTAL | — | bu uygulama için süren dinleme/dikte bırakılır |
| 9 | SONUÇ | `kimlik`, `metin`, `tamam` (Boolean), `pi` (ops. PendingIntent) | `KOMUT`'un cevabı; `metin`'i asistan söyler, `pi` varsa ekranı açar |

### Asistan → uygulama

| what | Ad | Alanlar | Anlamı |
|---|---|---|---|
| 101 | HOŞGELDİN | `surum`, `uyanma_hazir` | asistan uyanma sözlerini dinleyebiliyor mu; true ise uygulama kendi uyanma dinlemesini kapatır (mikrofonu aynı anda tek uygulama alır). Hazırlık değişince yeniden gelir |
| 102 | SÖYLENDİ | `kimlik`, `bayat` | `bayat` true: okunamadan bayatladı, uygulama o anki tazesini yeniden yollayabilir |
| 103 | METİN | `metin`, `uyanma` | kullanıcının söylediği; `uyanma` true: uyanma sözünden hemen sonra (ilk hece kaçmış olabilir: "al süt" → not) |
| 104 | DUYULMADI | — | `DİNLE`'de bir şey duyulmadı |
| 105 | DİKTE_SONUÇ | `kimlik`, `metin` (null: iptal ya da boş) | |
| 106 | KISMI | `metin` | dinlerken canlı metin |
| 107 | KOMUT | `kimlik`, `ad`, `sorgu` | yapılandırılmış komut (`ses_yetenekler.xml`'deki `komut` adları); cevap `SONUÇ`. Asistan 10 sn (özet 45 sn) bekler |

### Ses sınıfları (`tur`)

Öncelik: `talimat` > `uyari` > `cevap` > `bilgi`; aynı sınıfta geliş sırası.

- `talimat` (dönüş): hepsinin önüne geçer, yenisi eskisinin yerini alır. 4 sn'den eski talimat okunmaz, `SÖYLENDİ(bayat)`
  döner; uygulama tazesini üretip yeniden yollar (Harita `Navigator.restate`).
- `uyari`: önce ses işareti (`isaret` false ise uygulama kendi işaretini çalıyor), 15 sn'den eskisi atlanır.
- `cevap`: bayatlamaz. `bilgi`: 15 sn'den eskisi atlanır.
- Okunmadan çıkan her öğe (aynısı sırada, yerini yenisi aldı, taşma, bayat, susturuldu) `SÖYLENDİ` ile bildirilir:
  uygulamanın kendi zinciri askıda kalmaz.

### Durum anahtarları (`DURUM`)

- `navigasyon`: uygulama navigasyonda. K3 politikasında "araçta" sayılır; `devir="navigasyonda"` metin devrini açar.
- `onde`: uygulama ekranda; `devir="onde"` ve "İlgili uygulamalar açıkken" dinleme kipi için.
- `uyanma`: uygulamanın kendi ayarı şimdi uyanma dinlemesi istiyor (Harita "her zaman" kipi, ekrandayken).

## Yönlendirme (asistan)

1. Uyanma adı ("Hey Harita …", "Hey Kanallar …"); cümlenin başında söylenen ad da sayılır.
   - Köprüsü olan uygulamanın adıysa: `METİN` (uygulamanın kendi komut anlayışına; asistanda kuralları yeniden yazılmaz).
   - `kanallar`: asistanın Kanallarım becerisi (Kanallarım'da köprü yok; MediaBrowser + `not_al` oturum komutu).
   - Uygulama adıyla uyanılınca asistanın ekranı açılmaz, cevap sesli gelir.
2. Dicio becerileri (bizimkiler dahil).
3. Hiçbiri anlamazsa metin devri: devri o an kabul eden uygulama (önce navigasyondaki).
4. Gemini (Faz 6).

## Uygulama tarafında düşme kuralları

- Asistan kurulu değilse ya da bağlanılamazsa uygulama her şeyi kendi yoluyla yapar.
- Bağlantı koparsa (asistan zorla durduruldu) o an asistanda okunmayı bekleyen cümle uygulamanın kendi sesiyle okunur;
  bekleyen dinleme boş sonuçla biter. Asistan 30 sn içinde `SÖYLENDİ` demezse cümle bitti sayılır.

## Notlar yayınları (Faz 4)

Notlar'ın köprü servisi yok (Flutter uygulaması; asistanla konuşma tek yönlü ve kısa). Asistan, Harita ve Kanallarım
**sıralı yayınla** (`sendOrderedBroadcast`, bileşen `dev.kaimau.notlar/.HaritaNotAlici`,
`FLAG_INCLUDE_STOPPED_PACKAGES | FLAG_RECEIVER_FOREGROUND`) yazar ve sorar. Alıcı yalnız Notlar'ın **kişisel**
derlemesinde var (`android/app/src/kisisel`, `NOTLAR_KISISEL=1`); genel APK'da yoktur, yayın cevapsız kalır (kod 0).
Notlar öne gelmez, Flutter motoru açılmaz: alıcı uygulamanın Firebase Android SDK'sıyla doğrudan yazar/okur.

| Eylem | Ekler | Cevap kodu | Cevap ekleri |
|---|---|---|---|
| `dev.kaimau.notlar.HARITA_NOT` | `text`, `title`, `folder` (yoksa "Haritalar"), `place`, `pinned` | 1 yazıldı (sunucu onayladı), 2 sırada (çevrimdışı ya da giriş yok; bağlantı/giriş gelince yazılır), 0 olmadı | — |
| `dev.kaimau.notlar.ASISTAN_SORGU` | `istek` (`son` \| `ara`), `terimler` (ArrayList<String>, `ara`'da hepsi notta geçmeli), `adet` (1–10) | 1 tamam, 3 giriş yok, 0 olmadı | `basliklar`, `metinler` (ArrayList<String>), `zamanlar` (long[], ms) |

- Silinen, arşivlenen, **kilitli** notlar ve **gizli klasördeki** notlar sorguda asla verilmez.
- Klasör adıyla bulunur (büyük/küçük harf duyarsız), yoksa açılır. Asistanın aldığı notlar "Asistan" klasörüne,
  Harita'nınkiler "Haritalar"a, Kanallarım'ınkiler "YouTube"a (sabitli).
- Kanallarım'ın `transcript` eki şimdilik yok sayılır (Notlar'da tam transkript/AI düzeltme yolu yok).

## Dosya Okuyucu komutları (Faz 5)

Dosya Okuyucu'nun köprü servisi yalnız **kişisel** derlemesinde var (`DOSYA_KISISEL=1 bash ci/prepare_android.sh`,
`ci/kisisel/`); genel APK'da yoktur (betik manifesti denetler), asistan "kişisel sürümü kurulu değil" der.
`devir="hayir"`: serbest metin verilmez; asistan cümleyi kendisi çözer (`DosyalarSozleri`) ve `KOMUT` yollar.
Servis iletiyi gönderenin paketini de denetler (yalnız `com.ses.asistan`).

| `ad` | `sorgu` | Uygulamada |
|---|---|---|
| `ac` | dosya adında geçecek söz; boş: son açılan | MediaStore ad araması (Gemini işlevleriyle aynı AI kapsamı: kilitli ve AI dışı klasörler dönmez), en yenisi; `pi` = `file:` bağlantısı |
| `oku` | boş ya da dosya adı | uygulamanın kendi sesli okuması (`TtsService`) — **henüz bağlı değil** (PLAN K7) |
| `durdur` | — | sesli okumayı durdurur — **henüz bağlı değil** (K7) |
| `ozetle` | boş ya da dosya adı | uygulamanın Gemini'siyle özet; uzun sonuç `SÖYLE(cevap)` ile de gelebilir — **henüz bağlı değil** (K7) |

## Değişiklik kuralı

İleti ya da izin değişirse `SURUM` artar, HAFIZA "Köprü protokolü değişiklikleri"ne hangi uygulamada neyin değişeceği yazılır.

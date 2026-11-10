# _anx Auction Mod

DonutSMP için client-side Fabric modu. Açık artırma başlatırsın, sana `/pay` ile
en çok parayı gönderen öne geçer. Gelen ödemeler sohbetten otomatik takip edilir.

- Minecraft **1.21.11** (kök klasör, Java 21) ve **26.2** (`mc-26.2/` klasörü, Java 25), Fabric Loader, Fabric API
- Menü tuşu: **Sağ Shift** (Ayarlar > Kontroller > "_anx Auction Mod" altından değiştirilebilir). Başka kısayol yok

## Özellikler

- Eşya seçici (creative envanteri gibi): sekmeler (All / Blocks / Gear / Food / Eggs / Rare / Inv), arama, fare tekerleği ile kaydırma, kaydırma çubuğunu tutup sürükleme veya çubuğa tıklayarak atlama, altta hotbar'ın. Oyundaki TÜM eşyalar listelenir; "Inv" sekmesi ve hotbar enchantlı / özel isimli gerçek eşyalarını seçer. Adet kutusu var. "Use hand" ile eldeki eşya da seçilebilir
- Hologram: eşya, adının (örn. **Elytra x64**) hemen **üstünde** durur ve döner; altında teklif, kalan süre ve `/pay` ipucu yazar. Yeni teklifte isim beyaz yanıp söner, son 10 saniyede süre kırmızı/beyaz yanıp söner, satılınca isim renk değiştirir. Sadece sende görünür (client-side). Seni **takip eder**: önünde 3 blok ileride durur (`holoForward` / `holoUp` ile ayarlanır, `hologramFollow=false` ile sabit konuma döner). Setup'ta "Holo" ile kapat
- Sohbet etiketi: `X paid you $1M` satırının yanına çarpan eklenir: `[2x]` (taban miktarın tam katıysa yeşil kalın, örn. 2x / 4x / 10x; değilse sarı, örn. 1.5x). Taban miktar Setup ekranındaki **Chat tag base** kutusundan, açık artırma sırasında Auction ekranının altındaki **Chat base** kutusundan (ve **Set** düğmesinden) ayarlanır. Setup'taki **Chat tag** düğmesi ile (veya `chatTag=false`) kapanır. HUD'da ekranda 2x/4x/10x kutuları ve ayrı bir quick pay ekranı artık yok
- HUD'u sürükle: Setup / Auction ekranındaki **Move HUD** düğmesi. Paneli tutup istediğin yere sürükle, bırakınca kaydolur. "Reset position" varsayılana döner. Açık artırma yokken örnek panel gösterilir
- HUD: yuvarlak köşeli panel; eşya, teklif (çarpanıyla, örn. `$1M (2x)`), lider, sonraki minimum teklif, geri sayım çubuğu ve en iyi 3 teklif listesi. Açık artırma bitince 20 saniye kalır ve yukarı kayarak kapanır
- Efektler: HUD zıplayarak kayıp girer, panel nefes alır gibi parlar, teklif sayısı yeni değere doğru yuvarlanarak artar, her yeni teklifte `+$1M` yukarı süzülür, parlama ve şok halkaları, geri sayım çubuğunda kayan ışık, son 10 saniyede kırmızı titreşim, kazanana konfeti ve parıltılar (Setup'taki **FX** düğmesiyle kapanır)
- Sesler: açılışta zil + yükselen arpej, her teklifte bir basamak yükselen nota (teklif iki katına çıkarsa ekstra zil), sayılmayan ödemede alçak nota, 60 ve 30. saniyede hafif çınlama, 10. saniyede uyarı, son 5 saniyede yükselen tik, kazanan için fanfar, teklifsiz bitişte düşen notalar (**Sound** düğmesiyle kapanır)
- Eldeki eşyayı seç, süre / minimum teklif / maksimum teklif / minimum artış ayarla
- Kısa yazım: `5m`, `1h30m`, `90s` (süre) ve `500k`, `1.5m`, `2b` (para)
- Maksimum teklife (buyout) ulaşılırsa açık artırma hemen biter; üstündeki ödemeler sayılmaz
- Anti-snipe: son saniyelerde gelen teklif süreyi uzatır (0 = kapalı)
- Ekranın üstünde HUD paneli: eşya, teklif sayısı, en yüksek teklif (+çarpan), kalan süre, sonraki minimum teklif ve sağda en iyi 3 teklif. Tam chip miktarında ödeme gelirse o kutu parlar
- Ödeme kaydı: sayılan ve sayılmayan ödemeler çarpanı ve sebebiyle birlikte görünür (para iadesi gerekirse bilirsin)
- Sohbete duyuru butonları (başlangıç / kazanan) — sadece butona basınca mesaj gönderir
- Sohbetteki "X paid you $1.5K" satırları satırın başında olmalı; oyuncu sohbetinde yazılan sahte satırlar sayılmaz
- Sunucu ödemeleri doğrulanmaz, sadece sohbet mesajları okunur. Sohbet tanınmazsa elle kayıt alanı var

Ayarlar `config/anx-auction.properties` dosyasına kaydedilir (HUD konumu için `hudOffsetX` / `hudOffsetY`, artık sürükleyerek de ayarlanır; chat taban miktarı `chatBase`, eski `quickBase` değeri otomatik okunur).

## Jar dosyasını almak (ücretsiz)

1. Bu klasörün içindekileri bir GitHub deposuna yükle (`.github` klasörü de gitmeli).
2. Depoda **Actions** sekmesinde `build` çalışmasını başlat (**Run workflow**) veya bitmesini bekle.
3. Çalışmanın altındaki **anx-auction-mod** çıktısını indir, zip'ten `anx-auction-mod-1.0.0.jar` çıkar.
4. Jar'ı `.minecraft/mods` klasörüne koy (Fabric API ile birlikte).

## Sürümler

- `build.gradle` (kök): 1.21.11, Yarn eşlemeleri, Java 21
- `mc-26.2/`: 26.2, Mojang isimleri (Fabric artık Yarn yayınlamıyor), Java 25
- Actions iki jar üretir: `anx-auction-mod-1.21.11` ve `anx-auction-mod-26.2`

## Kendi bilgisayarında derlemek

JDK 21 ve Gradle 9.2.1 kurulu olmalı:

```
gradle build
```

Jar: `build/libs/anx-auction-mod-1.0.0.jar`

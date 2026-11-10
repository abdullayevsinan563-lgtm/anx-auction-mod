# _anx Auction Mod

DonutSMP için client-side Fabric modu. Açık artırma başlatırsın, sana `/pay` ile
en çok parayı gönderen öne geçer. Gelen ödemeler sohbetten otomatik takip edilir.

- Minecraft **1.21.11** (kök klasör, Java 21) ve **26.2** (`mc-26.2/` klasörü, Java 25), Fabric Loader, Fabric API
- Menü tuşu: **Sağ Shift** (Ayarlar > Kontroller > "_anx Auction Mod" altından değiştirilebilir)

## Özellikler

- Eşya seçici (creative envanteri gibi): sekmeler (All / Blocks / Gear / Food / Eggs / Rare / Inv), arama, fare tekerleğiyle satır satır kaydırma, altta hotbar'ın. Oyundaki TÜM eşyalar listelenir; "Inv" sekmesi ve hotbar enchantlı / özel isimli gerçek eşyalarını seçer. Adet kutusu var. "Use hand" ile eldeki eşya da seçilebilir
- Quick pay (Sağ Ctrl): HUD'daki 2x / 4x / 10x kutuları gerçek butonlara dönüşür, doğrudan HUD'un üstüne tıklarsın. Taban miktar x çarpan, tek tıkla `/pay`. Çarpan ekle / sil, varsayılan olarak ilk tık hazırlar, ikinci tık gönderir (Instant ile tek tık). Açık artırma olmasa da HUD'da görünür
- Hologram: açık artırma eşyası önünde süzülen dönen item + hologram yazısı (eşya, teklif, kalan süre, kazanan). Sadece sende görünür (client-side). "Move hologram here" ile yerini değiştir, Setup'ta "Holo" ile kapat
- Efektler: HUD kayarak girer, yeni teklifte parlama, geri sayım çubuğu, son 10 saniyede kırmızı nabız, kazanana konfeti (Setup ekranından kapatılabilir)
- Sesler: teklif geldikçe yükselen ses, son 5 saniyede tık sesi, kazanan sesi (Sound düğmesiyle kapanır)
- Eldeki eşyayı seç, süre / minimum teklif / maksimum teklif / minimum artış ayarla
- Kısa yazım: `5m`, `1h30m`, `90s` (süre) ve `500k`, `1.5m`, `2b` (para)
- Maksimum teklife (buyout) ulaşılırsa açık artırma hemen biter; üstündeki ödemeler sayılmaz
- Anti-snipe: son saniyelerde gelen teklif süreyi uzatır (0 = kapalı)
- Ekranın üstünde HUD paneli: eşya, en yüksek teklif, kalan süre
- Ödeme kaydı: sayılan ve sayılmayan ödemeler sebebiyle birlikte görünür (para iadesi gerekirse bilirsin)
- Sohbete duyuru butonları (başlangıç / kazanan) — sadece butona basınca mesaj gönderir
- Sohbetteki "X paid you $1.5K" satırları satırın başında olmalı; oyuncu sohbetinde yazılan sahte satırlar sayılmaz
- Sunucu ödemeleri doğrulanmaz, sadece sohbet mesajları okunur. Sohbet tanınmazsa elle kayıt alanı var

Ayarlar `config/anx-auction.properties` dosyasına kaydedilir (HUD konumu için `hudOffsetX` / `hudOffsetY`).

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

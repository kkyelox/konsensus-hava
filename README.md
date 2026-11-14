# Konsensüs Hava

9 küresel hava modeli (ECMWF IFS, ECMWF AIFS yapay zekâ modeli, NOAA GFS, DWD ICON, CMC GEM, JMA, Météo-France, UKMO, CMA),
3 ansambl sistemi (~120 üye), ERA5 yeniden analizi, NASA GIBS uydu görüntüleri ve RainViewer radarını birleştiren 16 günlük hava tahmin sitesi.

- Her konum için modellerin son 40 günlük hatası ölçülür; sapma düzeltilir ve modeller `1/RMSE²` ile ağırlıklandırılır.
- Günlük güven yüzdesi: `P(|hata| ≤ 2°C) = erf(2/σ√2)`.
- 8–16. günler ölçülen beceri oranında mevsim normaline sönümlenir.
- Konum tutarlılık skoru, model kararlılığı ve uyumu her açılışta yeniden hesaplanır.

## Sürekli çalışan kısım
`.github/workflows/daily.yml` her gün 06:17'de (TR) çalışır: `public/data/cities.json` içindeki şehirler için tahmini kaydeder,
geçmiş tahminleri gerçekleşen değerlerle karşılaştırır ve `public/data/verification.json` dosyasını günceller.
Commit sonrası Cloudflare siteyi otomatik yeniden yayınlar. Şehir eklemek için `cities.json` dosyasını düzenle.

## Yayın
- **Cloudflare**: Workers & Pages → Git deposunu bağla. Statik dosyalar `public/` klasöründe (`wrangler.jsonc`).
- **GitHub Pages** (isteğe bağlı): Settings → Pages → GitHub Actions veya `public` klasörü.

Veri: Open-Meteo (CC BY 4.0), ECMWF, NOAA, DWD, CMC, JMA, Météo-France, UKMO, CMA, Copernicus ERA5, NASA GIBS, RainViewer, OpenStreetMap, Esri.

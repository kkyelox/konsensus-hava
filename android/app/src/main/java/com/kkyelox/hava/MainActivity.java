package com.kkyelox.hava;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.util.Locale;

public class MainActivity extends Activity {
    SharedPreferences sp;
    TextView current, msg;
    LinearLayout results;
    final Handler ui = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);
        sp = getSharedPreferences("hava", MODE_PRIVATE);
        current = findViewById(R.id.current);
        msg = findViewById(R.id.msg);
        results = findViewById(R.id.results);
        showCurrent();
        EditText q = findViewById(R.id.query);
        findViewById(R.id.search).setOnClickListener(v -> search(q.getText().toString()));
        q.setOnEditorActionListener((v, id, e) -> {
            if (id == EditorInfo.IME_ACTION_SEARCH) { search(q.getText().toString()); return true; }
            return false;
        });
        findViewById(R.id.gps).setOnClickListener(v -> useGps());
        findViewById(R.id.open).setOnClickListener(v ->
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(RainWidget.SITE))));
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (sp.contains("lat")) refreshNow();
    }

    /** Uygulama ön plandayken veriyi çeker, widget'a yazar ve sonucu/hatayı gösterir. */
    void refreshNow() {
        msg.setText("Veri alınıyor…");
        final Context app = getApplicationContext();
        final double la = Double.longBitsToDouble(sp.getLong("lat", 0)), lo = Double.longBitsToDouble(sp.getLong("lon", 0));
        new Thread(() -> {
            String out;
            try {
                Forecast.Result r = Forecast.fetch(la, lo);
                RainWidget.render(app, r);
                StringBuilder b = new StringBuilder("✓ Widget güncellendi. Şu an " + (Double.isNaN(r.temp) ? "--" : Math.round(r.temp)) + "°\n");
                for (int d = 0; d < 3; d++) {
                    Forecast.Day day = r.days[d];
                    b.append(RainWidget.dayName(day.date, d)).append(": ")
                     .append(day.pop < 0 ? "--" : "%" + Math.round(day.pop * 100)).append(" ")
                     .append(Forecast.label(Forecast.level(day.pop)))
                     .append(day.window.isEmpty() ? "" : " (" + day.window + ")").append('\n');
                }
                if (r.errors.length() > 0) b.append("Not: ").append(r.errors);
                out = b.toString();
            } catch (Throwable e) {
                out = "⚠ Veri alınamadı: " + e.getClass().getSimpleName() + " — " + e.getMessage()
                        + "\nİnternet açık mı? Pil tasarrufu / veri tasarrufu bu uygulamayı engelliyor olabilir.";
            }
            final String o = out;
            ui.post(() -> msg.setText(o));
        }).start();
    }

    void showCurrent() {
        current.setText(sp.contains("lat") ? "Seçili konum: " + sp.getString("name", "") : "Seçili konum: yok");
    }

    void save(double lat, double lon, String name) {
        sp.edit().putLong("lat", Double.doubleToRawLongBits(lat)).putLong("lon", Double.doubleToRawLongBits(lon))
                .putString("name", name).apply();
        showCurrent();
        refreshNow();
    }

    void search(String text) {
        String t = text.trim();
        if (t.length() < 2) return;
        msg.setText("Aranıyor…");
        results.removeAllViews();
        new Thread(() -> {
            try {
                JSONObject j = new JSONObject(Forecast.get("https://geocoding-api.open-meteo.com/v1/search?count=6&language=tr&format=json&name="
                        + URLEncoder.encode(t, "UTF-8")));
                JSONArray r = j.optJSONArray("results");
                ui.post(() -> {
                    msg.setText(r == null || r.length() == 0 ? "Sonuç yok" : "");
                    if (r == null) return;
                    for (int i = 0; i < r.length(); i++) {
                        JSONObject x = r.optJSONObject(i);
                        String name = x.optString("name");
                        String sub = x.optString("admin1", "") + (x.has("country") ? ", " + x.optString("country") : "");
                        Button bt = new Button(this);
                        bt.setAllCaps(false);
                        bt.setText(name + " — " + sub);
                        final double la = x.optDouble("latitude"), lo = x.optDouble("longitude");
                        bt.setOnClickListener(v -> { results.removeAllViews(); save(la, lo, name); });
                        results.addView(bt);
                    }
                });
            } catch (Exception e) {
                ui.post(() -> msg.setText("Arama başarısız: internet bağlantısını kontrol et."));
            }
        }).start();
    }

    void useGps() {
        if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, 7);
            return;
        }
        msg.setText("Konum alınıyor…");
        LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
        Location best = null;
        try {
            for (String p : lm.getProviders(true)) {
                Location l = lm.getLastKnownLocation(p);
                if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
            }
        } catch (SecurityException ignored) { }
        if (best != null && System.currentTimeMillis() - best.getTime() < 6 * 3600_000L) {
            gotLocation(best);
            return;
        }
        try {
            String prov = lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) ? LocationManager.NETWORK_PROVIDER : LocationManager.GPS_PROVIDER;
            if (Build.VERSION.SDK_INT >= 30) {
                lm.getCurrentLocation(prov, null, getMainExecutor(), l -> {
                    if (l != null) gotLocation(l); else msg.setText("Konum alınamadı. Konum servisini aç ya da şehir ara.");
                });
            } else {
                final Location fallback = best;
                lm.requestSingleUpdate(prov, new android.location.LocationListener() {
                    @Override public void onLocationChanged(Location l) { gotLocation(l); }
                    @Override public void onStatusChanged(String s, int i, Bundle b) { }
                    @Override public void onProviderEnabled(String s) { }
                    @Override public void onProviderDisabled(String s) { if (fallback != null) gotLocation(fallback); }
                }, Looper.getMainLooper());
            }
        } catch (Exception e) {
            if (best != null) gotLocation(best);
            else msg.setText("Konum alınamadı. Konum servisini aç ya da şehir ara.");
        }
    }

    void gotLocation(Location l) {
        final double la = l.getLatitude(), lo = l.getLongitude();
        msg.setText("Konum bulundu, yer adı alınıyor…");
        new Thread(() -> {
            String name = String.format(Locale.US, "%.3f, %.3f", la, lo);
            try {
                JSONObject j = new JSONObject(Forecast.get(String.format(Locale.US,
                        "https://api.bigdatacloud.net/data/reverse-geocode-client?latitude=%.4f&longitude=%.4f&localityLanguage=tr", la, lo)));
                String loc = j.optString("locality", "");
                if (loc.isEmpty()) loc = j.optString("city", "");
                if (!loc.isEmpty()) name = loc;
            } catch (Exception ignored) { }
            final String n = name;
            ui.post(() -> save(la, lo, n));
        }).start();
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        if (code == 7 && res.length > 0 && res[0] == PackageManager.PERMISSION_GRANTED) useGps();
        else if (code == 7) msg.setText("Konum izni verilmedi — şehir arayabilirsin.");
    }
}

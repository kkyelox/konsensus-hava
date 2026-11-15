package com.kkyelox.hava;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.GeolocationPermissions;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.net.URLEncoder;
import java.util.Locale;

/** Uygulama = site. Konum ve widget ayarları köprüyle bağlı. */
public class MainActivity extends Activity {
    static final String SITE = "https://kkyelox.github.io/konsensus-hava/";
    static final String HOST = "kkyelox.github.io";
    WebView web;
    GeolocationPermissions.Callback geoCb;
    String geoOrigin;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(Color.parseColor("#1E3A8A"));
        web = new WebView(this);
        web.setBackgroundColor(Color.parseColor("#EEF3F9"));
        setContentView(web);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setGeolocationEnabled(true);
        s.setUserAgentString(s.getUserAgentString() + " KonsensusHavaApp/1");
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest req) {
                Uri u = req.getUrl();
                if (HOST.equals(u.getHost())) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception ignored) { }
                return true;
            }

            @Override
            public void onReceivedError(WebView v, WebResourceRequest req, WebResourceError err) {
                if (req.isForMainFrame()) {
                    v.loadDataWithBaseURL(null, "<html><body style='font-family:sans-serif;padding:30px;background:#EEF3F9'>"
                            + "<h2>İnternet bağlantısı yok</h2><p>Site yüklenemedi. Widget'lar son alınan veriyi göstermeye devam eder.</p>"
                            + "<p><a href='" + SITE + "' style='font-size:18px'>↻ Tekrar dene</a></p></body></html>", "text/html", "UTF-8", null);
                }
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback cb) {
                if (Loc.hasPermission(MainActivity.this)) { cb.invoke(origin, true, false); return; }
                geoCb = cb; geoOrigin = origin;
                Gps.ask(MainActivity.this);
            }
        });
        web.addJavascriptInterface(new Bridge(getApplicationContext()), "HavaApp");
        web.loadUrl(url(getIntent()));
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        setIntent(i);
        web.loadUrl(url(i));
    }

    @Override
    protected void onResume() {
        super.onResume();
        Ticker.schedule(getApplicationContext());
        BaroTicker.schedule(getApplicationContext());
        { final Context ac = getApplicationContext(); new Thread(() -> Baro.record(ac)).start(); }
        // Otomatik konumdaysa konumu tazele ve widget'ları güncelle
        final Context app = getApplicationContext();
        if (Loc.hasPermission(this) && Loc.usesDevice(app) && Loc.freshDue(app)) Gps.freshAndSave(this, null);
        else new Thread(() -> Updater.updateAll(app, false)).start();
    }

    String url(Intent i) {
        String tab = i.getStringExtra("tab");
        double lat = i.getDoubleExtra("lat", Double.NaN), lon = i.getDoubleExtra("lon", Double.NaN);
        String name = i.getStringExtra("name");
        if (Double.isNaN(lat)) {
            Loc g = Loc.global(getApplicationContext(), false);
            if (g != null) { lat = g.lat; lon = g.lon; name = g.name; }
        }
        StringBuilder u = new StringBuilder(SITE).append("?app=1");
        if (!Double.isNaN(lat)) {
            u.append(String.format(Locale.US, "&lat=%.4f&lon=%.4f", lat, lon));
            try { u.append("&name=").append(URLEncoder.encode(name == null ? "" : name, "UTF-8")); } catch (Exception ignored) { }
        }
        if (tab != null && !tab.isEmpty()) u.append('#').append(tab);
        return u.toString();
    }

    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] p, int[] r) {
        boolean ok = Loc.hasPermission(this);
        if (geoCb != null) { geoCb.invoke(geoOrigin, ok, false); geoCb = null; }
        if (ok && Loc.usesDevice(getApplicationContext())) Gps.freshAndSave(this, null);
    }

    /** Siteden çağrılır: window.HavaApp */
    class Bridge {
        final Context app;
        Bridge(Context c) { app = c; }

        @JavascriptInterface
        public void openSettings() {
            runOnUiThread(() -> startActivity(new Intent(MainActivity.this, SettingsActivity.class)));
        }

        /** Kullanıcı sitede yer seçti (gps=true: "Konumum"). */
        @JavascriptInterface
        public void onLocation(double lat, double lon, String name, boolean gps) {
            if (Double.isNaN(lat) || Double.isNaN(lon)) return;
            new Thread(() -> {
                if (gps) { Loc.setDevice(app, lat, lon, System.currentTimeMillis(), true); Loc.setGlobalAuto(app); }
                else Loc.setGlobalFixed(app, lat, lon, name == null ? "Konum" : name);
                Updater.updateAll(app, false);
            }).start();
        }

        @JavascriptInterface
        public String locationMode() { return Loc.globalMode(app); }

        @JavascriptInterface
        public boolean hasBarometer() { return Baro.available(app); }

        /** [[zaman_ms, hPa(istasyon)], ...] son 48 saat */
        @JavascriptInterface
        public String baroReadings() { return Baro.json(app); }

        @JavascriptInterface
        public void recordBaro() { new Thread(() -> Baro.record(app)).start(); }
    }
}

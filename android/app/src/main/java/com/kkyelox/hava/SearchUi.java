package com.kkyelox.hava;

import android.app.Activity;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;

/** Şehir arama kutusu (Ayarlar ve widget ayarı ekranında ortak). */
public class SearchUi {
    public interface Pick { void pick(double lat, double lon, String name); }

    public static void attach(Activity a, Pick pick) {
        EditText q = a.findViewById(R.id.query);
        LinearLayout results = a.findViewById(R.id.results);
        TextView msg = a.findViewById(R.id.msg);
        Runnable go = () -> {
            String t = q.getText().toString().trim();
            if (t.length() < 2) return;
            msg.setText("Aranıyor…");
            results.removeAllViews();
            new Thread(() -> {
                try {
                    JSONObject j = new JSONObject(Forecast.get("https://geocoding-api.open-meteo.com/v1/search?count=6&language=tr&format=json&name="
                            + URLEncoder.encode(t, "UTF-8")));
                    JSONArray r = j.optJSONArray("results");
                    a.runOnUiThread(() -> {
                        msg.setText(r == null || r.length() == 0 ? "Sonuç yok" : "");
                        if (r == null) return;
                        for (int i = 0; i < r.length(); i++) {
                            JSONObject x = r.optJSONObject(i);
                            String name = x.optString("name");
                            String sub = x.optString("admin1", "") + (x.has("country") ? ", " + x.optString("country") : "");
                            Button bt = new Button(a);
                            bt.setAllCaps(false);
                            bt.setText(name + " — " + sub);
                            final double la = x.optDouble("latitude"), lo = x.optDouble("longitude");
                            bt.setOnClickListener(v -> { results.removeAllViews(); q.setText(""); pick.pick(la, lo, name); });
                            results.addView(bt);
                        }
                    });
                } catch (Exception e) {
                    a.runOnUiThread(() -> msg.setText("Arama başarısız: internet bağlantısını kontrol et."));
                }
            }).start();
        };
        a.findViewById(R.id.search).setOnClickListener(v -> go.run());
        q.setOnEditorActionListener((v, id, e) -> { if (id == EditorInfo.IME_ACTION_SEARCH) { go.run(); return true; } return false; });
    }
}

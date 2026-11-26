package com.kkyelox.hava;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.util.List;

/** ⛺ Kamp günü bulucu: iller, ayarlar, sonuçlar. */
public class CampActivity extends Activity {
    LinearLayout root, cityList, searchRes, results;
    TextView msg;
    Context app;

    int dp(float v) { return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()); }

    TextView text(String s, float size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(0, dp(4), 0, dp(4));
        return t;
    }

    GradientDrawable box(int color, float r) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(r));
        return g;
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        app = getApplicationContext();
        getWindow().setStatusBarColor(Color.parseColor("#14532D"));
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(Color.parseColor("#EEF3F9"));
        sv.setFitsSystemWindows(true);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(30));
        sv.addView(root);
        setContentView(sv);

        root.addView(text("⛺ Kamp günü bulucu", 23, Color.parseColor("#14532D"), true));
        root.addView(text("Seçtiğin illerde önümüzdeki 16 günü 8 hava modeliyle tarar; art arda yağmursuz ve güneşli günler bulunca bildirim gönderir. 6 saatte bir kontrol eder.", 13.5f, Color.parseColor("#475569"), false));

        CheckBox on = new CheckBox(this);
        on.setText("Bildirim gönder");
        on.setTextSize(15);
        on.setChecked(Camp.enabled(app));
        on.setOnCheckedChangeListener((v, c) -> {
            Camp.setEnabled(app, c);
            if (c) askNotify();
            CampTicker.ensure(app);
        });
        root.addView(on);

        root.addView(text("Aranan aralık", 13, Color.parseColor("#64748B"), true));
        RadioGroup rg = new RadioGroup(this);
        rg.setOrientation(RadioGroup.HORIZONTAL);
        RadioButton r2 = new RadioButton(this), r3 = new RadioButton(this);
        r2.setText("En az 2 gün  ");
        r3.setText("En az 3 gün");
        r2.setId(View.generateViewId());
        r3.setId(View.generateViewId());
        rg.addView(r2);
        rg.addView(r3);
        (Camp.minDays(app) >= 3 ? r3 : r2).setChecked(true);
        rg.setOnCheckedChangeListener((g, id) -> { Camp.setMinDays(app, id == r3.getId() ? 3 : 2); refresh(); });
        root.addView(rg);

        root.addView(text("İller", 13, Color.parseColor("#64748B"), true));
        cityList = new LinearLayout(this);
        cityList.setOrientation(LinearLayout.VERTICAL);
        root.addView(cityList);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        EditText q = new EditText(this);
        q.setHint("İl / ilçe / kamp yeri ekle (ör. Bolu)");
        q.setInputType(InputType.TYPE_CLASS_TEXT);
        q.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        row.addView(q, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        Button sb = new Button(this);
        sb.setText("Ekle");
        sb.setAllCaps(false);
        row.addView(sb);
        root.addView(row);
        searchRes = new LinearLayout(this);
        searchRes.setOrientation(LinearLayout.VERTICAL);
        root.addView(searchRes);
        Runnable go = () -> search(q);
        sb.setOnClickListener(v -> go.run());
        q.setOnEditorActionListener((v, id, e) -> { if (id == EditorInfo.IME_ACTION_SEARCH) { go.run(); return true; } return false; });

        Button now = new Button(this);
        now.setText("🔄 Şimdi kontrol et");
        now.setAllCaps(false);
        now.setOnClickListener(v -> refresh());
        root.addView(now);

        msg = text("", 13, Color.parseColor("#B45309"), false);
        root.addView(msg);
        results = new LinearLayout(this);
        results.setOrientation(LinearLayout.VERTICAL);
        root.addView(results);

        root.addView(text("Puan: yağmursuzluk %45 (modellerin kaçı kuru diyor), güneş %25 (bulut), sıcaklık %15, rüzgâr %15. Yeşil gün = en az modellerin %75'i kuru, bulut ≤%60, hamle <50 km/sa. 10 günden uzak tarihler az modele dayanır; bildirim gelse de yaklaşınca tekrar bak — aralık bozulursa ayrıca haber verilir.",
                12, Color.parseColor("#64748B"), false));

        drawCities();
        if (Camp.enabled(app) && !Camp.cities(app).isEmpty()) askNotify();
        refresh();
    }

    void askNotify() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 51);
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] p, int[] r) {
        if (code == 51 && (r.length == 0 || r[0] != PackageManager.PERMISSION_GRANTED))
            msg.setText("Bildirim izni verilmedi — öneriler sadece bu ekranda görünecek. Ayarlar → Uygulamalar → Konsensüs Hava → Bildirimler'den açabilirsin.");
    }

    void drawCities() {
        cityList.removeAllViews();
        List<Camp.City> l = Camp.cities(app);
        if (l.isEmpty()) cityList.addView(text("Henüz il eklemedin. Aşağıdan ekle.", 14, Color.parseColor("#0F172A"), false));
        for (int i = 0; i < l.size(); i++) {
            final int idx = i;
            LinearLayout r = new LinearLayout(this);
            r.setGravity(Gravity.CENTER_VERTICAL);
            r.setPadding(dp(12), dp(4), dp(4), dp(4));
            r.setBackground(box(Color.WHITE, 12));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(6);
            TextView t = text("📍 " + l.get(i).name + (l.get(i).sub.isEmpty() ? "" : "  · " + l.get(i).sub), 15, Color.parseColor("#0F172A"), false);
            r.addView(t, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            Button x = new Button(this);
            x.setText("✕");
            x.setOnClickListener(v -> { Camp.remove(app, idx); drawCities(); refresh(); });
            r.addView(x, new LinearLayout.LayoutParams(dp(52), LinearLayout.LayoutParams.WRAP_CONTENT));
            cityList.addView(r, lp);
        }
    }

    void search(EditText q) {
        String t = q.getText().toString().trim();
        if (t.length() < 2) return;
        msg.setText("Aranıyor…");
        searchRes.removeAllViews();
        new Thread(() -> {
            try {
                JSONObject j = new JSONObject(Forecast.get("https://geocoding-api.open-meteo.com/v1/search?count=6&language=tr&format=json&name="
                        + URLEncoder.encode(t, "UTF-8")));
                JSONArray r = j.optJSONArray("results");
                runOnUiThread(() -> {
                    msg.setText(r == null || r.length() == 0 ? "Sonuç yok" : "");
                    if (r == null) return;
                    for (int i = 0; i < r.length(); i++) {
                        JSONObject x = r.optJSONObject(i);
                        final String name = x.optString("name");
                        final String sub = x.optString("admin1", "");
                        Button bt = new Button(this);
                        bt.setAllCaps(false);
                        bt.setText("+ " + name + " — " + sub + (x.has("country") ? ", " + x.optString("country") : ""));
                        final double la = x.optDouble("latitude"), lo = x.optDouble("longitude");
                        bt.setOnClickListener(v -> {
                            Camp.add(app, name, name.equals(sub) ? "" : sub, la, lo);
                            searchRes.removeAllViews();
                            q.setText("");
                            drawCities();
                            askNotify();
                            CampTicker.ensure(app);
                            refresh();
                        });
                        searchRes.addView(bt);
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> msg.setText("Arama başarısız: internet bağlantısını kontrol et."));
            }
        }).start();
    }

    void refresh() {
        if (Camp.cities(app).isEmpty()) { results.removeAllViews(); msg.setText(""); return; }
        msg.setText("16 günlük tahmin taranıyor…");
        new Thread(() -> {
            List<Camp.Result> rs = Camp.checkAll(app, false);
            runOnUiThread(() -> { msg.setText(""); draw(rs); });
        }).start();
    }

    void draw(List<Camp.Result> rs) {
        results.removeAllViews();
        for (Camp.Result r : rs) {
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(14), dp(10), dp(14), dp(12));
            card.setBackground(box(Color.WHITE, 16));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.topMargin = dp(10);
            card.addView(text("⛺ " + r.city.name, 17, Color.parseColor("#0F172A"), true));
            if (r.error != null) {
                card.addView(text("Veri alınamadı: " + r.error, 13, Color.parseColor("#B91C1C"), false));
            } else if (r.win != null) {
                LinearLayout w = new LinearLayout(this);
                w.setOrientation(LinearLayout.VERTICAL);
                w.setPadding(dp(12), dp(8), dp(12), dp(10));
                w.setBackground(box(Color.parseColor("#15803D"), 12));
                int lead = Camp.daysUntil(r.win.start);
                w.addView(text("✅ " + Camp.range(r.win) + " · " + r.win.len + " gün", 16, Color.WHITE, true));
                w.addView(text((lead == 1 ? "Yarın" : lead + " gün sonra") + " · puan " + r.win.score + "/100", 13, Color.parseColor("#DCFCE7"), false));
                w.addView(text(Camp.summary(r.win), 13, Color.WHITE, false));
                card.addView(w);
            } else {
                TextView t = text("❌ Önümüzdeki 16 günde art arda " + Camp.minDays(app) + " uygun gün görünmüyor.", 14, Color.parseColor("#9A3412"), false);
                card.addView(t);
            }
            // 16 günlük şerit
            HorizontalScrollView hs = new HorizontalScrollView(this);
            hs.setHorizontalScrollBarEnabled(false);
            LinearLayout strip = new LinearLayout(this);
            for (Camp.Day d : r.days) {
                boolean inWin = r.win != null && d.date.compareTo(r.win.start) >= 0 && d.date.compareTo(r.win.end) <= 0;
                LinearLayout c = new LinearLayout(this);
                c.setOrientation(LinearLayout.VERTICAL);
                c.setGravity(Gravity.CENTER_HORIZONTAL);
                c.setPadding(dp(4), dp(4), dp(4), dp(4));
                int bg = inWin ? Color.parseColor("#BBF7D0") : d.good ? Color.parseColor("#DCFCE7") : d.dry < 0.5 ? Color.parseColor("#DBEAFE") : Color.parseColor("#F1F5F9");
                c.setBackground(box(bg, 10));
                String ds = Camp.dayShort(d.date);
                String[] parts = ds.split(" ");
                TextView a = text(parts[2] + "\n" + parts[0], 11, Color.parseColor("#475569"), false);
                a.setGravity(Gravity.CENTER);
                c.addView(a);
                TextView ic = text(Camp.icon(d), 18, Color.BLACK, false);
                c.addView(ic);
                TextView sc = text(String.valueOf(d.score), 13, Color.parseColor(d.good ? "#15803D" : "#0F172A"), true);
                c.addView(sc);
                TextView tt = text(Double.isNaN(d.tmax) ? "" : Math.round(d.tmin) + "/" + Math.round(d.tmax) + "°", 10, Color.parseColor("#64748B"), false);
                c.addView(tt);
                LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(dp(50), LinearLayout.LayoutParams.WRAP_CONTENT);
                cl.rightMargin = dp(4);
                strip.addView(c, cl);
            }
            hs.addView(strip);
            LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            sl.topMargin = dp(8);
            card.addView(hs, sl);
            results.addView(card, lp);
        }
    }
}

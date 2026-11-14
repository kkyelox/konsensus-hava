package com.kkyelox.hava;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

/** Widget başına konum seçimi (eklerken ya da ⚙ ile). */
public class ConfigActivity extends Activity {
    int wid = AppWidgetManager.INVALID_APPWIDGET_ID;
    TextView current, msg;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_config);
        Bundle ex = getIntent().getExtras();
        if (ex != null) wid = ex.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, wid);
        // Geri tuşuyla çıkılsa bile widget eklensin (varsayılan konumla)
        setResult(RESULT_OK, new Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, wid));
        if (wid == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return; }
        current = findViewById(R.id.current);
        msg = findViewById(R.id.msg);
        final Context app = getApplicationContext();
        show();
        findViewById(R.id.useApp).setOnClickListener(v -> { Loc.setWidget(app, wid, "app", 0, 0, null); done(); });
        findViewById(R.id.auto).setOnClickListener(v -> {
            Loc.setWidget(app, wid, "auto", 0, 0, null);
            if (!Loc.hasPermission(this)) { Gps.ask(this); return; }
            msg.setText("Konum alınıyor…");
            Gps.freshAndSave(this, this::finish);
        });
        SearchUi.attach(this, (la, lo, name) -> { Loc.setWidget(app, wid, "fixed", la, lo, name); done(); });
        findViewById(R.id.appSettings).setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
    }

    void show() {
        Context app = getApplicationContext();
        String m = Loc.widgetMode(app, wid);
        Loc l = Loc.forWidget(app, wid, false);
        String what = "fixed".equals(m) ? "Sabit: " + (l == null ? "" : l.name)
                : "auto".equals(m) ? "📍 Bulunduğun yer" : "Uygulamadaki varsayılan";
        current.setText("Şu an: " + what + "\n" + Loc.describe(app));
    }

    void done() {
        msg.setText("Kaydedildi, güncelleniyor…");
        final Context app = getApplicationContext();
        new Thread(() -> { Updater.updateAll(app, false); runOnUiThread(this::finish); }).start();
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] p, int[] r) {
        if (code == Gps.REQ) {
            if (Loc.hasPermission(this)) { msg.setText("Konum alınıyor…"); Gps.freshAndSave(this, this::finish); }
            else msg.setText("Konum izni verilmedi — sabit bir yer seç.");
        }
    }
}

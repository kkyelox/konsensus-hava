package com.kkyelox.hava;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.TextView;

/** Varsayılan konum ve widget ayarları. */
public class SettingsActivity extends Activity {
    TextView current, msg;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_settings);
        current = findViewById(R.id.current);
        msg = findViewById(R.id.msg);
        final Context app = getApplicationContext();
        findViewById(R.id.auto).setOnClickListener(v -> {
            Loc.setGlobalAuto(app);
            if (!Loc.hasPermission(this)) { Gps.ask(this); return; }
            locateNow();
        });
        SearchUi.attach(this, (la, lo, name) -> {
            Loc.setGlobalFixed(app, la, lo, name);
            show();
            msg.setText("✓ " + name + " seçildi. Widget'lar güncelleniyor…");
            refreshAll(true);
        });
        findViewById(R.id.bg).setOnClickListener(v -> {
            if (!Loc.hasPermission(this)) { Gps.ask(this); return; }
            if (Build.VERSION.SDK_INT >= 29) requestPermissions(new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION}, 42);
            else msg.setText("Bu Android sürümünde ek izin gerekmiyor.");
        });
        findViewById(R.id.battery).setOnClickListener(v ->
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()))));
        findViewById(R.id.camp).setOnClickListener(v -> startActivity(new Intent(this, CampActivity.class)));
        findViewById(R.id.refresh).setOnClickListener(v -> { msg.setText("Yenileniyor…"); refreshAll(true); });
    }

    @Override
    protected void onResume() { super.onResume(); show(); }

    void show() {
        current.setText(Loc.describe(getApplicationContext()));
    }

    void locateNow() {
        msg.setText("Konum alınıyor…");
        Gps.freshAndSave(this, () -> { show(); msg.setText("✓ Konum güncellendi, widget'lar yenilendi."); });
    }

    void refreshAll(boolean force) {
        final Context app = getApplicationContext();
        new Thread(() -> {
            Updater.updateAll(app, force);
            final String err = Data.lastError;
            runOnUiThread(() -> { show(); msg.setText(err.isEmpty() ? "✓ Widget'lar güncellendi." : "⚠ " + err); });
        }).start();
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] p, int[] r) {
        boolean ok = r.length > 0 && r[0] == android.content.pm.PackageManager.PERMISSION_GRANTED;
        if (code == Gps.REQ) { if (Loc.hasPermission(this)) locateNow(); else msg.setText("Konum izni verilmedi — sabit bir yer seçebilirsin."); }
        if (code == 42) msg.setText(ok ? "✓ Konum arka planda da güncellenecek." : "Arka plan izni verilmedi; widget son bilinen konumu kullanır (uygulamayı açınca güncellenir).");
    }
}

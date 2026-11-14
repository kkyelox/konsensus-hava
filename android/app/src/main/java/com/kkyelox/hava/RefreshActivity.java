package com.kkyelox.hava;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.RemoteViews;

import java.util.ArrayList;
import java.util.List;

/**
 * ⟳ basılınca: görünmez kısa bir ekran açar (uygulama ön planda olduğu için veri tasarrufu engellemez),
 * ikon en az 2 tur döner, veri tazelenir, widget'lar yeniden çizilir.
 */
public class RefreshActivity extends Activity {
    static final int[] RF = {R.drawable.rf0, R.drawable.rf1, R.drawable.rf2, R.drawable.rf3,
            R.drawable.rf4, R.drawable.rf5, R.drawable.rf6, R.drawable.rf7};
    static final int MIN_FRAMES = 16;   // 2 tur
    static final long FRAME_MS = 70;
    static final int MAX_FRAMES = 300;  // ~21 sn üst sınır

    final Handler h = new Handler(Looper.getMainLooper());
    volatile boolean done = false;
    int frame = 0;
    final List<int[]> targets = new ArrayList<>(); // {widgetId, layoutId}

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        overridePendingTransition(0, 0);
        final Context app = getApplicationContext();
        AppWidgetManager m = AppWidgetManager.getInstance(app);
        for (Class<? extends WidgetBase> cls : Updater.ALL) {
            int layout;
            try { layout = cls.getDeclaredConstructor().newInstance().layout(); } catch (Exception e) { continue; }
            for (int id : m.getAppWidgetIds(new ComponentName(app, cls))) targets.add(new int[]{id, layout});
        }
        if (Loc.hasPermission(app) && Loc.usesDevice(app)) {
            Gps.freshAndSave(this, () -> done = true, true);   // konum + veri
        } else {
            new Thread(() -> {
                try { Updater.updateAll(app, true); } catch (Throwable ignored) { }
                done = true;
            }).start();
        }
        h.post(spin);
    }

    final Runnable spin = new Runnable() {
        @Override
        public void run() {
            AppWidgetManager m = AppWidgetManager.getInstance(getApplicationContext());
            boolean finish = (done && frame >= MIN_FRAMES && frame % 8 == 0) || frame >= MAX_FRAMES;
            if (!finish) {
                int res = RF[frame % 8];
                for (int[] t : targets) {
                    try {
                        RemoteViews v = new RemoteViews(getPackageName(), t[1]);
                        v.setImageViewResource(R.id.hRefresh, res);
                        m.partiallyUpdateAppWidget(t[0], v);
                    } catch (Exception ignored) { }
                }
                frame++;
                h.postDelayed(this, FRAME_MS);
                return;
            }
            // son çizim (ikon düz konumda)
            final Context app = getApplicationContext();
            new Thread(() -> {
                try { Updater.updateAll(app, false); } catch (Throwable ignored) { }
                Ticker.schedule(app);
                runOnUiThread(() -> { finish(); overridePendingTransition(0, 0); });
            }).start();
        }
    };
}

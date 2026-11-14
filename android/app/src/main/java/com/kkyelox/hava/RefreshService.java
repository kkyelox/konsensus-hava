package com.kkyelox.hava;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.widget.RemoteViews;

import java.util.ArrayList;
import java.util.List;

/**
 * ⟳ basılınca: ekran açmadan, birkaç saniyelik bir ön plan işi.
 * (Ön plan işi olduğu için veri tasarrufu interneti engellemez; telefonda gezinmeye devam edebilirsin.)
 * İkon en az 2 tur döner, veri tazelenir, widget'lar yeniden çizilir.
 */
public class RefreshService extends Service {
    static final int[] RF = {R.drawable.rf0, R.drawable.rf1, R.drawable.rf2, R.drawable.rf3,
            R.drawable.rf4, R.drawable.rf5, R.drawable.rf6, R.drawable.rf7};
    static final int MIN_FRAMES = 16;   // 2 tur
    static final long FRAME_MS = 70;
    static final int MAX_FRAMES = 300;  // ~21 sn üst sınır
    static final String CH = "refresh";

    final Handler h = new Handler(Looper.getMainLooper());
    volatile boolean done = false;
    boolean running = false;
    int frame = 0;
    final List<int[]> targets = new ArrayList<>();

    @Override
    public IBinder onBind(Intent i) { return null; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        goForeground();
        if (running) return START_NOT_STICKY;
        running = true;
        final Context app = getApplicationContext();
        AppWidgetManager m = AppWidgetManager.getInstance(app);
        for (Class<? extends WidgetBase> cls : Updater.ALL) {
            int layout;
            try { layout = cls.getDeclaredConstructor().newInstance().layout(); } catch (Exception e) { continue; }
            for (int id : m.getAppWidgetIds(new ComponentName(app, cls))) targets.add(new int[]{id, layout});
        }
        new Thread(() -> {
            try {
                if (Loc.usesDevice(app)) Loc.refreshDeviceNow(app);  // pasif: son bilinen konum, pil harcamaz
                Updater.updateAll(app, true);
            } catch (Throwable ignored) { }
            done = true;
        }).start();
        h.post(spin);
        return START_NOT_STICKY;
    }

    void goForeground() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm.getNotificationChannel(CH) == null) {
            NotificationChannel ch = new NotificationChannel(CH, "Yenileme", NotificationManager.IMPORTANCE_MIN);
            ch.setShowBadge(false);
            ch.setSound(null, null);
            nm.createNotificationChannel(ch);
        }
        Notification n = new Notification.Builder(this, CH)
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setContentTitle("Hava güncelleniyor…")
                .setOngoing(true)
                .build();
        if (Build.VERSION.SDK_INT >= 29) startForeground(77, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        else startForeground(77, n);
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
            final Context app = getApplicationContext();
            new Thread(() -> {
                try { Updater.updateAll(app, false); } catch (Throwable ignored) { }
                Ticker.schedule(app);
                h.post(() -> {
                    if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE); else stopForeground(true);
                    stopSelf();
                });
            }).start();
        }
    };
}

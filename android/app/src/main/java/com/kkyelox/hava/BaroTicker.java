package com.kkyelox.hava;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;

/** 30 dk'da bir sadece barometre okur (telefon uykudayken de, sistemin izin verdiği aralıklarla). */
public class BaroTicker extends BroadcastReceiver {
    static final long EVERY = 30 * 60_000L;

    public static void schedule(Context c) {
        try {
            if (!Baro.available(c)) return;
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            PendingIntent pi = PendingIntent.getBroadcast(c, 901, new Intent(c, BaroTicker.class),
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + EVERY, pi);
        } catch (Exception ignored) { }
    }

    @Override
    public void onReceive(Context c, Intent i) {
        final PendingResult pr = goAsync();
        final Context app = c.getApplicationContext();
        new Thread(() -> {
            try { Baro.record(app); } catch (Throwable ignored) { } finally {
                schedule(app);
                pr.finish();
            }
        }).start();
    }
}

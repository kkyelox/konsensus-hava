package com.kkyelox.hava;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;

/** 6 saatte bir kamp taraması (pil dostu: ilde 1 küçük istek). */
public class CampTicker extends BroadcastReceiver {
    static final long EVERY = 6 * 3600_000L;

    public static void schedule(Context c) { schedule(c, EVERY); }

    public static void schedule(Context c, long delay) {
        try {
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            PendingIntent pi = PendingIntent.getBroadcast(c, 902, new Intent(c, CampTicker.class),
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            if (!Camp.enabled(c) || Camp.cities(c).isEmpty()) { am.cancel(pi); return; }
            am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + delay, pi);
        } catch (Exception ignored) { }
    }

    /** Uygulama açılınca: son tarama 6 saatten eskiyse kısa süre sonra tara. */
    public static void ensure(Context c) {
        long last = c.getSharedPreferences("hava", Context.MODE_PRIVATE).getLong("camp_last", 0);
        schedule(c, System.currentTimeMillis() - last > EVERY ? 60_000L : EVERY);
    }

    @Override
    public void onReceive(Context c, Intent i) {
        final PendingResult pr = goAsync();
        final Context app = c.getApplicationContext();
        new Thread(() -> {
            try { Camp.checkAll(app, true); } catch (Throwable ignored) { } finally {
                schedule(app);
                pr.finish();
            }
        }).start();
    }
}

package com.kkyelox.hava;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;

/** 30 dakikada bir yenileme. Uyandırmayan alarm: telefon uyurken pil harcamaz, uyanınca vakti geldiyse yeniler. */
public class Ticker {
    static final long EVERY = 30 * 60_000L;

    static boolean anyWidget(Context c) {
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        for (Class<? extends WidgetBase> cls : Updater.ALL) {
            int[] ids = m.getAppWidgetIds(new ComponentName(c, cls));
            if (ids != null && ids.length > 0) return true;
        }
        return false;
    }

    public static void schedule(Context c) {
        try {
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            PendingIntent pi = PendingIntent.getBroadcast(c, 900, new Intent(c, TickReceiver.class),
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            if (!anyWidget(c)) { am.cancel(pi); return; }
            am.setWindow(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + EVERY, 2 * 60_000L, pi);
        } catch (Exception ignored) { }
    }
}

package com.kkyelox.hava;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.widget.RemoteViews;

import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class RainWidget extends AppWidgetProvider {
    public static final String ACTION_REFRESH = "com.kkyelox.hava.REFRESH";
    static final String SITE = "https://kkyelox.github.io/konsensus-hava/";
    static final String[] DN = {"Paz", "Pzt", "Sal", "Çar", "Per", "Cum", "Cmt"};

    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        final PendingResult pr = goAsync();
        new Thread(() -> {
            try { update(c); } finally { pr.finish(); }
        }).start();
    }

    @Override
    public void onReceive(Context c, Intent i) {
        super.onReceive(c, i);
        if (ACTION_REFRESH.equals(i.getAction())) {
            final PendingResult pr = goAsync();
            showStatus(c, "Yenileniyor…");
            new Thread(() -> {
                try { update(c); } finally { pr.finish(); }
            }).start();
        }
    }

    /** Uygulamadan da çağrılır (arka plan iş parçacığında). */
    public static void update(Context c) {
        SharedPreferences sp = c.getSharedPreferences("hava", Context.MODE_PRIVATE);
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget);
        bindClicks(c, v, sp);
        if (!sp.contains("lat")) {
            v.setTextViewText(R.id.city, "Konum seçmek için ⚙");
            v.setTextViewText(R.id.status, "");
            push(c, v);
            return;
        }
        double lat = Double.longBitsToDouble(sp.getLong("lat", 0));
        double lon = Double.longBitsToDouble(sp.getLong("lon", 0));
        try {
            render(c, Forecast.fetch(lat, lon));
        } catch (Throwable e) {
            String m = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            sp.edit().putString("lastError", m).apply();
            v.setTextViewText(R.id.city, sp.getString("name", "Konum"));
            v.setTextViewText(R.id.status, "Hata: " + m);
            push(c, v);
        }
    }

    /** Hesaplanmış sonucu widget'a yazar. */
    public static void render(Context c, Forecast.Result r) {
        SharedPreferences sp = c.getSharedPreferences("hava", Context.MODE_PRIVATE);
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget);
        bindClicks(c, v, sp);
        v.setTextViewText(R.id.city, sp.getString("name", "Konum"));
        {
            v.setTextViewText(R.id.icon, Forecast.icon(r.code, r.isDay));
            v.setTextViewText(R.id.temp, Double.isNaN(r.temp) ? "--°" : Math.round(r.temp) + "°");
            int[][] ids = {
                    {R.id.d0, R.id.d0n, R.id.d0p, R.id.d0v, R.id.d0t},
                    {R.id.d1, R.id.d1n, R.id.d1p, R.id.d1v, R.id.d1t},
                    {R.id.d2, R.id.d2n, R.id.d2p, R.id.d2v, R.id.d2t}};
            int[] bgs = {R.drawable.rv0, R.drawable.rv1, R.drawable.rv2, R.drawable.rv3, R.drawable.rv4, R.drawable.rv5};
            for (int d = 0; d < 3; d++) {
                Forecast.Day day = r.days[d];
                int lv = Forecast.level(day.pop);
                v.setInt(ids[d][0], "setBackgroundResource", bgs[lv]);
                v.setTextViewText(ids[d][1], dayName(day.date, d));
                v.setTextViewText(ids[d][2], day.pop < 0 ? "--" : "%" + Math.round(day.pop * 100));
                v.setTextViewText(ids[d][3], Forecast.label(lv));
                String t;
                if (!day.window.isEmpty()) t = "🕐 " + day.window;
                else if (day.mm >= 0.1) t = String.format(Locale.US, "%.1f mm", day.mm);
                else t = day.total > 0 ? day.yes + "/" + day.total + " model" : "";
                v.setTextViewText(ids[d][4], t);
            }
            String hm = new SimpleDateFormat("HH:mm", new Locale("tr")).format(new Date());
            v.setTextViewText(R.id.status, r.errors.length() > 0 ? "Kısmi veri " + hm + " · " + r.errors : "Güncellendi " + hm + " · 9 model");
            sp.edit().putLong("last", System.currentTimeMillis()).remove("lastError").apply();
        }
        push(c, v);
    }

    static String dayName(String date, int d) {
        if (d == 0) return "Bugün";
        if (d == 1) return "Yarın";
        try {
            Date dt = new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(date);
            Calendar cal = Calendar.getInstance();
            cal.setTime(dt);
            return DN[cal.get(Calendar.DAY_OF_WEEK) - 1];
        } catch (Exception e) {
            return "+2 gün";
        }
    }

    static void bindClicks(Context c, RemoteViews v, SharedPreferences sp) {
        int fl = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        // ⟳ yenile
        Intent r = new Intent(c, RainWidget.class).setAction(ACTION_REFRESH);
        v.setOnClickPendingIntent(R.id.refresh, PendingIntent.getBroadcast(c, 1, r, fl));
        // ⚙ ayarlar
        Intent s = new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        v.setOnClickPendingIntent(R.id.settings, PendingIntent.getActivity(c, 2, s, fl));
        // gövde: siteyi Yağmur sekmesinde aç
        String url = SITE;
        if (sp.contains("lat")) {
            try {
                url = SITE + String.format(Locale.US, "?lat=%.4f&lon=%.4f&name=",
                        Double.longBitsToDouble(sp.getLong("lat", 0)), Double.longBitsToDouble(sp.getLong("lon", 0)))
                        + URLEncoder.encode(sp.getString("name", ""), "UTF-8") + "#yagmur";
            } catch (Exception ignored) { }
        } else {
            v.setOnClickPendingIntent(R.id.root, PendingIntent.getActivity(c, 3, s, fl));
            return;
        }
        Intent o = new Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        v.setOnClickPendingIntent(R.id.root, PendingIntent.getActivity(c, 3, o, fl));
    }

    static void showStatus(Context c, String text) {
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget);
        v.setTextViewText(R.id.status, text);
        AppWidgetManager.getInstance(c).partiallyUpdateAppWidget(ids(c), v);
    }

    static int[] ids(Context c) {
        return AppWidgetManager.getInstance(c).getAppWidgetIds(new ComponentName(c, RainWidget.class));
    }

    static void push(Context c, RemoteViews v) {
        AppWidgetManager.getInstance(c).updateAppWidget(new ComponentName(c, RainWidget.class), v);
    }
}

package com.kkyelox.hava;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.widget.RemoteViews;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public abstract class WidgetBase extends AppWidgetProvider {
    public static final String ACTION_REFRESH = "com.kkyelox.hava.REFRESH";
    static final String[] DN = {"Paz", "Pzt", "Sal", "Çar", "Per", "Cum", "Cmt"};

    abstract int layout();
    abstract String tab();
    abstract void fill(Context c, RemoteViews v, Forecast.Result r, Loc loc, int wDp, int hDp);
    int defW() { return 250; }
    int defH() { return 110; }

    // ---- yaşam döngüsü ----
    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        final PendingResult pr = goAsync();
        final Class<? extends WidgetBase> cls = getClass();
        new Thread(() -> { try { Updater.update(c, cls, ids, false, null, true); } finally { pr.finish(); } }).start();
        Ticker.schedule(c);
    }

    @Override
    public void onEnabled(Context c) { Ticker.schedule(c); }

    @Override
    public void onAppWidgetOptionsChanged(Context c, AppWidgetManager m, int id, Bundle o) {
        final PendingResult pr = goAsync();
        final Class<? extends WidgetBase> cls = getClass();
        new Thread(() -> { try { Updater.update(c, cls, new int[]{id}, false, null, false); } finally { pr.finish(); } }).start();
    }

    @Override
    public void onDeleted(Context c, int[] ids) {
        for (int id : ids) Loc.removeWidget(c, id);
    }

    @Override
    public void onReceive(Context c, Intent i) {
        super.onReceive(c, i);
        if (ACTION_REFRESH.equals(i.getAction())) {
            final PendingResult pr = goAsync();
            new Thread(() -> { try { Updater.updateAll(c, true); } finally { pr.finish(); } }).start();
        }
    }

    // ---- tıklamalar ----
    void bindClicks(Context c, RemoteViews v, int id, Loc loc) {
        int fl = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        Intent open = new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra("tab", tab()).putExtra("wid", id);
        if (loc != null) open.putExtra("lat", loc.lat).putExtra("lon", loc.lon).putExtra("name", loc.name);
        v.setOnClickPendingIntent(R.id.root, PendingIntent.getActivity(c, id * 10 + 1, open, fl));
        Intent cfg = new Intent(c, ConfigActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id);
        v.setOnClickPendingIntent(R.id.hSettings, PendingIntent.getActivity(c, id * 10 + 2, cfg, fl));
        Intent rf = new Intent(c, RefreshActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        v.setOnClickPendingIntent(R.id.hRefresh, PendingIntent.getActivity(c, id * 10 + 3, rf, fl));
        v.setImageViewResource(R.id.hRefresh, R.drawable.rf0);
    }

    /** Veri yokken gösterilecek mesaj (tüm düzenlerde ortak id'ler). */
    void message(Context c, RemoteViews v, String title, String text) {
        v.setTextViewText(R.id.hCity, title);
        v.setTextViewText(R.id.hStatus, text);
        v.setTextViewText(R.id.nCity, title);
        v.setTextViewText(R.id.nStatus, text);
        v.setTextViewText(R.id.rCity, title);
        v.setTextViewText(R.id.rStatus, text);
    }

    // ---- ortak parçalar ----
    static String hm(long t) { return new SimpleDateFormat("HH:mm", Locale.US).format(new Date(t)); }

    static String status(Forecast.Result r) {
        return (r.stale ? "Eski veri · " : "Güncellendi ") + hm(r.fetched);
    }

    static String cityLabel(Loc loc) { return (loc.auto ? "📍 " : "") + loc.name; }

    static String fmt(double x) { return Double.isNaN(x) ? "--" : String.valueOf(Math.round(x)); }

    static String pct(double p) { return p < 0 ? "--" : "%" + Math.round(p * 100); }

    static String dayName(String date, int d) {
        if (d == 0) return "Bugün";
        if (d == 1) return "Yarın";
        try {
            Calendar cal = Calendar.getInstance();
            cal.setTime(new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(date));
            return DN[cal.get(Calendar.DAY_OF_WEEK) - 1];
        } catch (Exception e) { return "--"; }
    }

    static void size(RemoteViews v, int id, float sp) { v.setTextViewTextSize(id, TypedValue.COMPLEX_UNIT_SP, sp); }

    static void show(RemoteViews v, int id, boolean on) { v.setViewVisibility(id, on ? View.VISIBLE : View.GONE); }

    void header(RemoteViews v, Forecast.Result r, Loc loc, boolean showStatus) {
        v.setTextViewText(R.id.hIcon, Forecast.icon(r.code, r.isDay));
        v.setTextViewText(R.id.hTemp, fmt(r.temp) + "°");
        v.setTextViewText(R.id.hCity, cityLabel(loc));
        v.setTextViewText(R.id.hStatus, status(r));
        show(v, R.id.hStatus, showStatus);
    }

    static final int[] HS = {R.id.hs0, R.id.hs1, R.id.hs2, R.id.hs3, R.id.hs4, R.id.hs5, R.id.hs6, R.id.hs7};
    static final int[] HT = {R.id.ht0, R.id.ht1, R.id.ht2, R.id.ht3, R.id.ht4, R.id.ht5, R.id.ht6, R.id.ht7};
    static final int[] HI = {R.id.hi0, R.id.hi1, R.id.hi2, R.id.hi3, R.id.hi4, R.id.hi5, R.id.hi6, R.id.hi7};
    static final int[] HV = {R.id.hv0, R.id.hv1, R.id.hv2, R.id.hv3, R.id.hv4, R.id.hv5, R.id.hv6, R.id.hv7};
    static final int[] HP = {R.id.hp0, R.id.hp1, R.id.hp2, R.id.hp3, R.id.hp4, R.id.hp5, R.id.hp6, R.id.hp7};

    /** n saat; step: kaç saatte bir. */
    void hourly(RemoteViews v, Forecast.Result r, int n, int step, int max) {
        for (int k = 0; k < max; k++) {
            int idx = k * step;
            boolean on = k < n && idx < r.hours.size();
            show(v, HS[k], on);
            if (!on) continue;
            Forecast.Hour h = r.hours.get(idx);
            v.setTextViewText(HT[k], k == 0 ? "Şimdi" : h.time.substring(11, 13));
            v.setTextViewText(HI[k], Forecast.icon(h.code, h.day));
            v.setTextViewText(HV[k], fmt(h.temp) + "°");
            v.setTextViewText(HP[k], h.pop >= 0.2 ? pct(h.pop) : "");
        }
    }

    static final int[] DR = {R.id.dr0, R.id.dr1, R.id.dr2, R.id.dr3, R.id.dr4, R.id.dr5, R.id.dr6};
    static final int[] DNs = {R.id.dn0, R.id.dn1, R.id.dn2, R.id.dn3, R.id.dn4, R.id.dn5, R.id.dn6};
    static final int[] DI = {R.id.di0, R.id.di1, R.id.di2, R.id.di3, R.id.di4, R.id.di5, R.id.di6};
    static final int[] DP = {R.id.dp0, R.id.dp1, R.id.dp2, R.id.dp3, R.id.dp4, R.id.dp5, R.id.dp6};
    static final int[] DMN = {R.id.dmn0, R.id.dmn1, R.id.dmn2, R.id.dmn3, R.id.dmn4, R.id.dmn5, R.id.dmn6};
    static final int[] DMX = {R.id.dmx0, R.id.dmx1, R.id.dmx2, R.id.dmx3, R.id.dmx4, R.id.dmx5, R.id.dmx6};

    void daily(RemoteViews v, Forecast.Result r, int rows, int max) {
        for (int k = 0; k < max; k++) {
            boolean on = k < rows && k < r.days.length;
            show(v, DR[k], on);
            if (!on) continue;
            Forecast.Day d = r.days[k];
            v.setTextViewText(DNs[k], dayName(d.date, k));
            v.setTextViewText(DI[k], Forecast.icon(d.code, true));
            v.setTextViewText(DP[k], d.pop >= 0.2 ? "💧" + pct(d.pop) : "");
            v.setTextViewText(DMN[k], fmt(d.tmin) + "°");
            v.setTextViewText(DMX[k], fmt(d.tmax) + "°");
        }
    }

    static final int[] RB = {R.drawable.rb0, R.drawable.rb1, R.drawable.rb2, R.drawable.rb3, R.drawable.rb4, R.drawable.rb5};
    static final int[] RV = {R.drawable.rv0, R.drawable.rv1, R.drawable.rv2, R.drawable.rv3, R.drawable.rv4, R.drawable.rv5};
}

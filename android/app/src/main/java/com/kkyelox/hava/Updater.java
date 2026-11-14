package com.kkyelox.hava;

import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.os.Bundle;
import android.widget.RemoteViews;

import java.util.HashMap;
import java.util.Map;

public class Updater {
    @SuppressWarnings("unchecked")
    static final Class<? extends WidgetBase>[] ALL = new Class[]{RainWidget.class, NowWidget.class, HourlyWidget.class,
            DailyWidget.class, RainTodayWidget.class, DetailsWidget.class, BigWidget.class};

    public static synchronized void updateAll(Context c, boolean force) {
        Map<String, Forecast.Result> memo = new HashMap<>();
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        for (Class<? extends WidgetBase> cls : ALL) {
            int[] ids = m.getAppWidgetIds(new ComponentName(c, cls));
            if (ids != null && ids.length > 0) update(c, cls, ids, force, memo, true);
        }
    }

    public static synchronized void update(Context c, Class<? extends WidgetBase> cls, int[] ids, boolean force,
                                           Map<String, Forecast.Result> memo, boolean network) {
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        WidgetBase w;
        try { w = cls.getDeclaredConstructor().newInstance(); } catch (Exception e) { return; }
        if (memo == null) memo = new HashMap<>();
        for (int id : ids) {
            RemoteViews v = new RemoteViews(c.getPackageName(), w.layout());
            try {
                Loc loc = Loc.forWidget(c, id, network);
                w.bindClicks(c, v, id, loc);
                Bundle o = m.getAppWidgetOptions(id);
                int wd = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0);
                int ht = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0);
                if (wd <= 0) wd = w.defW();
                if (ht <= 0) ht = w.defH();
                if (loc == null) {
                    w.message(c, v, "Konum yok", "Konum izni ver ya da yer seç — dokun");
                } else {
                    Forecast.Result r = memo.get(loc.key());
                    if (r == null) {
                        r = network ? Data.get(c, loc, force) : Data.cachedOnly(c, loc);
                        if (r == null && !network) r = Data.get(c, loc, false);
                        if (r != null) memo.put(loc.key(), r);
                    }
                    if (r == null) w.message(c, v, loc.name, "Hata: " + Data.lastError);
                    else w.fill(c, v, r, loc, wd, ht);
                }
            } catch (Throwable e) {
                w.message(c, v, "Konsensüs Hava", "Hata: " + e.getClass().getSimpleName() + " " + e.getMessage());
            }
            m.updateAppWidget(id, v);
        }
    }
}

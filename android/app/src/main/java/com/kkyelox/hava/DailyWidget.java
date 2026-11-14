package com.kkyelox.hava;

import android.content.Context;
import android.widget.RemoteViews;

/** 7 günlük — uzadıkça daha çok gün. */
public class DailyWidget extends WidgetBase {
    int layout() { return R.layout.w_daily; }
    String tab() { return "gunluk"; }
    int defH() { return 180; }

    void fill(Context c, RemoteViews v, Forecast.Result r, Loc loc, int w, int h) {
        header(v, r, loc, h >= 200);
        int rows = Math.max(2, Math.min(7, (h - 50) / 26));
        daily(v, r, rows, 7);
    }
}

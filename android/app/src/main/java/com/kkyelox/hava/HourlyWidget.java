package com.kkyelox.hava;

import android.content.Context;
import android.widget.RemoteViews;

/** Saatlik tahmin — genişledikçe daha çok saat. */
public class HourlyWidget extends WidgetBase {
    int layout() { return R.layout.w_hourly; }
    String tab() { return "saatlik"; }

    void fill(Context c, RemoteViews v, Forecast.Result r, Loc loc, int w, int h) {
        header(v, r, loc, h >= 115);
        show(v, R.id.hdr, h >= 85);
        int n = Math.max(3, Math.min(8, w / 48));
        int step = h >= 170 ? 3 : 1; // çok yüksekse 3 saatlik adım → daha uzun ufuk
        hourly(v, r, n, step, 8);
    }
}

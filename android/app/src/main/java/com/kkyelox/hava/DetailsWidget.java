package com.kkyelox.hava;

import android.content.Context;
import android.widget.RemoteViews;

/** Ayrıntılar: hissedilen, nem, rüzgâr, hamle, UV, gün doğumu/batımı. */
public class DetailsWidget extends WidgetBase {
    int layout() { return R.layout.w_details; }
    String tab() { return "ozet"; }

    void fill(Context c, RemoteViews v, Forecast.Result r, Loc loc, int w, int h) {
        header(v, r, loc, h >= 140);
        Forecast.Day d = r.days[0];
        String[][] cells = {
                {"🌡️ Hissedilen", fmt(r.feels) + "°"},
                {"💧 Nem", Double.isNaN(r.rh) ? "--" : "%" + fmt(r.rh)},
                {"🌬️ Rüzgâr", fmt(r.wind) + " " + Forecast.dirTxt(r.dir)},
                {"💨 Hamle", fmt(r.gust) + " km/sa"},
                {"☀️ UV", Double.isNaN(d.uv) ? "--" : String.format(java.util.Locale.US, "%.1f", d.uv)},
                {"🌅 Gün", d.sunrise.isEmpty() ? "--" : d.sunrise + "–" + d.sunset}};
        int[] K = {R.id.k0, R.id.k1, R.id.k2, R.id.k3, R.id.k4, R.id.k5};
        int[] V = {R.id.v0, R.id.v1, R.id.v2, R.id.v3, R.id.v4, R.id.v5};
        for (int i = 0; i < 6; i++) { v.setTextViewText(K[i], cells[i][0]); v.setTextViewText(V[i], cells[i][1]); }
        show(v, R.id.drow1, h >= 120);
    }
}

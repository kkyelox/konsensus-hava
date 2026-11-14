package com.kkyelox.hava;

import android.content.Context;
import android.widget.RemoteViews;

import java.util.Locale;

/** Yağmur — 3 gün (ilk widget; sınıf adı korunuyor). */
public class RainWidget extends WidgetBase {
    int layout() { return R.layout.w_rain3; }
    String tab() { return "yagmur"; }

    void fill(Context c, RemoteViews v, Forecast.Result r, Loc loc, int w, int h) {
        header(v, r, loc, h >= 105);
        int[][] ids = {{R.id.d0, R.id.d0n, R.id.d0p, R.id.d0v, R.id.d0t},
                {R.id.d1, R.id.d1n, R.id.d1p, R.id.d1v, R.id.d1t},
                {R.id.d2, R.id.d2n, R.id.d2p, R.id.d2v, R.id.d2t}};
        float big = h < 100 ? 16 : (h < 140 ? 22 : 30);
        for (int d = 0; d < 3; d++) {
            Forecast.Day day = r.days[d];
            int lv = Forecast.level(day.pop);
            v.setInt(ids[d][0], "setBackgroundResource", RV[lv]);
            v.setTextViewText(ids[d][1], dayName(day.date, d));
            v.setTextViewText(ids[d][2], pct(day.pop));
            size(v, ids[d][2], big);
            v.setTextViewText(ids[d][3], Forecast.label(lv));
            String t;
            if (!day.window.isEmpty()) t = "🕐 " + day.window;
            else if (day.mm >= 0.1) t = String.format(Locale.US, "%.1f mm", day.mm);
            else t = day.total > 0 ? day.yes + "/" + day.total + " model" : "";
            v.setTextViewText(ids[d][4], t);
            show(v, ids[d][4], h >= 95);
        }
    }
}

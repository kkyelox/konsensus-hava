package com.kkyelox.hava;

import android.content.Context;
import android.widget.RemoteViews;

/** Büyük panel: anlık + saatlik + günlük. */
public class BigWidget extends WidgetBase {
    int layout() { return R.layout.w_big; }
    String tab() { return "ozet"; }
    int defH() { return 250; }

    void fill(Context c, RemoteViews v, Forecast.Result r, Loc loc, int w, int h) {
        header(v, r, loc, true);
        Forecast.Day d = r.days[0];
        v.setTextViewText(R.id.bDesc, Forecast.desc(r.code) + " · ↑" + fmt(d.tmax) + "° ↓" + fmt(d.tmin) + "° · ☔ bugün " + pct(d.pop)
                + (d.window.isEmpty() ? "" : " (" + d.window + ")"));
        hourly(v, r, Math.max(4, Math.min(6, w / 52)), 1, 6);
        int rows = Math.max(0, Math.min(5, (h - 150) / 26));
        show(v, R.id.secD, rows > 0);
        show(v, R.id.secH, h >= 130);
        daily(v, r, rows, 5);
    }
}

package com.kkyelox.hava;

import android.content.Context;
import android.widget.RemoteViews;

/** Anlık hava. */
public class NowWidget extends WidgetBase {
    int layout() { return R.layout.w_now; }
    String tab() { return "ozet"; }
    int defW() { return 110; }

    void fill(Context c, RemoteViews v, Forecast.Result r, Loc loc, int w, int h) {
        v.setTextViewText(R.id.nCity, cityLabel(loc));
        v.setTextViewText(R.id.nIcon, Forecast.icon(r.code, r.isDay));
        v.setTextViewText(R.id.nTemp, fmt(r.temp) + "°");
        int s = Math.min(w, h);
        size(v, R.id.nTemp, s < 90 ? 28 : (s < 140 ? 40 : 56));
        size(v, R.id.nIcon, s < 90 ? 22 : (s < 140 ? 32 : 44));
        v.setTextViewText(R.id.nDesc, Forecast.desc(r.code) + (Double.isNaN(r.feels) ? "" : " · hissedilen " + fmt(r.feels) + "°"));
        Forecast.Day d = r.days[0];
        v.setTextViewText(R.id.nExtra, "↑" + fmt(d.tmax) + "° ↓" + fmt(d.tmin) + "° · ☔ " + pct(d.pop));
        v.setTextViewText(R.id.nStatus, status(r));
        show(v, R.id.nDesc, h >= 100);
        show(v, R.id.nExtra, h >= 80);
        show(v, R.id.nStatus, h >= 130);
    }
}

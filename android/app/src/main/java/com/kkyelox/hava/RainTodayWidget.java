package com.kkyelox.hava;

import android.content.Context;
import android.widget.RemoteViews;

/** Bugün yağmur — tek büyük yüzde + saat. */
public class RainTodayWidget extends WidgetBase {
    int layout() { return R.layout.w_raintoday; }
    String tab() { return "yagmur"; }
    int defW() { return 110; }

    void fill(Context c, RemoteViews v, Forecast.Result r, Loc loc, int w, int h) {
        Forecast.Day d = r.days[0];
        int lv = Forecast.level(d.pop);
        v.setInt(R.id.root, "setBackgroundResource", RB[lv]);
        v.setTextViewText(R.id.rCity, cityLabel(loc));
        v.setTextViewText(R.id.rPct, pct(d.pop));
        int s = Math.min(w, h);
        size(v, R.id.rPct, s < 90 ? 28 : (s < 140 ? 40 : 56));
        v.setTextViewText(R.id.rVerdict, Forecast.label(lv));
        v.setTextViewText(R.id.rWhen, d.window.isEmpty() ? (lv >= 3 ? "saat belirsiz" : "yağışlı saat yok") : "🕐 " + d.window);
        // önümüzdeki 3 saat
        String next = "";
        int wetAt = -1;
        for (int k = 0; k < Math.min(3, r.hours.size()); k++) if (r.hours.get(k).pop >= 0.5) { wetAt = k; break; }
        if (wetAt == 0) next = "Şu an yağış var/başlıyor";
        else if (wetAt > 0) next = r.hours.get(wetAt).time.substring(11, 16) + "’da başlayabilir";
        else if (!r.hours.isEmpty()) next = "Önümüzdeki 3 saat kuru";
        v.setTextViewText(R.id.rNext, next);
        v.setTextViewText(R.id.rStatus, status(r));
        show(v, R.id.rTitle, h >= 120);
        show(v, R.id.rWhen, h >= 100);
        show(v, R.id.rNext, h >= 140);
        show(v, R.id.rStatus, h >= 170);
    }
}

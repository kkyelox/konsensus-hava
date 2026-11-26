package com.kkyelox.hava;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Kamp için ideal gün bulucu: seçilen illerde 16 günlük çok modelli tahmini tarar, 2–3 günlük kuru+güneşli aralık arar. */
public class Camp {
    static final String[] MODELS = {"ecmwf_ifs025", "ecmwf_aifs025_single", "gfs_seamless", "icon_seamless",
            "gem_seamless", "jma_seamless", "meteofrance_seamless", "ukmo_seamless"};
    static final String CH = "camp";
    static final String[] DN = {"Paz", "Pzt", "Sal", "Çar", "Per", "Cum", "Cmt"};
    static final String[] MN = {"Oca", "Şub", "Mar", "Nis", "May", "Haz", "Tem", "Ağu", "Eyl", "Eki", "Kas", "Ara"};

    public static class City { public String name, sub; public double lat, lon; }

    public static class Day {
        public String date;
        public int n, score;
        public double dry = Double.NaN, cloud = Double.NaN, sunH = Double.NaN, tmax = Double.NaN, tmin = Double.NaN, gust = Double.NaN, mm = Double.NaN;
        public boolean good;
    }

    public static class Window {
        public String start, end;
        public int len, score, minModels;
        public double dry, cloud, sunH, tmin, tmax, gust;
    }

    public static class Result { public City city; public List<Day> days = new ArrayList<>(); public Window win; public String error; }

    // ---------- iller ----------
    static SharedPreferences sp(Context c) { return c.getSharedPreferences("hava", Context.MODE_PRIVATE); }

    public static List<City> cities(Context c) {
        List<City> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(sp(c).getString("camp_cities", "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                City ci = new City();
                ci.name = o.optString("name");
                ci.sub = o.optString("sub");
                ci.lat = o.optDouble("lat");
                ci.lon = o.optDouble("lon");
                out.add(ci);
            }
        } catch (Exception ignored) { }
        return out;
    }

    static void save(Context c, List<City> l) {
        JSONArray a = new JSONArray();
        try {
            for (City ci : l) {
                JSONObject o = new JSONObject();
                o.put("name", ci.name); o.put("sub", ci.sub); o.put("lat", ci.lat); o.put("lon", ci.lon);
                a.put(o);
            }
        } catch (Exception ignored) { }
        sp(c).edit().putString("camp_cities", a.toString()).apply();
    }

    public static void add(Context c, String name, String sub, double lat, double lon) {
        List<City> l = cities(c);
        for (City ci : l) if (Math.abs(ci.lat - lat) < 0.01 && Math.abs(ci.lon - lon) < 0.01) return;
        City ci = new City();
        ci.name = name; ci.sub = sub; ci.lat = lat; ci.lon = lon;
        l.add(ci);
        save(c, l);
    }

    public static void remove(Context c, int idx) {
        List<City> l = cities(c);
        if (idx >= 0 && idx < l.size()) {
            sp(c).edit().remove("camp_n_" + key(l.get(idx))).apply();
            l.remove(idx);
            save(c, l);
        }
    }

    public static boolean enabled(Context c) { return sp(c).getBoolean("camp_on", true); }
    public static void setEnabled(Context c, boolean on) { sp(c).edit().putBoolean("camp_on", on).apply(); }
    public static int minDays(Context c) { return sp(c).getInt("camp_min", 2); }
    public static void setMinDays(Context c, int d) { sp(c).edit().putInt("camp_min", d).apply(); }

    static String key(City ci) { return String.format(Locale.US, "%.2f_%.2f", ci.lat, ci.lon); }

    // ---------- analiz ----------
    static double clamp(double x, double a, double b) { return Math.max(a, Math.min(b, x)); }

    public static Result check(Context c, City ci) {
        Result r = new Result();
        r.city = ci;
        try {
            StringBuilder ms = new StringBuilder();
            for (String m : MODELS) { if (ms.length() > 0) ms.append(','); ms.append(m); }
            String url = String.format(Locale.US, "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f&timezone=auto&forecast_days=16", ci.lat, ci.lon)
                    + "&daily=temperature_2m_max,temperature_2m_min,precipitation_sum,cloud_cover_mean,wind_gusts_10m_max,sunshine_duration&models=" + ms;
            JSONObject d = new JSONObject(Forecast.get(url)).getJSONObject("daily");
            JSONArray t = d.getJSONArray("time");
            for (int i = 0; i < t.length(); i++) {
                Day day = new Day();
                day.date = t.getString(i);
                int dryN = 0;
                double mm = 0, cl = 0, sun = 0, tx = 0, tn = 0, gu = 0;
                int nc = 0, ns = 0, nx = 0, nn = 0, ng = 0;
                for (String m : MODELS) {
                    double p = Forecast.val(d.optJSONArray("precipitation_sum_" + m), i);
                    if (Double.isNaN(p)) continue;
                    day.n++;
                    mm += p;
                    if (p < 0.5) dryN++;
                    double v;
                    v = Forecast.val(d.optJSONArray("cloud_cover_mean_" + m), i); if (!Double.isNaN(v)) { cl += v; nc++; }
                    v = Forecast.val(d.optJSONArray("sunshine_duration_" + m), i); if (!Double.isNaN(v)) { sun += v / 3600.0; ns++; }
                    v = Forecast.val(d.optJSONArray("temperature_2m_max_" + m), i); if (!Double.isNaN(v)) { tx += v; nx++; }
                    v = Forecast.val(d.optJSONArray("temperature_2m_min_" + m), i); if (!Double.isNaN(v)) { tn += v; nn++; }
                    v = Forecast.val(d.optJSONArray("wind_gusts_10m_max_" + m), i); if (!Double.isNaN(v)) { gu += v; ng++; }
                }
                if (day.n == 0) break;
                day.dry = (double) dryN / day.n;
                day.mm = mm / day.n;
                day.cloud = nc > 0 ? cl / nc : 50;
                day.sunH = ns > 0 ? sun / ns : Double.NaN;
                day.tmax = nx > 0 ? tx / nx : Double.NaN;
                day.tmin = nn > 0 ? tn / nn : Double.NaN;
                day.gust = ng > 0 ? gu / ng : 20;
                // puan: kuru %45, güneş %25, sıcaklık %15, rüzgâr %15
                double sunS = clamp((75 - day.cloud) / 55.0, 0, 1);
                double tempS = 0.6;
                if (!Double.isNaN(day.tmax)) {
                    tempS = clamp((day.tmax - 4) / 12.0, 0, 1);
                    if (day.tmax > 32) tempS *= clamp((38 - day.tmax) / 6.0, 0, 1);
                }
                if (!Double.isNaN(day.tmin) && day.tmin < 2) tempS *= clamp(1 + (day.tmin - 2) / 10.0, 0.2, 1);
                double windS = clamp((55 - day.gust) / 30.0, 0, 1);
                day.score = (int) Math.round(100 * (0.45 * day.dry + 0.25 * sunS + 0.15 * tempS + 0.15 * windS));
                day.good = day.dry >= 0.75 && day.cloud <= 60 && day.gust < 50 && day.score >= 62;
                r.days.add(day);
            }
            r.win = findWindow(r.days, minDays(c), today());
        } catch (Exception e) {
            r.error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        }
        return r;
    }

    static String today() { return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date()); }

    /** Yarından itibaren en yakın, en az minDays art arda iyi gün (en fazla 3 gün alınır). */
    static Window findWindow(List<Day> days, int minDays, String today) {
        for (int i = 0; i < days.size(); i++) {
            if (days.get(i).date.compareTo(today) <= 0) continue;
            int j = i;
            while (j < days.size() && days.get(j).good) j++;
            int run = j - i;
            if (run >= minDays) {
                int len = Math.min(run, 3);
                Window w = new Window();
                w.start = days.get(i).date;
                w.end = days.get(i + len - 1).date;
                w.len = len;
                w.tmin = 99; w.tmax = -99; w.minModels = 99;
                double dry = 0, cl = 0, sun = 0, gust = 0, sc = 0;
                int ns = 0;
                for (int k = i; k < i + len; k++) {
                    Day d = days.get(k);
                    dry += d.dry; cl += d.cloud; sc += d.score; gust = Math.max(gust, d.gust);
                    if (!Double.isNaN(d.sunH)) { sun += d.sunH; ns++; }
                    if (!Double.isNaN(d.tmin)) w.tmin = Math.min(w.tmin, d.tmin);
                    if (!Double.isNaN(d.tmax)) w.tmax = Math.max(w.tmax, d.tmax);
                    w.minModels = Math.min(w.minModels, d.n);
                }
                w.dry = dry / len; w.cloud = cl / len; w.score = (int) Math.round(sc / len); w.gust = gust;
                w.sunH = ns > 0 ? sun / ns : Double.NaN;
                return w;
            }
            if (run > 0) i = j - 1;
        }
        return null;
    }

    // ---------- metinler ----------
    static Calendar cal(String date) {
        Calendar c = Calendar.getInstance();
        try { c.setTime(new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(date)); } catch (Exception ignored) { }
        return c;
    }

    public static String dayShort(String date) {
        Calendar c = cal(date);
        return c.get(Calendar.DAY_OF_MONTH) + " " + MN[c.get(Calendar.MONTH)] + " " + DN[c.get(Calendar.DAY_OF_WEEK) - 1];
    }

    public static String range(Window w) {
        Calendar a = cal(w.start), b = cal(w.end);
        String s = a.get(Calendar.DAY_OF_MONTH) + (a.get(Calendar.MONTH) == b.get(Calendar.MONTH) ? "" : " " + MN[a.get(Calendar.MONTH)])
                + "–" + b.get(Calendar.DAY_OF_MONTH) + " " + MN[b.get(Calendar.MONTH)];
        return s + " (" + DN[a.get(Calendar.DAY_OF_WEEK) - 1] + "–" + DN[b.get(Calendar.DAY_OF_WEEK) - 1] + ")";
    }

    public static int daysUntil(String date) {
        long a = cal(today()).getTimeInMillis(), b = cal(date).getTimeInMillis();
        return (int) Math.round((b - a) / 86400000.0);
    }

    public static String confidence(Window w) {
        int lead = daysUntil(w.end);
        if (w.minModels <= 2 || lead >= 12) return "düşük (uzak tarih, az model)";
        if (w.minModels <= 4 || lead >= 7) return "orta";
        return "yüksek";
    }

    public static String summary(Window w) {
        String sky = w.cloud < 30 ? "açık ve güneşli" : w.cloud < 50 ? "çoğunlukla güneşli" : "parçalı bulutlu";
        String wind = w.gust < 25 ? "rüzgâr hafif" : w.gust < 40 ? "rüzgâr orta" : "rüzgâr sert";
        return "Yağmur ihtimali ~%" + Math.round((1 - w.dry) * 100) + " · " + sky
                + (Double.isNaN(w.sunH) ? "" : " (~" + Math.round(w.sunH) + " sa güneş)")
                + " · " + Math.round(w.tmin) + "…" + Math.round(w.tmax) + "°C · " + wind
                + (w.tmin < 0 ? " · ❄️ gece donu, kışlık uyku tulumu" : "")
                + " · güven " + confidence(w);
    }

    public static String icon(Day d) {
        if (d.dry < 0.5) return (!Double.isNaN(d.tmax) && d.tmax <= 1) ? "🌨️" : "🌧️";
        if (d.cloud < 35) return "☀️";
        if (d.cloud < 65) return "⛅";
        return "☁️";
    }

    // ---------- tarama + bildirim ----------
    /** notify=false: sadece kaydı günceller (uygulamada zaten görülüyor). */
    public static List<Result> checkAll(Context c, boolean notify) {
        List<Result> out = new ArrayList<>();
        for (City ci : cities(c)) {
            Result r = check(c, ci);
            out.add(r);
            if (r.error == null) handle(c, r, notify && enabled(c));
        }
        sp(c).edit().putLong("camp_last", System.currentTimeMillis()).apply();
        return out;
    }

    static void handle(Context c, Result r, boolean notify) {
        String k = "camp_n_" + key(r.city);
        String prev = sp(c).getString(k, "");
        String prevStart = prev.contains("|") ? prev.substring(0, prev.indexOf('|')) : "";
        if (r.win != null) {
            String now = r.win.start + "|" + r.win.end;
            if (!now.equals(prev)) {
                sp(c).edit().putString(k, now).apply();
                // sadece başlangıç günü değiştiyse (yeni aralık) bildir; bitiş oynaması sessiz güncellenir
                if (notify && !r.win.start.equals(prevStart)) {
                    int lead = daysUntil(r.win.start);
                    String when = lead == 1 ? "yarın başlıyor" : lead + " gün sonra";
                    post(c, r.city, "⛺ " + r.city.name + ": " + range(r.win) + " kamp için ideal",
                            when + " · " + r.win.len + " gün yağmursuz · " + summary(r.win));
                }
            }
        } else if (!prev.isEmpty()) {
            sp(c).edit().remove(k).apply();
            if (notify && prevStart.compareTo(today()) > 0) {
                post(c, r.city, "⛺ " + r.city.name + ": kamp aralığı bozuldu",
                        "Daha önce önerilen aralık artık uygun görünmüyor (yağış ya da bulut arttı). Yeni uygun aralık bulunca haber vereceğim.");
            }
        }
    }

    static void post(Context c, City ci, String title, String text) {
        try {
            NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm.getNotificationChannel(CH) == null) {
                NotificationChannel ch = new NotificationChannel(CH, "Kamp günü önerileri", NotificationManager.IMPORTANCE_DEFAULT);
                ch.setDescription("Seçtiğin illerde kamp için ideal günler");
                nm.createNotificationChannel(ch);
            }
            Intent open = new Intent(c, CampActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent pi = PendingIntent.getActivity(c, 500 + (key(ci).hashCode() & 0xffff), open,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            Notification n = new Notification.Builder(c, CH)
                    .setSmallIcon(R.drawable.ic_tent)
                    .setContentTitle(title)
                    .setContentText(text)
                    .setStyle(new Notification.BigTextStyle().bigText(text))
                    .setContentIntent(pi)
                    .setAutoCancel(true)
                    .build();
            nm.notify(key(ci).hashCode(), n);
        } catch (Exception ignored) { }
    }
}

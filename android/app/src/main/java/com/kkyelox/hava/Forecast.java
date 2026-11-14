package com.kkyelox.hava;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/** Siteyle aynı mantık: 9 deterministik model + ECMWF/NOAA ansambl → 3 günlük yağmur olasılığı. */
public class Forecast {
    static final String[] MODELS = {"ecmwf_ifs025", "ecmwf_aifs025_single", "gfs_seamless", "icon_seamless",
            "gem_seamless", "jma_seamless", "meteofrance_seamless", "ukmo_seamless", "cma_grapes_global"};
    static final String[] ENS = {"ecmwf_ifs025", "gfs025"};

    public static class Day {
        public String date;
        public double pop = -1;        // 0..1
        public int yes, total;         // model oylaması
        public double mm = -1;         // modellerin medyan toplamı
        public String window = "";     // "16-24" gibi
    }

    public static class Result {
        public double temp = Double.NaN;
        public int code = -1;
        public boolean isDay = true;
        public String now = "";        // yerel saat "2026-11-14T15:45"
        public Day[] days = new Day[3];
        public StringBuilder errors = new StringBuilder();
    }

    static String get(String u) throws Exception {
        Exception last = null;
        for (int a = 0; a < 3; a++) {
            try { return get1(u); }
            catch (Exception e) { last = e; try { Thread.sleep(1200L * (a + 1)); } catch (InterruptedException ignored) { } }
        }
        throw last;
    }

    static String get1(String u) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
        c.setConnectTimeout(10000);
        c.setReadTimeout(15000);
        c.setUseCaches(false);
        c.setRequestProperty("User-Agent", "KonsensusHava-Widget");
        try {
            BufferedReader r = new BufferedReader(new InputStreamReader(
                    c.getResponseCode() < 400 ? c.getInputStream() : c.getErrorStream(), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
            r.close();
            if (c.getResponseCode() >= 400) throw new Exception("HTTP " + c.getResponseCode());
            return sb.toString();
        } finally {
            c.disconnect();
        }
    }

    static double val(JSONArray a, int i) {
        if (a == null || i >= a.length() || a.isNull(i)) return Double.NaN;
        return a.optDouble(i, Double.NaN);
    }

    public static Result fetch(double lat, double lon) throws Exception {
        String ll = String.format(Locale.US, "latitude=%.4f&longitude=%.4f&timezone=auto", lat, lon);
        Result res = new Result();

        // tarihler (yedek: telefonun saati)
        java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US);
        java.util.Calendar cal = java.util.Calendar.getInstance();
        for (int d = 0; d < 3; d++) {
            res.days[d] = new Day();
            res.days[d].date = f.format(cal.getTime());
            cal.add(java.util.Calendar.DAY_OF_MONTH, 1);
        }
        res.now = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US).format(new java.util.Date());
        int okParts = 0;

        // 1) anlık
        try {
            JSONObject cur = new JSONObject(get("https://api.open-meteo.com/v1/forecast?" + ll
                    + "&current=temperature_2m,weather_code,is_day&forecast_days=3&daily=precipitation_sum"));
            JSONObject cc = cur.getJSONObject("current");
            res.temp = cc.optDouble("temperature_2m", Double.NaN);
            res.code = cc.optInt("weather_code", -1);
            res.isDay = cc.optInt("is_day", 1) == 1;
            res.now = cc.optString("time", res.now);
            JSONArray dts = cur.getJSONObject("daily").getJSONArray("time");
            for (int d = 0; d < 3 && d < dts.length(); d++) res.days[d].date = dts.optString(d, res.days[d].date);
            okParts++;
        } catch (Exception e) { res.errors.append("anlık: ").append(short_(e)).append("; "); }

        // 2) çoklu model günlük toplam yağış
        StringBuilder ms = new StringBuilder();
        for (String m : MODELS) { if (ms.length() > 0) ms.append(','); ms.append(m); }
        try {
            JSONObject md = new JSONObject(get("https://api.open-meteo.com/v1/forecast?" + ll
                    + "&forecast_days=3&daily=precipitation_sum&models=" + ms)).getJSONObject("daily");
            for (int d = 0; d < 3; d++) {
                List<Double> v = new ArrayList<>();
                for (String m : MODELS) {
                    double x = val(md.optJSONArray("precipitation_sum_" + m), d);
                    if (!Double.isNaN(x)) v.add(x);
                }
                Day day = res.days[d];
                day.total = v.size();
                for (double x : v) if (x >= 0.5) day.yes++;
                if (!v.isEmpty()) {
                    v.sort(null);
                    day.mm = v.get(v.size() / 2);
                }
            }
            okParts++;
        } catch (Exception e) { res.errors.append("modeller: ").append(short_(e)).append("; "); }

        // 3) ansambl: günlük ≥0.5 mm üye oranı + saatlik olasılık (yağış saatleri)
        double[] ensWet = new double[3];
        int[] ensN = new int[3];
        List<String> htimes = null;
        double[] hourWet = null;
        int[] hourN = null;
        for (String e : ENS) {
            try {
                JSONObject h = new JSONObject(get("https://ensemble-api.open-meteo.com/v1/ensemble?" + ll
                        + "&forecast_days=3&hourly=precipitation&models=" + e)).getJSONObject("hourly");
                JSONArray t = h.getJSONArray("time");
                if (htimes == null) {
                    htimes = new ArrayList<>();
                    for (int i = 0; i < t.length(); i++) htimes.add(t.getString(i));
                    hourWet = new double[t.length()];
                    hourN = new int[t.length()];
                }
                Iterator<String> it = h.keys();
                while (it.hasNext()) {
                    String k = it.next();
                    if (!k.startsWith("precipitation")) continue;
                    JSONArray a = h.getJSONArray(k);
                    double[] sum = new double[3];
                    int[] cnt = new int[3];
                    for (int i = 0; i < a.length() && i < t.length(); i++) {
                        double x = val(a, i);
                        if (Double.isNaN(x)) continue;
                        String day = t.getString(i).substring(0, 10);
                        for (int d = 0; d < 3; d++) if (day.equals(res.days[d].date)) { sum[d] += x; cnt[d]++; }
                        if (i < hourWet.length) { hourN[i]++; if (x >= 0.1) hourWet[i]++; }
                    }
                    for (int d = 0; d < 3; d++) if (cnt[d] >= 18) { ensN[d]++; if (sum[d] >= 0.5) ensWet[d]++; }
                }
                okParts++;
            } catch (Exception ex) { res.errors.append(e).append(": ").append(short_(ex)).append("; "); }
        }

        for (int d = 0; d < 3; d++) {
            Day day = res.days[d];
            Double pe = ensN[d] > 5 ? ensWet[d] / ensN[d] : null;
            Double pd = day.total > 0 ? (double) day.yes / day.total : null;
            if (pe != null && pd != null) day.pop = 0.65 * pe + 0.35 * pd;
            else if (pe != null) day.pop = pe;
            else if (pd != null) day.pop = pd;
            // yağışlı saat aralığı (olasılık ≥ %50)
            if (htimes != null) {
                int first = -1, last = -1;
                String nowH = res.now.length() >= 13 ? res.now.substring(0, 13) : "";
                for (int i = 0; i < htimes.size(); i++) {
                    String ti = htimes.get(i);
                    if (!ti.startsWith(day.date)) continue;
                    if (d == 0 && ti.substring(0, 13).compareTo(nowH) < 0) continue;
                    double p = hourN[i] > 0 ? hourWet[i] / hourN[i] : 0;
                    if (p >= 0.5) { if (first < 0) first = i; last = i; }
                }
                if (first >= 0) {
                    int a = Integer.parseInt(htimes.get(first).substring(11, 13));
                    int b = Integer.parseInt(htimes.get(last).substring(11, 13)) + 1;
                    day.window = String.format(Locale.US, "%02d–%02d", a, b);
                }
            }
        }
        if (okParts == 0) throw new Exception(res.errors.toString());
        return res;
    }

    static String short_(Exception e) {
        String m = e.getMessage();
        String n = e.getClass().getSimpleName();
        return n + (m == null ? "" : " " + (m.length() > 80 ? m.substring(0, 80) : m));
    }

    public static String icon(int code, boolean day) {
        if (code < 0) return "❔";
        if (code == 0) return day ? "☀️" : "🌙";
        if (code <= 2) return day ? "🌤️" : "🌙";
        if (code == 3) return "☁️";
        if (code <= 48) return "🌫️";
        if (code <= 67) return "🌧️";
        if (code <= 77) return "🌨️";
        if (code <= 82) return "🌦️";
        if (code <= 86) return "🌨️";
        return "⛈️";
    }

    /** 1..5 seviye ve kısa etiket (sitedeki eşiklerle aynı). */
    public static int level(double p) {
        if (p < 0) return 0;
        if (p >= 0.8) return 5;
        if (p >= 0.6) return 4;
        if (p >= 0.4) return 3;
        if (p >= 0.2) return 2;
        return 1;
    }

    public static String label(int lv) {
        switch (lv) {
            case 5: return "Yağacak";
            case 4: return "Büyük ihtimal";
            case 3: return "Belirsiz";
            case 2: return "Az ihtimal";
            case 1: return "Yağmaz";
            default: return "veri yok";
        }
    }
}

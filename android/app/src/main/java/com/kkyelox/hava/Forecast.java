package com.kkyelox.hava;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Veri indirme ve sitedeki mantıkla birleştirme: 9 model + ECMWF/NOAA ansambl. */
public class Forecast {
    static final String[] MODELS = {"ecmwf_ifs025", "ecmwf_aifs025_single", "gfs_seamless", "icon_seamless",
            "gem_seamless", "jma_seamless", "meteofrance_seamless", "ukmo_seamless", "cma_grapes_global"};
    static final String[] ENS = {"ecmwf_ifs025", "gfs025"};
    static final int ND = 7;

    public static class Hour {
        public String time = "";
        public double temp = Double.NaN, pop = -1, mm = 0;
        public int code = -1;
        public boolean day = true;
    }

    public static class Day {
        public String date = "";
        public double tmax = Double.NaN, tmin = Double.NaN, pop = -1, mm = -1, uv = Double.NaN;
        public int code = -1, yes, total;
        public String window = "", sunrise = "", sunset = "";
    }

    public static class Result {
        public double temp = Double.NaN, feels = Double.NaN, rh = Double.NaN, wind = Double.NaN, gust = Double.NaN, dir = Double.NaN;
        public int code = -1;
        public boolean isDay = true;
        public String now = "";
        public List<Hour> hours = new ArrayList<>();
        public Day[] days = new Day[ND];
        public StringBuilder errors = new StringBuilder();
        public long fetched;
        public boolean stale;
    }

    // ---------- ağ ----------
    static String get(String u) throws Exception {
        Exception last = null;
        for (int a = 0; a < 3; a++) {
            try { return get1(u); } catch (Exception e) {
                last = e;
                try { Thread.sleep(1200L * (a + 1)); } catch (InterruptedException ignored) { }
            }
        }
        throw last;
    }

    static String get1(String u) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
        c.setConnectTimeout(10000);
        c.setReadTimeout(15000);
        c.setUseCaches(false);
        c.setRequestProperty("User-Agent", "KonsensusHava-Android");
        try {
            int code = c.getResponseCode();
            java.io.InputStream in = code < 400 ? c.getInputStream() : c.getErrorStream();
            StringBuilder sb = new StringBuilder();
            if (in != null) {
                BufferedReader r = new BufferedReader(new InputStreamReader(in, "UTF-8"));
                String line;
                while ((line = r.readLine()) != null) sb.append(line);
                r.close();
            }
            if (code >= 400) throw new Exception("HTTP " + code + " " + (sb.length() > 80 ? sb.substring(0, 80) : sb));
            return sb.toString();
        } finally {
            c.disconnect();
        }
    }

    static String short_(Exception e) {
        String m = e.getMessage();
        return e.getClass().getSimpleName() + (m == null ? "" : " " + (m.length() > 70 ? m.substring(0, 70) : m));
    }

    /** Ham yanıtları indirir; hiçbiri gelmezse hata fırlatır. */
    public static JSONObject download(double lat, double lon) throws Exception {
        String ll = String.format(Locale.US, "latitude=%.4f&longitude=%.4f&timezone=auto", lat, lon);
        JSONObject raw = new JSONObject();
        StringBuilder err = new StringBuilder();
        int ok = 0;
        try {
            raw.put("main", get("https://api.open-meteo.com/v1/forecast?" + ll
                    + "&current=temperature_2m,apparent_temperature,relative_humidity_2m,wind_speed_10m,wind_direction_10m,wind_gusts_10m,weather_code,is_day"
                    + "&hourly=temperature_2m,weather_code,precipitation,precipitation_probability,is_day"
                    + "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_sum,sunrise,sunset,uv_index_max&forecast_days=" + ND));
            ok++;
        } catch (Exception e) { err.append("ana: ").append(short_(e)).append("; "); }
        StringBuilder ms = new StringBuilder();
        for (String m : MODELS) { if (ms.length() > 0) ms.append(','); ms.append(m); }
        try {
            raw.put("models", get("https://api.open-meteo.com/v1/forecast?" + ll + "&forecast_days=" + ND + "&daily=precipitation_sum&models=" + ms));
            ok++;
        } catch (Exception e) { err.append("modeller: ").append(short_(e)).append("; "); }
        for (int i = 0; i < ENS.length; i++) {
            try {
                raw.put("ens" + i, get("https://ensemble-api.open-meteo.com/v1/ensemble?" + ll + "&forecast_days=" + ND + "&hourly=precipitation&models=" + ENS[i]));
                ok++;
            } catch (Exception e) { err.append(ENS[i]).append(": ").append(short_(e)).append("; "); }
        }
        if (ok == 0) throw new Exception(err.toString());
        raw.put("err", err.toString());
        raw.put("t", System.currentTimeMillis());
        return raw;
    }

    static final String CUR = "temperature_2m,apparent_temperature,relative_humidity_2m,wind_speed_10m,wind_direction_10m,wind_gusts_10m,weather_code,is_day";

    /** 2 dakikalık hafif istek: sadece anlık değerler. */
    public static String downloadCurrent(double lat, double lon) throws Exception {
        String ll = String.format(Locale.US, "latitude=%.4f&longitude=%.4f&timezone=auto", lat, lon);
        return get1("https://api.open-meteo.com/v1/forecast?" + ll + "&current=" + CUR);
    }

    static void applyCurrent(Result res, JSONObject c) {
        res.temp = c.optDouble("temperature_2m", res.temp);
        res.feels = c.optDouble("apparent_temperature", res.feels);
        res.rh = c.optDouble("relative_humidity_2m", res.rh);
        res.wind = c.optDouble("wind_speed_10m", res.wind);
        res.gust = c.optDouble("wind_gusts_10m", res.gust);
        res.dir = c.optDouble("wind_direction_10m", res.dir);
        res.code = c.optInt("weather_code", res.code);
        res.isDay = c.optInt("is_day", res.isDay ? 1 : 0) == 1;
        res.now = c.optString("time", res.now);
    }

    static double val(JSONArray a, int i) {
        if (a == null || i < 0 || i >= a.length() || a.isNull(i)) return Double.NaN;
        return a.optDouble(i, Double.NaN);
    }

    /** Ham yanıtlardan sonuç üretir (sitedeki formül). */
    public static Result parse(JSONObject raw) {
        Result res = new Result();
        res.fetched = raw.optLong("t", System.currentTimeMillis());
        res.errors.append(raw.optString("err", ""));
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        Calendar cal = Calendar.getInstance();
        for (int d = 0; d < ND; d++) {
            res.days[d] = new Day();
            res.days[d].date = f.format(cal.getTime());
            cal.add(Calendar.DAY_OF_MONTH, 1);
        }
        res.now = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US).format(new Date());
        JSONArray bmTime = null, bmTemp = null, bmCode = null, bmPrec = null, bmProb = null, bmDay = null;
        // ana
        try {
            if (raw.has("main")) {
                JSONObject m = new JSONObject(raw.getString("main"));
                applyCurrent(res, m.getJSONObject("current"));
                if (raw.has("cur") && raw.optLong("tc", 0) >= raw.optLong("t", 0)) {
                    try { applyCurrent(res, new JSONObject(raw.getString("cur")).getJSONObject("current")); } catch (Exception ignored) { }
                }
                res.fetched = Math.max(raw.optLong("t", 0), raw.optLong("tc", 0));
                JSONObject dl = m.getJSONObject("daily");
                JSONArray dt = dl.getJSONArray("time");
                for (int d = 0; d < ND && d < dt.length(); d++) {
                    Day day = res.days[d];
                    day.date = dt.optString(d, day.date);
                    day.tmax = val(dl.optJSONArray("temperature_2m_max"), d);
                    day.tmin = val(dl.optJSONArray("temperature_2m_min"), d);
                    double cd = val(dl.optJSONArray("weather_code"), d);
                    day.code = Double.isNaN(cd) ? -1 : (int) cd;
                    day.uv = val(dl.optJSONArray("uv_index_max"), d);
                    JSONArray sr = dl.optJSONArray("sunrise"), ss = dl.optJSONArray("sunset");
                    if (sr != null && sr.optString(d, "").length() >= 16) day.sunrise = sr.optString(d).substring(11, 16);
                    if (ss != null && ss.optString(d, "").length() >= 16) day.sunset = ss.optString(d).substring(11, 16);
                    double ps = val(dl.optJSONArray("precipitation_sum"), d);
                    if (!Double.isNaN(ps)) day.mm = ps;
                }
                JSONObject h = m.getJSONObject("hourly");
                bmTime = h.optJSONArray("time");
                bmTemp = h.optJSONArray("temperature_2m");
                bmCode = h.optJSONArray("weather_code");
                bmPrec = h.optJSONArray("precipitation");
                bmProb = h.optJSONArray("precipitation_probability");
                bmDay = h.optJSONArray("is_day");
            }
        } catch (Exception e) { res.errors.append("ana-çözüm: ").append(short_(e)).append("; "); }
        // model oylaması
        try {
            if (raw.has("models")) {
                JSONObject md = new JSONObject(raw.getString("models")).getJSONObject("daily");
                for (int d = 0; d < ND; d++) {
                    List<Double> v = new ArrayList<>();
                    for (String mo : MODELS) {
                        double x = val(md.optJSONArray("precipitation_sum_" + mo), d);
                        if (!Double.isNaN(x)) v.add(x);
                    }
                    Day day = res.days[d];
                    day.total = v.size();
                    day.yes = 0;
                    for (double x : v) if (x >= 0.5) day.yes++;
                    if (!v.isEmpty()) { v.sort(null); day.mm = v.get(v.size() / 2); }
                }
            }
        } catch (Exception e) { res.errors.append("modeller-çözüm: ").append(short_(e)).append("; "); }
        // ansambl
        double[] ensWet = new double[ND];
        int[] ensN = new int[ND];
        Map<String, double[]> hourly = new HashMap<>(); // zaman -> {ıslak, toplam}
        for (int i = 0; i < ENS.length; i++) {
            try {
                if (!raw.has("ens" + i)) continue;
                JSONObject h = new JSONObject(raw.getString("ens" + i)).getJSONObject("hourly");
                JSONArray t = h.getJSONArray("time");
                Iterator<String> it = h.keys();
                while (it.hasNext()) {
                    String k = it.next();
                    if (!k.startsWith("precipitation")) continue;
                    JSONArray a = h.getJSONArray(k);
                    double[] sum = new double[ND];
                    int[] cnt = new int[ND];
                    for (int j = 0; j < a.length() && j < t.length(); j++) {
                        double x = val(a, j);
                        if (Double.isNaN(x)) continue;
                        String ti = t.getString(j);
                        String dd = ti.substring(0, 10);
                        for (int d = 0; d < ND; d++) if (dd.equals(res.days[d].date)) { sum[d] += x; cnt[d]++; }
                        double[] hw = hourly.get(ti);
                        if (hw == null) { hw = new double[2]; hourly.put(ti, hw); }
                        hw[1]++;
                        if (x >= 0.1) hw[0]++;
                    }
                    for (int d = 0; d < ND; d++) if (cnt[d] >= 18) { ensN[d]++; if (sum[d] >= 0.5) ensWet[d]++; }
                }
            } catch (Exception e) { res.errors.append("ansambl-çözüm: ").append(short_(e)).append("; "); }
        }
        String nowH = res.now.length() >= 13 ? res.now.substring(0, 13) : "";
        try {
            // önbellek eskiyse "şimdi"yi telefonun saatine göre ilerlet (yerel saat dilimi aynı varsayımıyla)
            String phone = new SimpleDateFormat("yyyy-MM-dd'T'HH", Locale.US).format(new Date());
            if (phone.compareTo(nowH) > 0) nowH = phone;
        } catch (Exception ignored) { }
        // günlük olasılık + yağış saatleri
        for (int d = 0; d < ND; d++) {
            Day day = res.days[d];
            Double pe = ensN[d] > 5 ? ensWet[d] / ensN[d] : null;
            Double pd = day.total > 0 ? (double) day.yes / day.total : null;
            if (pe != null && pd != null) day.pop = 0.65 * pe + 0.35 * pd;
            else if (pe != null) day.pop = pe;
            else if (pd != null) day.pop = pd;
            else if (bmProb != null && bmTime != null) {
                double mx = -1;
                for (int j = 0; j < bmTime.length(); j++) if (bmTime.optString(j).startsWith(day.date)) mx = Math.max(mx, val(bmProb, j));
                if (mx >= 0) day.pop = mx / 100.0;
            }
            int first = -1, last = -1;
            for (int hr = 0; hr < 24; hr++) {
                String ti = day.date + "T" + String.format(Locale.US, "%02d", hr) + ":00";
                if (d == 0 && ti.substring(0, 13).compareTo(nowH) < 0) continue;
                double p = hourPop(hourly, ti, bmTime, bmProb);
                if (p >= 0.5) { if (first < 0) first = hr; last = hr; }
            }
            if (first >= 0) day.window = String.format(Locale.US, "%02d–%02d", first, last + 1);
        }
        // önümüzdeki 24 saat
        if (bmTime != null) {
            int start = 0;
            for (int j = 0; j < bmTime.length(); j++) if (bmTime.optString(j).substring(0, 13).compareTo(nowH) >= 0) { start = j; break; }
            for (int j = start; j < bmTime.length() && res.hours.size() < 24; j++) {
                Hour h = new Hour();
                h.time = bmTime.optString(j);
                h.temp = val(bmTemp, j);
                double cd = val(bmCode, j);
                h.code = Double.isNaN(cd) ? -1 : (int) cd;
                double pr = val(bmPrec, j);
                h.mm = Double.isNaN(pr) ? 0 : pr;
                h.day = val(bmDay, j) != 0;
                h.pop = hourPop(hourly, h.time, bmTime, bmProb);
                res.hours.add(h);
            }
        }
        return res;
    }

    static double hourPop(Map<String, double[]> hourly, String ti, JSONArray bmTime, JSONArray bmProb) {
        double[] hw = hourly.get(ti);
        if (hw != null && hw[1] > 5) return hw[0] / hw[1];
        if (bmTime != null && bmProb != null) {
            for (int j = 0; j < bmTime.length(); j++) if (ti.equals(bmTime.optString(j))) {
                double v = val(bmProb, j);
                return Double.isNaN(v) ? -1 : v / 100.0;
            }
        }
        return -1;
    }

    // ---------- görünüm yardımcıları ----------
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

    public static String desc(int c) {
        if (c < 0) return "";
        if (c == 0) return "Açık";
        if (c == 1) return "Çoğunlukla açık";
        if (c == 2) return "Parçalı bulutlu";
        if (c == 3) return "Kapalı";
        if (c <= 48) return "Sis";
        if (c <= 55) return "Çisenti";
        if (c <= 57) return "Dondurucu çisenti";
        if (c == 61) return "Hafif yağmur";
        if (c == 63) return "Yağmur";
        if (c <= 67) return "Kuvvetli yağmur";
        if (c <= 77) return "Kar";
        if (c <= 82) return "Sağanak";
        if (c <= 86) return "Kar sağanağı";
        return "Gök gürültülü fırtına";
    }

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

    public static String dirTxt(double d) {
        if (Double.isNaN(d)) return "";
        String[] n = {"K", "KD", "D", "GD", "G", "GB", "B", "KB"};
        return n[(int) Math.round(d / 45.0) % 8];
    }
}

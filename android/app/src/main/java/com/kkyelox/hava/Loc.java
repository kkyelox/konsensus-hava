package com.kkyelox.hava;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;

import org.json.JSONObject;

import java.util.Locale;

/** Konum: seçilen sabit yer, yoksa cihaz konumu; widget başına geçersiz kılınabilir. */
public class Loc {
    public double lat, lon;
    public String name = "";
    public boolean auto;

    Loc(double lat, double lon, String name, boolean auto) { this.lat = lat; this.lon = lon; this.name = name == null ? "" : name; this.auto = auto; }

    static final long CHECK_EVERY = 30 * 60_000L;

    public String key() { return String.format(Locale.US, "%.2f_%.2f", lat, lon); }

    static SharedPreferences sp(Context c) { return c.getSharedPreferences("hava", Context.MODE_PRIVATE); }
    static double gd(SharedPreferences p, String k) { return Double.longBitsToDouble(p.getLong(k, 0)); }
    static void pd(SharedPreferences.Editor e, String k, double v) { e.putLong(k, Double.doubleToRawLongBits(v)); }

    // ---- varsayılan (uygulama) ----
    public static String globalMode(Context c) {
        SharedPreferences p = sp(c);
        return p.getString("g_mode", p.contains("lat") ? "fixed" : "auto");
    }

    public static void setGlobalFixed(Context c, double lat, double lon, String name) {
        SharedPreferences.Editor e = sp(c).edit();
        pd(e, "lat", lat); pd(e, "lon", lon);
        e.putString("name", name).putString("g_mode", "fixed").apply();
    }

    public static void setGlobalAuto(Context c) { sp(c).edit().putString("g_mode", "auto").apply(); }

    // ---- widget başına ----
    public static String widgetMode(Context c, int id) { return sp(c).getString("w" + id + "_mode", "app"); }

    public static void setWidget(Context c, int id, String mode, double lat, double lon, String name) {
        SharedPreferences.Editor e = sp(c).edit().putString("w" + id + "_mode", mode);
        if ("fixed".equals(mode)) { pd(e, "w" + id + "_lat", lat); pd(e, "w" + id + "_lon", lon); e.putString("w" + id + "_name", name); }
        e.apply();
    }

    public static void removeWidget(Context c, int id) {
        sp(c).edit().remove("w" + id + "_mode").remove("w" + id + "_lat").remove("w" + id + "_lon").remove("w" + id + "_name").apply();
    }

    // ---- cihaz konumu ----
    public static boolean hasPermission(Context c) {
        return c.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || c.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    static Location lastKnown(Context c) {
        if (!hasPermission(c)) return null;
        Location best = null;
        try {
            LocationManager lm = (LocationManager) c.getSystemService(Context.LOCATION_SERVICE);
            for (String pr : lm.getProviders(true)) {
                Location l = lm.getLastKnownLocation(pr);
                if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
            }
        } catch (Exception ignored) { }
        return best;
    }

    /** Yeni bir cihaz konumu kaydeder; gerekirse yer adını bulur (ağ — arka planda çağır). */
    public static void setDevice(Context c, double lat, double lon, long time, boolean network) {
        SharedPreferences p = sp(c);
        String name = p.getString("dev_name", "");
        boolean moved = !p.contains("dev_lat") || distKm(lat, lon, gd(p, "dev_lat"), gd(p, "dev_lon")) > 2;
        if ((moved || name.isEmpty()) && network) {
            String n = reverse(lat, lon);
            if (n != null) name = n;
        }
        if (name.isEmpty() || (moved && !network)) name = String.format(Locale.US, "%.2f, %.2f", lat, lon);
        SharedPreferences.Editor e = p.edit();
        pd(e, "dev_lat", lat); pd(e, "dev_lon", lon);
        e.putLong("dev_time", time).putLong("dev_check", System.currentTimeMillis()).putString("dev_name", name).apply();
    }

    public static Loc device(Context c, boolean network) {
        SharedPreferences p = sp(c);
        long now = System.currentTimeMillis();
        if (now - p.getLong("dev_check", 0) > CHECK_EVERY) {
            p.edit().putLong("dev_check", now).apply();
            Location l = lastKnown(c);
            if (l != null && l.getTime() > p.getLong("dev_time", 0)) setDevice(c, l.getLatitude(), l.getLongitude(), l.getTime(), network);
        }
        if (!p.contains("dev_lat")) return null;
        return new Loc(gd(p, "dev_lat"), gd(p, "dev_lon"), p.getString("dev_name", "Konumum"), true);
    }

    public static Loc global(Context c, boolean network) {
        SharedPreferences p = sp(c);
        if ("fixed".equals(globalMode(c)) && p.contains("lat")) return new Loc(gd(p, "lat"), gd(p, "lon"), p.getString("name", "Konum"), false);
        return device(c, network);
    }

    public static Loc forWidget(Context c, int id, boolean network) {
        SharedPreferences p = sp(c);
        String m = widgetMode(c, id);
        if ("fixed".equals(m) && p.contains("w" + id + "_lat"))
            return new Loc(gd(p, "w" + id + "_lat"), gd(p, "w" + id + "_lon"), p.getString("w" + id + "_name", "Konum"), false);
        if ("auto".equals(m)) return device(c, network);
        return global(c, network);
    }

    /** Bulunduğun yeri kullanan bir ayar var mı? (yoksa konuma hiç dokunma) */
    public static boolean usesDevice(Context c) {
        if (!"fixed".equals(globalMode(c))) return true;
        for (String k : sp(c).getAll().keySet())
            if (k.startsWith("w") && k.endsWith("_mode") && "auto".equals(sp(c).getString(k, ""))) return true;
        return false;
    }

    /** Son taze konum alımından bu yana 30 dk geçti mi? */
    public static boolean freshDue(Context c) {
        return System.currentTimeMillis() - sp(c).getLong("dev_check", 0) > CHECK_EVERY;
    }

    public static String describe(Context c) {
        Loc g = global(c, false);
        if ("fixed".equals(globalMode(c))) return "Varsayılan konum: " + (g == null ? "yok" : g.name) + " (sabit)";
        return "Varsayılan konum: 📍 bulunduğun yer" + (g == null ? " (henüz alınmadı — konum izni ver)" : " — " + g.name);
    }

    static String reverse(double lat, double lon) {
        try {
            JSONObject j = new JSONObject(Forecast.get(String.format(Locale.US,
                    "https://api.bigdatacloud.net/data/reverse-geocode-client?latitude=%.4f&longitude=%.4f&localityLanguage=tr", lat, lon)));
            String n = j.optString("locality", "");
            if (n.isEmpty()) n = j.optString("city", "");
            return n.isEmpty() ? null : n;
        } catch (Exception e) { return null; }
    }

    static double distKm(double a, double b, double c, double d) {
        double R = 6371, x = Math.toRadians(c - a), y = Math.toRadians(d - b);
        double h = Math.sin(x / 2) * Math.sin(x / 2) + Math.cos(Math.toRadians(a)) * Math.cos(Math.toRadians(c)) * Math.sin(y / 2) * Math.sin(y / 2);
        return 2 * R * Math.asin(Math.sqrt(h));
    }
}

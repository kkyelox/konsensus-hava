package com.kkyelox.hava;

import android.content.Context;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

/** Konum başına önbellek: tüm widget'lar aynı veriyi paylaşır (25 dk). */
public class Data {
    static final long FRESH = 25 * 60 * 1000L;
    public static volatile String lastError = "";

    static File file(Context c, Loc l) { return new File(c.getCacheDir(), "wx_" + l.key() + ".json"); }

    static JSONObject read(File f) {
        try (FileInputStream in = new FileInputStream(f)) {
            byte[] b = new byte[(int) f.length()];
            int n = 0;
            while (n < b.length) { int r = in.read(b, n, b.length - n); if (r < 0) break; n += r; }
            return new JSONObject(new String(b, 0, n, StandardCharsets.UTF_8));
        } catch (Exception e) { return null; }
    }

    public static Forecast.Result get(Context c, Loc l, boolean force) {
        File f = file(c, l);
        JSONObject cached = f.exists() ? read(f) : null;
        if (!force && cached != null && System.currentTimeMillis() - cached.optLong("t", 0) < FRESH) return Forecast.parse(cached);
        try {
            JSONObject raw = Forecast.download(l.lat, l.lon);
            try (FileOutputStream out = new FileOutputStream(f)) { out.write(raw.toString().getBytes(StandardCharsets.UTF_8)); }
            lastError = "";
            return Forecast.parse(raw);
        } catch (Exception e) {
            lastError = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            if (cached != null) { Forecast.Result r = Forecast.parse(cached); r.stale = true; return r; }
            return null;
        }
    }

    /** Ağ kullanmadan, varsa önbellek. */
    public static Forecast.Result cachedOnly(Context c, Loc l) {
        File f = file(c, l);
        JSONObject cached = f.exists() ? read(f) : null;
        if (cached == null) return null;
        Forecast.Result r = Forecast.parse(cached);
        r.stale = System.currentTimeMillis() - cached.optLong("t", 0) > FRESH;
        return r;
    }
}

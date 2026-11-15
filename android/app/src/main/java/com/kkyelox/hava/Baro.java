package com.kkyelox.hava;

import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.HandlerThread;

import org.json.JSONArray;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Telefonun barometresi: kısa okuma (birkaç sn), son 48 saati saklar. Ağ ve GPS kullanmaz. */
public class Baro {
    static final long KEEP = 48 * 3600_000L;
    static final long MIN_GAP = 5 * 60_000L;

    public static boolean available(Context c) {
        SensorManager sm = (SensorManager) c.getSystemService(Context.SENSOR_SERVICE);
        return sm != null && sm.getDefaultSensor(Sensor.TYPE_PRESSURE) != null;
    }

    /** Ana iş parçacığında çağırma. NaN = okunamadı. */
    public static float read(Context c, long timeoutMs) {
        SensorManager sm = (SensorManager) c.getSystemService(Context.SENSOR_SERVICE);
        if (sm == null) return Float.NaN;
        Sensor s = sm.getDefaultSensor(Sensor.TYPE_PRESSURE);
        if (s == null) return Float.NaN;
        final float[] sum = {0};
        final int[] n = {0};
        final CountDownLatch latch = new CountDownLatch(1);
        HandlerThread ht = new HandlerThread("baro");
        ht.start();
        SensorEventListener l = new SensorEventListener() {
            @Override public void onSensorChanged(SensorEvent e) {
                synchronized (sum) {
                    float v = e.values[0];
                    if (v > 300 && v < 1100) { sum[0] += v; n[0]++; }
                    if (n[0] >= 6) latch.countDown();
                }
            }
            @Override public void onAccuracyChanged(Sensor s, int a) { }
        };
        sm.registerListener(l, s, SensorManager.SENSOR_DELAY_NORMAL, new Handler(ht.getLooper()));
        try { latch.await(timeoutMs, TimeUnit.MILLISECONDS); } catch (InterruptedException ignored) { }
        sm.unregisterListener(l);
        ht.quitSafely();
        synchronized (sum) { return n[0] > 0 ? sum[0] / n[0] : Float.NaN; }
    }

    /** Ölç ve kaydet. */
    public static void record(Context c) {
        if (!available(c)) return;
        float p = read(c, 4000);
        if (!Float.isNaN(p)) add(c, System.currentTimeMillis(), p);
    }

    static SharedPreferences sp(Context c) { return c.getSharedPreferences("hava", Context.MODE_PRIVATE); }

    static synchronized void add(Context c, long t, float p) {
        try {
            JSONArray a = new JSONArray(sp(c).getString("baro", "[]"));
            JSONArray out = new JSONArray();
            for (int i = 0; i < a.length(); i++) {
                JSONArray r = a.getJSONArray(i);
                if (t - r.getLong(0) < KEEP) out.put(r);
            }
            // son okuma 5 dk'dan yeniyse onun yerine yaz
            if (out.length() > 0 && t - out.getJSONArray(out.length() - 1).getLong(0) < MIN_GAP) out.remove(out.length() - 1);
            JSONArray r = new JSONArray();
            r.put(t);
            r.put(Math.round(p * 100) / 100.0);
            out.put(r);
            sp(c).edit().putString("baro", out.toString()).apply();
        } catch (Exception ignored) { }
    }

    public static String json(Context c) { return sp(c).getString("baro", "[]"); }

    /** {son değer hPa (istasyon), 3 saatlik değişim hPa veya NaN}. null = veri yok. */
    public static double[] trend(Context c) {
        try {
            JSONArray a = new JSONArray(json(c));
            if (a.length() == 0) return null;
            JSONArray last = a.getJSONArray(a.length() - 1);
            long t = last.getLong(0);
            double p = last.getDouble(1);
            if (System.currentTimeMillis() - t > 3 * 3600_000L) return null;
            double d3 = Double.NaN, best = 1e18;
            for (int i = 0; i < a.length() - 1; i++) {
                JSONArray r = a.getJSONArray(i);
                double dt = (t - r.getLong(0)) / 3600_000.0;
                if (dt < 2.25 || dt > 4) continue;
                double off = Math.abs(dt - 3);
                if (off < best) { best = off; d3 = (p - r.getDouble(1)) * 3.0 / dt; }
            }
            return new double[]{p, d3};
        } catch (Exception e) { return null; }
    }
}

package com.kkyelox.hava;

import android.Manifest;
import android.app.Activity;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;

/** Ön plandayken taze konum alır. */
public class Gps {
    public interface Cb { void done(Location l); }

    public static final int REQ = 41;

    public static void ask(Activity a) {
        a.requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION}, REQ);
    }

    public static void fresh(Activity a, Cb cb0) {
        if (!Loc.hasPermission(a)) { cb0.done(null); return; }
        final boolean[] once = {false};
        final android.os.Handler h = new android.os.Handler(Looper.getMainLooper());
        final Cb cb = l -> { if (once[0]) return; once[0] = true; cb0.done(l); };
        h.postDelayed(() -> cb.done(Loc.lastKnown(a)), 8000); // en fazla 8 sn bekle
        LocationManager lm = (LocationManager) a.getSystemService(Activity.LOCATION_SERVICE);
        try {
            String prov = lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) ? LocationManager.NETWORK_PROVIDER
                    : (lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ? LocationManager.GPS_PROVIDER : null);
            if (prov == null) { cb.done(Loc.lastKnown(a)); return; }
            if (Build.VERSION.SDK_INT >= 30) {
                lm.getCurrentLocation(prov, null, a.getMainExecutor(), l -> cb.done(l != null ? l : Loc.lastKnown(a)));
            } else {
                lm.requestSingleUpdate(prov, new android.location.LocationListener() {
                    @Override public void onLocationChanged(Location l) { cb.done(l); }
                    @Override public void onStatusChanged(String s, int i, Bundle b) { }
                    @Override public void onProviderEnabled(String s) { }
                    @Override public void onProviderDisabled(String s) { cb.done(Loc.lastKnown(a)); }
                }, Looper.getMainLooper());
            }
        } catch (SecurityException e) {
            cb.done(null);
        }
    }

    /** Taze konumu kaydet (arka planda yer adı) ve widget'ları yenile. */
    public static void freshAndSave(Activity a, Runnable after) { freshAndSave(a, after, false); }

    public static void freshAndSave(Activity a, Runnable after, boolean forceData) {
        fresh(a, l -> {
            final android.content.Context app = a.getApplicationContext();
            new Thread(() -> {
                if (l != null) Loc.setDevice(app, l.getLatitude(), l.getLongitude(), System.currentTimeMillis(), true);
                Updater.updateAll(app, forceData);
                if (after != null) a.runOnUiThread(after);
            }).start();
        });
    }
}

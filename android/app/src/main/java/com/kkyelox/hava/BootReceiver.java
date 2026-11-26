package com.kkyelox.hava;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        Ticker.schedule(c.getApplicationContext());
        BaroTicker.schedule(c.getApplicationContext());
        CampTicker.ensure(c.getApplicationContext());
        final PendingResult pr = goAsync();
        final Context app = c.getApplicationContext();
        new Thread(() -> { try { Updater.updateAll(app, false); } catch (Throwable ignored) { } finally { pr.finish(); } }).start();
    }
}

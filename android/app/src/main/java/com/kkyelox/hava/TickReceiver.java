package com.kkyelox.hava;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class TickReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        final PendingResult pr = goAsync();
        final Context app = c.getApplicationContext();
        new Thread(() -> {
            try { Updater.updateAll(app, false); } catch (Throwable ignored) { } finally {
                Ticker.schedule(app);
                pr.finish();
            }
        }).start();
    }
}

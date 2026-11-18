package com.codeaque.launcher;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;

/**
 * 0.10.58: Nova no longer sits over other apps. This stub stays registered under the
 * old name so a sticky overlay from 0.10.57 is stopped on update instead of redrawn.
 */
public class NovaOverlayService extends Service {
    public static void retire(Context c) {
        if (c == null) return;
        try {
            c.getSharedPreferences("codeaque", Context.MODE_PRIVATE)
                    .edit().putBoolean(NovaView.PREF_OVERLAY, false).apply();
        } catch (Exception ignored) { }
        try { c.stopService(new Intent(c, NovaOverlayService.class)); } catch (Exception ignored) { }
    }

    /** @deprecated Overlay mode is gone; this only stops any leftover service. */
    public static void sync(Context c) { retire(c); }

    /** @deprecated No overlay window to hide or show. */
    public static void setHostVisible(Context c, boolean visible) { }

    @Override public void onCreate() {
        super.onCreate();
        stopSelf();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        stopSelf();
        return START_NOT_STICKY;
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}

package com.codeaque.launcher;

import android.content.Context;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;

/**
 * 0.10.62: duck system / notification stream volume only while the
 * recognizer is starting, so the start beep is muted. Always restore.
 * Named top-level class for d8 8.2.
 */
public final class NovaListenBeep {
    static final int[] STREAMS = new int[] {
            AudioManager.STREAM_NOTIFICATION,
            AudioManager.STREAM_SYSTEM
    };

    final Handler ui = new Handler(Looper.getMainLooper());
    final NovaListenBeepRestore restore = new NovaListenBeepRestore(this);
    AudioManager am;
    int[] saved;
    boolean ducked;

    public void duck(Context c) {
        if (c == null) return;
        restoreNow();
        try {
            am = (AudioManager) c.getApplicationContext().getSystemService(Context.AUDIO_SERVICE);
        } catch (Exception e) {
            am = null;
        }
        if (am == null) return;
        int[] vol = new int[STREAMS.length];
        boolean any = false;
        for (int i = 0; i < STREAMS.length; i++) {
            try {
                vol[i] = am.getStreamVolume(STREAMS[i]);
                if (vol[i] > 0) {
                    am.setStreamVolume(STREAMS[i], 0, 0);
                    any = true;
                }
            } catch (Exception e) {
                vol[i] = -1;
            }
        }
        if (!any) {
            am = null;
            return;
        }
        saved = vol;
        ducked = true;
        ui.postDelayed(restore, 1500);
    }

    public void restoreNow() {
        ui.removeCallbacks(restore);
        if (!ducked) return;
        ducked = false;
        AudioManager mgr = am;
        int[] vol = saved;
        am = null;
        saved = null;
        if (mgr == null || vol == null) return;
        for (int i = 0; i < STREAMS.length && i < vol.length; i++) {
            if (vol[i] < 0) continue;
            try { mgr.setStreamVolume(STREAMS[i], vol[i], 0); } catch (Exception ignored) { }
        }
    }
}

/** Named delayed restore so a missed onReady cannot leave the streams muted. */
final class NovaListenBeepRestore implements Runnable {
    final NovaListenBeep beep;
    NovaListenBeepRestore(NovaListenBeep beep) { this.beep = beep; }
    @Override public void run() { if (beep != null) beep.restoreNow(); }
}

package com.codeaque.launcher;

import android.content.Context;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;

/**
 * 0.10.62: hide the recognizer start cue while startListening runs, and
 * always restore afterwards.
 * 0.10.63: do that by switching the ringer to silent, never by setting a
 * stream volume to 0 (that puts many phones into vibrate mode, so the cue
 * vibrates instead of beeping). If silent is not allowed, skip the duck.
 * Named top-level class for d8 8.2.
 */
public final class NovaListenBeep {
    final Handler ui = new Handler(Looper.getMainLooper());
    final NovaListenBeepRestore restore = new NovaListenBeepRestore(this);
    AudioManager am;
    int savedRinger = -1;
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
        int previous;
        try { previous = am.getRingerMode(); }
        catch (Exception e) {
            am = null;
            return;
        }
        try {
            am.setRingerMode(NovaListenBeepGate.modeForDuck());
            int now = am.getRingerMode();
            if (NovaListenBeepGate.mustRevert(previous, now) || !NovaListenBeepGate.duckSucceeded(now)) {
                if (now != previous) {
                    try { am.setRingerMode(previous); } catch (Exception ignored) { }
                }
                am = null;
                savedRinger = -1;
                return;
            }
        } catch (Exception e) {
            try { am.setRingerMode(previous); } catch (Exception ignored) { }
            am = null;
            savedRinger = -1;
            return;
        }
        savedRinger = previous;
        ducked = true;
        ui.postDelayed(restore, 1500);
    }

    public void restoreNow() {
        ui.removeCallbacks(restore);
        if (!ducked) return;
        ducked = false;
        AudioManager mgr = am;
        int previous = savedRinger;
        am = null;
        savedRinger = -1;
        if (mgr == null || previous < 0) return;
        try { mgr.setRingerMode(previous); } catch (Exception ignored) { }
        // Never leave a mute attempt stuck in vibrate if we were not there.
        try {
            int now = mgr.getRingerMode();
            if (NovaListenBeepGate.mustRevert(previous, now)) mgr.setRingerMode(previous);
        } catch (Exception ignored) { }
    }
}

/** Named delayed restore so a missed onReady cannot leave the ringer changed. */
final class NovaListenBeepRestore implements Runnable {
    final NovaListenBeep beep;
    NovaListenBeepRestore(NovaListenBeep beep) { this.beep = beep; }
    @Override public void run() { if (beep != null) beep.restoreNow(); }
}

package com.codeaque.launcher;

/** Named UI post for voice amplitude / start / end (no anonymous Runnables). */
public final class NovaVoiceAmp implements Runnable {
    final NovaVoicePlay.Listener cb;
    final int kind; // 0 start, 1 amp, 2 end, 3 soon-end (pre-roll)
    final float amp;
    final long now;
    public NovaVoiceAmp(NovaVoicePlay.Listener cb, int kind, float amp, long now) {
        this.cb = cb; this.kind = kind; this.amp = amp; this.now = now;
    }
    @Override public void run() {
        if (cb == null) return;
        if (kind == 0) cb.onVoiceStart();
        else if (kind == 1) cb.onVoiceAmp(amp);
        else if (kind == 3) cb.onVoiceSoonEnd();
        else cb.onVoiceEnd();
    }
}

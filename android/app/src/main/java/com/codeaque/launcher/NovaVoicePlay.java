package com.codeaque.launcher;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

/**
 * Play mono float samples with AudioTrack and report amplitude for mouth sync.
 */
public final class NovaVoicePlay {
    public interface Listener {
        void onVoiceStart();
        void onVoiceAmp(float amp);
        void onVoiceEnd();
    }

    static AudioTrack track;
    static boolean stopFlag;
    /** 0.10.60: incremented by stop() so a late play() cannot revive a cancelled line. */
    static int session;

    private NovaVoicePlay() {}

    public static synchronized int newSession() {
        session = session == Integer.MAX_VALUE ? 1 : session + 1;
        stopFlag = false;
        return session;
    }

    public static synchronized int currentSession() { return session; }

    public static void stop() {
        synchronized (NovaVoicePlay.class) {
            session = session == Integer.MAX_VALUE ? 1 : session + 1;
            stopFlag = true;
        }
        AudioTrack t = track;
        track = null;
        if (t != null) {
            try { t.stop(); } catch (Exception ignored) { }
            try { t.release(); } catch (Exception ignored) { }
        }
    }

    /** Blocking play. Call off the UI thread. Pass the session from newSession(). */
    public static void play(float[] samples, int sampleRate, Listener cb) {
        play(samples, sampleRate, cb, currentSession());
    }

    public static void play(float[] samples, int sampleRate, Listener cb, int wantSession) {
        if (wantSession != currentSession()) {
            if (cb != null) post(cb, 2, 0);
            return;
        }
        if (samples == null || samples.length < 16 || sampleRate <= 0) {
            if (cb != null) post(cb, 2, 0);
            return;
        }
        int min = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT);
        if (min < 2048) min = 2048;
        AudioTrack t;
        try {
            t = new AudioTrack(AudioManager.STREAM_MUSIC, sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
                    min, AudioTrack.MODE_STREAM);
        } catch (Exception e) {
            if (cb != null) post(cb, 2, 0);
            return;
        }
        track = t;
        if (cb != null) post(cb, 0, 0);
        try { t.play(); } catch (Exception ignored) { }
        short[] pcm = new short[2048];
        int i = 0;
        while (i < samples.length && !stopFlag) {
            int n = Math.min(pcm.length, samples.length - i);
            float sum = 0;
            for (int k = 0; k < n; k++) {
                float s = samples[i + k];
                if (s > 1f) s = 1f;
                else if (s < -1f) s = -1f;
                pcm[k] = (short) (s * 32767f);
                sum += s * s;
            }
            try { t.write(pcm, 0, n); } catch (Exception e) { break; }
            float amp = (float) Math.sqrt(sum / Math.max(1, n));
            // Map RMS to a visible mouth opening.
            float mouth = amp * 4.5f;
            if (mouth < 0.08f) mouth = 0.08f;
            if (mouth > 1f) mouth = 1f;
            if (cb != null) post(cb, 1, mouth);
            i += n;
        }
        // Let the buffer drain a little so the last syllable isn't cut off.
        if (!stopFlag) {
            try { Thread.sleep(120); } catch (InterruptedException ignored) { }
        }
        try { t.stop(); } catch (Exception ignored) { }
        try { t.release(); } catch (Exception ignored) { }
        if (track == t) track = null;
        if (cb != null) post(cb, 2, 0);
    }

    static void post(Listener cb, int kind, float amp) {
        new Handler(Looper.getMainLooper()).post(new NovaVoiceAmp(cb, kind, amp, SystemClock.uptimeMillis()));
    }
}

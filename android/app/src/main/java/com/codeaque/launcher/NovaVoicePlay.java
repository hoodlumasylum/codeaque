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
        void onVoiceSoonEnd();
    }

    public static final long PREROLL_MS = 500L;

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
        NovaMouth.Ring ring = new NovaMouth.Ring(64);
        int written = 0;
        int i = 0;
        int stuck = 0;
        int lastHead = -1;
        while (i < samples.length && !stopFlag && wantSession == currentSession()) {
            int n = Math.min(pcm.length, samples.length - i);
            for (int k = 0; k < n; k++) {
                float s = samples[i + k];
                if (s > 1f) s = 1f;
                else if (s < -1f) s = -1f;
                pcm[k] = (short) (s * 32767f);
            }
            float chunkRms = NovaMouth.rms(samples, i, n);
            try { t.write(pcm, 0, n); } catch (Exception e) { break; }
            ring.add(written, chunkRms);
            written += n;
            int head = playbackHead(t);
            if (cb != null) post(cb, 1, NovaMouth.mouthFromRms(ring.atHead(head)));
            i += n;
        }
        // A short tail of silence so AudioTrack.stop() does not clip the last syllable.
        int pad = Math.max(sampleRate / 5, 800);
        java.util.Arrays.fill(pcm, (short) 0);
        int padded = 0;
        while (padded < pad && !stopFlag && wantSession == currentSession()) {
            int n = Math.min(pcm.length, pad - padded);
            try { t.write(pcm, 0, n); } catch (Exception e) { break; }
            written += n;
            padded += n;
        }
        // Drain until the playback head has actually reached the last real sample,
        // so the mouth stays in time and the last syllable is not cut off.
        int lastReal = Math.max(0, written - pad);
        while (!stopFlag && wantSession == currentSession()) {
            int head = playbackHead(t);
            if (cb != null) post(cb, 1, NovaMouth.mouthFromRms(ring.atHead(head)));
            if (NovaMouth.drained(head, lastReal, false)) break;
            if (head == lastHead) {
                stuck++;
                if (stuck > 50) break;
            } else {
                stuck = 0;
                lastHead = head;
            }
            try { Thread.sleep(20); } catch (InterruptedException ignored) { }
        }
        try { t.stop(); } catch (Exception ignored) { }
        try { t.release(); } catch (Exception ignored) { }
        if (track == t) track = null;
        if (cb != null) post(cb, 2, 0);
    }

    static boolean maybeSoon(Listener cb, boolean already, int head, int written, int sampleRate) {
        if (already || cb == null || sampleRate <= 0) return already;
        long rem = remainingMs(head, written, sampleRate);
        if (rem <= PREROLL_MS) {
            post(cb, 3, 0);
            return true;
        }
        return false;
    }

    public static long remainingMs(int head, int written, int sampleRate) {
        if (sampleRate <= 0) return 0;
        int left = written - head;
        if (left < 0) left = 0;
        return left * 1000L / sampleRate;
    }

    static int playbackHead(AudioTrack t) {
        if (t == null) return 0;
        try { return t.getPlaybackHeadPosition(); } catch (Exception e) { return 0; }
    }

    static void post(Listener cb, int kind, float amp) {
        new Handler(Looper.getMainLooper()).post(new NovaVoiceAmp(cb, kind, amp, SystemClock.uptimeMillis()));
    }
}

package com.codeaque.launcher;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Looper;

/**
 * 16 kHz mono AudioRecord feeding the built-in sherpa stream.
 * Never overlaps SpeechRecognizer or Heart AudioTrack: the caller stops
 * those first. Named thread body so d8 8.2 does not NPE.
 */
public final class NovaAsrMic {
    public static final int SAMPLE_RATE = 16000;

    public interface Listener {
        void onAsrPartial(String text);
        void onAsrResult(String text);
        void onAsrError(String message);
        void onAsrRms(float level);
        void onAsrListening(boolean on);
    }

    final Handler ui = new Handler(Looper.getMainLooper());
    final Listener listener;
    volatile boolean running;
    AudioRecord record;
    long stream;
    Thread thread;

    public NovaAsrMic(Listener listener) { this.listener = listener; }

    public boolean isRunning() { return running; }

    public String start(android.content.Context ctx) {
        stop();
        if (ctx == null) return "Built-in listening isn't available.";
        if (!NovaAsr.ensureLoaded(ctx)) {
            String err = NovaAsr.lastError();
            return err == null || err.isEmpty() ? "Built-in listening didn't start." : err;
        }
        stream = NovaAsr.createStream();
        if (stream == 0) return "Built-in listening didn't start.";
        int min = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT);
        if (min <= 0) min = SAMPLE_RATE / 5 * 2;
        int buf = Math.max(min, SAMPLE_RATE / 5 * 2);
        try {
            record = new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, buf);
            if (record.getState() != AudioRecord.STATE_INITIALIZED) {
                releaseRecord();
                NovaAsr.destroyStream(stream);
                stream = 0;
                return "Couldn't open the mic.";
            }
            record.startRecording();
        } catch (Throwable t) {
            releaseRecord();
            NovaAsr.destroyStream(stream);
            stream = 0;
            String m = t.getMessage();
            if (m != null && m.toLowerCase().contains("busy")) return NovaConvo.MIC_BUSY_TEXT;
            return "Couldn't open the mic.";
        }
        running = true;
        thread = new Thread(new NovaAsrMicLoop(this), "nova-asr-mic");
        thread.start();
        postListening(true);
        return null;
    }

    public void stop() {
        running = false;
        Thread t = thread;
        thread = null;
        AudioRecord r = record;
        record = null;
        if (r != null) {
            try { r.stop(); } catch (Throwable ignored) { }
            try { r.release(); } catch (Throwable ignored) { }
        }
        if (t != null && t != Thread.currentThread()) {
            try { t.join(400); } catch (InterruptedException ignored) { }
        }
        long s = stream;
        stream = 0;
        if (s != 0) NovaAsr.destroyStream(s);
        postListening(false);
        NovaListenDiag.setLevel(0f);
    }

    void releaseRecord() {
        if (record != null) {
            try { record.release(); } catch (Throwable ignored) { }
            record = null;
        }
    }

    void postListening(boolean on) {
        if (listener != null) ui.post(new NovaAsrMicPost(listener, NovaAsrMicPost.LISTEN, null, 0f, on));
    }

    void postError(String msg) {
        if (listener != null) ui.post(new NovaAsrMicPost(listener, NovaAsrMicPost.ERROR, msg, 0f, false));
    }

    void postResult(String text) {
        if (listener != null) ui.post(new NovaAsrMicPost(listener, NovaAsrMicPost.RESULT, text, 0f, false));
    }

    void postPartial(String text) {
        if (listener != null) ui.post(new NovaAsrMicPost(listener, NovaAsrMicPost.PARTIAL, text, 0f, false));
    }

    void postRms(float level) {
        NovaListenDiag.setLevel(level);
        if (listener != null) ui.post(new NovaAsrMicPost(listener, NovaAsrMicPost.RMS, null, level, true));
    }
}

package com.codeaque.launcher;

import android.content.Context;
import android.content.SharedPreferences;

import android.util.Log;

import java.io.File;

/**
 * 0.10.56: optional on-device Nova voice (Kokoro Heart by default) via sherpa-onnx.
 * The model is downloaded to app files, never bundled in the APK. Present it only
 * as Nova's voice. System TTS is used until the model is present and loaded.
 */
public final class NovaKokoro {
    public static final String PREF_VOICE_ID = "novaVoiceId";
    public static final String PREF_VOICE_LICENCE = "novaVoiceLicenceOk";
    public static final String DIR = "nova/kokoro";
    public static final String MODEL = "model.onnx";
    public static final String VOICES = "voices.bin";
    public static final String TOKENS = "tokens.txt";
    public static final String LEX_US = "lexicon-us-en.txt";
    public static final String LEX_GB = "lexicon-gb-en.txt";

    /** Hugging Face unpacked sherpa Kokoro v1.0 package (individual files, no bz2). */
    public static final String BASE =
            "https://huggingface.co/csukuangfj/kokoro-multi-lang-v1_0/resolve/main/";

    private static boolean libTried, libOk;
    private static long handle;
    private static int sampleRate;
    private static final Object LOCK = new Object();
    public static final String TAG = "NovaVoice";
    static String lastError;

    private NovaKokoro() {}

    public static File dir(Context c) {
        return new File(c.getFilesDir(), DIR);
    }

    public static boolean filesReady(Context c) {
        File d = dir(c);
        File model = new File(d, MODEL);
        File voices = new File(d, VOICES);
        File tokens = new File(d, TOKENS);
        File lex = new File(d, LEX_US);
        return model.isFile() && model.length() > 100_000_000L
                && voices.isFile() && voices.length() > 1_000_000L
                && tokens.isFile() && tokens.length() > 10L
                && lex.isFile() && lex.length() > 1000L;
    }

    public static String selectedId(SharedPreferences p) {
        String id = p == null ? null : p.getString(PREF_VOICE_ID, null);
        if (id == null || id.length() == 0) id = NovaVoices.DEFAULT_VOICE_ID;
        return id;
    }

    public static int speakerId(SharedPreferences p) {
        String id = selectedId(p);
        if (NovaVoices.ID_KOKORO_BELLA.equals(id)) return 2;
        if (NovaVoices.ID_KOKORO_EMMA.equals(id)) return 21;
        return 3; // Heart, Nova's default voice
    }

    public static boolean usesOnDevice(Context c, SharedPreferences p) {
        if (c == null) return false;
        return NovaVoiceGate.useOnDevice(filesReady(c), isLoaded(), selectedId(p));
    }

    public static String lastError() { return lastError; }

    public static synchronized boolean loadLibrary() {
        if (libTried) return libOk;
        libTried = true;
        try {
            System.loadLibrary("onnxruntime");
            System.loadLibrary("sherpa-onnx-c-api");
            System.loadLibrary("nova-tts");
            libOk = true;
            lastError = null;
            Log.i(TAG, "native TTS libraries loaded");
        } catch (Throwable e) {
            libOk = false;
            lastError = e.getClass().getSimpleName() + ": " + e.getMessage();
            Log.w(TAG, "native TTS libraries failed to load: " + lastError, e);
        }
        return libOk;
    }

    public static boolean isLoaded() {
        synchronized (LOCK) { return handle != 0; }
    }

    /** Load the Kokoro model from disk. Safe to call off the UI thread. */
    public static boolean load(Context c) {
        if (c == null) return false;
        if (!loadLibrary()) return false;
        if (!filesReady(c)) return false;
        synchronized (LOCK) {
            if (handle != 0) return true;
            File d = dir(c);
            String lex = new File(d, LEX_US).getAbsolutePath() + ","
                    + new File(d, LEX_GB).getAbsolutePath();
            if (!new File(d, LEX_GB).isFile()) lex = new File(d, LEX_US).getAbsolutePath();
            try {
                long h = nativeLoad(
                        new File(d, MODEL).getAbsolutePath(),
                        new File(d, VOICES).getAbsolutePath(),
                        new File(d, TOKENS).getAbsolutePath(),
                        lex);
                if (h == 0) {
                    lastError = "The voice engine did not start.";
                    Log.w(TAG, lastError);
                    return false;
                }
                handle = h;
                sampleRate = nativeSampleRate(h);
                if (sampleRate <= 0) sampleRate = 24000;
                lastError = null;
                Log.i(TAG, "Kokoro voice loaded, sampleRate=" + sampleRate);
                return true;
            } catch (Throwable e) {
                lastError = e.getClass().getSimpleName() + ": " + e.getMessage();
                Log.w(TAG, "Kokoro load failed: " + lastError, e);
                handle = 0;
                return false;
            }
        }
    }

    public static void unload() {
        synchronized (LOCK) {
            if (handle != 0) {
                try { nativeUnload(handle); } catch (Throwable ignored) { }
                handle = 0;
            }
        }
    }

    public static int sampleRate() {
        synchronized (LOCK) { return sampleRate > 0 ? sampleRate : 24000; }
    }

    /**
     * Synthesize speech. Returns mono float samples in [-1, 1], or null on failure.
     * Must not run on the UI thread for long lines.
     */
    public static float[] synthesize(String text, int sid) {
        if (text == null || text.trim().isEmpty()) return null;
        synchronized (LOCK) {
            if (handle == 0) return null;
            try { return nativeSynthesize(handle, text, sid, 1.0f); }
            catch (Throwable t) { return null; }
        }
    }

    public static void deleteFiles(Context c) {
        unload();
        File d = dir(c);
        File[] all = d.listFiles();
        if (all != null) for (File f : all) { try { f.delete(); } catch (Exception ignored) { } }
        try { d.delete(); } catch (Exception ignored) { }
    }

    static native long nativeLoad(String model, String voices, String tokens, String lexicon);
    static native void nativeUnload(long handle);
    static native int nativeSampleRate(long handle);
    static native int nativeNumSpeakers(long handle);
    static native float[] nativeSynthesize(long handle, String text, int sid, float speed);
}

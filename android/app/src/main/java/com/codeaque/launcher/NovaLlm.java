package com.codeaque.launcher;

import android.app.ActivityManager;
import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;

/**
 * 0.10.56: optional on-device Gemma 3 1B (Q4_K_M) via llama.cpp. Downloaded, not bundled.
 * She only presents as Nova. Falls back to the local set + Simple Wikipedia if the
 * model is missing, the phone is too weak, or generation fails.
 */
public final class NovaLlm {
    public static final String PREF_LICENCE = "novaGemmaLicenceOk";
    public static final String DIR = "nova/gemma";
    public static final String FILE = "google_gemma-3-1b-it-Q4_K_M.gguf";
    public static final String URL =
            "https://huggingface.co/bartowski/google_gemma-3-1b-it-GGUF/resolve/main/google_gemma-3-1b-it-Q4_K_M.gguf";
    /** Q4_K_M is about 806 MB; require close to that so a partial file is not "ready". */
    public static final long MIN_BYTES = 700_000_000L;

    private static boolean libTried, libOk;
    private static volatile long handle;
    private static final Object LOCK = new Object();
    static boolean loading;

    private NovaLlm() {}

    public static File file(Context c) {
        return new File(new File(c.getFilesDir(), DIR), FILE);
    }

    public static boolean filesReady(Context c) {
        File f = file(c);
        return f.isFile() && f.length() >= MIN_BYTES;
    }

    /** Rough floor: loading ~0.8 GB plus context needs a few GB of RAM. */
    public static boolean phoneStrongEnough(Context c) {
        try {
            ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
            if (am == null) return true;
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(mi);
            // totalMem is API 16+. Under ~2.8 GB total, skip the model.
            if (mi.totalMem > 0 && mi.totalMem < 2_800_000_000L) return false;
            if (mi.availMem > 0 && mi.availMem < 1_200_000_000L) return false;
            return true;
        } catch (Throwable t) {
            return true;
        }
    }

    public static synchronized boolean loadLibrary() {
        if (libTried) return libOk;
        libTried = true;
        try {
            System.loadLibrary("ggml-base");
            System.loadLibrary("ggml-cpu");
            System.loadLibrary("ggml");
            System.loadLibrary("llama");
            System.loadLibrary("nova-llm");
            libOk = true;
        } catch (Throwable t) {
            libOk = false;
        }
        return libOk;
    }

    public static boolean isLoaded() {
        // Must not wait on load(): a UI read during startup must stay instant.
        return handle != 0;
    }

    public static boolean load(Context c) {
        if (c == null || !filesReady(c) || !phoneStrongEnough(c)) return false;
        if (handle != 0) return true;
        if (!loadLibrary()) return false;
        synchronized (LOCK) {
            if (handle != 0) return true;
            if (loading) return false;
            loading = true;
        }
        long h = 0;
        try {
            int threads = Math.max(2, Math.min(4, Runtime.getRuntime().availableProcessors()));
            h = nativeLoad(file(c).getAbsolutePath(), 2048, threads);
        } catch (Throwable t) {
            h = 0;
        }
        synchronized (LOCK) {
            loading = false;
            if (h == 0) return false;
            handle = h;
            return true;
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

    /** Returns model text, or null on failure. Call off the UI thread. */
    public static String complete(String prompt) {
        if (prompt == null || prompt.isEmpty()) return null;
        synchronized (LOCK) {
            if (handle == 0) return null;
            try { return nativeComplete(handle, prompt, NovaReplyQuick.MAX_TOKENS); }
            catch (Throwable t) { return null; }
        }
    }

    public static boolean smarterUsesModel(Context c, SharedPreferences p) {
        if (c == null || p == null) return false;
        if (!p.getBoolean(NovaView.PREF_SMARTER, true)) return false;
        return filesReady(c);
    }

    public static void deleteFiles(Context c) {
        unload();
        File f = file(c);
        try { f.delete(); } catch (Exception ignored) { }
        try { f.getParentFile().delete(); } catch (Exception ignored) { }
    }

    static native long nativeLoad(String path, int nCtx, int nThreads);
    static native void nativeUnload(long handle);
    static native String nativeComplete(long handle, String prompt, int maxTokens);
}

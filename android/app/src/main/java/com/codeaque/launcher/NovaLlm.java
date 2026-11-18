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
    /** -1 when no download is in progress; 0–100 while one is. */
    static volatile int downloadPercent = -1;
    static String lastError;

    private NovaLlm() {}

    public static File file(Context c) {
        return new File(new File(c.getFilesDir(), DIR), FILE);
    }

    public static boolean filesReady(Context c) {
        File f = file(c);
        return f.isFile() && f.length() >= MIN_BYTES;
    }

    /** Rough floor for offering the download: needs a few GB of RAM. */
    public static boolean phoneStrongEnough(Context c) {
        return totalMemOk(c, 2_800_000_000L);
    }

    static boolean totalMemOk(Context c, long minTotal) {
        try {
            ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
            if (am == null) return true;
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(mi);
            if (mi.totalMem > 0 && mi.totalMem < minTotal) return false;
            return true;
        } catch (Throwable t) {
            return true;
        }
    }

    public static boolean isLoading() { return loading && handle == 0; }

    public static int downloadPercent() { return downloadPercent; }

    public static void setDownloadPercent(int percent) {
        if (percent < 0) downloadPercent = -1;
        else downloadPercent = Math.max(0, Math.min(100, percent));
    }

    public static String lastError() { return lastError; }

    /**
     * Status for Chat: real download percent when known, otherwise a plain
     * "getting ready" line. Never invents a load percent the JNI does not provide.
     */
    public static String readyStatus() {
        return NovaConvo.brainReadyText(downloadPercent);
    }

    public static boolean needsReadyStatus(Context c) {
        if (c == null) return false;
        if (downloadPercent >= 0 && downloadPercent < 100) return true;
        return filesReady(c) && handle == 0;
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
        // If the ~800 MB file is already on the phone, try to load it even when
        // availMem looks tight (Heart + the launcher can leave under 1.2 GB free
        // on a 4–6 GB phone). Only skip on truly small devices.
        if (c == null || !filesReady(c)) return false;
        if (handle != 0) return true;
        if (!totalMemOk(c, 2_000_000_000L)) return false;
        if (!loadLibrary()) return false;
        synchronized (LOCK) {
            if (handle != 0) return true;
            if (loading) {
                long deadline = System.currentTimeMillis() + 90_000L;
                while (loading && handle == 0 && System.currentTimeMillis() < deadline) {
                    try { LOCK.wait(400); } catch (InterruptedException e) { break; }
                }
                return handle != 0;
            }
            loading = true;
        }
        long h = 0;
        String err = null;
        try {
            int threads = Math.max(2, Math.min(4, Runtime.getRuntime().availableProcessors()));
            h = nativeLoad(file(c).getAbsolutePath(), 2048, threads);
            if (h == 0) err = "The on-device brain did not start.";
        } catch (Throwable t) {
            h = 0;
            err = t.getClass().getSimpleName() + ": " + t.getMessage();
        }
        synchronized (LOCK) {
            loading = false;
            if (h != 0) {
                handle = h;
                lastError = null;
            } else if (err != null) {
                lastError = err;
            }
            LOCK.notifyAll();
            return h != 0;
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

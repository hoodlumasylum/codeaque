package com.codeaque.launcher;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;

/**
 * Built-in streaming English recognizer via sherpa-onnx.
 * Native model init can abort the process (SIGSEGV or OOM) on a 4–6 GB phone,
 * so it must never run at startup or on the screen thread. Load only lazily,
 * off the UI, with a loading marker so a mid-load crash is skipped next start.
 */
public final class NovaAsr {
    public static final String TAG = "NovaAsr";

    private static boolean libTried, libOk;
    private static volatile long handle;
    private static volatile boolean loading;
    private static volatile boolean skipThisSession;
    static String lastError;
    private static final Object LOCK = new Object();
    private static final Object LOAD_GATE = new Object();
    private static final ArrayList<NovaAsrLoadCb> WAITERS = new ArrayList<NovaAsrLoadCb>();

    private NovaAsr() {}

    public static File dir(Context c) {
        if (c == null) return null;
        File u = NovaAsrFiles.usableDir(c.getFilesDir());
        if (u != null) return u;
        return new File(c.getFilesDir(), NovaAsrFiles.DIR);
    }

    /** Pack is on disk, names match, and it has not been marked unsafe. */
    public static boolean filesReady(Context c) {
        return usable(c);
    }

    public static boolean usable(Context c) {
        return c != null && NovaAsrFiles.usableDir(c.getFilesDir()) != null;
    }

    public static boolean packMarkedBad(Context c) {
        return c != null && NovaAsrFiles.packMarkedBad(c.getFilesDir());
    }

    public static String lastError() { return lastError; }

    public static boolean isLoading() { return loading; }

    public static boolean skippedThisSession() { return skipThisSession; }

    /**
     * If a previous run died while nativeLoad was in progress, mark that pack
     * bad and skip it. Never touches JNI. Safe on the UI thread at startup.
     */
    public static void recoverIfNeeded(Context c) {
        recoverAfterCrash(c);
    }

    public static void recoverAfterCrash(Context c) {
        if (c == null) return;
        recoverDir(new File(c.getFilesDir(), NovaAsrFiles.DIR));
        recoverDir(new File(c.getFilesDir(), NovaAsrFiles.DIR_SMALL));
    }

    static void recoverDir(File dir) {
        if (dir == null) return;
        if (NovaAsrGuard.diedMidLoad(dir)) {
            Log.w(TAG, "previous built-in listening load did not finish; skipping pack");
            markBad(dir, "built-in listening did not start safely last time");
        }
    }

    public static void markBad(File dir, String reason) {
        if (dir == null) return;
        lastError = reason == null ? "built-in listening is not safe to load" : reason;
        NovaAsrGuard.writeMark(NovaAsrGuard.badMark(dir));
        NovaAsrGuard.clearMark(NovaAsrGuard.loadingMark(dir));
        skipThisSession = true;
        try { new Thread(new NovaAsrPurge(dir), "nova-asr-purge").start(); }
        catch (Throwable ignored) { }
    }

    /** After a successful re-download, allow a fresh load attempt. */
    public static void clearBad(Context c, boolean small) {
        if (c == null) return;
        File dir = new File(c.getFilesDir(), small ? NovaAsrFiles.DIR_SMALL : NovaAsrFiles.DIR);
        NovaAsrGuard.clearMark(NovaAsrGuard.badMark(dir));
        NovaAsrGuard.clearMark(NovaAsrGuard.loadingMark(dir));
        skipThisSession = false;
        lastError = null;
    }

    public static synchronized boolean loadLibrary() {
        if (libTried) return libOk;
        libTried = true;
        try {
            System.loadLibrary("onnxruntime");
            System.loadLibrary("sherpa-onnx-c-api");
            System.loadLibrary("nova-asr");
            libOk = true;
            lastError = null;
            Log.i(TAG, "native ASR libraries loaded");
        } catch (Throwable t) {
            libOk = false;
            lastError = t.getMessage() == null ? "native library failed" : t.getMessage();
            Log.e(TAG, "loadLibrary: " + lastError);
        }
        return libOk;
    }

    /** Non-blocking. Must never wait on nativeLoad. */
    public static boolean isLoaded() {
        return handle != 0;
    }

    static boolean isMainThread() {
        try {
            Looper main = Looper.getMainLooper();
            return main != null && Looper.myLooper() == main;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Load the pack. Refuses the screen thread. Writes a loading marker before
     * native init and clears it after, so a crash mid-load is visible next start.
     */
    public static boolean ensureLoaded(Context c) {
        if (c == null) { lastError = "no context"; return false; }
        if (isMainThread()) {
            lastError = "built-in listening must load off the screen thread";
            return false;
        }
        if (handle != 0) return true;
        recoverIfNeeded(c);
        if (skipThisSession) {
            if (lastError == null) lastError = "built-in listening is not safe to load";
            return false;
        }
        File d = NovaAsrFiles.usableDir(c.getFilesDir());
        if (d == null) {
            lastError = packMarkedBad(c)
                    ? "built-in listening is not safe to load"
                    : "built-in listening files are missing";
            return false;
        }
        String encName = NovaAsrFiles.usingSmall(d) ? NovaAsrFiles.ENCODER_SMALL : NovaAsrFiles.ENCODER;
        String decName = NovaAsrFiles.usingSmall(d) ? NovaAsrFiles.DECODER_SMALL : NovaAsrFiles.DECODER;
        String joinName = NovaAsrFiles.usingSmall(d) ? NovaAsrFiles.JOINER_SMALL : NovaAsrFiles.JOINER;
        if (!NovaAsrGuard.canNativeLoad(true, NovaAsrGuard.diedMidLoad(d),
                NovaAsrGuard.isMarkedBad(d),
                NovaAsrGuard.namesMatch(encName, decName, joinName, NovaAsrFiles.TOKENS))) {
            lastError = "built-in listening files do not match the listener";
            return false;
        }
        if (!loadLibrary()) return false;
        synchronized (LOCK) {
            if (handle != 0) return true;
        }
        File mark = NovaAsrGuard.loadingMark(d);
        NovaAsrGuard.writeMark(mark);
        long h = 0;
        try {
            h = nativeLoad(
                    new File(d, encName).getAbsolutePath(),
                    new File(d, decName).getAbsolutePath(),
                    new File(d, joinName).getAbsolutePath(),
                    new File(d, NovaAsrFiles.TOKENS).getAbsolutePath());
        } catch (Throwable t) {
            h = 0;
            lastError = t.getMessage() == null ? "could not load the built-in listener" : t.getMessage();
        }
        if (h == 0) {
            if (lastError == null) lastError = "could not load the built-in listener";
            NovaAsrGuard.clearMark(mark);
            markBad(d, lastError);
            return false;
        }
        NovaAsrGuard.clearMark(mark);
        synchronized (LOCK) { handle = h; }
        lastError = null;
        return true;
    }

    /** Fire-and-forget background load. Safe on the UI thread. */
    public static void requestLoad(Context c) {
        loadAsync(c, null);
    }

    public static void requestLoad(Context c, NovaAsrLoadCb cb) {
        loadAsync(c, cb);
    }

    public static void loadAsync(Context c, NovaAsrLoadCb cb) {
        if (handle != 0) { postCb(cb, true, null); return; }
        if (c == null) { postCb(cb, false, "no context"); return; }
        final Context app = c.getApplicationContext();
        recoverIfNeeded(app);
        if (skipThisSession || !usable(app)) {
            String err = lastError;
            if (err == null || err.isEmpty()) {
                err = packMarkedBad(app)
                        ? "built-in listening is not safe to load"
                        : "built-in listening files are missing";
            }
            postCb(cb, false, err);
            return;
        }
        synchronized (LOAD_GATE) {
            if (handle != 0) { postCb(cb, true, null); return; }
            if (cb != null) WAITERS.add(cb);
            if (loading) return;
            loading = true;
        }
        try {
            Thread t = new Thread(new NovaAsrLoadJob(app), "nova-asr-load");
            t.start();
        } catch (Throwable t) {
            loading = false;
            postCb(cb, false, "could not start built-in listening");
            return;
        }
        try {
            new Handler(Looper.getMainLooper()).postDelayed(
                    new NovaAsrLoadTimeout(), NovaAsrGuard.LOAD_TIMEOUT_MS);
        } catch (Throwable ignored) { }
    }

    static void onLoadJobFinished(boolean ok, String err) {
        ArrayList<NovaAsrLoadCb> list;
        synchronized (LOAD_GATE) {
            loading = false;
            list = new ArrayList<NovaAsrLoadCb>(WAITERS);
            WAITERS.clear();
        }
        for (int i = 0; i < list.size(); i++) postCb(list.get(i), ok, err);
    }

    static void onLoadTimeout() {
        if (handle != 0 || !loading) return;
        lastError = "built-in listening took too long to load";
        skipThisSession = true;
        ArrayList<NovaAsrLoadCb> list;
        synchronized (LOAD_GATE) {
            // Leave loading true so a late success can still set the handle,
            // but do not wait again this session.
            list = new ArrayList<NovaAsrLoadCb>(WAITERS);
            WAITERS.clear();
        }
        for (int i = 0; i < list.size(); i++) postCb(list.get(i), false, lastError);
    }

    static void postCb(NovaAsrLoadCb cb, boolean ok, String err) {
        if (cb == null) return;
        try {
            new Handler(Looper.getMainLooper()).post(new NovaAsrLoadPost(cb, ok, err));
        } catch (Throwable t) {
            try { cb.onAsrLoad(ok, err); } catch (Throwable ignored) { }
        }
    }

    public static long createStream() {
        synchronized (LOCK) {
            if (handle == 0) return 0;
            try { return nativeCreateStream(handle); }
            catch (Throwable t) { return 0; }
        }
    }

    public static void destroyStream(long stream) {
        if (stream == 0) return;
        try { nativeDestroyStream(stream); } catch (Throwable ignored) { }
    }

    public static void accept(long stream, float[] samples, int n, int rate) {
        long h = handle;
        if (h == 0 || stream == 0 || samples == null) return;
        try { nativeAccept(h, stream, samples, n, rate); } catch (Throwable ignored) { }
    }

    public static String result(long stream) {
        long h = handle;
        if (h == 0 || stream == 0) return "";
        try {
            String s = nativeResult(h, stream);
            return s == null ? "" : s;
        } catch (Throwable t) { return ""; }
    }

    public static boolean isEndpoint(long stream) {
        long h = handle;
        if (h == 0 || stream == 0) return false;
        try { return nativeIsEndpoint(h, stream); } catch (Throwable t) { return false; }
    }

    public static void inputFinished(long stream) {
        long h = handle;
        if (h == 0 || stream == 0) return;
        try { nativeInputFinished(h, stream); } catch (Throwable ignored) { }
    }

    public static void reset(long stream) {
        long h = handle;
        if (h == 0 || stream == 0) return;
        try { nativeReset(h, stream); } catch (Throwable ignored) { }
    }

    public static void unload() {
        synchronized (LOCK) {
            long h = handle;
            handle = 0;
            if (h != 0) {
                try { nativeDestroy(h); } catch (Throwable ignored) { }
            }
        }
    }

    private static native long nativeLoad(String encoder, String decoder, String joiner, String tokens);
    private static native void nativeDestroy(long handle);
    private static native long nativeCreateStream(long handle);
    private static native void nativeDestroyStream(long stream);
    private static native void nativeAccept(long handle, long stream, float[] samples, int n, int rate);
    private static native String nativeResult(long handle, long stream);
    private static native boolean nativeIsEndpoint(long handle, long stream);
    private static native void nativeInputFinished(long handle, long stream);
    private static native void nativeReset(long handle, long stream);
}

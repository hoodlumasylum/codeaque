package com.codeaque.launcher;

import android.content.Context;
import android.util.Log;

import java.io.File;

/**
 * Built-in streaming English recognizer via sherpa-onnx. Reuses the packed
 * onnxruntime and sherpa-onnx-c-api libraries already used for Heart.
 * The model is loaded from app files after an on-demand download.
 */
public final class NovaAsr {
    public static final String TAG = "NovaAsr";

    private static boolean libTried, libOk;
    private static long handle;
    static String lastError;
    private static final Object LOCK = new Object();

    private NovaAsr() {}

    public static File dir(Context c) {
        return new File(c.getFilesDir(), NovaAsrFiles.DIR);
    }

    public static boolean filesReady(Context c) {
        if (c == null) return false;
        return NovaAsrFiles.filesReadyAt(dir(c));
    }


    public static String lastError() { return lastError; }

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

    public static boolean isLoaded() {
        synchronized (LOCK) { return handle != 0; }
    }

    public static boolean ensureLoaded(Context c) {
        if (c == null) { lastError = "no context"; return false; }
        if (!filesReady(c)) { lastError = "built-in listening files are missing"; return false; }
        if (!loadLibrary()) return false;
        synchronized (LOCK) {
            if (handle != 0) return true;
            File d = dir(c);
            try {
                handle = nativeLoad(
                        new File(d, NovaAsrFiles.ENCODER).getAbsolutePath(),
                        new File(d, NovaAsrFiles.DECODER).getAbsolutePath(),
                        new File(d, NovaAsrFiles.JOINER).getAbsolutePath(),
                        new File(d, NovaAsrFiles.TOKENS).getAbsolutePath());
            } catch (Throwable t) {
                handle = 0;
                lastError = t.getMessage() == null ? "could not load the built-in listener" : t.getMessage();
                return false;
            }
            if (handle == 0) {
                lastError = "could not load the built-in listener";
                return false;
            }
            lastError = null;
            return true;
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
        long h;
        synchronized (LOCK) { h = handle; }
        if (h == 0 || stream == 0 || samples == null) return;
        try { nativeAccept(h, stream, samples, n, rate); } catch (Throwable ignored) { }
    }

    public static String result(long stream) {
        long h;
        synchronized (LOCK) { h = handle; }
        if (h == 0 || stream == 0) return "";
        try {
            String s = nativeResult(h, stream);
            return s == null ? "" : s;
        } catch (Throwable t) { return ""; }
    }

    public static boolean isEndpoint(long stream) {
        long h;
        synchronized (LOCK) { h = handle; }
        if (h == 0 || stream == 0) return false;
        try { return nativeIsEndpoint(h, stream); } catch (Throwable t) { return false; }
    }

    public static void inputFinished(long stream) {
        long h;
        synchronized (LOCK) { h = handle; }
        if (h == 0 || stream == 0) return;
        try { nativeInputFinished(h, stream); } catch (Throwable ignored) { }
    }

    public static void reset(long stream) {
        long h;
        synchronized (LOCK) { h = handle; }
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

package com.codeaque.launcher;

import java.io.File;
import java.io.FileOutputStream;

/**
 * Crash-safe rules for the built-in listening pack. Native init of the 75 MB
 * model can abort the process; a marker file lets the next start skip it.
 * Plain Java for CatalogTest. No JNI here.
 */
public final class NovaAsrGuard {
    private NovaAsrGuard() {}

    public static final String LOADING_MARK = ".loading";
    public static final String BAD_MARK = ".bad";
    public static final long LOAD_TIMEOUT_MS = 20_000L;
    public static final int THREADS = 2;
    public static final int THREADS_LOW_RAM = 1;

    public static File loadingMark(File dir) {
        return dir == null ? null : new File(dir, LOADING_MARK);
    }

    public static File badMark(File dir) {
        return dir == null ? null : new File(dir, BAD_MARK);
    }

    public static boolean diedMidLoad(File dir) {
        File m = loadingMark(dir);
        return m != null && m.isFile();
    }

    public static boolean isMarkedBad(File dir) {
        File m = badMark(dir);
        return m != null && m.isFile();
    }

    /**
     * Only start native init when the files look like an int8 zipformer pack
     * and the previous load did not kill the app.
     */
    public static boolean canNativeLoad(boolean filesReady, boolean diedMidLoad, boolean markedBad,
            boolean namesMatch) {
        return filesReady && !diedMidLoad && !markedBad && namesMatch;
    }

    public static boolean namesMatch(String encoder, String decoder, String joiner, String tokens) {
        return isInt8Onnx(encoder, "encoder")
                && isInt8Onnx(decoder, "decoder")
                && isInt8Onnx(joiner, "joiner")
                && tokens != null && tokens.endsWith("tokens.txt");
    }

    static boolean isInt8Onnx(String name, String part) {
        if (name == null) return false;
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        String n = slash >= 0 ? name.substring(slash + 1) : name;
        return n.endsWith(".int8.onnx") && n.contains(part);
    }

    public static int numThreads(int ramMb) {
        if (ramMb > 0 && ramMb < 5000) return THREADS_LOW_RAM;
        return THREADS;
    }

    public static boolean writeMark(File mark) {
        if (mark == null) return false;
        File parent = mark.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) return false;
        try {
            FileOutputStream out = new FileOutputStream(mark);
            try { out.write('1'); } finally { out.close(); }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static void clearMark(File mark) {
        if (mark != null) {
            try { mark.delete(); } catch (Exception ignored) { }
        }
    }
}

package com.codeaque.launcher;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.io.File;

/**
 * Downloads an English streaming ASR pack file by file, like Heart.
 * Writes go to a .part file and are renamed into place (streamed to disk).
 * Progress is posted to the UI. The model is never bundled in the APK.
 */
public final class NovaAsrPack implements Runnable, NovaDl.Cb {
    final Context app;
    final NovaDlJob.NovaDlUi ui;
    final Handler uiHandler = new Handler(Looper.getMainLooper());
    final boolean small;
    int index;
    boolean failed;

    public NovaAsrPack(Context c, NovaDlJob.NovaDlUi ui) {
        this(c, ui, false);
    }

    public NovaAsrPack(Context c, NovaDlJob.NovaDlUi ui, boolean small) {
        this.app = c.getApplicationContext();
        this.ui = ui;
        this.small = small;
    }

    File packDir() {
        return new File(app.getFilesDir(), small ? NovaAsrFiles.DIR_SMALL : NovaAsrFiles.DIR);
    }

    String[] names() { return small ? NovaAsrFiles.NAMES_SMALL : NovaAsrFiles.NAMES; }

    String base() { return small ? NovaAsrFiles.BASE_SMALL : NovaAsrFiles.BASE; }

    @Override public void run() {
        // Re-download of a pack that crashed: drop leftover files first.
        File dir = packDir();
        if (NovaAsrGuard.isMarkedBad(dir) || NovaAsrGuard.diedMidLoad(dir)) {
            String[] names = names();
            for (int i = 0; i < names.length; i++) {
                try { new File(dir, names[i]).delete(); } catch (Throwable ignored) { }
            }
            NovaAsrGuard.clearMark(NovaAsrGuard.loadingMark(dir));
        }
        next();
    }

    void next() {
        if (failed) return;
        String[] names = names();
        if (index >= names.length) {
            File dir = packDir();
            boolean ok = small ? NovaAsrFiles.filesReadySmallAt(dir) : NovaAsrFiles.filesReadyAt(dir);
            if (ok) NovaAsr.clearBad(app, small);
            postDone(ok, ok ? null : "The listening files are incomplete. Try again.");
            return;
        }
        String name = names[index];
        File dest = new File(packDir(), name);
        if (NovaAsrFiles.alreadyDownloaded(dest, name)) {
            index++;
            next();
            return;
        }
        String url = base() + name;
        if (!NovaDl.download(url, dest, this)) {
            // onDone already posted by download()
        }
    }

    @Override public void onProgress(int percent, long got, long total) {
        // Encoder is most of the pack.
        int base = index == 0 ? 0 : (index == 1 ? 90 : 95);
        int span = index == 0 ? 90 : 4;
        int overall = Math.min(99, base + percent * span / 100);
        if (ui != null) uiHandler.post(new NovaDlUiPost(ui, true, true, overall, got, total, null));
    }

    @Override public void onDone(boolean ok, String error) {
        if (!ok) {
            failed = true;
            postDone(false, error);
            return;
        }
        index++;
        next();
    }

    void postDone(boolean ok, String error) {
        if (ui != null) uiHandler.post(new NovaDlUiPost(ui, false, ok, ok ? 100 : 0, 0, 0, error));
    }
}

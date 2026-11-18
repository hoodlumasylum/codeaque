package com.codeaque.launcher;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.io.File;

/**
 * Downloads the English streaming ASR pack file by file, like Heart.
 * Progress is posted to the UI. The model is never bundled in the APK.
 */
public final class NovaAsrPack implements Runnable, NovaDl.Cb {
    final Context app;
    final NovaDlJob.NovaDlUi ui;
    final Handler uiHandler = new Handler(Looper.getMainLooper());
    int index;
    boolean failed;

    public NovaAsrPack(Context c, NovaDlJob.NovaDlUi ui) {
        this.app = c.getApplicationContext();
        this.ui = ui;
    }

    @Override public void run() { next(); }

    void next() {
        if (failed) return;
        if (index >= NovaAsrFiles.NAMES.length) {
            boolean ok = NovaAsr.filesReady(app);
            postDone(ok, ok ? null : "The listening files are incomplete. Try again.");
            return;
        }
        String name = NovaAsrFiles.NAMES[index];
        File dest = new File(NovaAsr.dir(app), name);
        if (dest.isFile() && dest.length() > 8) {
            index++;
            next();
            return;
        }
        String url = NovaAsrFiles.BASE + name;
        if (!NovaDl.download(url, dest, this)) {
            // onDone already posted by download()
        }
    }

    @Override public void onProgress(int percent, long got, long total) {
        // Encoder is ~43 MB of the ~45 MB pack.
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

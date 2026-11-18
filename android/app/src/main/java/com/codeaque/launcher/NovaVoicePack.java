package com.codeaque.launcher;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.io.File;

/**
 * Downloads the Kokoro package (Heart / Bella / Emma share one model) file by file
 * on a worker thread. Progress and completion are posted to the UI.
 */
public final class NovaVoicePack implements Runnable, NovaDl.Cb {
    public static final String[] NAMES = {
            NovaKokoro.MODEL, NovaKokoro.VOICES, NovaKokoro.TOKENS,
            NovaKokoro.LEX_US, NovaKokoro.LEX_GB, "LICENSE"
    };

    final Context app;
    final NovaDlJob.NovaDlUi ui;
    final Handler uiHandler = new Handler(Looper.getMainLooper());
    int index;
    boolean failed;

    public NovaVoicePack(Context c, NovaDlJob.NovaDlUi ui) {
        this.app = c.getApplicationContext();
        this.ui = ui;
    }

    @Override public void run() { next(); }

    void next() {
        if (failed) return;
        if (index >= NAMES.length) {
            boolean ok = NovaKokoro.filesReady(app);
            postDone(ok, ok ? null : "The voice files are incomplete. Try again.");
            return;
        }
        String name = NAMES[index];
        File dest = new File(NovaKokoro.dir(app), name);
        if (dest.isFile() && dest.length() > 8) {
            index++;
            next();
            return;
        }
        String url = NovaKokoro.BASE + name;
        if (!NovaDl.download(url, dest, this)) {
            // onDone already posted by download()
        }
    }

    @Override public void onProgress(int percent, long got, long total) {
        int base = index == 0 ? 0 : (index == 1 ? 85 : 95);
        int span = index == 0 ? 85 : 5;
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

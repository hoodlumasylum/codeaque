package com.codeaque.launcher;

import android.os.Handler;
import android.os.Looper;

import java.io.File;

/** Named download worker (d8 8.2 NPEs on anonymous Runnables). */
public final class NovaDlJob implements Runnable {
    final String url;
    final File dest;
    final Handler ui = new Handler(Looper.getMainLooper());
    final NovaDlUi cb;

    public interface NovaDlUi {
        void onDlProgress(int percent, long got, long total);
        void onDlDone(boolean ok, String error);
    }

    public NovaDlJob(String url, File dest, NovaDlUi cb) {
        this.url = url;
        this.dest = dest;
        this.cb = cb;
    }

    @Override public void run() {
        NovaDl.download(url, dest, new NovaDlProgress(this));
    }

    void progress(int pct, long got, long total) {
        if (cb != null) ui.post(new NovaDlPost(this, false, true, pct, got, total, null));
    }

    void done(boolean ok, String error) {
        if (cb != null) ui.post(new NovaDlPost(this, ok, false, ok ? 100 : 0, 0, 0, error));
    }
}

package com.codeaque.launcher;

/** Named UI post for download progress / completion. */
public final class NovaDlUiPost implements Runnable {
    final NovaDlJob.NovaDlUi cb;
    final boolean isProgress, ok;
    final int pct;
    final long got, total;
    final String error;
    public NovaDlUiPost(NovaDlJob.NovaDlUi cb, boolean isProgress, boolean ok, int pct, long got, long total, String error) {
        this.cb = cb; this.isProgress = isProgress; this.ok = ok;
        this.pct = pct; this.got = got; this.total = total; this.error = error;
    }
    @Override public void run() {
        if (cb == null) return;
        if (isProgress) cb.onDlProgress(pct, got, total);
        else cb.onDlDone(ok, error);
    }
}

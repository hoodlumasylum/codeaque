package com.codeaque.launcher;

public final class NovaDlPost implements Runnable {
    final NovaDlJob job;
    final boolean ok, isProgress;
    final int pct;
    final long got, total;
    final String error;
    public NovaDlPost(NovaDlJob job, boolean ok, boolean isProgress, int pct, long got, long total, String error) {
        this.job = job; this.ok = ok; this.isProgress = isProgress;
        this.pct = pct; this.got = got; this.total = total; this.error = error;
    }
    @Override public void run() {
        if (job.cb == null) return;
        if (isProgress) job.cb.onDlProgress(pct, got, total);
        else job.cb.onDlDone(ok, error);
    }
}

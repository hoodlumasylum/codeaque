package com.codeaque.launcher;

/** Named progress callback for NovaDl (no anonymous classes). */
public final class NovaDlProgress implements NovaDl.Cb {
    final NovaDlJob job;
    public NovaDlProgress(NovaDlJob job) { this.job = job; }
    @Override public void onProgress(int percent, long got, long total) { job.progress(percent, got, total); }
    @Override public void onDone(boolean ok, String error) { job.done(ok, error); }
}

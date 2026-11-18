package com.codeaque.launcher;

import java.io.File;

/** Deletes model files for a pack marked unsafe. Runs off the UI thread. */
public final class NovaAsrPurge implements Runnable {
    final File dir;
    public NovaAsrPurge(File dir) { this.dir = dir; }

    @Override public void run() {
        if (dir == null) return;
        String[] names = NovaAsrFiles.namesFor(dir);
        for (int i = 0; i < names.length; i++) {
            try { new File(dir, names[i]).delete(); } catch (Throwable ignored) { }
        }
        // Keep .bad so the next start still skips this pack.
        NovaAsrGuard.clearMark(NovaAsrGuard.loadingMark(dir));
    }
}

package com.codeaque.launcher;

import android.content.Context;

/** Background thread body for sherpa-onnx init. Named so d8 8.2 does not NPE. */
public final class NovaAsrLoadJob implements Runnable {
    final Context app;
    public NovaAsrLoadJob(Context app) { this.app = app; }

    @Override public void run() {
        boolean ok = false;
        String err = null;
        try {
            ok = NovaAsr.ensureLoaded(app);
            if (!ok) err = NovaAsr.lastError();
        } catch (Throwable t) {
            ok = false;
            err = t.getMessage() == null ? "could not load the built-in listener" : t.getMessage();
            NovaAsr.lastError = err;
        }
        NovaAsr.onLoadJobFinished(ok, err);
    }
}

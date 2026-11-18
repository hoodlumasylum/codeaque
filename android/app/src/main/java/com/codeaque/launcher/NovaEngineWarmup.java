package com.codeaque.launcher;

import android.content.Context;

/**
 * Load Heart and the on-device brain in the background when the app starts,
 * so opening Chat can speak a canned greeting at once.
 */
public final class NovaEngineWarmup implements Runnable {
    final Context app;

    public NovaEngineWarmup(Context c) {
        this.app = c == null ? null : c.getApplicationContext();
    }

    @Override public void run() {
        if (app == null) return;
        try {
            if (NovaKokoro.filesReady(app) && !NovaKokoro.isLoaded()) NovaKokoro.load(app);
        } catch (Throwable t) {
            android.util.Log.w("NovaVoice", "Heart warmup failed", t);
        }
        try {
            if (NovaLlm.filesReady(app) && !NovaLlm.isLoaded()) NovaLlm.load(app);
        } catch (Throwable t) {
            android.util.Log.w("NovaVoice", "brain warmup failed", t);
        }
    }
}

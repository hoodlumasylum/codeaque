package com.codeaque.launcher;

/** UI-thread delivery of a built-in listening load result. */
public final class NovaAsrLoadPost implements Runnable {
    final NovaAsrLoadCb cb;
    final boolean ok;
    final String error;
    public NovaAsrLoadPost(NovaAsrLoadCb cb, boolean ok, String error) {
        this.cb = cb;
        this.ok = ok;
        this.error = error;
    }
    @Override public void run() {
        if (cb != null) {
            try { cb.onAsrLoad(ok, error); } catch (Throwable ignored) { }
        }
    }
}

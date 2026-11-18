package com.codeaque.launcher;

/** If Heart fails, retry or show the error on the UI thread. Never mix in the phone's voice once Heart is downloaded. */
public final class NovaVoiceFallback implements Runnable {
    final NovaView view;
    final String text;
    final int gen;
    final String error;
    public NovaVoiceFallback(NovaView view, String text) {
        this(view, text, 0, null);
    }
    public NovaVoiceFallback(NovaView view, String text, int gen, String error) {
        this.view = view;
        this.text = text;
        this.gen = gen;
        this.error = error;
    }
    @Override public void run() {
        if (view == null) return;
        if (gen != 0 && !NovaSpeakGate.isCurrent(gen, view.speakGen)) return;
        view.onHeartFailed(error);
        view.speakSystem(text);
    }
}

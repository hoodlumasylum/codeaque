package com.codeaque.launcher;

/** If the on-device voice fails, fall back to system TTS on the UI thread. */
public final class NovaVoiceFallback implements Runnable {
    final NovaView view;
    final String text;
    public NovaVoiceFallback(NovaView view, String text) {
        this.view = view;
        this.text = text;
    }
    @Override public void run() { view.speakSystem(text); }
}

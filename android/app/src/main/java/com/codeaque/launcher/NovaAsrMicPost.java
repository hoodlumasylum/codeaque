package com.codeaque.launcher;

/** UI-thread callback from {@link NovaAsrMic}. Named for d8 8.2. */
public final class NovaAsrMicPost implements Runnable {
    public static final int PARTIAL = 1, RESULT = 2, ERROR = 3, RMS = 4, LISTEN = 5;
    final NovaAsrMic.Listener listener;
    final int kind;
    final String text;
    final float level;
    final boolean on;

    public NovaAsrMicPost(NovaAsrMic.Listener listener, int kind, String text, float level, boolean on) {
        this.listener = listener;
        this.kind = kind;
        this.text = text;
        this.level = level;
        this.on = on;
    }

    @Override public void run() {
        if (listener == null) return;
        if (kind == PARTIAL) listener.onAsrPartial(text);
        else if (kind == RESULT) listener.onAsrResult(text);
        else if (kind == ERROR) listener.onAsrError(text);
        else if (kind == RMS) listener.onAsrRms(level);
        else if (kind == LISTEN) listener.onAsrListening(on);
    }
}

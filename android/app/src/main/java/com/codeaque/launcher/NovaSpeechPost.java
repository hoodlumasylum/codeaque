package com.codeaque.launcher;

/** UI-thread callback from NovaSpeech. Named so d8 8.2 does not NPE on an anonymous Runnable. */
public final class NovaSpeechPost implements Runnable {
    public static final int RESULT = 1, ERROR = 2, STATE = 3, RETRY = 4, USE_PROMPT = 5, NEED_SERVICE = 6;
    final NovaSpeech speech;
    final int kind;
    final String text;
    final boolean on;

    public NovaSpeechPost(NovaSpeech speech, int kind, String text, boolean on) {
        this.speech = speech;
        this.kind = kind;
        this.text = text;
        this.on = on;
    }

    @Override public void run() {
        if (speech == null || speech.destroyed) return;
        NovaSpeech.Callbacks c = speech.cb;
        if (c == null) return;
        if (kind == RESULT) c.onSpeechResult(text);
        else if (kind == ERROR) c.onSpeechError(text);
        else if (kind == STATE) c.onSpeechState(on);
        else if (kind == RETRY) c.onSpeechRetry();
        else if (kind == USE_PROMPT) c.onSpeechUsePrompt();
        else if (kind == NEED_SERVICE) c.onSpeechNeedService();
    }
}

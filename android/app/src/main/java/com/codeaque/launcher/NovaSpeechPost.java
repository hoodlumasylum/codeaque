package com.codeaque.launcher;

/** UI-thread callback from NovaSpeech. Named so d8 8.2 does not NPE on an anonymous Runnable. */
public final class NovaSpeechPost implements Runnable {
    public static final int RESULT = 1, ERROR = 2, STATE = 3, RETRY = 4, USE_PROMPT = 5, NEED_SERVICE = 6,
            PARTIAL = 7, MIC_BUSY = 8, RMS = 9, NEED_BUILTIN = 10;
    final NovaSpeech speech;
    final int kind;
    final String text;
    final boolean on;
    final int gen;
    final int code;
    final float level;

    public NovaSpeechPost(NovaSpeech speech, int kind, String text, boolean on) {
        this(speech, kind, text, on, speech == null ? 0 : speech.listenGen, 0, 0f);
    }

    public NovaSpeechPost(NovaSpeech speech, int kind, String text, boolean on, int gen) {
        this(speech, kind, text, on, gen, 0, 0f);
    }

    public NovaSpeechPost(NovaSpeech speech, int kind, String text, boolean on, int gen, int code) {
        this(speech, kind, text, on, gen, code, 0f);
    }

    public NovaSpeechPost(NovaSpeech speech, int kind, String text, boolean on, int gen, int code, float level) {
        this.speech = speech;
        this.kind = kind;
        this.text = text;
        this.on = on;
        this.gen = gen;
        this.code = code;
        this.level = level;
    }

    @Override public void run() {
        if (speech == null || speech.destroyed) return;
        NovaSpeech.Callbacks c = speech.cb;
        if (c == null) return;
        if (kind == RESULT || kind == PARTIAL || kind == ERROR || kind == MIC_BUSY || kind == RETRY) {
            if (NovaConvo.stale(gen, speech.listenGen) && gen != 0) return;
        }
        if (kind == RESULT) c.onSpeechResult(text);
        else if (kind == ERROR) c.onSpeechError(text);
        else if (kind == STATE) c.onSpeechState(on);
        else if (kind == RETRY) c.onSpeechRetry(code);
        else if (kind == USE_PROMPT) c.onSpeechUsePrompt();
        else if (kind == NEED_SERVICE) c.onSpeechNeedService();
        else if (kind == PARTIAL) c.onSpeechPartial(text);
        else if (kind == MIC_BUSY) c.onSpeechMicBusy(text);
        else if (kind == RMS) c.onSpeechRms(level);
        else if (kind == NEED_BUILTIN) c.onSpeechNeedBuiltin(text);
    }
}

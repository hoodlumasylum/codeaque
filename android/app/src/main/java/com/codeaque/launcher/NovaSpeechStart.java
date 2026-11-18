package com.codeaque.launcher;

import android.content.Context;

/** Hop SpeechRecognizer creation onto the main thread. Named for d8 8.2. */
public final class NovaSpeechStart implements Runnable {
    final NovaSpeech speech;
    final Context ctx;
    final NovaSpeech.Callbacks cb;
    final boolean forceBuiltin;
    public NovaSpeechStart(NovaSpeech speech, Context ctx, NovaSpeech.Callbacks cb) {
        this(speech, ctx, cb, false);
    }
    public NovaSpeechStart(NovaSpeech speech, Context ctx, NovaSpeech.Callbacks cb, boolean forceBuiltin) {
        this.speech = speech;
        this.ctx = ctx;
        this.cb = cb;
        this.forceBuiltin = forceBuiltin;
    }
    @Override public void run() { speech.start(ctx, cb, forceBuiltin); }
}

package com.codeaque.launcher;

import android.content.Context;

/** Hop SpeechRecognizer creation onto the main thread. Named for d8 8.2. */
public final class NovaSpeechStart implements Runnable {
    final NovaSpeech speech;
    final Context ctx;
    final NovaSpeech.Callbacks cb;
    public NovaSpeechStart(NovaSpeech speech, Context ctx, NovaSpeech.Callbacks cb) {
        this.speech = speech;
        this.ctx = ctx;
        this.cb = cb;
    }
    @Override public void run() { speech.start(ctx, cb); }
}

package com.codeaque.launcher;

/** Polls adaptive end-of-speech while the system recognizer is running. */
public final class NovaSpeechEndWatch implements Runnable {
    final NovaSpeech speech;
    final int gen;
    public NovaSpeechEndWatch(NovaSpeech speech, int gen) {
        this.speech = speech;
        this.gen = gen;
    }
    @Override public void run() {
        if (speech != null) speech.onEndpointWatch(gen);
    }
}

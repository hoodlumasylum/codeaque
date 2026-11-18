package com.codeaque.launcher;

/** Starts the built-in mic after a background model load, or falls back. */
public final class NovaSpeechAsrLoadCb implements NovaAsrLoadCb {
    final NovaSpeech speech;
    final String reason;
    final int gen;
    public NovaSpeechAsrLoadCb(NovaSpeech speech, String reason) {
        this.speech = speech;
        this.reason = reason;
        this.gen = speech == null ? 0 : speech.listenGen;
    }
    @Override public void onAsrLoad(boolean ok, String error) {
        if (speech != null) speech.onAsrLoadDone(ok, error, reason, gen);
    }
}

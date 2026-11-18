package com.codeaque.launcher;

/** After onEndOfSpeech, wait briefly for onResults, then use the last partial. */
public final class NovaSpeechResultsWait implements Runnable {
    final NovaSpeech speech;
    final int gen;
    public NovaSpeechResultsWait(NovaSpeech speech, int gen) {
        this.speech = speech;
        this.gen = gen;
    }
    @Override public void run() {
        if (speech != null) speech.onResultsWait(gen);
    }
}

package com.codeaque.launcher;

/** Named delayed startListening so a cancel can settle before the next start. */
public final class NovaSpeechListenGo implements Runnable {
    final NovaSpeech speech;
    final boolean preferOffline;
    final String lang;
    final int gen;

    public NovaSpeechListenGo(NovaSpeech speech, boolean preferOffline, String lang, int gen) {
        this.speech = speech;
        this.preferOffline = preferOffline;
        this.lang = lang;
        this.gen = gen;
    }

    @Override public void run() {
        if (speech == null) return;
        speech.startSoon = null;
        if (speech.destroyed) return;
        if (gen != 0 && speech.listenGen != gen && speech.listening) return;
        speech.listenNow(preferOffline, lang);
    }
}

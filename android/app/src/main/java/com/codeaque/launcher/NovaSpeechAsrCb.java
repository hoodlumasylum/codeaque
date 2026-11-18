package com.codeaque.launcher;

/** Forwards built-in ASR events into NovaSpeech. Named for d8 8.2. */
public final class NovaSpeechAsrCb implements NovaAsrMic.Listener {
    final NovaSpeech speech;
    public NovaSpeechAsrCb(NovaSpeech speech) { this.speech = speech; }

    @Override public void onAsrPartial(String text) {
        if (speech == null || speech.destroyed || !speech.listening) return;
        if (speech.cb != null) {
            speech.ui.post(new NovaSpeechPost(speech, NovaSpeechPost.PARTIAL, text, false, speech.listenGen));
        }
    }

    @Override public void onAsrResult(String text) {
        if (speech == null || speech.destroyed || !speech.listening) return;
        int gen = speech.listenGen;
        speech.listening = false;
        speech.notifyState(false);
        if (speech.cb != null) {
            speech.ui.post(new NovaSpeechPost(speech, NovaSpeechPost.RESULT, text, false, gen));
        }
    }

    @Override public void onAsrError(String message) {
        if (speech == null || speech.destroyed || !speech.listening) return;
        int gen = speech.listenGen;
        speech.listening = false;
        speech.notifyState(false);
        if (message != null && message.contains("Mic is busy")) {
            if (speech.cb != null) {
                speech.ui.post(new NovaSpeechPost(speech, NovaSpeechPost.MIC_BUSY, message, false, gen));
            }
            return;
        }
        if (speech.cb != null) {
            speech.ui.post(new NovaSpeechPost(speech, NovaSpeechPost.RETRY, message, false, gen, NovaVoiceGate.ERR_NO_MATCH));
        }
    }

    @Override public void onAsrRms(float level) {
        if (speech == null || speech.destroyed || !speech.listening) return;
        if (speech.cb != null) {
            speech.ui.post(new NovaSpeechPost(speech, NovaSpeechPost.RMS, null, false, speech.listenGen, 0, level));
        }
    }

    @Override public void onAsrListening(boolean on) {
        if (speech == null || speech.destroyed) return;
        speech.listening = on;
        speech.notifyState(on);
    }
}

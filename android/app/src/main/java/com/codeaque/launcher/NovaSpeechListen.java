package com.codeaque.launcher;

import android.os.Bundle;
import android.speech.RecognitionListener;

/**
 * Named RecognitionListener for NovaSpeech. d8 8.2 has NPEd on anonymous
 * listeners attached to Nova; keep this as a top-level class.
 */
public final class NovaSpeechListen implements RecognitionListener {
    final NovaSpeech speech;

    public NovaSpeechListen(NovaSpeech speech) { this.speech = speech; }

    @Override public void onReadyForSpeech(Bundle params) { if (speech != null) speech.onReady(); }
    @Override public void onBeginningOfSpeech() { }
    @Override public void onRmsChanged(float rmsdB) { if (speech != null) speech.onRms(rmsdB); }
    @Override public void onBufferReceived(byte[] buffer) { }
    @Override public void onEndOfSpeech() { if (speech != null) speech.onEndOfSpeech(); }
    @Override public void onError(int error) { if (speech != null) speech.onError(error); }
    @Override public void onResults(Bundle results) { if (speech != null) speech.onResults(results); }
    @Override public void onPartialResults(Bundle partialResults) { if (speech != null) speech.onPartial(partialResults); }
    @Override public void onEvent(int eventType, Bundle params) { }
}

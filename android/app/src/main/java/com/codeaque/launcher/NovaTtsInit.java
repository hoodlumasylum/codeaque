package com.codeaque.launcher;

import android.speech.tts.TextToSpeech;

/** Named TTS init callback. d8 8.2 has NPEd on anonymous listeners on Nova. */
public final class NovaTtsInit implements TextToSpeech.OnInitListener {
    final NovaView view;

    public NovaTtsInit(NovaView view) { this.view = view; }

    @Override public void onInit(int status) {
        if (view != null) view.onTtsInit(status);
    }
}

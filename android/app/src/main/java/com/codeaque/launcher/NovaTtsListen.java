package com.codeaque.launcher;

import android.speech.tts.UtteranceProgressListener;

/**
 * Named UtteranceProgressListener. d8 8.2 has NPEd on anonymous listeners on Nova.
 * Callbacks arrive on a binder thread — always post to the UI thread.
 */
public final class NovaTtsListen extends UtteranceProgressListener {
    final NovaView view;

    public NovaTtsListen(NovaView view) { this.view = view; }

    @Override public void onStart(String utteranceId) {
        post(NovaTtsEvent.START, utteranceId);
    }

    @Override public void onDone(String utteranceId) {
        post(NovaTtsEvent.DONE, utteranceId);
    }

    @Override public void onError(String utteranceId) {
        post(NovaTtsEvent.DONE, utteranceId);
    }

    @Override public void onError(String utteranceId, int errorCode) {
        post(NovaTtsEvent.DONE, utteranceId);
    }

    void post(int kind, String id) {
        if (view == null) return;
        view.post(new NovaTtsEvent(view, kind, id));
    }
}

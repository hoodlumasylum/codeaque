package com.codeaque.launcher;

/** Named NovaSpeech.Callbacks for NovaView. d8 8.2 has NPEd on anonymous listeners on Nova. */
public final class NovaTalkCb implements NovaSpeech.Callbacks {
    final NovaView view;

    public NovaTalkCb(NovaView view) { this.view = view; }

    @Override public void onSpeechResult(String text) {
        if (view == null) return;
        view.refreshMicLabel();
        view.onHeard(text);
    }

    @Override public void onSpeechPartial(String text) {
        if (view != null) view.onPartialHeard(text);
    }

    @Override public void onSpeechMicBusy(String message) {
        if (view != null) view.onMicBusy(message);
    }

    @Override public void onSpeechError(String message) {
        if (view == null) return;
        view.refreshMicLabel();
        if (message != null) view.showListenStatus(message);
        // Stay in the hands-free loop after a hard error, unless permission was refused
        // or the mic is busy (retrying would spin against a screen recorder).
        if (message != null && (message.toLowerCase().contains("wasn't allowed")
                || message.contains("Mic is busy"))) {
            return;
        }
        if (view.voiceLoop) view.postDelayed(new NovaListenSoon(view), 600);
    }

    @Override public void onSpeechState(boolean listening) {
        if (view != null) {
            view.refreshMicLabel();
            view.invalidate();
        }
    }

    @Override public void onSpeechRetry() {
        if (view == null) return;
        view.refreshMicLabel();
        view.postDelayed(new NovaListenSoon(view), 300);
    }

    @Override public void onSpeechUsePrompt() {
        if (view == null) return;
        view.refreshMicLabel();
        if (view.host != null) view.host.novaStartSpeechPrompt();
    }

    @Override public void onSpeechNeedService() {
        if (view == null) return;
        view.showSpeechServiceMissing();
    }
}

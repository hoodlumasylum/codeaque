package com.codeaque.launcher;

/** Named NovaSpeech.Callbacks for NovaView. d8 8.2 has NPEd on anonymous listeners on Nova. */
public final class NovaTalkCb implements NovaSpeech.Callbacks {
    final NovaView view;

    public NovaTalkCb(NovaView view) { this.view = view; }

    @Override public void onSpeechResult(String text) {
        if (view == null) return;
        view.refreshMicLabel();
        view.ask(text);
    }

    @Override public void onSpeechError(String message) {
        if (view == null) return;
        view.refreshMicLabel();
        if (message != null) {
            view.lastAnswer = message;
            if (view.answerView != null) view.answerView.setText(message);
        }
        // Stay in the hands-free loop after a hard error, unless permission was refused.
        if (message == null || !message.toLowerCase().contains("wasn't allowed")) {
            view.postDelayed(new NovaListenSoon(view), 600);
        }
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
}

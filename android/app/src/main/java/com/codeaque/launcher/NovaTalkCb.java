package com.codeaque.launcher;

/** Named NovaSpeech.Callbacks for NovaView. d8 8.2 has NPEd on anonymous listeners on Nova. */
public final class NovaTalkCb implements NovaSpeech.Callbacks {
    final NovaView view;

    public NovaTalkCb(NovaView view) { this.view = view; }

    @Override public void onSpeechResult(String text) {
        if (view == null) return;
        view.onHeard(text);
        view.refreshMicLabel();
    }

    @Override public void onSpeechPartial(String text) {
        if (view != null) view.onPartialHeard(text);
    }

    @Override public void onSpeechMicBusy(String message) {
        if (view != null) view.onMicBusy(message);
    }

    @Override public void onSpeechError(String message) {
        if (view == null) return;
        if (message != null && (message.toLowerCase().contains("wasn't allowed")
                || message.contains(NovaSpeechAvail.PERMISSION_DENIED))) {
            view.onPermissionDenied();
            return;
        }
        if (message != null && message.contains("Mic is busy")) {
            view.onMicBusy(message);
            return;
        }
        if (message != null && message.contains(NovaReplyQuick.NO_SOUND)) {
            view.onMicNoSound(message);
            return;
        }
        if (message != null && (message.contains("download built-in")
                || message.contains("No speech service"))) {
            view.showSpeechServiceMissing();
            return;
        }
        view.onEmptyListen(NovaVoiceGate.ERR_CLIENT);
    }

    @Override public void onSpeechState(boolean listening) {
        if (view != null) view.refreshMicLabel();
    }

    @Override public void onSpeechRetry(int code) {
        if (view != null) view.onEmptyListen(code);
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

    @Override public void onSpeechRms(float level) {
        if (view != null) view.onSpeechRms(level);
    }

    @Override public void onSpeechNeedBuiltin(String message) {
        if (view != null) view.showNeedBuiltin(message);
    }

    @Override public void onSpeechAsrLoading(String message) {
        if (view != null) view.onAsrLoading(message);
    }
}

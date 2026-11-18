package com.codeaque.launcher;

/** Named worker: try the on-device model, then local + Wikipedia fallback. */
public final class NovaLlmJob implements Runnable {
    final NovaView view;
    final String question;
    public NovaLlmJob(NovaView view, String question) {
        this.view = view;
        this.question = question;
    }
    @Override public void run() {
        String ans = null;
        try {
            if (!NovaLlm.isLoaded()) NovaLlm.load(view.getContext());
            if (NovaLlm.isLoaded()) {
                String raw = NovaLlm.complete(NovaLlmFilter.prompt(question));
                ans = NovaLlmFilter.filter(raw);
            }
        } catch (Throwable ignored) { }
        if (ans == null || ans.isEmpty()) {
            // Same fallback as before: local set, then a short Simple Wikipedia fact.
            view.finishOnlineAsk(question);
            return;
        }
        view.awaitingAnswer = false;
        view.post(new NovaWikiDone(view, ans));
    }
}

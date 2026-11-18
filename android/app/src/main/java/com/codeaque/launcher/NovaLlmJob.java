package com.codeaque.launcher;

/** Named worker: try the on-device model, then local + Wikipedia fallback. */
public final class NovaLlmJob implements Runnable {
    final NovaView view;
    final String question;
    final int gen;
    public NovaLlmJob(NovaView view, String question) {
        this(view, question, 0);
    }
    public NovaLlmJob(NovaView view, String question, int gen) {
        this.view = view;
        this.question = question;
        this.gen = gen;
    }
    @Override public void run() {
        if (stale()) return;
        String ans = null;
        try {
            if (!NovaLlm.isLoaded()) NovaLlm.load(view.getContext());
            if (stale()) return;
            if (NovaLlm.isLoaded()) {
                String raw = NovaLlm.complete(NovaLlmFilter.prompt(question));
                ans = NovaLlmFilter.filter(raw);
            }
        } catch (Throwable ignored) { }
        if (stale()) return;
        if (ans != null && NovaSmallTalk.isBareGreetingReply(ans)
                && !NovaSmallTalk.isBareGreeting(question)) {
            ans = NovaSmallTalk.brainFallback(question, view.kids);
        }
        if (ans == null || ans.isEmpty()) {
            // Same fallback as before: local set, then a short Simple Wikipedia fact.
            view.finishOnlineAsk(question);
            return;
        }
        view.awaitingAnswer = false;
        view.post(new NovaWikiDone(view, ans, gen));
    }
    boolean stale() {
        return view == null || (gen != 0 && view.askGen != gen);
    }
}

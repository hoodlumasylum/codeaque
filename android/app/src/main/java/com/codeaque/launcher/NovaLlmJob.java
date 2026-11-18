package com.codeaque.launcher;

/** Named worker: try the on-device model within a short budget, then local fallback. */
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
        // Never block a question on a model load. If it is not already in
        // memory, answer locally and let the warmup thread keep loading it.
        if (!NovaLlm.isLoaded()) {
            if (view.getContext() != null && NovaLlm.filesReady(view.getContext())) {
                try { new Thread(new NovaEngineWarmup(view.getContext()), "nova-brain-load").start(); }
                catch (Throwable ignored) { }
            }
            if (stale()) return;
            view.post(new NovaLlmBudget(view, question, gen));
            return;
        }
        String ans = null;
        try {
            String raw = NovaLlm.complete(NovaLlmFilter.prompt(question));
            ans = NovaLlmFilter.filter(raw);
        } catch (Throwable ignored) { }
        if (stale()) return;
        if (ans != null && (NovaSmallTalk.isUnreadyPhrase(ans)
                || (NovaSmallTalk.isBareGreetingReply(ans) && !NovaSmallTalk.isBareGreeting(question)))) {
            ans = null;
        }
        if (ans == null || ans.isEmpty()) {
            view.post(new NovaLlmBudget(view, question, gen));
            return;
        }
        view.post(new NovaWikiDone(view, ans, gen));
    }
    boolean stale() {
        return view == null || (gen != 0 && view.askGen != gen);
    }
}

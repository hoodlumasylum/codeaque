package com.codeaque.launcher;

/** If the on-device brain has not answered within the budget, fall back at once. */
public final class NovaLlmBudget implements Runnable {
    final NovaView view;
    final String question;
    final int gen;

    public NovaLlmBudget(NovaView view, String question, int gen) {
        this.view = view;
        this.question = question;
        this.gen = gen;
    }

    @Override public void run() {
        if (view == null) return;
        if (gen != 0 && view.askGen != gen) return;
        if (!view.awaitingAnswer) return;
        view.finishInstantAsk(question, gen);
    }
}

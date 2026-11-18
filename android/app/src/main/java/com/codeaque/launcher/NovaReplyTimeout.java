package com.codeaque.launcher;

/** Named so a late Heart load cannot wedge the conversation loop. d8 8.2. */
public final class NovaReplyTimeout implements Runnable {
    final NovaView view;
    final int gen;
    public NovaReplyTimeout(NovaView view, int gen) {
        this.view = view;
        this.gen = gen;
    }
    @Override public void run() {
        if (view != null) view.onReplyTimeout(gen);
    }
}

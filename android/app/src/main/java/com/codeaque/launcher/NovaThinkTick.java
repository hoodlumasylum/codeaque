package com.codeaque.launcher;

/** Updates "Thinking… 3s" once a second. Named for d8 8.2. */
public final class NovaThinkTick implements Runnable {
    final NovaView view;
    final long started;

    public NovaThinkTick(NovaView view, long started) {
        this.view = view;
        this.started = started;
    }

    @Override public void run() {
        if (view == null) return;
        view.onThinkTick(started);
    }
}

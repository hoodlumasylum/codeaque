package com.codeaque.launcher;

/** Finish a debounced Listening / Mic display update. */
public final class NovaListenDisplayTick implements Runnable {
    final NovaView view;
    public NovaListenDisplayTick(NovaView view) { this.view = view; }
    @Override public void run() {
        if (view == null) return;
        view.displayTick = null;
        view.refreshMicLabel();
    }
}

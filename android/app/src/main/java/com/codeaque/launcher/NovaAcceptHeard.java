package com.codeaque.launcher;

/** After Heart/TTS ends, start accepting his words (named for d8 8.2). */
public final class NovaAcceptHeard implements Runnable {
    final NovaView view;
    public NovaAcceptHeard(NovaView view) { this.view = view; }
    @Override public void run() {
        if (view != null) view.onAcceptHeard();
    }
}

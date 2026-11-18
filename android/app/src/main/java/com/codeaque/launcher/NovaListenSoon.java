package com.codeaque.launcher;

/** Resume the voice-conversation loop after she finishes speaking. Named for d8 8.2. */
public final class NovaListenSoon implements Runnable {
    final NovaView view;

    public NovaListenSoon(NovaView view) { this.view = view; }

    @Override public void run() {
        if (view != null) view.maybeResumeListen();
    }
}

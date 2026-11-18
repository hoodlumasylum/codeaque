package com.codeaque.launcher;

/**
 * If the system recognizer is still listening after the listen window with
 * no speech heard, treat it as an empty cycle so we can fall over to built-in.
 */
public final class NovaListenWatch implements Runnable {
    final NovaView view;
    final int gen;

    public NovaListenWatch(NovaView view, int gen) {
        this.view = view;
        this.gen = gen;
    }

    @Override public void run() {
        if (view != null) view.onListenWatch(gen);
    }
}

package com.codeaque.launcher;

/** Drops the mic bar toward zero when RMS stops arriving, so a stuck fill cannot look like a live meter. */
public final class NovaMeterDecay implements Runnable {
    final NovaView view;

    public NovaMeterDecay(NovaView view) { this.view = view; }

    @Override public void run() {
        if (view != null) view.onMeterDecay();
    }
}

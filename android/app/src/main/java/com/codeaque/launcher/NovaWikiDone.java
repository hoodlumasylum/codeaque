package com.codeaque.launcher;

/** UI-thread apply of a Smarter Nova answer. Named so d8 8.2 does not NPE on a NovaView anonymous Runnable. */
public final class NovaWikiDone implements Runnable {
    final NovaView view;
    final String ans;
    final int gen;

    public NovaWikiDone(NovaView view, String ans) {
        this(view, ans, 0);
    }

    public NovaWikiDone(NovaView view, String ans, int gen) {
        this.view = view;
        this.ans = ans;
        this.gen = gen;
    }

    @Override public void run() {
        if (view == null) return;
        if (gen != 0 && view.askGen != gen) return;
        view.speakStreamed(ans);
        view.invalidate();
    }
}

package com.codeaque.launcher;

/** Background Smarter Nova lookup. Named so d8 8.2 does not NPE on a NovaView anonymous Runnable. */
public final class NovaWikiJob implements Runnable {
    final NovaView view;
    final String question;

    public NovaWikiJob(NovaView view, String question) {
        this.view = view;
        this.question = question;
    }

    @Override public void run() {
        if (view != null) view.finishOnlineAsk(question);
    }
}

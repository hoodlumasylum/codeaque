package com.codeaque.launcher;

/** UI-thread apply of a Smarter Nova answer. Named so d8 8.2 does not NPE on a NovaView anonymous Runnable. */
public final class NovaWikiDone implements Runnable {
    final NovaView view;
    final String ans;

    public NovaWikiDone(NovaView view, String ans) {
        this.view = view;
        this.ans = ans;
    }

    @Override public void run() {
        if (view == null) return;
        view.lastAnswer = ans;
        if (view.answerView != null) view.answerView.setText(ans);
        view.speakAuto(ans);
        view.invalidate();
    }
}

package com.codeaque.launcher;

/** UI-thread TTS start/end. Named so d8 8.2 does not NPE on an anonymous Runnable. */
public final class NovaTtsEvent implements Runnable {
    public static final int START = 1, DONE = 2;
    final NovaView view;
    final int kind;
    final String id;

    public NovaTtsEvent(NovaView view, int kind, String id) {
        this.view = view;
        this.kind = kind;
        this.id = id;
    }

    @Override public void run() {
        if (view == null) return;
        if (kind == START) view.onTtsStart(id);
        else view.onTtsEnd(id);
    }
}

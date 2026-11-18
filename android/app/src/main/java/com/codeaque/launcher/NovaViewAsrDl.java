package com.codeaque.launcher;

/** Download callbacks for built-in listening, posted onto NovaView. Named for d8 8.2. */
public final class NovaViewAsrDl implements NovaDlJob.NovaDlUi {
    final NovaView view;
    public NovaViewAsrDl(NovaView view) { this.view = view; }
    @Override public void onDlProgress(int percent, long got, long total) {
        if (view != null) view.onAsrDlProgress(percent);
    }
    @Override public void onDlDone(boolean ok, String error) {
        if (view != null) view.onAsrDlDone(ok, error);
    }
}

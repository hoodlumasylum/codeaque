package com.codeaque.launcher;

import android.view.View;

/** Named click: Type instead / Listen toggle in Chat. d8 8.2 NPEs on anonymous listeners. */
public final class NovaTypeListenClick implements View.OnClickListener {
    final NovaView view;
    public NovaTypeListenClick(NovaView view) { this.view = view; }
    @Override public void onClick(View v) { view.toggleTextMode(); }
}

package com.codeaque.launcher;

import android.view.View;

/** Long-press the version label to show or hide listen error codes in Chat. */
public final class NovaListenDebugClick implements View.OnLongClickListener {
    final NovaView view;

    public NovaListenDebugClick(NovaView view) { this.view = view; }

    @Override public boolean onLongClick(View v) {
        if (view != null) view.toggleListenDebug();
        return true;
    }
}

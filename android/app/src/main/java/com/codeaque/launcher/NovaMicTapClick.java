package com.codeaque.launcher;

import android.view.View;

/** Named Mic click listener. d8 8.2 has NPEd on anonymous listeners on Nova. */
public final class NovaMicTapClick implements View.OnClickListener {
    final NovaView view;

    public NovaMicTapClick(NovaView view) { this.view = view; }

    @Override public void onClick(View v) {
        if (view != null) view.onMicTap();
    }
}

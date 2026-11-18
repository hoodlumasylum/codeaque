package com.codeaque.launcher;

import android.view.View;

/** Named listener — starts the built-in listening download from Chat. */
public final class NovaAsrOfferClick implements View.OnClickListener {
    final NovaView view;
    public NovaAsrOfferClick(NovaView view) { this.view = view; }
    @Override public void onClick(View v) {
        if (view != null) view.startAsrDownload();
    }
}

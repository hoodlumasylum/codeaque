package com.codeaque.launcher;

import android.view.View;

/** Named listener — starts the smaller 45 MB listening download from Chat. */
public final class NovaAsrSmallOfferClick implements View.OnClickListener {
    final NovaView view;
    public NovaAsrSmallOfferClick(NovaView view) { this.view = view; }
    @Override public void onClick(View v) {
        if (view != null) view.startAsrDownload(true);
    }
}

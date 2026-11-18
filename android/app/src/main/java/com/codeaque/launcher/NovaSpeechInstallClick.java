package com.codeaque.launcher;

import android.view.View;

/** Named listener — d8 8.2 has NPEd on anonymous listeners. Opens the speech-service Play Store page. */
public final class NovaSpeechInstallClick implements View.OnClickListener {
    final NovaView view;
    public NovaSpeechInstallClick(NovaView view) { this.view = view; }
    @Override public void onClick(View v) {
        if (view != null && view.host != null) view.host.novaOpenSpeechInstall();
    }
}

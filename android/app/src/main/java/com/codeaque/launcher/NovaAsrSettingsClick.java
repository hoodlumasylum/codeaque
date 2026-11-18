package com.codeaque.launcher;

import android.app.Activity;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

/** Starts the built-in listening download from Nova's tab. Named for d8 8.2. */
public final class NovaAsrSettingsClick implements View.OnClickListener, NovaDlJob.NovaDlUi {
    final Activity act;
    final TextView status;
    final Button btn;
    boolean busy;

    public NovaAsrSettingsClick(Activity act, TextView status, Button btn) {
        this.act = act;
        this.status = status;
        this.btn = btn;
    }

    @Override public void onClick(View v) {
        if (busy || act == null) return;
        if (NovaAsr.filesReady(act)) {
            if (status != null) status.setText("Built-in listening is on this phone.");
            if (btn != null) btn.setVisibility(View.GONE);
            return;
        }
        busy = true;
        if (btn != null) btn.setEnabled(false);
        if (status != null) status.setText("Downloading built-in listening… 0%");
        new Thread(new NovaAsrPack(act, this), "nova-asr-dl").start();
    }

    @Override public void onDlProgress(int percent, long got, long total) {
        if (status != null) status.setText("Downloading built-in listening… " + percent + "%");
        NovaListenDiag.setReason("Downloading built-in listening… " + percent + "%");
    }

    @Override public void onDlDone(boolean ok, String error) {
        busy = false;
        if (ok) {
            if (status != null) status.setText("Built-in listening is ready. Tap Mic in Chat to talk.");
            if (btn != null) btn.setVisibility(View.GONE);
            NovaListenDiag.setReason("Built-in listening is ready. Tap Mic to talk.");
        } else {
            if (btn != null) btn.setEnabled(true);
            String msg = error != null ? error : "The download didn't finish. You can try again.";
            if (status != null) status.setText(msg);
            NovaListenDiag.setReason(msg);
        }
    }
}

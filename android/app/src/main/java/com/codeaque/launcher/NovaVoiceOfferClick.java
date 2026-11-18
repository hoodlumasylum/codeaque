package com.codeaque.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.view.View;

/** Named click: start (or explain) the Nova's-voice download from Chat. */
public final class NovaVoiceOfferClick implements View.OnClickListener {
    final NovaView view;
    public NovaVoiceOfferClick(NovaView view) { this.view = view; }

    @Override public void onClick(View v) {
        Context c = view.getContext();
        if (NovaKokoro.filesReady(c)) {
            view.refreshVoiceOffer();
            return;
        }
        SharedPreferences p = view.prefs;
        if (p != null && p.getBoolean(NovaKokoro.PREF_VOICE_LICENCE, false)) {
            start();
            return;
        }
        if (!(c instanceof Activity)) {
            start();
            return;
        }
        new AlertDialog.Builder((Activity) c, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Nova's voice")
                .setMessage("Download Nova's own voice (about 360 MB) under the Apache-2.0 licence for the Kokoro-82M model. The file stays on this phone. Until it is downloaded, she uses the phone's voice.")
                .setPositiveButton("Accept and download", new NovaVoiceOfferLicenceOk(view))
                .setNegativeButton("Not now", null)
                .show();
    }

    void start() { new Thread(new NovaVoicePack(view.getContext(), new NovaVoiceOfferUi(view)), "nova-voice-dl").start(); }
}

final class NovaVoiceOfferLicenceOk implements android.content.DialogInterface.OnClickListener {
    final NovaView view;
    NovaVoiceOfferLicenceOk(NovaView view) { this.view = view; }
    @Override public void onClick(android.content.DialogInterface d, int w) {
        if (view.prefs != null) view.prefs.edit().putBoolean(NovaKokoro.PREF_VOICE_LICENCE, true).apply();
        new Thread(new NovaVoicePack(view.getContext(), new NovaVoiceOfferUi(view)), "nova-voice-dl").start();
    }
}

final class NovaVoiceOfferUi implements NovaDlJob.NovaDlUi {
    final NovaView view;
    NovaVoiceOfferUi(NovaView view) { this.view = view; }
    @Override public void onDlProgress(int percent, long got, long total) { view.onVoiceOfferProgress(percent); }
    @Override public void onDlDone(boolean ok, String error) { view.onVoiceOfferDone(ok, error); }
}

final class NovaVoiceWarmup implements Runnable {
    final Context app;
    NovaVoiceWarmup(Context c) { this.app = c.getApplicationContext(); }
    @Override public void run() {
        try { NovaKokoro.load(app); }
        catch (Throwable t) { android.util.Log.w(NovaKokoro.TAG, "warmup load failed", t); }
    }
}

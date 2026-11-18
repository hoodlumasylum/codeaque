package com.codeaque.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

/**
 * 0.10.56: grown-ups Nova settings extras — download Nova's voice (Heart default)
 * and the optional on-device Gemma brain. Named listeners only.
 */
public final class NovaSettingsUi implements NovaDlJob.NovaDlUi {
    final Activity act;
    final SharedPreferences prefs;
    final TextView voiceStatus;
    final TextView gemmaStatus;
    final Button voiceBtn;
    final Button previewBtn;
    final Button gemmaBtn;
    boolean voiceBusy, gemmaBusy;
    int dlPercent;

    NovaSettingsUi(Activity act, SharedPreferences prefs,
                   TextView voiceStatus, Button voiceBtn, Button previewBtn,
                   TextView gemmaStatus, Button gemmaBtn) {
        this.act = act;
        this.prefs = prefs;
        this.voiceStatus = voiceStatus;
        this.voiceBtn = voiceBtn;
        this.previewBtn = previewBtn;
        this.gemmaStatus = gemmaStatus;
        this.gemmaBtn = gemmaBtn;
        refresh();
    }

    static void add(Activity act, LinearLayout box, SharedPreferences prefs, float d) {
        int pad = Math.round(12 * d);
        TextView h1 = hint(act, "Nova's voice");
        h1.setPadding(0, pad, 0, pad / 4);
        box.addView(h1);
        TextView vs = hint(act, "");
        box.addView(vs);
        Button dl = btn(act, "Download Nova's voice");
        Button prev = btn(act, "Test voice");
        Button heart = btn(act, "Heart (default)");
        Button bella = btn(act, "Bella");
        Button emma = btn(act, "Emma");
        box.addView(dl);
        box.addView(prev);
        addSpeedRow(act, box, prefs, d);
        box.addView(heart);
        box.addView(bella);
        box.addView(emma);
        TextView notice = hint(act, "This is Nova's voice (Apache-2.0 Kokoro). The model is downloaded, not stored in the app. Until then she uses the phone's voice. Bella and Emma are extras that use the same download. Test voice speaks a line and tells you which voice was used.");
        notice.setPadding(0, pad / 4, 0, pad);
        box.addView(notice);

        addListenPanel(act, box, prefs, d);

        TextView h2 = hint(act, "On-device brain");
        box.addView(h2);
        TextView gs = hint(act, "");
        box.addView(gs);
        Button gdl = btn(act, "Download on-device brain");
        box.addView(gdl);
        TextView gnote = hint(act, "Optional Gemma 3 1B model (about 800 MB) used only as Nova, under Google's Gemma Terms of Use. If it is not downloaded or the phone is too weak, she still uses her local answers and, when Smarter Nova is on, a short Wikipedia fact.");
        gnote.setPadding(0, pad / 4, 0, pad / 2);
        box.addView(gnote);

        NovaSettingsUi ui = new NovaSettingsUi(act, prefs, vs, dl, prev, gs, gdl);
        dl.setOnClickListener(new NovaVoiceDlClick(ui));
        prev.setOnClickListener(new NovaVoicePreviewClick(ui));
        heart.setOnClickListener(new NovaVoicePickClick(ui, NovaVoices.ID_KOKORO_HEART, "Heart is Nova's default voice."));
        bella.setOnClickListener(new NovaVoicePickClick(ui, NovaVoices.ID_KOKORO_BELLA, "Bella is on. Preview to hear her."));
        emma.setOnClickListener(new NovaVoicePickClick(ui, NovaVoices.ID_KOKORO_EMMA, "Emma is on. Preview to hear her."));
        gdl.setOnClickListener(new NovaGemmaDlClick(ui));
        ui.refresh();
    }

    /** Status, Test voice, and speed — also used by the Kids Nova tab. */
    static void addVoicePanel(Activity act, LinearLayout box, SharedPreferences prefs, float d) {
        int pad = Math.round(12 * d);
        TextView vs = hint(act, NovaVoiceStatus.of(NovaKokoro.filesReady(act), NovaKokoro.isLoaded(), false, 0, NovaKokoro.lastError()));
        box.addView(vs);
        Button test = btn(act, "Test voice");
        box.addView(test);
        addSpeedRow(act, box, prefs, d);
        TextView live = vs;
        test.setOnClickListener(new NovaVoicePreviewClick(new NovaSettingsUi(act, prefs, live, test, test, hint(act, ""), test)));
        addListenPanel(act, box, prefs, d);
    }

    /** Listen reason, mic meter, and built-in listening download — Nova's tab. */
    static void addListenPanel(Activity act, LinearLayout box, SharedPreferences prefs, float d) {
        int pad = Math.round(12 * d);
        TextView head = hint(act, "Listening");
        head.setPadding(0, pad, 0, 0);
        box.addView(head);
        String installed = NovaAppVer.NAME;
        try {
            installed = act.getPackageManager().getPackageInfo(act.getPackageName(), 0).versionName;
        } catch (Exception ignored) { }
        box.addView(hint(act, NovaAppVer.label(installed)));
        String line = NovaListenDiag.line();
        if (line == null || line.isEmpty()) {
            line = "Listening status will show here after you use the mic.";
        }
        box.addView(hint(act, line));
        NovaMicMeter meter = new NovaMicMeter(act);
        meter.setLevel(NovaListenDiag.level());
        android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = pad / 2;
        box.addView(meter, lp);
        TextView st = hint(act, NovaAsr.filesReady(act)
                ? "Built-in listening is on this phone."
                : "Built-in listening works without Google (" + NovaAsrFiles.SIZE_LABEL + ", Apache-2.0)." );
        box.addView(st);
        Button dl = btn(act, NovaAsrFiles.DOWNLOAD_BTN);
        if (NovaAsr.filesReady(act)) dl.setVisibility(android.view.View.GONE);
        box.addView(dl);
        dl.setOnClickListener(new NovaAsrSettingsClick(act, st, dl));
    }

    static void addSpeedRow(Activity act, LinearLayout box, SharedPreferences prefs, float d) {
        int pad = Math.round(8 * d);
        TextView lab = hint(act, speedLabel(NovaKokoro.speedFrom(prefs)));
        lab.setPadding(0, pad, 0, 0);
        box.addView(lab);
        SeekBar bar = new SeekBar(act);
        bar.setMax(60);
        bar.setProgress(NovaVoiceStatus.speedToProgress(NovaKokoro.speedFrom(prefs)));
        bar.setOnSeekBarChangeListener(new NovaVoiceSpeedChange(prefs, lab));
        box.addView(bar);
    }

    static String speedLabel(float speed) {
        return "Voice speed  " + Math.round(speed * 100f) + "%";
    }

    static TextView hint(Context c, String t) {
        TextView v = new TextView(c);
        v.setText(t);
        v.setTextColor(0xFFD8D0C0);
        return v;
    }

    static Button btn(Context c, String t) {
        Button b = new Button(c);
        b.setText(t);
        b.setAllCaps(false);
        return b;
    }

    void refresh() {
        boolean v = NovaKokoro.filesReady(act);
        String id = NovaKokoro.selectedId(prefs);
        String name = "Heart";
        if (NovaVoices.ID_KOKORO_BELLA.equals(id)) name = "Bella";
        else if (NovaVoices.ID_KOKORO_EMMA.equals(id)) name = "Emma";
        if (voiceBusy) voiceStatus.setText(NovaVoiceStatus.downloading(dlPercent));
        else voiceStatus.setText(NovaVoiceStatus.of(v, NovaKokoro.isLoaded(), false, 0, NovaKokoro.lastError()));
        if (voiceBtn != previewBtn) {
            voiceBtn.setText(v ? "Remove Nova's voice" : "Download Nova's voice");
            voiceBtn.setEnabled(!voiceBusy);
        }
        previewBtn.setEnabled(!voiceBusy);
        previewBtn.setText("Test voice");

        boolean g = NovaLlm.filesReady(act);
        if (gemmaBusy) gemmaStatus.setText("Downloading the on-device brain…");
        else if (g) gemmaStatus.setText("On-device brain is on this phone. Smarter Nova will use it when she can, with a kid-safe filter.");
        else gemmaStatus.setText("Not downloaded. Smarter Nova will use her local answers and Wikipedia until you download it.");
        gemmaBtn.setText(g ? "Remove on-device brain" : "Download on-device brain");
        gemmaBtn.setEnabled(!gemmaBusy);
    }

    void toast(String s) { Toast.makeText(act, s, Toast.LENGTH_LONG).show(); }

    void startVoiceDownload() {
        if (voiceBusy) return;
        voiceBusy = true;
        refresh();
        new Thread(new NovaVoicePack(act, this), "nova-voice-dl").start();
    }

    void startGemmaDownload() {
        if (gemmaBusy) return;
        gemmaBusy = true;
        NovaLlm.setDownloadPercent(0);
        refresh();
        FileDest dest = new FileDest();
        dest.file = NovaLlm.file(act);
        new Thread(new NovaDlJob(NovaLlm.URL, dest.file, new NovaGemmaDlUi(this)), "nova-gemma-dl").start();
    }

    void voiceDone(boolean ok, String error) {
        voiceBusy = false;
        if (ok) {
            toast("Nova's voice is downloaded. Starting Heart…");
            new Thread(new NovaVoiceWarmupAndRefresh(this), "nova-voice-load").start();
        } else {
            if (error != null) NovaKokoro.lastError = error;
            refresh();
            toast(error != null ? error : "Nova's voice didn't finish downloading.");
        }
    }

    void gemmaDone(boolean ok, String error) {
        gemmaBusy = false;
        NovaLlm.setDownloadPercent(ok ? 100 : -1);
        refresh();
        if (ok) {
            toast("The on-device brain is downloaded. Getting it ready…");
            new Thread(new NovaEngineWarmup(act), "nova-brain-load").start();
        } else {
            toast(error != null ? error : "The on-device brain didn't finish downloading.");
        }
    }

    @Override public void onDlProgress(int percent, long got, long total) {
        dlPercent = percent;
        if (voiceStatus != null) voiceStatus.setText(NovaVoiceStatus.downloading(percent));
    }

    @Override public void onDlDone(boolean ok, String error) { voiceDone(ok, error); }

    static final class FileDest { java.io.File file; }
}

final class NovaGemmaDlUi implements NovaDlJob.NovaDlUi {
    final NovaSettingsUi ui;
    NovaGemmaDlUi(NovaSettingsUi ui) { this.ui = ui; }
    @Override public void onDlProgress(int percent, long got, long total) {
        NovaLlm.setDownloadPercent(percent);
        if (ui.gemmaStatus != null) ui.gemmaStatus.setText("Downloading the on-device brain… " + percent + "%");
    }
    @Override public void onDlDone(boolean ok, String error) { ui.gemmaDone(ok, error); }
}

final class NovaVoiceDlClick implements android.view.View.OnClickListener {
    final NovaSettingsUi ui;
    NovaVoiceDlClick(NovaSettingsUi ui) { this.ui = ui; }
    @Override public void onClick(android.view.View v) {
        if (NovaKokoro.filesReady(ui.act)) {
            NovaKokoro.deleteFiles(ui.act);
            ui.refresh();
            ui.toast("Nova's voice was removed. She will use the phone's voice.");
            return;
        }
        if (ui.prefs.getBoolean(NovaKokoro.PREF_VOICE_LICENCE, false)) {
            ui.startVoiceDownload();
            return;
        }
        String msg = "Download Nova's voice (about 360 MB) under the Apache-2.0 licence for the Kokoro-82M model. This is Nova's voice only. The file stays on this phone. Details are in About.";
        new AlertDialog.Builder(ui.act, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Nova's voice")
                .setMessage(msg)
                .setPositiveButton("Accept and download", new NovaVoiceLicenceOk(ui))
                .setNegativeButton("Not now", null)
                .show();
    }
}

final class NovaVoiceLicenceOk implements android.content.DialogInterface.OnClickListener {
    final NovaSettingsUi ui;
    NovaVoiceLicenceOk(NovaSettingsUi ui) { this.ui = ui; }
    @Override public void onClick(android.content.DialogInterface d, int w) {
        ui.prefs.edit().putBoolean(NovaKokoro.PREF_VOICE_LICENCE, true).apply();
        ui.startVoiceDownload();
    }
}

final class NovaVoicePreviewClick implements android.view.View.OnClickListener {
    final NovaSettingsUi ui;
    NovaVoicePreviewClick(NovaSettingsUi ui) { this.ui = ui; }
    @Override public void onClick(android.view.View v) {
        ui.toast("Playing a short hello…");
        new Thread(new NovaVoicePreviewJob(ui), "nova-voice-preview").start();
    }
}

final class NovaVoicePreviewJob implements Runnable, NovaVoicePlay.Listener {
    final NovaSettingsUi ui;
    final Context app;
    NovaVoicePreviewJob(NovaSettingsUi ui) {
        this.ui = ui;
        this.app = ui.act.getApplicationContext();
    }
    @Override public void run() {
        boolean heart = false;
        if (NovaVoiceStatus.shouldTryOnDevice(NovaKokoro.filesReady(app), NovaKokoro.selectedId(ui.prefs))) {
            if (!NovaKokoro.isLoaded()) NovaKokoro.load(app);
            float[] s = NovaKokoro.synthesize(NovaVoices.HELLO_LINE, NovaKokoro.speakerId(ui.prefs), NovaKokoro.speedFrom(ui.prefs));
            if (s != null && s.length > 16) {
                heart = true;
                NovaVoicePlay.play(s, NovaKokoro.sampleRate(), this);
            }
        }
        final boolean usedHeart = heart;
        ui.act.runOnUiThread(new NovaVoiceTestResult(ui, usedHeart));
        if (!usedHeart) ui.act.runOnUiThread(new NovaVoiceTestSystem(ui));
    }
    @Override public void onVoiceStart() { }
    @Override public void onVoiceAmp(float amp) { }
    @Override public void onVoiceEnd() { }
    @Override public void onVoiceSoonEnd() { }
}

final class NovaVoiceTestResult implements Runnable {
    final NovaSettingsUi ui;
    final boolean heart;
    NovaVoiceTestResult(NovaSettingsUi ui, boolean heart) { this.ui = ui; this.heart = heart; }
    @Override public void run() {
        ui.refresh();
        ui.toast(NovaVoiceStatus.testResult(heart));
    }
}

final class NovaVoiceTestSystem implements Runnable {
    final NovaSettingsUi ui;
    NovaVoiceTestSystem(NovaSettingsUi ui) { this.ui = ui; }
    @Override public void run() {
        // Speak the hello line with the phone's voice so the test always makes a sound.
        if (ui.act instanceof MainActivity) {
            ((MainActivity) ui.act).speakNovaLine(NovaVoices.HELLO_LINE);
        }
    }
}

final class NovaVoiceWarmupAndRefresh implements Runnable {
    final NovaSettingsUi ui;
    NovaVoiceWarmupAndRefresh(NovaSettingsUi ui) { this.ui = ui; }
    @Override public void run() {
        try { NovaKokoro.load(ui.act.getApplicationContext()); }
        catch (Throwable t) { android.util.Log.w(NovaKokoro.TAG, "warmup after download failed", t); }
        ui.act.runOnUiThread(new NovaVoiceStatusRefresh(ui));
    }
}

final class NovaVoiceStatusRefresh implements Runnable {
    final NovaSettingsUi ui;
    NovaVoiceStatusRefresh(NovaSettingsUi ui) { this.ui = ui; }
    @Override public void run() { ui.refresh(); ui.toast(NovaVoiceStatus.of(NovaKokoro.filesReady(ui.act), NovaKokoro.isLoaded(), false, 0, NovaKokoro.lastError())); }
}

final class NovaVoiceSpeedChange implements android.widget.SeekBar.OnSeekBarChangeListener {
    final SharedPreferences prefs;
    final TextView label;
    NovaVoiceSpeedChange(SharedPreferences prefs, TextView label) {
        this.prefs = prefs;
        this.label = label;
    }
    @Override public void onProgressChanged(android.widget.SeekBar bar, int progress, boolean fromUser) {
        float sp = NovaVoiceStatus.progressToSpeed(progress);
        if (label != null) label.setText(NovaSettingsUi.speedLabel(sp));
        if (fromUser && prefs != null) prefs.edit().putFloat(NovaVoiceStatus.PREF_SPEED, sp).apply();
    }
    @Override public void onStartTrackingTouch(android.widget.SeekBar bar) { }
    @Override public void onStopTrackingTouch(android.widget.SeekBar bar) { }
}

final class NovaVoicePickClick implements android.view.View.OnClickListener {
    final NovaSettingsUi ui;
    final String id, toast;
    NovaVoicePickClick(NovaSettingsUi ui, String id, String toast) {
        this.ui = ui; this.id = id; this.toast = toast;
    }
    @Override public void onClick(android.view.View v) {
        ui.prefs.edit().putString(NovaKokoro.PREF_VOICE_ID, id).apply();
        ui.refresh();
        ui.toast(toast);
    }
}

final class NovaGemmaDlClick implements android.view.View.OnClickListener {
    final NovaSettingsUi ui;
    NovaGemmaDlClick(NovaSettingsUi ui) { this.ui = ui; }
    @Override public void onClick(android.view.View v) {
        if (NovaLlm.filesReady(ui.act)) {
            NovaLlm.deleteFiles(ui.act);
            ui.refresh();
            ui.toast("The on-device brain was removed. Smarter Nova will use her local answers and Wikipedia.");
            return;
        }
        if (ui.prefs.getBoolean(NovaLlm.PREF_LICENCE, false)) {
            ui.startGemmaDownload();
            return;
        }
        String msg = "Download Google's Gemma 3 1B model (about 800 MB) under Google's Gemma Terms of Use (https://ai.google.dev/gemma/terms). It stays on this phone and is used only as Nova, with a kid-safe filter. A grown-up should read those terms before accepting.";
        new AlertDialog.Builder(ui.act, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("On-device brain")
                .setMessage(msg)
                .setPositiveButton("Accept and download", new NovaGemmaLicenceOk(ui))
                .setNegativeButton("Not now", null)
                .show();
    }
}

final class NovaGemmaLicenceOk implements android.content.DialogInterface.OnClickListener {
    final NovaSettingsUi ui;
    NovaGemmaLicenceOk(NovaSettingsUi ui) { this.ui = ui; }
    @Override public void onClick(android.content.DialogInterface d, int w) {
        ui.prefs.edit().putBoolean(NovaLlm.PREF_LICENCE, true).apply();
        ui.startGemmaDownload();
    }
}

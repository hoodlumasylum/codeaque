package com.codeaque.launcher;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.provider.Settings;

/** RECORD_AUDIO request helpers and named dialog clicks. d8 8.2 must not see anonymous listeners. */
public final class NovaMicPerm {
    public static final String PREF_ASKED = "novaMicAsked";
    private NovaMicPerm() {}

    public static void markAsked(Activity act) {
        if (act == null) return;
        SharedPreferences p = act.getSharedPreferences("codeaque", android.content.Context.MODE_PRIVATE);
        p.edit().putBoolean(PREF_ASKED, true).apply();
    }

    public static boolean asked(Activity act) {
        if (act == null) return false;
        SharedPreferences p = act.getSharedPreferences("codeaque", android.content.Context.MODE_PRIVATE);
        return p.getBoolean(PREF_ASKED, false);
    }

    public static void openAppSettings(Activity act) {
        if (act == null) return;
        try {
            Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            i.setData(Uri.fromParts("package", act.getPackageName(), null));
            act.startActivity(i);
        } catch (Exception ignored) { }
    }
}

final class NovaMicAllowClick implements DialogInterface.OnClickListener {
    final Activity act;
    NovaMicAllowClick(Activity act) { this.act = act; }
    @Override public void onClick(DialogInterface d, int w) {
        if (act == null) return;
        NovaMicPerm.markAsked(act);
        act.requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO}, MainActivity.REQ_MIC);
    }
}

final class NovaMicNotNowClick implements DialogInterface.OnClickListener {
    final Activity act;
    NovaMicNotNowClick(Activity act) { this.act = act; }
    @Override public void onClick(DialogInterface d, int w) {
        if (act instanceof MainActivity) ((MainActivity) act).onMicDialogDenied();
    }
}

final class NovaMicOpenSettingsClick implements DialogInterface.OnClickListener {
    final Activity act;
    NovaMicOpenSettingsClick(Activity act) { this.act = act; }
    @Override public void onClick(DialogInterface d, int w) {
        NovaMicPerm.openAppSettings(act);
        if (act instanceof MainActivity) ((MainActivity) act).onMicDialogOpenSettings();
    }
}

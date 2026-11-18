package com.codeaque.launcher;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Build;

/** Foreground-service channels must not sound or vibrate. */
public final class NovaNotifyQuiet {
    private NovaNotifyQuiet() {}

    public static void apply(NotificationChannel ch) {
        if (ch == null || Build.VERSION.SDK_INT < 26) return;
        ch.enableVibration(false);
        ch.setVibrationPattern(null);
        ch.setSound(null, null);
        ch.enableLights(false);
    }

    public static void install(NotificationManager nm, String id, String name, String description) {
        if (nm == null || id == null || Build.VERSION.SDK_INT < 26) return;
        NotificationChannel ch = nm.getNotificationChannel(id);
        if (ch == null) ch = new NotificationChannel(id, name, NotificationManager.IMPORTANCE_LOW);
        if (description != null) ch.setDescription(description);
        apply(ch);
        nm.createNotificationChannel(ch);
    }
}

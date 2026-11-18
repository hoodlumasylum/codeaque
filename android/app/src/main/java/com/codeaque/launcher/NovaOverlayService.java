package com.codeaque.launcher;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.Toast;

/**
 * Optional Nova bubble over other apps. Needs the parent to turn it on (PIN) and to grant
 * Android's Display over other apps permission. Off while Kids mode is on. Some phones,
 * full-screen games, or lock-task / OEM game modes may still hide or block the overlay.
 */
public class NovaOverlayService extends Service {
    public static final String ACTION_SYNC = "com.codeaque.launcher.NOVA_OVERLAY";
    public static final String ACTION_HOST = "com.codeaque.launcher.NOVA_HOST";
    static final String CH = "nova-overlay";
    static final int NOTE_ID = 52;
    static final String EXTRA_HOST = "hostVisible";
    static boolean hostVisible;

    NovaView bubble;
    WindowManager wm;
    boolean added;

    public static SharedPreferences prefs(Context c) { return c.getSharedPreferences("codeaque", MODE_PRIVATE); }

    public static boolean canOverlay(Context c) {
        return Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(c);
    }

    /** Wanted by the parent, permission granted, and not in Kids mode. */
    public static boolean shouldRun(Context c) {
        SharedPreferences p = prefs(c);
        return p.getBoolean(NovaView.PREF_OVERLAY, false) && canOverlay(c) && !p.getBoolean("kidsMode", false);
    }

    public static void sync(Context c) {
        Intent i = new Intent(c, NovaOverlayService.class).setAction(ACTION_SYNC);
        try {
            if (shouldRun(c)) {
                if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i);
                else c.startService(i);
            } else c.stopService(i);
        } catch (Exception ignored) { }
    }

    public static void setHostVisible(Context c, boolean visible) {
        hostVisible = visible;
        if (!shouldRun(c)) return;
        try {
            c.startService(new Intent(c, NovaOverlayService.class).setAction(ACTION_HOST)
                    .putExtra(EXTRA_HOST, visible));
        } catch (Exception ignored) { }
    }

    public static void openOverlaySettings(Context c) {
        ScreenTimeService.openOverlaySettings(c);
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public void onCreate() {
        super.onCreate();
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CH, "Nova", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("Nova is shown over other apps");
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) nm.createNotificationChannel(ch);
        }
        startForeground(NOTE_ID, note());
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTE_ID, note());
        if (!shouldRun(this)) { stopSelf(); return START_NOT_STICKY; }
        if (intent != null && ACTION_HOST.equals(intent.getAction()) && intent.hasExtra(EXTRA_HOST))
            hostVisible = intent.getBooleanExtra(EXTRA_HOST, false);
        if (hostVisible) hideBubble();
        else showBubble();
        return START_STICKY;
    }

    @Override public void onDestroy() {
        hideBubble();
        if (bubble != null) { bubble.release(); bubble = null; }
        super.onDestroy();
    }

    void showBubble() {
        if (added || wm == null) return;
        if (!canOverlay(this)) {
            Toast.makeText(this, "Nova over games needs Display over other apps in Android Settings.", Toast.LENGTH_LONG).show();
            stopSelf();
            return;
        }
        if (bubble == null) bubble = new NovaView(this, prefs(this), true);
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        bubble.setHost(new NovaOverlayHost(this, lp));
        try {
            wm.addView(bubble, lp);
            added = true;
        } catch (Exception e) {
            added = false;
        }
    }

    void hideBubble() {
        if (!added || wm == null || bubble == null) { added = false; return; }
        try { wm.removeView(bubble); } catch (Exception ignored) { }
        added = false;
        if (bubble != null) bubble.collapse();
    }

    Notification note() {
        Intent open = new Intent(this, MainActivity.class).setAction(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        int fl = Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_UPDATE_CURRENT | 0x02000000 : PendingIntent.FLAG_UPDATE_CURRENT;
        PendingIntent pi = PendingIntent.getActivity(this, 52, open, fl);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CH) : new Notification.Builder(this);
        b.setContentTitle("Nova is on your screen")
                .setContentText("Shown over other apps until you turn it off in CodeAque Settings. Some games may still hide her.")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentIntent(pi).setOngoing(true).setOnlyAlertOnce(true);
        if (Build.VERSION.SDK_INT < 26) b.setPriority(Notification.PRIORITY_LOW);
        return b.build();
    }
}

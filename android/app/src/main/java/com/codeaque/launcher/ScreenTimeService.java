package com.codeaque.launcher;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;

import java.util.TimeZone;

/** 0.10.37: counts Kids-mode play, and brings CodeAque back in Strict mode (time's up, or Settings /
 *  Play Store / the installer) and when "Lock phone to CodeAque in Kids mode" is on (recents / other apps).
 *  Approved kids games that were just launched are left in front. No root and no Accessibility. */
public class ScreenTimeService extends Service {
    public static final String ACTION_START = "com.codeaque.launcher.SCREEN_TIME";
    static final String CH = "screen-time";
    static final int NOTE_ID = 37, WARN_ID = 38, UP_ID = 39;
    final Handler h = new Handler(Looper.getMainLooper());
    boolean warned5;

    public static SharedPreferences prefs(Context c) { return c.getSharedPreferences("codeaque", MODE_PRIVATE); }

    public static ScreenTime.State load(SharedPreferences p, long now) {
        ScreenTime.State s = new ScreenTime.State(
                p.getInt("stLimitMin", 0), p.getLong("stUsedMs", 0), p.getInt("stExtraMin", 0),
                p.getBoolean("stUnlocked", false), p.getBoolean("stStrict", false),
                p.getString("stDay", ""), p.getLong("stStartedAt", 0));
        return ScreenTime.normalize(s, now, TimeZone.getDefault());
    }

    public static void save(SharedPreferences p, ScreenTime.State s) {
        p.edit().putInt("stLimitMin", s.limitMin).putLong("stUsedMs", s.usedMs).putInt("stExtraMin", s.extraMin)
                .putBoolean("stUnlocked", s.unlocked).putBoolean("stStrict", s.strict)
                .putString("stDay", s.day).putLong("stStartedAt", s.startedAt).apply();
    }

    public static void sync(Context c) {
        SharedPreferences p = prefs(c);
        boolean kids = p.getBoolean("kidsMode", false);
        ScreenTime.State s = load(p, System.currentTimeMillis());
        boolean need = kids && (s.strict || s.limitMin > 0 || p.getBoolean("kidsLock", false));
        Intent i = new Intent(c, ScreenTimeService.class).setAction(ACTION_START);
        try {
            if (need) {
                if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i);
                else c.startService(i);
            } else c.stopService(i);
        } catch (Exception ignored) { }
    }

    public static boolean canOverlay(Context c) {
        return Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(c);
    }

    public static boolean canUsage(Context c) {
        if (Build.VERSION.SDK_INT < 21) return false;
        try {
            android.app.AppOpsManager ops = (android.app.AppOpsManager) c.getSystemService(Context.APP_OPS_SERVICE);
            int mode = ops.checkOpNoThrow("android:get_usage_stats", android.os.Process.myUid(), c.getPackageName());
            return mode == android.app.AppOpsManager.MODE_ALLOWED;
        } catch (Exception e) { return false; }
    }

    /** Bring the Kids home to the front. timeUp shows the full-screen "Time's up" PIN gate. */
    public static void bringKidsHome(Context ctx, boolean timeUp) {
        int flags = Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED;
        Intent i = new Intent(ctx, MainActivity.class).setAction(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME).addFlags(flags)
                .putExtra("kidsTimeUp", timeUp).putExtra("kidsBounce", !timeUp);
        try { ctx.startActivity(i); }
        catch (Exception e) {
            try {
                ctx.startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                        .setPackage(ctx.getPackageName()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        .putExtra("kidsTimeUp", timeUp).putExtra("kidsBounce", !timeUp));
            } catch (Exception ignored) { }
        }
    }

    public static void openOverlaySettings(Context c) {
        try {
            c.startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    android.net.Uri.parse("package:" + c.getPackageName())).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            try { c.startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
            catch (Exception ignored) { }
        }
    }

    public static void openUsageSettings(Context c) {
        try { c.startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
        catch (Exception ignored) { }
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public void onCreate() {
        super.onCreate();
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CH, "Screen time", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("Kids mode play time and Strict mode");
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) nm.createNotificationChannel(ch);
        }
        startForeground(NOTE_ID, note("Kids mode is on", "Screen time is watching play time."));
        h.post(tick);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTE_ID, note("Kids mode is on", "Screen time is watching play time."));
        h.removeCallbacks(tick);
        h.post(tick);
        return START_STICKY;
    }

    @Override public void onDestroy() {
        h.removeCallbacks(tick);
        super.onDestroy();
    }

    final ScreenTimeTick tick = new ScreenTimeTick(this);

    String foregroundPkg() {
        if (!canUsage(this)) return null;
        try {
            UsageStatsManager um = (UsageStatsManager) getSystemService(USAGE_STATS_SERVICE);
            if (um == null) return null;
            long end = System.currentTimeMillis(), start = end - 8_000;
            UsageEvents ev = um.queryEvents(start, end);
            UsageEvents.Event e = new UsageEvents.Event();
            String last = null;
            while (ev.hasNextEvent()) {
                ev.getNextEvent(e);
                if (e.getEventType() == UsageEvents.Event.MOVE_TO_FOREGROUND) last = e.getPackageName();
            }
            return last;
        } catch (Exception e) { return null; }
    }

    Notification note(String title, String text) {
        Intent open = new Intent(this, MainActivity.class).setAction(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        int fl = Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_UPDATE_CURRENT | 0x02000000 : PendingIntent.FLAG_UPDATE_CURRENT;
        PendingIntent pi = PendingIntent.getActivity(this, 37, open, fl);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CH) : new Notification.Builder(this);
        b.setContentTitle(title).setContentText(text).setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentIntent(pi).setOngoing(true).setOnlyAlertOnce(true);
        if (Build.VERSION.SDK_INT < 26) b.setPriority(Notification.PRIORITY_LOW);
        return b.build();
    }

    void warn(String title, String text) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm == null) return;
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CH) : new Notification.Builder(this);
        b.setContentTitle(title).setContentText(text).setSmallIcon(android.R.drawable.ic_dialog_info)
                .setAutoCancel(true);
        if (Build.VERSION.SDK_INT < 26) b.setPriority(Notification.PRIORITY_DEFAULT);
        nm.notify(WARN_ID, b.build());
    }

    void timeUp() {
        bringKidsHome(this, true);
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm == null) return;
        Intent open = new Intent(this, MainActivity.class).setAction(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra("kidsTimeUp", true);
        int fl = Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_UPDATE_CURRENT | 0x02000000 : PendingIntent.FLAG_UPDATE_CURRENT;
        PendingIntent pi = PendingIntent.getActivity(this, 39, open, fl);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CH) : new Notification.Builder(this);
        b.setContentTitle("Time's up").setContentText("A parent PIN is needed to play more today.")
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm).setContentIntent(pi).setAutoCancel(true);
        if (Build.VERSION.SDK_INT >= 21) b.setFullScreenIntent(pi, true).setCategory(Notification.CATEGORY_ALARM);
        if (Build.VERSION.SDK_INT < 26) b.setPriority(Notification.PRIORITY_HIGH);
        nm.notify(UP_ID, b.build());
    }
}


/** Package-level (not an inner class) so the older D8 dexer can compile the tick loop. */
final class ScreenTimeTick implements Runnable {
    final ScreenTimeService svc;
    ScreenTimeTick(ScreenTimeService svc) { this.svc = svc; }

    @Override public void run() {
        android.content.SharedPreferences p = ScreenTimeService.prefs(svc);
        if (!p.getBoolean("kidsMode", false)) { svc.stopSelf(); return; }
        long now = System.currentTimeMillis();
        ScreenTime.State s = ScreenTimeService.load(p, now);
        boolean kidsLock = p.getBoolean("kidsLock", false);
        if (s.limitMin <= 0 && !s.strict && !kidsLock) { svc.stopSelf(); return; }
        if (kidsLock && s.limitMin <= 0)
            svc.startForeground(ScreenTimeService.NOTE_ID, svc.note("Kids mode is on", "Keeping CodeAque in front. Games you open from here can stay open."));
        if (s.limitMin > 0 && !s.unlocked) {
            String lab = ScreenTime.label(s, now, java.util.TimeZone.getDefault());
            svc.startForeground(ScreenTimeService.NOTE_ID, svc.note(lab.isEmpty() ? "Screen time" : lab,
                    s.strict ? "Strict mode is on." : "Play time is counted while a game is open."));
            if (ScreenTime.warn5(s, now, java.util.TimeZone.getDefault()) && !svc.warned5 && !p.getBoolean("stWarned5", false)) {
                svc.warned5 = true;
                p.edit().putBoolean("stWarned5", true).apply();
                svc.warn("5 minutes left", "Play time is almost up.");
            }
            if (!ScreenTime.warn5(s, now, java.util.TimeZone.getDefault()) && !ScreenTime.timeUp(s, now, java.util.TimeZone.getDefault())
                    && !s.day.equals(p.getString("stWarnDay", ""))) {
                if (ScreenTime.remainingMs(s, now, java.util.TimeZone.getDefault()) > 5 * 60_000L)
                    p.edit().putBoolean("stWarned5", false).apply();
            }
            if (s.strict && ScreenTime.timeUp(s, now, java.util.TimeZone.getDefault()) && s.startedAt > 0) {
                s = ScreenTime.pausePlay(s, now, java.util.TimeZone.getDefault());
                ScreenTimeService.save(p, s);
                svc.timeUp();
            }
        }
        String fg = (s.strict || kidsLock) ? svc.foregroundPkg() : null;
        if (fg != null && !fg.equals(svc.getPackageName())) {
            String allow = p.getString("kidsFgPkg", null);
            boolean blocked = ScreenTime.blockedKidPackage(fg) || Catalog.emulatorPkg(fg);
            boolean storeOk = ScreenTime.parentStoreAllowed(fg, p.getLong(ScreenTime.PARENT_STORE_UNTIL, 0), now);
            // 0.10.40: never bounce the parent out of system Settings after a PIN in adult mode
            if (p.getBoolean("parentSettings", false) && Catalog.settingsPkg(fg)) { /* leave Settings alone */ }
            else if (storeOk) { /* 0.10.49: parent PIN approved a Play Store install; leave it open */ }
            else if (kidsLock && (allow == null || !allow.equals(fg)))
                ScreenTimeService.bringKidsHome(svc, false);
            else if (s.strict && blocked)
                ScreenTimeService.bringKidsHome(svc, false);
        }
        svc.h.postDelayed(this, 2000);
    }
}

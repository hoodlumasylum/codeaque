package com.codeaque.launcher;

import java.util.Calendar;
import java.util.TimeZone;

/** 0.10.37: daily play limit for Kids / Kids 12+. Stored only on the device. Plain Java so checks can run on a PC. */
public final class ScreenTime {
    private ScreenTime() { }

    public static final int[] PRESETS = { 0, 15, 30, 45, 60, 90, 120 };

    public static final class State {
        public final int limitMin;     // 0 = off
        public final long usedMs;
        public final int extraMin;
        public final boolean unlocked; // rest of the local day
        public final boolean strict;
        public final String day;       // local yyyy-MM-dd
        public final long startedAt;   // wall clock when the current play session began, 0 if paused

        public State(int limitMin, long usedMs, int extraMin, boolean unlocked, boolean strict, String day, long startedAt) {
            this.limitMin = Math.max(0, limitMin);
            this.usedMs = Math.max(0, usedMs);
            this.extraMin = Math.max(0, extraMin);
            this.unlocked = unlocked;
            this.strict = strict;
            this.day = day == null ? "" : day;
            this.startedAt = Math.max(0, startedAt);
        }
    }

    public static String dayOf(long now, TimeZone tz) {
        Calendar c = Calendar.getInstance(tz == null ? TimeZone.getDefault() : tz);
        c.setTimeInMillis(now);
        return String.format("%04d-%02d-%02d", c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
    }

    public static long nextMidnight(long now, TimeZone tz) {
        Calendar c = Calendar.getInstance(tz == null ? TimeZone.getDefault() : tz);
        c.setTimeInMillis(now);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        c.add(Calendar.DAY_OF_MONTH, 1);
        return c.getTimeInMillis();
    }

    public static State normalize(State s, long now, TimeZone tz) {
        if (s == null) return new State(0, 0, 0, false, false, dayOf(now, tz), 0);
        String today = dayOf(now, tz);
        if (!today.equals(s.day)) return new State(s.limitMin, 0, 0, false, s.strict, today, 0);
        return s;
    }

    /** -1 means no limit (off, or unlocked for today). */
    public static long remainingMs(State s, long now, TimeZone tz) {
        s = normalize(s, now, tz);
        if (s.limitMin <= 0 || s.unlocked) return -1;
        long used = s.usedMs;
        if (s.startedAt > 0 && now > s.startedAt) used += now - s.startedAt;
        return Math.max(0, (s.limitMin + s.extraMin) * 60_000L - used);
    }

    public static boolean timeUp(State s, long now, TimeZone tz) {
        return remainingMs(s, now, tz) == 0;
    }

    public static boolean warn5(State s, long now, TimeZone tz) {
        long r = remainingMs(s, now, tz);
        return r > 0 && r <= 5 * 60_000L;
    }

    public static String label(State s, long now, TimeZone tz) {
        long r = remainingMs(s, now, tz);
        if (r < 0) return "";
        if (r == 0) return "Time's up";
        long min = (r + 59_999L) / 60_000L;
        if (min >= 60) {
            long h = min / 60, m = min % 60;
            return m == 0 ? h + " h left" : h + " h " + m + " min left";
        }
        return min + " min left";
    }

    public static State startPlay(State s, long now, TimeZone tz) {
        s = normalize(s, now, tz);
        if (s.limitMin <= 0 || s.unlocked || s.startedAt > 0 || timeUp(s, now, tz)) return s;
        return new State(s.limitMin, s.usedMs, s.extraMin, s.unlocked, s.strict, s.day, now);
    }

    public static State pausePlay(State s, long now, TimeZone tz) {
        s = normalize(s, now, tz);
        if (s.startedAt <= 0) return s;
        long add = Math.max(0, now - s.startedAt);
        return new State(s.limitMin, s.usedMs + add, s.extraMin, s.unlocked, s.strict, s.day, 0);
    }

    public static State addMinutes(State s, int min, long now, TimeZone tz) {
        s = pausePlay(s, now, tz);
        if (min <= 0) return s;
        int limit = s.limitMin <= 0 ? min : s.limitMin;
        return new State(limit, s.usedMs, s.extraMin + min, false, s.strict, s.day, 0);
    }

    public static State unlockToday(State s, long now, TimeZone tz) {
        s = pausePlay(s, now, tz);
        return new State(s.limitMin, s.usedMs, s.extraMin, true, s.strict, s.day, 0);
    }

    public static State setLimit(State s, int limitMin, long now, TimeZone tz) {
        s = pausePlay(normalize(s, now, tz), now, tz);
        return new State(Math.max(0, limitMin), 0, 0, false, s.strict, s.day, 0);
    }

    public static State setStrict(State s, boolean strict, long now, TimeZone tz) {
        s = normalize(s, now, tz);
        return new State(s.limitMin, s.usedMs, s.extraMin, s.unlocked, strict, s.day, s.startedAt);
    }

    /** A game / experience launched from Kids mode, not Settings, Wi-Fi, Update or Grown-ups. */
    public static boolean isPlayLaunch(Catalog.Item it) {
        if (it == null) return false;
        if (it == Catalog.PLAY_STORE || it == Catalog.DOWNLOADS || it == Catalog.CALL_TEXT) return false;
        if (it.isAction()) {
            String a = it.action;
            return a.startsWith("roblox:") || a.startsWith("gfn:") || a.startsWith("steamstore:");
        }
        return it.isRom() || it.isDeviceApp() || it.storePackage() != null || it.webUrl != null;
    }

    /** Android Settings, Play Store, the package installer, or Files — hidden / bounced in Kids mode. */
    public static boolean blockedKidPackage(String pkg) {
        if (pkg == null || pkg.isEmpty()) return false;
        String p = pkg.toLowerCase(java.util.Locale.ROOT);
        if (p.equals("com.android.settings") || p.startsWith("com.android.settings.") || p.equals("com.android.tv.settings")) return true;
        if (p.equals("com.android.vending") || p.startsWith("com.android.vending.") || p.equals("com.google.android.finsky")) return true;
        if (p.contains("packageinstaller")) return true;
        if (p.equals("com.android.documentsui") || p.equals("com.google.android.documentsui")) return true;
        return false;
    }

    /** 0.10.49: Play Store and Play's installer only — not Settings or Files. */
    public static final long PARENT_STORE_MS = 10 * 60_000L;
    public static final String PARENT_STORE_UNTIL = "parentStoreUntil";

    public static boolean parentStorePackage(String pkg) {
        if (pkg == null || pkg.isEmpty()) return false;
        String p = pkg.toLowerCase(java.util.Locale.ROOT);
        if (p.equals("com.android.vending") || p.startsWith("com.android.vending.") || p.equals("com.google.android.finsky")) return true;
        if (p.contains("packageinstaller")) return true;
        return false;
    }

    /** True while a parent-PIN install window is still open and this package is Play Store / its installer. */
    public static boolean parentStoreAllowed(String pkg, long untilMs, long nowMs) {
        return nowMs < untilMs && parentStorePackage(pkg);
    }
}

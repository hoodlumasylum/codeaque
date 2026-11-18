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

    /** 0.10.49: allowed play hours in the device's local time. Minutes are 0–1439 from midnight.
     *  If weekendSeparate is false, the weekday window applies every day. A window with start == end
     *  is treated as all day. A window with start > end wraps midnight. overrideUntil is a wall-clock
     *  PIN override (0 = none). unlocked-for-today on State also bypasses hours. */
    public static final class Hours {
        public final boolean enabled, weekendSeparate;
        public final int weekStart, weekEnd, weekendStart, weekendEnd;
        public final long overrideUntil;
        public Hours(boolean enabled, int weekStart, int weekEnd, boolean weekendSeparate,
                     int weekendStart, int weekendEnd, long overrideUntil) {
            this.enabled = enabled;
            this.weekStart = clampMin(weekStart);
            this.weekEnd = clampMin(weekEnd);
            this.weekendSeparate = weekendSeparate;
            this.weekendStart = clampMin(weekendStart);
            this.weekendEnd = clampMin(weekendEnd);
            this.overrideUntil = Math.max(0, overrideUntil);
        }
    }

    public static final int DEFAULT_WEEK_START = 16 * 60, DEFAULT_WEEK_END = 19 * 60;
    public static final int DEFAULT_WEEKEND_START = 10 * 60, DEFAULT_WEEKEND_END = 18 * 60;

    public static int clampMin(int m) {
        if (m < 0) return 0;
        if (m > 23 * 60 + 59) return 23 * 60 + 59;
        return m;
    }

    public static int minuteOfDay(long now, TimeZone tz) {
        Calendar c = Calendar.getInstance(tz == null ? TimeZone.getDefault() : tz);
        c.setTimeInMillis(now);
        return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE);
    }

    public static boolean isWeekend(long now, TimeZone tz) {
        Calendar c = Calendar.getInstance(tz == null ? TimeZone.getDefault() : tz);
        c.setTimeInMillis(now);
        int d = c.get(Calendar.DAY_OF_WEEK);
        return d == Calendar.SATURDAY || d == Calendar.SUNDAY;
    }

    public static int windowStart(Hours h, long now, TimeZone tz) {
        if (h != null && h.weekendSeparate && isWeekend(now, tz)) return h.weekendStart;
        return h == null ? DEFAULT_WEEK_START : h.weekStart;
    }

    public static int windowEnd(Hours h, long now, TimeZone tz) {
        if (h != null && h.weekendSeparate && isWeekend(now, tz)) return h.weekendEnd;
        return h == null ? DEFAULT_WEEK_END : h.weekEnd;
    }

    /** Inclusive start, exclusive end. start == end means all day. */
    public static boolean inWindow(int start, int end, int nowMin) {
        start = clampMin(start); end = clampMin(end);
        nowMin = clampMin(nowMin);
        if (start == end) return true;
        if (start < end) return nowMin >= start && nowMin < end;
        return nowMin >= start || nowMin < end;
    }

    public static boolean inHours(Hours h, long now, TimeZone tz) {
        if (h == null || !h.enabled) return true;
        return inWindow(windowStart(h, now, tz), windowEnd(h, now, tz), minuteOfDay(now, tz));
    }

    /** True when allowed hours are on and the child is currently outside them (and not PIN-overridden). */
    public static boolean outsideHours(Hours h, State s, long now, TimeZone tz) {
        if (h == null || !h.enabled) return false;
        s = normalize(s, now, tz);
        if (s.unlocked) return false;
        if (h.overrideUntil > 0 && now < h.overrideUntil) return false;
        return !inHours(h, now, tz);
    }

    public static boolean playBlocked(State s, Hours h, long now, TimeZone tz) {
        return timeUp(s, now, tz) || outsideHours(h, s, now, tz);
    }

    public static Hours overrideHours(Hours h, long untilMs) {
        if (h == null) h = new Hours(false, DEFAULT_WEEK_START, DEFAULT_WEEK_END, false,
                DEFAULT_WEEKEND_START, DEFAULT_WEEKEND_END, 0);
        return new Hours(h.enabled, h.weekStart, h.weekEnd, h.weekendSeparate, h.weekendStart, h.weekendEnd, Math.max(0, untilMs));
    }

    /** 12-hour clock for the Kids lock screen: 16:00 → "4pm", 16:30 → "4:30pm". */
    public static String formatClock(int minutes) {
        minutes = clampMin(minutes);
        int h = minutes / 60, m = minutes % 60;
        int h12 = h % 12; if (h12 == 0) h12 = 12;
        String ap = h < 12 ? "am" : "pm";
        if (m == 0) return h12 + ap;
        return h12 + ":" + (m < 10 ? "0" + m : Integer.toString(m)) + ap;
    }

    public static String format24(int minutes) {
        minutes = clampMin(minutes);
        return String.format("%02d:%02d", minutes / 60, minutes % 60);
    }

    /** Wall-clock time of the next window start at or after now. If hours are off, 0. */
    public static long nextOpen(Hours h, long now, TimeZone tz) {
        if (h == null || !h.enabled) return 0;
        tz = tz == null ? TimeZone.getDefault() : tz;
        // Search up to 8 days so a weekend-only window still resolves.
        for (int day = 0; day <= 8; day++) {
            long t = now + day * 24L * 60 * 60 * 1000;
            // Use midnight of that local day + start minutes, but compute from a calendar on `now` + days.
            Calendar c = Calendar.getInstance(tz);
            c.setTimeInMillis(now);
            c.set(Calendar.SECOND, 0);
            c.set(Calendar.MILLISECOND, 0);
            c.add(Calendar.DAY_OF_MONTH, day);
            int start = windowStart(h, c.getTimeInMillis(), tz);
            int end = windowEnd(h, c.getTimeInMillis(), tz);
            if (start == end) {
                c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0);
                long open = c.getTimeInMillis();
                if (open >= now) return open;
                continue;
            }
            if (start < end) {
                c.set(Calendar.HOUR_OF_DAY, start / 60); c.set(Calendar.MINUTE, start % 60);
                long open = c.getTimeInMillis();
                if (open >= now) return open;
            } else {
                // wraps midnight: open periods are [start, midnight) and [midnight, end)
                c.set(Calendar.HOUR_OF_DAY, start / 60); c.set(Calendar.MINUTE, start % 60);
                long open = c.getTimeInMillis();
                if (open >= now) return open;
                if (day == 0 && minuteOfDay(now, tz) < end) return now; // already in the post-midnight part
            }
        }
        return 0;
    }

    /** "4pm", "4pm tomorrow", or "4pm on Saturday" for the lock screen. */
    public static String backAtLabel(Hours h, long now, TimeZone tz) {
        long open = nextOpen(h, now, tz);
        if (open <= 0) return formatClock(windowStart(h, now, tz));
        tz = tz == null ? TimeZone.getDefault() : tz;
        Calendar a = Calendar.getInstance(tz); a.setTimeInMillis(now);
        Calendar b = Calendar.getInstance(tz); b.setTimeInMillis(open);
        String clock = formatClock(b.get(Calendar.HOUR_OF_DAY) * 60 + b.get(Calendar.MINUTE));
        if (a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR))
            return clock;
        Calendar tom = (Calendar) a.clone();
        tom.add(Calendar.DAY_OF_MONTH, 1);
        if (tom.get(Calendar.YEAR) == b.get(Calendar.YEAR) && tom.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR))
            return clock + " tomorrow";
        String[] days = { "", "Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday" };
        int d = b.get(Calendar.DAY_OF_WEEK);
        String name = (d >= 1 && d <= 7) ? days[d] : clock;
        return clock + " on " + name;
    }

    public static String hoursSummary(Hours h) {
        if (h == null || !h.enabled) return "Allowed hours: off";
        String week = format24(h.weekStart) + "–" + format24(h.weekEnd);
        if (!h.weekendSeparate) return "Allowed hours: " + week;
        return "Allowed hours: weekdays " + week + ", weekend " + format24(h.weekendStart) + "–" + format24(h.weekendEnd);
    }
}

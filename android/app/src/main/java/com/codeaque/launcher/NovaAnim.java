package com.codeaque.launcher;

/**
 * Blink, idle gaze and breathing for Nova's portrait. Plain Java so CatalogTest can
 * check the timing. Ported from the desktop face (InQuad close, hold, OutQuad open;
 * rest lids fully open; small damped saccades; bob, not a layout margin).
 */
public final class NovaAnim {
    public static final int CLOSE_MS = 72, HOLD_MS = 36, OPEN_MS = 140;
    public static final int BLINK_TOTAL = CLOSE_MS + HOLD_MS + OPEN_MS;
    public static final float GAZE_MAX_X = 0.45f, GAZE_MAX_Y = 0.25f;
    public static final float GAZE_SMOOTH_MS = 180f;
    public static final float BOB_AMP = 0.008f;
    public static final float BOB_PERIOD_MS = 1400f;
    public static final float SWAY_AMP = 1.0f;          // degrees
    public static final float SWAY_PERIOD_MS = 2400f;
    public static final float SMILE_MAX = 1f, BROW_MAX = 1f;

    public static final class State {
        public float blink;     // 0 open … 1 closed
        public float gazeX, gazeY;
        public float bob;       // fraction of the face height
        public float mouth;     // 0 closed … 1 open (lip-sync while talking)
        public float smile;     // 0 rest … 1 a soft closed-mouth smile
        public float brow;      // 0 rest … 1 slightly raised
        public float sway;      // degrees, a slow lean (hair / shoulders)
        public boolean talking;
        public boolean doublePending;
        long blinkAt, nextBlink, nextGaze, doubleAt, nextExpr, exprUntil;
        float gazeTx, gazeTy, smileT, browT;
        boolean inited;
    }

    private NovaAnim() {}

    public static void reset(State s, long now) {
        s.blink = 0;
        s.gazeX = s.gazeY = s.gazeTx = s.gazeTy = 0;
        s.bob = 0;
        s.mouth = 0;
        s.smile = s.smileT = 0;
        s.brow = s.browT = 0;
        s.sway = 0;
        s.talking = false;
        s.doublePending = false;
        s.blinkAt = 0;
        s.nextBlink = now + 1800;
        s.doubleAt = 0;
        s.nextGaze = now + 900;
        s.nextExpr = now + 2200;
        s.exprUntil = 0;
        s.inited = true;
    }

    public static void tick(State s, long now, long dt, int rand) {
        if (!s.inited) reset(s, now);
        if (s.blinkAt > 0) {
            s.blink = blinkAt(now - s.blinkAt);
            if (now - s.blinkAt >= BLINK_TOTAL) {
                s.blink = 0;
                s.blinkAt = 0;
                if (s.doublePending) {
                    s.doublePending = false;
                    s.doubleAt = now + 80;
                } else {
                    s.nextBlink = now + blinkGap(rand);
                }
            }
        } else if (s.doubleAt > 0 && now >= s.doubleAt) {
            s.doubleAt = 0;
            s.blinkAt = now;
            s.blink = 0;
        } else if (now >= s.nextBlink) {
            s.blinkAt = now;
            s.doublePending = (rand & 7) == 0;   // occasional double blink
            s.blink = 0;
        }
        if (now >= s.nextGaze) {
            s.gazeTx = gazePick(rand, GAZE_MAX_X);
            s.gazeTy = gazePick(rand >> 8, GAZE_MAX_Y);
            s.nextGaze = now + 1200 + Math.abs(rand % 2300);
        }
        float k = 1f - expMs(dt, GAZE_SMOOTH_MS);
        s.gazeX += (s.gazeTx - s.gazeX) * k;
        s.gazeY += (s.gazeTy - s.gazeY) * k;
        s.gazeX = clamp(s.gazeX, -GAZE_MAX_X, GAZE_MAX_X);
        s.gazeY = clamp(s.gazeY, -GAZE_MAX_Y, GAZE_MAX_Y);
        s.bob = (float) Math.sin(now / BOB_PERIOD_MS * 2.0 * Math.PI) * BOB_AMP;
        s.sway = (float) Math.sin(now / SWAY_PERIOD_MS * 2.0 * Math.PI) * SWAY_AMP;
        tickExpr(s, now, dt, rand);
    }

    /** Idle: occasional soft smile or a brow raise. While talking, a warmer smile and the odd emphasis. */
    static void tickExpr(State s, long now, long dt, int rand) {
        if (s.talking) {
            s.smileT = 0.45f + 0.25f * (float) Math.sin(now / 900.0 * 2.0 * Math.PI);
            if (now >= s.nextExpr) {
                s.browT = ((rand & 3) == 0) ? 0.55f : 0.15f;
                s.nextExpr = now + 900 + Math.abs(rand % 1200);
                s.exprUntil = now + 400 + Math.abs(rand % 400);
            } else if (s.exprUntil > 0 && now >= s.exprUntil) {
                s.browT = 0.12f;
                s.exprUntil = 0;
            }
        } else if (s.exprUntil > 0 && now >= s.exprUntil) {
            s.smileT = 0;
            s.browT = 0;
            s.exprUntil = 0;
            s.nextExpr = now + 2800 + Math.abs(rand % 4200);
        } else if (now >= s.nextExpr) {
            int kind = Math.abs(rand % 5);
            if (kind == 0) { s.smileT = 0.7f; s.browT = 0.15f; }
            else if (kind == 1) { s.smileT = 0.35f; s.browT = 0.55f; }
            else if (kind == 2) { s.smileT = 0; s.browT = 0.4f; }
            else { s.smileT = 0.2f; s.browT = 0; }
            s.exprUntil = now + 900 + Math.abs(rand % 1400);
            s.nextExpr = s.exprUntil + 2000;
        }
        float k = 1f - expMs(dt, 220f);
        s.smile += (s.smileT - s.smile) * k;
        s.brow += (s.browT - s.brow) * k;
        s.smile = clamp(s.smile, 0, SMILE_MAX);
        s.brow = clamp(s.brow, 0, BROW_MAX);
    }

    /** Blink amount 0..1 for a time into the current blink (ms). 0 if not blinking. */
    public static float blinkAt(long into) {
        if (into < 0 || into >= BLINK_TOTAL) return 0f;
        if (into < CLOSE_MS) return inQuad(into / (float) CLOSE_MS);
        if (into < CLOSE_MS + HOLD_MS) return 1f;
        float t = (into - CLOSE_MS - HOLD_MS) / (float) OPEN_MS;
        return 1f - outQuad(t);
    }

    public static float inQuad(float t) { t = clamp(t, 0, 1); return t * t; }
    public static float outQuad(float t) { t = clamp(t, 0, 1); float u = 1f - t; return 1f - u * u; }

    static float expMs(long dt, float tau) {
        if (dt <= 0) return 1f;
        return (float) Math.exp(-dt / tau);
    }

    static int blinkGap(int rand) { return 2500 + Math.abs(rand % 3500); }

    static float gazePick(int rand, float max) {
        float u = ((rand & 0xffff) / 65535f) * 2f - 1f;
        return u * max;
    }

    static float clamp(float v, float lo, float hi) { return v < lo ? lo : (v > hi ? hi : v); }

    /** Flap the mouth while talking; ease shut when idle. Rest (photo) lids/lips stay closed. */
    public static void tickTalk(State s, long now, boolean talking) {
        if (s == null) return;
        s.talking = talking;
        if (!talking) {
            s.mouth += (0f - s.mouth) * 0.4f;
            if (s.mouth < 0.02f) s.mouth = 0f;
            return;
        }
        float a = (float) (0.55 + 0.45 * Math.sin(now / 90.0 * 2.0 * Math.PI));
        float b = (float) (0.5 + 0.5 * Math.sin(now / 145.0 * 2.0 * Math.PI + 0.8));
        float target = clamp(a * b, 0.08f, 1f);
        s.mouth += (target - s.mouth) * 0.45f;
    }
}

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

    public static final class State {
        public float blink;     // 0 open … 1 closed
        public float gazeX, gazeY;
        public float bob;       // fraction of the face height
        public boolean doublePending;
        long blinkAt, nextBlink, nextGaze, doubleAt;
        float gazeTx, gazeTy;
        boolean inited;
    }

    private NovaAnim() {}

    public static void reset(State s, long now) {
        s.blink = 0;
        s.gazeX = s.gazeY = s.gazeTx = s.gazeTy = 0;
        s.bob = 0;
        s.doublePending = false;
        s.blinkAt = 0;
        s.nextBlink = now + 1800;
        s.doubleAt = 0;
        s.nextGaze = now + 900;
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
}

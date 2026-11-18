package com.codeaque.launcher;

/**
 * 0.10.55: Kids Search bar sizes that fit the placeholder on short landscape
 * screens, and the Hide key on the custom on-screen keyboard.
 *
 * The 0.10.54 EditText used 18 dp text in a 36*s-tall box, so on a dense
 * landscape phone the hint was clipped at the top and bottom.
 */
public final class KidsSearchLayout {
    private KidsSearchLayout() {}

    public static final String HINT = "Search for a game";
    public static final String HIDE_KEY = "Hide";

    public static final float BAR_S = 52f;
    public static final float BAR_MIN_DP = 44f;
    public static final float CHIP_S = 36f;
    public static final float GAP_S = 8f;

    /** Height of the query box in pixels, capped so the chip row still fits. */
    public static float queryHeight(float s, float density, float availH) {
        if (s < 0.05f) s = 0.05f;
        if (density < 0.5f) density = 0.5f;
        if (availH < 1) availH = 1;
        float gap = GAP_S * s;
        float chipMin = 28 * s;
        float maxBar = availH - gap - chipMin;
        if (maxBar < 32 * s) maxBar = Math.max(32 * s, availH * 0.45f);
        float want = Math.max(BAR_S * s, BAR_MIN_DP * density);
        if (want > maxBar) want = maxBar;
        if (want < 32 * s && availH >= 32 * s) want = Math.min(32 * s, maxBar);
        if (want < 1) want = 1;
        return want;
    }

    public static float chipHeight(float s, float remain) {
        if (s < 0.05f) s = 0.05f;
        float want = Math.min(40 * s, Math.max(30 * s, CHIP_S * s));
        if (remain > 0 && want > remain) want = Math.max(24 * s, remain);
        return want;
    }

    public static float padH(float s, float density) {
        if (s < 0.05f) s = 0.05f;
        if (density < 0.5f) density = 0.5f;
        return Math.max(12 * s, 10 * density);
    }

    /** Vertical padding so the letters sit in the middle with air above and below. */
    public static float padV(float barH) {
        if (barH < 1) barH = 1;
        float pad = barH * 0.18f;
        if (pad < 4) pad = Math.min(4, barH * 0.2f);
        return pad;
    }

    /**
     * Text size in pixels that fits inside the bar with padding.
     * Caps at 18 layout units or 16 dp (nearby chip labels), never larger than the inner height.
     */
    public static float textSize(float barH, float s, float density) {
        if (s < 0.05f) s = 0.05f;
        if (density < 0.5f) density = 0.5f;
        float pad = padV(barH);
        float inner = Math.max(1, barH - 2 * pad);
        float fit = inner / 1.25f;
        float want = Math.min(18 * s, 16 * density);
        if (want < 13 * s) want = Math.min(13 * s, fit);
        if (want > fit) want = fit;
        if (want < 1) want = 1;
        return want;
    }

    /** True if the typographic height plus padding fits in the bar. */
    public static boolean textFits(float barH, float textPx) {
        return textPx * 1.25f + 2 * padV(barH) <= barH + 0.51f;
    }

    /** Baseline Y from the top of the box for Canvas.drawText (y is the baseline). */
    public static float textBaselineY(float boxTop, float boxH, float textPx) {
        return boxTop + boxH * 0.5f + textPx * 0.35f;
    }

    /** Custom on-screen keyboard rows, including a visible Hide key on the bottom row. */
    public static String[][] searchKeys(boolean shift, boolean sym) {
        if (sym) {
            return new String[][]{
                    {"1", "2", "3", "4", "5", "6", "7", "8", "9", "0"},
                    {"-", "'", ".", "!", "?", "@", "&", ":", ";", "+"},
                    {"ABC", ",", "(", ")", "\"", "/", "#", "*", "⌫"},
                    {"ABC", " ", HIDE_KEY, "GO"}
            };
        }
        if (shift) {
            return new String[][]{
                    {"Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P"},
                    {"A", "S", "D", "F", "G", "H", "J", "K", "L"},
                    {"⇧", "Z", "X", "C", "V", "B", "N", "M", "⌫"},
                    {"123", " ", HIDE_KEY, "GO"}
            };
        }
        return new String[][]{
                {"q", "w", "e", "r", "t", "y", "u", "i", "o", "p"},
                {"a", "s", "d", "f", "g", "h", "j", "k", "l"},
                {"⇧", "z", "x", "c", "v", "b", "n", "m", "⌫"},
                {"123", " ", HIDE_KEY, "GO"}
        };
    }

    public static boolean isHideKey(String lab) {
        return HIDE_KEY.equals(lab) || (lab != null && "hide".equalsIgnoreCase(lab));
    }

    public static boolean lastRowHasHide(String[][] keys) {
        if (keys == null || keys.length == 0) return false;
        String[] last = keys[keys.length - 1];
        if (last == null) return false;
        for (String k : last) if (isHideKey(k)) return true;
        return false;
    }
}

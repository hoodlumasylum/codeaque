package com.codeaque.launcher;

/**
 * 0.10.66: the top category strip is a fixed-height, pixel-aligned row.
 * Widths are measured once (not on every scroll frame), icons and labels
 * stay a single size, and Y is snapped to the pixel grid so a horizontal
 * swipe cannot crawl 1–2 px vertically. The same geometry is used in
 * Kids mode. This is a custom canvas row, not a RecyclerView, so there
 * is no setHasFixedSize — the cache and fixed sizes are the equivalent.
 */
public final class HomeTabs {
    private HomeTabs() {}

    /** Icon box in design units. Selection must not change this (that moved the label). */
    public static final float ICON = 36f;
    /** One size for every label, selected or not. */
    public static final float LABEL = 16f;
    /** Gap from the icon's bottom to the label baseline, design units. */
    public static final float LABEL_GAP = 20f;
    public static final float MIN_WIDTH = 72f;
    public static final float PAD = 14f;
    public static final float GAP = 16f;
    public static final float STRIP_TOP = 0.11f;
    public static final float ICON_Y = 0.16f;
    public static final float STRIP_BOT = 0.26f;
    public static final float UNDERLINE = 6f;

    public static float snap(float v) {
        return (float) Math.round(v);
    }

    public static float stripTop(int height) { return snap(height * STRIP_TOP); }
    public static float iconY(int height) { return snap(height * ICON_Y); }
    public static float stripBot(int height) { return snap(height * STRIP_BOT); }

    public static float iconSize(float s) { return snap(ICON * s); }

    /** Label baseline: icon centre + half icon + gap. Independent of selection. */
    public static float labelY(float iconY, float s) {
        return snap(iconY + iconSize(s) * 0.5f + LABEL_GAP * s);
    }

    public static float labelSize(float s) { return LABEL * s; }

    public static float underlineTop(int height, float s) {
        return snap(stripBot(height) - UNDERLINE * s);
    }

    public static boolean needsLayout(int catCount, int width, float s,
            int laidCount, int laidWidth, float laidS, boolean dirty) {
        if (dirty) return true;
        if (catCount != laidCount || width != laidWidth) return true;
        return Math.abs(s - laidS) > 0.0001f;
    }

    /** Selection is colour and the underline, never a size or translationY change. */
    public static boolean selectionScalesIcon() { return false; }
    public static boolean selectionChangesLabelSize() { return false; }
    public static boolean twoLineLabels() { return false; }
}

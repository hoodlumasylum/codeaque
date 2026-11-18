package com.codeaque.launcher;

/**
 * 0.10.53: where Nova's bubble sits, and how it snaps to an edge after a drag.
 * Plain Java so CatalogTest can check it. Left or right edge, Y kept and clamped on-screen.
 */
public final class NovaPlace {
    private NovaPlace() {}

    /** Top-left of the bubble after snapping to the nearer vertical edge. */
    public static float[] snap(float x, float y, float size, float viewW, float viewH,
                               float insetL, float insetT, float insetR, float insetB, float margin) {
        float minX = insetL + margin;
        float minY = insetT + margin;
        float maxX = viewW - insetR - margin - size;
        float maxY = viewH - insetB - margin - size;
        if (maxX < minX) maxX = minX;
        if (maxY < minY) maxY = minY;
        float destX = (x - minX) <= (maxX - x) ? minX : maxX;
        return new float[] { destX, clamp(y, minY, maxY) };
    }

    /** Vertical position as 0..1 of the movable range (stable across rotations). */
    public static float yFrac(float y, float size, float viewH, float insetT, float insetB, float margin) {
        float minY = insetT + margin;
        float maxY = viewH - insetB - margin - size;
        if (maxY <= minY) return 1f;   // default: bottom
        return clamp((y - minY) / (maxY - minY), 0f, 1f);
    }

    public static float yFromFrac(float frac, float size, float viewH, float insetT, float insetB, float margin) {
        float minY = insetT + margin;
        float maxY = viewH - insetB - margin - size;
        if (maxY < minY) maxY = minY;
        return minY + clamp(frac, 0f, 1f) * (maxY - minY);
    }

    public static boolean leftSide(float x, float size, float viewW, float insetL, float insetR, float margin) {
        float minX = insetL + margin;
        float maxX = viewW - insetR - margin - size;
        if (maxX <= minX) return false;
        return (x - minX) <= (maxX - x);
    }

    public static float xForSide(boolean left, float size, float viewW, float insetL, float insetR, float margin) {
        float minX = insetL + margin;
        float maxX = viewW - insetR - margin - size;
        if (maxX < minX) maxX = minX;
        return left ? minX : maxX;
    }

    public static boolean movedPast(float dx, float dy, float slop) {
        return dx * dx + dy * dy > slop * slop;
    }

    /**
     * 0.10.54: a finger move past slop on the bubble starts a drag, even before the long-press timeout.
     * (0.10.53 cancelled that move so a real finger often never started a drag.)
     */
    public static boolean dragStartsOnMove(boolean alreadyDragging, boolean big, boolean pinching, boolean movedPastSlop) {
        return !alreadyDragging && !big && !pinching && movedPastSlop;
    }

    public static float clamp(float v, float lo, float hi) { return v < lo ? lo : (v > hi ? hi : v); }

    /** Window for a second tap to count as a double-tap. The first tap already fires immediately. */
    public static final int DOUBLE_MS = 280;
    /** Pinch-out past this scale goes to the large portrait + Chat. */
    public static final float PINCH_OUT = 1.25f;
    /** Pinch-in past this scale shrinks back to the bubble. */
    public static final float PINCH_IN = 0.80f;

    public static boolean isDoubleTap(long firstUpMs, long secondDownMs, int windowMs) {
        long dt = secondDownMs - firstUpMs;
        return dt >= 0 && dt <= windowMs;
    }

    /** First tap already fired; a second tap in the window is a double-tap (hide). */
    public static boolean isImmediateDoubleTap(long lastSingleUpMs, long thisUpMs, int windowMs) {
        return lastSingleUpMs > 0 && isDoubleTap(lastSingleUpMs, thisUpMs, windowMs);
    }

    public static float span(float x0, float y0, float x1, float y1) {
        float dx = x1 - x0, dy = y1 - y0;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    public static float pinchScale(float startSpan, float endSpan) {
        if (startSpan <= 1f) return 1f;
        return clamp(endSpan / startSpan, 0.4f, 3f);
    }

    /** After a pinch ends: true = large portrait + Chat, false = bubble. Unclear scale keeps the current mode. */
    public static boolean goBig(boolean wasBig, float scale) {
        if (scale >= PINCH_OUT) return true;
        if (scale <= PINCH_IN) return false;
        return wasBig;
    }

    /** Parent "Show Nova" off hides her completely. Double-tap also turns Show Nova off until the Nova tab turns her back on. */
    public static boolean showBubble(boolean parentShow, boolean hidden) {
        return parentShow && !hidden;
    }

    public static boolean showTab(boolean parentShow, boolean hidden) {
        return parentShow && hidden;
    }

    /**
     * 0.10.54: double-tap tucks her into the nearest corner (still her face, smaller).
     * Full hide is a separate action and uses the gold edge tab.
     */
    public static float[] tuckSnap(float x, float y, float size, float viewW, float viewH,
                                   float insetL, float insetT, float insetR, float insetB, float margin) {
        float minX = insetL + margin;
        float minY = insetT + margin;
        float maxX = viewW - insetR - margin - size;
        float maxY = viewH - insetB - margin - size;
        if (maxX < minX) maxX = minX;
        if (maxY < minY) maxY = minY;
        float destX = (x - minX) <= (maxX - x) ? minX : maxX;
        float destY = (y - minY) <= (maxY - y) ? minY : maxY;
        return new float[] { destX, destY };
    }

    public static boolean tuckedTop(float y, float size, float viewH, float insetT, float insetB, float margin) {
        return yFrac(y, size, viewH, insetT, insetB, margin) <= 0.5f;
    }
}

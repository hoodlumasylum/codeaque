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

    public static float clamp(float v, float lo, float hi) { return v < lo ? lo : (v > hi ? hi : v); }
}

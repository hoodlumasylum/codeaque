package com.codeaque.launcher;

/**
 * 0.10.61: adult (and Kids) home scroll stays smooth. Baking covers and
 * allocating glow shaders must not happen on the UI thread during a fling.
 * Plain Java so CatalogTest can check the rules.
 */
public final class HomeDrawGate {
    private HomeDrawGate() {}

    /** Never decode, trim, or round a cover on the draw / UI thread. */
    public static boolean bakeOnDrawThread() { return false; }

    /** Pause selection glows, embers, and other heavy chrome while the row is moving. */
    public static boolean drawHeavyFx(boolean rowBusy) { return !rowBusy; }

    /** A cache miss during a fling should draw a placeholder, not hitch to bake. */
    public static boolean canStartBake(boolean rowBusy, boolean alreadyQueued) {
        if (alreadyQueued) return false;
        return !rowBusy;
    }

    /** Keep starting a background bake once the row is still, even if it was skipped mid-fling. */
    public static boolean canStartBakeWhenIdle(boolean rowBusy, boolean alreadyQueued) {
        if (alreadyQueued) return false;
        return !rowBusy;
    }

    /** Stable tile size key so a resize of 1px does not thrash the LRU. */
    public static String tileKey(String itemKey, int tw, int th, int srcW, int srcH) {
        return (itemKey == null ? "?" : itemKey) + "@" + tw + "x" + th + "#" + srcW + "x" + srcH;
    }
}

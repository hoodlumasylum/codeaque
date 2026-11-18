package com.codeaque.launcher;

/**
 * Chat card rules so the reply cannot cover the input or the Mic on a short
 * landscape panel. Plain Java for CatalogTest.
 */
public final class NovaChatLayout {
    private NovaChatLayout() {}

    public static final int MIC_MIN_DP = 48;

    /** Reply sits in its own row above the input; it must never steal taps. */
    public static boolean replyAboveAskRow() { return true; }
    public static boolean replyInterceptsTouches() { return false; }

    public static int replyMaxLines(int panelHeightDp) {
        return panelHeightDp > 0 && panelHeightDp < 260 ? 2 : 4;
    }

    /**
     * Head + reserved chrome (reply, status, ask+Mic, meter, version, actions)
     * must leave some space, but the shrinking mid-scroll is what gives way.
     */
    public static int reservedChromeDp(int replyLines) {
        int reply = Math.max(1, replyLines) * 20 + 8;
        return 36 + reply + 16 + MIC_MIN_DP + 16 + 14 + MIC_MIN_DP;
    }

    public static boolean chromeFits(int panelHeightDp, int replyLines) {
        if (panelHeightDp <= 0) return true;
        return reservedChromeDp(replyLines) <= panelHeightDp;
    }
}

package com.codeaque.launcher;

/**
 * 0.10.51: how a cover is cropped to a tile. Trim transparent padding first, then centre-crop
 * so the opaque picture fills the tile exactly (no letterbox, no inset).
 */
public final class CoverFit {
    private CoverFit() { }

    /**
     * Inclusive-exclusive content box {left, top, right, bottom} after dropping columns/rows
     * whose pixels are all below {@code minAlpha}. If the whole image is transparent, returns the full image.
     */
    public static int[] contentBox(int[] px, int w, int h, int minAlpha) {
        if (px == null || w <= 0 || h <= 0 || px.length < w * h) return new int[]{0, 0, Math.max(0, w), Math.max(0, h)};
        int aMin = minAlpha < 0 ? 0 : minAlpha;
        int top = 0, bot = h, left = 0, right = w;
        while (top < h && rowClear(px, w, top, aMin)) top++;
        while (bot > top && rowClear(px, w, bot - 1, aMin)) bot--;
        while (left < w && colClear(px, w, h, left, top, bot, aMin)) left++;
        while (right > left && colClear(px, w, h, right - 1, top, bot, aMin)) right--;
        if (right - left < 1 || bot - top < 1) return new int[]{0, 0, w, h};
        return new int[]{left, top, right, bot};
    }

    private static boolean rowClear(int[] px, int w, int y, int minA) {
        int i = y * w, end = i + w;
        for (; i < end; i++) if (((px[i] >>> 24) & 0xFF) >= minA) return false;
        return true;
    }
    private static boolean colClear(int[] px, int w, int h, int x, int top, int bot, int minA) {
        for (int y = top; y < bot; y++) if (((px[y * w + x] >>> 24) & 0xFF) >= minA) return false;
        return true;
    }

    /**
     * Source rectangle (left, top, right, bottom) that centre-crops {@code sw x sh} to fill {@code dw x dh}.
     * Uses Math.max scale (cover), never contain/letterbox. Degenerate sizes fill the dest from (0,0).
     */
    public static int[] coverSrc(int sw, int sh, int dw, int dh) {
        if (sw <= 0 || sh <= 0) return new int[]{0, 0, Math.max(1, sw), Math.max(1, sh)};
        if (dw <= 0 || dh <= 0) return new int[]{0, 0, sw, sh};
        // scale = max(dw/sw, dh/sh); src size = dest / scale
        long srcW, srcH;
        if ((long) dw * sh >= (long) dh * sw) { // dw/sw >= dh/sh: fill width, crop height
            srcW = sw;
            srcH = (long) sw * dh / dw;
            if (srcH < 1) srcH = 1;
            if (srcH > sh) srcH = sh;
        } else {
            srcH = sh;
            srcW = (long) sh * dw / dh;
            if (srcW < 1) srcW = 1;
            if (srcW > sw) srcW = sw;
        }
        int left = (int) ((sw - srcW) / 2);
        int top = (int) ((sh - srcH) / 2);
        return new int[]{left, top, left + (int) srcW, top + (int) srcH};
    }

    /** Combine trim + cover crop: box is in the original image. */
    public static int[] coverSrcTrimmed(int[] px, int w, int h, int dw, int dh, int minAlpha) {
        int[] box = contentBox(px, w, h, minAlpha);
        int[] src = coverSrc(box[2] - box[0], box[3] - box[1], dw, dh);
        return new int[]{box[0] + src[0], box[1] + src[1], box[0] + src[2], box[1] + src[3]};
    }
}

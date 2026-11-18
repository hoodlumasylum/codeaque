package com.codeaque.launcher;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;

/**
 * 0.10.61: bake a cover off the UI thread (downsample / trim / round / fade).
 * Named top-level class for d8 8.2.
 */
public final class HomeTileBake implements Runnable {
    final HomeView view;
    final String key;
    final Bitmap cov;
    final int tw, th;
    final float rad;

    public HomeTileBake(HomeView view, String key, Bitmap cov, int tw, int th, float rad) {
        this.view = view;
        this.key = key;
        this.cov = cov;
        this.tw = tw;
        this.th = th;
        this.rad = rad;
    }

    @Override public void run() {
        Bitmap out = null;
        try { out = bake(cov, tw, th, rad); } catch (Throwable ignored) { }
        if (view != null) view.post(new HomeTileReady(view, key, out));
    }

    /** Opaque, centre-cropped, rounded tile at the exact size. Background thread only. */
    public static Bitmap bake(Bitmap cov, int tw, int th, float rad) {
        if (cov == null || cov.isRecycled() || tw < 2 || th < 2) return null;
        Bitmap out = Bitmap.createBitmap(tw, th, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        int sw = cov.getWidth(), sh = cov.getHeight();
        int[] box;
        if (cov.hasAlpha()) {
            int[] px = new int[sw * sh];
            cov.getPixels(px, 0, sw, 0, 0, sw, sh);
            box = CoverFit.coverSrcTrimmed(px, sw, sh, tw, th, 16);
        } else {
            box = CoverFit.coverSrc(sw, sh, tw, th);
        }
        if (box[2] <= box[0] || box[3] <= box[1]) {
            box = new int[]{0, 0, sw, sh};
        }
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        c.drawColor(0xFF101418);
        Rect src = new Rect(box[0], box[1], box[2], box[3]);
        RectF dst = new RectF(-1, -1, tw + 1, th + 1);
        c.drawBitmap(cov, src, dst, paint);
        paint.setShader(new LinearGradient(0, th * 0.52f, 0, th, 0x00000000, 0xF2000000, Shader.TileMode.CLAMP));
        c.drawRect(0, th * 0.52f, tw, th, paint);
        paint.setShader(null);
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
        paint.setColor(0xFFFFFFFF);
        c.drawRoundRect(new RectF(0, 0, tw, th), rad, rad, paint);
        paint.setXfermode(null);
        return out;
    }
}

final class HomeTileReady implements Runnable {
    final HomeView view;
    final String key;
    final Bitmap bmp;
    HomeTileReady(HomeView view, String key, Bitmap bmp) {
        this.view = view;
        this.key = key;
        this.bmp = bmp;
    }
    @Override public void run() {
        if (view != null) view.tileBaked(key, bmp);
    }
}

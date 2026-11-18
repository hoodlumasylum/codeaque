package com.codeaque.launcher;

import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;

import java.io.InputStream;

/**
 * Draws Nova's original portrait with a light Canvas eye/blink/breath pass.
 * Layout numbers match /workspace/nova face_layout.py (normalized on the 1024 portrait).
 */
public final class NovaFace {
    public static final String ASSET = "nova-face.jpg";
    // Lid boxes (include the lash margin).
    public static final float LLX = 0.345f, LLY = 0.358f, LLW = 0.120f, LLH = 0.075f;
    public static final float RLX = 0.516f, RLY = 0.336f, RLW = 0.132f, RLH = 0.068f;
    // Visible eyeball, inside the lashes, used to shift the real iris.
    public static final float LEX = 0.352f, LEY = 0.370f, LEW = 0.108f, LEH = 0.052f;
    public static final float REX = 0.518f, REY = 0.344f, REW = 0.124f, REH = 0.054f;
    public static final float FX = 0.18f, FY = 0.02f, FW = 0.66f, FH = 0.78f;
    static final int FALLBACK_LID = 0xFFB08978;

    final Paint bmp = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    final Paint lid = new Paint(Paint.ANTI_ALIAS_FLAG);
    final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    final Paint shadow = new Paint(Paint.ANTI_ALIAS_FLAG);
    final Paint clip = new Paint(Paint.ANTI_ALIAS_FLAG);
    final Rect src = new Rect();
    final RectF dst = new RectF(), eye = new RectF(), tmp = new RectF();
    final Path almond = new Path();
    final Matrix gazeM = new Matrix();
    Bitmap portrait;
    int lidColor = FALLBACK_LID;
    int srcW, srcH;

    public boolean load(AssetManager am) {
        if (portrait != null && !portrait.isRecycled()) return true;
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inPreferredConfig = Bitmap.Config.ARGB_8888;
        o.inSampleSize = 2;   // 512px is enough for the bubble and the panel face
        try (InputStream in = am.open(ASSET)) {
            portrait = BitmapFactory.decodeStream(in, null, o);
        } catch (Exception e) {
            portrait = null;
        }
        if (portrait == null) return false;
        srcW = portrait.getWidth();
        srcH = portrait.getHeight();
        lidColor = sampleLid();
        lid.setColor(lidColor);
        lid.setStyle(Paint.Style.FILL);
        ring.setStyle(Paint.Style.STROKE);
        ring.setColor(0xFFE8C872);
        shadow.setColor(0x66000000);
        shadow.setStyle(Paint.Style.STROKE);
        return true;
    }

    public void recycle() {
        if (portrait != null && !portrait.isRecycled()) portrait.recycle();
        portrait = null;
    }

    public boolean ready() { return portrait != null && !portrait.isRecycled(); }

    /** dest is the circle (or rounded square) that shows the head crop. */
    public void draw(Canvas c, RectF dest, NovaAnim.State st, float ringDp, boolean circle) {
        if (dest == null || dest.width() <= 0) return;
        float bob = st == null ? 0 : st.bob * dest.height();
        dst.set(dest);
        dst.offset(0, bob);
        int save = c.save();
        if (circle) {
            almond.reset();
            almond.addOval(dst, Path.Direction.CW);
            c.clipPath(almond);
        }
        if (ready()) {
            focusSrc(src);
            c.drawBitmap(portrait, src, dst, bmp);
            float gx = st == null ? 0 : st.gazeX;
            float gy = st == null ? 0 : st.gazeY;
            float blink = st == null ? 0 : st.blink;
            shiftEye(c, LEX, LEY, LEW, LEH, gx, gy);
            shiftEye(c, REX, REY, REW, REH, gx, gy);
            if (blink > 0.01f) {
                drawLids(c, LEX, LEY, LEW, LEH, blink);
                drawLids(c, REX, REY, REW, REH, blink);
            }
        } else {
            Paint p = clip;
            p.setColor(0xFF3A2A28);
            p.setStyle(Paint.Style.FILL);
            c.drawOval(dst, p);
            p.setColor(0xFFE8C872);
            p.setTextAlign(Paint.Align.CENTER);
            p.setTextSize(Math.max(12, dest.width() * 0.22f));
            c.drawText("Nova", dst.centerX(), dst.centerY() + p.getTextSize() * 0.35f, p);
        }
        c.restoreToCount(save);
        if (ringDp > 0) {
            shadow.setStrokeWidth(ringDp * 1.6f);
            c.drawOval(dest, shadow);
            ring.setStrokeWidth(ringDp);
            c.drawOval(dest, ring);
        }
    }

    void focusSrc(Rect out) {
        out.set(Math.round(FX * srcW), Math.round(FY * srcH),
                Math.round((FX + FW) * srcW), Math.round((FY + FH) * srcH));
    }

    void mapEye(float nx, float ny, float nw, float nh, RectF out) {
        // Eye boxes are in full-portrait space; dest shows only BUBBLE_FOCUS.
        float x = (nx - FX) / FW;
        float y = (ny - FY) / FH;
        out.set(dst.left + x * dst.width(), dst.top + y * dst.height(),
                dst.left + (x + nw / FW) * dst.width(), dst.top + (y + nh / FH) * dst.height());
    }

    void shiftEye(Canvas c, float nx, float ny, float nw, float nh, float gx, float gy) {
        if (!ready()) return;
        mapEye(nx, ny, nw, nh, eye);
        if (eye.width() < 2) return;
        int s = c.save();
        almond.reset();
        almond.addOval(eye, Path.Direction.CW);
        c.clipPath(almond);
        // Shift the iris a little, still inside the socket.
        float ox = gx * eye.width() * 0.16f;
        float oy = gy * eye.height() * 0.18f;
        tmp.set(dst);
        tmp.offset(ox, oy);
        c.drawBitmap(portrait, src, tmp, bmp);
        c.restoreToCount(s);
    }

    void drawLids(Canvas c, float nx, float ny, float nw, float nh, float blink) {
        mapEye(nx, ny, nw, nh, eye);
        int s = c.save();
        almond.reset();
        almond.addOval(eye, Path.Direction.CW);
        c.clipPath(almond);
        // Meet in the middle and fully cover the iris when blink == 1
        // (upper 0.62 + lower 0.45 overlaps so the right eye cannot leave a sliver).
        float mid = eye.top + eye.height() * 0.55f;
        float up = eye.top + (mid - eye.top) * blink * 1.08f;
        float lo = eye.bottom - (eye.bottom - mid) * blink * 1.08f;
        if (up > lo) { float m = (up + lo) * 0.5f; up = m; lo = m; }
        c.drawRect(eye.left - 2, eye.top - 2, eye.right + 2, up, lid);
        c.drawRect(eye.left - 2, lo, eye.right + 2, eye.bottom + 2, lid);
        c.restoreToCount(s);
    }

    int sampleLid() {
        try {
            int x = clampI(Math.round((LEX + LEW * 0.5f) * srcW), 0, srcW - 1);
            int y = clampI(Math.round((LEY - 0.01f) * srcH), 0, srcH - 1);
            int c = portrait.getPixel(x, y);
            int r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
            if (r + g + b < 120) return FALLBACK_LID;   // landed on a lash
            return 0xFF000000 | (c & 0x00FFFFFF);
        } catch (Exception e) { return FALLBACK_LID; }
    }

    static int clampI(int v, int lo, int hi) { return v < lo ? lo : (v > hi ? hi : v); }
}

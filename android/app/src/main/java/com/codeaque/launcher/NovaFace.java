package com.codeaque.launcher;

import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
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
    /** 0.10.54: studio backdrop keyed out, soft feathered edge. Same 1024 layout as ASSET. */
    public static final String ASSET_CUTOUT = "nova-face-cutout.png";
    public static final String[] MOUTH_ASSETS = {
            "nova-mouth-1.png", "nova-mouth-2.png", "nova-mouth-3.png"
    };
    /** Overlay box for the pre-rendered mouth frames (slightly larger than MOUTH so the full lower lip is inside). */
    public static final float OX = 0.400f, OY = 0.470f, OW = 0.235f, OH = 0.125f;
    // Lid boxes (include the lash margin).
    public static final float LLX = 0.345f, LLY = 0.358f, LLW = 0.120f, LLH = 0.075f;
    public static final float RLX = 0.516f, RLY = 0.336f, RLW = 0.132f, RLH = 0.068f;
    // Visible eyeball, inside the lashes, used to shift the real iris.
    public static final float LEX = 0.352f, LEY = 0.370f, LEW = 0.108f, LEH = 0.052f;
    public static final float REX = 0.518f, REY = 0.344f, REW = 0.124f, REH = 0.054f;
    public static final float FX = 0.18f, FY = 0.02f, FW = 0.66f, FH = 0.78f;
    // Closed-lips box on the 1024 portrait (face_layout.MOUTH), used to lip-sync.
    public static final float MX = 0.418f, MY = 0.488f, MW = 0.195f, MH = 0.070f;
    static final int FALLBACK_LID = 0xFFB08978;

    final Paint bmp = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    final Paint lid = new Paint(Paint.ANTI_ALIAS_FLAG);
    final Paint mouthPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    final Paint shadow = new Paint(Paint.ANTI_ALIAS_FLAG);
    final Paint clip = new Paint(Paint.ANTI_ALIAS_FLAG);
    final Rect src = new Rect();
    final RectF dst = new RectF(), eye = new RectF(), tmp = new RectF();
    final Path almond = new Path();
    final Matrix gazeM = new Matrix();
    Bitmap portrait;
    Bitmap[] mouthFrames;
    int lidColor = FALLBACK_LID;
    int srcW, srcH;

    public boolean load(AssetManager am) {
        if (portrait != null && !portrait.isRecycled()) return true;
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inPreferredConfig = Bitmap.Config.ARGB_8888;
        o.inSampleSize = 1;   // full 1024: the large (pinch-out) portrait is big enough to show softening
        portrait = openBitmap(am, ASSET_CUTOUT, o);
        if (portrait == null) portrait = openBitmap(am, ASSET, o);
        if (portrait == null) return false;
        srcW = portrait.getWidth();
        srcH = portrait.getHeight();
        loadMouth(am);
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
        if (mouthFrames != null) {
            for (int i = 0; i < mouthFrames.length; i++) {
                if (mouthFrames[i] != null && !mouthFrames[i].isRecycled()) mouthFrames[i].recycle();
                mouthFrames[i] = null;
            }
            mouthFrames = null;
        }
    }

    public boolean ready() { return portrait != null && !portrait.isRecycled(); }

    /** dest is the box that shows the head crop. 0.10.54: no circular clip or gold ring; a soft drop shadow sits under the cut-out. ringDp/circle are ignored (kept so callers compile). */
    public void draw(Canvas c, RectF dest, NovaAnim.State st, float ringDp, boolean circle) {
        if (dest == null || dest.width() <= 0) return;
        float bob = st == null ? 0 : st.bob * dest.height();
        dst.set(dest);
        dst.offset(0, bob);
        int save = c.save();
        if (ready()) {
            focusSrc(src);
            // Soft shadow: the cut-out itself, tinted dark and offset a little.
            shadow.setColorFilter(new PorterDuffColorFilter(0x66000000, PorterDuff.Mode.SRC_IN));
            tmp.set(dst);
            tmp.offset(dst.width() * 0.03f, dst.height() * 0.04f);
            c.drawBitmap(portrait, src, tmp, shadow);
            shadow.setColorFilter((ColorFilter) null);
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
            float mouth = st == null ? 0 : st.mouth;
            if (mouth > 0.04f) drawMouth(c, mouth);
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
    }

    static Bitmap openBitmap(AssetManager am, String name, BitmapFactory.Options o) {
        try (InputStream in = am.open(name)) {
            return BitmapFactory.decodeStream(in, null, o);
        } catch (Exception e) {
            return null;
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

    void loadMouth(AssetManager am) {
        mouthFrames = new Bitmap[MOUTH_ASSETS.length];
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inPreferredConfig = Bitmap.Config.ARGB_8888;
        for (int i = 0; i < MOUTH_ASSETS.length; i++) {
            try (InputStream in = am.open(MOUTH_ASSETS[i])) {
                mouthFrames[i] = BitmapFactory.decodeStream(in, null, o);
            } catch (Exception e) {
                mouthFrames[i] = null;
            }
        }
    }

    /**
     * Cross-fade the pre-rendered mouth frames (from her own portrait) while she talks.
     * open 0 = rest photo, ~0.33 = slightly parted, ~0.66 = mid, 1 = more open.
     */
    void drawMouth(Canvas c, float open) {
        if (mouthFrames == null) return;
        float o = open < 0 ? 0 : (open > 1 ? 1 : open);
        if (o <= 0.04f) return;
        mapEye(OX, OY, OW, OH, eye);
        if (eye.width() < 4) return;
        int a, b;
        float t;
        if (o < 0.33f) {
            a = 0; b = 0;
            t = (o - 0.04f) / 0.29f;
        } else if (o < 0.66f) {
            a = 0; b = 1;
            t = (o - 0.33f) / 0.33f;
        } else {
            a = 1; b = 2;
            t = (o - 0.66f) / 0.34f;
        }
        if (t < 0) t = 0;
        if (t > 1) t = 1;
        if (a == b) {
            blitMouth(c, a, Math.round(255 * t));
        } else {
            blitMouth(c, a, 255);
            if (t > 0.02f) blitMouth(c, b, Math.round(255 * t));
        }
    }

    void blitMouth(Canvas c, int index, int alpha) {
        if (mouthFrames == null || index < 0 || index >= mouthFrames.length) return;
        Bitmap bm = mouthFrames[index];
        if (bm == null || bm.isRecycled() || alpha <= 0) return;
        mouthPaint.setAlpha(alpha);
        c.drawBitmap(bm, null, eye, mouthPaint);
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

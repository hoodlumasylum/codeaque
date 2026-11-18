package com.codeaque.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/** On-screen mic level bar so a screenshot can show whether the mic is open. */
public final class NovaMicMeter extends View {
    final Paint back = new Paint(Paint.ANTI_ALIAS_FLAG);
    final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    final Paint border;
    final RectF rect = new RectF();
    float level;

    public NovaMicMeter(Context c) { this(c, null); }
    public NovaMicMeter(Context c, AttributeSet a) {
        super(c, a);
        back.setColor(0xFF4A5562);
        fill.setColor(0xFFE8C872);
        border = new Paint(Paint.ANTI_ALIAS_FLAG);
        border.setStyle(Paint.Style.STROKE);
        border.setColor(0xCCE8C872);
        border.setStrokeWidth(Math.max(1f, dp(1)));
        setMinimumHeight(dp(16));
    }

    int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    public void setLevel(float v) {
        if (v < 0f) v = 0f;
        if (v > 1f) v = 1f;
        if (Math.abs(v - level) < 0.02f && v < 0.98f) return;
        level = v;
        invalidate();
    }

    public float getLevel() { return level; }

    @Override protected void onMeasure(int wSpec, int hSpec) {
        int w = MeasureSpec.getSize(wSpec);
        int h = dp(16);
        if (MeasureSpec.getMode(hSpec) == MeasureSpec.EXACTLY) h = MeasureSpec.getSize(hSpec);
        else if (MeasureSpec.getMode(hSpec) == MeasureSpec.AT_MOST) h = Math.min(h, MeasureSpec.getSize(hSpec));
        setMeasuredDimension(w, h);
    }

    @Override protected void onDraw(Canvas c) {
        int pad = dp(1);
        rect.set(pad, pad, getWidth() - pad, getHeight() - pad);
        c.drawRoundRect(rect, dp(3), dp(3), back);
        float w = rect.width() * level;
        if (w > 1f) {
            android.graphics.RectF fillRect = new android.graphics.RectF(rect.left, rect.top, rect.left + w, rect.bottom);
            c.drawRoundRect(fillRect, dp(3), dp(3), fill);
        }
        if (border != null) c.drawRoundRect(rect, dp(3), dp(3), border);
    }
}

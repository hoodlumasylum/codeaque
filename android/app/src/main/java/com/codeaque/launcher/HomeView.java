package com.codeaque.launcher;

import android.content.Context;
import android.graphics.*;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** The whole home screen, drawn on one Canvas: cheap on a 2 GB RK3566 (no layouts, a handful of
 *  paths per frame, 30 fps on "auto"). Gamepad: D-pad / stick, L1 R1 change category, A open,
 *  B back, Y settings. Touch: tap a category or a tile, tap the selected tile again to open it, swipe the tiles. */
public class HomeView extends View {
    public interface Host {
        void open(Catalog.Item item);
        boolean installed(Catalog.Item item);
        void sound(String name);
        String effects();
        String status();      // "87%" or "87% charging", "" without a battery
        boolean online();
    }

    private static final int GOLD = 0xFFE6C35C, GOLD_D = 0xFF9C7A22, GREEN = 0xFF2BE37A, GREEN_D = 0xFF0B3B22, TXT = 0xFFEAF5EE, DIM = 0xFF8FA89A;
    private final Host host;
    private final List<Catalog.Category> cats = Catalog.categories();
    private int cat = 0, tile = 1;       // Games, focused on the first real tile after "Cloud Gaming"? start on Cloud Gaming:
    private boolean inCats = false;
    private float catAnim = 0, scrollAnim = 0, t = 0;
    private long last = 0, lastFrame = 0;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG), stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private Bitmap ankh, ankhSmall;
    private final RectF r = new RectF(), iconRect = new RectF();
    private Catalog.Item prompt;          // "not installed" panel
    private final float[] motes = new float[36];
    private final SimpleDateFormat clock = new SimpleDateFormat("EEE d MMM  HH:mm", Locale.getDefault());
    private float downX, downY, s = 1; private boolean dragging; private float dragTiles;

    public HomeView(Context c, Host host) {
        super(c);
        this.host = host;
        tile = 0;
        setFocusable(true); setFocusableInTouchMode(true);
        try {
            ankh = BitmapFactory.decodeStream(c.getAssets().open("hh_ankh.png"));
            ankhSmall = BitmapFactory.decodeStream(c.getAssets().open("hh_ankh_gold.png"));
        } catch (Exception ignored) { }
        java.util.Random rnd = new java.util.Random(7);
        for (int i = 0; i < motes.length; i++) motes[i] = rnd.nextFloat();
        stroke.setStyle(Paint.Style.STROKE); stroke.setStrokeCap(Paint.Cap.ROUND); stroke.setStrokeJoin(Paint.Join.ROUND);
    }

    // ---------- input ----------
    private List<Catalog.Item> items() { return cats.get(cat).items; }

    private void setCat(int c) {
        c = (c + cats.size()) % cats.size();
        if (c != cat) { cat = c; tile = 0; host.sound("hhcat"); }
        invalidate();
    }
    private void setTile(int i) {
        i = Math.max(0, Math.min(items().size() - 1, i));
        if (i != tile) { tile = i; host.sound("hhmove"); }
        invalidate();
    }
    private void activate() {
        Catalog.Item it = items().get(tile);
        host.sound("hhselect");
        if (it.action != null && it.action.startsWith("goto:")) { setCat(Catalog.indexOf(cats, it.action.substring(5))); return; }
        if (!it.isAction() && it.webUrl == null && !host.installed(it)) { prompt = it; invalidate(); return; }
        host.open(it);
    }

    @Override public boolean onKeyDown(int code, KeyEvent e) {
        if (prompt != null) {
            if (code == KeyEvent.KEYCODE_BUTTON_A || code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_DPAD_CENTER) { Catalog.Item it = prompt; prompt = null; host.open(it); }
            else if (code == KeyEvent.KEYCODE_BUTTON_B || code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE) { prompt = null; host.sound("hhback"); }
            invalidate(); return true;
        }
        switch (code) {
            case KeyEvent.KEYCODE_BUTTON_L1: case KeyEvent.KEYCODE_PAGE_UP: case KeyEvent.KEYCODE_Q: setCat(cat - 1); return true;
            case KeyEvent.KEYCODE_BUTTON_R1: case KeyEvent.KEYCODE_PAGE_DOWN: case KeyEvent.KEYCODE_E: setCat(cat + 1); return true;
            case KeyEvent.KEYCODE_DPAD_LEFT: if (inCats) setCat(cat - 1); else setTile(tile - 1); return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT: if (inCats) setCat(cat + 1); else setTile(tile + 1); return true;
            case KeyEvent.KEYCODE_DPAD_UP: if (!inCats) { inCats = true; host.sound("hhmove"); invalidate(); } return true;
            case KeyEvent.KEYCODE_DPAD_DOWN: if (inCats) { inCats = false; host.sound("hhmove"); invalidate(); } return true;
            case KeyEvent.KEYCODE_BUTTON_A: case KeyEvent.KEYCODE_ENTER: case KeyEvent.KEYCODE_DPAD_CENTER: case KeyEvent.KEYCODE_BUTTON_START:
                if (inCats) { inCats = false; host.sound("hhselect"); invalidate(); } else activate(); return true;
            case KeyEvent.KEYCODE_BUTTON_B: case KeyEvent.KEYCODE_BACK: case KeyEvent.KEYCODE_ESCAPE:
                if (!inCats) inCats = true; else setCat(0);
                host.sound("hhback"); invalidate(); return true;    // a home screen never closes
            case KeyEvent.KEYCODE_BUTTON_Y: case KeyEvent.KEYCODE_MENU: case KeyEvent.KEYCODE_BUTTON_SELECT:
                setCat(Catalog.indexOf(cats, "settings")); inCats = false; return true;
        }
        return super.onKeyDown(code, e);
    }

    @Override public boolean onGenericMotionEvent(MotionEvent e) {   // left stick / hat as a D-pad, with repeat delay
        if ((e.getSource() & android.view.InputDevice.SOURCE_JOYSTICK) != 0 && e.getAction() == MotionEvent.ACTION_MOVE) {
            float x = e.getAxisValue(MotionEvent.AXIS_X) + e.getAxisValue(MotionEvent.AXIS_HAT_X);
            float y = e.getAxisValue(MotionEvent.AXIS_Y) + e.getAxisValue(MotionEvent.AXIS_HAT_Y);
            long now = android.os.SystemClock.uptimeMillis();
            if (Math.abs(x) < 0.5f && Math.abs(y) < 0.5f) { last = 0; return true; }
            if (now - last < 220) return true;
            last = now;
            int k = Math.abs(x) > Math.abs(y) ? (x > 0 ? KeyEvent.KEYCODE_DPAD_RIGHT : KeyEvent.KEYCODE_DPAD_LEFT)
                                               : (y > 0 ? KeyEvent.KEYCODE_DPAD_DOWN : KeyEvent.KEYCODE_DPAD_UP);
            return onKeyDown(k, new KeyEvent(KeyEvent.ACTION_DOWN, k));
        }
        return super.onGenericMotionEvent(e);
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        float x = e.getX(), y = e.getY(), H = getHeight();
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: downX = x; downY = y; dragging = false; dragTiles = 0; return true;
            case MotionEvent.ACTION_MOVE:
                if (Math.abs(x - downX) > 24 * s && y > tilesTop()) {
                    dragging = true;
                    float step = tileW() + tileGap();
                    int moved = (int) ((downX - x) / step);
                    if (moved != (int) dragTiles) { setTile(tile + moved - (int) dragTiles); dragTiles = moved; inCats = false; }
                }
                return true;
            case MotionEvent.ACTION_UP:
                if (dragging) return true;
                if (prompt != null) {
                    Catalog.Item it = prompt; prompt = null;
                    if (y > H * 0.5f && x > getWidth() * 0.5f - 200 * s && x < getWidth() * 0.5f) host.open(it); else host.sound("hhback");
                    invalidate(); return true;
                }
                if (y < catsBottom() && y > H * 0.12f) {
                    float cw = getWidth() / (float) cats.size();
                    int c = (int) (x / cw);
                    setCat(c); inCats = false; return true;
                }
                if (y > tilesTop() && y < tilesTop() + tileH()) {
                    float step = tileW() + tileGap();
                    int i = (int) Math.floor((x - tilesLeft() + scrollAnim * step) / step);
                    if (i >= 0 && i < items().size()) {
                        inCats = false;
                        if (i == tile) activate(); else setTile(i);
                    }
                    return true;
                }
                return true;
        }
        return super.onTouchEvent(e);
    }

    public void showCloud() { setCat(Catalog.indexOf(cats, "cloud")); tile = 0; inCats = false; invalidate(); }
    public void focus(String catId, int i) { cat = Math.max(0, Catalog.indexOf(cats, catId)); tile = i; catAnim = cat; inCats = false; invalidate(); }

    // ---------- geometry (scaled from the 1280x720 design) ----------
    private float catsBottom() { return getHeight() * 0.26f; }
    private float tileH() { return 112 * s; }
    private float tileW() { return 156 * s; }
    private float tileGap() { return 14 * s; }
    private float tilesTop() { return getHeight() - 80 * s - tileH(); }
    private float tilesLeft() { return 24 * s; }

    // ---------- drawing ----------
    @Override protected void onDraw(Canvas c) {
        int W = getWidth(), H = getHeight();
        s = Math.min(W / 1000f, H / 720f);   // 1 on 1280x720, bigger than a plain scale on 4:3 screens (640x480)
        String fx = host.effects();
        boolean anim = !"off".equals(fx);
        long now = System.nanoTime();
        float dt = lastFrame == 0 ? 0.016f : Math.min(0.1f, (now - lastFrame) / 1e9f);
        lastFrame = now;
        if (anim) t += dt;
        float k = anim ? Math.min(1, dt * 12) : 1;          // easing towards the target
        catAnim += (cat - catAnim) * k;
        float vis = Math.max(1, (int) ((W - 2 * tilesLeft()) / (tileW() + tileGap())));
        float target = Math.max(0, Math.min(tile - (vis - 1) / 2f, items().size() - vis));
        target = Math.max(0, Math.round(target));
        if (Math.abs(target - scrollAnim) > 3) scrollAnim = target;
        scrollAnim += (target - scrollAnim) * k;

        background(c, W, H, anim);
        topBar(c, W, H);
        categories(c, W, H);
        if (cats.get(cat).id.equals("games") || cats.get(cat).id.equals("cloud") || H / (float) W < 0.7f || true) mark(c, W, H);
        tiles(c, W, H);
        hints(c, W, H);
        if (prompt != null) prompt(c, W, H);

        boolean settling = Math.abs(cat - catAnim) > 0.002f || Math.abs(target - scrollAnim) > 0.002f;
        if (anim || settling) {
            if ("on".equals(fx) || settling) postInvalidateOnAnimation();
            else postInvalidateDelayed(33);                 // auto: 30 fps background on weak chips
        }
    }

    private void background(Canvas c, int W, int H, boolean anim) {
        c.drawColor(0xFF000000);
        p.setShader(new RadialGradient(W * 0.5f, H * 0.48f, W * 0.6f, 0xFF06301A, 0xFF000000, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, W, H, p); p.setShader(null);
        // flowing waves: a few sine curves, green with one gold thread
        int[] cols = {0x6622E070, 0x4418B860, 0x3330FF90, 0x88E6C35C, 0x3322E070};
        float[] amp = {60, 40, 80, 30, 55}, fr = {1.2f, 1.9f, 0.9f, 1.5f, 2.4f}, sp = {0.35f, -0.25f, 0.2f, 0.3f, -0.4f}, wd = {3, 2, 1.5f, 1.4f, 1.2f};
        for (int w = 0; w < cols.length; w++) {
            path.reset();
            for (int i = 0; i <= 48; i++) {
                float x = W * i / 48f, u = i / 48f;
                float y = H * 0.47f + s * amp[w] * (float) Math.sin(u * Math.PI * 2 * fr[w] + t * sp[w] * 3 + w)
                        * (0.6f + 0.4f * (float) Math.sin(u * Math.PI));
                if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
            }
            stroke.setColor(cols[w]); stroke.setStrokeWidth(wd[w] * s * 1.5f);
            c.drawPath(path, stroke);
        }
        p.setColor(0xFF7DFFB0);
        for (int i = 0; i < motes.length; i += 3) {
            float x = (motes[i] * W + t * 12 * s * (0.5f + motes[i + 1])) % W;
            float y = H * (0.3f + 0.4f * motes[i + 1]) + (float) Math.sin(t + i) * 10 * s;
            p.setAlpha((int) (60 + 120 * motes[i + 2]));
            c.drawCircle(x, y, (1 + 1.5f * motes[i + 2]) * s, p);
        }
        p.setAlpha(255);
    }

    private void goldText(Canvas c, String txt, float x, float y, float size, Paint.Align a, boolean bold) {
        p.setTextSize(size); p.setTextAlign(a); p.setFakeBoldText(bold);
        p.setTypeface(Typeface.create("sans-serif-black", Typeface.NORMAL));
        p.setShader(new LinearGradient(0, y - size, 0, y, new int[]{0xFFFFF1B8, GOLD, GOLD_D, 0xFFF5D77A}, null, Shader.TileMode.CLAMP));
        c.drawText(txt, x, y, p); p.setShader(null); p.setFakeBoldText(false); p.setTypeface(Typeface.DEFAULT);
    }

    private void text(Canvas c, String txt, float x, float y, float size, int col, Paint.Align a, boolean bold) {
        p.setTextSize(size); p.setTextAlign(a); p.setColor(col);
        p.setTypeface(bold ? Typeface.create("sans-serif", Typeface.BOLD) : Typeface.create("sans-serif", Typeface.NORMAL));
        c.drawText(txt, x, y, p); p.setTypeface(Typeface.DEFAULT);
    }

    private void spaced(Canvas c, String txt, float x, float y, float size, int col, Paint.Align a) {
        p.setLetterSpacing(0.25f); text(c, txt, x, y, size, col, a, false); p.setLetterSpacing(0);
    }

    private void topBar(Canvas c, int W, int H) {
        if (ankhSmall != null) { r.set(18 * s, 12 * s, 98 * s, 44 * s); c.drawBitmap(ankhSmall, null, r, p); }
        goldText(c, "CODEAQUE", 108 * s, 38 * s, 30 * s, Paint.Align.LEFT, false);
        spaced(c, "PORTABLE GAMING SYSTEM", 110 * s, 58 * s, 11 * s, 0xFFB8C8BE, Paint.Align.LEFT);
        String st = host.status();
        String right = clock.format(new Date()) + (st.isEmpty() ? "" : "   " + st);
        text(c, right, W - 24 * s, 36 * s, 20 * s, TXT, Paint.Align.RIGHT, false);
        p.setTextSize(20 * s);
        float wx = W - 24 * s - p.measureText(right) - 30 * s;
        icon(c, "wifi", wx, 29 * s, 22 * s, host.online() ? TXT : 0xFF555555, 2f);
    }

    private void categories(Canvas c, int W, int H) {
        float cw = W / (float) cats.size(), y = H * 0.17f;
        for (int i = 0; i < cats.size(); i++) {
            float x = cw * (i + 0.5f);
            float sel = Math.max(0, 1 - Math.abs(catAnim - i));
            float size = (40 + 12 * sel) * s;
            if (sel > 0.05f) {
                p.setShader(new RadialGradient(x, y, 60 * s, (((int) (0x55 * sel)) << 24) | 0x2BE37A, 0x002BE37A, Shader.TileMode.CLAMP));
                c.drawCircle(x, y, 60 * s, p); p.setShader(null);
            }
            int col = i == cat ? (inCats ? GOLD : 0xFF6CFFA8) : 0xFFDDE7E1;
            icon(c, cats.get(i).icon, x, y, size, col, i == cat ? 2.6f : 2.2f);
            text(c, cats.get(i).label, x, y + size * 0.5f + (i == cat ? 32 : 26) * s, (i == cat ? 24 : 18) * s, i == cat ? 0xFFFFFFFF : 0xFFC9D6CF, Paint.Align.CENTER, i == cat);
        }
    }

    private void mark(Canvas c, int W, int H) {
        float cx = W / 2f, top = H * 0.30f;
        float aw = 420 * s, ah = aw * 0.4f;
        if (ankh != null) { r.set(cx - aw / 2, top, cx + aw / 2, top + ah); c.drawBitmap(ankh, null, r, p); }
        float ty = top + ah + 64 * s, size = 80 * s;
        c.save(); c.scale(1f, 0.32f, cx, ty - size * 0.35f);
        p.setShader(new RadialGradient(cx, ty - size * 0.35f, 400 * s, 0x6622E070, 0x0022E070, Shader.TileMode.CLAMP));
        c.drawCircle(cx, ty - size * 0.35f, 400 * s, p); p.setShader(null); c.restore();
        p.setTextSize(size); p.setTextAlign(Paint.Align.CENTER); p.setLetterSpacing(0.06f);
        p.setTypeface(Typeface.create("sans-serif-black", Typeface.BOLD));
        p.setShader(new LinearGradient(0, ty - size * 0.75f, 0, ty, new int[]{0xFFFFFFFF, 0xFFC9D1D6, 0xFF6E7A82, 0xFFE9EEF2, 0xFF9AA4AA},
                new float[]{0, 0.4f, 0.55f, 0.75f, 1}, Shader.TileMode.CLAMP));
        c.drawText("CODEAQUE", cx, ty, p);
        p.setShader(null); p.setLetterSpacing(0); p.setTypeface(Typeface.DEFAULT);
        spaced(c, "GAMING  •  CLOUD  •  EMULATION  •  STREAMING", cx, ty + 30 * s, 15 * s, 0xFFCFE0D6, Paint.Align.CENTER);
    }

    private void tiles(Canvas c, int W, int H) {
        List<Catalog.Item> its = items();
        float top = tilesTop(), w = tileW(), h = tileH(), gap = tileGap(), left = tilesLeft();
        for (int i = 0; i < its.size(); i++) {
            float x = left + (i - scrollAnim) * (w + gap);
            if (x > W || x + w < 0) continue;
            boolean sel = i == tile && !inCats;
            Catalog.Item it = its.get(i);
            float grow = sel ? 8 * s : 0;
            r.set(x - grow, top - grow * 1.5f, x + w + grow, top + h + grow * 0.5f);
            if (sel) {
                p.setShader(new RadialGradient(r.centerX(), r.centerY(), w * 0.85f, 0x662BE37A, 0x002BE37A, Shader.TileMode.CLAMP));
                c.drawCircle(r.centerX(), r.centerY(), w * 0.85f, p); p.setShader(null);
            }
            p.setShader(new LinearGradient(0, r.top, 0, r.bottom, sel ? 0xFF0F4A2A : 0xF0082414, 0xF0020C06, Shader.TileMode.CLAMP));
            c.drawRoundRect(r, 12 * s, 12 * s, p); p.setShader(null);
            stroke.setStrokeWidth((sel ? 3 : 1.5f) * s);
            stroke.setShader(new LinearGradient(0, r.top, 0, r.bottom, sel ? new int[]{0xFFFFF1B8, GOLD, GOLD_D} : new int[]{0xFF2E8F58, 0xFF1B5E38, 0xFF2E8F58}, null, Shader.TileMode.CLAMP));
            c.drawRoundRect(r, 12 * s, 12 * s, stroke); stroke.setShader(null);
            icon(c, it.icon, r.centerX(), r.top + h * 0.30f, 30 * s, sel ? 0xFF7DFFB0 : 0xFF3FD083, 2.2f);
            spaced(c, "CODEAQUE", r.centerX(), r.top + h * 0.56f, 8.5f * s, sel ? GOLD : 0xFF9FB8A8, Paint.Align.CENTER);
            String lab = it.label.toUpperCase(Locale.ROOT);
            float ts = 15 * s; p.setTextSize(ts); p.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
            while (p.measureText(lab) > w - 14 * s && ts > 10 * s) { ts -= 0.5f * s; p.setTextSize(ts); }
            if (p.measureText(lab) > w - 14 * s && lab.contains(" ")) {
                int sp = lab.lastIndexOf(' ');
                text(c, lab.substring(0, sp), r.centerX(), r.top + h * 0.73f, ts, 0xFFFFFFFF, Paint.Align.CENTER, true);
                text(c, lab.substring(sp + 1), r.centerX(), r.top + h * 0.73f + ts * 1.1f, ts, 0xFFFFFFFF, Paint.Align.CENTER, true);
            } else text(c, lab, r.centerX(), r.top + h * 0.78f, ts, 0xFFFFFFFF, Paint.Align.CENTER, true);
            if (!it.isAction() && !host.installed(it)) {
                String b = it.webUrl != null ? "WEB" : "GET";
                p.setColor(it.webUrl != null ? 0xCC0E5C33 : 0xCC6B5414);
                RectF br = new RectF(r.right - 44 * s, r.top + 7 * s, r.right - 7 * s, r.top + 23 * s);
                c.drawRoundRect(br, 8 * s, 8 * s, p);
                text(c, b, br.centerX(), br.bottom - 4 * s, 10 * s, 0xFFFFFFFF, Paint.Align.CENTER, true);
            }
        }
        // the focused tile's hint under the row
        Catalog.Item f = its.get(tile);
        String hint = f.isAction() ? "" : host.installed(f) ? "Installed" : f.webUrl != null ? "Opens in your browser (or the app, once installed)" : "Not installed - press A for its Play Store page";
        text(c, hint, W / 2f, top + h + 24 * s, 14 * s, DIM, Paint.Align.CENTER, false);
    }

    private void hints(Canvas c, int W, int H) {
        float y = H - 24 * s;
        p.setColor(0x33FFFFFF); c.drawRect(0, H - 52 * s, W, H - 51 * s, p);
        String[][] hs = {{"A", "Open"}, {"B", "Back"}, {"L1 R1", "Category"}, {"Y", "Settings"}};
        float x = 24 * s;
        for (String[] hnt : hs) {
            p.setTextSize(13 * s); p.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
            float bw = Math.max(26 * s, p.measureText(hnt[0]) + 14 * s);
            stroke.setStrokeWidth(1.6f * s); stroke.setColor(0xFFDDE7E1);
            r.set(x, y - 18 * s, x + bw, y + 6 * s); c.drawRoundRect(r, 12 * s, 12 * s, stroke);
            text(c, hnt[0], r.centerX(), y - 1.5f * s, 13 * s, 0xFFDDE7E1, Paint.Align.CENTER, true);
            text(c, hnt[1], r.right + 9 * s, y, 18 * s, 0xFFDDE7E1, Paint.Align.LEFT, false);
            p.setTextSize(18 * s);
            x = r.right + 9 * s + p.measureText(hnt[1]) + 26 * s;
        }
        boolean on = host.online();
        text(c, on ? "Online" : "Offline", W - 24 * s, y, 18 * s, on ? 0xFF6CFFA8 : DIM, Paint.Align.RIGHT, false);
        p.setTextSize(18 * s);
        icon(c, "globe", W - 24 * s - p.measureText(on ? "Online" : "Offline") - 16 * s, y - 6 * s, 18 * s, on ? 0xFF6CFFA8 : DIM, 1.8f);
    }

    private void prompt(Canvas c, int W, int H) {
        p.setColor(0xCC000000); c.drawRect(0, 0, W, H, p);
        r.set(W / 2f - 300 * s, H / 2f - 110 * s, W / 2f + 300 * s, H / 2f + 110 * s);
        p.setColor(0xFF04170C); c.drawRoundRect(r, 16 * s, 16 * s, p);
        stroke.setColor(GOLD); stroke.setStrokeWidth(2 * s); c.drawRoundRect(r, 16 * s, 16 * s, stroke);
        text(c, prompt.label + " is not installed", W / 2f, r.top + 56 * s, 26 * s, 0xFFFFFFFF, Paint.Align.CENTER, true);
        text(c, "Get it from the Play Store, then come back here.", W / 2f, r.top + 96 * s, 17 * s, DIM, Paint.Align.CENTER, false);
        text(c, "(A)  Open Play Store          (B)  Not now", W / 2f, r.bottom - 36 * s, 19 * s, GOLD, Paint.Align.CENTER, false);
    }

    /** Original line icons (consistent round stroke), drawn in a size x size box centred on cx, cy. */
    private void icon(Canvas c, String kind, float cx, float cy, float size, int col, float wMul) {
        stroke.setColor(col); stroke.setShader(null); stroke.setStrokeWidth(size / 24f * wMul);
        float u = size / 24f, x0 = cx - 12 * u, y0 = cy - 12 * u;
        path.reset();
        RectF ir = iconRect;
        switch (kind) {
            case "pad":
                ir.set(x0 + 2 * u, y0 + 6 * u, x0 + 22 * u, y0 + 18 * u); c.drawRoundRect(ir, 6 * u, 6 * u, stroke);
                c.drawLine(x0 + 7 * u, y0 + 10 * u, x0 + 7 * u, y0 + 14 * u, stroke); c.drawLine(x0 + 5 * u, y0 + 12 * u, x0 + 9 * u, y0 + 12 * u, stroke);
                c.drawCircle(x0 + 16 * u, y0 + 11 * u, 0.9f * u, stroke); c.drawCircle(x0 + 18 * u, y0 + 13.5f * u, 0.9f * u, stroke); break;
            case "cloud":
                path.moveTo(x0 + 7 * u, y0 + 18 * u);
                path.cubicTo(x0 + 2 * u, y0 + 18 * u, x0 + 2 * u, y0 + 11 * u, x0 + 7 * u, y0 + 11 * u);
                path.cubicTo(x0 + 8 * u, y0 + 5 * u, x0 + 16 * u, y0 + 4 * u, x0 + 17 * u, y0 + 10 * u);
                path.cubicTo(x0 + 22 * u, y0 + 10 * u, x0 + 22 * u, y0 + 18 * u, x0 + 17 * u, y0 + 18 * u);
                path.close(); c.drawPath(path, stroke);
                path.reset(); path.moveTo(x0 + 10.5f * u, y0 + 11 * u); path.lineTo(x0 + 14.5f * u, y0 + 13.5f * u); path.lineTo(x0 + 10.5f * u, y0 + 16 * u); path.close();
                c.drawPath(path, stroke); break;
            case "invader":
                ir.set(x0 + 5 * u, y0 + 7 * u, x0 + 19 * u, y0 + 17 * u); c.drawRoundRect(ir, 3 * u, 3 * u, stroke);
                c.drawLine(x0 + 8 * u, y0 + 7 * u, x0 + 6 * u, y0 + 3 * u, stroke); c.drawLine(x0 + 16 * u, y0 + 7 * u, x0 + 18 * u, y0 + 3 * u, stroke);
                c.drawLine(x0 + 2 * u, y0 + 11 * u, x0 + 2 * u, y0 + 15 * u, stroke); c.drawLine(x0 + 22 * u, y0 + 11 * u, x0 + 22 * u, y0 + 15 * u, stroke);
                c.drawCircle(x0 + 9.5f * u, y0 + 11.5f * u, 1 * u, stroke); c.drawCircle(x0 + 14.5f * u, y0 + 11.5f * u, 1 * u, stroke);
                c.drawLine(x0 + 8 * u, y0 + 17 * u, x0 + 8 * u, y0 + 21 * u, stroke); c.drawLine(x0 + 16 * u, y0 + 17 * u, x0 + 16 * u, y0 + 21 * u, stroke); break;
            case "stream":
                c.drawCircle(cx, cy, 9 * u, stroke);
                path.moveTo(x0 + 10 * u, y0 + 8 * u); path.lineTo(x0 + 16 * u, y0 + 12 * u); path.lineTo(x0 + 10 * u, y0 + 16 * u); path.close(); c.drawPath(path, stroke); break;
            case "film":
                ir.set(x0 + 3 * u, y0 + 5 * u, x0 + 21 * u, y0 + 19 * u); c.drawRoundRect(ir, 2 * u, 2 * u, stroke);
                c.drawLine(x0 + 7 * u, y0 + 5 * u, x0 + 7 * u, y0 + 19 * u, stroke); c.drawLine(x0 + 17 * u, y0 + 5 * u, x0 + 17 * u, y0 + 19 * u, stroke);
                c.drawLine(x0 + 3 * u, y0 + 12 * u, x0 + 7 * u, y0 + 12 * u, stroke); c.drawLine(x0 + 17 * u, y0 + 12 * u, x0 + 21 * u, y0 + 12 * u, stroke); break;
            case "music":
                c.drawCircle(x0 + 7 * u, y0 + 18 * u, 3 * u, stroke); c.drawCircle(x0 + 17 * u, y0 + 16 * u, 3 * u, stroke);
                path.moveTo(x0 + 10 * u, y0 + 18 * u); path.lineTo(x0 + 10 * u, y0 + 5 * u); path.lineTo(x0 + 20 * u, y0 + 3 * u); path.lineTo(x0 + 20 * u, y0 + 16 * u); c.drawPath(path, stroke); break;
            case "gear":
                c.drawCircle(cx, cy, 3.5f * u, stroke); c.drawCircle(cx, cy, 8 * u, stroke);
                for (int i = 0; i < 8; i++) { double a = i * Math.PI / 4; c.drawLine(cx + (float) Math.cos(a) * 8 * u, cy + (float) Math.sin(a) * 8 * u, cx + (float) Math.cos(a) * 10.5f * u, cy + (float) Math.sin(a) * 10.5f * u, stroke); }
                break;
            case "grid":
                for (int i = 0; i < 4; i++) { ir.set(x0 + (4 + (i % 2) * 9) * u, y0 + (4 + (i / 2) * 9) * u, x0 + (11 + (i % 2) * 9) * u, y0 + (11 + (i / 2) * 9) * u); c.drawRoundRect(ir, 2 * u, 2 * u, stroke); }
                break;
            case "cube":
                path.moveTo(cx, y0 + 3 * u); path.lineTo(x0 + 20 * u, y0 + 7.5f * u); path.lineTo(x0 + 20 * u, y0 + 16.5f * u); path.lineTo(cx, y0 + 21 * u);
                path.lineTo(x0 + 4 * u, y0 + 16.5f * u); path.lineTo(x0 + 4 * u, y0 + 7.5f * u); path.close();
                path.moveTo(x0 + 4 * u, y0 + 7.5f * u); path.lineTo(cx, y0 + 12 * u); path.lineTo(x0 + 20 * u, y0 + 7.5f * u); path.moveTo(cx, y0 + 12 * u); path.lineTo(cx, y0 + 21 * u);
                c.drawPath(path, stroke); break;
            case "globe":
                c.drawCircle(cx, cy, 9 * u, stroke); ir.set(cx - 4 * u, cy - 9 * u, cx + 4 * u, cy + 9 * u); c.drawOval(ir, stroke);
                c.drawLine(cx - 9 * u, cy, cx + 9 * u, cy, stroke); break;
            case "wifi":
                for (int i = 1; i <= 3; i++) { float rr = i * 3.6f * u; ir.set(cx - rr, cy + 6 * u - rr, cx + rr, cy + 6 * u + rr); c.drawArc(ir, 225, 90, false, stroke); }
                p.setColor(col); c.drawCircle(cx, cy + 6 * u, 1.4f * u, p); break;
            case "bt":
                path.moveTo(x0 + 7 * u, y0 + 7 * u); path.lineTo(x0 + 17 * u, y0 + 16 * u); path.lineTo(cx, y0 + 21 * u); path.lineTo(cx, y0 + 3 * u);
                path.lineTo(x0 + 17 * u, y0 + 8 * u); path.lineTo(x0 + 7 * u, y0 + 17 * u); c.drawPath(path, stroke); break;
            case "display":
                ir.set(x0 + 2 * u, y0 + 4 * u, x0 + 22 * u, y0 + 17 * u); c.drawRoundRect(ir, 2 * u, 2 * u, stroke);
                c.drawLine(x0 + 8 * u, y0 + 21 * u, x0 + 16 * u, y0 + 21 * u, stroke); c.drawLine(cx, y0 + 17 * u, cx, y0 + 21 * u, stroke); break;
            case "sparkle":
                path.moveTo(cx, y0 + 2 * u); path.quadTo(cx, cy, x0 + 22 * u, cy); path.quadTo(cx, cy, cx, y0 + 22 * u);
                path.quadTo(cx, cy, x0 + 2 * u, cy); path.quadTo(cx, cy, cx, y0 + 2 * u); c.drawPath(path, stroke); break;
            case "home":
                path.moveTo(x0 + 3 * u, y0 + 11 * u); path.lineTo(cx, y0 + 3 * u); path.lineTo(x0 + 21 * u, y0 + 11 * u);
                path.moveTo(x0 + 6 * u, y0 + 9 * u); path.lineTo(x0 + 6 * u, y0 + 20 * u); path.lineTo(x0 + 18 * u, y0 + 20 * u); path.lineTo(x0 + 18 * u, y0 + 9 * u);
                c.drawPath(path, stroke); break;
            case "handheld":
                ir.set(x0 + 2 * u, y0 + 6 * u, x0 + 22 * u, y0 + 18 * u); c.drawRoundRect(ir, 4 * u, 4 * u, stroke);
                ir.set(x0 + 7 * u, y0 + 8 * u, x0 + 17 * u, y0 + 16 * u); c.drawRect(ir, stroke); break;
            case "disc":
                c.drawCircle(cx, cy, 9 * u, stroke); c.drawCircle(cx, cy, 2.5f * u, stroke);
                ir.set(cx - 6 * u, cy - 6 * u, cx + 6 * u, cy + 6 * u); c.drawArc(ir, 200, 60, false, stroke); break;
            case "cart":
                path.moveTo(x0 + 5 * u, y0 + 3 * u); path.lineTo(x0 + 19 * u, y0 + 3 * u); path.lineTo(x0 + 19 * u, y0 + 21 * u); path.lineTo(x0 + 5 * u, y0 + 21 * u); path.close();
                c.drawPath(path, stroke); ir.set(x0 + 8 * u, y0 + 6 * u, x0 + 16 * u, y0 + 13 * u); c.drawRect(ir, stroke);
                c.drawLine(x0 + 9 * u, y0 + 18 * u, x0 + 15 * u, y0 + 18 * u, stroke); break;
            default:
                c.drawCircle(cx, cy, 8 * u, stroke);
        }
    }
}

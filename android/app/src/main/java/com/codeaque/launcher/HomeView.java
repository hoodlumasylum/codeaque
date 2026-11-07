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
        Bitmap appIcon(Catalog.Item item);
        Bitmap cover(Catalog.Item item);
        String wifi();        // 0.10.5: "Connected to Home" / "Wi-Fi off" / "Not connected"     // 0.9.5: a game picture (the app's own banner, or cached libretro box art), null = none   // an installed app's own icon (null for CodeAque's line-drawn tiles)
    }

    private Palette T = Palette.NEON;
    private int GOLD = T.gold, GOLD_D = T.goldDark, TXT = T.text, DIM = T.dim;
    private Bitmap wallpaper;
    public void setTheme(Palette t) { T = t; GOLD = t.gold; GOLD_D = t.goldDark; TXT = t.text; DIM = t.dim; invalidate(); }
    public void setWallpaper(Bitmap b) { wallpaper = b; invalidate(); }

    private final Host host;
    private List<Catalog.Category> cats = Catalog.categories();

    /** 0.9.4: new rows after the installed apps / game folders were scanned; keeps the chosen category and tile. */
    public void setCategories(List<Catalog.Category> n) {
        String id = cats.get(cat).id;
        cats = n;
        int i = Catalog.indexOf(cats, id);
        cat = i < 0 ? 0 : i; catAnim = cat;
        tile = Math.max(0, Math.min(tile, items().size() - 1));
        invalidate();
    }
    private int cat = 0, tile = 0;       // 0.10.1: start on the Featured row, Roblox first
    private boolean inCats = false;
    private float catAnim = 0, scrollAnim = 0, t = 0;
    private long last = 0, lastFrame = 0;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG), stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private Bitmap logo, splash;
    private long splashStart = 0;                 // Code's splash with a live loading bar, shown once when the launcher starts
    private static final long SPLASH_MS = 1900, SPLASH_FADE = 350;
    private final RectF r = new RectF(), iconRect = new RectF();
    private Catalog.Item prompt;          // "not installed" panel
    private final float[] motes = new float[36];
    private final SimpleDateFormat clock = new SimpleDateFormat("EEE d MMM  HH:mm", Locale.getDefault());
    private float downX, downY, s = 1; private boolean dragging; private float dragTiles;

    public HomeView(Context c, Host host) {
        super(c);
        this.host = host;
        tile = 0; cat = 0; catAnim = 0;          // 0.10.1: start on Featured (Roblox, Minecraft, then Steam)
        setFocusable(true); setFocusableInTouchMode(true);
        try {
            logo = BitmapFactory.decodeStream(c.getAssets().open("logo.png"));
            splash = BitmapFactory.decodeStream(c.getAssets().open("splash.jpg"));   // 1280x720 copy of the 1672x941 art   // the white CodeAque logo (3:1)
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
        if (splash != null) { splash = null; invalidate(); return true; }     // any button skips the splash
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
                if (kids) { if (e.getRepeatCount() == 0) e.startTracking(); return true; }   // only a long hold, see onKeyLongPress
                setCat(Catalog.indexOf(cats, "settings")); inCats = false; return true;
        }
        return super.onKeyDown(code, e);
    }

    @Override public boolean onKeyLongPress(int code, KeyEvent e) {
        if (kids && (code == KeyEvent.KEYCODE_BUTTON_SELECT || code == KeyEvent.KEYCODE_BUTTON_Y || code == KeyEvent.KEYCODE_MENU)) {
            host.open(Catalog.KIDS_OFF); return true;
        }
        return super.onKeyLongPress(code, e);
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
        if (projector) { x = (x - getWidth() * SAFE) / (1 - 2 * SAFE); y = (y - H * SAFE) / (1 - 2 * SAFE); }
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: if (splash != null) { splash = null; invalidate(); return true; }
                downX = x; downY = y; dragging = false; dragTiles = 0; return true;
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
    // 0.10.2 Kids mode: bigger tiles and labels (readable at 640x480), hold Select to reach the grown-up switch
    private boolean kids;
    public void setKids(boolean k) { kids = k; invalidate(); }
    static final float KIDS_SCALE = 1.7f;
    private float big() { return kids ? KIDS_SCALE : 1f; }
    private boolean covers() { String id = cats.get(cat).id; if (kids) return id.equals("kplay"); id = cats.get(cat).id; return id.equals("mygames") || id.equals("steam"); }      // 0.9.5: portrait box-art tiles
    private float tileH() { return (covers() ? 190 : 112) * s * big(); }
    private float tileW() { return (covers() ? 128 : 156) * s * big(); }
    private float tileGap() { return 14 * s * big(); }
    private float tilesTop() { return getHeight() - 80 * s - tileH(); }
    private float tilesLeft() { return 24 * s; }

    // ---------- drawing ----------
    // 0.9.6 Projector mode: everything inside a 5% overscan-safe frame, tiles and text about 15% bigger (TV remote / D-pad)
    private boolean projector;
    public void setProjector(boolean on) { projector = on; invalidate(); }
    private static final float SAFE = 0.05f;

    @Override protected void onDraw(Canvas c) {
        if (!projector) { drawAll(c); return; }
        c.save();
        c.drawColor(0xFF000000);
        c.translate(getWidth() * SAFE, getHeight() * SAFE); c.scale(1 - 2 * SAFE, 1 - 2 * SAFE);
        c.clipRect(0, 0, getWidth(), getHeight());
        drawAll(c);
        c.restore();
    }

    private void drawAll(Canvas c) {
        int W = getWidth(), H = getHeight();
        s = Math.min(W / 1000f, H / 720f) * (projector ? 1.15f : 1f);   // 1 on 1280x720, bigger than a plain scale on 4:3 screens (640x480)
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
        if (splash != null && drawSplash(c, W, H)) { postInvalidateOnAnimation(); return; }

        boolean settling = Math.abs(cat - catAnim) > 0.002f || Math.abs(target - scrollAnim) > 0.002f;
        if (anim || settling) {
            if ("on".equals(fx) || settling) postInvalidateOnAnimation();
            else postInvalidateDelayed(33);                 // auto: 30 fps background on weak chips
        }
    }

    /** Returns false (and frees the picture) once the splash is over. Geometry is in the 1672x941 art's pixels. */
    private boolean drawSplash(Canvas c, int W, int H) {
        long now = android.os.SystemClock.uptimeMillis();
        if (splashStart == 0) splashStart = now;
        long el = now - splashStart;
        if (el > SPLASH_MS + SPLASH_FADE) { splash.recycle(); splash = null; return false; }
        float k = Math.min(Math.max(W / 1672f, H / 941f), W * 0.96f / (1672f * 0.80f));   // never crop the wordmark
        float ox = (W - 1672 * k) / 2, oy = (H - 941 * k) / 2;
        int alpha = el < SPLASH_MS ? 255 : (int) (255 * (1 - (el - SPLASH_MS) / (float) SPLASH_FADE));
        p.setAlpha(255); p.setColor(0xFF000000); p.setAlpha(alpha); c.drawRect(0, 0, W, H, p);
        r.set(ox, oy, ox + 1672 * k, oy + 941 * k); p.setFilterBitmap(true); p.setAlpha(alpha); c.drawBitmap(splash, null, r, p);
        float u = Math.min(1, el / (float) (SPLASH_MS - 200)); float prog = u < 0.5f ? 4 * u * u * u : 1 - (float) Math.pow(-2 * u + 2, 3) / 2;
        float bh = 19 * k, bw = Math.max(bh, 627 * k * prog);
        r.set(ox + 523 * k, oy + 682 * k, ox + 523 * k + bw, oy + 682 * k + bh);
        p.setShader(new LinearGradient(0, r.top, 0, r.bottom, new int[]{0xFFFF6A6A, 0xFFFF1A1A, 0xFFA80000}, new float[]{0, 0.35f, 1}, Shader.TileMode.CLAMP));
        p.setAlpha(alpha); c.drawRoundRect(r, bh / 2, bh / 2, p); p.setShader(null);
        int d = (int) (el / 400) % 4;
        p.setColor(0xFFFF2A2A); p.setAlpha(alpha);
        r.set(ox + (757 + d * 46) * k, oy + 741 * k, ox + (776 + d * 46) * k, oy + 760 * k);
        c.drawRoundRect(r, 3 * k, 3 * k, p);
        p.setAlpha(255);
        return true;
    }

    private void background(Canvas c, int W, int H, boolean anim) {
        c.drawColor(0xFF000000);
        if (wallpaper != null) {             // the user's own picture, centre-cropped, darkened so the text stays readable
            float sc = Math.max(W / (float) wallpaper.getWidth(), H / (float) wallpaper.getHeight());
            float bw = wallpaper.getWidth() * sc, bh = wallpaper.getHeight() * sc;
            r.set((W - bw) / 2, (H - bh) / 2, (W + bw) / 2, (H + bh) / 2);
            p.setFilterBitmap(true); c.drawBitmap(wallpaper, null, r, p);
            p.setColor(0x73000000); c.drawRect(0, 0, W, H, p);
            return;
        }
        p.setShader(new RadialGradient(W * 0.5f, H * 0.48f, W * 0.6f, T.bgCenter, 0xFF000000, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, W, H, p); p.setShader(null);
        if (T.aura) aura(c, W, H);
        if (T.burst) burst(c, W, H);
        if (T.snow) snow(c, W, H);
        // flowing waves: a few sine curves, green with one gold thread
        int[] cols = T.waves;
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
        p.setColor(T.accentLight);
        for (int i = 0; i < motes.length; i += 3) {
            float x = (motes[i] * W + t * 12 * s * (0.5f + motes[i + 1])) % W;
            float y = H * (0.3f + 0.4f * motes[i + 1]) + (float) Math.sin(t + i) * 10 * s;
            p.setAlpha((int) (60 + 120 * motes[i + 2]));
            c.drawCircle(x, y, (1 + 1.5f * motes[i + 2]) * s, p);
        }
        p.setAlpha(255);
    }

    /** Power Up: a pulsing orange core with a blue rim and energy sparks rising around the emblem. */
    private void aura(Canvas c, int W, int H) {
        float cx = W / 2f, cy = H * 0.44f, pulse = 0.5f + 0.5f * (float) Math.sin(t * 3.2f);
        float rad = (230 + 30 * pulse) * s;
        p.setShader(new RadialGradient(cx, cy, rad * 1.6f, new int[]{0x00000000, 0x332E9BFF, 0x00000000},
                new float[]{0.45f, 0.75f, 1f}, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, rad * 1.6f, p);
        p.setShader(new RadialGradient(cx, cy, rad, new int[]{(((int) (0x70 + 0x30 * pulse)) << 24) | 0xFFB040, 0x55FF6A00, 0x00FF4000},
                new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, rad, p); p.setShader(null);
        // flickering flame tongues around the core
        for (int i = 0; i < 14; i++) {
            double a = i * Math.PI * 2 / 14 + t * 0.35;
            float len = (60 + 40 * (float) Math.abs(Math.sin(t * 4 + i * 1.7))) * s;
            float x1 = cx + (float) Math.cos(a) * rad * 0.7f, y1 = cy + (float) Math.sin(a) * rad * 0.55f;
            float x2 = x1 + (float) Math.cos(a) * len * 0.4f, y2 = y1 - len;
            stroke.setStrokeWidth((5 + 3 * pulse) * s);
            stroke.setShader(new LinearGradient(x1, y1, x2, y2, i % 3 == 0 ? 0x885CC8FF : 0x88FF9A1F, 0x00FF9A1F, Shader.TileMode.CLAMP));
            c.drawLine(x1, y1, x2, y2, stroke);
        }
        stroke.setShader(null);
        // rising sparks
        for (int i = 0; i < motes.length; i += 3) {
            float life = (t * (0.25f + 0.3f * motes[i + 1]) + motes[i]) % 1f;
            float x = cx + (motes[i] - 0.5f) * W * 0.7f + (float) Math.sin(t * 2 + i) * 8 * s;
            float y = H * 0.85f - life * H * 0.75f;
            p.setColor(i % 2 == 0 ? 0xFFFFC060 : 0xFF8FD8FF);
            p.setAlpha((int) (220 * (1 - life)));
            c.drawCircle(x, y, (1.5f + 2f * motes[i + 2]) * s, p);
        }
        p.setAlpha(255);
    }

    /** Catch: slowly turning red/white sunburst with falling yellow confetti stars. */
    private void burst(Canvas c, int W, int H) {
        float cx = W / 2f, cy = H * 0.44f, R = Math.max(W, H) * 0.9f;
        p.setColor(0x22FFFFFF);
        for (int i = 0; i < 16; i++) {
            double a0 = i * Math.PI / 8 + t * 0.15, a1 = a0 + Math.PI / 16;
            path.reset(); path.moveTo(cx, cy);
            path.lineTo(cx + (float) Math.cos(a0) * R, cy + (float) Math.sin(a0) * R);
            path.lineTo(cx + (float) Math.cos(a1) * R, cy + (float) Math.sin(a1) * R); path.close();
            c.drawPath(path, p);
        }
        p.setShader(new RadialGradient(cx, cy, 260 * s, 0x66FFD23F, 0x00FFD23F, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, 260 * s, p); p.setShader(null);
        for (int i = 0; i < motes.length; i += 3) {
            float life = (t * (0.08f + 0.1f * motes[i + 1]) + motes[i + 2]) % 1f;
            float x = motes[i] * W + (float) Math.sin(t * 1.5f + i) * 14 * s, y = -20 * s + life * (H + 40 * s);
            p.setColor(i % 9 == 0 ? 0xFFFF4A4A : i % 2 == 0 ? 0xFFFFD23F : 0xFFFFFFFF); p.setAlpha(190);
            star(c, x, y, (5 + 4 * motes[i + 1]) * s, t * 2 + i, p);
        }
        p.setAlpha(255);
    }

    private final float[] flakes = new float[180];
    /** Christmas: soft snow drifting down at three depths (60 flakes: cheap on a 2 GB RK3566). */
    private void snow(Canvas c, int W, int H) {
        if (flakes[0] == 0) { java.util.Random rnd = new java.util.Random(11); for (int i = 0; i < flakes.length; i++) flakes[i] = 0.0001f + rnd.nextFloat(); }
        for (int i = 0; i < flakes.length; i += 3) {
            float depth = flakes[i + 2];                       // 0 far .. 1 near
            float y = ((flakes[i + 1] + t * (0.03f + 0.07f * depth)) % 1f) * (H + 20 * s) - 10 * s;
            float x = flakes[i] * W + (float) Math.sin(t * (0.6f + depth) + i) * 18 * s * depth;
            p.setColor(0xFFFFFFFF); p.setAlpha((int) (90 + 140 * depth));
            c.drawCircle(x, y, (1.2f + 2.6f * depth) * s, p);
        }
        p.setAlpha(255);
    }

    private void star(Canvas c, float x, float y, float rad, float rot, Paint q) {
        path.reset();
        for (int k = 0; k < 10; k++) {
            double a = rot + k * Math.PI / 5 - Math.PI / 2; float rr = k % 2 == 0 ? rad : rad * 0.45f;
            float px = x + (float) Math.cos(a) * rr, py = y + (float) Math.sin(a) * rr;
            if (k == 0) path.moveTo(px, py); else path.lineTo(px, py);
        }
        path.close(); c.drawPath(path, q);
    }

    private void goldText(Canvas c, String txt, float x, float y, float size, Paint.Align a, boolean bold) {
        p.setTextSize(size); p.setTextAlign(a); p.setFakeBoldText(bold);
        p.setTypeface(Typeface.create("sans-serif-black", Typeface.NORMAL));
        p.setShader(new LinearGradient(0, y - size, 0, y, new int[]{T.goldLight, GOLD, GOLD_D, T.goldLight}, null, Shader.TileMode.CLAMP));
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
        if (logo != null) { r.set(18 * s, 10 * s, 18 * s + 150 * s, 60 * s); p.setFilterBitmap(true); c.drawBitmap(logo, null, r, p); }
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
                p.setShader(new RadialGradient(x, y, 60 * s, (((int) (0x55 * sel)) << 24) | T.glowRgb, T.glowRgb, Shader.TileMode.CLAMP));
                c.drawCircle(x, y, 60 * s, p); p.setShader(null);
            }
            int col = i == cat ? (inCats ? GOLD : T.accent) : 0xFFDDE7E1;
            icon(c, cats.get(i).icon, x, y, size, col, i == cat ? 2.6f : 2.2f);
            text(c, cats.get(i).label, x, y + size * 0.5f + (i == cat ? 32 : 26) * s, (i == cat ? 24 : 18) * s, i == cat ? 0xFFFFFFFF : 0xFFC9D6CF, Paint.Align.CENTER, i == cat);
        }
    }

    private void mark(Canvas c, int W, int H) {
        float cx = W / 2f, top = H * 0.30f;
        float lw = 560 * s, lh = lw / 3f, ly = top + 6 * s;
        c.save(); c.scale(1f, 0.45f, cx, ly + lh / 2);     // soft theme-coloured glow behind the white logo
        p.setShader(new RadialGradient(cx, ly + lh / 2, 330 * s, 0x77000000 | T.glowRgb, T.glowRgb, Shader.TileMode.CLAMP));
        c.drawCircle(cx, ly + lh / 2, 330 * s, p); p.setShader(null); c.restore();
        if (logo != null) { r.set(cx - lw / 2, ly, cx + lw / 2, ly + lh); p.setFilterBitmap(true); c.drawBitmap(logo, null, r, p); }
        float ty = ly + lh + 2 * s;
        if (!covers()) spaced(c, "GAMING  •  CLOUD  •  EMULATION  •  STREAMING", cx, ty + 30 * s, 15 * s, 0xFFCFE0D6, Paint.Align.CENTER);
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
                p.setShader(new RadialGradient(r.centerX(), r.centerY(), w * 0.85f, 0x66000000 | T.glowRgb, T.glowRgb, Shader.TileMode.CLAMP));
                c.drawCircle(r.centerX(), r.centerY(), w * 0.85f, p); p.setShader(null);
            }
            if (covers()) { coverTile(c, it, sel); continue; }
            p.setShader(new LinearGradient(0, r.top, 0, r.bottom, sel ? T.tileSelTop : T.tileTop, T.tileBottom, Shader.TileMode.CLAMP));
            c.drawRoundRect(r, 12 * s, 12 * s, p); p.setShader(null);
            stroke.setStrokeWidth((sel ? 3 : 1.5f) * s);
            stroke.setShader(new LinearGradient(0, r.top, 0, r.bottom, sel ? new int[]{T.goldLight, GOLD, GOLD_D} : new int[]{T.border, T.borderDark, T.border}, null, Shader.TileMode.CLAMP));
            c.drawRoundRect(r, 12 * s, 12 * s, stroke); stroke.setShader(null);
            if (sel && T.burst) {
                p.setColor(0xFFFFD23F); star(c, r.right - 4 * s, r.top + 2 * s, 13 * s, t * 4, p);
                for (int k = 0; k < 2; k++) {          // two little pills orbiting the tile
                    double a = t * 2.4 + k * Math.PI;
                    float px = r.centerX() + (float) Math.cos(a) * (w * 0.62f), py = r.centerY() + (float) Math.sin(a) * (h * 0.62f);
                    c.save(); c.rotate((float) Math.toDegrees(a) + 90, px, py);
                    p.setColor(k == 0 ? 0xFFFF4A4A : 0xFFFFD23F);
                    RectF pill = new RectF(px - 9 * s, py - 4 * s, px + 9 * s, py + 4 * s);
                    c.drawRoundRect(pill, 4 * s, 4 * s, p); c.restore();
                }
            }
            if (T.snow) {                                     // a soft snow cap along the tile's top edge
                path.reset(); float sy = r.top + 2 * s;
                path.moveTo(r.left + 6 * s, sy);
                for (int k2 = 0; k2 <= 6; k2++) { float px = r.left + 6 * s + (r.width() - 12 * s) * k2 / 6f; path.quadTo(px - (r.width() - 12 * s) / 12f, sy + ((k2 % 2 == 0) ? 7 : 3) * s, px, sy); }
                path.lineTo(r.right - 6 * s, r.top - 3 * s); path.quadTo(r.centerX(), r.top - 8 * s, r.left + 6 * s, r.top - 3 * s); path.close();
                p.setColor(0xF2FFFFFF); c.drawPath(path, p);
            }
            Bitmap ai = host.appIcon(it);
            if (ai != null) {                                   // the app's own icon, as Android shows it
                float is = 40 * s * big(); iconRect.set(r.centerX() - is / 2, r.top + h * 0.30f - is / 2 - 4 * s, r.centerX() + is / 2, r.top + h * 0.30f + is / 2 - 4 * s);
                p.setFilterBitmap(true); p.setAlpha(255); c.drawBitmap(ai, null, iconRect, p);
            } else icon(c, it.icon, r.centerX(), r.top + h * 0.30f, 30 * s, sel ? T.accentLight : T.accentMid, 2.2f);
            spaced(c, "CODEAQUE", r.centerX(), r.top + h * 0.56f, 8.5f * s, sel ? GOLD : T.sub, Paint.Align.CENTER);
            String lab = it.label.toUpperCase(Locale.ROOT);
            float ts = (kids ? 26 : 15) * s; p.setTextSize(ts); p.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
            while (p.measureText(lab) > w - 14 * s && ts > 10 * s) { ts -= 0.5f * s; p.setTextSize(ts); }
            if (p.measureText(lab) > w - 14 * s && lab.contains(" ")) {
                int sp = lab.lastIndexOf(' ');
                text(c, lab.substring(0, sp), r.centerX(), r.top + h * 0.73f, ts, 0xFFFFFFFF, Paint.Align.CENTER, true);
                text(c, lab.substring(sp + 1), r.centerX(), r.top + h * 0.73f + ts * 1.1f, ts, 0xFFFFFFFF, Paint.Align.CENTER, true);
            } else text(c, lab, r.centerX(), r.top + h * 0.78f, ts, 0xFFFFFFFF, Paint.Align.CENTER, true);
            if (!it.isAction() && !host.installed(it)) {
                String b = it.webUrl != null ? "WEB" : "GET";
                p.setColor(it.webUrl != null ? T.webBadge : T.getBadge);
                RectF br = new RectF(r.right - 44 * s, r.top + 7 * s, r.right - 7 * s, r.top + 23 * s);
                c.drawRoundRect(br, 8 * s, 8 * s, p);
                text(c, b, br.centerX(), br.bottom - 4 * s, 10 * s, 0xFFFFFFFF, Paint.Align.CENTER, true);
            }
        }
        // the focused tile's hint under the row
        if (its.isEmpty()) return;
        Catalog.Item f = its.get(Math.min(tile, its.size() - 1));
        String hint = "wifi".equals(f.action) ? host.wifi() : f.isAction() ? "" : f.isRom() ? "Your game file - opens with an emulator you have installed" : f.isDeviceApp() ? "Installed on this device" : host.installed(f) ? "Installed" : f.webUrl != null ? "Opens in your browser (or the app, once installed)" : "Not installed - press A for its Play Store page";
        text(c, hint, W / 2f, top + h + 24 * s, 14 * s, DIM, Paint.Align.CENTER, false);
    }

    private final Path clip = new Path();
    private final RectF src = new RectF();
    /** A portrait My Games tile in r: the game's picture (centre-cropped), else CodeAque's own generic art with the app icon. */
    private void coverTile(Canvas c, Catalog.Item it, boolean sel) {
        float rad = 12 * s;
        Bitmap cov = host.cover(it), ai = cov == null ? host.appIcon(it) : null;
        c.save(); clip.reset(); clip.addRoundRect(r, rad, rad, Path.Direction.CW); c.clipPath(clip);
        if (it == Catalog.ROBLOX) blockyArt(c);          // 0.10.3: CodeAque's own original art, drawn here (no Roblox characters or logo)
        else if (cov != null) {
            float sc = Math.max(r.width() / cov.getWidth(), r.height() / cov.getHeight());
            float bw = cov.getWidth() * sc, bh = cov.getHeight() * sc;
            src.set(r.centerX() - bw / 2, r.centerY() - bh / 2, r.centerX() + bw / 2, r.centerY() + bh / 2);
            p.setFilterBitmap(true); p.setAlpha(255); c.drawBitmap(cov, null, src, p);
        } else {
            float hue = (Math.abs(it.key().hashCode()) % 360);
            int top = Color.HSVToColor(new float[]{hue, 0.7f, 0.55f}), mid = Color.HSVToColor(new float[]{hue, 0.8f, 0.22f});
            p.setShader(new LinearGradient(0, r.top, 0, r.bottom, new int[]{top, mid, 0xFF05080A}, new float[]{0, 0.6f, 1}, Shader.TileMode.CLAMP));
            c.drawRect(r, p); p.setShader(null);
            p.setColor(0x10FFFFFF);
            for (int k = 0; k < 5; k++) { float x0 = r.left + (k * 0.3f - 0.3f) * r.width(); path.reset(); path.moveTo(x0, r.bottom); path.lineTo(x0 + 8 * s, r.bottom);
                path.lineTo(x0 + 8 * s + r.height() * 0.55f, r.top); path.lineTo(x0 + r.height() * 0.55f, r.top); path.close(); c.drawPath(path, p); }
            stroke.setColor(0x33FFFFFF); stroke.setShader(null); stroke.setStrokeWidth(2 * s);
            c.drawCircle(r.centerX(), r.top + r.height() * 0.36f, r.width() * 0.32f, stroke);
            if (ai != null) { float is = 52 * s; iconRect.set(r.centerX() - is / 2, r.top + r.height() * 0.36f - is / 2, r.centerX() + is / 2, r.top + r.height() * 0.36f + is / 2);
                p.setFilterBitmap(true); p.setColor(0xFFFFFFFF); c.drawBitmap(ai, null, iconRect, p); }
            else icon(c, it.icon, r.centerX(), r.top + r.height() * 0.36f, 40 * s, sel ? T.accentLight : 0xFFDDF4FF, 2.2f);
        }
        p.setShader(new LinearGradient(0, r.bottom - r.height() * 0.34f, 0, r.bottom, 0x00000000, 0xE6000000, Shader.TileMode.CLAMP));
        c.drawRect(r.left, r.bottom - r.height() * 0.34f, r.right, r.bottom, p); p.setShader(null);
        c.restore();
        stroke.setStrokeWidth((sel ? 3 : 1.5f) * s);
        stroke.setShader(new LinearGradient(0, r.top, 0, r.bottom, sel ? new int[]{T.goldLight, GOLD, GOLD_D} : new int[]{T.border, T.borderDark, T.border}, null, Shader.TileMode.CLAMP));
        c.drawRoundRect(r, rad, rad, stroke); stroke.setShader(null);
        if (T.snow) { p.setColor(0xF2FFFFFF); r2.set(r.left + 6 * s, r.top - 4 * s, r.right - 6 * s, r.top + 5 * s); c.drawRoundRect(r2, 5 * s, 5 * s, p); }
        String lab = it.label.toUpperCase(Locale.ROOT);
        float ts = 14 * s; p.setTextSize(ts); p.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        while (p.measureText(lab) > r.width() - 12 * s && ts > 10 * s) { ts -= 0.5f * s; p.setTextSize(ts); }
        if (p.measureText(lab) > r.width() - 12 * s && lab.contains(" ")) {
            int sp = lab.lastIndexOf(' ');
            text(c, lab.substring(0, sp), r.centerX(), r.bottom - 12 * s - ts * 1.1f, ts, 0xFFFFFFFF, Paint.Align.CENTER, true);
            text(c, lab.substring(sp + 1), r.centerX(), r.bottom - 12 * s, ts, 0xFFFFFFFF, Paint.Align.CENTER, true);
        } else text(c, lab, r.centerX(), r.bottom - 12 * s, ts, 0xFFFFFFFF, Paint.Align.CENTER, true);
    }
    private final RectF r2 = new RectF();

    /** Original CodeAque art for the Roblox tile: a bright sky, a grass block world and two of our own blocky buddies. */
    private void blockyArt(Canvas c) {
        float W = r.width(), H = r.height(), x0 = r.left, y0 = r.top;
        p.setShader(new LinearGradient(0, y0, 0, y0 + H, new int[]{0xFF39C6FF, 0xFF8FE3FF, 0xFFFFE27A}, null, Shader.TileMode.CLAMP));
        c.drawRect(r, p); p.setShader(null);
        p.setColor(0xFFFFFFFF);                                          // square clouds
        c.drawRect(x0 + W * 0.08f, y0 + H * 0.10f, x0 + W * 0.30f, y0 + H * 0.15f, p);
        c.drawRect(x0 + W * 0.14f, y0 + H * 0.07f, x0 + W * 0.24f, y0 + H * 0.10f, p);
        c.drawRect(x0 + W * 0.62f, y0 + H * 0.16f, x0 + W * 0.90f, y0 + H * 0.21f, p);
        float g = y0 + H * 0.66f, b = W / 8f;                          // grass + dirt blocks
        for (int k = 0; k < 8; k++) {
            p.setColor(k % 2 == 0 ? 0xFF4CD964 : 0xFF3BC453); c.drawRect(x0 + k * b, g, x0 + (k + 1) * b, g + b * 0.5f, p);
            p.setColor(k % 2 == 0 ? 0xFFB5793F : 0xFFA26A35); c.drawRect(x0 + k * b, g + b * 0.5f, x0 + (k + 1) * b, y0 + H, p);
        }
        buddy(c, x0 + W * 0.30f, g, W * 0.24f, 0xFFFF4FA3, 0xFF2D7DFF, false);
        buddy(c, x0 + W * 0.68f, g, W * 0.26f, 0xFFFFC23A, 0xFF8A4DFF, true);
    }

    /** One blocky buddy standing on y: square head with a smile, shirt, arms (one waving), legs. */
    private void buddy(Canvas c, float cx, float y, float u, int shirt, int pants, boolean wave) {
        float hs = u * 0.62f, bw = u * 0.62f, bh = u * 0.62f, lh = u * 0.5f, aw = u * 0.2f;
        float legTop = y - lh, bodyTop = legTop - bh, headTop = bodyTop - hs;
        p.setColor(pants); c.drawRect(cx - bw / 2, legTop, cx - 0.02f * u, y, p); c.drawRect(cx + 0.02f * u, legTop, cx + bw / 2, y, p);
        p.setColor(shirt); c.drawRect(cx - bw / 2, bodyTop, cx + bw / 2, legTop, p);
        p.setColor(0xFFFFD9A8);
        c.drawRect(cx - bw / 2 - aw, bodyTop, cx - bw / 2, legTop - bh * 0.1f, p);
        if (wave) c.drawRect(cx + bw / 2, bodyTop - bh * 0.8f, cx + bw / 2 + aw, bodyTop + bh * 0.1f, p);
        else c.drawRect(cx + bw / 2, bodyTop, cx + bw / 2 + aw, legTop - bh * 0.1f, p);
        c.drawRect(cx - hs / 2, headTop, cx + hs / 2, bodyTop, p);
        p.setColor(0xFF202020);
        c.drawRect(cx - hs * 0.25f, headTop + hs * 0.30f, cx - hs * 0.12f, headTop + hs * 0.45f, p);
        c.drawRect(cx + hs * 0.12f, headTop + hs * 0.30f, cx + hs * 0.25f, headTop + hs * 0.45f, p);
        c.drawRect(cx - hs * 0.22f, headTop + hs * 0.62f, cx + hs * 0.22f, headTop + hs * 0.70f, p);
        c.drawRect(cx - hs * 0.22f, headTop + hs * 0.55f, cx - hs * 0.14f, headTop + hs * 0.65f, p);
        c.drawRect(cx + hs * 0.14f, headTop + hs * 0.55f, cx + hs * 0.22f, headTop + hs * 0.65f, p);
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
        text(c, on ? "Online" : "Offline", W - 24 * s, y, 18 * s, on ? T.accent : DIM, Paint.Align.RIGHT, false);
        p.setTextSize(18 * s);
        icon(c, "globe", W - 24 * s - p.measureText(on ? "Online" : "Offline") - 16 * s, y - 6 * s, 18 * s, on ? T.accent : DIM, 1.8f);
    }

    private void prompt(Canvas c, int W, int H) {
        p.setColor(0xCC000000); c.drawRect(0, 0, W, H, p);
        r.set(W / 2f - 300 * s, H / 2f - 110 * s, W / 2f + 300 * s, H / 2f + 110 * s);
        p.setColor(T.prompt); c.drawRoundRect(r, 16 * s, 16 * s, p);
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
            case "download":
                c.drawLine(cx, y0 + 3 * u, cx, y0 + 15 * u, stroke);
                path.moveTo(x0 + 7 * u, y0 + 10 * u); path.lineTo(cx, y0 + 15 * u); path.lineTo(x0 + 17 * u, y0 + 10 * u); c.drawPath(path, stroke);
                c.drawLine(x0 + 4 * u, y0 + 20 * u, x0 + 20 * u, y0 + 20 * u, stroke); break;
            case "palette":
                path.moveTo(cx, y0 + 3 * u); path.cubicTo(x0 + 2 * u, y0 + 3 * u, x0 + 2 * u, y0 + 21 * u, cx, y0 + 21 * u);
                path.cubicTo(x0 + 15 * u, y0 + 21 * u, x0 + 13 * u, y0 + 16 * u, x0 + 16 * u, y0 + 15 * u);
                path.cubicTo(x0 + 22 * u, y0 + 14 * u, x0 + 23 * u, y0 + 3 * u, cx, y0 + 3 * u); c.drawPath(path, stroke);
                c.drawCircle(x0 + 8 * u, y0 + 10 * u, 1.2f * u, stroke); c.drawCircle(x0 + 12 * u, y0 + 7 * u, 1.2f * u, stroke);
                c.drawCircle(x0 + 16 * u, y0 + 9 * u, 1.2f * u, stroke); break;
            case "image":
                ir.set(x0 + 3 * u, y0 + 5 * u, x0 + 21 * u, y0 + 19 * u); c.drawRoundRect(ir, 2 * u, 2 * u, stroke);
                path.moveTo(x0 + 4 * u, y0 + 17 * u); path.lineTo(x0 + 10 * u, y0 + 11 * u); path.lineTo(x0 + 14 * u, y0 + 15 * u);
                path.lineTo(x0 + 16 * u, y0 + 13 * u); path.lineTo(x0 + 20 * u, y0 + 17 * u); c.drawPath(path, stroke);
                c.drawCircle(x0 + 16 * u, y0 + 9 * u, 1.5f * u, stroke); break;
            default:
                c.drawCircle(cx, cy, 8 * u, stroke);
        }
    }
}

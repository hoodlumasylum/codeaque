package com.codeaque.launcher;

import android.content.Context;
import android.graphics.*;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** The whole home screen, drawn on one Canvas (no child views to lay out or recycle): cheap on a 2 GB RK3566.
 *  Off-screen tiles are skipped. Cover bitmaps are decoded off the UI thread and, from 0.10.51, pre-scaled to
 *  the tile size and cached. Gamepad is unchanged: D-pad / stick, L1 R1 change category, A open, B back, Y settings.
 *  Touch (0.10.29 / 0.10.51): one tap opens a tile. Swipe left or right moves between sections. A drag on the
 *  row (from anywhere except tabs and buttons) scrolls it; a fling coasts, then eases to a tile. Snap never
 *  runs against a live finger. The on-screen Back button does the same as controller B.
 *  Mouse (0.10.78): hover selects a tile (same highlight as the pad); a short click opens it; click-and-drag
 *  or the scroll wheel (vertical or horizontal) moves the game row or the tab strip, with a smooth fling;
 *  a drag does not open the tile; right-click is Back. Wi-Fi and camera in the top bar work the same way.
 *  Kids mode uses this path too, and still respects the parent PIN and Kids filters. */
public class HomeView extends View {
    public interface Host {
        void open(Catalog.Item item);
        void askRemoveFav(Catalog.Item item);
        void addFavSite();
        boolean installed(Catalog.Item item);
        void sound(String name);
        String scanNote(String catId);   // 0.10.9
        String effects();
        String status();      // "87%" or "87% charging", "" without a battery
        boolean online();
        Bitmap appIcon(Catalog.Item item);
        /** 0.10.17: Kids Games row: the listing's wide feature graphic, else the icon over a blurred copy of itself. */
        Bitmap wideCover(Catalog.Item item);
        Bitmap cover(Catalog.Item item);
        String wifi();        // 0.10.5: "Connected to Home" / "Wi-Fi off" / "Not connected"     // 0.9.5: a game picture (the app's own banner, or cached libretro box art), null = none   // an installed app's own icon (null for CodeAque's line-drawn tiles)
        /** 0.10.29: seven quick taps on the CodeAque logo. MainActivity shows the parent PIN only while Lock mode is on. */
        void logoSecret();
        /** 0.10.32 Box Set Mode: leave with Exit or a held Start/Select — no PIN. */
        void leaveBoxSet();
        /** 0.10.34: Roblox row is showing (or was just left). nearEnd means load the next public page. */
        void robloxRow(boolean on, boolean nearEnd);
        /** 0.10.37: Kids / Kids 12+ banner (profile + time left). Empty in adult mode. */
        String kidsBanner();
        /** 0.10.37: game tiles show Time's up when the daily limit has run out. */
        boolean timeUpTile(Catalog.Item item);
        boolean timeUpScreen();
        void timeUpAskParent();
        /** 0.10.49: lock-screen copy (daily limit vs allowed hours). */
        String timeUpHeadline();
        String timeUpMessage();
        /** 0.10.37: while a parent PIN dialog is open, tiles must not activate (wrong PIN / A / Enter must not launch). */
        boolean pinDialogOpen();
        /** 0.10.42 / 0.10.48: tabbed home with one row of Wi-Fi-sized tiles (Kids always; adult default unless Classic home is on). */
        boolean browseLayout();
        /** Description under a browse-list title; empty if none. */
        String itemBlurb(Catalog.Item item);
        /** Load the next page of a paged Kids/adult game tab when the list is near its end. */
        void kidsBrowseMore(String catId);
        /** 0.10.45: parent-PIN, then open Play Store pages for the ticked games (skip installed). */
        void installPicked(java.util.List<String> pkgs);
        /** 0.10.49 / 0.10.50: Kids search stays inside CodeAque. Never opens the raw Play Store search. */
        void runKidsSearch(String query);
        String kidsSearchStatus();
        void kidsSearchRetry();
        void kidsSearchMore();
        void runKidsSearchChip(String chip);
        String kidsSearchChip();
        void requestKidsIme();
        void hideKidsIme();
        /** 0.10.55: so Back / B can hide the system IME first, without leaving Search. */
        boolean imeVisible();
        void placeKidsSearchField(float left, float top, float right, float bottom, String text, String hint, boolean focused, boolean showField, float textPx, float padH, float padV);
        /** 0.10.59: Install stays on home and Kids until the update is actually installed. */
        default boolean updateReady() { return false; }
        default String updateReadyLabel() { return "Update ready"; }
        default void onUpdateInstall() {}
        /** 0.10.76: open B612 if installed, else its Play Store page. Adult mode only. */
        default void openCamera() {}
        /** 0.10.78: top-bar Wi-Fi icon (Kids and adult). */
        default void openWifi() {}
    }

    private Palette T = Palette.NEON;
    private int GOLD = T.gold, GOLD_D = T.goldDark, TXT = T.text, DIM = T.dim;
    private Bitmap wallpaper;
    public void setTheme(Palette t) { T = t; GOLD = t.gold; GOLD_D = t.goldDark; TXT = t.text; DIM = t.dim; invalidate(); }
    public void setWallpaper(Bitmap b) { wallpaper = b; invalidate(); }

    private final Host host;
    private List<Catalog.Category> cats = Catalog.categories();

    /** 0.10.34: swap one row's tiles (Roblox pages) without rebuilding the rest of the home or resetting the scroll. */
    public void replaceRow(String id, List<Catalog.Item> items) {
        cats = Catalog.replaceItems(cats, id, items);
        tabsDirty = true;
        tile = Math.max(0, Math.min(tile, Math.max(0, items().size() - 1)));
        if (scrollAnim > Math.max(0, items().size())) { scrollAnim = 0; tile = 0; }
        row.focusTile(tile, items().size());
        row.scroll = scrollAnim;
        invalidate();
    }

    /** 0.9.4: new rows after the installed apps / game folders were scanned; keeps the chosen category and tile. */
    public void setCategories(List<Catalog.Category> n) {
        String id = cats.get(cat).id;
        cats = n;
        tabsDirty = true;
        int i = Catalog.indexOf(cats, id);
        cat = i < 0 ? 0 : i; catAnim = cat;
        tile = Math.max(0, Math.min(tile, items().size() - 1));
        row.focusTile(tile, items().size());
        row.scroll = scrollAnim;
        invalidate();
        notifyRoblox(false);
    }
    private int cat = 0, tile = 0;       // 0.10.1: start on the Featured row, Roblox first
    private boolean inCats = false;
    private float catAnim = 0, scrollAnim = 0, t = 0, tabScroll, tabScrollTo, tabTotal;
    private float[] tabX, tabW;
    private boolean tabDrag, tabFollow = true;
    private boolean tabsDirty = true;
    private int tabsLaidN, tabsLaidW;
    private float tabsLaidS;
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
    private float downX, downY, lastTouchX, lastTouchY, s = 1;
    private long downTime;
    private boolean rowDrag, sectionSwipe, gestureChose;
    /** Tile-units per second while a finger fling is coasting. userScroll means the finger owns the scroll
     *  (focus-follow waits) until the fling slows down. Kept in sync with {@link #row}. */
    private float flingV;
    private boolean userScroll;
    /** 0.10.49: animated scale of the focused browse tile (1 = rest, 1.05 = subtle lift). */
    private float focusScale = 1f;
    final HomeScroll row = new HomeScroll();
    private Typeface tfBlack, tfBold, tfRegular, tfMedium;
    private final TileBmpCache tileBmps;
    private final java.util.Set<String> bakeQueued = java.util.Collections.synchronizedSet(new java.util.HashSet<String>());
    private Bitmap chromeSnap, phoenixScreen, phoenixBg;
    private int chromeW, chromeH, phoenixSw, phoenixSh, bakesThisFrame;
    private boolean chromeReady, phoenixTried, artPending, robloxNearPosted;
    private int[] coverPixels;
    private final Rect bakeSrc = new Rect();
    private final RectF bakeDst = new RectF();
    private final Paint tileBake = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final PorterDuffXfermode bakeDstIn = new PorterDuffXfermode(PorterDuff.Mode.DST_IN);
    private long lastRobloxNearAt;
    private final long[] frameNs = new long[120];
    private int frameAt, frameSamples;
    private long frameWorstNs;
    String lastFrameReport = "";
    private VelocityTracker velocity;
    private int logoTaps; private long logoTapAt;
    private final RectF logoTop = new RectF(), logoCenter = new RectF(), backBtn = new RectF();
    private final RectF cameraHit = new RectF();
    private final RectF wifiHit = new RectF();
    private float wifiCx;
    private String hoverChrome = "";   // 0.10.78: "camera" / "wifi" while the mouse is over a top-bar icon
    private final RectF installBtn = new RectF();
    private final RectF updateBar = new RectF(), updateBtn = new RectF();
    private final RectF searchQueryBox = new RectF(), searchRetryBox = new RectF(), searchKbBox = new RectF(), searchChipBox = new RectF();
    private final RectF[] searchChipRects = new RectF[KidsPlay.SEARCH_CHIPS.length];
    private String searchQ = "";
    private boolean searchShift, searchSym, searchKbFallback;
    private int kbRow, kbCol, searchChipSel = -1, searchChipNav;
    private static final int SF_BOX = 0, SF_CHIPS = 1, SF_KEYS = 2, SF_TILES = 3;
    private int searchFocus = SF_BOX;
    private final java.util.LinkedHashSet<String> picked = new java.util.LinkedHashSet<>();
    private long holdAt; private int holdTile = -1; private boolean longPressFired, activateHeld, holdFav;
    private final RectF promptOpen = new RectF(), promptBack = new RectF(), pill = new RectF();
    private long clockAt; private String clockTxt = "";

    public HomeView(Context c, Host host) {
        super(c);
        this.host = host;
        tile = 0; cat = 0; catAnim = 0;          // 0.10.1: start on Featured (Roblox, Minecraft, then Steam)
        row.reset(0, 0);
        setFocusable(true); setFocusableInTouchMode(true);
        tfRegular = Typeface.create("sans-serif", Typeface.NORMAL);
        tfBold = Typeface.create("sans-serif", Typeface.BOLD);
        tfMedium = Typeface.create("sans-serif-medium", Typeface.BOLD);
        tfBlack = Typeface.create("sans-serif-black", Typeface.BOLD);
        if (tfBlack == null) tfBlack = tfBold;
        int cacheKb = 8 * 1024;
        try {
            android.app.ActivityManager am = (android.app.ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
            if (am != null) cacheKb = Math.max(4096, Math.min(16384, am.getMemoryClass() * 1024 / 8));
        } catch (Exception ignored) { }
        tileBmps = new TileBmpCache(cacheKb);
        try {
            logo = BitmapFactory.decodeStream(c.getAssets().open("logo.png"));
            splash = BitmapFactory.decodeStream(c.getAssets().open("splash.jpg"));   // 1280x720 copy of the 1672x941 art   // the white CodeAque logo (3:1)
        } catch (Exception ignored) { }
        java.util.Random rnd = new java.util.Random(7);
        for (int i = 0; i < motes.length; i++) motes[i] = rnd.nextFloat();
        stroke.setStyle(Paint.Style.STROKE); stroke.setStrokeCap(Paint.Cap.ROUND); stroke.setStrokeJoin(Paint.Join.ROUND);
        for (int i = 0; i < searchChipRects.length; i++) searchChipRects[i] = new RectF();
    }

    // ---------- input ----------
    private boolean boxset;
    private java.util.List<Catalog.Item> boxItems = java.util.Collections.emptyList();
    public void setBoxSet(boolean on, java.util.List<Catalog.Item> items) {
        boxset = on;
        boxItems = items != null ? items : java.util.Collections.emptyList();
        if (on) { inCats = false; tile = 0; }
        invalidate();
    }
    public boolean boxSet() { return boxset; }
    private List<Catalog.Item> items() { return boxset ? boxItems : cats.get(cat).items; }
    private Catalog.Item currentItem() {
        List<Catalog.Item> l = items();
        if (l.isEmpty() || tile < 0 || tile >= l.size()) return null;
        return l.get(tile);
    }

    private void setCat(int c) {
        c = (c + cats.size()) % cats.size();
        if (c != cat) { cat = c; tile = 0; userScroll = false; flingV = 0; scrollAnim = 0; row.reset(0, 0); chromeReady = false; host.sound("hhcat");
            if (onSearch()) { searchFocus = SF_BOX; kbRow = 0; kbCol = 0; host.requestKidsIme(); }
            else host.hideKidsIme();
        }
        tabFollow = true;
        invalidate();
        notifyRoblox(false);
    }
    private void setTile(int i) {
        userScroll = false; flingV = 0;   // the controller takes the scroll back; focus-follow eases to this tile
        i = Math.max(0, Math.min(items().size() - 1, i));
        if (i != tile) { tile = i; host.sound("hhmove"); }
        row.focusTile(tile, items().size());
        row.scroll = scrollAnim;
        invalidate();
        notifyRoblox(true);
    }
    private void activate() {
        if (host.pinDialogOpen()) return;
        Catalog.Item it = items().get(tile);
        host.sound("hhselect");
        if (it.action != null && it.action.startsWith("goto:")) {
            String id = it.action.substring(5);
            int i = Catalog.indexOf(cats, id);
            if (i < 0 && "roblox".equals(id)) i = Catalog.indexOf(cats, "kroblox");  // 0.10.34: Kids row id
            setCat(i); return;
        }
        if (!it.isAction() && it.webUrl == null && !host.installed(it)) { prompt = it; invalidate(); return; }
        host.open(it);
    }

    @Override public boolean onKeyDown(int code, KeyEvent e) {
        if (splash != null) { splash = null; invalidate(); return true; }     // any button skips the splash
        if (host.pinDialogOpen()) return true;   // 0.10.37: don't let A / Enter leak through a PIN dialog and launch the tile
        if (kids && host.timeUpScreen()) {
            if (code == KeyEvent.KEYCODE_BUTTON_A || code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_DPAD_CENTER
                    || code == KeyEvent.KEYCODE_BUTTON_START)
                host.timeUpAskParent();
            return true;   // Back does not dismiss it; a parent PIN (or hold Select for Grown-ups) does
        }
        if (prompt != null) {
            if (code == KeyEvent.KEYCODE_BUTTON_A || code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_DPAD_CENTER) { Catalog.Item it = prompt; prompt = null; host.open(it); }
            else if (code == KeyEvent.KEYCODE_BUTTON_B || code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE) { prompt = null; host.sound("hhback"); }
            invalidate(); return true;
        }
        if (onSearch() && handleSearchKey(code, e)) return true;
        switch (code) {
            case KeyEvent.KEYCODE_BUTTON_L1: case KeyEvent.KEYCODE_PAGE_UP: case KeyEvent.KEYCODE_Q:
                if (boxset) { setTile(tile - 1); return true; }
                setCat(cat - 1); return true;
            case KeyEvent.KEYCODE_BUTTON_R1: case KeyEvent.KEYCODE_PAGE_DOWN: case KeyEvent.KEYCODE_E:
                if (boxset) { setTile(tile + 1); return true; }
                setCat(cat + 1); return true;
            case KeyEvent.KEYCODE_DPAD_LEFT: if (inCats) setCat(cat - 1); else setTile(tile - 1); return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT: if (inCats) setCat(cat + 1); else setTile(tile + 1); return true;
            case KeyEvent.KEYCODE_DPAD_UP:
                if (!inCats) {
                    inCats = true; host.sound("hhmove"); invalidate();
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (inCats) { inCats = false; host.sound("hhmove"); invalidate(); }
                return true;
            case KeyEvent.KEYCODE_BUTTON_A: case KeyEvent.KEYCODE_ENTER: case KeyEvent.KEYCODE_DPAD_CENTER:
                // 0.10.45: short press opens; hold A / Enter ticks the tile for multi-select install
                if (e.getRepeatCount() == 0) e.startTracking();
                return true;
            case KeyEvent.KEYCODE_BUTTON_START:
                if (boxset) { if (e.getRepeatCount() == 0) e.startTracking(); return true; }
                if (inCats) { inCats = false; host.sound("hhselect"); invalidate(); } else activate(); return true;
            case KeyEvent.KEYCODE_BUTTON_B: case KeyEvent.KEYCODE_BACK: case KeyEvent.KEYCODE_ESCAPE:
                goBack(); return true;    // a home screen never closes
            case KeyEvent.KEYCODE_BUTTON_Y: case KeyEvent.KEYCODE_MENU: case KeyEvent.KEYCODE_BUTTON_SELECT:
                if (boxset || kids) { if (e.getRepeatCount() == 0) e.startTracking(); return true; }   // Box Set: hold to leave (no PIN). Kids: hold for grown-up switch.
                if (!inCats && Catalog.favSaved(currentItem())) { host.askRemoveFav(currentItem()); return true; }
                setCat(Catalog.indexOf(cats, "settings")); inCats = false; return true;
        }
        return super.onKeyDown(code, e);
    }

    @Override public boolean onKeyLongPress(int code, KeyEvent e) {
        if (boxset && (code == KeyEvent.KEYCODE_BUTTON_SELECT || code == KeyEvent.KEYCODE_BUTTON_START
                || code == KeyEvent.KEYCODE_BUTTON_Y || code == KeyEvent.KEYCODE_MENU)) {
            host.leaveBoxSet(); return true;
        }
        if (kids && (code == KeyEvent.KEYCODE_BUTTON_SELECT || code == KeyEvent.KEYCODE_BUTTON_Y || code == KeyEvent.KEYCODE_MENU)) {
            host.open(Catalog.KIDS_OFF); return true;
        }
        if ((code == KeyEvent.KEYCODE_BUTTON_A || code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_DPAD_CENTER)
                && !inCats && browseList()) {
            togglePick(tile); activateHeld = true; return true;
        }
        return super.onKeyLongPress(code, e);
    }

    @Override public boolean onKeyUp(int code, KeyEvent e) {
        if (code == KeyEvent.KEYCODE_BUTTON_A || code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_DPAD_CENTER) {
            if (activateHeld) { activateHeld = false; return true; }
            if (e.isTracking() && !e.isCanceled()) {
                if (inCats) { inCats = false; host.sound("hhselect"); invalidate(); }
                else activate();
            }
            return true;
        }
        return super.onKeyUp(code, e);
    }

    @Override public boolean onGenericMotionEvent(MotionEvent e) {
        if (HomeMouse.isMouseWheel(e.getAction(), e.getSource())) {
            float x = e.getX(), y = e.getY();
            if (projector) { x = (x - getWidth() * SAFE) / (1 - 2 * SAFE); y = (y - getHeight() * SAFE) / (1 - 2 * SAFE); }
            float h = e.getAxisValue(MotionEvent.AXIS_HSCROLL);
            float v = e.getAxisValue(MotionEvent.AXIS_VSCROLL);
            boolean onTabs = !boxset && y >= tabStripTop() && y <= catsBottom();
            if (onTabs) {
                layoutTabsIfNeeded();
                tabFollow = false;
                float px = Math.abs(h) >= Math.abs(v) && h != 0f ? h * HomeMouse.WHEEL_PX * s : -v * HomeMouse.WHEEL_PX * s;
                tabScroll = tabScrollTo = clampTabScroll(tabScroll + px);
                invalidate();
                return true;
            }
            float step = browseList() ? (browseTileW() + browseGap()) : (tileW() + tileGap());
            float d = HomeMouse.wheelToTiles(h, v, Math.max(1f, step), HomeMouse.WHEEL_PX * s);
            if (d == 0f) return true;
            row.scroll = scrollAnim;
            row.pointerDown();
            row.pointerMove(d, maxScroll());
            row.pointerUp(0, visibleTiles(), maxScroll(), items().size());
            scrollAnim = row.scroll;
            tile = row.tile;
            userScroll = row.busy();
            inCats = false;
            invalidate();
            return true;
        }
        // left stick / hat as a D-pad, with repeat delay
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

    @Override public boolean onHoverEvent(MotionEvent e) {
        float x = e.getX(), y = e.getY();
        if (projector) { x = (x - getWidth() * SAFE) / (1 - 2 * SAFE); y = (y - getHeight() * SAFE) / (1 - 2 * SAFE); }
        int act = e.getActionMasked();
        if (act == MotionEvent.ACTION_HOVER_EXIT) {
            if (!hoverChrome.isEmpty()) { hoverChrome = ""; invalidate(); }
            return true;
        }
        boolean onCam = !cameraHit.isEmpty() && cameraHit.contains(x, y);
        boolean onWifi = !wifiHit.isEmpty() && wifiHit.contains(x, y);
        boolean onTabs = !boxset && y >= tabStripTop() && y <= catsBottom();
        int browse = hitBrowseItem(x, y);
        int classic = -1;
        if (browse < 0 && !browseList() && y > tilesTop() && y < tilesTop() + tileH()) {
            float step = tileW() + tileGap();
            classic = (int) Math.floor((x - tilesLeft() + scrollAnim * step) / step);
            if (classic < 0 || classic >= items().size()) classic = -1;
        }
        String chrome = HomeMouse.hoverTarget(onCam, onWifi, false, false);
        boolean dirty = !chrome.equals(hoverChrome);
        hoverChrome = chrome;
        if (browse >= 0) {
            if (inCats || tile != browse) {
                inCats = false;
                tile = browse;
                row.focusTile(tile, items().size());
                dirty = true;
            }
        } else if (classic >= 0) {
            if (inCats || tile != classic) {
                inCats = false;
                tile = classic;
                row.focusTile(tile, items().size());
                dirty = true;
            }
        } else if (onTabs) {
            int i = hitTab(x, y);
            if (i >= 0 && !inCats) { inCats = true; dirty = true; }
        }
        if (dirty) invalidate();
        return true;
    }

    /** Same as controller B: leave a prompt, or step back to the category row, then to the first section. */
    /** 0.10.55: hide the custom keyboard or the system IME. True if something was up. */
    boolean hideAnyKeyboard() {
        boolean custom = searchKbFallback;
        boolean ime = host != null && host.imeVisible();
        if (custom) {
            searchKbFallback = false;
            if (searchFocus == SF_KEYS) searchFocus = SF_BOX;
        }
        if (host != null) host.hideKidsIme();
        if (custom || ime) { invalidate(); return true; }
        return false;
    }

    boolean searchKeyboardUp() {
        return searchKbFallback || (host != null && host.imeVisible());
    }

    /**
     * 0.10.56: true if a screen-space touch is on the Search text box or the
     * custom on-screen keyboard, so the Activity should not dismiss it.
     */
    boolean touchOnSearchFieldOrKeyboard(android.view.MotionEvent ev) {
        if (ev == null || !onSearch()) return false;
        int[] loc = new int[2];
        getLocationOnScreen(loc);
        float x = ev.getRawX() - loc[0], y = ev.getRawY() - loc[1];
        if (projector) {
            x = (x - getWidth() * SAFE) / (1 - 2 * SAFE);
            y = (y - getHeight() * SAFE) / (1 - 2 * SAFE);
        }
        if (searchQueryBox.contains(x, y)) return true;
        return searchKbFallback && !searchKbBox.isEmpty() && searchKbBox.contains(x, y);
    }

    private void goBack() {
        if (kids && host.timeUpScreen()) return;   // stays up until a parent PIN
        if (prompt != null) { prompt = null; host.sound("hhback"); invalidate(); return; }
        if (boxset) { host.leaveBoxSet(); return; }
        // Hide the pop-up keyboard first (custom or system IME). Back / B should not also leave Search.
        if (onSearch() && searchKeyboardUp()) { hideAnyKeyboard(); host.sound("hhback"); return; }
        if (onSearch() && !inCats && searchFocus == SF_TILES) {
            searchFocus = searchKbFallback ? SF_KEYS : SF_CHIPS; host.sound("hhback"); invalidate(); return;
        }
        if (onSearch() && !inCats && searchFocus == SF_KEYS) {
            searchFocus = SF_CHIPS; host.sound("hhback"); invalidate(); return;
        }
        if (onSearch() && !inCats && searchFocus == SF_CHIPS) {
            searchFocus = SF_BOX; host.requestKidsIme(); host.sound("hhback"); invalidate(); return;
        }
        if (onSearch() && !inCats && searchFocus == SF_BOX) {
            host.hideKidsIme(); inCats = true; host.sound("hhback"); invalidate(); return;
        }
        if (!inCats) inCats = true; else setCat(0);
        host.sound("hhback"); invalidate();
    }

    private float maxScroll() {
        if (browseList()) {
            float step = browseTileW() + browseGap();
            if (step <= 1) return 0;
            float vis = Math.max(1, (getWidth() - 2 * browsePad()) / step);
            return Math.max(0, items().size() - vis);
        }
        float step = tileW() + tileGap();
        if (step <= 1) return 0;
        float vis = Math.max(1, (getWidth() - 2 * tilesLeft()) / step);
        return Math.max(0, items().size() - vis);
    }
    boolean browseList() {
        if (boxset || cats.isEmpty()) return false;
        if (!host.browseLayout()) return false;
        // Utility rows with a handful of actions keep the compact tile row.
        String id = cats.get(Math.max(0, Math.min(cat, cats.size() - 1))).id;
        if ("kthemes".equals(id) || "kwifi".equals(id) || "kparent".equals(id) || "settings".equals(id) || "power".equals(id)) return false;
        return true;
    }
    /** 0.10.48: game tiles use the same size, gap and placement as the Wi-Fi / Update action tiles. */
    private float browsePad() { return 24 * s; }
    private float browseGap() { return 16 * s * big(); }
    private float browseTileH() { return 136 * s * big(); }
    private float browseTileW() { return 188 * s * big(); }
    private float browseTop() { return getHeight() - footH() - browseTileH(); }
    private float browseBottom() { return getHeight() - footH(); }
    private float clampScroll(float v) { float m = maxScroll(); return v < 0 ? 0 : v > m ? m : v; }

    private String pickKey(Catalog.Item it) {
        if (it == null) return null;
        String p = it.storePackage();
        if (p != null && !p.isEmpty()) return p;
        return null;
    }
    private boolean isPicked(Catalog.Item it) {
        String k = pickKey(it);
        return k != null && picked.contains(k);
    }
    private void togglePick(int i) {
        if (i < 0 || i >= items().size()) return;
        String k = pickKey(items().get(i));
        if (k == null) return;
        if (!picked.remove(k)) picked.add(k);
        host.sound("hhselect");
        invalidate();
    }
    public void clearPicks() { picked.clear(); installBtn.setEmpty(); invalidate(); }
    private int hitBrowseItem(float x, float y) {
        if (!browseList() || y < browseTop() || y > browseBottom()) return -1;
        float left = browsePad(), tw = browseTileW(), gap = browseGap(), th = browseTileH();
        if (y > browseTop() + th) return -1;
        int i = (int) Math.floor((x - left) / (tw + gap) + scrollAnim);
        if (i < 0 || i >= items().size()) return -1;
        float x0 = left + (i - scrollAnim) * (tw + gap);
        if (x < x0 || x > x0 + tw) return -1;
        return i;
    }
    private boolean onInstallBtn(float x, float y) { return !picked.isEmpty() && installBtn.contains(x, y); }
    private boolean onUpdateInstallBtn(float x, float y) {
        return host.updateReady() && !updateBtn.isEmpty() && updateBtn.contains(x, y);
    }

    /** Persistent update strip: stays until the installed version is no longer older. No timeout. */
    private void updateReadyBar(Canvas c, int W, int H) {
        if (!host.updateReady()) { updateBar.setEmpty(); updateBtn.setEmpty(); return; }
        float den = getResources().getDisplayMetrics().density;
        float bh = Math.max(48 * s * (kids ? 1.1f : 1f), 48 * den);
        float margin = 10 * s;
        float bot = H - margin;
        if (!backBtn.isEmpty() && backBtn.top > H * 0.4f) bot = Math.min(bot, backBtn.top - 8 * s);
        if (!installBtn.isEmpty() && installBtn.top > H * 0.4f) bot = Math.min(bot, installBtn.top - 8 * s);
        String lab = host.updateReadyLabel();
        String go = UpdateOffer.INSTALL;
        p.setShader(null);
        p.setFakeBoldText(true);
        p.setTextSize(Math.max(16 * s, 14 * den) * (kids ? 1.1f : 1f));
        p.setTextAlign(Paint.Align.LEFT);
        float goW = Math.max(120 * s, p.measureText(go) + 36 * s);
        float width = Math.min(W - 2 * margin, Math.max(420 * s, p.measureText(lab) + goW + 48 * s));
        float left = (W - width) / 2f;
        updateBar.set(left, bot - bh, left + width, bot);
        updateBtn.set(updateBar.right - goW - 8 * s, updateBar.top + 6 * s, updateBar.right - 8 * s, updateBar.bottom - 6 * s);
        p.setColor(0xF2161A22);
        c.drawRoundRect(updateBar, 14 * s, 14 * s, p);
        stroke.setShader(null); stroke.setStrokeWidth(2.2f * s); stroke.setColor(GOLD);
        c.drawRoundRect(updateBar, 14 * s, 14 * s, stroke);
        p.setColor(0xFFFFFFFF);
        float ty = updateBar.centerY() + p.getTextSize() * 0.35f;
        c.drawText(lab, updateBar.left + 16 * s, ty, p);
        p.setColor(0xF2E8C872);
        c.drawRoundRect(updateBtn, bh / 3, bh / 3, p);
        p.setColor(0xFF0C0E12);
        p.setTextAlign(Paint.Align.CENTER);
        c.drawText(go, updateBtn.centerX(), updateBtn.centerY() + p.getTextSize() * 0.35f, p);
        p.setFakeBoldText(false);
        p.setTextAlign(Paint.Align.LEFT);
    }

    private boolean onRobloxRow() {
        if (boxset || cats.isEmpty()) return false;
        String id = cats.get(Math.max(0, Math.min(cat, cats.size() - 1))).id;
        return "roblox".equals(id) || "kroblox".equals(id);
    }
    private boolean robloxNearEnd() {
        if (!onRobloxRow()) return false;
        int n = items().size();
        if (n == 0) return false;
        int pos = Math.max(tile, (int) Math.ceil(scrollAnim));
        return n - pos <= 4;
    }
    private Boolean lastRobloxOn;
    /** Tell MainActivity when Roblox mode is opened or the row is near its end (so it can fetch the next public page). */
    private void notifyRoblox(boolean considerEnd) {
        boolean on = onRobloxRow();
        boolean near = considerEnd && robloxNearEnd();
        if (lastRobloxOn == null || lastRobloxOn.booleanValue() != on) {
            lastRobloxOn = on;
            host.robloxRow(on, near);
        } else if (on && near) host.robloxRow(true, true);
        if (considerEnd && browseList() && nearBrowseEnd()) {
            String id = cats.get(Math.max(0, Math.min(cat, cats.size() - 1))).id;
            if (KidsPlay.catFromId(id) != null) host.kidsBrowseMore(id);
            if ("ksearch".equals(id)) host.kidsSearchMore();
        }
    }
    private boolean nearBrowseEnd() {
        int n = items().size();
        if (n == 0) return false;
        int pos = Math.max(tile, (int) Math.ceil(scrollAnim));
        return n - pos <= 4;
    }

    /** Seven taps, each within 0.9 s of the last, open the hidden parent menu (only useful while Lock mode is on). */
    private void logoHit() {
        long now = android.os.SystemClock.uptimeMillis();
        if (now - logoTapAt > 900) logoTaps = 0;
        logoTapAt = now;
        if (++logoTaps >= 7) { logoTaps = 0; host.logoSecret(); }
    }

    private void onTap(float x, float y, float H) {
        if (onUpdateInstallBtn(x, y)) { host.onUpdateInstall(); return; }
        if (!cameraHit.isEmpty() && cameraHit.contains(x, y)) { host.openCamera(); return; }
        if (!wifiHit.isEmpty() && wifiHit.contains(x, y)) { host.openWifi(); return; }
        if (kids && host.timeUpScreen()) {
            if (r2.contains(x, y) || (y > getHeight() * 0.60f && y < getHeight() * 0.80f)) host.timeUpAskParent();
            else if (logoTop.contains(x, y) || logoCenter.contains(x, y)) logoHit();
            return;
        }
        if (prompt != null) {
            if (promptOpen.contains(x, y)) { Catalog.Item it = prompt; prompt = null; host.open(it); }
            else { prompt = null; host.sound("hhback"); }
            invalidate(); return;
        }
        if (onInstallBtn(x, y)) { confirmInstall(); return; }
        if (backBtn.contains(x, y)) { goBack(); return; }
        if (onSearch() && !searchRetryBox.isEmpty() && searchRetryBox.contains(x, y)) { host.kidsSearchRetry(); return; }
        if (onSearch() && searchKbFallback && tapSearchKey(x, y)) return;
        if (onSearch() && tapSearchChip(x, y)) {
            if (searchKeyboardUp() && (searchKbBox.isEmpty() || !searchKbBox.contains(x, y))) hideAnyKeyboard();
            return;
        }
        if (onSearch() && searchQueryBox.contains(x, y)) {
            inCats = false; searchFocus = SF_BOX; host.requestKidsIme(); invalidate(); return;
        }
        // 0.10.55: a tap outside the field or the custom keyboard closes it.
        if (onSearch() && searchKeyboardUp()
                && !searchQueryBox.contains(x, y)
                && (searchKbBox.isEmpty() || !searchKbBox.contains(x, y))) {
            hideAnyKeyboard();
        }
        if (logoTop.contains(x, y) || logoCenter.contains(x, y)) { logoHit(); return; }
        if (!boxset && y <= catsBottom() && y >= tabStripTop()) {
            int i = hitTab(x, y);
            if (i >= 0) { setCat(i); inCats = false; }
            return;
        }
        if (browseList() && y >= browseTop() && y <= browseBottom()) {
            int i = hitBrowseItem(x, y);
            if (i >= 0) { inCats = false; tile = i; activate(); }
            return;
        }
        if (y > tilesTop() && y < tilesTop() + tileH()) {
            float step = tileW() + tileGap();
            int i = (int) Math.floor((x - tilesLeft() + scrollAnim * step) / step);
            if (i >= 0 && i < items().size()) { inCats = false; tile = i; activate(); }   // one tap opens
        }
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        float x = e.getX(), y = e.getY(), H = getHeight();
        if (projector) { x = (x - getWidth() * SAFE) / (1 - 2 * SAFE); y = (y - H * SAFE) / (1 - 2 * SAFE); }
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (splash != null) { splash = null; invalidate(); return true; }
                if (HomeMouse.isRightClick(e.getButtonState())) {
                    int fi = hitHomeTile(x, y);
                    if (fi >= 0 && Catalog.favSaved(items().get(fi))) { inCats = false; tile = fi; host.askRemoveFav(items().get(fi)); return true; }
                    goBack(); return true;
                }
                downX = x; downY = y; lastTouchX = x; lastTouchY = y; downTime = e.getEventTime();
                rowDrag = false; sectionSwipe = false; gestureChose = false; tabDrag = false;
                longPressFired = false; holdTile = -1; holdAt = 0; holdFav = false;
                if (velocity != null) velocity.recycle();
                velocity = VelocityTracker.obtain();
                velocity.addMovement(e);
                userScroll = false; flingV = 0;
                row.pointerDown();
                row.scroll = scrollAnim;
                row.tile = tile;
                if (!onInstallBtn(x, y) && !onUpdateInstallBtn(x, y)) {
                    int hi = hitBrowseItem(x, y);
                    if (hi >= 0) { holdTile = hi; holdAt = e.getEventTime() + 450; postInvalidateDelayed(450); }
                    else {
                        int fi = hitHomeTile(x, y);
                        if (fi >= 0 && Catalog.favSaved(items().get(fi))) { holdTile = fi; holdFav = true; holdAt = e.getEventTime() + 450; postInvalidateDelayed(450); }
                    }
                }
                return true;
            case MotionEvent.ACTION_MOVE:
                if (velocity != null) velocity.addMovement(e);
                float dx = x - downX, dy = y - downY;
                if (!gestureChose && Math.hypot(dx, dy) > 28 * s) {
                    gestureChose = true;
                    holdTile = -1; holdAt = 0;
                    boolean horiz = Math.abs(dx) > Math.abs(dy) * 1.2f;
                    boolean onList = browseList() && downY >= browseTop() && downY <= browseBottom();
                    boolean onTiles = downY > tilesTop() && downY < tilesTop() + tileH();
                    boolean onTabs = !boxset && downY >= tabStripTop() && downY <= catsBottom();
                    boolean rowZone = browseRowScrollZone(downX, downY);
                    if (onTabs && horiz) { tabDrag = true; tabFollow = false; }
                    else if (!horiz) { /* a vertical rub is not a tap and not a section change */ }
                    else if (rowZone && maxScroll() >= 0.5f) rowDrag = true;   // 0.10.49: swipe anywhere except tabs / buttons
                    else if ((!onTiles && !onList) || maxScroll() < 0.5f) sectionSwipe = true;
                    else {
                        float vx = Math.abs(dx) / Math.max(1f, e.getEventTime() - downTime);   // px per ms
                        if (vx > 1.15f) sectionSwipe = true;   // a flick changes section (classic / utility rows)
                        else rowDrag = true;
                    }
                }
                if (tabDrag) {
                    layoutTabsIfNeeded();
                    tabScroll = tabScrollTo = clampTabScroll(tabScroll + (lastTouchX - x));
                    lastTouchX = x;
                    invalidate();
                } else if (rowDrag) {
                    userScroll = true; flingV = 0;
                    float step = browseList() ? (browseTileW() + browseGap()) : (tileW() + tileGap());
                    float d = (lastTouchX - x) / Math.max(1f, step);
                    row.dragging = true;
                    row.pointerMove(d, maxScroll());
                    scrollAnim = row.scroll;
                    lastTouchX = x;
                    invalidate();
                }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                holdTile = -1; holdAt = 0;
                if (velocity != null) { velocity.addMovement(e); velocity.computeCurrentVelocity(1000); }
                boolean tap = e.getActionMasked() == MotionEvent.ACTION_UP && !gestureChose && !longPressFired && Math.hypot(x - downX, y - downY) < 28 * s;
                if (tap) onTap(x, y, H);
                else if (sectionSwipe && !boxset && e.getActionMasked() == MotionEvent.ACTION_UP) {
                    if (x - downX < -40 * s) setCat(cat + 1);
                    else if (x - downX > 40 * s) setCat(cat - 1);
                } else if (rowDrag && e.getActionMasked() == MotionEvent.ACTION_UP && velocity != null) {
                    float vis = visibleTiles();
                    float step = Math.max(1f, browseList() ? (browseTileW() + browseGap()) : (tileW() + tileGap()));
                    flingV = -velocity.getXVelocity() / step;   // finger left -> later tiles
                    row.scroll = scrollAnim;
                    row.pointerUp(flingV, vis, maxScroll(), items().size());
                    flingV = row.vel;
                    userScroll = row.busy();
                    if (!row.busy()) tile = row.tile;
                    captureChrome();
                    invalidate();
                } else if (row.dragging && e.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                    row.cancelPointer(visibleTiles(), maxScroll(), items().size());
                    scrollAnim = row.scroll; tile = row.tile; userScroll = false; flingV = 0;
                }
                if (!rowDrag && row.dragging && (e.getActionMasked() == MotionEvent.ACTION_UP || e.getActionMasked() == MotionEvent.ACTION_CANCEL)) {
                    // Down started a pointer session; a tap / cancelled gesture must not leave dragging stuck
                    // or the next onDraw would skip snap forever... actually pointerDown is on every DOWN.
                    // If we never entered rowDrag, drop the drag flag so D-pad snap still works.
                    row.dragging = false;
                }
                if (velocity != null) { velocity.recycle(); velocity = null; }
                rowDrag = false; sectionSwipe = false; tabDrag = false;
                return true;
        }
        return super.onTouchEvent(e);
    }

    /** 0.10.49: the game row can be dragged from the logo, the background, or below the tiles — not only on the tiles themselves. */
    private boolean browseRowScrollZone(float x, float y) {
        if (!browseList() || boxset) return false;
        if (!boxset && y >= tabStripTop() && y <= catsBottom()) return false;
        if (backBtn.contains(x, y)) return false;
        if (!installBtn.isEmpty() && installBtn.contains(x, y)) return false;
        if (onUpdateInstallBtn(x, y)) return false;
        if (onSearch() && inSearchUi(x, y)) return false;
        return true;
    }

    public void showCloud() { setCat(Catalog.indexOf(cats, "cloud")); tile = 0; inCats = false; tabFollow = true; invalidate(); }
    public void focus(String catId, int i) { cat = Math.max(0, Catalog.indexOf(cats, catId)); tile = i; catAnim = cat; inCats = false; tabFollow = true; invalidate(); notifyRoblox(false); }

    // ---------- geometry (scaled from the 1280x720 design) ----------
    private float catsBottom() { return HomeTabs.stripBot(getHeight()); }
    private float tabStripTop() { return HomeTabs.stripTop(getHeight()); }
    private float tabMinGap() { return HomeTabs.GAP * s; }
    private float tabPad() { return HomeTabs.PAD * s; }
    private float tabMaxScroll() { return Math.max(0, tabTotal - getWidth()); }
    private float clampTabScroll(float v) { float m = tabMaxScroll(); return v < 0 ? 0 : v > m ? m : v; }

    /** Widths are measured only when the category list, width, or scale changes — never per scroll frame. */
    private void layoutTabsIfNeeded() {
        int n = cats.size();
        int w = getWidth();
        if (!HomeTabs.needsLayout(n, w, s, tabsLaidN, tabsLaidW, tabsLaidS, tabsDirty)
                && tabX != null && tabX.length == n) return;
        layoutTabs();
        tabsLaidN = n;
        tabsLaidW = w;
        tabsLaidS = s;
        tabsDirty = false;
    }

    /** Each tab is at least wide enough for its single-line label; gaps never shrink. */
    private void layoutTabs() {
        int n = cats.size();
        if (n == 0) { tabX = new float[0]; tabW = new float[0]; tabTotal = 0; return; }
        if (tabX == null || tabX.length != n) { tabX = new float[n]; tabW = new float[n]; }
        p.setTypeface(tfMedium != null ? tfMedium : tfBold);
        p.setTextSize(HomeTabs.labelSize(s));
        float x = tabPad();
        for (int i = 0; i < n; i++) {
            float tw = p.measureText(cats.get(i).label);
            tabW[i] = Math.max(HomeTabs.MIN_WIDTH * s, tw + 2 * tabPad());
            tabX[i] = x;
            x += tabW[i] + tabMinGap();
        }
        tabTotal = x - tabMinGap() + tabPad();
    }
    private void ensureTabInView(int i) {
        if (tabX == null || i < 0 || i >= tabX.length) return;
        float margin = 12 * s, left = tabX[i], right = tabX[i] + tabW[i], visL = tabScrollTo, visR = tabScrollTo + getWidth();
        if (left < visL + margin) tabScrollTo = left - margin;
        else if (right > visR - margin) tabScrollTo = right - getWidth() + margin;
        tabScrollTo = clampTabScroll(tabScrollTo);
    }
    private int hitTab(float x, float y) {
        if (boxset || y < tabStripTop() || y > catsBottom() || tabX == null) return -1;
        float px = x + tabScroll;
        for (int i = 0; i < tabX.length; i++)
            if (px >= tabX[i] && px < tabX[i] + tabW[i]) return i;
        return -1;
    }
    // 0.10.2 Kids mode: bigger tiles and labels (readable at 640x480), hold Select to reach the grown-up switch
    private boolean kids;
    public void setKids(boolean k) {
        if (kids == k) return;
        kids = k;
        cameraHit.setEmpty();
        chromeReady = false;   // top-bar camera icon is adult-only; rebuild the frozen chrome
        invalidate();
    }
    static final float KIDS_SCALE = 1.7f;
    private float big() { return kids ? KIDS_SCALE : 1f; }
    private boolean covers() {
        if (boxset) return true;
        String id = cats.get(cat).id;
        if (Catalog.kidsBrowseId(id)) return true;
        if (kids) return id.equals("kplay") || id.equals("kgames") || gameRow(id);
        return id.equals("mygames") || id.equals("steam") || id.equals("trilogies") || id.equals("coop") || id.equals("roblox") || gameRow(id);
    }
    /** 0.10.12: the Dreamcast and handheld game rows use portrait covers (player's own picture or a title card). */
    private static boolean gameRow(String id) { return id.equals("dreamcast") || id.equals("kdc") || id.equals("psp") || id.equals("kpsp"); }      // 0.9.5: portrait box-art tiles
    /** 0.10.6: the Featured / Kids Play row uses wide 16:9-ish covers (feature graphics, Roblox art). */
    private boolean wide() { String id = cats.get(cat).id; return id.equals("steam") || id.equals("kplay") || (kids && id.equals("kgames")); }
    private float tileH() { return (wide() ? (kids ? 118 : 164) : covers() ? 200 : 136) * s * big(); }
    private float tileW() { return (wide() ? (kids ? 220 : 292) : covers() ? 144 : 188) * s * big(); }
    private float tileGap() { return 16 * s * big(); }
    /** Room for the on-screen Back button (at least 48dp) under the tiles. */
    private float footH() {
        float den = getResources().getDisplayMetrics().density;
        return Math.max(78 * s, 56 * den);
    }
    private float tilesTop() { return getHeight() - footH() - tileH(); }
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
        fireTileHold();
        float vis = visibleTiles(), maxScroll = maxScroll();
        int nItems = items().size();
        // Keep physics in HomeScroll. A live finger (row.dragging) never snaps — that fight caused swipe jitter.
        row.scroll = scrollAnim;
        if (!row.dragging && !row.flinging) row.tile = tile;
        boolean wantScale = (!inCats && !searchKeysFocused() && browseList() && nItems > 0) ? true : false;
        boolean coast = row.busy();
        boolean moving = row.step(dt, vis, maxScroll, nItems, wantScale);
        scrollAnim = row.scroll;
        if (!row.busy()) tile = row.tile;
        focusScale = row.focusScale;
        flingV = row.vel;
        userScroll = row.busy();
        if (coast && !row.busy() && artPending) { artPending = false; postInvalidateOnAnimation(); }
        recordFrame(dt, coast || moving);
        float focusTarget = row.focusTarget(vis, maxScroll);

        layoutTabsIfNeeded();
        if (!tabDrag) {
            if (tabFollow) ensureTabInView(cat);
            tabScrollTo = clampTabScroll(tabScrollTo);
            float tdist = tabScrollTo - tabScroll;
            tabScroll += tdist * Math.min(1f, dt * 12f);
            if (Math.abs(tabScrollTo - tabScroll) < 0.5f) tabScroll = tabScrollTo;
        }
        tabScroll = clampTabScroll(tabScroll);
        boolean freezeChrome = row.busy() && chromeReady && chromeSnap != null && chromeSnap.getWidth() == W && chromeSnap.getHeight() == H;
        if (freezeChrome) {
            p.setShader(null); p.setAlpha(255); p.setFilterBitmap(false);
            c.drawBitmap(chromeSnap, 0, 0, p);
        } else {
            background(c, W, H, anim && !row.busy());
            topBar(c, W, H);
            if (!boxset) categories(c, W, H);
            if (onSearch()) drawSearchUi(c, W, H);
            else host.placeKidsSearchField(0, 0, 0, 0, "", "", false, false, 0, 0, 0);
            if (!boxset) mark(c, W, H);
            hints(c, W, H);
            if (row.busy()) captureChromeFrom(c, W, H);
        }
        notifyRoblox(!row.busy());
        bakesThisFrame = 0;
        if (browseList()) browseList(c, W, H); else tiles(c, W, H);
        if (browseList()) installFab(c, W, H);
        if (prompt != null) prompt(c, W, H);
        if (kids && host.timeUpScreen()) timeUpPanel(c, W, H);
        updateReadyBar(c, W, H);
        if (splash != null && drawSplash(c, W, H)) { postInvalidateOnAnimation(); return; }

        boolean settling = moving || Math.abs(cat - catAnim) > 0.002f || Math.abs(focusTarget - scrollAnim) > 0.004f
                || Math.abs(tabScrollTo - tabScroll) > 0.5f;
        if (anim || settling) {
            if ("on".equals(fx) || settling) postInvalidateOnAnimation();
            else postInvalidateDelayed(33);                 // auto: 30 fps background on weak chips
        } else if (kids) postInvalidateDelayed(1000);      // 0.10.49: pick up allowed-hours lock without a continuous loop
        else if (artPending) { artPending = false; postInvalidateOnAnimation(); }
    }

    private float visibleTiles() {
        if (browseList()) {
            float step = browseTileW() + browseGap();
            if (step <= 1) return 1;
            return Math.max(1, (getWidth() - 2 * browsePad()) / step);
        }
        float step = tileW() + tileGap();
        if (step <= 1) return 1;
        return Math.max(1, (getWidth() - 2 * tilesLeft()) / step);
    }

    /** True while a finger or fling owns the row — art arrivals must not invalidate mid-scroll. */
    public boolean rowBusy() { return row.busy(); }

    /** A cover finished decoding. Apply it after the row is still, so a late bitmap cannot hitch a fling. */
    public void artArrived() {
        if (row.busy()) artPending = true;
        else invalidate();
    }

    public String frameReport() { return lastFrameReport == null ? "" : lastFrameReport; }

    private void recordFrame(float dt, boolean scrolling) {
        if (!scrolling) return;
        long ns = (long) (dt * 1e9f);
        frameNs[frameAt] = ns;
        frameAt = (frameAt + 1) % frameNs.length;
        if (frameSamples < frameNs.length) frameSamples++;
        if (ns > frameWorstNs) frameWorstNs = ns;
        int n = frameSamples, over = 0;
        long sum = 0, worst = 0;
        for (int i = 0; i < n; i++) {
            long v = frameNs[i];
            sum += v;
            if (v > worst) worst = v;
            if (v > 18_000_000L) over++;
        }
        lastFrameReport = n + " frames, avg " + (n == 0 ? 0 : (sum / n) / 1_000_000f)
                + " ms, worst " + (worst / 1_000_000f) + " ms, over-18ms " + over;
    }

    private void captureChrome() { chromeReady = false; }

    /** After a full chrome draw, snapshot it so a fling only has to redraw the tile row. */
    private void captureChromeFrom(Canvas src, int W, int H) {
        if (W < 2 || H < 2) return;
        try {
            if (chromeSnap == null || chromeW != W || chromeH != H) {
                chromeSnap = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
                chromeW = W; chromeH = H;
            }
            // The canvas we just drew into is the view's; copy the pixels now, then the row is drawn on top.
            // We cannot reliably copy from a hardware canvas, so rebuild into the snapshot once.
            Canvas cc = new Canvas(chromeSnap);
            background(cc, W, H, false);
            topBar(cc, W, H);
            if (!boxset) categories(cc, W, H);
            if (onSearch()) drawSearchUi(cc, W, H);
            if (!boxset) mark(cc, W, H);
            hints(cc, W, H);
            chromeReady = true;
        } catch (Throwable ignored) { chromeReady = false; }
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
        if (T.embers) {                      // 0.10.27 Phoenix: Hoodlum Asylum's own phoenix art + rising embers
            ensurePhoenix(W, H);
            if (phoenixScreen != null) {
                p.setFilterBitmap(false); p.setAlpha(255); p.setShader(null);
                c.drawBitmap(phoenixScreen, 0, 0, p);
                if (anim) embers(c, W, H);
                return;
            }
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
    /** 0.10.27 Phoenix: glowing embers drifting upward (same particle field as the snow, moving the other way). */
    private void embers(Canvas c, int W, int H) {
        if (flakes[0] == 0) { java.util.Random rnd = new java.util.Random(11); for (int i = 0; i < flakes.length; i++) flakes[i] = 0.0001f + rnd.nextFloat(); }
        int[] cols = {0xFFFFD27A, 0xFFFF8A1F, 0xFFFF4A1A};
        for (int i = 0; i < flakes.length; i += 3) {
            float depth = flakes[i + 2];
            float y = H + 10 * s - ((flakes[i + 1] + t * (0.04f + 0.08f * depth)) % 1f) * (H + 20 * s);
            float x = flakes[i] * W + (float) Math.sin(t * (0.8f + depth) + i) * 22 * s * depth;
            float life = 1f - Math.abs((y / H) - 0.5f) * 1.2f;
            p.setColor(cols[(i / 3) % 3]); p.setAlpha((int) Math.max(30, Math.min(230, (80 + 150 * depth) * Math.max(0.3f, life))));
            c.drawCircle(x, y, (0.9f + 2.2f * depth) * s, p);
        }
        p.setAlpha(255);
    }

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

    private Typeface face(boolean bold) {
        if (heavy) return tfBlack != null ? tfBlack : Typeface.DEFAULT_BOLD;
        if (bold) return tfBold != null ? tfBold : Typeface.DEFAULT_BOLD;
        return tfRegular != null ? tfRegular : Typeface.DEFAULT;
    }

    private void goldText(Canvas c, String txt, float x, float y, float size, Paint.Align a, boolean bold) {
        p.setTextSize(size); p.setTextAlign(a); p.setFakeBoldText(bold);
        p.setTypeface(tfBlack != null ? tfBlack : Typeface.DEFAULT_BOLD);
        p.setShader(new LinearGradient(0, y - size, 0, y, new int[]{T.goldLight, GOLD, GOLD_D, T.goldLight}, null, Shader.TileMode.CLAMP));
        c.drawText(txt, x, y, p); p.setShader(null); p.setFakeBoldText(false); p.setTypeface(tfRegular);
    }

    private void text(Canvas c, String txt, float x, float y, float size, int col, Paint.Align a, boolean bold) {
        p.setTextSize(size); p.setTextAlign(a); p.setColor(col);
        p.setTypeface(face(bold));
        if (heavy) p.setShadowLayer(4 * s, 0, 1.5f * s, 0xE6000000);
        c.drawText(txt, x, y, p); p.clearShadowLayer(); p.setTypeface(tfRegular);
    }

    /** 0.10.19: tile names drawn big and heavy (sans-serif-black, soft shadow). */
    private boolean heavy;
    /** 0.10.19: a tile name as large as fits in maxW on up to 2 lines, bottom line's baseline at y.
     *  0.10.48: a line that is still too wide at the minimum size is ellipsized, so long game names stay inside the tile. */
    private void tileName(Canvas c, String lab, float cx, float y, float maxW, float start, float min) {
        heavy = true;
        p.setTypeface(tfBlack != null ? tfBlack : Typeface.DEFAULT_BOLD);
        String l1 = lab, l2 = null; float ts = start;
        for (; ; ts -= 0.5f * s) {
            p.setTextSize(ts); l1 = lab; l2 = null;
            if (p.measureText(lab) <= maxW) break;
            int best = -1; float bw = Float.MAX_VALUE;
            for (int i = lab.indexOf(' '); i > 0; i = lab.indexOf(' ', i + 1)) { float w = Math.max(p.measureText(lab, 0, i), p.measureText(lab, i + 1, lab.length())); if (w < bw) { bw = w; best = i; } }
            if (best > 0 && bw <= maxW) { l1 = lab.substring(0, best); l2 = lab.substring(best + 1); break; }
            if (ts <= min) { if (best > 0) { l1 = lab.substring(0, best); l2 = lab.substring(best + 1); } break; }
        }
        p.setTextSize(ts);
        l1 = ellipsize(l1, maxW);
        if (l2 != null) l2 = ellipsize(l2, maxW);
        if (l2 != null) { text(c, l1, cx, y - ts * 1.05f, ts, 0xFFFFFFFF, Paint.Align.CENTER, true); text(c, l2, cx, y, ts, 0xFFFFFFFF, Paint.Align.CENTER, true); }
        else text(c, l1, cx, y, ts, 0xFFFFFFFF, Paint.Align.CENTER, true);
        heavy = false;
    }
    private String ellipsize(String lab, float maxW) {
        if (lab == null || lab.isEmpty() || p.measureText(lab) <= maxW) return lab == null ? "" : lab;
        String dots = "…";
        float dw = p.measureText(dots);
        if (dw >= maxW) return dots;
        int lo = 0, hi = lab.length();
        while (lo < hi) {
            int mid = (lo + hi + 1) / 2;
            if (p.measureText(lab, 0, mid) + dw <= maxW) lo = mid; else hi = mid - 1;
        }
        if (lo <= 0) return dots;
        return lab.substring(0, lo).trim() + dots;
    }

    private void spaced(Canvas c, String txt, float x, float y, float size, int col, Paint.Align a) {
        p.setLetterSpacing(0.25f); text(c, txt, x, y, size, col, a, false); p.setLetterSpacing(0);
    }

    private void topBar(Canvas c, int W, int H) {
        logoTop.set(18 * s, 10 * s, 18 * s + 150 * s, 60 * s);
        if (logo != null) { p.setFilterBitmap(true); c.drawBitmap(logo, null, logoTop, p); }
        String st = host.status();
        long nowMs = System.currentTimeMillis();
        if (clockTxt.isEmpty() || nowMs - clockAt > 1000) { clockAt = nowMs; clockTxt = clock.format(new Date()); }
        String right = clockTxt + (st.isEmpty() ? "" : "   " + st);
        text(c, right, W - 24 * s, 36 * s, 20 * s, TXT, Paint.Align.RIGHT, false);
        p.setTextSize(20 * s);
        // Wi-Fi stays at the same size, Y and formula as before 0.10.76 (tab-strip geometry is untouched).
        float wx = W - 24 * s - p.measureText(right) - 30 * s;
        wifiCx = wx;
        float wr = CameraApp.HIT_RADIUS * s;
        wifiHit.set(wx - wr, CameraApp.ICON_Y * s - wr, wx + wr, CameraApp.ICON_Y * s + wr);
        int wifiCol = "wifi".equals(hoverChrome) ? GOLD : (host.online() ? TXT : 0xFF555555);
        icon(c, "wifi", wx, CameraApp.ICON_Y * s, CameraApp.ICON_SIZE * s, wifiCol, 2f);
        if (CameraApp.showIcon(kids)) {
            float cx = CameraApp.cameraCx(wx, s), cy = CameraApp.cameraCy(s);
            icon(c, "camera", cx, cy, CameraApp.iconSize(s), "camera".equals(hoverChrome) ? GOLD : TXT, 2f);
            cameraHit.set(CameraApp.hitLeft(wx, s), CameraApp.hitTop(s), CameraApp.hitRight(wx, s), CameraApp.hitBottom(s));
        } else {
            cameraHit.setEmpty();
        }
        if (kids) {
            String ban = host.kidsBanner();
            if (ban != null && !ban.isEmpty())
                text(c, ban, W / 2f, 36 * s, 18 * s, GOLD, Paint.Align.CENTER, true);
        }
    }

    /** 0.10.46: horizontally scrolling tab strip. 0.10.66: fixed height, one-line
     *  labels, pixel-aligned Y, and no per-scroll remeasure, so a swipe cannot
     *  bounce the row 1–2 px. The game row stays still by freezing this chrome;
     *  this row has to move, so its geometry is locked instead. */
    private void categories(Canvas c, int W, int H) {
        layoutTabsIfNeeded();
        float y = HomeTabs.iconY(H), top = tabStripTop(), bot = catsBottom();
        float size = HomeTabs.iconSize(s);
        float labY = HomeTabs.labelY(y, s);
        float labSize = HomeTabs.labelSize(s);
        float underTop = HomeTabs.underlineTop(H, s);
        c.save();
        c.clipRect(0, top, W, bot);
        for (int i = 0; i < cats.size(); i++) {
            float x = tabX[i] - tabScroll + tabW[i] / 2f;
            if (x + tabW[i] < -20 * s || x - tabW[i] > W + 20 * s) continue;
            float sel = Math.max(0, 1 - Math.abs(catAnim - i));
            if (sel > 0.05f) {
                p.setShader(new RadialGradient(x, y, 50 * s, (((int) (0x55 * sel)) << 24) | T.glowRgb, T.glowRgb, Shader.TileMode.CLAMP));
                c.drawCircle(x, y, 50 * s, p); p.setShader(null);
            }
            int col = i == cat ? (inCats ? GOLD : T.accent) : 0xFFDDE7E1;
            icon(c, cats.get(i).icon, x, y, size, col, i == cat ? 2.6f : 2.2f);
            String lab = cats.get(i).label;
            p.setTextSize(labSize);
            lab = ellipsize(lab, tabW[i] - 4 * s);
            text(c, lab, x, labY, labSize, i == cat ? 0xFFFFFFFF : 0xFFC9D6CF, Paint.Align.CENTER, i == cat);
            if (i == cat) {
                float uw = Math.min(tabW[i] - 8 * s, p.measureText(lab) + 8 * s);
                p.setShader(null); p.setColor(GOLD);
                c.drawRoundRect(new RectF(x - uw / 2, underTop, x + uw / 2, underTop + 3 * s), 2 * s, 2 * s, p);
            }
        }
        c.restore();
    }

    private void mark(Canvas c, int W, int H) {
        float cx = W / 2f, top = H * 0.30f;
        float lw = 560 * s, lh = lw / 3f, ly = top + 6 * s;
        c.save(); c.scale(1f, 0.45f, cx, ly + lh / 2);     // soft theme-coloured glow behind the white logo
        p.setShader(new RadialGradient(cx, ly + lh / 2, 330 * s, 0x77000000 | T.glowRgb, T.glowRgb, Shader.TileMode.CLAMP));
        c.drawCircle(cx, ly + lh / 2, 330 * s, p); p.setShader(null); c.restore();
        logoCenter.set(cx - lw / 2, ly, cx + lw / 2, ly + lh);
        if (logo != null) { p.setFilterBitmap(true); c.drawBitmap(logo, null, logoCenter, p); }
        float ty = ly + lh + 2 * s;
        if (T.embers) { heavy = true; p.setLetterSpacing(0.3f); text(c, "PHOENIX", cx, ty + 52 * s, 46 * s, GOLD, Paint.Align.CENTER, true); p.setLetterSpacing(0); heavy = false; }   // 0.10.27
        else if (!covers()) spaced(c, "GAMING  •  CLOUD  •  EMULATION  •  STREAMING", cx, ty + 30 * s, 15 * s, 0xFFCFE0D6, Paint.Align.CENTER);
    }

    private int hitHomeTile(float x, float y) {
        if (y <= tilesTop() || y >= tilesTop() + tileH()) return -1;
        float step = tileW() + tileGap();
        int i = (int) Math.floor((x - tilesLeft() + scrollAnim * step) / step);
        if (i >= 0 && i < items().size()) return i;
        return -1;
    }
    private void fireTileHold() {
        if (holdTile < 0 || holdAt == 0 || longPressFired) return;
        if (android.os.SystemClock.uptimeMillis() < holdAt) return;
        int i = holdTile;
        holdTile = -1; holdAt = 0; longPressFired = true; gestureChose = true;
        inCats = false; tile = i;
        if (holdFav && Catalog.favSaved(currentItem())) { holdFav = false; host.askRemoveFav(currentItem()); return; }
        holdFav = false;
        togglePick(i);
    }
    private void confirmInstall() {
        if (picked.isEmpty()) return;
        java.util.ArrayList<String> pkgs = new java.util.ArrayList<>(picked);
        host.installPicked(pkgs);
    }
    private void installFab(Canvas c, int W, int H) {
        if (picked.isEmpty()) { installBtn.setEmpty(); return; }
        String lab = "Install (" + picked.size() + ")";
        p.setShader(null); p.setFakeBoldText(true); p.setTextSize(18 * s); p.setTextAlign(Paint.Align.CENTER);
        float tw = p.measureText(lab) + 36 * s, th = 40 * s;
        float cx = W / 2f, bot = browseBottom() - 6 * s;
        installBtn.set(cx - tw / 2, bot - th, cx + tw / 2, bot);
        p.setColor(0xF2E53935); c.drawRoundRect(installBtn, th / 2, th / 2, p);
        p.setColor(0xFFFFFFFF);
        c.drawText(lab, installBtn.centerX(), installBtn.centerY() + p.getTextSize() * 0.36f, p);
        p.setFakeBoldText(false);
    }
    private void getBadge(Canvas c, Catalog.Item it) {
        if (it.isAction()) return;
        boolean on = host.installed(it);
        if (!on && it.webUrl == null) {
            p.setShader(null); p.setColor(T.getBadge);
            RectF br = new RectF(r.right - 44 * s, r.top + 7 * s, r.right - 7 * s, r.top + 23 * s);
            c.drawRoundRect(br, 8 * s, 8 * s, p);
            text(c, "GET", br.centerX(), br.bottom - 4 * s, 10 * s, 0xFFFFFFFF, Paint.Align.CENTER, true);
            return;
        }
        if (!on && it.webUrl != null) {
            p.setShader(null); p.setColor(T.webBadge);
            RectF br = new RectF(r.right - 44 * s, r.top + 7 * s, r.right - 7 * s, r.top + 23 * s);
            c.drawRoundRect(br, 8 * s, 8 * s, p);
            text(c, "WEB", br.centerX(), br.bottom - 4 * s, 10 * s, 0xFFFFFFFF, Paint.Align.CENTER, true);
            return;
        }
        if (on) {
            p.setShader(null); p.setColor(0xF22E7D32);
            RectF br = new RectF(r.right - 48 * s, r.top + 7 * s, r.right - 7 * s, r.top + 23 * s);
            c.drawRoundRect(br, 8 * s, 8 * s, p);
            text(c, "PLAY", br.centerX(), br.bottom - 4 * s, 10 * s, 0xFFFFFFFF, Paint.Align.CENTER, true);
        }
    }
    private void tickBadge(Canvas c, Catalog.Item it) {
        if (!isPicked(it)) return;
        float cx = r.left + 14 * s, cy = r.top + 14 * s, rad = 11 * s;
        p.setShader(null); p.setColor(0xFF2E7D32);
        c.drawCircle(cx, cy, rad, p);
        stroke.setShader(null); stroke.setColor(0xFFFFFFFF); stroke.setStrokeWidth(2.4f * s);
        c.drawLine(cx - 5 * s, cy, cx - 1.5f * s, cy + 4.5f * s, stroke);
        c.drawLine(cx - 1.5f * s, cy + 4.5f * s, cx + 6 * s, cy - 4 * s, stroke);
    }

    /** 0.10.50: covers match 0.10.48 (centre-crop fill, big bold title over the lower cover). */
    private void browseList(Canvas c, int W, int H) {
        List<Catalog.Item> its = items();
        float top = browseTop(), bot = browseBottom();
        float tw = browseTileW(), th = browseTileH(), gap = browseGap(), left = browsePad();
        c.save();
        c.clipRect(0, top - 20 * s, W, H);
        for (int i = 0; i < its.size(); i++) {
            float x = left + (i - scrollAnim) * (tw + gap);
            if (x > W + tw || x + tw < -tw) continue;   // skip off-screen (one canvas; nothing to recycle)
            boolean sel = i == row.highlight(visibleTiles(), its.size()) && !inCats;
            Catalog.Item it = its.get(i); badgeItem = it;
            float grow = sel ? th * (focusScale - 1f) / 2f : 0;
            r.set(x - grow, top - grow, x + tw + grow, top + th + grow);
            if (sel && !row.busy()) {
                p.setShader(new RadialGradient(r.centerX(), r.centerY(), tw * 0.85f, 0x66000000 | T.glowRgb, T.glowRgb, Shader.TileMode.CLAMP));
                c.drawCircle(r.centerX(), r.centerY(), tw * 0.85f, p); p.setShader(null);
            }
            coverTile(c, it, sel, false);
            getBadge(c, it);
            tickBadge(c, it);
            updBadge(c);
            tileName(c, it.label.toUpperCase(Locale.ROOT), r.centerX(), r.top + th * 0.86f, tw - 12 * s, (kids ? 34 : 20) * s, 11 * s);
        }
        c.restore();
        if (its.isEmpty()) {
            String empty = onSearch() ? searchEmptyMessage() : (rowRetryMessage() != null ? rowRetryMessage() : "Nothing in this list yet.");
            text(c, empty, W / 2f, (top + bot) / 2, 16 * s, DIM, Paint.Align.CENTER, false);
        } else {
            Catalog.Item f = its.get(Math.min(tile, its.size() - 1));
            String hint = onSearch() ? (host.installed(f) ? "Installed on this device — press A to play" : "Not installed — press A for the parent PIN, then its Play Store page")
                    : "wifi".equals(f.action) ? host.wifi() : host.scanNote(cats.get(cat).id) != null && !f.isRom() ? host.scanNote(cats.get(cat).id) : Catalog.copyHint(cats.get(cat).id, f) != null ? Catalog.copyHint(cats.get(cat).id, f) : f.isAction() ? "" : f.isRom() ? "Your game file - opens with an emulator you have installed" : f.isDeviceApp() ? "Installed on this device" : host.installed(f) ? "Installed" : f.webUrl != null ? "Opens in your browser (or the app, once installed)" : "Not installed - press A for its Play Store page";
            text(c, hint, W / 2f, top + th + 24 * s, 14 * s, DIM, Paint.Align.CENTER, false);
        }
    }

    private void tiles(Canvas c, int W, int H) {
        List<Catalog.Item> its = items();
        float top = tilesTop(), w = tileW(), h = tileH(), gap = tileGap(), left = tilesLeft();
        for (int i = 0; i < its.size(); i++) {
            float x = left + (i - scrollAnim) * (w + gap);
            if (x > W || x + w < 0) continue;   // skip tiles that are off screen (nothing to recycle: this is one canvas)
            boolean sel = i == tile && !inCats;
            Catalog.Item it = its.get(i); badgeItem = it;
            float grow = sel ? 8 * s : 0;
            r.set(x - grow, top - grow * 1.5f, x + w + grow, top + h + grow * 0.5f);
            if (sel) {
                p.setShader(new RadialGradient(r.centerX(), r.centerY(), w * 0.85f, 0x66000000 | T.glowRgb, T.glowRgb, Shader.TileMode.CLAMP));
                c.drawCircle(r.centerX(), r.centerY(), w * 0.85f, p); p.setShader(null);
            }
            if (covers()) { coverTile(c, it, sel, true); updBadge(c); continue; }
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
                    pill.set(px - 9 * s, py - 4 * s, px + 9 * s, py + 4 * s);
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
            tileName(c, lab, r.centerX(), r.top + h * 0.86f, w - 12 * s, (kids ? 34 : 20) * s, 11 * s);   // 0.10.19 bigger, bolder
            updBadge(c);
            timeUpBadge(c, it);
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
        String hint = "wifi".equals(f.action) ? host.wifi() : host.scanNote(cats.get(cat).id) != null && !f.isRom() ? host.scanNote(cats.get(cat).id) : Catalog.copyHint(cats.get(cat).id, f) != null ? Catalog.copyHint(cats.get(cat).id, f) : f.isAction() ? "" : f.isRom() ? "Your game file - opens with an emulator you have installed" : f.isDeviceApp() ? "Installed on this device" : host.installed(f) ? "Installed" : f.webUrl != null ? "Opens in your browser (or the app, once installed)" : "Not installed - press A for its Play Store page";
        text(c, hint, W / 2f, top + h + 24 * s, 14 * s, DIM, Paint.Align.CENTER, false);
    }

    /** 0.10.12: CodeAque's own title card for a game without a picture: name-coloured, big bold name. No platform marks. */
    private void titleCard(Canvas c, Catalog.Item it, boolean sel) {
        float hue = (Math.abs(it.label.toLowerCase(Locale.ROOT).hashCode()) % 360);
        int a = Color.HSVToColor(new float[]{hue, 0.75f, 0.70f}), b = Color.HSVToColor(new float[]{(hue + 40) % 360, 0.85f, 0.28f});
        p.setShader(new LinearGradient(r.left, r.top, r.right, r.bottom, new int[]{a, b, 0xFF06080C}, new float[]{0, 0.65f, 1}, Shader.TileMode.CLAMP));
        c.drawRect(r, p); p.setShader(null);
        p.setColor(0x18FFFFFF);   // CodeAque chevrons
        for (int k = 0; k < 4; k++) { float y = r.top + r.height() * (0.08f + k * 0.07f); path.reset(); path.moveTo(r.left, y); path.lineTo(r.centerX(), y + r.width() * 0.25f);
            path.lineTo(r.right, y); path.lineTo(r.right, y + 3 * s); path.lineTo(r.centerX(), y + r.width() * 0.25f + 3 * s); path.lineTo(r.left, y + 3 * s); path.close(); c.drawPath(path, p); }
        p.setColor(GOLD); c.drawRect(r.left + 10 * s, r.top + r.height() * 0.42f, r.left + 10 * s + r.width() * 0.3f, r.top + r.height() * 0.42f + 3 * s, p);
        String[] words = it.label.toUpperCase(Locale.ROOT).split(" ");
        float maxW = r.width() - 16 * s, ts = 34 * s;   // 0.10.19 bigger
        java.util.List<String> lines = new java.util.ArrayList<>();
        p.setTypeface(tfBlack != null ? tfBlack : Typeface.DEFAULT_BOLD);
        while (true) {
            p.setTextSize(ts); lines.clear(); String cur = "";
            for (String w : words) { String t = cur.isEmpty() ? w : cur + " " + w; if (p.measureText(t) <= maxW || cur.isEmpty()) cur = t; else { lines.add(cur); cur = w; } }
            if (!cur.isEmpty()) lines.add(cur);
            boolean fits = lines.size() <= 5 && ts * 1.1f * lines.size() < r.height() * 0.5f;
            for (String l : lines) if (p.measureText(l) > maxW) fits = false;
            if (fits || ts <= 9 * s) break;
            ts -= 1 * s;
        }
        float y = r.top + r.height() * 0.42f + 8 * s + ts;
        heavy = true; for (String l : lines) { text(c, l, r.left + 10 * s, y, ts, 0xFFFFFFFF, Paint.Align.LEFT, true); y += ts * 1.1f; } heavy = false;
    }
    private void drawCoverFrame(Canvas c, boolean sel) {
        stroke.setStrokeWidth((sel ? 3 : 1.5f) * s);
        stroke.setShader(new LinearGradient(0, r.top, 0, r.bottom, sel ? new int[]{T.goldLight, GOLD, GOLD_D} : new int[]{T.border, T.borderDark, T.border}, null, Shader.TileMode.CLAMP));
        c.drawRoundRect(r, 12 * s, 12 * s, stroke); stroke.setShader(null);
    }

    private final Path clip = new Path();
    private final RectF src = new RectF();
    /** A portrait My Games tile in r: the game's picture (centre-cropped), else CodeAque's own generic art with the app icon. */
    private void coverTile(Canvas c, Catalog.Item it, boolean sel) { coverTile(c, it, sel, true); }
    private void coverTile(Canvas c, Catalog.Item it, boolean sel, boolean nameInside) {
        float rad = 12 * s;
        // 0.10.50: browse tiles use the 0.10.48 look — ordinary cover, centre-cropped to fill the (Wi-Fi-sized) tile.
        boolean kg = nameInside && kids && cats.get(cat).id.equals("kgames") && !it.isAction();   // 0.10.17 full-bleed Kids Games covers
        Bitmap cov = kg ? host.wideCover(it) : host.cover(it), ai = cov == null ? host.appIcon(it) : null;
        if (cov != null) {
            // 0.10.51: pre-bake an opaque, centre-cropped, rounded tile at the un-grown size and draw it 1:1
            // (slightly outset so the art sits under the gold stroke and there is no dark AA gap).
            int bw = Math.max(1, Math.round(browseList() ? browseTileW() : tileW()));
            int bh = Math.max(1, Math.round(browseList() ? browseTileH() : tileH()));
            Bitmap baked = bakedTile(it, cov, bw, bh, rad);
            if (baked != null) {
                p.setShader(null); p.setAlpha(255);
                p.setFilterBitmap(sel && (Math.abs(r.width() - bw) > 0.5f || Math.abs(r.height() - bh) > 0.5f));
                float pad = Math.max(1f, s);
                src.set(r.left - pad, r.top - pad, r.right + pad, r.bottom + pad);
                c.drawBitmap(baked, null, src, p);
                drawTileStroke(c, sel, rad);
                if (T.snow) { p.setColor(0xF2FFFFFF); r2.set(r.left + 6 * s, r.top - 4 * s, r.right - 6 * s, r.top + 5 * s); c.drawRoundRect(r2, 5 * s, 5 * s, p); }
                if (nameInside) {
                    String lab = it.label.toUpperCase(Locale.ROOT);
                    tileName(c, lab, r.centerX(), r.bottom - 10 * s, r.width() - 12 * s, 26 * s * big(), 11 * s);
                }
                timeUpBadge(c, it);
                return;
            }
            // Waiting to bake: opaque rounded fill, no clipPath (avoids a mid-scroll hitch and a fringe).
            p.setShader(null); p.setColor(0xFF151515); p.setAlpha(255);
            c.drawRoundRect(r, rad, rad, p);
            drawTileStroke(c, sel, rad);
            timeUpBadge(c, it);
            return;
        }
        c.save(); clip.reset(); clip.addRoundRect(r, rad, rad, Path.Direction.CW); c.clipPath(clip);
        if (!nameInside) {
            // Same face as the Wi-Fi / Update tiles, so a missing cover still matches that style.
            p.setShader(new LinearGradient(0, r.top, 0, r.bottom, sel ? T.tileSelTop : T.tileTop, T.tileBottom, Shader.TileMode.CLAMP));
            c.drawRect(r, p); p.setShader(null);
            if (ai != null) {
                float is = 40 * s * big(); iconRect.set(r.centerX() - is / 2, r.top + r.height() * 0.30f - is / 2 - 4 * s, r.centerX() + is / 2, r.top + r.height() * 0.30f + is / 2 - 4 * s);
                p.setFilterBitmap(true); p.setAlpha(255); c.drawBitmap(ai, null, iconRect, p);
            } else icon(c, it.icon, r.centerX(), r.top + r.height() * 0.30f, 30 * s, sel ? T.accentLight : T.accentMid, 2.2f);
            spaced(c, "CODEAQUE", r.centerX(), r.top + r.height() * 0.56f, 8.5f * s, sel ? GOLD : T.sub, Paint.Align.CENTER);
            c.restore();
            stroke.setStrokeWidth((sel ? 3 : 1.5f) * s);
            stroke.setShader(new LinearGradient(0, r.top, 0, r.bottom, sel ? new int[]{T.goldLight, GOLD, GOLD_D} : new int[]{T.border, T.borderDark, T.border}, null, Shader.TileMode.CLAMP));
            c.drawRoundRect(r, rad, rad, stroke); stroke.setShader(null);
            timeUpBadge(c, it);
            return;
        } else if (it.isRom() || ai == null) {
            // 0.10.46: styled name-tile when no cover or Play / package icon is available.
            titleCard(c, it, sel);
            c.restore(); drawCoverFrame(c, sel);
            timeUpBadge(c, it);
            return;
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
        p.setShader(new LinearGradient(0, r.bottom - r.height() * 0.48f, 0, r.bottom, 0x00000000, 0xF2000000, Shader.TileMode.CLAMP));   // 0.10.19 taller, darker fade
        c.drawRect(r.left, r.bottom - r.height() * 0.48f, r.right, r.bottom, p); p.setShader(null);
        c.restore();
        stroke.setStrokeWidth((sel ? 3 : 1.5f) * s);
        stroke.setShader(new LinearGradient(0, r.top, 0, r.bottom, sel ? new int[]{T.goldLight, GOLD, GOLD_D} : new int[]{T.border, T.borderDark, T.border}, null, Shader.TileMode.CLAMP));
        c.drawRoundRect(r, rad, rad, stroke); stroke.setShader(null);
        if (T.snow) { p.setColor(0xF2FFFFFF); r2.set(r.left + 6 * s, r.top - 4 * s, r.right - 6 * s, r.top + 5 * s); c.drawRoundRect(r2, 5 * s, 5 * s, p); }
        if (nameInside) {
            String lab = it.label.toUpperCase(Locale.ROOT);
            tileName(c, lab, r.centerX(), r.bottom - 10 * s, r.width() - 12 * s, 26 * s * big(), 11 * s);   // 0.10.19 bigger, bolder, up to 2 lines
        }
        timeUpBadge(c, it);
    }
    private final RectF r2 = new RectF();
    private Catalog.Item badgeItem;
    /** 0.10.28: small "Update available" badge on the Update tiles (Kids Wi-Fi & Update row and Settings). */
    private void updBadge(Canvas c) {
        if (!Catalog.showsUpdateBadge(badgeItem)) return;
        p.setShader(null); p.setTextSize(11 * s * big()); p.setFakeBoldText(true); p.setTextAlign(Paint.Align.CENTER);
        String t = "UPDATE AVAILABLE"; float tw = p.measureText(t) + 14 * s, bh = 18 * s * big();
        r2.set(r.centerX() - tw / 2, r.top + 6 * s, r.centerX() + tw / 2, r.top + 6 * s + bh);
        p.setColor(0xFFE53935); c.drawRoundRect(r2, bh / 2, bh / 2, p);
        p.setColor(0xFFFFFFFF); c.drawText(t, r2.centerX(), r2.centerY() + p.getTextSize() * 0.36f, p);
        p.setFakeBoldText(false);
    }

    private void timeUpBadge(Canvas c, Catalog.Item it) {
        if (!kids || !host.timeUpTile(it)) return;
        p.setShader(null); p.setFakeBoldText(true); p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(11 * s * big());
        String t = "TIME'S UP"; float tw = p.measureText(t) + 14 * s, bh = 18 * s * big();
        r2.set(r.centerX() - tw / 2, r.top + 6 * s, r.centerX() + tw / 2, r.top + 6 * s + bh);
        p.setColor(0xE0C62828); c.drawRoundRect(r2, bh / 2, bh / 2, p);
        p.setColor(0xFFFFFFFF); c.drawText(t, r2.centerX(), r2.centerY() + p.getTextSize() * 0.36f, p);
        p.setFakeBoldText(false);
    }

    private void hints(Canvas c, int W, int H) {
        float den = getResources().getDisplayMetrics().density;
        float bh = Math.max(44 * s, 48 * den), bw = Math.max(96 * s, 88 * den);
        float top = H - 8 * s - bh;
        backBtn.set(16 * s, top, 16 * s + bw, top + bh);
        p.setShader(null); p.setColor(0xF0141820); c.drawRoundRect(backBtn, 14 * s, 14 * s, p);
        stroke.setShader(null); stroke.setStrokeWidth(2.2f * s); stroke.setColor(GOLD);
        c.drawRoundRect(backBtn, 14 * s, 14 * s, stroke);
        text(c, boxset ? "Exit" : "Back", backBtn.centerX(), backBtn.centerY() + 7 * s, Math.max(16 * s, 14 * den), 0xFFFFFFFF, Paint.Align.CENTER, true);
        float y = backBtn.centerY() + 6 * s;
        p.setColor(0x33FFFFFF); c.drawRect(backBtn.right + 8 * s, backBtn.top, W, backBtn.top + 1, p);
        String[][] hs = boxset ? new String[][]{{"A", "Open"}, {"Start / Select", "Hold to leave (no PIN)"}} : new String[][]{{"A", "Open"}, {"L1 R1", "Category"}, {"Y", "Settings"}};
        float x = backBtn.right + 16 * s;
        for (String[] hnt : hs) {
            if (x > W - 150 * s) break;          // keep Online visible; the Back button is always drawn
            p.setTextSize(13 * s); p.setTypeface(tfBold != null ? tfBold : Typeface.DEFAULT_BOLD);
            float keyW = Math.max(26 * s, p.measureText(hnt[0]) + 14 * s);
            stroke.setStrokeWidth(1.6f * s); stroke.setColor(0xFFDDE7E1);
            r.set(x, y - 18 * s, x + keyW, y + 6 * s); c.drawRoundRect(r, 12 * s, 12 * s, stroke);
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


    private void timeUpPanel(Canvas c, int W, int H) {
        p.setShader(null);
        p.setColor(0xE80A0C10); c.drawRect(0, 0, W, H, p);
        String head = host.timeUpHeadline(), line = host.timeUpMessage();
        if (head == null || head.isEmpty()) head = "TIME'S UP";
        if (line == null || line.isEmpty()) line = "Play time is used up for today.";
        text(c, head, W / 2f, H * 0.38f, 42 * s, GOLD, Paint.Align.CENTER, true);
        text(c, line, W / 2f, H * 0.50f, 20 * s, 0xFFFFFFFF, Paint.Align.CENTER, false);
        text(c, "Ask a parent to add time or unlock.", W / 2f, H * 0.56f, 16 * s, 0xFFC9D6CF, Paint.Align.CENTER, false);
        float bw = 280 * s, bh = 64 * s;
        r2.set(W / 2f - bw / 2, H * 0.66f, W / 2f + bw / 2, H * 0.66f + bh);
        p.setColor(T.accent); c.drawRoundRect(r2, 12 * s, 12 * s, p);
        text(c, "ASK A PARENT", r2.centerX(), r2.centerY() + 8 * s, 20 * s, 0xFF101010, Paint.Align.CENTER, true);
    }

    private void prompt(Canvas c, int W, int H) {
        p.setColor(0xCC000000); c.drawRect(0, 0, W, H, p);
        r.set(W / 2f - 320 * s, H / 2f - 130 * s, W / 2f + 320 * s, H / 2f + 150 * s);
        p.setShader(null); p.setColor(T.prompt); c.drawRoundRect(r, 16 * s, 16 * s, p);
        stroke.setShader(null); stroke.setColor(GOLD); stroke.setStrokeWidth(2 * s); c.drawRoundRect(r, 16 * s, 16 * s, stroke);
        text(c, prompt.label + " is not installed", W / 2f, r.top + 52 * s, 24 * s, 0xFFFFFFFF, Paint.Align.CENTER, true);
        text(c, "Get it from the Play Store, then come back here.", W / 2f, r.top + 90 * s, 16 * s, DIM, Paint.Align.CENTER, false);
        text(c, "A  open     B  back", W / 2f, r.top + 124 * s, 15 * s, GOLD, Paint.Align.CENTER, false);
        float bw = 240 * s, bh = Math.max(48 * s, 48 * getResources().getDisplayMetrics().density), gap = 16 * s;
        float yb = r.bottom - 22 * s - bh;
        promptBack.set(W / 2f - gap / 2f - bw, yb, W / 2f - gap / 2f, yb + bh);
        promptOpen.set(W / 2f + gap / 2f, yb, W / 2f + gap / 2f + bw, yb + bh);
        p.setColor(0xFF1A2228); c.drawRoundRect(promptBack, 12 * s, 12 * s, p);
        stroke.setColor(0xFFDDE7E1); c.drawRoundRect(promptBack, 12 * s, 12 * s, stroke);
        text(c, "Back", promptBack.centerX(), promptBack.centerY() + 7 * s, 18 * s, 0xFFFFFFFF, Paint.Align.CENTER, true);
        p.setColor(GOLD); c.drawRoundRect(promptOpen, 12 * s, 12 * s, p);
        text(c, "Open Play Store", promptOpen.centerX(), promptOpen.centerY() + 7 * s, 18 * s, 0xFF101418, Paint.Align.CENTER, true);
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
            case "plus":
                c.drawLine(cx, y0 + 4 * u, cx, y0 + 20 * u, stroke); c.drawLine(x0 + 4 * u, cy, x0 + 20 * u, cy, stroke); break;
            case "wifi":
                for (int i = 1; i <= 3; i++) { float rr = i * 3.6f * u; ir.set(cx - rr, cy + 6 * u - rr, cx + rr, cy + 6 * u + rr); c.drawArc(ir, 225, 90, false, stroke); }
                p.setColor(col); c.drawCircle(cx, cy + 6 * u, 1.4f * u, p); break;
            case "camera":   // generic camera glyph; not a third-party logo
                ir.set(x0 + 3 * u, y0 + 7 * u, x0 + 21 * u, y0 + 20 * u); c.drawRoundRect(ir, 2 * u, 2 * u, stroke);
                c.drawCircle(cx, y0 + 13.5f * u, 4 * u, stroke);
                c.drawCircle(cx, y0 + 13.5f * u, 1.6f * u, stroke);
                ir.set(x0 + 8 * u, y0 + 4 * u, x0 + 16 * u, y0 + 7 * u); c.drawRoundRect(ir, 1.2f * u, 1.2f * u, stroke);
                break;
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
            case "folder":
                path.moveTo(x0 + 3 * u, y0 + 6 * u); path.lineTo(x0 + 9 * u, y0 + 6 * u); path.lineTo(x0 + 11 * u, y0 + 8 * u);
                path.lineTo(x0 + 21 * u, y0 + 8 * u); path.lineTo(x0 + 21 * u, y0 + 19 * u); path.lineTo(x0 + 3 * u, y0 + 19 * u); path.close();
                c.drawPath(path, stroke); c.drawLine(x0 + 3 * u, y0 + 11 * u, x0 + 21 * u, y0 + 11 * u, stroke); break;
            case "image":
                ir.set(x0 + 3 * u, y0 + 5 * u, x0 + 21 * u, y0 + 19 * u); c.drawRoundRect(ir, 2 * u, 2 * u, stroke);
                path.moveTo(x0 + 4 * u, y0 + 17 * u); path.lineTo(x0 + 10 * u, y0 + 11 * u); path.lineTo(x0 + 14 * u, y0 + 15 * u);
                path.lineTo(x0 + 16 * u, y0 + 13 * u); path.lineTo(x0 + 20 * u, y0 + 17 * u); c.drawPath(path, stroke);
                c.drawCircle(x0 + 16 * u, y0 + 9 * u, 1.5f * u, stroke); break;
            case "phone":
                ir.set(x0 + 7 * u, y0 + 2 * u, x0 + 17 * u, y0 + 22 * u); c.drawRoundRect(ir, 3 * u, 3 * u, stroke);
                c.drawLine(x0 + 10 * u, y0 + 18 * u, x0 + 14 * u, y0 + 18 * u, stroke); break;
            default:
                c.drawCircle(cx, cy, 8 * u, stroke);
        }
    }

    // ---------- 0.10.50 Kids search (system IME, category chips, embed keyboard as fallback) ----------
    private boolean onSearch() {
        return !boxset && kids && !cats.isEmpty()
                && "ksearch".equals(cats.get(Math.max(0, Math.min(cat, cats.size() - 1))).id);
    }
    public boolean onKidsSearch() { return onSearch(); }
    public String searchQueryText() { return searchQ == null ? "" : searchQ; }
    public void setSearchQueryText(String q, boolean immediate) {
        searchQ = q == null ? "" : q;
        searchChipSel = -1;
        if (immediate) fireSearch();
        else scheduleSearch();
        invalidate();
    }
    public void setSearchImeFallback(boolean on) {
        if (searchKbFallback == on) return;
        searchKbFallback = on;
        if (on && onSearch() && !inCats && searchFocus == SF_BOX) searchFocus = SF_KEYS;
        if (!on && searchFocus == SF_KEYS) searchFocus = SF_BOX;
        invalidate();
    }
    public boolean searchImeFallback() { return searchKbFallback; }
    private boolean searchKeysFocused() { return onSearch() && searchFocus == SF_KEYS && !inCats; }
    private boolean searchBoxFocused() { return onSearch() && searchFocus == SF_BOX && !inCats; }
    private boolean searchChipsFocused() { return onSearch() && searchFocus == SF_CHIPS && !inCats; }

    private String[][] searchKeys() {
        return KidsSearchLayout.searchKeys(searchShift, searchSym);
    }

    private void mapContentToView(RectF src, RectF dst) {
        if (!projector) { dst.set(src); return; }
        float W = getWidth(), H = getHeight(), S = 1 - 2 * SAFE;
        dst.set(W * SAFE + src.left * S, H * SAFE + src.top * S,
                W * SAFE + src.right * S, H * SAFE + src.bottom * S);
    }

    private void layoutSearch(int W, int H) {
        float den = getResources().getDisplayMetrics().density;
        float top = catsBottom() + 8 * s;
        float bot = browseTop() - 8 * s;
        float avail = Math.max(32 * s, bot - top);
        float qh = KidsSearchLayout.queryHeight(s, den, avail);
        searchQueryBox.set(24 * s, top, W - 24 * s, top + qh);
        float chipTop = searchQueryBox.bottom + KidsSearchLayout.GAP_S * s;
        float remain = bot - chipTop;
        float chipH = KidsSearchLayout.chipHeight(s, remain);
        searchChipBox.set(16 * s, chipTop, W - 16 * s, chipTop + chipH);
        String[] chips = KidsPlay.SEARCH_CHIPS;
        float gap = 8 * s;
        float cw = (searchChipBox.width() - gap * (chips.length + 1)) / Math.max(1, chips.length);
        if (cw < 64 * s) cw = 64 * s;
        float x = searchChipBox.left + gap;
        for (int i = 0; i < chips.length; i++) {
            if (searchChipRects[i] == null) searchChipRects[i] = new RectF();
            searchChipRects[i].set(x, searchChipBox.top + 2 * s, x + cw, searchChipBox.bottom - 2 * s);
            x += cw + gap;
        }
        String st = host.kidsSearchStatus();
        boolean retry = st != null && st.toLowerCase(Locale.ROOT).contains("retry");
        float retryH = retry ? Math.min(32 * s, 28 * s) : 0;
        if (searchKbFallback) {
            float kbTop = searchChipBox.bottom + 6 * s;
            float kbBot = bot - (retry ? retryH + 6 * s : 0);
            if (kbBot < kbTop + 40 * s) kbBot = kbTop + 40 * s;
            searchKbBox.set(16 * s, kbTop, W - 16 * s, kbBot);
            if (retry) searchRetryBox.set(W * 0.12f, kbBot + 4 * s, W * 0.88f, kbBot + 4 * s + retryH);
            else searchRetryBox.setEmpty();
        } else {
            searchKbBox.setEmpty();
            if (retry) searchRetryBox.set(W * 0.12f, searchChipBox.bottom + 6 * s, W * 0.88f, searchChipBox.bottom + 6 * s + retryH);
            else searchRetryBox.setEmpty();
        }
        mapContentToView(searchQueryBox, r2);
        boolean showField = onSearch() && !searchKbFallback;
        float ts = KidsSearchLayout.textSize(qh, s, den);
        float padH = KidsSearchLayout.padH(s, den);
        float padV = KidsSearchLayout.padV(qh);
        host.placeKidsSearchField(r2.left, r2.top, r2.right, r2.bottom, searchQ,
                KidsSearchLayout.HINT, searchBoxFocused(), showField, ts, padH, padV);
    }

    private void drawSearchUi(Canvas c, int W, int H) {
        layoutSearch(W, H);
        p.setShader(null); p.setColor(0xCC141518);
        c.drawRoundRect(searchQueryBox, 12 * s, 12 * s, p);
        stroke.setShader(null); stroke.setColor(searchBoxFocused() ? GOLD : 0x66FFFFFF); stroke.setStrokeWidth(2 * s);
        c.drawRoundRect(searchQueryBox, 12 * s, 12 * s, stroke);
        if (searchKbFallback) {
            String shown = searchQ.isEmpty() ? KidsSearchLayout.HINT : searchQ + (searchKeysFocused() || searchBoxFocused() ? "▌" : "");
            int col = searchQ.isEmpty() ? DIM : TXT;
            float den2 = getResources().getDisplayMetrics().density;
            float ts2 = KidsSearchLayout.textSize(searchQueryBox.height(), s, den2);
            float padH2 = KidsSearchLayout.padH(s, den2);
            float by = KidsSearchLayout.textBaselineY(searchQueryBox.top, searchQueryBox.height(), ts2);
            text(c, shown, searchQueryBox.left + padH2, by, ts2, col, Paint.Align.LEFT, false);
        }

        String[] chips = KidsPlay.SEARCH_CHIPS;
        String active = host.kidsSearchChip();
        if (active != null) {
            for (int i = 0; i < chips.length; i++) if (chips[i].equalsIgnoreCase(active)) searchChipSel = i;
        } else if (searchChipSel >= 0 && (searchQ != null && !searchQ.trim().isEmpty())) {
            searchChipSel = -1;
        }
        for (int i = 0; i < chips.length; i++) {
            RectF cr = searchChipRects[i];
            boolean on = i == searchChipSel;
            boolean nav = searchChipsFocused() && i == searchChipNav;
            p.setShader(null);
            p.setColor(on ? (0xE0000000 | (GOLD & 0x00FFFFFF)) : 0xD01C1D22);
            c.drawRoundRect(cr, cr.height() / 2, cr.height() / 2, p);
            if (nav || on) {
                stroke.setColor(nav ? 0xFFFFFFFF : GOLD);
                stroke.setStrokeWidth((nav ? 2.4f : 1.6f) * s);
                c.drawRoundRect(cr, cr.height() / 2, cr.height() / 2, stroke);
            }
            float ts = Math.min(16 * s, cr.height() * 0.48f);
            text(c, chips[i], cr.centerX(), cr.centerY() + ts * 0.35f, ts, 0xFFFFFFFF, Paint.Align.CENTER, on || nav);
        }

        if (searchKbFallback && !searchKbBox.isEmpty()) {
            String[][] keys = searchKeys();
            float pad = 4 * s;
            float kbH = searchKbBox.height(), kbW = searchKbBox.width();
            int rows = keys.length;
            float rowH = kbH / Math.max(1, rows);
            for (int r = 0; r < rows; r++) {
                int cols = keys[r].length;
                float kw = kbW / cols;
                for (int colI = 0; colI < cols; colI++) {
                    String lab = keys[r][colI];
                    RectF kr = new RectF(searchKbBox.left + colI * kw + pad, searchKbBox.top + r * rowH + pad,
                            searchKbBox.left + (colI + 1) * kw - pad, searchKbBox.top + (r + 1) * rowH - pad);
                    boolean sel = searchKeysFocused() && r == kbRow && colI == Math.min(kbCol, cols - 1);
                    p.setColor(sel ? (0xE0000000 | (GOLD & 0x00FFFFFF)) : 0xD01C1D22);
                    if (" ".equals(lab)) p.setColor(sel ? 0xE02E7D32 : 0xD0222830);
                    if ("GO".equals(lab)) p.setColor(sel ? 0xF2E53935 : 0xE0C62828);
                    c.drawRoundRect(kr, 8 * s, 8 * s, p);
                    if (sel) { stroke.setColor(0xFFFFFFFF); stroke.setStrokeWidth(2 * s); c.drawRoundRect(kr, 8 * s, 8 * s, stroke); }
                    String draw = " ".equals(lab) ? "space" : lab;
                    float ts = Math.min(22 * s, kr.height() * 0.55f);
                    if (draw.length() > 1 && !"GO".equals(draw)) ts *= 0.85f;
                    text(c, draw, kr.centerX(), kr.centerY() + ts * 0.35f, ts, 0xFFFFFFFF, Paint.Align.CENTER, "GO".equals(draw) || sel);
                }
            }
        }
        if (!searchRetryBox.isEmpty()) {
            p.setColor(0xE0C62828);
            c.drawRoundRect(searchRetryBox, 12 * s, 12 * s, p);
            String st = host.kidsSearchStatus();
            if (st == null || st.isEmpty()) st = "Couldn't load more games, tap to retry";
            text(c, st, searchRetryBox.centerX(), searchRetryBox.centerY() + 5 * s, 13 * s, 0xFFFFFFFF, Paint.Align.CENTER, true);
        } else {
            String st = host.kidsSearchStatus();
            if (st != null && !st.isEmpty() && !st.toLowerCase(Locale.ROOT).contains("retry")) {
                float sy = searchKbFallback && !searchKbBox.isEmpty() ? searchKbBox.bottom + 16 * s : searchChipBox.bottom + 18 * s;
                text(c, st, W / 2f, sy, 13 * s, DIM, Paint.Align.CENTER, false);
            }
        }
    }

    private boolean inSearchUi(float x, float y) {
        if (searchQueryBox.contains(x, y) || searchChipBox.contains(x, y)
                || (!searchRetryBox.isEmpty() && searchRetryBox.contains(x, y))) return true;
        return searchKbFallback && searchKbBox.contains(x, y);
    }

    private boolean tapSearchChip(float x, float y) {
        if (!searchChipBox.contains(x, y)) return false;
        String[] chips = KidsPlay.SEARCH_CHIPS;
        for (int i = 0; i < chips.length; i++) {
            if (searchChipRects[i] != null && searchChipRects[i].contains(x, y)) {
                activateSearchChip(i);
                return true;
            }
        }
        return false;
    }

    private void activateSearchChip(int i) {
        String[] chips = KidsPlay.SEARCH_CHIPS;
        if (i < 0 || i >= chips.length) return;
        searchChipSel = i;
        searchChipNav = i;
        searchQ = "";
        inCats = false;
        searchFocus = SF_TILES;
        host.hideKidsIme();
        host.sound("hhselect");
        host.runKidsSearchChip(chips[i]);
        invalidate();
    }

    private boolean tapSearchKey(float x, float y) {
        if (!searchKbFallback || !searchKbBox.contains(x, y)) return false;
        String[][] keys = searchKeys();
        int rows = keys.length;
        float kbH = searchKbBox.height(), kbW = searchKbBox.width();
        float rowH = kbH / Math.max(1f, rows);
        int r = (int) Math.floor((y - searchKbBox.top) / Math.max(1f, rowH));
        if (r < 0 || r >= rows) return false;
        int cols = keys[r].length;
        float kw = kbW / cols;
        int colI = (int) Math.floor((x - searchKbBox.left) / Math.max(1f, kw));
        if (colI < 0 || colI >= cols) return false;
        kbRow = r; kbCol = colI; searchFocus = SF_KEYS; inCats = false;
        pressSearchKey(keys[r][colI]);
        return true;
    }

    private void pressSearchKey(String lab) {
        if (lab == null) return;
        host.sound("hhmove");
        if ("⇧".equals(lab)) { searchShift = !searchShift; invalidate(); return; }
        if ("123".equals(lab)) { searchSym = true; searchShift = false; kbRow = 0; kbCol = 0; invalidate(); return; }
        if ("ABC".equals(lab)) { searchSym = false; kbRow = 0; kbCol = 0; invalidate(); return; }
        if ("⌫".equals(lab)) { if (searchQ.length() > 0) searchQ = searchQ.substring(0, searchQ.length() - 1); searchChipSel = -1; scheduleSearch(); invalidate(); return; }
        if (KidsSearchLayout.isHideKey(lab)) { hideAnyKeyboard(); return; }
        if ("GO".equals(lab) || "go".equals(lab)) { hideAnyKeyboard(); searchFocus = SF_TILES; host.runKidsSearch(searchQ); invalidate(); return; }
        searchChipSel = -1;
        if (" ".equals(lab)) searchQ += " ";
        else searchQ += lab;
        if (!searchSym) searchShift = false;
        scheduleSearch();
        invalidate();
    }

    final HomeSearchDebounce searchDebounce = new HomeSearchDebounce(this);
    void fireSearch() { if (onSearch()) host.runKidsSearch(searchQ); }
    private void scheduleSearch() {
        removeCallbacks(searchDebounce);
        postDelayed(searchDebounce, 450);
    }

    private boolean handleSearchKey(int code, KeyEvent e) {
        if (!onSearch()) return false;
        if (searchKeysFocused()) {
            if (code == KeyEvent.KEYCODE_DEL || code == KeyEvent.KEYCODE_BUTTON_L1) { pressSearchKey("⌫"); return true; }
            if (code == KeyEvent.KEYCODE_BUTTON_R1) { pressSearchKey(searchSym ? "ABC" : "⇧"); return true; }
            if (code == KeyEvent.KEYCODE_SPACE) { pressSearchKey(" "); return true; }
            if (code == KeyEvent.KEYCODE_ENTER && e.getRepeatCount() == 0) { pressSearchKey("GO"); return true; }
            if (code == KeyEvent.KEYCODE_DPAD_LEFT) { moveKb(0, -1); return true; }
            if (code == KeyEvent.KEYCODE_DPAD_RIGHT) { moveKb(0, 1); return true; }
            if (code == KeyEvent.KEYCODE_DPAD_UP) {
                if (kbRow == 0) { searchFocus = SF_CHIPS; host.sound("hhmove"); invalidate(); }
                else moveKb(-1, 0);
                return true;
            }
            if (code == KeyEvent.KEYCODE_DPAD_DOWN) {
                String[][] keys = searchKeys();
                if (kbRow >= keys.length - 1) { searchFocus = SF_TILES; inCats = false; host.sound("hhmove"); invalidate(); }
                else moveKb(1, 0);
                return true;
            }
            if (code == KeyEvent.KEYCODE_BUTTON_A || code == KeyEvent.KEYCODE_DPAD_CENTER) {
                if (e.getRepeatCount() == 0) {
                    String[][] keys = searchKeys();
                    int r = Math.max(0, Math.min(kbRow, keys.length - 1));
                    int c = Math.max(0, Math.min(kbCol, keys[r].length - 1));
                    pressSearchKey(keys[r][c]);
                }
                return true;
            }
            int u = e.getUnicodeChar();
            if (u >= 32 && u < 127 && code != KeyEvent.KEYCODE_ENTER) { pressSearchKey(String.valueOf((char) u)); return true; }
        }
        if (searchBoxFocused()) {
            if (code == KeyEvent.KEYCODE_DPAD_DOWN) {
                searchFocus = SF_CHIPS; host.hideKidsIme(); host.sound("hhmove"); invalidate(); return true;
            }
            if (code == KeyEvent.KEYCODE_DPAD_UP) {
                inCats = true; host.hideKidsIme(); host.sound("hhmove"); invalidate(); return true;
            }
            if ((code == KeyEvent.KEYCODE_BUTTON_A || code == KeyEvent.KEYCODE_DPAD_CENTER) && e.getRepeatCount() == 0) {
                host.requestKidsIme(); return true;
            }
            if (code == KeyEvent.KEYCODE_ENTER && e.getRepeatCount() == 0) { hideAnyKeyboard(); fireSearch(); return true; }
            return false;   // IME / hardware typing goes to the EditText
        }
        if (searchChipsFocused()) {
            String[] chips = KidsPlay.SEARCH_CHIPS;
            if (code == KeyEvent.KEYCODE_DPAD_LEFT) {
                searchChipNav = (searchChipNav - 1 + chips.length) % chips.length; host.sound("hhmove"); invalidate(); return true;
            }
            if (code == KeyEvent.KEYCODE_DPAD_RIGHT) {
                searchChipNav = (searchChipNav + 1) % chips.length; host.sound("hhmove"); invalidate(); return true;
            }
            if (code == KeyEvent.KEYCODE_DPAD_UP) {
                searchFocus = SF_BOX; host.requestKidsIme(); host.sound("hhmove"); invalidate(); return true;
            }
            if (code == KeyEvent.KEYCODE_DPAD_DOWN) {
                if (searchKbFallback) searchFocus = SF_KEYS;
                else { searchFocus = SF_TILES; inCats = false; }
                host.sound("hhmove"); invalidate(); return true;
            }
            if ((code == KeyEvent.KEYCODE_BUTTON_A || code == KeyEvent.KEYCODE_DPAD_CENTER) && e.getRepeatCount() == 0) {
                activateSearchChip(searchChipNav); return true;
            }
        }
        // From the result row, Up returns to chips (or the fallback keyboard).
        if (!inCats && searchFocus == SF_TILES && code == KeyEvent.KEYCODE_DPAD_UP) {
            searchFocus = searchKbFallback ? SF_KEYS : SF_CHIPS; host.sound("hhmove"); invalidate(); return true;
        }
        // From the tab strip, Down opens the search field (not the tiles first).
        if (inCats && code == KeyEvent.KEYCODE_DPAD_DOWN) {
            inCats = false; searchFocus = SF_BOX; host.requestKidsIme(); host.sound("hhmove"); invalidate(); return true;
        }
        return false;
    }

    private void moveKb(int dr, int dc) {
        String[][] keys = searchKeys();
        kbRow = Math.max(0, Math.min(keys.length - 1, kbRow + dr));
        kbCol = Math.max(0, Math.min(keys[kbRow].length - 1, kbCol + dc));
        host.sound("hhmove");
        invalidate();
    }

    private String searchEmptyMessage() {
        String st = host.kidsSearchStatus();
        if (st != null && !st.isEmpty()) return st;
        if (searchChipSel >= 0) return "Couldn't load more games, tap to retry.";
        if (searchQ.trim().isEmpty()) return "Games on this device show here. Type a name, or pick a category, to find more on the Play Store.";
        return "No games found.";
    }
    private String rowRetryMessage() {
        String st = host.scanNote(cats.get(Math.max(0, Math.min(cat, cats.size() - 1))).id);
        if (st != null && st.toLowerCase(Locale.ROOT).contains("retry")) return st;
        return null;
    }

    private void ensurePhoenix(int W, int H) {
        if (W < 2 || H < 2) return;
        if (phoenixScreen != null && phoenixSw == W && phoenixSh == H && !phoenixScreen.isRecycled()) return;
        if (!phoenixTried) {
            phoenixTried = true;
            new Thread(new PhoenixLoad(this, W, H), "phoenix-bg").start();
            return;
        }
        if (phoenixBg != null) bakePhoenix(W, H);
    }

    void loadPhoenix(int rw, int rh) {
        Bitmap raw = null;
        try { raw = BitmapFactory.decodeResource(getResources(), R.drawable.phoenix_bg); }
        catch (Throwable ignored) { return; }
        if (raw == null) return;
        Bitmap baked = null;
        try { baked = scalePhoenix(raw, rw, rh); } catch (Throwable ignored) { }
        post(new PhoenixReady(this, raw, baked));
    }

    void phoenixReady(Bitmap src, Bitmap ready) {
        phoenixBg = src;
        if (ready != null) { phoenixScreen = ready; phoenixSw = ready.getWidth(); phoenixSh = ready.getHeight(); }
        else bakePhoenix(getWidth(), getHeight());
        chromeReady = false;
        artArrived();
    }

    private void bakePhoenix(int W, int H) {
        if (phoenixBg == null || W < 2 || H < 2) return;
        if (phoenixScreen != null && phoenixSw == W && phoenixSh == H && !phoenixScreen.isRecycled()) return;
        try {
            Bitmap out = scalePhoenix(phoenixBg, W, H);
            if (out == null) return;
            phoenixScreen = out;
            phoenixSw = W;
            phoenixSh = H;
        } catch (Throwable ignored) { }
    }

    private static Bitmap scalePhoenix(Bitmap raw, int W, int H) {
        if (raw == null || W < 2 || H < 2) return null;
        Bitmap out = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        float sc = Math.max(W / (float) raw.getWidth(), H / (float) raw.getHeight());
        float bw = raw.getWidth() * sc, bh = raw.getHeight() * sc;
        Paint pp = new Paint(Paint.FILTER_BITMAP_FLAG);
        c.drawBitmap(raw, null, new RectF((W - bw) / 2, (H - bh) / 2, (W + bw) / 2, (H + bh) / 2), pp);
        c.drawColor(0x59000000);
        return out;
    }

    private void drawTileStroke(Canvas c, boolean sel, float rad) {
        stroke.setStrokeWidth((sel ? 3 : 1.5f) * s);
        if (row.busy()) {
            stroke.setShader(null);
            stroke.setColor(sel ? GOLD : T.border);
        } else {
            stroke.setShader(new LinearGradient(0, r.top, 0, r.bottom, sel ? new int[]{T.goldLight, GOLD, GOLD_D} : new int[]{T.border, T.borderDark, T.border}, null, Shader.TileMode.CLAMP));
        }
        c.drawRoundRect(r, rad, rad, stroke);
        stroke.setShader(null);
    }

    /** Cached pre-scaled cover. A miss is baked on a worker thread, never on the draw thread. */
    private Bitmap bakedTile(Catalog.Item it, Bitmap cov, int tw, int th, float rad) {
        if (it == null || cov == null || cov.isRecycled() || tw < 2 || th < 2) return null;
        String key = HomeDrawGate.tileKey(it.key(), tw, th, cov.getWidth(), cov.getHeight());
        Bitmap have = tileBmps.get(key);
        if (have != null && !have.isRecycled()) return have;
        if (!HomeDrawGate.bakeOnDrawThread()
                && !bakeQueued.contains(key)
                && bakeQueued.add(key)) {
            new Thread(new HomeTileBake(this, key, cov, tw, th, rad), "home-tile-bake").start();
        }
        return null;
    }

    /** Called on the UI thread when a background bake finishes. */
    void tileBaked(String key, Bitmap bmp) {
        bakeQueued.remove(key);
        if (bmp != null) tileBmps.put(key, bmp);
        artArrived();
    }

    private Bitmap bakeCover(Bitmap cov, int tw, int th, float rad) {
        try {
            Bitmap out = Bitmap.createBitmap(tw, th, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(out);
            int sw = cov.getWidth(), sh = cov.getHeight();
            int[] box;
            if (cov.hasAlpha()) {
                int n = sw * sh;
                if (coverPixels == null || coverPixels.length < n) coverPixels = new int[n];
                cov.getPixels(coverPixels, 0, sw, 0, 0, sw, sh);
                box = CoverFit.coverSrcTrimmed(coverPixels, sw, sh, tw, th, 16);
            } else {
                box = CoverFit.coverSrc(sw, sh, tw, th);
            }
            if (box[2] <= box[0] || box[3] <= box[1]) box = new int[]{0, 0, sw, sh};
            c.drawColor(0xFF101418);
            tileBake.setXfermode(null);
            tileBake.setShader(null);
            tileBake.setAlpha(255);
            tileBake.setFilterBitmap(true);
            tileBake.setAntiAlias(true);
            bakeSrc.set(box[0], box[1], box[2], box[3]);
            bakeDst.set(-1, -1, tw + 1, th + 1);   // 1px overscan, sits under the gold stroke
            c.drawBitmap(cov, bakeSrc, bakeDst, tileBake);
            tileBake.setShader(new LinearGradient(0, th * 0.52f, 0, th, 0x00000000, 0xF2000000, Shader.TileMode.CLAMP));
            c.drawRect(0, th * 0.52f, tw, th, tileBake);
            tileBake.setShader(null);
            tileBake.setXfermode(bakeDstIn);
            tileBake.setColor(0xFFFFFFFF);
            c.drawRoundRect(new RectF(0, 0, tw, th), rad, rad, tileBake);
            tileBake.setXfermode(null);
            return out;
        } catch (Throwable ignored) {
            return null;
        }
    }

}

/** Package-level (not an inner class) so the older D8 dexer can compile the search debounce. */
final class HomeSearchDebounce implements Runnable {
    final HomeView view;
    HomeSearchDebounce(HomeView view) { this.view = view; }
    @Override public void run() { view.fireSearch(); }
}

/** Package-level cache of baked tiles. Not android.util.LruCache: D8 8.2.2 crashes when that class is subclassed. */
final class TileBmpCache {
    private final int maxKb;
    private int usedKb;
    private final java.util.LinkedHashMap<String, android.graphics.Bitmap> map =
            new java.util.LinkedHashMap<String, android.graphics.Bitmap>(16, 0.75f, true);

    TileBmpCache(int cacheKb) { this.maxKb = Math.max(1024, cacheKb); }

    synchronized android.graphics.Bitmap get(String key) { return map.get(key); }

    synchronized void put(String key, android.graphics.Bitmap value) {
        if (key == null || value == null) return;
        android.graphics.Bitmap old = map.put(key, value);
        if (old != null) usedKb -= sizeOf(old);
        usedKb += sizeOf(value);
        evict();
    }

    private static int sizeOf(android.graphics.Bitmap b) {
        return b == null ? 1 : Math.max(1, b.getByteCount() / 1024);
    }

    private void evict() {
        java.util.Iterator<java.util.Map.Entry<String, android.graphics.Bitmap>> it = map.entrySet().iterator();
        while (usedKb > maxKb && it.hasNext()) {
            java.util.Map.Entry<String, android.graphics.Bitmap> e = it.next();
            usedKb -= sizeOf(e.getValue());
            it.remove();
        }
    }
}

final class PhoenixLoad implements Runnable {
    final HomeView view;
    final int w, h;
    PhoenixLoad(HomeView view, int w, int h) { this.view = view; this.w = w; this.h = h; }
    @Override public void run() { view.loadPhoenix(w, h); }
}

final class PhoenixReady implements Runnable {
    final HomeView view;
    final android.graphics.Bitmap src, ready;
    PhoenixReady(HomeView view, android.graphics.Bitmap src, android.graphics.Bitmap ready) {
        this.view = view; this.src = src; this.ready = ready;
    }
    @Override public void run() { view.phoenixReady(src, ready); }
}


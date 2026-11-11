package com.codeaque.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** CodeAque HOME launcher. Android stays the system underneath; this is just the home screen. */
public class MainActivity extends Activity implements HomeView.Host {
    private HomeView view;
    private SoundPool pool;
    private final Map<String, Integer> sounds = new HashMap<>();
    private final Map<String, Boolean> installedCache = new HashMap<>();
    private SharedPreferences prefs;
    // 0.10.34: Roblox Mode public explore list (adult home). Kids mode keeps the official short list.
    private List<Catalog.Item> robloxItems;
    private String robloxSession = "", robloxToken = "";
    private boolean robloxOn, robloxLoading;
    private long robloxMoreAt;

    @Override protected void onCreate(Bundle b) {
        Catalog.VERSION = myVersion();   // 0.10.28
        super.onCreate(b);
        prefs = getSharedPreferences("codeaque", MODE_PRIVATE);
        ensurePin();                                  // 0.10.23: default parent PIN 1234 (hashed)
        edgeToEdge();
        view = new HomeView(this, this);
        setContentView(view);
        immersive();
        rescan();
        IntentFilter pf = new IntentFilter();         // an app or game installed / removed while CodeAque is open
        pf.addAction(Intent.ACTION_PACKAGE_ADDED); pf.addAction(Intent.ACTION_PACKAGE_REMOVED);
        pf.addAction(Intent.ACTION_PACKAGE_CHANGED); pf.addAction(Intent.ACTION_PACKAGE_REPLACED);
        pf.addDataScheme("package");
        registerReceiver(packages, pf);
        view.requestFocus();
        pool = new SoundPool.Builder().setMaxStreams(3).setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build();
        for (String n : new String[]{"hhmove", "hhcat", "hhselect", "hhback", "powerup", "chime", "jingle"}) {
            try { sounds.put(n, pool.load(getAssets().openFd(n + ".wav"), 1)); } catch (Exception ignored) { }
        }
        Intent in0 = getIntent();      // am start -e theme powerup  (same as Settings > Theme)
        if (in0 != null && in0.hasExtra("theme")) prefs.edit().putString("theme", Palette.byId(in0.getStringExtra("theme")).id).apply();
        view.setTheme(Palette.byId(prefs.getString("theme", "neon")));
        loadWallpaper();          // 0.10.25: the update check runs from onResume (start and every return)
        maybeAskHomeOnce();
        maybeSuggestRotation();
        PackageManager pm0 = getPackageManager();        // a TV box / projector: suggest Projector mode once
        if (!prefs.contains("projector") && !prefs.getBoolean("projHint", false)
                && (pm0.hasSystemFeature(PackageManager.FEATURE_LEANBACK) || !pm0.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN))) {
            prefs.edit().putBoolean("projHint", true).apply();
            Toast.makeText(this, "On a TV or projector? Try Settings > Projector mode", Toast.LENGTH_LONG).show();
        }
        java.util.Calendar cal = java.util.Calendar.getInstance();          // December: suggest the Christmas theme once a year
        String yr = "xmasHint" + cal.get(java.util.Calendar.YEAR);
        if (cal.get(java.util.Calendar.MONTH) == java.util.Calendar.DECEMBER && !theme().snow && !prefs.getBoolean(yr, false)) {
            prefs.edit().putBoolean(yr, true).apply();
            Toast.makeText(this, "It's December: try the Christmas theme in Settings > Theme", Toast.LENGTH_LONG).show();
        }
        final String launch = theme().selectSound;      // the theme's sound once on launch
        if (launch != null) pool.setOnLoadCompleteListener((sp, id, st) -> { Integer want = sounds.get(launch); if (want != null && id == want) sp.play(id, 0.6f, 0.6f, 1, 0, 1f); });
        Intent i = getIntent();     // for screenshots/tests: am start -e cat cloud -e tile 1
        if (i != null && i.hasExtra("cat")) view.focus(i.getStringExtra("cat"), i.getIntExtra("tile", 0));
        if (b == null) parentEntry(i);   // 0.10.30: CodeAque Adult / CodeAque Settings icon or long-press shortcut
    }

    @Override protected void onNewIntent(Intent i) {      // pressing Home again, the screenshot extras, or the installer's answer
        super.onNewIntent(i);
        if ("com.codeaque.launcher.INSTALLED".equals(i.getAction())) {
            int st = i.getIntExtra(android.content.pm.PackageInstaller.EXTRA_STATUS, -999);
            if (st == android.content.pm.PackageInstaller.STATUS_PENDING_USER_ACTION) {
                Intent confirm = i.getParcelableExtra(Intent.EXTRA_INTENT);     // Android's own "Install this update?" prompt
                if (confirm != null) startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } else if (st != android.content.pm.PackageInstaller.STATUS_SUCCESS)
                Toast.makeText(this, "Update not installed: " + i.getStringExtra(android.content.pm.PackageInstaller.EXTRA_STATUS_MESSAGE), Toast.LENGTH_LONG).show();
            return;
        }
        if (parentEntry(i)) return;      // 0.10.30
        if (i.hasExtra("theme")) { prefs.edit().putString("theme", Palette.byId(i.getStringExtra("theme")).id).apply(); view.setTheme(theme()); }
        if (i.hasExtra("cat")) view.focus(i.getStringExtra("cat"), i.getIntExtra("tile", 0));
    }

    @Override protected void onResume() {
        super.onResume();
        scanDreamcast();
        kidsDiff();
        lockResume();                 // 0.10.21
        ensureHome();                 // 0.10.28: stop the Home chooser coming back after restarts
        maybeUpdateCheck();           // 0.10.25
        newAppsCheck();               // 0.10.27: every start / resume, at most every 30 minutes
        installedCache.clear();       // apps may have been installed while we were away
        immersive();
        rescan();
        view.invalidate();
    }

    @Override public void onWindowFocusChanged(boolean has) {
        super.onWindowFocusChanged(has);
        if (has) immersive();          // after a dialog, the volume panel or a swipe that showed the bars
        if (has && pendingPopup != null) { UpdateInfo u = pendingPopup; view.postDelayed(() -> { if (hasWindowFocus() && pendingPopup == u) updatePopup(u, myVersion()); }, 600); }
    }

    // ---------- 0.9.4: true full screen (no status or navigation bar, drawn under the camera cut-out) ----------
    private void edgeToEdge() {
        android.view.Window w = getWindow();
        w.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS | WindowManager.LayoutParams.FLAG_FULLSCREEN
               );
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            WindowManager.LayoutParams lp = w.getAttributes();
            // SHORT_EDGES left a black band on Android 14 when the cut-out is on a long edge (landscape-native screens);
            // ALWAYS (Android 11+) draws under every cut-out
            lp.layoutInDisplayCutoutMode = android.os.Build.VERSION.SDK_INT >= 30
                    ? WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                    : WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            w.setAttributes(lp);
        }
        if (android.os.Build.VERSION.SDK_INT >= 30) w.setDecorFitsSystemWindows(false);
        w.setStatusBarColor(0); w.setNavigationBarColor(0);
    }

    @SuppressWarnings("deprecation")
    private void immersive() {
        android.view.Window w = getWindow();
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            w.setDecorFitsSystemWindows(false);
            android.view.WindowInsetsController c = w.getInsetsController();
            if (c != null) {
                c.hide(android.view.WindowInsets.Type.statusBars() | android.view.WindowInsets.Type.navigationBars());
                c.setSystemBarsBehavior(android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            w.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }
    }

    // ---------- 0.9.4: games and apps already on the device, emulators, and the user's own game folders ----------
    static final class PackageWatch extends android.content.BroadcastReceiver {
        private final java.lang.ref.WeakReference<MainActivity> a;
        PackageWatch(MainActivity m) { a = new java.lang.ref.WeakReference<>(m); }
        @Override public void onReceive(Context c, Intent i) {
            MainActivity m = a.get();
            if (m == null) return;
            if (Intent.ACTION_PACKAGE_ADDED.equals(i.getAction()) && !i.getBooleanExtra(Intent.EXTRA_REPLACING, false) && i.getData() != null)
                m.kidsInstalled(i.getData().getSchemeSpecificPart());
            m.installedCache.clear(); m.icons.clear(); m.coverCache.clear(); m.rescan(); m.newAppsCheck();
        }
    }
    private final PackageWatch packages = new PackageWatch(this);
    private final Map<String, android.graphics.Bitmap> icons = new HashMap<>();
    private volatile List<Catalog.Item> roms = new ArrayList<>();
    private volatile boolean romsScanned;

    private static boolean isGame(android.content.pm.ApplicationInfo ai) {
        if (ai == null) return false;
        if (android.os.Build.VERSION.SDK_INT >= 26 && ai.category == android.content.pm.ApplicationInfo.CATEGORY_GAME) return true;
        return (ai.flags & android.content.pm.ApplicationInfo.FLAG_IS_GAME) != 0;     // older apps mark themselves this way
    }

    /** Reads the launchable apps (the manifest's MAIN/LAUNCHER <queries> entry makes them visible; no QUERY_ALL_PACKAGES). */
    private void rescan() {
        PackageManager pm = getPackageManager();
        List<ResolveInfo> ri = new ArrayList<>(pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0));
        ri.addAll(pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0));
        java.util.Set<String> emuPkgs = new java.util.HashSet<>();
        for (Catalog.Item e : Catalog.emulators()) emuPkgs.addAll(e.packages);
        List<Catalog.Item> games = new ArrayList<>(), apps = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (ResolveInfo r : ri) {
            String pkg = r.activityInfo.packageName;
            if (pkg.equals(getPackageName()) || !seen.add(pkg)) continue;
            if (locked() && LockPin.blocked(pkg)) continue;   // 0.10.21 hidden while locked
            Catalog.Item it = Catalog.deviceApp(r.loadLabel(pm).toString(), pkg, r.activityInfo.name);
            apps.add(it);
            if (isGame(r.activityInfo.applicationInfo) && !emuPkgs.contains(pkg)) games.add(it);
        }
        if (locked()) {   // 0.10.29: no other launcher is listed, so it cannot be opened from inside CodeAque
            java.util.Set<String> homes = new java.util.HashSet<>();
            for (ResolveInfo h : pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0))
                if (h.activityInfo != null) homes.add(h.activityInfo.packageName);
            java.util.Iterator<Catalog.Item> it = apps.iterator();
            while (it.hasNext()) { String pkg = it.next().pkg; if (homes.contains(pkg) && !pkg.equals(getPackageName())) it.remove(); }
            it = games.iterator();
            while (it.hasNext()) { String pkg = it.next().pkg; if (homes.contains(pkg) && !pkg.equals(getPackageName())) it.remove(); }
        }
        java.util.Comparator<Catalog.Item> az = (x, y) -> x.label.compareToIgnoreCase(y.label);
        Collections.sort(apps, az); Collections.sort(games, az);
        List<Catalog.Item> emus = new ArrayList<>();
        for (Catalog.Item e : Catalog.emulators()) if (launchIntent(e) != null) emus.add(e);
        boolean proj = prefs.getBoolean("projector", false);
        view.setProjector(proj);
        boolean kidsOn = prefs.getBoolean("kidsMode", false);
        view.setKids(kidsOn);
        List<Catalog.Item> allRoms = new ArrayList<>(roms); allRoms.addAll(dcFiles);
        List<Catalog.Item> kidsNew = new ArrayList<>();   // 0.10.6: games installed during Kids mode
        java.util.Set<String> kidsSet = prefs.getStringSet("kidsAdded", Collections.<String>emptySet());
        java.util.Set<String> approved = prefs.getStringSet("kidsApproved", Collections.<String>emptySet());   // 0.10.27
        for (Catalog.Item a : apps) if (kidsSet.contains(a.pkg) || approved.contains(a.pkg)) kidsNew.add(a);
        List<Catalog.Category> cs = new ArrayList<>(kidsOn ? Catalog.kidsMode(allRoms, kidsNew, approved)
                : Catalog.applyPlacements(Catalog.categories(games, apps, emus, allRoms, proj, bootAnimAvailable()),
                        Catalog.placements(prefs.getStringSet("placed", null)), apps));
        if (hasPhone()) cs.add(Math.min(1, cs.size()), Catalog.callRow());   // 0.10.29: Phone / Messages in Kids mode and adult mode
        if (locked()) cs = Catalog.applyLockMode(cs);   // Lock mode, Android settings and Home tiles disappear
        if (!kidsOn && robloxItems != null && !robloxItems.isEmpty())
            cs = Catalog.replaceItems(cs, "roblox", robloxItems);  // 0.10.34: keep the discovered list across a rescan
        syncAliases();                                  // 0.10.30: Adult / Settings home-screen icons off while locked
        view.setCategories(cs);
        if (view.boxSet() && !kidsOn) view.setBoxSet(true, java.util.Arrays.asList(Catalog.boxSetItems()));
        else if (kidsOn) view.setBoxSet(false, java.util.Collections.emptyList());
        if (!romsScanned) { romsScanned = true; scanRoms(); }
        if (!boxsetBootApplied) {
            boxsetBootApplied = true;
            if (prefs.getBoolean("boxsetBoot", false) && !kidsOn && !locked()) enterBoxSet();
        }
    }

    @Override public android.graphics.Bitmap appIcon(Catalog.Item it) {
        if (it == Catalog.MARVEL_RIVALS) return rivalsHeader();
        if (!it.isDeviceApp()) return playArt(it, false);
        String k = it.key();
        if (icons.containsKey(k)) return icons.get(k);
        final String pkg = it.pkg, cls = it.cls;
        final int px = (int) (64 * getResources().getDisplayMetrics().density);
        return decodeLater(icons, k, () -> {
            android.graphics.drawable.Drawable d = getPackageManager().getActivityIcon(new android.content.ComponentName(pkg, cls));
            android.graphics.Bitmap b = android.graphics.Bitmap.createBitmap(px, px, android.graphics.Bitmap.Config.ARGB_8888);
            android.graphics.Canvas cv = new android.graphics.Canvas(b);
            d.setBounds(0, 0, px, px); d.draw(cv);
            return b;
        });
    }

    // ---------- 0.10.3: pictures from the game's own Google Play listing (runtime only, cached, never bundled) ----------
    private final Map<String, android.graphics.Bitmap> playArtCache = new HashMap<>();

    /** Not-installed Play tiles: the listing's icon (or feature graphic when wide); installed ones keep their own icon. */
    /** The listing's own wide feature graphic, installed or not (Featured row); null = keep the icon. */
    private final java.util.HashMap<Integer, android.graphics.Bitmap> ownArt = new java.util.HashMap<>();
    private android.graphics.Bitmap playFeature(Catalog.Item it) { return it.storePackage() == null ? null : playFeature(it.storePackage()); }
    private android.graphics.Bitmap playFeature(String pkg) {
        String k = pkg + "#F";
        if (playArtCache.containsKey(k)) return playArtCache.get(k);
        java.io.File dir = new java.io.File(getCacheDir(), "playart"), f = PlayArt.featureFile(dir, pkg);
        if (f.isFile()) return decodeLater(playArtCache, k, () -> decodeCover(f));
        if (!PlayArt.missFile(dir, pkg).exists() && online() && fetching.add("play:" + pkg))
            new Thread(() -> {
                boolean ok = PlayArt.fetch(PlayArt.PLAY, pkg, dir);
                runOnUiThread(() -> { fetching.remove("play:" + pkg); if (!ok || !f.isFile()) playArtCache.put(k, null); view.invalidate(); });
            }).start();
        return null;
    }

    /** 0.10.17: Kids Games tiles. The game's own Play listing feature graphic (fetched at runtime like GeForce NOW's,
     *  cached, never bundled); without one, its icon scaled up and centred over a blurred copy of itself. */
    private final Map<String, android.graphics.Bitmap> wideCache = new HashMap<>();
    @Override public android.graphics.Bitmap wideCover(Catalog.Item it) {
        if (it.isRom()) return cover(it);
        String pkg = it.storePackage() != null ? it.storePackage() : it.pkg;
        if (pkg != null) { android.graphics.Bitmap f = playFeature(pkg); if (f != null) return f; }
        android.graphics.Bitmap ai = appIcon(it);
        if (ai == null && pkg != null && it.isDeviceApp()) ai = playIconFor(pkg);
        if (ai == null) return null;
        String k = "wide:" + it.key() + "#" + ai.getWidth() + "x" + ai.getHeight();
        final android.graphics.Bitmap src = ai;
        return decodeLater(wideCache, k, () -> iconBanner(src));
    }
    private android.graphics.Bitmap playIconFor(String pkg) {
        java.io.File f = PlayArt.iconFile(new java.io.File(getCacheDir(), "playart"), pkg);
        if (!f.isFile()) return null;
        return decodeLater(playArtCache, pkg + "#pi", () -> decodeCover(f));
    }
    static android.graphics.Bitmap iconBanner(android.graphics.Bitmap ai) {
        int W = 640, H = 344;
        android.graphics.Bitmap out = android.graphics.Bitmap.createBitmap(W, H, android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas cv = new android.graphics.Canvas(out);
        android.graphics.Paint pp = new android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG | android.graphics.Paint.ANTI_ALIAS_FLAG);
        cv.drawColor(0xFF101418);
        android.graphics.Bitmap tiny = android.graphics.Bitmap.createScaledBitmap(ai, 6, 6, true);          // blur: shrink hard, then
        android.graphics.Bitmap mid = android.graphics.Bitmap.createScaledBitmap(tiny, 24, 24, true);       // grow back in smooth steps
        cv.drawBitmap(mid, null, new android.graphics.RectF(-W * 0.1f, -W * 0.5f + H / 2f, W * 1.1f, W * 0.6f + H / 2f), pp);
        cv.drawColor(0x66000000);
        float is = H * 0.62f, cx = W / 2f, cy = H * 0.42f;
        pp.setColor(0x55000000); cv.drawRoundRect(new android.graphics.RectF(cx - is / 2 + 4, cy - is / 2 + 8, cx + is / 2 + 4, cy + is / 2 + 8), is * 0.2f, is * 0.2f, pp);
        android.graphics.Path clipP = new android.graphics.Path(); android.graphics.RectF ir = new android.graphics.RectF(cx - is / 2, cy - is / 2, cx + is / 2, cy + is / 2);
        clipP.addRoundRect(ir, is * 0.2f, is * 0.2f, android.graphics.Path.Direction.CW);
        cv.save(); cv.clipPath(clipP); pp.setColor(0xFFFFFFFF); cv.drawBitmap(ai, null, ir, pp); cv.restore();
        return out;
    }

    private android.graphics.Bitmap playArt(Catalog.Item it, boolean feature) {
        if (it.isAction() || it.isRom() || it.webUrl != null || it.storePackage() == null) return null;
        Intent li = launchIntent(it);
        if (li != null && li.getComponent() != null)
            return feature ? null : appIcon(Catalog.deviceApp(it.label, li.getComponent().getPackageName(), li.getComponent().getClassName()));
        String pkg = it.storePackage(), k = pkg + (feature ? "#f" : "#i");
        if (playArtCache.containsKey(k)) return playArtCache.get(k);
        java.io.File dir = new java.io.File(getCacheDir(), "playart");
        java.io.File f = feature ? PlayArt.featureFile(dir, pkg) : PlayArt.iconFile(dir, pkg);
        if (f.isFile()) return decodeLater(playArtCache, k, () -> decodeCover(f));
        android.graphics.Bitmap b = null;
        if (!PlayArt.missFile(dir, pkg).exists() && online() && fetching.add("play:" + pkg))
            new Thread(() -> {
                boolean ok = PlayArt.fetch(PlayArt.PLAY, pkg, dir);
                runOnUiThread(() -> { fetching.remove("play:" + pkg); if (ok) { playArtCache.remove(pkg + "#i"); playArtCache.remove(pkg + "#f"); } else { playArtCache.put(pkg + "#i", null); playArtCache.put(pkg + "#f", null); } view.invalidate(); });
            }).start();
        if (b != null || f.isFile() || PlayArt.missFile(dir, pkg).exists()) playArtCache.put(k, b);
        return b;
    }

    // ---------- 0.9.5: game pictures ----------
    private final Map<String, android.graphics.Bitmap> coverCache = new HashMap<>();
    private final java.util.Set<String> fetching = java.util.Collections.synchronizedSet(new java.util.HashSet<>());

    @Override public android.graphics.Bitmap cover(Catalog.Item it) {
        String k = it.key();
        if (coverCache.containsKey(k)) return coverCache.get(k);
        android.graphics.Bitmap store = storeArt(it);
        if (store != null || Catalog.steamAppId(it) != null || Catalog.robloxPlaceId(it) != null || Catalog.modeAsset(it) != null)
            return store;   // 0.10.32: runtime Steam / Roblox art, original mode fallback
        if (Catalog.isDreamcastRom(it) || Catalog.isPspRom(it)) return gameCover(it);   // 0.10.12: the player's own cover, else a title card
        if (it == Catalog.GEFORCE_NOW) { android.graphics.Bitmap w = playFeature(it); if (w != null) return w; }   // 0.10.6 wide covers
        int own = it == Catalog.MARVEL_RIVALS ? R.drawable.duat_realm_s1 : it == Catalog.MINECRAFT ? R.drawable.duat_realm_blocks
                : it == Catalog.ROBLOX ? R.drawable.duat_realm_bricks : 0;   // 0.10.6: Hoodlum Asylum's own "Duat Realm" art, logos cropped out
        if (own != 0) {
            if (ownArt.containsKey(own)) return ownArt.get(own);
            final int id = own;
            if (fetching.add("res:" + id)) new Thread(() -> {
                android.graphics.Bitmap b = null;
                try { b = android.graphics.BitmapFactory.decodeResource(getResources(), id); } catch (Exception ignored) { }
                android.graphics.Bitmap fb = b;
                runOnUiThread(() -> { ownArt.put(id, fb); fetching.remove("res:" + id); view.invalidate(); });
            }).start();
            if (it == Catalog.MARVEL_RIVALS) return rivalsHeader();
            return null;
        }
        if (!it.isRom() && !it.isAction() && !it.isDeviceApp() && launchIntent(it) == null) return playArt(it, true);   // 0.10.3
        android.graphics.Bitmap b = null;
        if (!it.isRom() && !it.isAction() && !it.isDeviceApp()) {     // a catalogue tile (Steam, Steam Link): the installed app's own banner
            Intent li = launchIntent(it);
            if (li != null && li.getComponent() != null) it = Catalog.deviceApp(it.label, li.getComponent().getPackageName(), li.getComponent().getClassName());
        }
        if (it.isDeviceApp()) {                  // the game's own TV banner, decoded off the UI thread
            final String pkg = it.pkg, cls = it.cls, ck = k;
            return decodeLater(coverCache, ck, () -> {
                android.graphics.drawable.Drawable d = getPackageManager().getActivityBanner(new android.content.ComponentName(pkg, cls));
                if (d == null) d = getPackageManager().getApplicationBanner(pkg);
                if (d == null) return null;
                int w = Math.max(1, d.getIntrinsicWidth() > 0 ? Math.min(d.getIntrinsicWidth(), 640) : 320);
                int h = Math.max(1, d.getIntrinsicHeight() > 0 ? Math.min(d.getIntrinsicHeight(), 360) : 180);
                android.graphics.Bitmap banner = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888);
                d.setBounds(0, 0, w, h); d.draw(new android.graphics.Canvas(banner));
                return banner;
            });
        } else if (it.isRom() && it.boxart() != null) {
            java.io.File f = coverFile(it);
            if (f.isFile()) return decodeLater(coverCache, k, () -> decodeCover(f));
            fetchCover(it, f); return null;           // not cached yet: fetched in the background, shown when it arrives
        }
        coverCache.put(k, b);
        return b;
    }

    /** 0.10.12: cover.png / folder.jpg / boxart.* / <game>.png next to the game file (the player's own pictures only).
     *  Found and decoded in the background, cached in memory; root copy when the folder can't be listed. null = title card. */
    private final Map<String, android.graphics.Bitmap> gameCovers = new HashMap<>();
    private android.graphics.Bitmap gameCover(Catalog.Item it) {
        String k = it.uri;
        if (gameCovers.containsKey(k)) return gameCovers.get(k);
        if (!fetching.add("gc:" + k)) return null;
        new Thread(() -> {
            android.graphics.Bitmap b = null;
            try {
                java.io.File game = new java.io.File(Uri.parse(it.uri).getPath()), dir = game.getParentFile();
                java.util.List<String> want = Catalog.coverNames(game.getName(), dir == null ? null : dir.getName());
                java.io.File[] fs = dir == null ? null : dir.listFiles();
                java.io.File pick = null;
                if (fs != null) {
                    for (String w : want) { for (java.io.File f : fs) if (f.isFile() && f.getName().toLowerCase(java.util.Locale.ROOT).equals(w)) { pick = f; break; } if (pick != null) break; }
                } else if (dir != null) {   // unreadable folder (found via root): copy the picture out with su
                    StringBuilder ls = new StringBuilder();
                    String rdir = dir.getAbsolutePath().replaceFirst("^/storage/emulated/0", "/data/media/0").replaceFirst("^/storage/(?!emulated)", "/mnt/media_rw/");
                    if (su("ls -1 '" + rdir.replace("'", "'\\''") + "'", ls) == 0) {
                        String hit = null;
                        for (String w : want) { for (String l : ls.toString().split("\n")) if (l.trim().toLowerCase(java.util.Locale.ROOT).equals(w)) { hit = l.trim(); break; } if (hit != null) break; }
                        if (hit != null) {
                            java.io.File dst = new java.io.File(getCacheDir(), "gamecover-" + Integer.toHexString(k.hashCode()));
                            String src = (rdir + "/" + hit).replace("'", "'\\''");
                            if (su("cat '" + src + "' > '" + dst.getAbsolutePath() + "' && chmod 644 '" + dst.getAbsolutePath() + "'", null) == 0 && dst.length() > 0) pick = dst;
                        }
                    }
                }
                if (pick != null && pick.length() < 20_000_000) b = decodeCover(pick);
            } catch (Exception ignored) { }
            if (b == null && Catalog.isPspRom(it) && Catalog.isViceCity(it.label))   // 0.10.13: Hoodlum Asylum's own "Vice City" cover
                b = android.graphics.BitmapFactory.decodeResource(getResources(), R.drawable.ha_vice_city);
            if (b == null && Catalog.isDreamcastRom(it)) {   // 0.10.16: Hoodlum Asylum's own Dreamcast covers
                String dn = null; try { java.io.File pf = new java.io.File(Uri.parse(it.uri).getPath()).getParentFile(); if (pf != null) dn = pf.getName(); } catch (Exception ignored) { }
                String key = Catalog.haDcCover(it.label, dn);
                int id = key == null ? 0 : haDcRes(key);
                if (id != 0) b = android.graphics.BitmapFactory.decodeResource(getResources(), id);
            }
            final android.graphics.Bitmap fb = b;
            runOnUiThread(() -> { gameCovers.put(k, fb); fetching.remove("gc:" + k); if (fb != null) view.invalidate(); });
        }).start();
        return null;
    }

    private static int haDcRes(String key) {
        switch (key) {
            case "sonic_adventure": return R.drawable.ha_sonic_adventure;
            case "sonic_adventure_2": return R.drawable.ha_sonic_adventure_2;
            case "spider_man": return R.drawable.ha_spider_man;
            case "street_fighter_3": return R.drawable.ha_street_fighter_3;
            case "toy_story_2": return R.drawable.ha_toy_story_2;
            case "crazy_taxi": return R.drawable.ha_crazy_taxi;
            case "jet_grind_radio": return R.drawable.ha_jet_grind_radio;
            case "marvel_vs_capcom": return R.drawable.ha_marvel_vs_capcom;
            case "mortal_kombat_gold": return R.drawable.ha_mortal_kombat_gold;
            case "prince_of_persia": return R.drawable.ha_prince_of_persia;
            case "rayman_2": return R.drawable.ha_rayman_2;
            case "shenmue_disc_1": return R.drawable.ha_shenmue_disc_1;
            case "shenmue_disc_2": return R.drawable.ha_shenmue_disc_2;
            default: return 0;
        }
    }

    private java.io.File coverFile(Catalog.Item it) {
        java.io.File dir = new java.io.File(getFilesDir(), "covers"); dir.mkdirs();
        return new java.io.File(dir, Integer.toHexString(it.boxart().hashCode()) + ".png");
    }

    /** Puts the bitmap in cache on the UI thread when the background decode finishes. A miss is cached too, so a
     *  bad file is not decoded again on every frame. Never call BitmapFactory on the UI thread from the draw path. */
    private android.graphics.Bitmap decodeLater(Map<String, android.graphics.Bitmap> cache, String key, java.util.concurrent.Callable<android.graphics.Bitmap> job) {
        if (cache.containsKey(key)) return cache.get(key);
        if (!fetching.add("dec:" + key)) return null;
        new Thread(() -> {
            android.graphics.Bitmap b = null;
            try { b = job.call(); } catch (Exception ignored) { }
            android.graphics.Bitmap fb = b;
            runOnUiThread(() -> { cache.put(key, fb); fetching.remove("dec:" + key); if (view != null) view.invalidate(); });
        }).start();
        return null;
    }

    /** Downsamples so the long side is about 400px. Background thread only. */
    private android.graphics.Bitmap decodeCover(java.io.File f) {
        android.graphics.BitmapFactory.Options o = new android.graphics.BitmapFactory.Options(); o.inJustDecodeBounds = true;
        android.graphics.BitmapFactory.decodeFile(f.getPath(), o);
        o.inSampleSize = Catalog.sampleSize(Math.max(o.outWidth, o.outHeight), 400); o.inJustDecodeBounds = false;
        return android.graphics.BitmapFactory.decodeFile(f.getPath(), o);
    }

    /** One request to the public libretro thumbnails server for a game file the user has; a miss is remembered (no retries). */
    private void fetchCover(Catalog.Item it, java.io.File f) {
        final String url = it.boxart(), k = it.key();
        if (prefs.getStringSet("coverMiss", java.util.Collections.emptySet()).contains(url) || !fetching.add(url)) return;
        new Thread(() -> {
            boolean ok = false;
            try {
                java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
                c.setConnectTimeout(8000); c.setReadTimeout(15000);
                if (c.getResponseCode() == 200) {
                    java.io.File tmp = new java.io.File(f.getPath() + ".part");
                    try (java.io.InputStream in = c.getInputStream(); java.io.FileOutputStream out = new java.io.FileOutputStream(tmp)) {
                        byte[] b = new byte[32768]; int n; long t = 0;
                        while ((n = in.read(b)) > 0) { out.write(b, 0, n); t += n; if (t > 8L << 20) throw new java.io.IOException("too big"); }
                    }
                    ok = tmp.renameTo(f);
                } else if (c.getResponseCode() == 404) {
                    java.util.Set<String> m = new java.util.HashSet<>(prefs.getStringSet("coverMiss", java.util.Collections.emptySet()));
                    m.add(url); prefs.edit().putStringSet("coverMiss", m).apply();
                }
                c.disconnect();
            } catch (Exception ignored) { }
            final boolean got = ok;
            runOnUiThread(() -> { if (got) coverCache.remove(k); else coverCache.put(k, null); view.invalidate(); });
        }).start();
    }

    private static final int PICK_ROM_FOLDER = 8;

    private void pickRomFolder() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);     // Android's own folder picker: CodeAque only sees what you choose
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try { startActivityForResult(i, PICK_ROM_FOLDER); }
        catch (ActivityNotFoundException e) { Toast.makeText(this, "This device has no folder picker", Toast.LENGTH_LONG).show(); }
    }

    /** Lists game files in the folders the user picked (two folder levels deep, at most 500 files). Read only. */
    private void scanRoms() {
        final java.util.Set<String> trees = new java.util.HashSet<>(prefs.getStringSet("romTrees", new java.util.HashSet<>()));
        if (trees.isEmpty()) return;
        new Thread(() -> {
            List<Catalog.Item> out = new ArrayList<>();
            for (String t : trees) {
                try {
                    Uri tree = Uri.parse(t);
                    listDocs(tree, android.provider.DocumentsContract.getTreeDocumentId(tree), 0, out);
                } catch (Exception ignored) { }
            }
            Collections.sort(out, (x, y) -> x.label.compareToIgnoreCase(y.label));
            roms = out;
            runOnUiThread(() -> { romsScanned = true; rescan(); });
        }).start();
    }

    private void listDocs(Uri tree, String docId, int depth, List<Catalog.Item> out) {
        Uri kids = android.provider.DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId);
        String[] cols = {android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID, android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE};
        try (android.database.Cursor c = getContentResolver().query(kids, cols, null, null, null)) {
            while (c != null && c.moveToNext() && out.size() < 500) {
                String id = c.getString(0), name = c.getString(1), mime = c.getString(2);
                if (android.provider.DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) { if (depth < 2) listDocs(tree, id, depth + 1, out); }
                else if (name != null && Catalog.isRomFile(name))
                    out.add(Catalog.rom(name, android.provider.DocumentsContract.buildDocumentUriUsingTree(tree, id).toString()));
            }
        }
    }

    /** A game file: Android asks which installed emulator opens it (or opens the only one). */
    private void openRom(Catalog.Item it) {
        Intent v = new Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(it.uri), "*/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try { start(Intent.createChooser(v, "Open " + it.label + " with")); }
        catch (ActivityNotFoundException e) { Toast.makeText(this, "Install an emulator first (Retro row)", Toast.LENGTH_LONG).show(); }
    }

    @Override public void onBackPressed() { /* a home screen stays */ }
    @Override protected void onDestroy() { try { unregisterReceiver(packages); } catch (Exception ignored) { } pool.release(); super.onDestroy(); }

    // ---------- Host ----------
    @Override public boolean installed(Catalog.Item it) {
        if (it.isDeviceApp() || it.isRom()) return true;
        String key = it.label;
        Boolean c = installedCache.get(key);
        if (c != null) return c;
        boolean ok = launchIntent(it) != null;
        installedCache.put(key, ok);
        return ok;
    }

    private Intent launchIntent(Catalog.Item it) {
        PackageManager pm = getPackageManager();
        for (String pkg : it.packages) {
            Intent i = pm.getLaunchIntentForPackage(pkg);
            if (i == null) i = pm.getLeanbackLaunchIntentForPackage(pkg);
            if (i != null) return i;
        }
        return null;
    }

    @Override public void open(Catalog.Item it) {
        try {
            if (prefs.getBoolean("kidsMode", false) && Catalog.kidsBlocked(it)
                    && !(it.isDeviceApp() && prefs.getStringSet("kidsApproved", Collections.<String>emptySet()).contains(it.pkg))) { toast("Not available in Kids mode."); return; }   // 0.10.24; 0.10.27: parent-approved apps open
            if (kidsPinGate(it)) { askPin("Parent PIN", () -> open(it)); return; }   // 0.10.23
            if (it.isAction()) {
                if (mustPin(it.action) && !pinFresh()) { askPin("Parent PIN", () -> action(it.action)); return; }
                action(it.action); return;
            }
            if (locked() && it.isDeviceApp() && LockPin.blocked(it.pkg)) { toast("Locked by a parent."); return; }
            if (Catalog.isPspRom(it)) { forceLandscape(); openInPpsspp(it); return; }   // 0.10.7
            if (Catalog.isDreamcastRom(it)) { forceLandscape(); openInFlycast(it); return; }
            if (it.isRom()) { forceLandscape(); openRom(it); return; }
            if (it.isDeviceApp()) { start(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setClassName(it.pkg, it.cls)); return; }
            if (it == Catalog.PLAY_STORE || !installed(it)) kidsStoreOpened();     // 0.10.6: watch for games installed now
            if (it == Catalog.GEFORCE_NOW && Catalog.gfnInBrowser(handheld())) { openWeb(Catalog.GFN_WEB); return; }
            Intent i = launchIntent(it);
            if (i != null) { if (!forceLandscapeThen(i)) start(i); return; }
            if (it.webUrl != null) { openWeb(it.webUrl); return; }
            if (it == Catalog.PLAY_STORE) { Toast.makeText(this, "Play Store not installed on this device", Toast.LENGTH_LONG).show(); return; }
            store(it.storePackage());
        } catch (Exception e) {
            Toast.makeText(this, "Could not open " + it.label, Toast.LENGTH_SHORT).show();
        }
    }

    private void start(Intent i) {
        if (locked() && !pinFresh() && blockedTarget(i)) { toast("Locked by a parent."); return; }   // 0.10.21 Lock mode
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(i);
    }

    // ---------- 0.10.21 Lock mode (parent PIN). Soft lock inside CodeAque; full kiosk only if CodeAque is device owner. ----------
    private long pinOkUntil;
    private boolean boxsetBootApplied;
    /** 0.10.23: a parent PIN exists (set from Kids mode or Lock mode). */
    private boolean pinSet() { return prefs.getString("lockPin", null) != null; }
    /** 0.10.23 / 0.10.35: leaving Kids mode (Grown-ups Settings / Adult mode, or hold Select) and any Play Store
     *  launch from Kids mode need the parent PIN. A PIN entered earlier must not skip that. */
    private boolean kidsPinGate(Catalog.Item it) {
        if (!prefs.getBoolean("kidsMode", false) || pinFresh()) return false;
        if (it.isAction()) return "kidsoff".equals(it.action) || "adultpin".equals(it.action) || "settingspin".equals(it.action) || "goto:settings".equals(it.action);
        return Catalog.opensStore(it, installed(it));
    }
    private boolean locked() { return prefs.getBoolean("lockMode", false) && prefs.getString("lockPin", null) != null; }
    /** Safe contacts and Downloads always ask for the parent PIN. Everything else uses the lock list only while Lock mode is on. */
    private boolean mustPin(String action) {
        if (action == null) return false;
        if (action.equals("safecontacts") || action.equals("downloads") || action.equals("blockunknown") || action.equals("safeonly")) return true;
        return locked() && LockPin.needsPin(action);
    }
    /** A correct PIN opens grown-up things for 2 minutes. */
    private boolean pinFresh() { return android.os.SystemClock.elapsedRealtime() < pinOkUntil; }

    /** Phone, contacts, messages, Android settings, or another Home app: not opened from CodeAque while locked. */
    private boolean blockedTarget(Intent i) {
        String pkg = i.getComponent() != null ? i.getComponent().getPackageName() : i.getPackage();
        if (pkg == null) {
            String a = i.getAction();
            if (a != null && (a.startsWith("android.settings.") || a.equals(Intent.ACTION_DIAL) || a.equals(Intent.ACTION_CALL) || a.equals(Intent.ACTION_SENDTO))) return true;
            ResolveInfo r = getPackageManager().resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY);
            if (r != null && r.activityInfo != null && !"android".equals(r.activityInfo.packageName)) pkg = r.activityInfo.packageName;
        }
        return pkg != null && (LockPin.blocked(pkg) || otherHome(pkg));
    }
    private boolean otherHome(String pkg) {
        if (pkg.equals(getPackageName())) return false;
        for (ResolveInfo r : getPackageManager().queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0))
            if (r.activityInfo != null && pkg.equals(r.activityInfo.packageName)) return true;
        return false;
    }

    private void askPin(String title, Runnable ok) {
        if (!pinSet()) { createParentPin(ok); return; }   // 0.10.35: no PIN yet, make one, then continue
        long wait = prefs.getLong("lockWaitUntil", 0) - System.currentTimeMillis();
        if (wait > 0) { toast("Too many wrong PINs. Try again in " + ((wait + 999) / 1000) + " s."); return; }
        android.widget.EditText e = pinField();
        android.widget.LinearLayout box = pinBox(pinIsDefault() ? "Enter the parent PIN (default 1234)." : "Enter the parent PIN.", e);
        addForgotPin(box);
        AlertDialog dlg = pinDlg(title, box)
                .setPositiveButton("OK", (d, w) -> submitPin(e.getText().toString(), ok))
                .setNegativeButton("Cancel", null)
                .create();
        bindPinEnter(e, dlg, () -> submitPin(e.getText().toString(), ok));
        showPinDlg(dlg, e);
    }
    /** One compact PIN field. IME Done so Enter submits on a keyboard or handheld. */
    private android.widget.EditText pinField() {
        android.widget.EditText e = new android.widget.EditText(this);
        e.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        e.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(6)});
        e.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);
        e.setSingleLine(true);
        return e;
    }
    /** Short message plus fields, no tall AlertDialog setMessage block. */
    private android.widget.LinearLayout pinBox(String msg, android.view.View... fields) {
        android.widget.LinearLayout box = new android.widget.LinearLayout(this);
        box.setOrientation(android.widget.LinearLayout.VERTICAL);
        int p = Math.max(8, (int) (8 * getResources().getDisplayMetrics().density));
        box.setPadding(p, p / 2, p, 0);
        if (msg != null && !msg.isEmpty()) {
            android.widget.TextView t = new android.widget.TextView(this);
            t.setText(msg);
            t.setMaxLines(2);
            t.setPadding(0, 0, 0, p / 2);
            box.addView(t);
        }
        for (android.view.View v : fields) box.addView(v);
        return box;
    }
    private void addForgotPin(android.widget.LinearLayout box) {
        android.widget.TextView f = new android.widget.TextView(this);
        f.setText("Forgot PIN?");
        f.setPadding(0, (int) (6 * getResources().getDisplayMetrics().density), 0, 0);
        f.setOnClickListener(v -> toast("Clearing CodeAque's data in Android Settings > Apps > CodeAque > Storage resets the PIN to 1234."));
        box.addView(f);
    }
    private AlertDialog.Builder pinDlg(String title, android.view.View body) {
        android.widget.ScrollView sv = new android.widget.ScrollView(this);
        sv.setFillViewport(true);
        sv.addView(body);
        return new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert).setTitle(title).setView(sv);
    }
    /** Keep OK/Cancel on screen on a short landscape handheld; resize for the keyboard. */
    private void showPinDlg(AlertDialog dlg, android.widget.EditText focus) {
        dlg.show();
        if (focus != null) focus.requestFocus();
        if (dlg.getWindow() == null) return;
        android.view.Window w = dlg.getWindow();
        w.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
                | android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
        int width = Math.min(dm.widthPixels * 92 / 100, (int) (420 * dm.density));
        int maxH = Math.max(dm.heightPixels * 80 / 100, 1);
        w.setLayout(width, android.view.WindowManager.LayoutParams.WRAP_CONTENT);
        android.view.View decor = w.getDecorView();
        decor.post(() -> {
            if (dlg.getWindow() != null && decor.getHeight() > maxH) dlg.getWindow().setLayout(width, maxH);
        });
    }
    /** 0.10.36: OK, IME Done/Enter, and controller A / DPAD_CENTER all submit the same way. A wrong PIN stays in Kids mode. */
    private void bindPinEnter(android.widget.EditText e, AlertDialog dlg, Runnable submit) {
        e.setOnEditorActionListener((v, id, ev) -> {
            if (id == android.view.inputmethod.EditorInfo.IME_ACTION_DONE || id == android.view.inputmethod.EditorInfo.IME_ACTION_GO
                    || id == android.view.inputmethod.EditorInfo.IME_ACTION_NEXT
                    || (ev != null && ev.getAction() == android.view.KeyEvent.ACTION_DOWN
                        && pinSubmitKey(ev.getKeyCode()))) {
                clickPinOk(dlg, submit);
                return true;
            }
            return false;
        });
        e.setOnKeyListener((v, code, ev) -> {
            if (ev.getAction() == android.view.KeyEvent.ACTION_UP && pinSubmitKey(code)) {
                clickPinOk(dlg, submit);
                return true;
            }
            return false;
        });
        dlg.setOnKeyListener((d, code, ev) -> {
            if (ev.getAction() == android.view.KeyEvent.ACTION_UP && pinSubmitKey(code)) {
                clickPinOk(dlg, submit);
                return true;
            }
            return false;
        });
    }
    private boolean pinSubmitKey(int code) {
        return code == android.view.KeyEvent.KEYCODE_ENTER || code == android.view.KeyEvent.KEYCODE_NUMPAD_ENTER
                || code == android.view.KeyEvent.KEYCODE_DPAD_CENTER || code == android.view.KeyEvent.KEYCODE_BUTTON_A;
    }
    private void clickPinOk(AlertDialog dlg, Runnable submit) {
        if (!dlg.isShowing()) return;
        android.widget.Button b = dlg.getButton(AlertDialog.BUTTON_POSITIVE);
        if (b != null && b.isEnabled()) b.performClick();
        else if (submit != null) { dlg.dismiss(); submit.run(); }
    }
    private void submitPin(String typed, Runnable ok) {
        if (LockPin.check(typed, prefs.getString("lockPin", null))) {
            prefs.edit().putInt("lockWrong", 0).putLong("lockWaitUntil", 0).apply();
            pinOkUntil = android.os.SystemClock.elapsedRealtime() + 120_000; ok.run();
        } else {
            int n = prefs.getInt("lockWrong", 0) + 1;
            prefs.edit().putInt("lockWrong", n).putLong("lockWaitUntil", System.currentTimeMillis() + LockPin.backoff(n)).apply();
            toast("Wrong PIN.");
        }
    }
    /** 0.10.35: first-time PIN, 4-6 digits typed twice. Cancel or a mismatch leaves Kids mode as it is. */
    private void createParentPin(Runnable ok) {
        android.widget.EditText a = pinField(), b = pinField();
        a.setHint("New parent PIN (4-6 digits)"); b.setHint("Type it again");
        a.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_NEXT);
        android.widget.LinearLayout box = pinBox("Pick a parent PIN (4-6 digits).", a, b);
        AlertDialog dlg = pinDlg("Create a parent PIN", box)
                .setPositiveButton("OK", (d, w) -> {
                    String p = a.getText().toString();
                    if (!LockPin.validPin(p)) { toast("The PIN must be 4 to 6 digits. Still in Kids mode."); return; }
                    if (!p.equals(b.getText().toString())) { toast("The two PINs didn't match. Still in Kids mode."); return; }
                    prefs.edit().putString("lockPin", LockPin.newHash(p)).putBoolean("pinDefault", p.equals(DEFAULT_PIN)).putInt("lockWrong", 0).putLong("lockWaitUntil", 0).apply();
                    pinOkUntil = android.os.SystemClock.elapsedRealtime() + 120_000; ok.run();
                })
                .setNegativeButton("Cancel", null)
                .create();
        a.setOnEditorActionListener((v, id, ev) -> { b.requestFocus(); return true; });
        bindPinEnter(b, dlg, null);
        showPinDlg(dlg, a);
    }

    /** Settings > Lock mode: on = set a new 4-6 digit PIN (typed twice); off = needs the PIN (open() already asked). */
    private void lockToggle() {
        if (locked()) {
            new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert).setTitle("Turn off Lock mode?")
                    .setMessage("Settings and Files will open without the PIN again. Kids mode and the Play Store in Kids mode still need it.")
                    .setPositiveButton("Turn off", (d, w) -> { prefs.edit().putBoolean("lockMode", false).apply(); kioskOff(); rescan(); toast("Lock mode is off."); })
                    .setNeutralButton(deviceOwner() ? "Off + remove full kiosk" : null, deviceOwner() ? (d, w) -> { prefs.edit().putBoolean("lockMode", false).apply(); removeOwner(); rescan(); } : null)
                    .setNegativeButton("Cancel", null).show();
            return;
        }
        // 0.10.23: Lock mode uses the parent PIN (1234 until changed in Settings > Change PIN); asked before turning on
        askPin("Turn on Lock mode", () -> {
            prefs.edit().putBoolean("lockMode", true).apply();
            pinOkUntil = 0; rescan();
            toast("Lock mode is on. That option is now hidden. Tap the CodeAque logo 7 times quickly, then enter the PIN, to turn it off or open Android settings."
                    + (pinIsDefault() ? " Still using 1234 - change it in Settings, Change PIN." : ""));
            // No root and no device-owner needed. If CodeAque is not already the home screen, the parent picks it
            // and taps Always. start() would refuse this while locked, so this parent step opens the screen directly.
            if (!isDefaultHome()) {
                homePromptShown = true;
                makeHome(false);
                toast("Pick CodeAque and tap Always, so it stays the home screen.");
                try { startActivity(new Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
                catch (Exception e) { toast("Home settings are not available on this device."); }
            }
            lockResume();
        });
    }

    // ---------- 0.10.30 Adult mode / Settings: Kids "Grown-ups" row, home-screen icons, long-press shortcuts ----------
    /** Opened from the CodeAque Adult / CodeAque Settings icon or shortcut: ask for the PIN once CodeAque is on screen. */
    private boolean parentEntry(Intent i) {
        if (i == null) return false;
        String t = Catalog.entryTarget(i.getAction(), i.getComponent() != null ? i.getComponent().getClassName() : null);
        if (t == null) return false;
        setIntent(new Intent(Intent.ACTION_MAIN).setClass(this, MainActivity.class));   // handled once, not again after a rotation
        view.post(() -> parentGo("settings".equals(t), true));
        return true;
    }
    /** Parent PIN first (a wrong PIN or Cancel leaves everything as it was), then Adult mode on the first row or on
     *  Settings. Lock Mode is not changed: its tile stays hidden, so the 7-tap logo menu is still the only way out. */
    private void parentGo(boolean toSettings, boolean alwaysAsk) {
        Runnable go = () -> {
            boolean wasKids = prefs.getBoolean("kidsMode", false);
            prefs.edit().putBoolean("kidsMode", false).apply();
            rescan();
            view.focus(toSettings ? "settings" : "", 0);   // "" = the first row
            toast(toSettings ? "Settings" + (wasKids ? ". Kids mode is off; the Kids Mode tile turns it back on." : "")
                    : wasKids ? "Adult mode. The Kids Mode tile turns Kids mode back on." : "Adult mode.");
        };
        if (!alwaysAsk && pinFresh()) go.run();
        else askPin(toSettings ? "Parent PIN: Settings" : "Parent PIN: Adult mode", go);
    }
    /** The two extra home-screen icons are switched off while Lock Mode is on and back on when it is off.
     *  The long-press shortcuts cannot be switched off (manifest shortcuts), but they still need the PIN. */
    private void syncAliases() {
        PackageManager pm = getPackageManager();
        int want = locked() ? PackageManager.COMPONENT_ENABLED_STATE_DISABLED : PackageManager.COMPONENT_ENABLED_STATE_DEFAULT;
        for (String a : Catalog.ALIASES) {
            android.content.ComponentName cn = new android.content.ComponentName(getPackageName(), getPackageName() + a);
            try { if (pm.getComponentEnabledSetting(cn) != want) pm.setComponentEnabledSetting(cn, want, PackageManager.DONT_KILL_APP); }
            catch (Exception ignored) { }
        }
    }

    /** Hidden parent escape (logo tapped 7 times). Wrong PINs use the same short cooldown as every other PIN prompt. */
    @Override public void logoSecret() {
        if (!locked()) return;
        askPin("Parent PIN", () -> dlg().setTitle("Parent")
                .setItems(new String[]{"Turn off Lock Mode", "Open Android Settings"}, (d, w) -> {
                    if (w == 0) {
                        prefs.edit().putBoolean("lockMode", false).apply();
                        kioskOff(); rescan(); toast("Lock mode is off.");
                    } else {
                        // Only after the right PIN. The shade and a safe-mode boot still cannot be blocked here.
                        try { startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
                        catch (Exception e) { toast("Android settings could not be opened on this device."); }
                    }
                }).setNegativeButton("Cancel", null).show());
    }

    // ---------- 0.10.23 parent PIN: 1234 out of the box (stored hashed), Settings > Change PIN ----------
    static final String DEFAULT_PIN = "1234";
    /** Makes sure a PIN exists: the default 1234, hashed like any other. Also after "Clear data". */
    private void ensurePin() {
        if (prefs.getString("lockPin", null) == null)
            prefs.edit().putString("lockPin", LockPin.newHash(DEFAULT_PIN)).putBoolean("pinDefault", true).putInt("lockWrong", 0).apply();
    }
    private boolean pinIsDefault() { return prefs.getBoolean("pinDefault", false); }

    /** Current PIN, then the new PIN twice. */
    private void changePin() {
        long wait = prefs.getLong("lockWaitUntil", 0) - System.currentTimeMillis();
        if (wait > 0) { toast("Too many wrong PINs. Try again in " + ((wait + 999) / 1000) + " s."); return; }
        android.widget.EditText cur = pinField(), a = pinField(), b = pinField();
        cur.setHint("Current PIN"); a.setHint("New PIN (4-6 digits)"); b.setHint("New PIN again");
        cur.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_NEXT);
        a.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_NEXT);
        android.widget.LinearLayout box = pinBox(pinIsDefault() ? "Pick a new PIN (default is 1234)." : "Current PIN, then a new one.", cur, a, b);
        addForgotPin(box);
        AlertDialog dlg = pinDlg("Change PIN", box)
                .setPositiveButton("Change", (d, w) -> {
                    if (!LockPin.check(cur.getText().toString(), prefs.getString("lockPin", null))) {
                        int n = prefs.getInt("lockWrong", 0) + 1;
                        prefs.edit().putInt("lockWrong", n).putLong("lockWaitUntil", System.currentTimeMillis() + LockPin.backoff(n)).apply();
                        toast("Wrong current PIN. Nothing was changed."); return;
                    }
                    String p = a.getText().toString();
                    if (!LockPin.validPin(p)) { toast("The new PIN must be 4 to 6 digits. Nothing was changed."); return; }
                    if (!p.equals(b.getText().toString())) { toast("The two new PINs didn't match. Nothing was changed."); return; }
                    prefs.edit().putString("lockPin", LockPin.newHash(p)).putBoolean("pinDefault", p.equals(DEFAULT_PIN)).putInt("lockWrong", 0).putLong("lockWaitUntil", 0).apply();
                    toast("PIN changed.");
                })
                .setNegativeButton("Cancel", null)
                .create();
        cur.setOnEditorActionListener((v, id, ev) -> { a.requestFocus(); return true; });
        a.setOnEditorActionListener((v, id, ev) -> { b.requestFocus(); return true; });
        bindPinEnter(b, dlg, null);
        showPinDlg(dlg, cur);
    }

    private boolean homePromptShown;
    /** On resume while locked: full kiosk when device owner; otherwise make sure CodeAque is still the Home app. */
    private void lockResume() {
        if (!locked()) return;
        if (deviceOwner()) { kioskOn(); return; }
        if (!isDefaultHome() && !homePromptShown) {
            homePromptShown = true;
            askPin("CodeAque is no longer the Home app", () -> { homePromptShown = false; try { startActivity(new Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); } catch (Exception e) { toast("Home settings are not available here"); } });
        }
    }

    private boolean deviceOwner() {
        try { return ((android.app.admin.DevicePolicyManager) getSystemService(DEVICE_POLICY_SERVICE)).isDeviceOwnerApp(getPackageName()); }
        catch (Exception e) { return false; }
    }
    static final String[] KIOSK_RULES = {android.os.UserManager.DISALLOW_FACTORY_RESET, android.os.UserManager.DISALLOW_SAFE_BOOT,
            android.os.UserManager.DISALLOW_ADD_USER, android.os.UserManager.DISALLOW_OUTGOING_CALLS, android.os.UserManager.DISALLOW_SMS};
    /** Device owner only (set with adb): lock task over CodeAque plus the games it can open, CodeAque pinned as Home. UNTESTED ON A REAL DEVICE. */
    private void kioskOn() { kioskOn(new String[0]); }
    private void kioskOn(String[] extra) {
        try {
            android.app.admin.DevicePolicyManager dpm = (android.app.admin.DevicePolicyManager) getSystemService(DEVICE_POLICY_SERVICE);
            android.content.ComponentName admin = new android.content.ComponentName(this, CodeAqueAdmin.class);
            java.util.Set<String> allow = new java.util.TreeSet<>(); allow.add(getPackageName());
            for (ResolveInfo r : getPackageManager().queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0))
                if (!LockPin.blocked(r.activityInfo.packageName) && !otherHome(r.activityInfo.packageName)) allow.add(r.activityInfo.packageName);
            allow.add("com.android.vending"); allow.add("com.google.android.packageinstaller"); allow.add("com.android.packageinstaller");
            for (String x : extra) if (x != null) allow.add(x);   // 0.10.22: the dialer / SMS app, only for a Safe contacts call or text
            dpm.setLockTaskPackages(admin, allow.toArray(new String[0]));
            if (android.os.Build.VERSION.SDK_INT >= 28)
                dpm.setLockTaskFeatures(admin, android.app.admin.DevicePolicyManager.LOCK_TASK_FEATURE_GLOBAL_ACTIONS | android.app.admin.DevicePolicyManager.LOCK_TASK_FEATURE_SYSTEM_INFO);
            for (String r : KIOSK_RULES) dpm.addUserRestriction(admin, r);
            IntentFilter home = new IntentFilter(Intent.ACTION_MAIN); home.addCategory(Intent.CATEGORY_HOME); home.addCategory(Intent.CATEGORY_DEFAULT);
            dpm.addPersistentPreferredActivity(admin, home, new android.content.ComponentName(this, MainActivity.class));
            startLockTask();
        } catch (Exception e) { toast("Full kiosk could not start: " + e.getMessage()); }
    }
    private void kioskOff() {
        if (!deviceOwner()) return;
        try {
            android.app.admin.DevicePolicyManager dpm = (android.app.admin.DevicePolicyManager) getSystemService(DEVICE_POLICY_SERVICE);
            android.content.ComponentName admin = new android.content.ComponentName(this, CodeAqueAdmin.class);
            for (String r : KIOSK_RULES) dpm.clearUserRestriction(admin, r);
            try { stopLockTask(); } catch (Exception ignored) { }
            dpm.setLockTaskPackages(admin, new String[0]);
        } catch (Exception ignored) { }
    }
    // ---------- 0.10.22 Safe contacts: Call & Text only to numbers a parent added ----------
    static final int REQ_CONTACTS = 61, REQ_CALL = 62, REQ_ROLE = 63;
    private SafeContacts.Contact pendingCall;
    boolean hasPhone() { return getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELEPHONY); }
    private List<SafeContacts.Contact> safeList() { return SafeContacts.parse(prefs.getString("safeContacts", "")); }
    private void saveSafe(List<SafeContacts.Contact> l) { prefs.edit().putString("safeContacts", SafeContacts.write(l)).apply(); }
    private android.app.AlertDialog.Builder dlg() { return new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert); }

    private android.widget.LinearLayout col() {
        android.widget.LinearLayout box = new android.widget.LinearLayout(this);
        box.setOrientation(android.widget.LinearLayout.VERTICAL);
        int pad = (int) (12 * getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad, pad, pad);
        return box;
    }
    private android.widget.Button bigButton(String label) {
        android.widget.Button b = new android.widget.Button(this);
        b.setText(label); b.setAllCaps(false); b.setTextSize(22);
        b.setMinHeight((int) (64 * getResources().getDisplayMetrics().density));
        return b;
    }

    /** Kids mode Phone / Messages: only the safe contacts. A tap calls or texts that one number. No keypad, no other people. */
    private void callText() {
        if (!hasPhone()) { toast("This device can't make calls."); return; }
        List<SafeContacts.Contact> l = safeList();
        if (l.isEmpty()) { toast("No safe contacts yet. A parent can add some in Settings, Safe contacts."); return; }
        android.widget.ScrollView sv = new android.widget.ScrollView(this);
        android.widget.LinearLayout box = col();
        for (SafeContacts.Contact c : l) {
            android.widget.Button b = bigButton(c.name + "\n" + c.number);
            b.setOnClickListener(v -> contactActions(c));
            box.addView(b);
        }
        sv.addView(box);
        dlg().setTitle("Phone / Messages").setView(sv).setNegativeButton("Back", null).show();
    }
    private void contactActions(SafeContacts.Contact c) {
        android.widget.LinearLayout box = col();
        android.widget.Button call = bigButton("Call " + c.name), text = bigButton("Text " + c.name);
        AlertDialog[] self = new AlertDialog[1];
        call.setOnClickListener(v -> { if (self[0] != null) self[0].dismiss(); safeCall(c); });
        text.setOnClickListener(v -> { if (self[0] != null) self[0].dismiss(); safeText(c); });
        box.addView(call); box.addView(text);
        self[0] = dlg().setTitle(c.name).setMessage("Only this number. There is no keypad.").setView(box).setNegativeButton("Back", null).show();
    }

    private String[] phoneApps() {
        String dial = null, sms = null;
        try { dial = ((android.telecom.TelecomManager) getSystemService(TELECOM_SERVICE)).getDefaultDialerPackage(); } catch (Exception ignored) { }
        try { sms = android.provider.Telephony.Sms.getDefaultSmsPackage(this); } catch (Exception ignored) { }
        return new String[]{dial, sms};
    }
    /** Started directly (not through start(), which blocks phone apps while locked); in full kiosk the dialer and SMS
     *  app are allowed just for this, and onResume puts the normal kiosk list back. */
    private void safeLaunch(Intent i) {
        if (!SafeContacts.allowed(i.getData() == null ? null : i.getData().getSchemeSpecificPart(), safeList())) { toast("That number is not a safe contact."); return; }
        if (locked() && deviceOwner()) kioskOn(phoneApps());
        try { startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); } catch (Exception e) { toast("No app here can do that."); }
    }
    private void safeCall(SafeContacts.Contact c) {
        if (checkSelfPermission(android.Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
            safeLaunch(new Intent(Intent.ACTION_CALL, Uri.parse("tel:" + c.number))); return;
        }
        dlg().setTitle("Allow calls?")
                .setMessage("CodeAque asks to call " + c.name + " itself, so the number cannot be changed. If you say no, the phone app opens with this number filled in, and that app may show its own keypad.")
                .setPositiveButton("Continue", (d, w) -> { pendingCall = c; requestPermissions(new String[]{android.Manifest.permission.CALL_PHONE}, REQ_CALL); })
                .setNegativeButton("Back", null).show();
    }
    private void safeText(SafeContacts.Contact c) { safeLaunch(new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + c.number))); }

    /** Parent screen, always behind the PIN: add, edit and remove. Stored only on this phone. */
    private void safeContacts() {
        List<SafeContacts.Contact> l = safeList();
        android.widget.ScrollView sv = new android.widget.ScrollView(this);
        android.widget.LinearLayout box = col();
        if (l.isEmpty()) {
            android.widget.TextView t = new android.widget.TextView(this);
            t.setText("No safe contacts yet. Add a name and a number. They stay on this phone.");
            t.setTextSize(18); box.addView(t);
        }
        for (int i = 0; i < l.size(); i++) {
            int index = i;
            android.widget.Button b = bigButton(l.get(i).name + "\n" + l.get(i).number);
            b.setOnClickListener(v -> contactMenu(index));
            box.addView(b);
        }
        sv.addView(box);
        dlg().setTitle("Safe contacts").setView(sv)
                .setPositiveButton("Add", (d, w) -> addSafe())
                .setNeutralButton("Import from phone", (d, w) -> importSafe())
                .setNegativeButton("Back", null).show();
    }
    private void contactMenu(int index) {
        List<SafeContacts.Contact> l = safeList();
        if (index < 0 || index >= l.size()) { safeContacts(); return; }
        SafeContacts.Contact c = l.get(index);
        dlg().setTitle(c.name)
                .setPositiveButton("Edit", (d, w) -> editSafe(index))
                .setNeutralButton("Remove", (d, w) -> {
                    List<SafeContacts.Contact> cur = safeList();
                    if (index >= 0 && index < cur.size()) { cur.remove(index); saveSafe(cur); toast("Removed."); }
                    safeContacts();
                })
                .setNegativeButton("Back", (d, w) -> safeContacts()).show();
    }
    private void editSafe(int index) {
        List<SafeContacts.Contact> l = safeList();
        if (index < 0 || index >= l.size()) { safeContacts(); return; }
        SafeContacts.Contact c = l.get(index);
        android.widget.LinearLayout box = col();
        android.widget.EditText n = new android.widget.EditText(this), num = new android.widget.EditText(this);
        n.setHint("Name"); num.setHint("Phone number"); num.setInputType(android.text.InputType.TYPE_CLASS_PHONE);
        n.setText(c.name); num.setText(c.number);
        box.addView(n); box.addView(num);
        dlg().setTitle("Edit " + c.name).setView(box)
                .setPositiveButton("Save", (d, w) -> {
                    List<SafeContacts.Contact> cur = safeList();
                    if (SafeContacts.update(cur, index, n.getText().toString(), num.getText().toString())) { saveSafe(cur); toast("Saved."); }
                    else toast("That number doesn't look right, or it is already in the list. Nothing was changed.");
                    safeContacts();
                }).setNegativeButton("Back", (d, w) -> safeContacts()).show();
    }
    private void addSafe() {
        android.widget.LinearLayout box = new android.widget.LinearLayout(this); box.setOrientation(android.widget.LinearLayout.VERTICAL);
        android.widget.EditText n = new android.widget.EditText(this), num = new android.widget.EditText(this);
        n.setHint("Name (e.g. Mum)"); num.setHint("Phone number"); num.setInputType(android.text.InputType.TYPE_CLASS_PHONE);
        box.addView(n); box.addView(num);
        dlg().setTitle("Add a safe contact").setView(box)
                .setPositiveButton("Save", (d, w) -> {
                    List<SafeContacts.Contact> l = safeList();
                    if (SafeContacts.add(l, n.getText().toString(), num.getText().toString())) { saveSafe(l); toast("Saved."); }
                    else toast("That number doesn't look right. Nothing was saved.");
                    safeContacts();
                }).setNegativeButton("Cancel", (d, w) -> safeContacts()).show();
    }
    /** READ_CONTACTS is asked only here, when the parent taps Import. */
    private void importSafe() {
        if (checkSelfPermission(android.Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.READ_CONTACTS}, REQ_CONTACTS); return;
        }
        final List<SafeContacts.Contact> found = new ArrayList<>();
        try (android.database.Cursor cu = getContentResolver().query(android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                new String[]{android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER},
                null, null, android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC")) {
            while (cu != null && cu.moveToNext()) SafeContacts.add(found, cu.getString(0), cu.getString(1));
        } catch (Exception e) { toast("Could not read the phone's contacts."); return; }
        if (found.isEmpty()) { toast("No contacts with phone numbers were found."); safeContacts(); return; }
        String[] rows = new String[found.size()]; boolean[] pick = new boolean[found.size()];
        for (int i = 0; i < rows.length; i++) rows[i] = found.get(i).name + "   " + found.get(i).number;
        dlg().setTitle("Choose safe contacts").setMultiChoiceItems(rows, pick, (d, w, on) -> pick[w] = on)
                .setPositiveButton("Add chosen", (d, w) -> {
                    List<SafeContacts.Contact> l = safeList(); int n = 0;
                    for (int i = 0; i < pick.length; i++) if (pick[i] && SafeContacts.add(l, found.get(i).name, found.get(i).number)) n++;
                    saveSafe(l); toast(n + " added."); safeContacts();
                }).setNegativeButton("Cancel", (d, w) -> safeContacts()).show();
    }

    /** Block unknown callers (call screening role) / Only call safe numbers (call redirection role). Android 10+. UNTESTED ON A REAL DEVICE. */
    private String roleKey;
    private void callRoleToggle(String key, String label, boolean screening) {
        if (!hasPhone()) { toast("This device can't make calls."); return; }
        if (prefs.getBoolean(key, false)) { prefs.edit().putBoolean(key, false).apply(); toast(label + ": off."); return; }
        if (android.os.Build.VERSION.SDK_INT < 29) { toast(label + " needs Android 10 or newer."); return; }
        try {
            android.app.role.RoleManager rm = getSystemService(android.app.role.RoleManager.class);
            String role = screening ? android.app.role.RoleManager.ROLE_CALL_SCREENING : android.app.role.RoleManager.ROLE_CALL_REDIRECTION;
            if (rm == null || !rm.isRoleAvailable(role)) { toast(label + " isn't available on this device."); return; }
            if (rm.isRoleHeld(role)) { prefs.edit().putBoolean(key, true).apply(); toast(label + ": on."); return; }
            roleKey = key;
            startActivityForResult(rm.createRequestRoleIntent(role), REQ_ROLE);
        } catch (Exception e) { toast(label + " isn't available on this device."); }
    }

    @Override public void onRequestPermissionsResult(int req, String[] perms, int[] res) {
        super.onRequestPermissionsResult(req, perms, res);
        boolean ok = res.length > 0 && res[0] == PackageManager.PERMISSION_GRANTED;
        if (req == REQ_CONTACTS) { if (ok) importSafe(); else { toast("Contacts were not shared, so nothing was imported."); safeContacts(); } }
        else if (req == REQ_CALL && pendingCall != null) {
            SafeContacts.Contact c = pendingCall; pendingCall = null;
            if (ok) safeLaunch(new Intent(Intent.ACTION_CALL, Uri.parse("tel:" + c.number)));
            else {
                // Permission denied: Android's own phone app, number filled in. It may show a keypad. That is an Android limit.
                toast("Calls were not allowed, so the phone app opens with this number. It may show its own keypad.");
                safeLaunch(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + c.number)));
            }
        }
    }

    /** Settings > Remove full kiosk: CodeAque gives up device owner (PIN asked by open()). */
    private void removeOwner() {
        if (!deviceOwner()) { toast("CodeAque is not the device owner, so there is nothing to remove."); return; }
        kioskOff();
        try { ((android.app.admin.DevicePolicyManager) getSystemService(DEVICE_POLICY_SERVICE)).clearDeviceOwnerApp(getPackageName()); toast("Full kiosk removed."); }
        catch (Exception e) { toast("Could not remove it: " + e.getMessage()); }
    }

    /** Adult-mode Downloads. LibreTorrent if it is installed, otherwise its Play Store or F-Droid page.
     *  Never a ROM, piracy or IPTV link. The tile is not in Kids mode; this refuses as well. */
    private void openDownloads() {
        if (prefs.getBoolean("kidsMode", false)) { toast("Not available in Kids mode."); return; }
        Intent launch = null;
        try { launch = getPackageManager().getLaunchIntentForPackage(Catalog.LIBRETORRENT); } catch (Exception ignored) { }
        if (launch != null) { start(launch); return; }
        dlg().setTitle("Downloads")
                .setMessage("LibreTorrent is not installed. Open its store page? CodeAque does not add any other download sites.")
                .setPositiveButton("Play Store", (d, w) -> openStorePage(true))
                .setNeutralButton("F-Droid", (d, w) -> openStorePage(false))
                .setNegativeButton("Back", null).show();
    }
    private void openStorePage(boolean play) {
        String pkg = Catalog.LIBRETORRENT;
        if (play) {
            try { start(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + pkg))); }
            catch (ActivityNotFoundException e) {
                start(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + pkg)));
            }
        } else {
            start(new Intent(Intent.ACTION_VIEW, Uri.parse("https://f-droid.org/packages/" + pkg + "/")));
        }
    }

    private void store(String pkg) {
        if (prefs.getBoolean("kidsMode", false) && pinSet() && !pinFresh()) { askPin("Parent PIN", () -> store(pkg)); return; }   // 0.10.23
        try { start(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + pkg))); }
        catch (ActivityNotFoundException e) { start(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + pkg))); }
    }

    private void action(String a) {
        if (a != null && a.startsWith("steamstore:")) { openSteamStore(a.substring(11)); return; }
        if (a != null && a.startsWith("roblox:")) { openRobloxPlace(a.substring(7)); return; }
        switch (a) {
            case "wifi": openWifi(); break;
            case "scandetails": if (!prefs.getBoolean("kidsMode", false)) showScanDetails(); break;
            case "rescan": if (!prefs.getBoolean("kidsMode", false)) rescanGames(); break;
            case "filemgr": if (!prefs.getBoolean("kidsMode", false)) openFiles(); break;
            case "kidsgames": kidsGamesManage(); break;
            case "files": askFileAccess(); break;
            case "bluetooth": start(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)); break;
            case "display": start(new Intent(Settings.ACTION_DISPLAY_SETTINGS)); break;
            case "settings": start(new Intent(Settings.ACTION_SETTINGS)); break;
            case "home": if (isDefaultHome()) toast("CodeAque is already your Home app."); else start(new Intent(Settings.ACTION_HOME_SETTINGS)); break;
            case "browser": start(new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com"))); break;
            case "effects": {
                String n = Catalog.nextEffects(effects());
                prefs.edit().putString("effects", n).apply();
                Toast.makeText(this, "Effects: " + n + (n.equals("auto") ? " (30 fps waves)" : n.equals("off") ? " (still background, saves battery)" : " (60 fps)"), Toast.LENGTH_SHORT).show();
                view.invalidate(); break;
            }
            case "allapps": allApps(); break;
            case "romfolder": pickRomFolder(); break;
            case "accounts": start(new Intent(Settings.ACTION_SYNC_SETTINGS)); break;
            case "projector": {
                boolean on = !prefs.getBoolean("projector", false);
                prefs.edit().putBoolean("projector", on).apply();
                Toast.makeText(this, "Projector mode " + (on ? "on: bigger tiles, safe margins, Cloud and Media first" : "off"), Toast.LENGTH_SHORT).show();
                rescan(); break;
            }
            case "update": checkUpdate(true); break;
            case "about": toast("CodeAque version " + myVersion() + (Catalog.updateAvailable ? " - an update is available" : "")); break;   // 0.10.28
            case "makehome": makeHome(true); break;              // 0.10.28
            case "hidelaunchers": hideLaunchers(); break;        // 0.10.28 (PIN in Lock mode; always asks the PIN below)
            case "bootanim": bootAnim(); break;
            case "rotation": rotationToggle(); break;
            case "kids": kidsMode(true); break;
            case "gfn:rivals": openRivals(); break;
            case "kidsoff": kidsMode(false); break;
            case "adultpin": parentGo(false, true); break;      // 0.10.35: always ask, even if a PIN was entered a minute ago
            case "settingspin": parentGo(true, true); break;    // 0.10.35
            case "sortapps": sortApps(); break;            // 0.10.27
            case "kidson":                                   // 0.10.26: one tap, no PIN, no question
                leaveBoxSet();
                pinOkUntil = 0;                               // 0.10.35: a PIN from before Kids mode must not unlock Settings
                prefs.edit().putBoolean("kidsMode", true).apply(); rescan(); toast("Kids mode is on. Leaving it needs the parent PIN."); break;
            case "boxset": enterBoxSet(); break;             // 0.10.32: no PIN to leave
            case "boxsetboot": toggleBoxSetBoot(); break;
            case "lock": lockToggle(); break;              // 0.10.21
            case "downloads": openDownloads(); break;      // 0.10.29 adult only, PIN already asked
            case "changepin": changePin(); break;          // 0.10.23
            case "calltext": callText(); break;            // 0.10.22 Safe contacts
            case "safecontacts": safeContacts(); break;
            case "blockunknown": callRoleToggle("blockUnknown", "Block unknown callers", true); break;
            case "safeonly": callRoleToggle("safeOnly", "Only call safe numbers", false); break;
            case "removeowner": removeOwner(); break;
            case "theme": themePicker(); break;            // 0.10.27: big preview picker (normal Settings)
            case "kthemes": themePicker(); break;          // 0.10.27: Kids Themes tile, no PIN, themes only
            case "wallpaper":
                if (prefs.getString("wallpaper", null) != null) {
                    prefs.edit().remove("wallpaper").apply(); view.setWallpaper(null);
                    Toast.makeText(this, "Wallpaper off: theme background", Toast.LENGTH_SHORT).show();
                } else {
                    Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*");
                    startActivityForResult(pick, PICK_WALLPAPER);
                }
                break;
        }
    }

    private void allApps() {
        if (prefs.getBoolean("kidsMode", false)) { toast("Not available in Kids mode."); return; }   // 0.10.24                       // text-only list: no app logos on our screen
        PackageManager pm = getPackageManager();
        Intent q = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> ri = pm.queryIntentActivities(q, 0);
        final List<String[]> apps = new ArrayList<>();
        for (ResolveInfo r : ri) if (!r.activityInfo.packageName.equals(getPackageName()) && !(locked() && (LockPin.blocked(r.activityInfo.packageName) || otherHome(r.activityInfo.packageName))))
            apps.add(new String[]{r.loadLabel(pm).toString(), r.activityInfo.packageName, r.activityInfo.name});
        Collections.sort(apps, (x, y) -> x[0].compareToIgnoreCase(y[0]));
        String[] names = new String[apps.size()];
        for (int i = 0; i < names.length; i++) names[i] = apps.get(i)[0];
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert).setTitle("All apps")
                .setItems(names, (d, w) -> start(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                        .setClassName(apps.get(w)[1], apps.get(w)[2]))).show();
    }

    // ---------- APK self-update: latest GitHub release -> SHA-256 check -> Android's own install prompt ----------
    private volatile boolean updating;

    private String myVersion() {
        try { return getPackageManager().getPackageInfo(getPackageName(), 0).versionName; } catch (Exception e) { return "0"; }
    }

    private static String get(String url) throws java.io.IOException { return get(url, "application/vnd.github+json"); }
    private static String get(String url, String accept) throws java.io.IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        fetch(url, accept, out, null);
        return out.toString("UTF-8");
    }

    /** 0.10.20: robust download. Up to 4 tries with backoff (1, 2, 4 s), retrying network errors, 5xx and 429; follows
     *  redirects by hand across hosts (HTTPS only, at most 6 hops); User-Agent; long timeouts. Errors name the real cause. */
    private static void fetch(String url, String accept, java.io.OutputStream sink, java.security.MessageDigest md) throws java.io.IOException {
        java.io.IOException last = null;
        for (int attempt = 0; attempt < 4; attempt++) {
            if (attempt > 0) try { Thread.sleep(1000L << (attempt - 1)); } catch (InterruptedException ie) { throw new java.io.IOException("interrupted"); }
            java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
            try {
                String u = url;
                for (int hop = 0; ; hop++) {
                    if (hop > 6) throw new java.io.IOException("too many redirects at " + u);
                    java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(u).openConnection();
                    c.setInstanceFollowRedirects(false);
                    c.setConnectTimeout(20000); c.setReadTimeout(60000);
                    c.setRequestProperty("User-Agent", "CodeAque-Updater (Android " + android.os.Build.VERSION.RELEASE + ")");
                    if (accept != null && u.startsWith("https://api.github.com/")) c.setRequestProperty("Accept", accept);
                    try {
                        int code = c.getResponseCode();
                        if (code >= 300 && code < 400) {
                            String loc = c.getHeaderField("Location");
                            if (loc == null) throw new java.io.IOException("HTTP " + code + " without a Location from " + new java.net.URL(u).getHost());
                            java.net.URL next = new java.net.URL(new java.net.URL(u), loc);
                            if (!"https".equals(next.getProtocol())) throw new java.io.IOException("refused non-HTTPS redirect to " + next.getHost());
                            u = next.toString(); continue;
                        }
                        if (code != 200) {
                            java.io.IOException he = new java.io.IOException("HTTP " + code + " from " + new java.net.URL(u).getHost());
                            if (code >= 500 || code == 429 || code == 408) { last = he; break; }   // worth retrying
                            throw new FatalHttp(he.getMessage());
                        }
                        try (java.io.InputStream in = c.getInputStream()) {
                            byte[] b = new byte[65536]; int n; long total = 0;
                            while ((n = in.read(b)) > 0) { buf.write(b, 0, n); total += n; if (total > 100L << 20) throw new FatalHttp("download too big"); }
                        }
                        byte[] all = buf.toByteArray();
                        if (md != null) md.update(all);
                        sink.write(all);
                        return;
                    } finally { c.disconnect(); }
                }
            } catch (FatalHttp f) {
                throw f;
            } catch (java.io.IOException e) {
                last = new java.io.IOException(e.getClass().getSimpleName() + ": " + e.getMessage(), e);
            }
        }
        throw last != null ? last : new java.io.IOException("download failed");
    }
    static final class FatalHttp extends java.io.IOException { FatalHttp(String m) { super(m); } }

    private void toast(String t) { runOnUiThread(() -> Toast.makeText(this, t, Toast.LENGTH_LONG).show()); }

    /** manual = Settings > Update (asks and installs); otherwise the once-a-day quiet check that only tells you. */
    private void checkUpdate(boolean manual) {
        if (updating) return;
        updating = true;
        if (manual) toast("Checking for updates ...");
        new Thread(() -> {
            try {
                UpdateInfo u = UpdateInfo.parse(get(UpdateInfo.API));
                String mine = myVersion();
                if (u == null || !UpdateInfo.newer(u.version, mine)) { Catalog.updateAvailable = false; if (manual) toast("You're up to date! (CodeAque " + mine + ")"); return; }
                prefs.edit().putString("updKnown", u.version).apply();
                Catalog.updateAvailable = true; runOnUiThread(() -> view.invalidate());   // 0.10.28 badge
                if (!manual) { runOnUiThread(() -> updatePopup(u, mine)); return; }
                runOnUiThread(() -> new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                        .setTitle("Update to CodeAque " + u.version + "?")
                        .setMessage("You have " + mine + ". The new version is downloaded, checked against the release's SHA256SUMS.txt and then Android asks you to install it.")
                        .setPositiveButton("Update", (d, w) -> new Thread(() -> install(u)).start())
                        .setNegativeButton("Not now", null).show());
            } catch (Exception e) {
                if (manual) toast("Could not check for updates. Is the Wi-Fi on?");
                else nextUpdateCheck = android.os.SystemClock.elapsedRealtime() + UpdateInfo.RETRY_MS;   // 0.10.25: quiet retry soon
            } finally { updating = false; }
        }).start();
    }

    private void install(UpdateInfo u) {
        try {
            toast("Downloading CodeAque " + u.version + " ...");
            String want = null;
            try { want = UpdateInfo.expectedSha(get(u.sumsUrl, null)); }
            catch (java.io.IOException sumsErr) { if (u.apkDigest == null) throw new java.io.IOException("checksum file: " + sumsErr.getMessage(), sumsErr); }
            if (want == null) want = u.apkDigest;   // 0.10.20 fallback: GitHub's own digest for the APK asset
            if (want == null) { toast("The release has no checksum for the APK, so it was not installed."); return; }
            if (u.apkDigest != null && !u.apkDigest.equals(want)) { toast("The release's checksums disagree, so it was not installed."); return; }
            java.io.File f = new java.io.File(getCacheDir(), "update.apk");
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            try (java.io.FileOutputStream out = new java.io.FileOutputStream(f)) { fetch(u.apkUrl, null, out, md); }
            catch (java.io.IOException apkErr) {
                if (u.apkApiUrl == null) throw new java.io.IOException("APK: " + apkErr.getMessage(), apkErr);
                md.reset();   // 0.10.20 fallback: the same file through the GitHub API
                try (java.io.FileOutputStream out = new java.io.FileOutputStream(f)) { fetch(u.apkApiUrl, "application/octet-stream", out, md); }
                catch (java.io.IOException e2) { throw new java.io.IOException("APK: " + e2.getMessage(), e2); }
            }
            StringBuilder hex = new StringBuilder(); for (byte x : md.digest()) hex.append(String.format("%02x", x));
            if (!hex.toString().equals(want)) { f.delete(); toast("The download did not match its checksum, so it was not installed."); return; }
            android.content.pm.PackageInstaller pi = getPackageManager().getPackageInstaller();
            android.content.pm.PackageInstaller.SessionParams sp = new android.content.pm.PackageInstaller.SessionParams(
                    android.content.pm.PackageInstaller.SessionParams.MODE_FULL_INSTALL);
            int id = pi.createSession(sp);
            try (android.content.pm.PackageInstaller.Session s = pi.openSession(id)) {
                try (java.io.InputStream in = new java.io.FileInputStream(f); java.io.OutputStream out = s.openWrite("codeaque", 0, f.length())) {
                    byte[] b = new byte[65536]; int n; while ((n = in.read(b)) > 0) out.write(b, 0, n); s.fsync(out);
                }
                Intent done = new Intent(this, MainActivity.class).setAction("com.codeaque.launcher.INSTALLED");
                int fl = android.os.Build.VERSION.SDK_INT >= 31 ? 0x02000000 /* FLAG_MUTABLE */ : 0;
                android.app.PendingIntent pend = android.app.PendingIntent.getActivity(this, 1, done, android.app.PendingIntent.FLAG_UPDATE_CURRENT | fl);
                s.commit(pend.getIntentSender());
            }
            f.delete();
        } catch (Exception e) {
            toast("The update could not be installed (" + e.getClass().getSimpleName() + "): " + e.getMessage() + ". Please try again in a few minutes.");
        }
    }

    // ---------- 0.10.1 Home app: never re-prompt once CodeAque is the default ----------
    static final int REQ_HOME_ROLE = 41;

    boolean isDefaultHome() {
        try {
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                android.app.role.RoleManager rm = getSystemService(android.app.role.RoleManager.class);
                if (rm != null && rm.isRoleAvailable(android.app.role.RoleManager.ROLE_HOME)) return rm.isRoleHeld(android.app.role.RoleManager.ROLE_HOME);
            }
            ResolveInfo r = getPackageManager().resolveActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY);
            return r != null && r.activityInfo != null && getPackageName().equals(r.activityInfo.packageName);
        } catch (Exception e) { return true; }   // unsure: never nag
    }

    /** Asks at most once, ever, and only on API 29+ through RoleManager; never fires a HOME intent or chooser itself. */
    private void maybeAskHomeOnce() {
        if (prefs.getBoolean("homeAsked", false) || android.os.Build.VERSION.SDK_INT < 29) return;
        prefs.edit().putBoolean("homeAsked", true).apply();
        if (isDefaultHome()) return;
        try {
            android.app.role.RoleManager rm = getSystemService(android.app.role.RoleManager.class);
            if (rm != null && rm.isRoleAvailable(android.app.role.RoleManager.ROLE_HOME))
                startActivityForResult(rm.createRequestRoleIntent(android.app.role.RoleManager.ROLE_HOME), REQ_HOME_ROLE);
        } catch (Exception ignored) { }
    }

    // ---------- 0.10.2 GeForce NOW web client ----------
    private boolean handheld() { return Rotation.suggest(android.os.Build.MANUFACTURER, android.os.Build.MODEL, android.os.Build.HARDWARE, android.os.Build.BOARD); }

    /** Chrome when installed (GeForce NOW's web client supports it), otherwise the default browser. */

    private File storeArtDir() { return new File(getCacheDir(), "storeart"); }

    private android.graphics.Bitmap storeArt(Catalog.Item it) {
        String asset = Catalog.modeAsset(it);
        String appid = Catalog.steamAppId(it);
        String place = Catalog.robloxPlaceId(it);
        File dir = storeArtDir();
        if (appid != null) {
            File f = StoreArt.steamFile(dir, appid);
            if (f.isFile()) return decodeLater(coverCache, it.key(), () -> android.graphics.BitmapFactory.decodeFile(f.getPath()));
            if (online() && fetching.add("steam:" + appid))
                new Thread(() -> { boolean ok = StoreArt.fetchSteam(appid, dir); fetching.remove("steam:" + appid); if (ok) runOnUiThread(() -> { coverCache.remove(it.key()); view.invalidate(); }); }).start();
            return modeBitmap(asset);
        }
        if (place != null) {
            File f = StoreArt.robloxFile(dir, place);
            if (f.isFile()) return decodeLater(coverCache, it.key(), () -> android.graphics.BitmapFactory.decodeFile(f.getPath()));
            if (online() && fetching.add("roblox:" + place))
                new Thread(() -> { boolean ok = StoreArt.fetchRoblox(place, dir); fetching.remove("roblox:" + place); if (ok) runOnUiThread(() -> { coverCache.remove(it.key()); view.invalidate(); }); }).start();
            return modeBitmap(asset);
        }
        return modeBitmap(asset);
    }

    private android.graphics.Bitmap modeBitmap(String asset) {
        if (asset == null) return null;
        if (coverCache.containsKey("asset:" + asset)) return coverCache.get("asset:" + asset);
        return decodeLater(coverCache, "asset:" + asset, () -> {
            try (java.io.InputStream in = getAssets().open(asset)) {
                return android.graphics.BitmapFactory.decodeStream(in);
            } catch (Exception e) { return null; }
        });
    }

    private void openSteamStore(String appid) {
        if (appid == null || !appid.matches("\\d+")) return;
        try {
            start(new Intent(Intent.ACTION_VIEW, Uri.parse("steam://store/" + appid)));
        } catch (Exception e) {
            openWeb("https://store.steampowered.com/app/" + appid + "/");
        }
    }

    private void openRobloxPlace(String placeId) {
        if (placeId == null || !placeId.matches("\\d+")) return;
        try {
            start(new Intent(Intent.ACTION_VIEW, Uri.parse("roblox://placeId=" + placeId)).setPackage("com.roblox.client"));
        } catch (Exception e) {
            openWeb("https://www.roblox.com/games/" + placeId + "/");
        }
    }

    private void enterBoxSet() {
        if (prefs.getBoolean("kidsMode", false)) { toast("Box Set Mode is not available in Kids mode."); return; }
        view.setBoxSet(true, java.util.Arrays.asList(Catalog.boxSetItems()));
        toast("Box Set Mode. Press Exit or hold Start / Select to leave (no PIN).");
    }

        @Override public void robloxRow(boolean on, boolean nearEnd) {
        if (prefs.getBoolean("kidsMode", false)) {   // official short list only (explore includes mild/moderate titles)
            robloxOn = false;
            return;
        }
        if (!on) { robloxOn = false; return; }
        if (!robloxOn) {
            robloxOn = true;
            refreshRoblox();
        } else if (nearEnd) {
            loadMoreRoblox();
        }
    }

    private File robloxCacheFile() { return new File(getFilesDir(), RobloxDiscover.CACHE_NAME); }

    private void showRoblox(List<Catalog.Item> items) {
        if (items == null || items.isEmpty()) return;
        robloxItems = items;
        if (!prefs.getBoolean("kidsMode", false)) view.replaceRow("roblox", items);
    }

    private void refreshRoblox() {
        if (robloxLoading) return;
        if (robloxItems == null || robloxItems.isEmpty()) {
            List<Catalog.Item> cached = RobloxDiscover.loadCache(robloxCacheFile());
            if (!cached.isEmpty()) showRoblox(cached);
        }
        // 0.10.35: always try the public explore API. online() can be stale or wrong on some phones,
        // and skipping the fetch was leaving Roblox Mode on the official 12-game list.
        robloxSession = RobloxDiscover.newSession();
        robloxToken = "";
        robloxLoading = true;
        final String session = robloxSession;
        new Thread(() -> {
            RobloxDiscover.Page page;
            try { page = RobloxDiscover.fetchPage(session, ""); }
            catch (Exception e) { page = new RobloxDiscover.Page(new java.util.ArrayList<>(), ""); }
            final RobloxDiscover.Page got = page;
            runOnUiThread(() -> {
                robloxLoading = false;
                if (!got.items.isEmpty()) {
                    robloxToken = got.nextToken;
                    showRoblox(got.items);
                    RobloxDiscover.saveCache(robloxCacheFile(), got.items);
                } else if (robloxItems == null || robloxItems.isEmpty()) {
                    List<Catalog.Item> cached = RobloxDiscover.loadCache(robloxCacheFile());
                    showRoblox(cached.isEmpty() ? RobloxDiscover.fallback() : cached);
                }
            });
        }, "codeaque-roblox").start();
    }

    private void loadMoreRoblox() {
        if (robloxLoading || robloxToken == null || robloxToken.isEmpty()) return;
        long now = android.os.SystemClock.uptimeMillis();
        if (now - robloxMoreAt < 2500) return;   // onDraw can be near the end every frame
        robloxMoreAt = now;
        robloxLoading = true;
        final String session = robloxSession, token = robloxToken;
        new Thread(() -> {
            RobloxDiscover.Page page;
            try { page = RobloxDiscover.fetchPage(session, token); }
            catch (Exception e) { page = new RobloxDiscover.Page(new java.util.ArrayList<>(), ""); }
            final RobloxDiscover.Page got = page;
            runOnUiThread(() -> {
                robloxLoading = false;
                if (!got.items.isEmpty()) {
                    robloxToken = got.nextToken;
                    List<Catalog.Item> merged = RobloxDiscover.merge(robloxItems, got.items);
                    showRoblox(merged);
                    RobloxDiscover.saveCache(robloxCacheFile(), merged);
                } else if (!got.nextToken.isEmpty()) {
                    robloxToken = got.nextToken;
                }
            });
        }, "codeaque-roblox-more").start();
    }

    @Override public void leaveBoxSet() {
        view.setBoxSet(false, java.util.Collections.emptyList());
        toast("Back to CodeAque.");
    }

    private void toggleBoxSetBoot() {
        boolean on = !prefs.getBoolean("boxsetBoot", false);
        prefs.edit().putBoolean("boxsetBoot", on).apply();
        toast(on
                ? "Lock on: after a reboot, CodeAque opens straight in Box Set Mode. Leaving it does not turn this off."
                : "Lock off: CodeAque will not start in Box Set Mode after a reboot.");
    }

    private void openWeb(String url) {
        Intent v = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        try { getPackageManager().getPackageInfo("com.android.chrome", 0); start(new Intent(v).setPackage("com.android.chrome")); return; }
        catch (Exception ignored) { }
        start(v);
    }

    // ---------- 0.10.5 Dreamcast: the player's own files on internal storage / SD ----------
    private volatile List<Catalog.Item> dcFiles = new ArrayList<>();
    private volatile boolean dcScanning, rescanAsked;
    private final Map<String, String> scanNotes = new HashMap<>();

    /** 0.10.9: grown-up note under an empty Dreamcast / PSP section (never in Kids mode). */
    @Override public String scanNote(String catId) {
        if (prefs.getBoolean("kidsMode", false)) return null;
        if (!canReadFiles()) return "CodeAque can't read your files yet - Settings > Find Dreamcast games to allow All files access";
        return scanNotes.get(catId);
    }
    private static String shortList(List<String> dirs) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < dirs.size() && i < 4; i++) b.append(i > 0 ? ", " : "").append(dirs.get(i));
        if (dirs.size() > 4) b.append(" +").append(dirs.size() - 4).append(" more");
        return b.toString();
    }
    private volatile String scanDetails = "Not scanned yet - press Rescan.";
    private String BuildConfigVersion() { try { return getPackageManager().getPackageInfo(getPackageName(), 0).versionName; } catch (Exception e) { return "?"; } }
    /** 0.10.11 grown-up readout: permission state and every folder tried (exists / canRead / how many entries listFiles saw). */
    private void showScanDetails() {
        if (prefs.getBoolean("kidsMode", false)) return;
        android.widget.TextView t = new android.widget.TextView(this);
        t.setText(scanDetails); t.setTextIsSelectable(true); t.setTextSize(12); t.setPadding(24, 16, 24, 16);
        android.widget.ScrollView sv = new android.widget.ScrollView(this); sv.addView(t);
        new android.app.AlertDialog.Builder(this).setTitle("Scan details").setView(sv)
                .setPositiveButton("Rescan", (dl, w) -> rescanGames()).setNegativeButton("Close", null).show();
    }
    private void rescanGames() {
        if (!canReadFiles()) { askFileAccess(); return; }
        if (dcScanning) { toast("Already looking for games..."); return; }
        rescanAsked = true; toast("Looking for games..."); scanDreamcast();
    }

    private boolean canReadFiles() {
        if (android.os.Build.VERSION.SDK_INT >= 30) return android.os.Environment.isExternalStorageManager();
        return checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    private void scanDreamcast() {
        if (dcFiles.isEmpty()) {   // 0.10.14: last good scan, shared by the normal and Kids mode rows, shown at once
            List<Catalog.Item> cached = new ArrayList<>();
            for (String e : prefs.getStringSet("gameScanCache", Collections.<String>emptySet())) cached.add(Catalog.gameRom(PspScan.fileName(new java.io.File(e)), Uri.fromFile(new java.io.File(e)).toString()));
            if (!cached.isEmpty()) { java.util.Collections.sort(cached, (x, y) -> x.label.compareToIgnoreCase(y.label)); dcFiles = cached; rescan(); }
        }
        if (dcScanning || !canReadFiles()) return;
        dcScanning = true;
        new Thread(() -> {
            List<java.io.File> roots = new ArrayList<>();
            Map<String, String> volLabels = new HashMap<>();   // removable volume root -> its label (e.g. "Dreamcast")
            roots.add(android.os.Environment.getExternalStorageDirectory());
            roots.addAll(DcScan.volumeRoots(new java.io.File("/storage"), new java.io.File("/mnt/media_rw")));   // SD + USB OTG
            if (android.os.Build.VERSION.SDK_INT >= 30) {   // 0.10.9: every mounted volume Android knows about (SD card /storage/XXXX-XXXX, USB)
                try {
                    android.os.storage.StorageManager sm = (android.os.storage.StorageManager) getSystemService(Context.STORAGE_SERVICE);
                    for (android.os.storage.StorageVolume v : sm.getStorageVolumes()) {
                        java.io.File dir = v.getDirectory(); if (dir == null) continue;
                        roots.add(dir);
                        if (v.isRemovable() || !v.isPrimary()) volLabels.put(DcScan.key(dir), String.valueOf(v.getDescription(this)));
                    }
                } catch (Exception ignored) { }
            }
            for (java.io.File f : getExternalFilesDirs(null)) {           // .../Android/data/<pkg>/files on each volume -> its root
                if (f == null) continue;
                String p = f.getAbsolutePath(); int i = p.indexOf("/Android/data/");
                if (i > 0) roots.add(new java.io.File(p.substring(0, i)));
            }
            List<Catalog.Item> out = new ArrayList<>();
            java.util.LinkedHashSet<String> rs = new java.util.LinkedHashSet<>();
            List<java.io.File> uniq = new ArrayList<>();
            for (java.io.File r : roots) if (r != null && rs.add(DcScan.key(r))) uniq.add(r);
            roots = uniq;
            List<String> dcDirs = new ArrayList<>(), pspDirs = new ArrayList<>();
            List<java.io.File> dcFound = DcScan.scan(roots, dcDirs), pspFound = PspScan.scan(roots, pspDirs);
            String primary = DcScan.key(android.os.Environment.getExternalStorageDirectory());
            for (java.io.File r : roots) {   // 0.10.10: game folders at the root of each SD card / USB stick
                String k = DcScan.key(r);
                if (k.equals(primary) || k.startsWith("/storage/emulated") || k.equals("/storage/self")) continue;
                DcScan.scanVolumeRoot(r, volLabels.get(k), dcFound, dcDirs);
            }
            String bios = importBios(roots);
            // 0.10.11 scan details + fallbacks when plain file listing finds nothing
            StringBuilder diag = new StringBuilder();
            diag.append("Android ").append(android.os.Build.VERSION.RELEASE).append(" (SDK ").append(android.os.Build.VERSION.SDK_INT)
                .append("), CodeAque ").append(BuildConfigVersion()).append("\nAll files access: ")
                .append(android.os.Build.VERSION.SDK_INT >= 30 ? String.valueOf(android.os.Environment.isExternalStorageManager()) : "n/a")
                .append(", read storage: ").append(checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED)
                .append("\nVolumes:\n");
            for (java.io.File r : roots) diag.append("  ").append(DcScan.probe(r)).append(volLabels.containsKey(DcScan.key(r)) ? "  label=" + volLabels.get(DcScan.key(r)) : "").append('\n');
            diag.append("Dreamcast folders:\n"); for (String x : dcDirs) diag.append("  ").append(DcScan.probe(new java.io.File(x.replace(" (Dreamcast card)", "")))).append('\n');
            diag.append("PSP folders:\n"); for (String x : pspDirs) diag.append("  ").append(DcScan.probe(new java.io.File(x))).append('\n');
            diag.append("Found by listing: ").append(dcFound.size()).append(" Dreamcast, ").append(pspFound.size()).append(" PSP\n");
            boolean anyDcCard = false; for (String l : volLabels.values()) if (l != null && l.toLowerCase(java.util.Locale.ROOT).contains("dreamcast")) anyDcCard = true;
            if (dcFound.isEmpty() || pspFound.isEmpty()) {   // MediaStore knows every file when All files access is on
                int n = 0;
                try (android.database.Cursor cur = getContentResolver().query(android.provider.MediaStore.Files.getContentUri("external"),
                        new String[]{"_data"}, "lower(_data) LIKE '%.gdi' OR lower(_data) LIKE '%.cdi' OR lower(_data) LIKE '%.chd' OR lower(_data) LIKE '%.iso' OR lower(_data) LIKE '%.cso' OR lower(_data) LIKE '%.pbp'", null, null)) {
                    while (cur != null && cur.moveToNext() && n < 600) { String path = cur.getString(0); if (path == null) continue; n++;
                        DcScan.classify(path, anyDcCard && !path.startsWith("/storage/emulated"), dcFound, pspFound); }
                    diag.append("MediaStore: ").append(n).append(" matching files\n");
                } catch (Exception e) { diag.append("MediaStore: ").append(e.getClass().getSimpleName()).append('\n'); }
            }
            if ((dcFound.isEmpty() || pspFound.isEmpty()) && (rescanAsked || prefs.getBoolean("rootScanOk", false))) {   // root (Magisk asks first) - a grown-up's Rescan, or silently once Magisk has said yes
                StringBuilder o2 = new StringBuilder();
                int rc = su("find /data/media/0 /mnt/media_rw -maxdepth 7 -type f \\( -iname '*.gdi' -o -iname '*.cdi' -o -iname '*.chd' -o -iname '*.iso' -o -iname '*.cso' -o -iname '*.pbp' \\) 2>/dev/null | head -n 600", o2);
                int n = 0;
                for (String line : o2.toString().split("\n")) { line = line.trim(); if (!line.startsWith("/")) continue; n++;
                    String path = DcScan.fromRootPath(line); DcScan.classify(path, anyDcCard && !path.startsWith("/storage/emulated"), dcFound, pspFound); }
                diag.append("Root find: exit ").append(rc).append(", ").append(n).append(" files\n");
                if (rc == 0) prefs.edit().putBoolean("rootScanOk", true).apply();
            }
            diag.append("Total: ").append(dcFound.size()).append(" Dreamcast, ").append(pspFound.size()).append(" PSP");
            out.clear();
            for (java.io.File f : dcFound) out.add(Catalog.gameRom(f.getName(), Uri.fromFile(f).toString()));
            for (java.io.File f : pspFound) out.add(Catalog.gameRom(PspScan.fileName(f), Uri.fromFile(f).toString()));
            java.util.Set<String> cache = new java.util.HashSet<>();
            for (java.io.File f : dcFound) cache.add(f.getAbsolutePath());
            for (java.io.File f : pspFound) cache.add(f.getAbsolutePath());
            if (!cache.isEmpty()) prefs.edit().putStringSet("gameScanCache", cache).apply();
            else if (!dcFiles.isEmpty()) { out.clear(); out.addAll(dcFiles); diag.append("\nNothing found this time - kept the last good list (").append(dcFiles.size()).append(" games)"); }
            scanDetails = diag.toString();
            String vols = android.text.TextUtils.join(", ", rs);
            String dcNote = dcFound.isEmpty() && out.isEmpty() ? "No games found in: " + (dcDirs.isEmpty() ? "no Dreamcast / ROMS folders on " + vols : shortList(dcDirs)) + " - Settings > Scan details" : null;
            String pspNote = pspFound.isEmpty() && out.isEmpty() ? "No games found in: " + (pspDirs.isEmpty() ? "no PSP / ROMS folders on " + vols : shortList(pspDirs)) + " - Settings > Scan details" : null;
            final boolean manual = rescanAsked; rescanAsked = false;
            runOnUiThread(() -> { dcScanning = false; scanNotes.put("dreamcast", dcNote); scanNotes.put("psp", pspNote);
                boolean changed = out.size() != dcFiles.size(); dcFiles = out; if (changed) rescan(); view.invalidate();
                if (manual) { toast("Found " + dcFound.size() + " Dreamcast and " + pspFound.size() + " PSP games"); if (dcFound.isEmpty()) showScanDetails(); }
                if (bios != null && !prefs.getBoolean("kidsMode", false)) toast(bios); });
        }).start();
    }

    /** The player's own dc_boot.bin / dc_flash.bin, copied into Flycast's data folder once. Returns a message or null. */
    private String importBios(List<java.io.File> roots) {
        List<java.io.File> found = DcScan.findBios(roots);
        if (found.isEmpty()) return null;
        java.io.File data = new java.io.File(android.os.Environment.getExternalStorageDirectory(), DcScan.FLYCAST_DATA);
        java.util.Set<String> done = new java.util.HashSet<>(prefs.getStringSet("dcBios", new java.util.HashSet<>()));
        List<String> copied = new ArrayList<>(), failed = new ArrayList<>();
        for (java.io.File b : found) {
            java.io.File dst = new java.io.File(data, DcScan.targetName(b));
            if (done.contains(b.getAbsolutePath()) || (dst.isFile() && dst.length() == b.length())) continue;
            boolean ok = false;
            try {                                                      // works where Android still lets us write there (Android 10 and older)
                data.mkdirs();
                try (java.io.InputStream in = new java.io.FileInputStream(b); java.io.OutputStream o = new java.io.FileOutputStream(dst)) {
                    byte[] buf = new byte[65536]; int n; while ((n = in.read(buf)) > 0) o.write(buf, 0, n);
                }
                ok = dst.length() == b.length();
            } catch (Exception ignored) { }
            if (!ok && bootAnimAvailable()) {                          // Android 11+: only root can write into another app's folder
                String c = DcScan.rootCopyCmd(b.getAbsolutePath(), data.getAbsolutePath());
                ok = c != null && su(c, null) == 0;
            }
            if (ok) { copied.add(b.getName()); done.add(b.getAbsolutePath()); } else failed.add(b.getAbsolutePath());
        }
        prefs.edit().putStringSet("dcBios", done).apply();
        if (!failed.isEmpty()) return "Found your Dreamcast BIOS but Android doesn't let CodeAque copy it into Flycast. In Flycast: Settings > "
                + "Custom Paths > BIOS Folders > Add, and pick " + new java.io.File(failed.get(0)).getParent();
        return copied.isEmpty() ? null : "Copied your Dreamcast BIOS (" + String.join(", ", copied) + ") into Flycast.";
    }

    /** Grown-up Settings: let CodeAque read game files (Android's "All files access"); hidden in Kids mode. */
    private void askFileAccess() {
        if (canReadFiles()) { toast("CodeAque can already read your game files."); scanDreamcast(); return; }
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            try { start(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + getPackageName()))); return; }
            catch (Exception e) { try { start(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)); return; } catch (Exception ignored) { } }
        } else { requestPermissions(new String[]{android.Manifest.permission.READ_EXTERNAL_STORAGE}, 5); return; }
        toast("Allow file access for CodeAque in Android settings.");
    }

    /** 0.10.7: opens one PSP game file directly in PPSSPP (Gold if that is the one installed); Play page if neither. */
    private void openInPpsspp(Catalog.Item it) {
        String pkg = null;
        for (String p : new String[]{"org.ppsspp.ppsspp", "org.ppsspp.ppssppgold"}) if (getPackageManager().getLaunchIntentForPackage(p) != null) { pkg = p; break; }
        if (pkg == null) { store("org.ppsspp.ppsspp"); return; }
        Uri u = Uri.parse(it.uri);
        Intent v = new Intent(Intent.ACTION_VIEW).setDataAndType(u, "*/*").setPackage(pkg).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        android.os.StrictMode.VmPolicy old = android.os.StrictMode.getVmPolicy();
        try {
            if ("file".equals(u.getScheme())) android.os.StrictMode.setVmPolicy(new android.os.StrictMode.VmPolicy.Builder().build());   // PPSSPP reads the path itself
            start(v);
        } catch (Exception e) {
            toast("PPSSPP could not open it directly. Open PPSSPP and pick the game from its folder.");
            start(getPackageManager().getLaunchIntentForPackage(pkg));
        } finally { android.os.StrictMode.setVmPolicy(old); }
    }

    /** Opens one game file directly in Flycast (store page if Flycast is missing). */
    private void openInFlycast(Catalog.Item it) {
        if (getPackageManager().getLaunchIntentForPackage("com.flycast.emulator") == null) { store("com.flycast.emulator"); return; }
        Uri u = Uri.parse(it.uri);
        Intent v = new Intent(Intent.ACTION_VIEW).setDataAndType(u, "*/*").setPackage("com.flycast.emulator")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        android.os.StrictMode.VmPolicy old = android.os.StrictMode.getVmPolicy();
        try {
            if ("file".equals(u.getScheme())) android.os.StrictMode.setVmPolicy(new android.os.StrictMode.VmPolicy.Builder().build());  // Flycast reads the path itself
            start(v);
        } catch (Exception e) {
            toast("Flycast could not open it directly. Open Flycast and add the folder with your games.");
            start(getPackageManager().getLaunchIntentForPackage("com.flycast.emulator"));
        } finally { android.os.StrictMode.setVmPolicy(old); }
    }

    // ---------- 0.10.6 games installed during Kids mode appear in Kids mode ----------
    /** Launchable packages right now (for the before/after diff around the Kids Play Store tile). */
    private java.util.Set<String> launchablePkgs() {
        java.util.Set<String> out = new java.util.HashSet<>();
        for (ResolveInfo r : getPackageManager().queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0))
            out.add(r.activityInfo.packageName);
        return out;
    }

    /** Kids mode opened the Play Store: remember what was installed, so new games can be found on return. */
    private void kidsStoreOpened() {
        if (!prefs.getBoolean("kidsMode", false)) return;
        prefs.edit().putStringSet("kidsSnap", launchablePkgs()).putLong("kidsSnapAt", System.currentTimeMillis()).apply();
    }

    void kidsInstalled(String pkg) {
        if (!prefs.getBoolean("kidsMode", false) || pkg == null || pkg.equals(getPackageName())) return;
        if (getPackageManager().getLaunchIntentForPackage(pkg) == null) return;   // only apps with an icon to open
        java.util.Set<String> s = new java.util.HashSet<>(prefs.getStringSet("kidsAdded", Collections.<String>emptySet()));
        if (s.add(pkg)) prefs.edit().putStringSet("kidsAdded", s).apply();
    }

    /** Back on the home screen in Kids mode: anything new since the Play Store was opened joins "My New Games". */
    private void kidsDiff() {
        java.util.Set<String> snap = prefs.getStringSet("kidsSnap", null);
        if (snap == null || !prefs.getBoolean("kidsMode", false)) return;
        java.util.Set<String> now = launchablePkgs();
        boolean changed = false;
        for (String p : Catalog.newPackages(snap, now)) {
            java.util.Set<String> s = new java.util.HashSet<>(prefs.getStringSet("kidsAdded", Collections.<String>emptySet()));
            if (!p.equals(getPackageName()) && s.add(p)) { prefs.edit().putStringSet("kidsAdded", s).apply(); changed = true; }
        }
        if (System.currentTimeMillis() - prefs.getLong("kidsSnapAt", 0) > 6L * 3600 * 1000) prefs.edit().remove("kidsSnap").apply();
        if (changed) rescan();
    }

    /** Grown-up Settings (hidden in Kids mode): untick games to take them out of Kids mode. Games first (CATEGORY_GAME). */
    private void kidsGamesManage() {
        List<String> pkgs = new ArrayList<>(prefs.getStringSet("kidsAdded", Collections.<String>emptySet()));
        if (pkgs.isEmpty()) { toast("No games were added in Kids mode yet."); return; }
        PackageManager pm = getPackageManager();
        Collections.sort(pkgs, (a, b) -> Boolean.compare(!isGamePkg(a), !isGamePkg(b)));
        String[] names = new String[pkgs.size()]; boolean[] keep = new boolean[pkgs.size()];
        for (int i = 0; i < pkgs.size(); i++) {
            String n; try { n = pm.getApplicationLabel(pm.getApplicationInfo(pkgs.get(i), 0)).toString(); } catch (Exception e) { n = pkgs.get(i) + " (removed)"; }
            names[i] = n + (isGamePkg(pkgs.get(i)) ? "" : "  (app)"); keep[i] = true;
        }
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Games added in Kids mode")
                .setMultiChoiceItems(names, keep, (d, w, on) -> keep[w] = on)
                .setPositiveButton("Save", (d, w) -> {
                    java.util.Set<String> s = new java.util.HashSet<>();
                    for (int i = 0; i < pkgs.size(); i++) if (keep[i]) s.add(pkgs.get(i));
                    prefs.edit().putStringSet("kidsAdded", s).apply(); rescan();
                })
                .setNegativeButton("Cancel", null).show();
    }

    // ---------- 0.10.27 new app pop-up: pick its section, optionally add it to Kids mode (PIN) ----------
    private final java.util.ArrayDeque<String> newAppQueue = new java.util.ArrayDeque<>();
    private boolean appPopupShowing;
    /** Normal mode only: apps that weren't there last time get the pop-up, one at a time. First run just remembers. */
    void newAppsCheck() {
        java.util.Set<String> now = launchablePkgs(); now.remove(getPackageName());
        java.util.Set<String> known = prefs.getStringSet("knownApps", null);
        if (known == null || prefs.getBoolean("kidsMode", false)) { prefs.edit().putStringSet("knownApps", now).apply(); return; }
        for (String p : Catalog.newPackages(known, now)) if (!newAppQueue.contains(p) && !(locked() && LockPin.blocked(p))) newAppQueue.add(p);
        prefs.edit().putStringSet("knownApps", now).apply();
        showNextNewApp();
    }
    private void showNextNewApp() {
        if (appPopupShowing || newAppQueue.isEmpty() || isFinishing()) return;
        appPopupShowing = true;
        appPlacer(newAppQueue.poll(), true, () -> { appPopupShowing = false; showNextNewApp(); });
    }

    /** The picker: app name + icon, big section buttons (smart guess selected), "Also add to Kids mode" (off; on needs the PIN). */
    private void appPlacer(String pkg, boolean isNew, Runnable done) {
        PackageManager pm = getPackageManager();
        String name; android.graphics.drawable.Drawable icon = null; int cat = -1; boolean game = false;
        try {
            android.content.pm.ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
            name = pm.getApplicationLabel(ai).toString(); icon = pm.getApplicationIcon(ai); game = isGame(ai);
            if (android.os.Build.VERSION.SDK_INT >= 26) cat = ai.category;
        } catch (Exception e) { done.run(); return; }
        java.util.Map<String, String> placed = Catalog.placements(prefs.getStringSet("placed", null));
        final String[] pick = {placed.containsKey(pkg) ? placed.get(pkg) : Catalog.guessSection(game, cat, pkg)};
        java.util.Set<String> appr = prefs.getStringSet("kidsApproved", Collections.<String>emptySet());
        final boolean[] kids = {appr.contains(pkg)};
        float dp = getResources().getDisplayMetrics().density;
        android.widget.ScrollView sv = new android.widget.ScrollView(this);
        android.widget.LinearLayout box = new android.widget.LinearLayout(this); box.setOrientation(android.widget.LinearLayout.VERTICAL);
        box.setPadding((int) (16 * dp), (int) (8 * dp), (int) (16 * dp), (int) (8 * dp)); sv.addView(box);
        android.widget.TextView q = new android.widget.TextView(this); q.setText("Where should it go?"); q.setTextSize(20); box.addView(q);
        java.util.List<android.widget.Button> btns = new java.util.ArrayList<>();
        Runnable paint = () -> { for (android.widget.Button b : btns) { boolean on = b.getTag().equals(pick[0]); b.setText((on ? "\u2714  " : "") + Catalog.sectionLabel((String) b.getTag())); b.setAlpha(on ? 1f : 0.7f); } };
        android.widget.GridLayout grid = new android.widget.GridLayout(this); grid.setColumnCount(2);
        for (String sec : Catalog.SECTIONS) {
            android.widget.Button b = new android.widget.Button(this); b.setTag(sec); b.setTextSize(20); b.setAllCaps(false); b.setMinHeight((int) (56 * dp));
            b.setOnClickListener(v -> { pick[0] = sec; paint.run(); });
            android.widget.GridLayout.LayoutParams lp = new android.widget.GridLayout.LayoutParams(); lp.width = (int) (200 * dp);
            grid.addView(b, lp); btns.add(b);
        }
        box.addView(grid); paint.run();
        android.widget.Switch k = new android.widget.Switch(this); k.setText("Also add to Kids mode"); k.setTextSize(20); k.setChecked(kids[0]);
        k.setPadding(0, (int) (12 * dp), 0, 0);
        k.setOnCheckedChangeListener((v, on) -> {
            if (!on) { kids[0] = false; return; }
            if (kids[0]) return;
            k.setChecked(false);                               // only on after the parent PIN
            Runnable yes = () -> { kids[0] = true; k.setChecked(true); };
            if (pinFresh()) yes.run(); else askPin("Parent PIN: add to Kids mode", yes);
        });
        box.addView(k);
        AlertDialog d = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle((isNew ? "New app: " : "") + name).setIcon(icon).setView(sv)
                .setPositiveButton("Save", (x, w) -> {
                    java.util.Set<String> a2 = new java.util.HashSet<>(prefs.getStringSet("kidsApproved", Collections.<String>emptySet()));
                    if (kids[0]) a2.add(pkg); else a2.remove(pkg);
                    prefs.edit().putStringSet("placed", Catalog.withPlacement(prefs.getStringSet("placed", null), pkg, pick[0]))
                            .putStringSet("kidsApproved", a2).apply();
                    rescan(); toast(name + " \u2192 " + Catalog.sectionLabel(pick[0]) + (kids[0] ? " and Kids mode" : ""));
                })
                .setNegativeButton("Skip", null)
                .setOnDismissListener(x -> done.run())
                .create();
        d.show();
        for (int b : new int[]{AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE}) { android.widget.Button bt = d.getButton(b); if (bt != null) { bt.setTextSize(22); bt.setAllCaps(false); } }
        if (!btns.isEmpty()) for (android.widget.Button b : btns) if (b.getTag().equals(pick[0])) b.requestFocus();
    }

    /** Settings > Sort apps (PIN in Lock mode via open()): every app, tap one to move it or change Kids access. */
    private void sortApps() {
        PackageManager pm = getPackageManager();
        List<String[]> l = new ArrayList<>();
        java.util.Map<String, String> placed = Catalog.placements(prefs.getStringSet("placed", null));
        java.util.Set<String> appr = prefs.getStringSet("kidsApproved", Collections.<String>emptySet());
        for (String p : launchablePkgs()) {
            if (p.equals(getPackageName())) continue;
            String n; try { n = pm.getApplicationLabel(pm.getApplicationInfo(p, 0)).toString(); } catch (Exception e) { continue; }
            l.add(new String[]{n, p});
        }
        Collections.sort(l, (x, y) -> x[0].compareToIgnoreCase(y[0]));
        String[] rows = new String[l.size()];
        for (int i = 0; i < rows.length; i++) {
            String p = l.get(i)[1];
            rows[i] = l.get(i)[0] + "   \u2192 " + (placed.containsKey(p) ? Catalog.sectionLabel(placed.get(p)) : "automatic") + (appr.contains(p) ? "  \u2022 Kids" : "");
        }
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert).setTitle("Sort apps")
                .setItems(rows, (d, w) -> appPlacer(l.get(w)[1], false, () -> { }))
                .setNegativeButton("Done", null).show();
    }

    /** 0.10.27: big preview tiles for each original CodeAque theme; changes the theme only. */
    private void themePicker() {
        float dp = getResources().getDisplayMetrics().density;
        android.widget.ScrollView sv = new android.widget.ScrollView(this);
        android.widget.LinearLayout box = new android.widget.LinearLayout(this); box.setOrientation(android.widget.LinearLayout.VERTICAL);
        box.setPadding((int) (12 * dp), (int) (8 * dp), (int) (12 * dp), (int) (8 * dp)); sv.addView(box);
        AlertDialog[] self = new AlertDialog[1];
        String cur = theme().id; android.widget.Button focus = null;
        for (Palette t : Palette.ALL) {
            android.widget.Button b = new android.widget.Button(this);
            b.setText((t.id.equals(cur) ? "\u2714  " : "") + t.name.toUpperCase(java.util.Locale.ROOT));
            b.setTextSize(28); b.setTextColor(t.text); b.setAllCaps(false); b.setMinHeight((int) (84 * dp));
            b.setTypeface(android.graphics.Typeface.create("sans-serif-black", android.graphics.Typeface.BOLD));
            android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,
                    new int[]{t.tileSelTop | 0xFF000000, t.bgCenter | 0xFF000000, t.gold});
            g.setCornerRadius(16 * dp); g.setStroke((int) (3 * dp), t.gold);
            b.setBackground(g);
            android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(-1, (int) (84 * dp)); lp.setMargins(0, (int) (6 * dp), 0, (int) (6 * dp));
            b.setOnClickListener(v -> {
                prefs.edit().putString("theme", t.id).apply(); view.setTheme(t); sound("hhselect");
                toast("Theme: " + t.name); if (self[0] != null) self[0].dismiss();
            });
            box.addView(b, lp); if (t.id.equals(cur)) focus = b;
        }
        self[0] = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert).setTitle("Themes").setView(sv).setNegativeButton("Close", null).show();
        if (focus != null) focus.requestFocus();
    }

    private boolean isGamePkg(String p) {
        try { return isGame(getPackageManager().getApplicationInfo(p, 0)); } catch (Exception e) { return false; }
    }

    // ---------- 0.10.7 Files: the system file manager (copy Dreamcast games / BIOS from a USB stick) ----------
    private void openFiles() {
        for (String pkg : new String[]{"com.android.documentsui", "com.google.android.documentsui"}) {
            for (String uri : new String[]{"content://com.android.externalstorage.documents/root/primary", "content://com.android.providers.downloads.documents/root/downloads"}) {
                Intent i = new Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(uri), "vnd.android.document/root").setPackage(pkg);
                if (tryStart(i)) return;
            }
            Intent l = getPackageManager().getLaunchIntentForPackage(pkg);
            if (l != null && tryStart(l)) return;
        }
        Intent f = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_FILES);
        if (tryStart(f)) return;
        if (tryStart(new Intent("android.settings.INTERNAL_STORAGE_SETTINGS"))) { toast("Open Files from here to copy your games"); return; }
        toast("No file manager found on this device");
    }
    private boolean tryStart(Intent i) {
        try { i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(i); return true; } catch (Exception e) { return false; }
    }

    // ---------- 0.10.5 Kids mode Wi-Fi tile ----------
    private void openWifi() {
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            try { startActivity(new Intent(Settings.Panel.ACTION_WIFI)); return; } catch (Exception ignored) { }
        }
        try { start(new Intent(Settings.ACTION_WIFI_SETTINGS)); } catch (Exception e) { toast("Wi-Fi settings are not available here"); }
    }

    /** Shown under the Wi-Fi tile. The network name needs location access on Android 10+, so it may just say "Connected". */
    @Override public String wifi() {
        try {
            android.net.wifi.WifiManager wm = (android.net.wifi.WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wm != null && !wm.isWifiEnabled()) return "Wi-Fi is off - press A to turn it on";
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkCapabilities nc = cm == null ? null : cm.getNetworkCapabilities(cm.getActiveNetwork());
            if (nc == null || !nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return "Not connected - press A to pick a Wi-Fi";
            String ssid = wm == null || wm.getConnectionInfo() == null ? null : wm.getConnectionInfo().getSSID();
            ssid = Catalog.cleanSsid(ssid);
            return ssid == null ? "Connected to Wi-Fi" : "Connected to " + ssid;
        } catch (Exception e) { return "Press A for Wi-Fi"; }
    }

    // ---------- 0.10.4 Marvel Rivals on GeForce NOW ----------
    /** Handhelds (GammaOS): the GFN web client in Chrome. Elsewhere: the GFN app with the game's deep link, else its
     *  home screen, else the web link. */
    private void openRivals() {
        if (Catalog.gfnInBrowser(handheld())) { openWeb(Catalog.RIVALS_GFN); return; }
        Intent app = getPackageManager().getLaunchIntentForPackage("com.nvidia.geforcenow");
        if (app != null) {
            try { start(new Intent(Intent.ACTION_VIEW, Uri.parse(Catalog.RIVALS_GFN)).setPackage("com.nvidia.geforcenow")); return; }
            catch (Exception ignored) { }
            toast("Opening GeForce NOW: search for Marvel Rivals."); start(app); return;
        }
        openWeb(Catalog.RIVALS_GFN);
    }

    private android.graphics.Bitmap rivalsPic;
    private boolean rivalsTried;
    /** The official Steam header, fetched once at runtime and cached; null (CodeAque icon + label) until then or offline. */
    private android.graphics.Bitmap rivalsHeader() {
        if (rivalsPic != null) return rivalsPic;
        java.io.File f = new java.io.File(new java.io.File(getCacheDir(), "playart"), "steam-2767030.header.jpg");
        if (f.isFile()) {
            if (rivalsPic != null) return rivalsPic;
            return decodeLater(coverCache, "rivals-header", () -> { rivalsPic = decodeCover(f); return rivalsPic; });
        }
        if (!rivalsTried && online()) {
            rivalsTried = true;
            new Thread(() -> { f.getParentFile().mkdirs(); boolean ok = PlayArt.save(Catalog.RIVALS_HEADER, f); if (ok) runOnUiThread(view::invalidate); }).start();
        }
        return null;
    }

    // ---------- 0.10.2 Kids mode ----------
    private void kidsMode(boolean on) {
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(on ? "Turn on Kids mode?" : "Turn off Kids mode?")
                .setMessage(on ? "The home shows only Play, Kids Games and the Play Store, with big tiles. Retro, game files, Ask and "
                        + "Settings are hidden. To get Settings back, use the Grown-ups row (parent PIN), or hold Select (or Y / Menu) for a second."
                        + (Rotation.suggest(android.os.Build.MANUFACTURER, android.os.Build.MODEL, android.os.Build.HARDWARE, android.os.Build.BOARD) ? " Recommended on this handheld." : "")
                        : "This brings back all rows and Settings.")
                .setPositiveButton(on ? "Turn on" : "Turn off", (d, w) -> { prefs.edit().putBoolean("kidsMode", on).apply(); rescan(); })
                .setNegativeButton("Cancel", null).show();
    }

    // ---------- 0.10.1 Force landscape for games (root only, off by default). UNTESTED ON A REAL DEVICE. ----------
    private void rotationToggle() {
        if (!bootAnimAvailable()) { toast("Force landscape needs a rooted device (Magisk). Nothing was changed."); return; }
        String m = Rotation.next(prefs.getString("rotation", "off"));
        prefs.edit().putString("rotation", m).apply();
        toast(Rotation.label(m) + (m.equals("off") ? "" : ". If games are still sideways, choose it again for the next rotation. " + Rotation.ORIENTATION_MANAGER_TIP));
    }

    /** Before a game opens: lock the rotation with root, only when the user switched it on. Never restores (not needed). */
    /** 0.10.3: lock the rotation first, then open the game, so it starts already landscape. False = nothing to do. */
    private boolean forceLandscapeThen(Intent i) {
        String c = Rotation.cmd(prefs.getString("rotation", "off"));
        if (c == null || !bootAnimAvailable()) return false;
        new Thread(() -> { su(c, null); runOnUiThread(() -> { try { start(i); } catch (Exception e) { toast("Could not open it"); } }); }).start();
        return true;
    }

    private void forceLandscape() {
        String c = Rotation.cmd(prefs.getString("rotation", "off"));
        if (c == null || !bootAnimAvailable()) return;
        new Thread(() -> su(c, null)).start();
    }

    private void maybeSuggestRotation() {
        if (prefs.contains("rotation") || prefs.getBoolean("rotSuggested", false)) return;
        if (!Rotation.suggest(android.os.Build.MANUFACTURER, android.os.Build.MODEL, android.os.Build.HARDWARE, android.os.Build.BOARD)) return;
        prefs.edit().putBoolean("rotSuggested", true).apply();
        toast("Games sideways? Settings > Force landscape for games (needs root).");
    }

    // ---------- 0.10.1 CodeAque boot animation (Magisk module). UNTESTED ON A REAL DEVICE. ----------
    private boolean bootAnimAvailable() {
        boolean app;
        try { getPackageManager().getPackageInfo(BootAnim.MAGISK_PKG, 0); app = true; } catch (Exception e) { app = false; }
        return BootAnim.available(app, p -> new java.io.File(p).exists());
    }

    private void bootAnim() {
        if (!bootAnimAvailable()) { toast("The boot animation needs a rooted device with Magisk. Nothing was changed."); return; }
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("CodeAque boot animation")
                .setMessage(BootAnim.INFO)
                .setPositiveButton("Download and install", (d, w) -> bootAnimDownload())
                .setNeutralButton("Remove boot animation", (d, w) -> bootAnimRemove())
                .setNegativeButton("Cancel", null).show();
    }

    // ---------- 0.10.28: keep CodeAque as Home ----------
    private boolean homeTried;
    /** Once per start: if CodeAque is not the Home app and the device is rooted, set it with su (Magisk asks once). */
    private void ensureHome() {
        if (homeTried || isDefaultHome() || !new java.io.File("/system/bin/su").exists() && !new java.io.File("/sbin/su").exists() && !new java.io.File("/system/xbin/su").exists() && !new java.io.File("/debug_ramdisk/su").exists()) return;
        homeTried = true;
        makeHome(false);
    }
    /** Runs the set-home-activity command (cmd package, then pm) and the HOME role command as root. */
    private void makeHome(boolean manual) {
        if (manual && isDefaultHome()) { toast("CodeAque is already your Home app."); }
        new Thread(() -> {
            boolean ok = false;
            for (String c : Catalog.MAKE_HOME_CMDS) if (su(c, null) == 0) { ok = true; break; }
            if (android.os.Build.VERSION.SDK_INT >= 29 && su(Catalog.ROLE_CMD, null) == 0) ok = true;
            final boolean done = ok;
            runOnUiThread(() -> {
                if (done && isDefaultHome()) { if (manual) toast("CodeAque is now your Home app."); }
                else if (manual) { toast("Could not set it with root. Pick CodeAque and tap Always."); try { start(new Intent(Settings.ACTION_HOME_SETTINGS)); } catch (Exception e) { } }
            });
        }).start();
    }
    /** Other apps that answer the HOME button (not CodeAque, not Android Settings' fallback). */
    private java.util.List<String> otherLaunchers() {
        java.util.List<String> out = new java.util.ArrayList<>();
        for (ResolveInfo r : getPackageManager().queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0))
            if (r.activityInfo != null && Catalog.canHideLauncher(r.activityInfo.packageName) && !out.contains(r.activityInfo.packageName)) out.add(r.activityInfo.packageName);
        return out;
    }
    /** PIN-gated: disables the other launchers with pm disable-user (root), or re-enables the ones CodeAque hid. */
    private void hideLaunchers() {
        askPin("Hide other launchers", () -> {
            java.util.Set<String> hidden = new java.util.HashSet<>(prefs.getStringSet("hiddenLaunchers", new java.util.HashSet<>()));
            java.util.List<String> others = otherLaunchers();
            StringBuilder m = new StringBuilder("Turns off the other Home apps so Android stops asking which one to use. Nothing is uninstalled.\n\n");
            m.append(others.isEmpty() ? "No other launchers are on right now." : "Will turn off:\n" + String.join("\n", others));
            if (!hidden.isEmpty()) m.append("\n\nHidden by CodeAque:\n").append(String.join("\n", hidden));
            AlertDialog.Builder b = dlg().setTitle("Hide other launchers").setMessage(m.toString()).setNegativeButton("Cancel", null);
            if (!others.isEmpty()) b.setPositiveButton("Hide them", (d, w) -> new Thread(() -> {
                java.util.Set<String> h = new java.util.HashSet<>(hidden); int n = 0;
                for (String pkg : others) if (su("pm disable-user --user 0 " + pkg, null) == 0) { h.add(pkg); n++; }
                prefs.edit().putStringSet("hiddenLaunchers", h).apply();
                toast(n == 0 ? "Needs root (Magisk). Nothing was changed." : "Hid " + n + " launcher(s). Undo is in the same menu.");
                makeHome(false);
            }).start());
            if (!hidden.isEmpty()) b.setNeutralButton("Undo (show them again)", (d, w) -> new Thread(() -> {
                java.util.Set<String> left = new java.util.HashSet<>();
                for (String pkg : hidden) if (su("pm enable " + pkg, null) != 0) left.add(pkg);
                prefs.edit().putStringSet("hiddenLaunchers", left).apply();
                toast(left.isEmpty() ? "The other launchers are back." : "Could not turn some back on: " + String.join(", ", left));
            }).start());
            b.show();
        });
    }

    /** Runs one command as root; the user must allow the Magisk superuser prompt. Returns exit code, -1 if su is missing. */
    private int su(String cmd, StringBuilder out) {
        try {
            Process p = new ProcessBuilder("su", "-c", cmd).redirectErrorStream(true).start();
            try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream()))) {
                String l; while ((l = r.readLine()) != null) if (out != null && out.length() < 120000) out.append(l).append('\n');
            }
            return p.waitFor();
        } catch (Exception e) { return -1; }
    }

    private void bootAnimDownload() {
        android.app.ProgressDialog pd = new android.app.ProgressDialog(this, android.R.style.Theme_DeviceDefault_Dialog_Alert);
        pd.setTitle("Downloading boot animation"); pd.setProgressStyle(android.app.ProgressDialog.STYLE_HORIZONTAL);
        pd.setMax(100); pd.setCancelable(false); pd.show();
        new Thread(() -> {
            String path = null; Uri saved = null;
            try {
                java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
                java.io.OutputStream out; java.io.File file = null;
                if (android.os.Build.VERSION.SDK_INT >= 29) {
                    android.content.ContentResolver cr = getContentResolver();
                    try { cr.delete(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                            android.provider.MediaStore.MediaColumns.DISPLAY_NAME + "=?", new String[]{BootAnim.FILE}); } catch (Exception ignored) { }
                    android.content.ContentValues v = new android.content.ContentValues();
                    v.put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, BootAnim.FILE);
                    v.put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "application/zip");
                    v.put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS);
                    saved = cr.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
                    if (saved == null) throw new java.io.IOException("could not save to Downloads");
                    out = cr.openOutputStream(saved);
                } else {
                    java.io.File dir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS);
                    if (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED || !(dir.isDirectory() || dir.mkdirs()))
                        dir = getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS);
                    file = new java.io.File(dir, BootAnim.FILE);
                    out = new java.io.FileOutputStream(file);
                }
                java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(BootAnim.URL).openConnection();
                c.setConnectTimeout(10000); c.setReadTimeout(30000);
                try (java.io.InputStream in = c.getInputStream(); java.io.OutputStream o = out) {
                    long len = c.getContentLengthLong(), total = 0; byte[] b = new byte[65536]; int n;
                    while ((n = in.read(b)) > 0) {
                        o.write(b, 0, n); md.update(b, 0, n); total += n;
                        if (total > BootAnim.MAX_BYTES) throw new java.io.IOException("too big");
                        final int pc = len > 0 ? (int) (total * 100 / len) : 0; runOnUiThread(() -> pd.setProgress(pc));
                    }
                } finally { c.disconnect(); }
                if (!BootAnim.matches(md.digest())) {
                    if (saved != null) getContentResolver().delete(saved, null, null); if (file != null) file.delete();
                    runOnUiThread(pd::dismiss); toast("The download did not match its checksum, so it was deleted and nothing was installed."); return;
                }
                String name = BootAnim.FILE;   // Android may rename it, e.g. "... (1).zip", if an older copy belongs to another app
                if (saved != null) try (android.database.Cursor q = getContentResolver().query(saved,
                        new String[]{android.provider.MediaStore.MediaColumns.DISPLAY_NAME}, null, null, null)) {
                    if (q != null && q.moveToFirst() && q.getString(0) != null) name = q.getString(0);
                } catch (Exception ignored) { }
                path = file != null ? file.getAbsolutePath()
                        : new java.io.File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), name).getAbsolutePath();
            } catch (Exception e) {
                if (saved != null) try { getContentResolver().delete(saved, null, null); } catch (Exception ignored) { }
                runOnUiThread(pd::dismiss); toast("Could not download the boot animation (are you online?) " + e.getMessage()); return;
            }
            final String p = path;
            runOnUiThread(() -> {
                pd.dismiss();
                new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                        .setTitle("Boot animation downloaded")
                        .setMessage("Checked and saved to " + p + ".\n\nInstall now asks Magisk for root: allow the prompt. UNTESTED on a real device; undo with Remove boot animation.")
                        .setPositiveButton("Install now", (d, w) -> bootAnimInstall(p))
                        .setNeutralButton("Open Magisk", (d, w) -> openMagisk())
                        .setNegativeButton("Later", null).show();
            });
        }).start();
    }

    private void bootAnimInstall(String path) {
        if (!BootAnim.safePath(path)) { openMagisk(); return; }
        toast("Installing ... allow the Magisk prompt if it appears.");
        new Thread(() -> {
            StringBuilder log = new StringBuilder();
            int r = su(BootAnim.installCmd(path), log);
            runOnUiThread(() -> {
                if (r == 0) restartPrompt("Boot animation installed. It shows from the next restart.");
                else new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                        .setTitle("Not installed").setMessage(BootAnim.NO_ROOT + (log.length() > 0 ? "\n\n" + log : ""))
                        .setPositiveButton("Open Magisk", (d, w) -> openMagisk()).setNegativeButton("Close", null).show();
            });
        }).start();
    }

    private void bootAnimRemove() {
        new Thread(() -> {
            int r = su(BootAnim.removeCmd(), null);
            runOnUiThread(() -> {
                if (r == 0) restartPrompt("The boot animation will be removed at the next restart.");
                else toast("Could not remove it with root (not installed, or root was refused). You can also remove it in Magisk > Modules.");
            });
        }).start();
    }

    private void restartPrompt(String msg) {
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Restart needed").setMessage(msg)
                .setPositiveButton("Restart now", (d, w) -> new Thread(() -> su(BootAnim.REBOOT_CMD, null)).start())
                .setNegativeButton("Later", null).show();
    }

    private void openMagisk() {
        Intent i = getPackageManager().getLaunchIntentForPackage(BootAnim.MAGISK_PKG);
        if (i == null) { toast("Magisk is not installed. The module is in Downloads as " + BootAnim.FILE + "."); return; }
        toast("In Magisk: Modules > Install from storage > Download > " + BootAnim.FILE + ", then restart.");
        start(i);
    }

    // ---------- 0.10.6 "New update ready" pop-up (also in Kids mode: it only offers the update) ----------
    private boolean popupShowing;

    /** 0.10.25: "Later" (or Back) snoozes this version for 4 hours, saved so it survives a restart. */
    private void snoozeUpdate(String v) {
        prefs.edit().putString("updSnoozeVer", v).putLong("updSnoozeUntil", System.currentTimeMillis() + UpdateInfo.SNOOZE_MS).apply();
    }

    private UpdateInfo pendingPopup;
    void updatePopup(UpdateInfo u, String mine) {
        if (popupShowing || isFinishing()) return;
        // 0.10.28: if the Home chooser, another dialog or the screen is not ours yet, show it when we get focus back
        if (!hasWindowFocus()) { pendingPopup = u; return; }
        pendingPopup = null;
        if (UpdateInfo.snoozed(u.version, prefs.getString("updSnoozeVer", ""), prefs.getLong("updSnoozeUntil", 0), System.currentTimeMillis())) return;
        popupShowing = true;
        String news = UpdateInfo.whatsNew(u.notes);
        AlertDialog d = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("New update ready (v" + u.version + ")")
                .setMessage((news.isEmpty() ? "" : news + "\n\n") + "You have " + mine + ".")
                .setPositiveButton("Update now", (x, w) -> new Thread(() -> install(u)).start())
                .setNegativeButton("Later", (x, w) -> snoozeUpdate(u.version))
                .setOnCancelListener(x -> snoozeUpdate(u.version))
                .setOnDismissListener(x -> popupShowing = false)
                .create();
        d.show();
        float big = prefs.getBoolean("kidsMode", false) ? 30 : 24;           // big, controller- and touch-friendly
        int minH = (int) (64 * getResources().getDisplayMetrics().density);
        for (int b : new int[]{AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE}) {
            android.widget.Button bt = d.getButton(b);
            if (bt != null) { bt.setTextSize(big); bt.setMinHeight(minH); bt.setAllCaps(false); bt.setPadding(minH / 2, 0, minH / 2, 0); }
        }
        android.widget.TextView msg = d.findViewById(android.R.id.message);
        if (msg != null) msg.setTextSize(big - 4);
        android.widget.Button now = d.getButton(AlertDialog.BUTTON_POSITIVE);
        if (now != null) now.requestFocus();                                  // A on the pad = Update now
    }

    /** 0.10.25: replaces the once-a-day check (which ran only in onCreate - rare for a Home app - and was skipped for
     *  24 h even when the check had failed). Runs on every start and resume, at most every 30 minutes, retries in 5 minutes
     *  after a failure, in normal and Kids mode. Fetches through the robust fetch() (retries, redirects, User-Agent). */
    private long nextUpdateCheck;
    private void maybeUpdateCheck() {
        long now = android.os.SystemClock.elapsedRealtime();
        if (now < nextUpdateCheck || updating) return;
        nextUpdateCheck = now + UpdateInfo.CHECK_EVERY_MS;
        checkUpdate(false);
    }

    private static final int PICK_WALLPAPER = 7;
    private Palette theme() { return Palette.byId(prefs.getString("theme", "neon")); }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_ROLE) {   // 0.10.22 call screening / redirection role
            if (res == RESULT_OK && roleKey != null) { prefs.edit().putBoolean(roleKey, true).apply(); toast("Turned on."); }
            else toast("Not turned on (the role was not given to CodeAque).");
            roleKey = null; return;
        }
        if (req == PICK_ROM_FOLDER && res == RESULT_OK && data != null && data.getData() != null) {
            Uri t = data.getData();
            try { getContentResolver().takePersistableUriPermission(t, Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch (Exception ignored) { }
            java.util.Set<String> trees = new java.util.HashSet<>(prefs.getStringSet("romTrees", new java.util.HashSet<>()));
            trees.add(t.toString()); prefs.edit().putStringSet("romTrees", trees).apply();
            Toast.makeText(this, "Looking for your games in that folder ...", Toast.LENGTH_SHORT).show();
            scanRoms(); return;
        }
        if (req != PICK_WALLPAPER || res != RESULT_OK || data == null || data.getData() == null) return;
        Uri u = data.getData();
        try { getContentResolver().takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch (Exception ignored) { }
        prefs.edit().putString("wallpaper", u.toString()).apply();
        loadWallpaper();
    }

    /** Decodes the chosen picture off the UI thread, at about screen size, so a big photo costs a few MB, not hundreds. */
    private void loadWallpaper() {
        String s = prefs.getString("wallpaper", null);
        if (s == null) { view.setWallpaper(null); return; }
        final int maxSide = Math.max(getResources().getDisplayMetrics().widthPixels, getResources().getDisplayMetrics().heightPixels);
        new Thread(() -> {
            try {
                Uri u = Uri.parse(s);
                android.graphics.BitmapFactory.Options o = new android.graphics.BitmapFactory.Options();
                o.inJustDecodeBounds = true;
                try (java.io.InputStream in = getContentResolver().openInputStream(u)) { android.graphics.BitmapFactory.decodeStream(in, null, o); }
                o.inSampleSize = Catalog.sampleSize(Math.max(o.outWidth, o.outHeight), maxSide);
                o.inJustDecodeBounds = false;
                android.graphics.Bitmap b;
                try (java.io.InputStream in = getContentResolver().openInputStream(u)) { b = android.graphics.BitmapFactory.decodeStream(in, null, o); }
                android.graphics.Bitmap fb = b;
                runOnUiThread(() -> view.setWallpaper(fb));
            } catch (Exception e) {
                runOnUiThread(() -> {
                    prefs.edit().remove("wallpaper").apply(); view.setWallpaper(null);
                    Toast.makeText(this, "Could not open that picture", Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    @Override public void sound(String n) {
        if (theme().selectSound != null && n.equals("hhselect")) n = theme().selectSound;
        Integer id = sounds.get(n);
        if (id != null) pool.play(id, 0.5f, 0.5f, 1, 0, 1f);
    }

    @Override public String effects() { return prefs.getString("effects", "auto"); }

    private long statusAt; private String statusVal = "";
    @Override public String status() {
        long now = System.currentTimeMillis();
        if (now - statusAt < 10000) return statusVal;
        statusAt = now; statusVal = readBattery(); return statusVal;
    }

    private String readBattery() {
        Intent b = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (b == null || !b.getBooleanExtra(BatteryManager.EXTRA_PRESENT, false)) return "";
        int lvl = b.getIntExtra(BatteryManager.EXTRA_LEVEL, -1), sc = b.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        int st = b.getIntExtra(BatteryManager.EXTRA_STATUS, 0);
        if (lvl < 0) return "";
        return (lvl * 100 / sc) + "%" + (st == BatteryManager.BATTERY_STATUS_CHARGING ? " charging" : "");
    }

    private long onlineAt; private boolean onlineVal;
    @Override public boolean online() {
        long now = System.currentTimeMillis();
        if (now - onlineAt > 3000) {
            onlineAt = now;
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkCapabilities nc = cm == null ? null : cm.getNetworkCapabilities(cm.getActiveNetwork());
            onlineVal = nc != null && nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        }
        return onlineVal;
    }
}

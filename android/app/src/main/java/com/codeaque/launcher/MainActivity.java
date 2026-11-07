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

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("codeaque", MODE_PRIVATE);
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
        loadWallpaper();
        maybeDailyCheck();
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
        if (i.hasExtra("theme")) { prefs.edit().putString("theme", Palette.byId(i.getStringExtra("theme")).id).apply(); view.setTheme(theme()); }
        if (i.hasExtra("cat")) view.focus(i.getStringExtra("cat"), i.getIntExtra("tile", 0));
    }

    @Override protected void onResume() {
        super.onResume();
        scanDreamcast();
        installedCache.clear();       // apps may have been installed while we were away
        immersive();
        rescan();
        view.invalidate();
    }

    @Override public void onWindowFocusChanged(boolean has) {
        super.onWindowFocusChanged(has);
        if (has) immersive();          // after a dialog, the volume panel or a swipe that showed the bars
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
            if (m != null) { m.installedCache.clear(); m.icons.clear(); m.coverCache.clear(); m.rescan(); }
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
            Catalog.Item it = Catalog.deviceApp(r.loadLabel(pm).toString(), pkg, r.activityInfo.name);
            apps.add(it);
            if (isGame(r.activityInfo.applicationInfo) && !emuPkgs.contains(pkg)) games.add(it);
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
        view.setCategories(kidsOn ? Catalog.kidsMode(allRoms) : Catalog.categories(games, apps, emus, allRoms, proj, bootAnimAvailable()));
        if (!romsScanned) { romsScanned = true; scanRoms(); }
    }

    @Override public android.graphics.Bitmap appIcon(Catalog.Item it) {
        if (it == Catalog.MARVEL_RIVALS) return rivalsHeader();
        if (!it.isDeviceApp()) return playArt(it, false);
        String k = it.key();
        if (icons.containsKey(k)) return icons.get(k);
        android.graphics.Bitmap b = null;
        try {
            android.graphics.drawable.Drawable d = getPackageManager().getActivityIcon(new android.content.ComponentName(it.pkg, it.cls));
            int px = (int) (64 * getResources().getDisplayMetrics().density);
            b = android.graphics.Bitmap.createBitmap(px, px, android.graphics.Bitmap.Config.ARGB_8888);
            android.graphics.Canvas cv = new android.graphics.Canvas(b);
            d.setBounds(0, 0, px, px); d.draw(cv);
        } catch (Exception ignored) { }
        icons.put(k, b);
        return b;
    }

    // ---------- 0.10.3: pictures from the game's own Google Play listing (runtime only, cached, never bundled) ----------
    private final Map<String, android.graphics.Bitmap> playArtCache = new HashMap<>();

    /** Not-installed Play tiles: the listing's icon (or feature graphic when wide); installed ones keep their own icon. */
    private android.graphics.Bitmap playArt(Catalog.Item it, boolean feature) {
        if (it.isAction() || it.isRom() || it.webUrl != null || it.storePackage() == null) return null;
        Intent li = launchIntent(it);
        if (li != null && li.getComponent() != null)
            return feature ? null : appIcon(Catalog.deviceApp(it.label, li.getComponent().getPackageName(), li.getComponent().getClassName()));
        String pkg = it.storePackage(), k = pkg + (feature ? "#f" : "#i");
        if (playArtCache.containsKey(k)) return playArtCache.get(k);
        java.io.File dir = new java.io.File(getCacheDir(), "playart");
        java.io.File f = feature ? PlayArt.featureFile(dir, pkg) : PlayArt.iconFile(dir, pkg);
        android.graphics.Bitmap b = null;
        if (f.isFile()) b = decodeCover(f);
        else if (!PlayArt.missFile(dir, pkg).exists() && online() && fetching.add("play:" + pkg))
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
        if (it == Catalog.MARVEL_RIVALS) return rivalsHeader();
        if (!it.isRom() && !it.isAction() && !it.isDeviceApp() && launchIntent(it) == null) return playArt(it, true);   // 0.10.3
        android.graphics.Bitmap b = null;
        if (!it.isRom() && !it.isAction() && !it.isDeviceApp()) {     // a catalogue tile (Steam, Steam Link): the installed app's own banner
            Intent li = launchIntent(it);
            if (li != null && li.getComponent() != null) it = Catalog.deviceApp(it.label, li.getComponent().getPackageName(), li.getComponent().getClassName());
        }
        if (it.isDeviceApp()) {                  // the game's own TV banner, when it has one
            try {
                PackageManager pm = getPackageManager();
                android.graphics.drawable.Drawable d = pm.getActivityBanner(new android.content.ComponentName(it.pkg, it.cls));
                if (d == null) d = pm.getApplicationBanner(it.pkg);
                if (d != null) {
                    int w = Math.max(1, d.getIntrinsicWidth() > 0 ? d.getIntrinsicWidth() : 320), h = Math.max(1, d.getIntrinsicHeight() > 0 ? d.getIntrinsicHeight() : 180);
                    b = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888);
                    d.setBounds(0, 0, w, h); d.draw(new android.graphics.Canvas(b));
                }
            } catch (Exception ignored) { }
        } else if (it.isRom() && it.boxart() != null) {
            java.io.File f = coverFile(it);
            if (f.isFile()) b = decodeCover(f);
            else { fetchCover(it, f); return null; }           // not cached yet: fetched in the background, shown when it arrives
        }
        coverCache.put(k, b);
        return b;
    }

    private java.io.File coverFile(Catalog.Item it) {
        java.io.File dir = new java.io.File(getFilesDir(), "covers"); dir.mkdirs();
        return new java.io.File(dir, Integer.toHexString(it.boxart().hashCode()) + ".png");
    }

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
            if (it.isAction()) { action(it.action); return; }
            if (Catalog.isDreamcastRom(it)) { forceLandscape(); openInFlycast(it); return; }
            if (it.isRom()) { forceLandscape(); openRom(it); return; }
            if (it.isDeviceApp()) { start(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setClassName(it.pkg, it.cls)); return; }
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

    private void start(Intent i) { i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(i); }

    private void store(String pkg) {
        try { start(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + pkg))); }
        catch (ActivityNotFoundException e) { start(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + pkg))); }
    }

    private void action(String a) {
        switch (a) {
            case "wifi": openWifi(); break;
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
            case "bootanim": bootAnim(); break;
            case "rotation": rotationToggle(); break;
            case "kids": kidsMode(true); break;
            case "gfn:rivals": openRivals(); break;
            case "kidsoff": kidsMode(false); break;
            case "theme": {
                String n = Palette.next(theme().id);
                prefs.edit().putString("theme", n).apply();
                view.setTheme(Palette.byId(n));
                Toast.makeText(this, "Theme: " + Palette.byId(n).name, Toast.LENGTH_SHORT).show();
                sound("hhselect"); break;
            }
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

    private void allApps() {                       // text-only list: no app logos on our screen
        PackageManager pm = getPackageManager();
        Intent q = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> ri = pm.queryIntentActivities(q, 0);
        final List<String[]> apps = new ArrayList<>();
        for (ResolveInfo r : ri) if (!r.activityInfo.packageName.equals(getPackageName()))
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

    private static String get(String url) throws java.io.IOException {
        java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
        c.setConnectTimeout(10000); c.setReadTimeout(20000); c.setRequestProperty("Accept", "application/vnd.github+json");
        try (java.io.InputStream in = c.getInputStream(); java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
            byte[] b = new byte[16384]; int n; while ((n = in.read(b)) > 0) out.write(b, 0, n);
            return out.toString("UTF-8");
        } finally { c.disconnect(); }
    }

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
                if (u == null || !UpdateInfo.newer(u.version, mine)) { if (manual) toast("CodeAque " + mine + " is the latest version."); return; }
                if (!manual) { toast("CodeAque " + u.version + " is available: Settings > Update"); return; }
                runOnUiThread(() -> new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                        .setTitle("Update to CodeAque " + u.version + "?")
                        .setMessage("You have " + mine + ". The new version is downloaded, checked against the release's SHA256SUMS.txt and then Android asks you to install it.")
                        .setPositiveButton("Update", (d, w) -> new Thread(() -> install(u)).start())
                        .setNegativeButton("Not now", null).show());
            } catch (Exception e) {
                if (manual) toast("Could not check for updates (are you online?)");
            } finally { updating = false; }
        }).start();
    }

    private void install(UpdateInfo u) {
        try {
            toast("Downloading CodeAque " + u.version + " ...");
            String want = UpdateInfo.expectedSha(get(u.sumsUrl));
            if (want == null) { toast("The release has no checksum for the APK, so it was not installed."); return; }
            java.io.File f = new java.io.File(getCacheDir(), "update.apk");
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(u.apkUrl).openConnection();
            c.setConnectTimeout(10000); c.setReadTimeout(30000);
            try (java.io.InputStream in = c.getInputStream(); java.io.FileOutputStream out = new java.io.FileOutputStream(f)) {
                byte[] b = new byte[65536]; int n; long total = 0;
                while ((n = in.read(b)) > 0) { out.write(b, 0, n); md.update(b, 0, n); total += n; if (total > 100L << 20) throw new java.io.IOException("too big"); }
            } finally { c.disconnect(); }
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
            toast("The update could not be installed: " + e.getMessage());
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
    private void openWeb(String url) {
        Intent v = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        try { getPackageManager().getPackageInfo("com.android.chrome", 0); start(new Intent(v).setPackage("com.android.chrome")); return; }
        catch (Exception ignored) { }
        start(v);
    }

    // ---------- 0.10.5 Dreamcast: the player's own files on internal storage / SD ----------
    private volatile List<Catalog.Item> dcFiles = new ArrayList<>();
    private volatile boolean dcScanning;

    private boolean canReadFiles() {
        if (android.os.Build.VERSION.SDK_INT >= 30) return android.os.Environment.isExternalStorageManager();
        return checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    private void scanDreamcast() {
        if (dcScanning || !canReadFiles()) return;
        dcScanning = true;
        new Thread(() -> {
            List<java.io.File> roots = new ArrayList<>();
            roots.add(android.os.Environment.getExternalStorageDirectory());
            roots.addAll(DcScan.volumeRoots(new java.io.File("/storage"), new java.io.File("/mnt/media_rw")));   // SD + USB OTG
            for (java.io.File f : getExternalFilesDirs(null)) {           // .../Android/data/<pkg>/files on each volume -> its root
                if (f == null) continue;
                String p = f.getAbsolutePath(); int i = p.indexOf("/Android/data/");
                if (i > 0) roots.add(new java.io.File(p.substring(0, i)));
            }
            List<Catalog.Item> out = new ArrayList<>();
            for (java.io.File f : DcScan.scan(roots)) out.add(Catalog.rom(f.getName(), Uri.fromFile(f).toString()));
            String bios = importBios(roots);
            runOnUiThread(() -> { dcScanning = false; boolean changed = out.size() != dcFiles.size(); dcFiles = out; if (changed) rescan();
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
        if (f.isFile()) return rivalsPic = decodeCover(f);
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
                        + "Settings are hidden. To get Settings back, hold Select (or Y / Menu) for a second."
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

    /** Runs one command as root; the user must allow the Magisk superuser prompt. Returns exit code, -1 if su is missing. */
    private int su(String cmd, StringBuilder out) {
        try {
            Process p = new ProcessBuilder("su", "-c", cmd).redirectErrorStream(true).start();
            try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream()))) {
                String l; while ((l = r.readLine()) != null) if (out != null && out.length() < 4000) out.append(l).append('\n');
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

    private void maybeDailyCheck() {
        long now = System.currentTimeMillis();
        if (now - prefs.getLong("updChecked", 0) < 24L * 3600 * 1000) return;
        prefs.edit().putLong("updChecked", now).apply();
        checkUpdate(false);
    }

    private static final int PICK_WALLPAPER = 7;
    private Palette theme() { return Palette.byId(prefs.getString("theme", "neon")); }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
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

    /** Decodes the chosen picture at about screen size, so a big photo costs a few MB, not hundreds. */
    private void loadWallpaper() {
        String s = prefs.getString("wallpaper", null);
        if (s == null) { view.setWallpaper(null); return; }
        try {
            Uri u = Uri.parse(s);
            android.graphics.BitmapFactory.Options o = new android.graphics.BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            try (java.io.InputStream in = getContentResolver().openInputStream(u)) { android.graphics.BitmapFactory.decodeStream(in, null, o); }
            int maxSide = Math.max(getResources().getDisplayMetrics().widthPixels, getResources().getDisplayMetrics().heightPixels);
            o.inSampleSize = Catalog.sampleSize(Math.max(o.outWidth, o.outHeight), maxSide);
            o.inJustDecodeBounds = false;
            try (java.io.InputStream in = getContentResolver().openInputStream(u)) { view.setWallpaper(android.graphics.BitmapFactory.decodeStream(in, null, o)); }
        } catch (Exception e) {
            prefs.edit().remove("wallpaper").apply(); view.setWallpaper(null);
            Toast.makeText(this, "Could not open that picture", Toast.LENGTH_SHORT).show();
        }
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

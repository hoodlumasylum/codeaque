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
public class MainActivity extends Activity implements HomeView.Host, ArtLoader.Listener, NovaView.Host {
    private HomeView view;
    private SoundPool pool;
    private final Map<String, Integer> sounds = new HashMap<>();
    private final Map<String, Boolean> installedCache = new HashMap<>();
    private SharedPreferences prefs;
    // 0.10.34 / 0.10.37: Roblox explore list. Kids / Kids 12+ show a filtered feed (official short list if ratings fail).
    private List<Catalog.Item> robloxItems;
    private List<RobloxDiscover.Game> robloxGames;
    private final java.util.ArrayList<String> storeQueue = new java.util.ArrayList<>();
    private int storeQueueAt;
    private final java.util.HashMap<String, Integer> kidsBrowseAt = new java.util.HashMap<>();
    private final java.util.HashMap<String, java.util.List<Catalog.Item>> lastGoodBrowse = new java.util.HashMap<>();
    private final java.util.HashSet<String> browseRetry = new java.util.HashSet<>();
    private final java.util.ArrayList<Catalog.Item> installedLaunchers = new java.util.ArrayList<>();
    private final java.util.ArrayList<Catalog.Item> lastSearchItems = new java.util.ArrayList<>();
    private final java.util.LinkedHashMap<String, java.util.List<PlaySearch.Hit>> searchCache = new java.util.LinkedHashMap<>();
    private String searchStatus = "", searchQuery = "", searchChip = "";
    private int searchGen, searchMoreOffset, imeFallbackGen;
    private boolean searchPlayFailed, searchWatchLock;
    private android.widget.FrameLayout root;
    private android.widget.EditText searchField;
    private NovaView nova;
    private boolean startupReady;
    private String pendingAnimPath;
    private String robloxSession = "", robloxToken = "";
    private boolean robloxOn, robloxLoading, robloxRatingsOk, timeUpScreen;
    private long robloxMoreAt;
    private Catalog.Item pendingPlay;
    private boolean pinDialogOpen;

    @Override protected void onCreate(Bundle b) {
        Catalog.VERSION = myVersion();   // 0.10.28
        super.onCreate(b);
        try { NovaAsr.recoverAfterCrash(this); } catch (Throwable ignored) { }
        prefs = getSharedPreferences("codeaque", MODE_PRIVATE);
        UpdateOfferStore.apply(prefs, myVersion());       // 0.10.59: keep Install visible across restarts
        NovaOverlayService.retire(this);             // 0.10.58: stop any leftover over-apps bubble
        ensurePin();                                  // 0.10.23: default parent PIN 1234 (hashed)
        applyAlwaysKids();                            // 0.10.37: default on — cold start / Home after reboot opens in Kids mode
        edgeToEdge();
        view = new HomeView(this, this);
        root = new android.widget.FrameLayout(this);
        root.addView(view, new android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT));
        searchField = buildKidsSearchField();
        root.addView(searchField);
        // 0.10.68: paint Home (and the splash) first. Nova, a package rescan,
        // Heart/LLM warmup, and TTS must not run before the first frame, and
        // must never stall the UI thread if an engine load is in progress.
        setContentView(root);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            if (view != null && view.onKidsSearch() && imeVisible()) view.setSearchImeFallback(false);
            return v.onApplyWindowInsets(insets);
        });
        immersive();
        if (view != null) {
            view.requestFocus();
            view.post(new NovaStartupAttach(this, b, getIntent()));
        }
    }

    /** After the first frame. A Nova failure must not stop the launcher. */
    void finishStartup(Bundle state, Intent launch) {
        if (startupReady || isFinishing()) return;
        try { attachNova(); }
        catch (Throwable t) {
            android.util.Log.e("CodeAque", "Nova failed to start; home still works", t);
            nova = null;
        }
        try { startDeferredServices(state, launch); }
        catch (Throwable t) { android.util.Log.e("CodeAque", "deferred startup failed", t); }
        startupReady = true;
        try { resumeHome(); }
        catch (Throwable t) { android.util.Log.e("CodeAque", "first resume failed", t); }
    }

    void attachNova() {
        if (nova != null || root == null) return;
        nova = new NovaView(this);
        nova.setHost(this);
        root.addView(nova, new android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT));
    }

    void startDeferredServices(Bundle state, Intent launch) {
        if (artLoader == null) {
            artLoader = new ArtLoader();
            artLoader.setListener(this);
            artLoader.start();
        }
        IntentFilter pf = new IntentFilter();         // an app or game installed / removed while CodeAque is open
        pf.addAction(Intent.ACTION_PACKAGE_ADDED); pf.addAction(Intent.ACTION_PACKAGE_REMOVED);
        pf.addAction(Intent.ACTION_PACKAGE_CHANGED); pf.addAction(Intent.ACTION_PACKAGE_REPLACED);
        pf.addDataScheme("package");
        registerReceiver(packages, pf);
        if (pool == null) {
            pool = new SoundPool.Builder().setMaxStreams(3).setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build();
            for (String n : new String[]{"hhmove", "hhcat", "hhselect", "hhback", "powerup", "chime", "jingle"}) {
                try { sounds.put(n, pool.load(getAssets().openFd(n + ".wav"), 1)); } catch (Exception ignored) { }
            }
        }
        Intent in0 = launch != null ? launch : getIntent();      // am start -e theme powerup  (same as Settings > Theme)
        if (in0 != null && in0.hasExtra("theme")) prefs.edit().putString("theme", Palette.byId(in0.getStringExtra("theme")).id).apply();
        if (view != null) view.setTheme(Palette.byId(prefs.getString("theme", "neon")));
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
        final String themeSound = theme().selectSound;      // the theme's sound once on launch
        if (themeSound != null && pool != null) pool.setOnLoadCompleteListener((sp, id, st) -> { Integer want = sounds.get(themeSound); if (want != null && id == want) sp.play(id, 0.6f, 0.6f, 1, 0, 1f); });
        Intent i = in0;     // for screenshots/tests: am start -e cat cloud -e tile 1
        if (i != null && i.hasExtra("cat") && view != null) view.focus(i.getStringExtra("cat"), i.getIntExtra("tile", 0));
        if (state == null) {
            if (handleInstallerResult(i)) return;
            parentEntry(i);   // 0.10.30: CodeAque Adult / CodeAque Settings icon or long-press shortcut
        }
        new Thread(new NovaEngineWarmup(this), "nova-warmup").start();
    }

    @Override protected void onNewIntent(Intent i) {      // pressing Home again, the screenshot extras, or the installer's answer
        super.onNewIntent(i);
        if (handleInstallerResult(i)) return;
        handleKidsGuardIntent(i);
        if (parentEntry(i)) return;      // 0.10.30
        if (i.hasExtra("theme")) { prefs.edit().putString("theme", Palette.byId(i.getStringExtra("theme")).id).apply(); view.setTheme(theme()); }
        if (i.hasExtra("cat")) view.focus(i.getStringExtra("cat"), i.getIntExtra("tile", 0));
    }

    @Override protected void onResume() {
        super.onResume();
        pausePlayClock();            // 0.10.37: stop counting when CodeAque is back
        if (!startupReady) return;   // 0.10.68: first resume waits until the splash/home has painted
        resumeHome();
    }

    void resumeHome() {
        handleKidsGuardIntent(getIntent());
        scanDreamcast();
        kidsDiff();
        lockResume();                 // 0.10.21
        ensureHome();                 // 0.10.28: stop the Home chooser coming back after restarts
        UpdateOfferStore.apply(prefs, myVersion());
        maybeUpdateCheck();           // 0.10.25
        newAppsCheck();               // 0.10.27: every start / resume, at most every 30 minutes
        installedCache.clear();       // apps may have been installed while we were away
        immersive();
        rescan();
        ScreenTimeService.sync(this);
        int storeGen = parentStoreGen;
        int updateGen = parentUpdateGen;
        resumeStoreQueue();   // may open the next PIN-approved Play Store page (bumps parentStoreGen)
        scheduleCameraOffer();
        if (prefs.getBoolean("kidsMode", false)) {
            if (pendingUpdateConfirm) {
                pendingUpdateConfirm = false;   // installer confirm just launched; keep the update window
            } else if (parentUpdateGen == updateGen) {
                endParentUpdate();
            }
            if (parentStoreGen == storeGen) {
                endParentStore();   // 0.10.49: back on the Kids home with no further store page — re-lock
                if (!parentUpdateActive()) prefs.edit().remove("kidsFgPkg").apply();
            }
            syncDeviceOwnerKiosk();
            syncKidsPinning();
        } else if (deviceOwner()) syncDeviceOwnerKiosk();
        if (nova != null) {
            nova.setKids(prefs.getBoolean("kidsMode", false));
            nova.applyPref();
            nova.bringToFront();
        }
        NovaListenService.appInForeground = true;
        NovaOverlayService.retire(this);
        // If she is visible again, stop hidden listening — in-app Chat can take over.
        if (prefs.getBoolean(NovaView.PREF_BUBBLE, true) && !prefs.getBoolean(NovaView.PREF_HIDDEN, false))
            NovaListenService.stopOnly(this);
        view.invalidate();
    }

    @Override protected void onPause() {
        super.onPause();
        NovaListenService.appInForeground = false;
        maybeBounceKidsLeave();
    }

    @Override protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        maybeBounceKidsLeave();
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
            if (locked() && LockPin.blocked(pkg) && !Catalog.settingsPkg(pkg)) continue;   // 0.10.21; 0.10.40: Settings stays visible in adult mode
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
        installedLaunchers.clear();
        installedLaunchers.addAll(games);
        installedLaunchers.addAll(apps);
        List<Catalog.Item> emus = new ArrayList<>();
        for (Catalog.Item e : Catalog.emulators()) if (launchIntent(e) != null) emus.add(e);
        boolean proj = prefs.getBoolean("projector", false);
        view.setProjector(proj);
        boolean kidsOn = prefs.getBoolean("kidsMode", false);
        view.setKids(kidsOn);
        if (nova != null) {   // 0.10.53: same corner bubble in Kids mode, kid-safe panel
            nova.setKids(kidsOn);
            nova.applyPref();
            nova.bringToFront();
        }
        List<Catalog.Item> allRoms = new ArrayList<>(roms); allRoms.addAll(dcFiles);
        List<Catalog.Item> kidsNew = new ArrayList<>();   // 0.10.6: games installed during Kids mode
        java.util.Set<String> kidsSet = prefs.getStringSet("kidsAdded", Collections.<String>emptySet());
        java.util.Set<String> approved = prefs.getStringSet("kidsApproved", Collections.<String>emptySet());   // 0.10.27
        java.util.Set<String> allowedEmus = allowedEmus();
        for (Catalog.Item a : apps) {
            if (kidsSet.contains(a.pkg) || approved.contains(a.pkg) || allowedEmus.contains(a.pkg)) kidsNew.add(a);
        }
        List<Catalog.Category> cs;
        if (kidsOn) {
            cs = new ArrayList<>(Catalog.kidsMode(allRoms, kidsNew, approved, kids12(), allowedEmus));
        } else if (!prefs.getBoolean("classicHome", false)) {   // 0.10.43: kids-style layout is the adult default; toggle Classic home layout to go back
            Catalog.Item[] rbx = (robloxItems != null && !robloxItems.isEmpty())
                    ? robloxItems.toArray(new Catalog.Item[0]) : Catalog.ROBLOX_ITEMS;
            cs = new ArrayList<>(Catalog.adultBrowse(games, apps, emus, allRoms, proj, bootAnimAvailable(), rbx));
        } else {
            cs = new ArrayList<>(Catalog.applyPlacements(Catalog.categories(games, apps, emus, allRoms, proj, bootAnimAvailable()),
                    Catalog.placements(prefs.getStringSet("placed", null)), apps));
        }
        if (hasPhone()) cs.add(Math.min(1, cs.size()), Catalog.callRow());   // 0.10.29: Phone / Messages in Kids mode and adult mode
        if (locked()) cs = Catalog.applyLockMode(cs);   // Lock mode, Android settings and Home tiles disappear
        if (kidsOn) {
            if (robloxGames != null && robloxRatingsOk) {
                java.util.List<Catalog.Item> shown = withRobloxApp(applyRobloxFilter(robloxGames, true));
                cs = Catalog.replaceItems(cs, "kroblox", shown.isEmpty() ? withRobloxApp(RobloxDiscover.fallback()) : shown);
            }
        } else if (robloxItems != null && !robloxItems.isEmpty()) {
            String row = Catalog.indexOf(cs, "kroblox") >= 0 ? "kroblox" : "roblox";
            cs = Catalog.replaceItems(cs, row, withRobloxApp(robloxItems));
        }
        syncAliases();                                  // 0.10.30: Adult / Settings home-screen icons off while locked
        // 0.10.49: never leave a game tab empty — reuse the last good row if a rebuild comes back blank.
        if (kidsOn) {
            for (int i = 0; i < cs.size(); i++) {
                Catalog.Category row = cs.get(i);
                if (row.items != null && !row.items.isEmpty() && Catalog.kidsBrowseId(row.id) && !"ksearch".equals(row.id))
                    lastGoodBrowse.put(row.id, new java.util.ArrayList<>(row.items));
                else if ((row.items == null || row.items.isEmpty()) && lastGoodBrowse.containsKey(row.id) && !"ksearch".equals(row.id)) {
                    cs = Catalog.replaceItems(cs, row.id, lastGoodBrowse.get(row.id));
                    browseRetry.add(row.id);
                }
            }
        }
        if (!kidsOn) cs = Catalog.replaceItems(cs, "favsites", FavSites.items(favRoot()));
        view.setCategories(cs);
        if (view.boxSet() && !kidsOn) view.setBoxSet(true, java.util.Arrays.asList(Catalog.boxSetItems()));
        else if (kidsOn) view.setBoxSet(false, java.util.Collections.emptyList());
        if (kidsOn) {
            if (!lastSearchItems.isEmpty()) view.replaceRow("ksearch", lastSearchItems);
            else if (searchChip != null && !searchChip.isEmpty()) runKidsSearchChip(searchChip);
            else runKidsSearch(searchQuery == null ? "" : searchQuery);
        }
        if (!romsScanned) { romsScanned = true; scanRoms(); }
        if (!boxsetBootApplied) {
            boxsetBootApplied = true;
            if (prefs.getBoolean("boxsetBoot", false) && !kidsOn && !locked()) enterBoxSet();
        }
    }


    private java.io.File favRoot() { return new java.io.File(getFilesDir(), "favsites"); }

    private android.graphics.Bitmap favIcon(Catalog.Item it) {
        if (it == null) return null;
        String path = it.art;
        if (path == null || path.isEmpty()) {
            String id = Catalog.favId(it);
            if (id == null) return null;
            java.io.File f = FavSites.iconFile(favRoot(), id);
            if (!f.isFile()) return null;
            path = f.getAbsolutePath();
        }
        final String fp = path;
        String k = "favico:" + fp;
        if (coverCache.containsKey(k)) return coverCache.get(k);
        java.io.File f = new java.io.File(fp);
        if (!f.isFile()) return null;
        return decodeLater(coverCache, k, () -> decodeCover(f));
    }

    @Override public void addFavSite() {
        if (prefs.getBoolean("kidsMode", false)) { toast("Not available in Kids mode."); return; }
        android.widget.EditText e = new android.widget.EditText(this);
        e.setHint("example.com");
        e.setSingleLine(true);
        e.setInputType(android.text.InputType.TYPE_TEXT_VARIATION_URI);
        AlertDialog dlg = dlg().setTitle("Add fav site")
                .setMessage("Type or paste a website address. It will appear on the Fav Sites row with its icon.")
                .setView(e)
                .setPositiveButton("Save", (d, w) -> saveFav(e.getText() == null ? "" : e.getText().toString(), null))
                .setNegativeButton("Cancel", null)
                .create();
        dlg.show();
    }

    @Override public void askRemoveFav(Catalog.Item it) {
        if (prefs.getBoolean("kidsMode", false)) { toast("Not available in Kids mode."); return; }
        final String id = Catalog.favId(it);
        if (id == null) return;
        String name = it.label;
        dlg().setTitle("Remove fav site")
                .setMessage("Remove \"" + name + "\" from Fav Sites? This does not change the website itself.")
                .setPositiveButton("Remove", (d, w) -> {
                    FavSites.remove(favRoot(), id);
                    toast("Removed from Fav Sites.");
                    rescan();
                })
                .setNegativeButton("Keep", null)
                .show();
    }

    private void saveFav(String url, String title) {
        if (prefs.getBoolean("kidsMode", false)) { toast("Not available in Kids mode."); return; }
        FavSites.Site s = FavSites.add(favRoot(), url, title);
        if (s == null) { toast("That does not look like a website address."); return; }
        toast("Saved to Fav Sites: " + s.title);
        rescan();
        final String sid = s.id, surl = s.url;
        new Thread(() -> {
            FavSites.fetchIcon(favRoot(), surl, sid);
            FavSites.add(favRoot(), surl, s.title); // refresh icon path in the store
            runOnUiThread(this::rescan);
        }, "fav-icon").start();
    }

    private void openFav(Catalog.Item it) {
        if (prefs.getBoolean("kidsMode", false)) { toast("Not available in Kids mode."); return; }
        String id = Catalog.favId(it);
        FavSites.Site s = id == null ? null : FavSites.get(favRoot(), id);
        String url = s != null ? s.url : it.webUrl;
        if (url == null || url.isEmpty()) { toast("That fav site is no longer saved."); return; }
        start(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
    }

    @Override public android.graphics.Bitmap appIcon(Catalog.Item it) {
        android.graphics.Bitmap fav = favIcon(it);
        if (fav != null) return fav;
        if (it == Catalog.MARVEL_RIVALS) return rivalsHeader();
        android.graphics.Bitmap inst = installedIcon(it);
        if (inst != null) return inst;
        return playArt(it, false);
    }

    /** Installed app icon from PackageManager for any of the item's packages. Cached by package, never by tile index. */
    private android.graphics.Bitmap installedIcon(Catalog.Item it) {
        if (it == null) return null;
        java.util.ArrayList<String> pkgs = new java.util.ArrayList<>();
        if (it.pkg != null) pkgs.add(it.pkg);
        if (it.storePackage() != null) pkgs.add(it.storePackage());
        if (it.packages != null) for (String p : it.packages) if (p != null) pkgs.add(p);
        for (int i = 0; i < pkgs.size(); i++) {
            String pkg = pkgs.get(i);
            String k = "pm:" + pkg;
            if (icons.containsKey(k)) {
                android.graphics.Bitmap b = icons.get(k);
                if (b != null) return b;
                continue;
            }
            try {
                getPackageManager().getPackageInfo(pkg, 0);
            } catch (Exception e) { continue; }
            final String p2 = pkg;
            final int px = (int) (64 * getResources().getDisplayMetrics().density);
            return decodeLater(icons, k, () -> {
                android.graphics.drawable.Drawable d = getPackageManager().getApplicationIcon(p2);
                android.graphics.Bitmap b = android.graphics.Bitmap.createBitmap(px, px, android.graphics.Bitmap.Config.ARGB_8888);
                android.graphics.Canvas cv = new android.graphics.Canvas(b);
                d.setBounds(0, 0, px, px); d.draw(cv);
                return b;
            });
        }
        if (it.isDeviceApp() && it.pkg != null && it.cls != null) {
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
        return null;
    }

    // ---------- 0.10.3: pictures from the game's own Google Play listing (runtime only, cached, never bundled) ----------
    private final Map<String, android.graphics.Bitmap> playArtCache = new HashMap<>();

    /** Not-installed Play tiles: the listing's icon (or feature graphic when wide); installed ones keep their own icon. */
    /** The listing's own wide feature graphic, installed or not (Featured row); null = keep the icon. */
    private android.graphics.Bitmap playFeature(Catalog.Item it) { return it.storePackage() == null ? null : playFeature(it.storePackage()); }
    private android.graphics.Bitmap playFeature(String pkg) {
        String k = pkg + "#F";
        if (playArtCache.containsKey(k)) return playArtCache.get(k);
        java.io.File dir = new java.io.File(getCacheDir(), "playart"), f = PlayArt.featureFile(dir, pkg);
        if (f.isFile()) return decodeLater(playArtCache, k, () -> decodeCover(f));
        if (!PlayArt.isFreshMiss(dir, pkg) && online()) requestPlayArt(pkg, dir);
        return null;
    }

    private ArtLoader artLoader;
    @Override public void artUpdated(String key) {
        if (key != null && key.startsWith("play:")) {
            String pkg = key.substring(5);
            playArtCache.remove(pkg + "#f");
            playArtCache.remove(pkg + "#i");
            playArtCache.remove(pkg + "#F");
            playArtCache.remove(pkg + "#pi");
        }
        if (view != null) view.artArrived();
    }
    private void requestPlayArt(String pkg, java.io.File dir) {
        if (artLoader == null || pkg == null) return;
        artLoader.requestPlay(pkg, dir);
    }
    private void artLoaderRequestSteam(String appid, java.io.File dir) {
        if (artLoader == null || appid == null) return;
        artLoader.requestSteam(appid, dir);
    }
    private void artLoaderRequestRoblox(String place, java.io.File dir) {
        if (artLoader == null || place == null) return;
        artLoader.requestRoblox(place, dir);
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

    private final java.util.Set<String> featureRetry = java.util.Collections.synchronizedSet(new java.util.HashSet<String>());

    private android.graphics.Bitmap playArt(Catalog.Item it, boolean feature) {
        // 0.10.46: web tiles (Xbox / GeForce NOW / Luna / Boosteroid) still have a Play package — fetch their icon.
        if (it == null || it.isRom() || it.storePackage() == null) return null;
        if (it.isAction() && it.storePackage() == null) return null;
        android.graphics.Bitmap inst = installedIcon(it);
        if (inst != null && !feature) return inst;
        String pkg = it.storePackage(), k = pkg + (feature ? "#f" : "#i");
        if (playArtCache.containsKey(k)) return playArtCache.get(k);
        java.io.File dir = new java.io.File(getCacheDir(), "playart");
        java.io.File feat = PlayArt.featureFile(dir, pkg), icon = PlayArt.iconFile(dir, pkg);
        if (feature) {
            if (feat.isFile()) return decodeLater(playArtCache, k, () -> decodeCover(feat));
            // 0.10.51: an icon-only cache from an older parser may still have a listing screenshot
            if (icon.isFile()) {
                if (!PlayArt.isFreshMiss(dir, pkg) && online() && featureRetry.add(pkg)) requestPlayArt(pkg, dir);
                return decodeLater(playArtCache, k, () -> decodeCover(icon));
            }
            if (!PlayArt.isFreshMiss(dir, pkg) && online()) requestPlayArt(pkg, dir);
            return null;
        }
        if (icon.isFile()) return decodeLater(playArtCache, k, () -> decodeCover(icon));
        if (!PlayArt.isFreshMiss(dir, pkg) && online()) requestPlayArt(pkg, dir);
        return null;
    }

    // ---------- 0.9.5: game pictures ----------
    private final Map<String, android.graphics.Bitmap> coverCache = new HashMap<>();
    private final java.util.Set<String> fetching = java.util.Collections.synchronizedSet(new java.util.HashSet<>());

    @Override public android.graphics.Bitmap cover(Catalog.Item it) {
        android.graphics.Bitmap fav = favIcon(it);
        if (fav != null) return fav;
        String k = it.key();
        if (coverCache.containsKey(k)) return coverCache.get(k);
        android.graphics.Bitmap store = storeArt(it);
        if (store != null || Catalog.steamAppId(it) != null || Catalog.robloxPlaceId(it) != null || Catalog.modeAsset(it) != null)
            return store;   // 0.10.32: runtime Steam / Roblox art, original mode fallback
        if (Catalog.isDreamcastRom(it) || Catalog.isPspRom(it)) return gameCover(it);   // 0.10.12: the player's own cover, else a title card
        if (it == Catalog.GEFORCE_NOW || it == Catalog.GFN || it == Catalog.XBOX || it == Catalog.LUNA || it == Catalog.BOOST || it == Catalog.RETROARCH)
            { android.graphics.Bitmap w = playArt(it, true); if (w != null) return w; android.graphics.Bitmap ic = installedIcon(it); if (ic != null) return ic; }
        if (it == Catalog.MARVEL_RIVALS) return rivalsHeader();   // 0.10.37: official Steam header at runtime, no bundled Duat art
        if (!it.isRom() && !it.isAction()) {
            android.graphics.Bitmap w = playArt(it, true);
            if (w != null) return w;
            android.graphics.Bitmap ic = installedIcon(it);
            if (ic != null) return ic;
        }
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
        // 0.10.46: do not cache a null cover — a later Play / PackageManager result can still arrive.
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
            runOnUiThread(() -> { if (fb != null) cache.put(key, fb); fetching.remove("dec:" + key); if (view != null) view.invalidate(); });
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

    @Override public void onBackPressed() {
        if (hideSoftKeyboard()) return;                // 0.10.55: dismiss the pop-up keyboard first
        if (view != null && view.searchKeyboardUp() && view.hideAnyKeyboard()) return;
        if (nova != null && nova.collapse()) return;   // 0.10.52: close Nova's panel first
        /* a home screen stays */
    }

    /** 0.10.55: hide the system IME from whatever field is focused. True if a field had focus. */
    boolean hideSoftKeyboard() {
        android.view.View focus = getCurrentFocus();
        boolean had = focus instanceof android.widget.EditText;
        ImeHide.hide(this);
        if (searchField != null && searchField.hasFocus() && view != null) view.requestFocus();
        imeFallbackGen++;
        return had || imeVisible();
    }

    /**
     * 0.10.56: a press anywhere outside the focused text field and the custom
     * CodeAque keyboard closes both the system IME and the custom keyboard.
     * Runs at the Activity so tiles, tabs, the background, and Nova Chat all see it.
     */
    @Override public boolean dispatchTouchEvent(android.view.MotionEvent ev) {
        if (ev.getActionMasked() == android.view.MotionEvent.ACTION_DOWN) {
            android.view.View focus = getCurrentFocus();
            boolean onField = ImeHide.focusedEdit(focus) && ImeHide.eventInside(focus, ev);
            boolean onCustom = view != null && view.touchOnSearchFieldOrKeyboard(ev);
            if (!ImeHide.shouldKeepKeyboard(onField, onCustom)) dismissAllKeyboards();
        }
        return super.dispatchTouchEvent(ev);
    }

    /** Hide the system IME and the custom Search keyboard, and clear field focus. */
    void dismissAllKeyboards() {
        ImeHide.hide(this);
        imeFallbackGen++;
        if (nova != null) nova.hideSoftIme();
        if (view != null) view.hideAnyKeyboard();
        if (searchField != null && searchField.hasFocus() && view != null) view.requestFocus();
    }
    @Override protected void onDestroy() {
        if (nova != null) nova.release();
        try { unregisterReceiver(packages); } catch (Exception ignored) { } pool.release(); super.onDestroy();
    }

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
            if (pinDialogOpen) return;   // 0.10.37: a leaked A / Enter / tap after a wrong PIN must not launch the tile
            pendingPlay = null;
            if (prefs.getBoolean("kidsMode", false) && Catalog.kidsBlocked(it, allowedEmus())
                    && !(it.isDeviceApp() && prefs.getStringSet("kidsApproved", Collections.<String>emptySet()).contains(it.pkg))) { toast("Not available in Kids mode."); return; }   // 0.10.24; 0.10.27: parent-approved apps open
            if (prefs.getBoolean("kidsMode", false) && ScreenTime.isPlayLaunch(it) && stTimeUp()) {
                askPin("Parent PIN: time's up", () -> screenTimeExtend(it)); return;
            }
            if (kidsPinGate(it)) { askPin("Parent PIN", () -> open(it)); return; }   // 0.10.23
            if (prefs.getBoolean("kidsMode", false) && ScreenTime.isPlayLaunch(it)) pendingPlay = it;
            if (it.isAction()) {
                if (mustPin(it.action) && !pinFresh()) { askPin("Parent PIN", () -> action(it.action)); return; }
                action(it.action); return;
            }
            if (it.isDeviceApp() && Catalog.settingsPkg(it.pkg)) {
                if (prefs.getBoolean("kidsMode", false)) { toast("Not available in Kids mode."); return; }
                if (!pinFresh()) { askPin("Parent PIN", () -> openSystemSettings(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setClassName(it.pkg, it.cls), new Intent(Settings.ACTION_SETTINGS))); return; }
                openSystemSettings(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setClassName(it.pkg, it.cls), new Intent(Settings.ACTION_SETTINGS));
                return;
            }
            if (locked() && it.isDeviceApp() && LockPin.blocked(it.pkg)) { toast("Locked by a parent."); return; }
            if (prefs.getBoolean("kidsMode", false) && it.isRom()) { toast("Not available in Kids mode."); return; }
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
            store(it.storePackage(), it);
        } catch (Exception e) {
            Toast.makeText(this, "Could not open " + it.label, Toast.LENGTH_SHORT).show();
        }
    }

    private void start(Intent i) {
        if (locked() && !pinFresh() && blockedTarget(i)) { toast("Locked by a parent."); return; }   // 0.10.21 Lock mode
        if (prefs.getBoolean("kidsMode", false) && !pinFresh() && kidsBlockedIntent(i)) {
            toast("Not available in Kids mode."); pendingPlay = null; return;
        }
        if (prefs.getBoolean("kidsMode", false) && prefs.getBoolean("kidsLock", false))
            allowKidsForeground(i.getPackage() != null ? i.getPackage() : (i.getComponent() != null ? i.getComponent().getPackageName() : null));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(i);
        if (pendingPlay != null) { onPlayLaunched(pendingPlay); pendingPlay = null; }
    }

    // ---------- 0.10.21 Lock mode (parent PIN). Soft lock inside CodeAque; full kiosk only if CodeAque is device owner. ----------
    private long pinOkUntil;
    private boolean boxsetBootApplied;
    /** 0.10.23: a parent PIN exists (set from Kids mode or Lock mode). */
    private boolean pinSet() { return prefs.getString("lockPin", null) != null; }
    /** 0.10.23 / 0.10.35 / 0.10.52: leaving Kids mode, Grown-ups Settings, Update, raw Play browsing,
     *  sideloading and Screen Time still need the parent PIN. A PIN entered earlier must not skip that.
     *  Age-filtered GET games do not, unless Settings > Ask PIN to install games is on. */
    private boolean kidsPinGate(Catalog.Item it) {
        if (!prefs.getBoolean("kidsMode", false) || pinFresh()) return false;
        if (it.isAction()) return LockPin.kidsActionNeedsPin(it.action);
        if (Catalog.opensStore(it, installed(it)))
            return KidsPlay.kidsStoreNeedsPin(it, installed(it), kids12(), prefs.getBoolean(KidsPlay.PREF_ASK_PIN_INSTALL, false));
        return false;
    }
    private boolean locked() { return prefs.getBoolean("lockMode", false) && prefs.getString("lockPin", null) != null; }
    /** Safe contacts and Downloads always ask for the parent PIN. Everything else uses the lock list only while Lock mode is on. */
    private boolean mustPin(String action) {
        if (action == null) return false;
        if (action.equals("safecontacts") || action.equals("downloads") || action.equals("blockunknown") || action.equals("safeonly")
                || action.equals("kidsprofile") || action.equals("screentime") || action.equals("alwayskids") || action.equals("kidslock")
                || action.equals("lockphone") || action.equals("removeowner") || action.equals("exitcodeaque")
                || action.equals("phonesettings") || action.equals("phoneaccounts") || action.equals("homeapp")
                || action.equals("installkids") || action.equals("kidsemus") || action.equals("installpin")) return true;
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
        android.widget.LinearLayout box = pinBox("Enter the parent PIN.", e);
        addForgotPin(box);
        final boolean[] done = { false };
        AlertDialog dlg = pinDlg(title, box)
                .setPositiveButton("OK", null)
                .setNegativeButton("Cancel", (d, w) -> closePinDialog())
                .setOnCancelListener(d -> closePinDialog())
                .create();
        dlg.setCanceledOnTouchOutside(true);
        Runnable submit = () -> trySubmitPin(dlg, e, ok, done);
        bindPinEnter(e, dlg, submit);
        pinDialogOpen = true;
        showPinDlg(dlg, e);
        bindPinOk(dlg, submit);
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
            t.setMaxLines(3);
            t.setPadding(0, 0, 0, p / 2);
            box.addView(t);
        }
        if (pinHintNeeded()) {
            android.widget.TextView h = new android.widget.TextView(this);
            h.setText(DEFAULT_PIN_HINT);
            h.setPadding(0, 0, 0, p / 2);
            box.addView(h);
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
    /** 0.10.36 / 0.10.37: OK, IME Done/Enter, and controller A / DPAD_CENTER all go through the same verifier.
     *  The dialog stays open on a wrong or empty PIN, and A / Enter is consumed so it cannot launch the tile underneath. */
    private void bindPinEnter(android.widget.EditText e, AlertDialog dlg, Runnable submit) {
        e.setOnEditorActionListener((v, id, ev) -> {
            if (id == android.view.inputmethod.EditorInfo.IME_ACTION_DONE || id == android.view.inputmethod.EditorInfo.IME_ACTION_GO
                    || (ev != null && ev.getAction() == android.view.KeyEvent.ACTION_DOWN && pinSubmitKey(ev.getKeyCode()))) {
                if (submit != null) submit.run();
                return true;
            }
            return false;
        });
        e.setOnKeyListener((v, code, ev) -> {
            if (pinSubmitKey(code)) {
                if (ev.getAction() == android.view.KeyEvent.ACTION_UP && submit != null) submit.run();
                return true;
            }
            return false;
        });
        dlg.setOnKeyListener((d, code, ev) -> {
            if (pinSubmitKey(code)) {
                if (ev.getAction() == android.view.KeyEvent.ACTION_UP && submit != null) submit.run();
                return true;   // consume DOWN and UP so HomeView cannot activate a tile
            }
            return false;
        });
    }
    private boolean pinSubmitKey(int code) {
        return code == android.view.KeyEvent.KEYCODE_ENTER || code == android.view.KeyEvent.KEYCODE_NUMPAD_ENTER
                || code == android.view.KeyEvent.KEYCODE_DPAD_CENTER || code == android.view.KeyEvent.KEYCODE_BUTTON_A;
    }
    /** Replace AlertDialog's auto-dismiss OK handler. Only the verifier may dismiss and run onSuccess. */
    private void bindPinOk(AlertDialog dlg, Runnable submit) {
        android.widget.Button b = dlg.getButton(AlertDialog.BUTTON_POSITIVE);
        if (b != null) b.setOnClickListener(v -> { if (submit != null) submit.run(); });
    }
    private void closePinDialog() { pinDialogOpen = false; ImeHide.hide(this); }

    /** Shared accept/reject path for every "enter the parent PIN" dialog. Wrong / empty never runs ok. */
    private void trySubmitPin(AlertDialog dlg, android.widget.EditText e, Runnable ok, boolean[] done) {
        if (dlg == null || !dlg.isShowing() || done[0]) return;
        int r = PinAction.verify(e.getText().toString(), prefs.getString("lockPin", null));
        if (r != PinAction.ACCEPTED) {
            if (r != PinAction.EMPTY) {
                int n = prefs.getInt("lockWrong", 0) + 1;
                prefs.edit().putInt("lockWrong", n).putLong("lockWaitUntil", System.currentTimeMillis() + LockPin.backoff(n)).apply();
            }
            toast(r == PinAction.EMPTY ? "Enter the parent PIN." : "Wrong PIN.");
            e.setText("");
            e.requestFocus();
            return;
        }
        done[0] = true;
        prefs.edit().putInt("lockWrong", 0).putLong("lockWaitUntil", 0).apply();
        pinOkUntil = android.os.SystemClock.elapsedRealtime() + 120_000;
        pinDialogOpen = false;
        dlg.dismiss();
        if (ok != null) ok.run();
    }
    /** 0.10.35: first-time PIN, 4-6 digits typed twice. Cancel or a mismatch leaves Kids mode as it is. */
    private void createParentPin(Runnable ok) {
        android.widget.EditText a = pinField(), b = pinField();
        a.setHint("New parent PIN (4-6 digits)"); b.setHint("Type it again");
        a.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_NEXT);
        android.widget.LinearLayout box = pinBox("Pick a parent PIN (4-6 digits).", a, b);
        final boolean[] done = { false };
        AlertDialog dlg = pinDlg("Create a parent PIN", box)
                .setPositiveButton("OK", null)
                .setNegativeButton("Cancel", (d, w) -> closePinDialog())
                .setOnCancelListener(d -> closePinDialog())
                .create();
        dlg.setCanceledOnTouchOutside(true);
        Runnable submit = () -> {
            if (dlg == null || !dlg.isShowing() || done[0]) return;
            int r = PinAction.verifyNew(a.getText().toString(), b.getText().toString());
            if (r != PinAction.ACCEPTED) {
                toast(r == PinAction.EMPTY ? "Type the new PIN twice."
                        : r == PinAction.INVALID ? "The PIN must be 4 to 6 digits. Still in Kids mode."
                        : "The two PINs didn't match. Still in Kids mode.");
                return;
            }
            done[0] = true;
            String pin = a.getText().toString();
            prefs.edit().putString("lockPin", LockPin.newHash(pin)).putBoolean("pinDefault", false).putBoolean("pin_changed", true).putInt("lockWrong", 0).putLong("lockWaitUntil", 0).apply();
            pinOkUntil = android.os.SystemClock.elapsedRealtime() + 120_000;
            pinDialogOpen = false;
            dlg.dismiss();
            if (ok != null) ok.run();
        };
        a.setOnEditorActionListener((v, id, ev) -> { b.requestFocus(); return true; });
        bindPinEnter(b, dlg, submit);
        pinDialogOpen = true;
        showPinDlg(dlg, a);
        bindPinOk(dlg, submit);
    }

    /** Settings > Lock mode: on = set a new 4-6 digit PIN (typed twice); off = needs the PIN (open() already asked). */
    private void lockToggle() {
        if (locked()) {
            new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert).setTitle("Turn off Lock mode?")
                    .setMessage("Settings and Files will open without the PIN again. Kids mode, leaving Kids mode, and raw Play browsing still need it. Age-filtered GET games do not, unless Ask PIN to install games is on.")
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
            prefs.edit().putBoolean("kidsMode", false).remove(ScreenTime.PARENT_STORE_UNTIL).remove(ScreenTime.PARENT_UPDATE_UNTIL).apply();
            parentStoreGen++;
            timeUpScreen = false;
            if (wasKids) stopKidsPinning();   // 0.10.37: PIN exit drops screen pinning
            if (wasKids) syncDeviceOwnerKiosk();   // clears Kids install / factory-reset restrictions
            ScreenTimeService.sync(this);
            if (wasKids && deviceOwner() && locked()) kioskOn();
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
    static final String DEFAULT_PIN_HINT = "Default PIN: 1234, change it in Settings";
    /** Makes sure a PIN exists: the default 1234, hashed like any other. Also after "Clear data". */
    private void ensurePin() {
        SharedPreferences.Editor ed = null;
        if (prefs.getString("lockPin", null) == null)
            ed = prefs.edit().putString("lockPin", LockPin.newHash(DEFAULT_PIN)).putBoolean("pinDefault", true).putBoolean("pin_changed", false).putInt("lockWrong", 0);
        // 0.10.42: pin_changed is the only switch for the default-PIN hint. Never infer it from the stored hash.
        // Existing installs that already used Change PIN have pinDefault=false.
        if (!prefs.contains("pin_changed")) {
            boolean already = prefs.contains("pinDefault") && !prefs.getBoolean("pinDefault", true);
            if (ed == null) ed = prefs.edit();
            ed.putBoolean("pin_changed", already);
        }
        if (ed != null) ed.apply();
    }
    /** True while the factory default PIN has never been changed by the parent. */
    private boolean pinHintNeeded() { return !prefs.getBoolean("pin_changed", false); }
    private boolean pinIsDefault() { return pinHintNeeded(); }

    /** Current PIN, then the new PIN twice. */
    private void changePin() {
        long wait = prefs.getLong("lockWaitUntil", 0) - System.currentTimeMillis();
        if (wait > 0) { toast("Too many wrong PINs. Try again in " + ((wait + 999) / 1000) + " s."); return; }
        android.widget.EditText cur = pinField(), a = pinField(), b = pinField();
        cur.setHint("Current PIN"); a.setHint("New PIN (4-6 digits)"); b.setHint("New PIN again");
        cur.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_NEXT);
        a.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_NEXT);
        android.widget.LinearLayout box = pinBox("Current PIN, then a new one.", cur, a, b);
        addForgotPin(box);
        final boolean[] done = { false };
        AlertDialog dlg = pinDlg("Change PIN", box)
                .setPositiveButton("Change", null)
                .setNegativeButton("Cancel", (d, w) -> closePinDialog())
                .setOnCancelListener(d -> closePinDialog())
                .create();
        cur.setOnEditorActionListener((v, id, ev) -> { a.requestFocus(); return true; });
        a.setOnEditorActionListener((v, id, ev) -> { b.requestFocus(); return true; });
        Runnable submit = () -> {
            if (dlg == null || !dlg.isShowing() || done[0]) return;
            int curR = PinAction.verify(cur.getText().toString(), prefs.getString("lockPin", null));
            if (curR != PinAction.ACCEPTED) {
                if (curR != PinAction.EMPTY) {
                    int n = prefs.getInt("lockWrong", 0) + 1;
                    prefs.edit().putInt("lockWrong", n).putLong("lockWaitUntil", System.currentTimeMillis() + LockPin.backoff(n)).apply();
                }
                toast(curR == PinAction.EMPTY ? "Enter the current PIN." : "Wrong current PIN. Nothing was changed.");
                return;
            }
            int neu = PinAction.verifyNew(a.getText().toString(), b.getText().toString());
            if (neu != PinAction.ACCEPTED) {
                toast(neu == PinAction.EMPTY ? "Type the new PIN twice."
                        : neu == PinAction.INVALID ? "The new PIN must be 4 to 6 digits. Nothing was changed."
                        : "The two new PINs didn't match. Nothing was changed.");
                return;
            }
            done[0] = true;
            String pin = a.getText().toString();
            prefs.edit().putString("lockPin", LockPin.newHash(pin)).putBoolean("pinDefault", false).putBoolean("pin_changed", true).putInt("lockWrong", 0).putLong("lockWaitUntil", 0).apply();
            pinDialogOpen = false;
            dlg.dismiss();
            toast("PIN changed.");
        };
        bindPinEnter(b, dlg, submit);
        pinDialogOpen = true;
        showPinDlg(dlg, cur);
        bindPinOk(dlg, submit);
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
    /** The active device-admin component (AdminReceiver for new setups, or CodeAqueAdmin if that was set earlier). */
    private android.content.ComponentName adminComponent() {
        android.content.ComponentName a = new android.content.ComponentName(this, AdminReceiver.class);
        android.content.ComponentName b = new android.content.ComponentName(this, CodeAqueAdmin.class);
        try {
            android.app.admin.DevicePolicyManager dpm = (android.app.admin.DevicePolicyManager) getSystemService(DEVICE_POLICY_SERVICE);
            if (dpm.isAdminActive(a)) return a;
            if (dpm.isAdminActive(b)) return b;
        } catch (Exception ignored) { }
        return a;
    }
    static final String[] KIOSK_RULES = {android.os.UserManager.DISALLOW_FACTORY_RESET, android.os.UserManager.DISALLOW_SAFE_BOOT,
            android.os.UserManager.DISALLOW_ADD_USER, android.os.UserManager.DISALLOW_OUTGOING_CALLS, android.os.UserManager.DISALLOW_SMS};
    /** Applied only while Kids mode is on and CodeAque is the device owner. Cleared on a PIN exit to adult mode. */
    static final String[] KIDS_KIOSK_RULES = {android.os.UserManager.DISALLOW_INSTALL_APPS,
            android.os.UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES, android.os.UserManager.DISALLOW_FACTORY_RESET};
    /** Device owner: lock task over CodeAque plus the games it can open, CodeAque pinned as Home. UNTESTED ON A REAL DEVICE. */
    private void kioskOn() { kioskOn(new String[0]); }
    private void kioskOn(String[] extra) {
        try {
            android.app.admin.DevicePolicyManager dpm = (android.app.admin.DevicePolicyManager) getSystemService(DEVICE_POLICY_SERVICE);
            android.content.ComponentName admin = adminComponent();
            java.util.Set<String> allow = prefs.getBoolean("kidsMode", false) ? kidsLockTaskPackages() : new java.util.TreeSet<>();
            if (!prefs.getBoolean("kidsMode", false)) {
                allow.add(getPackageName());
                for (ResolveInfo r : getPackageManager().queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0))
                    if ((!LockPin.blocked(r.activityInfo.packageName) || Catalog.settingsPkg(r.activityInfo.packageName)) && !otherHome(r.activityInfo.packageName)) allow.add(r.activityInfo.packageName);
                allow.add("com.android.vending"); allow.add("com.google.android.packageinstaller"); allow.add("com.android.packageinstaller");
                allow.add("com.android.settings"); allow.add("com.android.tv.settings");
            }
            for (String x : extra) if (x != null && !(prefs.getBoolean("kidsMode", false) && ScreenTime.blockedKidPackage(x))) allow.add(x);   // 0.10.22: the dialer / SMS app, only for a Safe contacts call or text
            dpm.setLockTaskPackages(admin, allow.toArray(new String[0]));
            if (android.os.Build.VERSION.SDK_INT >= 28) {
                int feat = android.app.admin.DevicePolicyManager.LOCK_TASK_FEATURE_GLOBAL_ACTIONS;
                if (!prefs.getBoolean("kidsMode", false)) feat |= android.app.admin.DevicePolicyManager.LOCK_TASK_FEATURE_SYSTEM_INFO;
                dpm.setLockTaskFeatures(admin, feat);   // Kids: no recents / notifications; power menu still works
            }
            for (String r : KIOSK_RULES) dpm.addUserRestriction(admin, r);
            if (prefs.getBoolean("kidsMode", false))
                for (String r : KIDS_KIOSK_RULES) {
                    if (android.os.UserManager.DISALLOW_INSTALL_APPS.equals(r) && (parentStoreActive() || parentUpdateActive()))
                        continue;
                    if (android.os.UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES.equals(r) && parentUpdateActive())
                        continue;   // 0.10.49: our own signed APK update is not from Play
                    dpm.addUserRestriction(admin, r);
                }
            IntentFilter home = new IntentFilter(Intent.ACTION_MAIN); home.addCategory(Intent.CATEGORY_HOME); home.addCategory(Intent.CATEGORY_DEFAULT);
            dpm.addPersistentPreferredActivity(admin, home, new android.content.ComponentName(this, MainActivity.class));
            startLockTask();
        } catch (Exception e) { toast("Full kiosk could not start: " + e.getMessage()); }
    }
    private void kioskOff() {
        if (!deviceOwner()) return;
        try {
            android.app.admin.DevicePolicyManager dpm = (android.app.admin.DevicePolicyManager) getSystemService(DEVICE_POLICY_SERVICE);
            android.content.ComponentName admin = adminComponent();
            for (String r : KIOSK_RULES) dpm.clearUserRestriction(admin, r);
            for (String r : KIDS_KIOSK_RULES) dpm.clearUserRestriction(admin, r);
            try { stopLockTask(); } catch (Exception ignored) { }
            dpm.setLockTaskPackages(admin, new String[0]);
            dpm.clearPackagePersistentPreferredActivities(admin, getPackageName());
        } catch (Exception ignored) { }
    }

    /** CodeAque plus approved Kids / Kids 12+ game packages (not Settings, Play Store, installers or emulators). */
    private java.util.Set<String> kidsLockTaskPackages() {
        java.util.Set<String> allow = new java.util.TreeSet<>();
        allow.add(getPackageName());
        java.util.List<Catalog.Item> games = new java.util.ArrayList<>();
        games.add(Catalog.ROBLOX); games.add(Catalog.MINECRAFT);
        for (Catalog.Item k : Catalog.KIDS) games.add(k);
        for (KidsPlay.Game g : KidsPlay.ALL) {
            if (KidsPlay.allowed(g.pegi, KidsPlay.maxPegi(kids12()))) games.add(g.item());
        }
        java.util.Set<String> emusOk = allowedEmus();
        for (Catalog.Item it : games) {
            if (it == null) continue;
            if (Catalog.kidsEmulator(it) && !Catalog.emuAllowed(it, emusOk)) continue;
            for (String p : it.packages) if (p != null && !ScreenTime.blockedKidPackage(p)
                    && !Catalog.cloudGamingPkg(p)
                    && (!Catalog.emulatorPkg(p) || emusOk.contains(p))) allow.add(p);
        }
        for (String p : prefs.getStringSet("kidsApproved", java.util.Collections.<String>emptySet()))
            if (p != null && !ScreenTime.blockedKidPackage(p) && !Catalog.cloudGamingPkg(p)
                    && (!Catalog.emulatorPkg(p) || emusOk.contains(p))) allow.add(p);
        for (String p : emusOk)
            if (p != null && !ScreenTime.blockedKidPackage(p) && !Catalog.cloudGamingPkg(p)) allow.add(p);
        String fg = prefs.getString("kidsFgPkg", null);
        if (fg != null && !ScreenTime.blockedKidPackage(fg) && !Catalog.emulatorPkg(fg)
                && !Catalog.cloudGamingPkg(fg)) allow.add(fg);
        if (parentStoreActive()) {   // 0.10.49: lock-task must allow Play Store after a parent PIN
            allow.add("com.android.vending");
            allow.add("com.google.android.finsky");
            allow.add("com.google.android.packageinstaller");
            allow.add("com.android.packageinstaller");
        }
        if (parentUpdateActive()) {   // 0.10.49: lock-task must allow the installer for our own APK update
            allow.add("com.google.android.packageinstaller");
            allow.add("com.android.packageinstaller");
        }
        String ime = currentImePackage();   // 0.10.50: Kids lock-task must not block the system keyboard
        if (ime != null && !ime.isEmpty()) allow.add(ime);
        return allow;
    }

    /** When CodeAque is the device owner, apply or drop the Kids kiosk. No-op if we are not the owner. */
    private void syncDeviceOwnerKiosk() {
        if (!deviceOwner()) return;
        if (prefs.getBoolean("kidsMode", false)) kioskOn();
        else {
            try {
                android.app.admin.DevicePolicyManager dpm = (android.app.admin.DevicePolicyManager) getSystemService(DEVICE_POLICY_SERVICE);
                android.content.ComponentName admin = adminComponent();
                for (String r : KIDS_KIOSK_RULES) {
                    boolean keep = locked();
                    if (keep) {
                        keep = false;
                        for (String k : KIOSK_RULES) if (k.equals(r)) { keep = true; break; }
                    }
                    if (!keep) dpm.clearUserRestriction(admin, r);
                }
                if (locked()) kioskOn();
                else {
                    try { stopLockTask(); } catch (Exception ignored) { }
                    // Preferred Home stays so a reboot still opens CodeAque; restrictions for installs are off.
                }
            } catch (Exception ignored) { }
        }
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
        } else if (req == REQ_MIC) {
            NovaMicPerm.markAsked(this);
            if (nova != null) nova.onMicPermission(ok);
            if (ok) {
                if (nova == null || !nova.expanded) NovaListenService.tryStartFromUser(this);
            } else {
                toast(NovaSpeechAvail.PERMISSION_DENIED);
            }
        }
    }

    /** Settings > Remove device owner (PIN already asked). So a buyer is not stuck with a kiosk phone. */
    private void removeOwner() {
        if (!deviceOwner()) { toast("CodeAque is not the device owner, so there is nothing to remove."); return; }
        dlg().setTitle("Remove device owner?")
                .setMessage("This turns off the full kiosk lock. The phone can have other Home apps and accounts again. To set device owner later you need a factory-reset phone with no accounts, or root (su) from this same Settings item.")
                .setPositiveButton("Remove", (d, w) -> {
                    kioskOff();
                    try {
                        ((android.app.admin.DevicePolicyManager) getSystemService(DEVICE_POLICY_SERVICE)).clearDeviceOwnerApp(getPackageName());
                        toast("Device owner removed. The phone is no longer locked to CodeAque.");
                    } catch (Exception e) { toast("Could not remove it: " + e.getMessage()); }
                    rescan();
                })
                .setNegativeButton("Cancel", null).show();
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

    @Override public void openCamera() {
        if (prefs.getBoolean("kidsMode", false)) return;   // icon is hidden in Kids mode
        Intent launch = null;
        try { launch = getPackageManager().getLaunchIntentForPackage(CameraApp.PKG); } catch (Exception ignored) { }
        if (launch != null) { start(launch); return; }
        store(CameraApp.PKG);
    }

    private boolean cameraOfferScheduled;

    /** Adult first-run / first return after this update: one-time Play Store prompt if B612 is missing. */
    private void scheduleCameraOffer() {
        if (cameraOfferScheduled || prefs.getBoolean("kidsMode", false)) return;
        if (prefs.getBoolean(CameraApp.PREF_OFFER, false) || pkgInstalled(CameraApp.PKG)) return;
        cameraOfferScheduled = true;
        if (view != null) view.postDelayed(this::maybeOfferCameraOnce, 2300);
        else maybeOfferCameraOnce();
    }

    private void maybeOfferCameraOnce() {
        if (!CameraApp.shouldOfferInstall(prefs.getBoolean("kidsMode", false),
                pkgInstalled(CameraApp.PKG), prefs.getBoolean(CameraApp.PREF_OFFER, false))) return;
        if (!hasWindowFocus()) {
            cameraOfferScheduled = false;   // try again on the next resume
            return;
        }
        prefs.edit().putBoolean(CameraApp.PREF_OFFER, true).apply();
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Install camera app")
                .setMessage("A free camera app with filters (B612) is not installed. Open its Google Play page to install it? CodeAque never copies or sideloads other apps.")
                .setPositiveButton("Open Play Store", (d, w) -> store(CameraApp.PKG))
                .setNegativeButton("Not now", null)
                .show();
    }

    private void store(String pkg) { store(pkg, null); }
    /** Opens only this package's Play details page (not the raw store home). */
    private void store(String pkg, Catalog.Item src) {
        if (pkg == null || pkg.isEmpty()) return;
        if (prefs.getBoolean("kidsMode", false) && pinSet() && !pinFresh() && !parentStoreActive()) {
            boolean need = src != null
                    ? KidsPlay.kidsStoreNeedsPin(src, false, kids12(), prefs.getBoolean(KidsPlay.PREF_ASK_PIN_INSTALL, false))
                    : KidsPlay.kidsStoreNeedsPin(pkg, kids12(), prefs.getBoolean(KidsPlay.PREF_ASK_PIN_INSTALL, false));
            if (need) { askPin("Parent PIN", () -> store(pkg, src)); return; }
        }
        if (prefs.getBoolean("kidsMode", false)) beginParentStore(!pinFresh() && !prefs.getBoolean(KidsPlay.PREF_ASK_PIN_INSTALL, false)
                && (src != null ? KidsPlay.ageFilteredGet(src, false, kids12()) : KidsPlay.ageFilteredGet(pkg, kids12())));
        pendingPlay = null;   // a store visit is not a game launch (do not treat the game package as the allowed foreground)
        Intent market = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + pkg));
        market.setPackage("com.android.vending");
        try { start(market); }
        catch (ActivityNotFoundException e1) {
            try { start(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + pkg))); }
            catch (ActivityNotFoundException e2) {
                if (prefs.getBoolean("kidsMode", false)) { toast("Play Store is not available on this device."); return; }
                start(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + pkg)));
            }
        }
    }

    /** 0.10.49 / 0.10.52: let Play Store (and Play's installer) stay open for 10 minutes,
     *  or until CodeAque is back in front, after a parent PIN or an age-filtered GET (setting off).
     *  This still opens only that game's details page, not the raw Play home. */
    private int parentStoreGen;
    private boolean parentStoreActive() {
        return System.currentTimeMillis() < prefs.getLong(ScreenTime.PARENT_STORE_UNTIL, 0);
    }
    private void beginParentStore() { beginParentStore(false); }
    private void beginParentStore(boolean kidGet) {
        if (!prefs.getBoolean("kidsMode", false)) return;
        boolean already = parentStoreActive();
        long until = System.currentTimeMillis() + ScreenTime.PARENT_STORE_MS;
        prefs.edit().putLong(ScreenTime.PARENT_STORE_UNTIL, until).putString("kidsFgPkg", "com.android.vending").apply();
        if (deviceOwner()) {
            try {
                android.app.admin.DevicePolicyManager dpm = (android.app.admin.DevicePolicyManager) getSystemService(DEVICE_POLICY_SERVICE);
                dpm.clearUserRestriction(adminComponent(), android.os.UserManager.DISALLOW_INSTALL_APPS);
            } catch (Exception ignored) { }
            try { syncDeviceOwnerKiosk(); } catch (Exception ignored) { }
        }
        if (prefs.getBoolean("kidsLock", false) && inLockTask() && !deviceOwner()) {
            try { stopLockTask(); } catch (Exception ignored) { }
        }
        final int gen = ++parentStoreGen;
        if (view != null) view.postDelayed(() -> { if (gen == parentStoreGen) endParentStore(); }, ScreenTime.PARENT_STORE_MS + 250);
        if (!already) toast(kidGet
                ? "Opening this game's Play page so you can install it. Come back to CodeAque when you're done."
                : "Play Store can stay open for 10 minutes, or until you come back to CodeAque.");
    }
    private void endParentStore() {
        parentStoreGen++;
        boolean was = prefs.contains(ScreenTime.PARENT_STORE_UNTIL);
        android.content.SharedPreferences.Editor ed = prefs.edit().remove(ScreenTime.PARENT_STORE_UNTIL);
        String fg = prefs.getString("kidsFgPkg", null);
        if (fg != null && ScreenTime.parentStorePackage(fg)) ed.remove("kidsFgPkg");
        ed.apply();
        if (!was) return;
        if (deviceOwner() && prefs.getBoolean("kidsMode", false)) {
            try {
                android.app.admin.DevicePolicyManager dpm = (android.app.admin.DevicePolicyManager) getSystemService(DEVICE_POLICY_SERVICE);
                dpm.addUserRestriction(adminComponent(), android.os.UserManager.DISALLOW_INSTALL_APPS);
            } catch (Exception ignored) { }
            try { syncDeviceOwnerKiosk(); } catch (Exception ignored) { }
        }
        if (prefs.getBoolean("kidsMode", false) && prefs.getBoolean("kidsLock", false))
            syncKidsPinning();
    }

    /** 0.10.49: after a parent PIN, let Android's installer confirm our own signed APK update, then re-lock. */
    private int parentUpdateGen;
    private boolean pendingUpdateConfirm;
    private boolean parentUpdateActive() {
        return System.currentTimeMillis() < prefs.getLong(ScreenTime.PARENT_UPDATE_UNTIL, 0);
    }
    private void beginParentUpdate() {
        if (!prefs.getBoolean("kidsMode", false)) return;
        long until = System.currentTimeMillis() + ScreenTime.PARENT_UPDATE_MS;
        prefs.edit().putLong(ScreenTime.PARENT_UPDATE_UNTIL, until)
                .putString("kidsFgPkg", "com.android.packageinstaller").apply();
        if (deviceOwner()) {
            try {
                android.app.admin.DevicePolicyManager dpm = (android.app.admin.DevicePolicyManager) getSystemService(DEVICE_POLICY_SERVICE);
                android.content.ComponentName admin = adminComponent();
                dpm.clearUserRestriction(admin, android.os.UserManager.DISALLOW_INSTALL_APPS);
                dpm.clearUserRestriction(admin, android.os.UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES);
            } catch (Exception ignored) { }
            try { syncDeviceOwnerKiosk(); } catch (Exception ignored) { }
        }
        if (prefs.getBoolean("kidsLock", false) && inLockTask() && !deviceOwner()) {
            try { stopLockTask(); } catch (Exception ignored) { }
        }
        final int gen = ++parentUpdateGen;
        if (view != null) view.postDelayed(() -> { if (gen == parentUpdateGen) endParentUpdate(); }, ScreenTime.PARENT_UPDATE_MS + 250);
    }
    private void endParentUpdate() {
        parentUpdateGen++;
        pendingUpdateConfirm = false;
        boolean was = prefs.contains(ScreenTime.PARENT_UPDATE_UNTIL);
        android.content.SharedPreferences.Editor ed = prefs.edit().remove(ScreenTime.PARENT_UPDATE_UNTIL);
        String fg = prefs.getString("kidsFgPkg", null);
        if (fg != null && ScreenTime.parentUpdatePackage(fg) && !parentStoreActive()) ed.remove("kidsFgPkg");
        ed.apply();
        if (!was) return;
        if (deviceOwner() && prefs.getBoolean("kidsMode", false)) {
            try {
                android.app.admin.DevicePolicyManager dpm = (android.app.admin.DevicePolicyManager) getSystemService(DEVICE_POLICY_SERVICE);
                android.content.ComponentName admin = adminComponent();
                dpm.addUserRestriction(admin, android.os.UserManager.DISALLOW_INSTALL_APPS);
                dpm.addUserRestriction(admin, android.os.UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES);
            } catch (Exception ignored) { }
            try { syncDeviceOwnerKiosk(); } catch (Exception ignored) { }
        }
        if (prefs.getBoolean("kidsMode", false) && prefs.getBoolean("kidsLock", false))
            syncKidsPinning();
    }

    /** PackageInstaller callback: Android's "Install this update?" prompt, or a failed session. */
    private boolean handleInstallerResult(Intent i) {
        if (i == null || !"com.codeaque.launcher.INSTALLED".equals(i.getAction())) return false;
        int st = i.getIntExtra(android.content.pm.PackageInstaller.EXTRA_STATUS, -999);
        if (st == android.content.pm.PackageInstaller.STATUS_PENDING_USER_ACTION) {
            Runnable go = () -> {
                beginParentUpdate();
                Intent confirm = i.getParcelableExtra(Intent.EXTRA_INTENT);
                if (confirm != null) {
                    confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    String ip = confirm.getPackage();
                    if (ip == null && confirm.getComponent() != null) ip = confirm.getComponent().getPackageName();
                    if (ip != null) allowKidsForeground(ip);
                    pendingUpdateConfirm = true;
                    try { startActivity(confirm); }
                    catch (Exception e) { pendingUpdateConfirm = false; toast("The installer could not be opened: " + e.getMessage()); endParentUpdate(); }
                }
            };
            // 0.10.54: CodeAque's own Update in Kids mode does not ask for the PIN.
            // beginParentUpdate() still runs in go so the 10-minute installer window works.
            go.run();
        } else if (st != android.content.pm.PackageInstaller.STATUS_SUCCESS) {
            endParentUpdate();
            Toast.makeText(this, "Update not installed: " + i.getStringExtra(android.content.pm.PackageInstaller.EXTRA_STATUS_MESSAGE), Toast.LENGTH_LONG).show();
        } else endParentUpdate();
        return true;
    }

    private void action(String a) {
        if (a != null && a.startsWith("steamstore:")) { openSteamStore(a.substring(11)); return; }
        if (a != null && a.startsWith("roblox:")) { openRobloxPlace(a.substring(7)); return; }
        if (a != null && a.startsWith("favopen:")) { openFav(Catalog.favItem("", "", a.substring(8), null)); return; }
        switch (a) {
            case "wifi": openWifi(); break;
            case "scandetails": if (!prefs.getBoolean("kidsMode", false)) showScanDetails(); break;
            case "rescan": if (!prefs.getBoolean("kidsMode", false)) rescanGames(); break;
            case "filemgr": if (!prefs.getBoolean("kidsMode", false)) openFiles(); break;
            case "kidsgames": kidsGamesManage(); break;
            case "installkids": installKidsGames(); break;
            case "kidsemus": kidsEmusSettings(); break;
            case "classichome": toggleClassicHome(); break;
            case "files": askFileAccess(); break;
            case "bluetooth": start(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)); break;
            case "display": start(new Intent(Settings.ACTION_DISPLAY_SETTINGS)); break;
            case "phonesettings": case "settings":
                openSystemSettings(new Intent(Settings.ACTION_SETTINGS), null); break;
            case "homeapp": case "home":
                openSystemSettings(new Intent(Settings.ACTION_HOME_SETTINGS), new Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)); break;
            case "browser": start(new Intent(Intent.ACTION_VIEW, Uri.parse("https://duckduckgo.com/"))); break;
            case "addfav": addFavSite(); break;
            case "effects": {
                String n = Catalog.nextEffects(effects());
                prefs.edit().putString("effects", n).apply();
                Toast.makeText(this, "Effects: " + n + (n.equals("auto") ? " (30 fps waves)" : n.equals("off") ? " (still background, saves battery)" : " (60 fps)"), Toast.LENGTH_SHORT).show();
                view.invalidate(); break;
            }
            case "allapps": allApps(); break;
            case "romfolder": pickRomFolder(); break;
            case "phoneaccounts": case "accounts":
                openSystemSettings(new Intent(Settings.ACTION_SYNC_SETTINGS), new Intent(Settings.ACTION_SETTINGS)); break;
            case "projector": {
                boolean on = !prefs.getBoolean("projector", false);
                prefs.edit().putBoolean("projector", on).apply();
                Toast.makeText(this, "Projector mode " + (on ? "on: bigger tiles, safe margins, Cloud and Media first" : "off"), Toast.LENGTH_SHORT).show();
                rescan(); break;
            }
            case "update": checkUpdate(true); break;
            case "about": aboutDialog(); break;
            case "makehome": makeHome(true); break;              // 0.10.28
            case "exitcodeaque": exitCodeaque(); break;     // 0.10.39: PIN; opens Home-app settings
            case "hidelaunchers": hideLaunchers(); break;        // 0.10.28 (PIN in Lock mode; always asks the PIN below)
            case "bootanim": bootAnim(); break;
            case "rotation": rotationToggle(); break;
            case "nova": novaSettings(); break;          // 0.10.52
            case "novashow": novaToggleShow(); break;
            case "novavoice": novaToggleVoice(); break;
            case "novasmarter": novaToggleSmarter(); break;
            case "novahide": novaHide(); break;
            case "novalistenhidden": novaToggleListenHidden(); break;
            case "installpin": installPinToggle(); break;  // 0.10.52
            case "kids": enterKids(); break;          // 0.10.54: no PIN, no extra question
            case "kidsprofile": kidsProfilePicker(); break;   // 0.10.37: always PIN (mustPin)
            case "screentime": screenTimePicker(); break;
            case "alwayskids": alwaysKidsToggle(); break;   // 0.10.37: adult Settings, PIN
            case "kidslock": kidsLockToggle(); break;
            case "lockphone": lockPhone(); break;         // 0.10.37: set device owner (su if present, else ADB guide)
            case "gfn:rivals": openRivals(); break;
            case "kidsoff": kidsMode(false); break;
            case "adultpin": parentGo(false, true); break;      // 0.10.35: always ask, even if a PIN was entered a minute ago
            case "settingspin": parentGo(true, true); break;    // 0.10.35
            case "sortapps": sortApps(); break;            // 0.10.27
            case "kidson": enterKids(); break;              // 0.10.26 / 0.10.54: one tap, no PIN, last-used profile
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

    /** 0.10.52: show / hide the in-app bubble, or (PIN) Nova over other games. */
    private void novaToggleShow() {
        boolean on = !prefs.getBoolean(NovaView.PREF_BUBBLE, true);
        android.content.SharedPreferences.Editor ed = prefs.edit().putBoolean(NovaView.PREF_BUBBLE, on);
        if (on) ed.putBoolean(NovaView.PREF_HIDDEN, false);
        ed.apply();
        if (nova != null) nova.applyPref();
        if (on) {
            NovaListenService.stopOnly(this);
            toast("Nova is on.");
        } else {
            toast(NovaListenHidden.TOAST_HIDDEN);
            novaMaybeStartHiddenListen();
        }
    }

    private void novaToggleListenHidden() {
        boolean on = !prefs.getBoolean(NovaListenHidden.PREF, false);
        prefs.edit().putBoolean(NovaListenHidden.PREF, on).apply();
        if (on) {
            toast("Keep listening while hidden is on. Hide Nova and she can still hear you, even in a game, and will answer by voice.");
            novaMaybeStartHiddenListen();
        } else {
            NovaListenService.stopOnly(this);
            toast("Keep listening while hidden is off.");
        }
        if (nova != null) nova.applyPref();
    }

    /** User action in the foreground: start hidden listening if the switch is on and she is hidden. */
    void novaMaybeStartHiddenListen() {
        if (!NovaListenService.shouldRun(this)) return;
        if (!novaMicAllowed()) { novaRequestMic(); return; }
        NovaListenService.tryStartFromUser(this);
    }

    @Override public void onNovaHiddenByUser() { novaMaybeStartHiddenListen(); }

    void speakNovaLine(String s) { if (nova != null) nova.speak(s); }

    private void novaToggleVoice() {
        boolean on = !prefs.getBoolean(NovaView.PREF_VOICE_TALK, true);
        prefs.edit().putBoolean(NovaView.PREF_VOICE_TALK, on).apply();
        if (nova != null) nova.rebuildPanel();
        toast(on ? "Voice talk is on. Opening Nova starts a conversation." : "Voice talk is off. You can type in Chat.");
    }

    private void novaToggleSmarter() {
        boolean on = !prefs.getBoolean(NovaView.PREF_SMARTER, true);
        prefs.edit().putBoolean(NovaView.PREF_SMARTER, on).apply();
        toast(on ? "Smarter Nova is on." : "Smarter Nova is off. She will only use what is stored on this phone.");
    }

    private void novaHide() {
        if (nova != null) nova.hideCompletely();
        else {
            prefs.edit().putBoolean(NovaView.PREF_BUBBLE, false).putBoolean(NovaView.PREF_HIDDEN, false)
                    .putBoolean(NovaView.PREF_TUCKED, false).apply();
            toast(NovaListenHidden.TOAST_HIDDEN);
            novaMaybeStartHiddenListen();
        }
    }

    private void novaSettings() {
        if (prefs.getBoolean("kidsMode", false)) { kidsNovaSettings(); return; }
        float d = getResources().getDisplayMetrics().density;
        int pad = Math.round(12 * d);
        android.widget.LinearLayout box = new android.widget.LinearLayout(this);
        box.setOrientation(android.widget.LinearLayout.VERTICAL);
        box.setPadding(pad, pad / 2, pad, 0);
        // 0.10.57: put the voice download first so it is not hidden under the long note.
        NovaSettingsUi.add(this, box, prefs, d);
        android.widget.TextView msg = new android.widget.TextView(this);
        msg.setText("Nova is a corner helper for simple CodeAque questions. She has no account and does not collect data. Open her and, if Voice talk is on, she greets you and then listens; say another question after she answers, or tap Type instead / Listen to switch, or Close to leave. Double-tap hides her until you turn Show Nova on in this tab. Keep listening while hidden (optional, off by default) lets her hear you and answer by voice while she is hidden, including in a game — she never sits over other apps. Smarter Nova can look up a short fact when she isn't sure, or use an optional on-device brain if a grown-up downloads it. Nova's voice can be downloaded too. Licence details are in About.");
        msg.setPadding(0, 0, 0, pad);
        box.addView(msg);
        android.widget.CheckBox show = new android.widget.CheckBox(this);
        show.setText("Show Nova");
        show.setChecked(prefs.getBoolean(NovaView.PREF_BUBBLE, true));
        box.addView(show);
        android.widget.CheckBox listenHidden = new android.widget.CheckBox(this);
        listenHidden.setText("Keep listening while hidden");
        listenHidden.setChecked(prefs.getBoolean(NovaListenHidden.PREF, false));
        box.addView(listenHidden);
        android.widget.CheckBox voice = new android.widget.CheckBox(this);
        voice.setText("Voice talk");
        voice.setChecked(prefs.getBoolean(NovaView.PREF_VOICE_TALK, true));
        box.addView(voice);
        android.widget.CheckBox smarter = new android.widget.CheckBox(this);
        smarter.setText("Smarter Nova");
        smarter.setChecked(prefs.getBoolean(NovaView.PREF_SMARTER, true));
        box.addView(smarter);
        android.widget.TextView cred = new android.widget.TextView(this);
        cred.setText("Smarter Nova may look up a short fact when she isn't sure, or use an optional on-device brain if you download it. Licence details are in About.");
        cred.setPadding(0, pad / 2, 0, pad / 2);
        box.addView(cred);
        android.widget.ScrollView sc = new android.widget.ScrollView(this);
        sc.addView(box);
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Nova")
                .setView(sc)
                .setPositiveButton("OK", (dlg, w) -> applyNovaSettings(show.isChecked(), listenHidden.isChecked(), voice.isChecked(), smarter.isChecked()))
                .setNegativeButton("Cancel", null).show();
    }

    /** Kids-mode Nova tab: Show Nova and optional listen-while-hidden. No overlay, no PIN. */
    private void kidsNovaSettings() {
        float d = getResources().getDisplayMetrics().density;
        int pad = Math.round(12 * d);
        android.widget.LinearLayout box = new android.widget.LinearLayout(this);
        box.setOrientation(android.widget.LinearLayout.VERTICAL);
        box.setPadding(pad, pad / 2, pad, 0);
        android.widget.TextView msg = new android.widget.TextView(this);
        msg.setText("Double-tap Nova to hide her. Turn Show Nova on here to bring her back. Keep listening while hidden (off unless you turn it on) lets her hear you and answer by voice while she is hidden, even in a game. She does not sit over the game.");
        msg.setPadding(0, 0, 0, pad);
        box.addView(msg);
        android.widget.CheckBox show = new android.widget.CheckBox(this);
        show.setText("Show Nova");
        show.setChecked(prefs.getBoolean(NovaView.PREF_BUBBLE, true));
        box.addView(show);
        android.widget.CheckBox listenHidden = new android.widget.CheckBox(this);
        listenHidden.setText("Keep listening while hidden");
        listenHidden.setChecked(prefs.getBoolean(NovaListenHidden.PREF, false));
        box.addView(listenHidden);
        NovaSettingsUi.addVoicePanel(this, box, prefs, d);
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Nova")
                .setView(box)
                .setPositiveButton("OK", (dlg, w) -> applyNovaSettings(show.isChecked(), listenHidden.isChecked(),
                        prefs.getBoolean(NovaView.PREF_VOICE_TALK, true),
                        prefs.getBoolean(NovaView.PREF_SMARTER, true)))
                .setNegativeButton("Cancel", null).show();
    }

    private void applyNovaSettings(boolean show, boolean listenHidden, boolean voice, boolean smarter) {
        android.content.SharedPreferences.Editor ed = prefs.edit()
                .putBoolean(NovaView.PREF_BUBBLE, show)
                .putBoolean(NovaListenHidden.PREF, listenHidden)
                .putBoolean(NovaView.PREF_VOICE_TALK, voice)
                .putBoolean(NovaView.PREF_SMARTER, smarter)
                .putBoolean(NovaView.PREF_OVERLAY, false);
        if (show) ed.putBoolean(NovaView.PREF_HIDDEN, false);
        ed.apply();
        NovaOverlayService.retire(this);
        if (nova != null) { nova.rebuildPanel(); nova.applyPref(); }
        if (show || !listenHidden) NovaListenService.stopOnly(this);
        else novaMaybeStartHiddenListen();
    }

    /** 0.10.52: Grown-ups setting, off by default. PIN already asked via mustPin. */
    private void installPinToggle() {
        boolean on = !prefs.getBoolean(KidsPlay.PREF_ASK_PIN_INSTALL, false);
        prefs.edit().putBoolean(KidsPlay.PREF_ASK_PIN_INSTALL, on).apply();
        toast(on
                ? "Ask PIN to install games is on. GET in Kids mode needs the parent PIN."
                : "Ask PIN to install games is off. Kids can install age-filtered games from GET without the PIN. Settings, leaving Kids mode, raw Play browsing, sideloading and Screen Time still need the PIN.");
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
        // 0.10.54: CodeAque's own Update does not ask for the PIN in Kids mode.
        if (updating) return;
        updating = true;
        if (manual) toast("Checking for updates ...");
        new Thread(() -> {
            try {
                UpdateInfo u = UpdateInfo.parse(get(UpdateInfo.API));
                String mine = myVersion();
                if (u == null) {
                    UpdateOfferStore.apply(prefs, mine);   // keep a persisted offer if the check could not be parsed
                    if (manual) toast("Could not check for updates. Is the Wi-Fi on?");
                    return;
                }
                if (!UpdateInfo.newer(u.version, mine)) {
                    UpdateOfferStore.clear(prefs);
                    if (manual) toast("You're up to date! (CodeAque " + mine + ")");
                    runOnUiThread(() -> view.invalidate());
                    return;
                }
                UpdateOfferStore.remember(prefs, u);
                runOnUiThread(() -> view.invalidate());   // 0.10.28 badge + 0.10.59 Install bar
                if (!manual) {
                    runOnUiThread(() -> updatePopup(u, mine)); return;
                }
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
            if (prefs.getBoolean("kidsMode", false)) runOnUiThread(() -> beginParentUpdate());
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
            if (prefs.getBoolean("kidsMode", false)) runOnUiThread(() -> endParentUpdate());
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
            if (online()) artLoaderRequestSteam(appid, dir);
            return modeBitmap(asset);
        }
        if (place != null) {
            File f = StoreArt.robloxFile(dir, place);
            if (f.isFile()) return decodeLater(coverCache, it.key(), () -> android.graphics.BitmapFactory.decodeFile(f.getPath()));
            if (online()) artLoaderRequestRoblox(place, dir);
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
        if (!on) { robloxOn = false; return; }
        if (!robloxOn) {
            robloxOn = true;
            refreshRoblox();
        } else if (nearEnd) {
            if (prefs.getBoolean("kidsMode", false) && !robloxRatingsOk) return;  // official short list: do not append unfiltered pages
            loadMoreRoblox();
        }
    }

    private File robloxCacheFile() { return new File(getFilesDir(), RobloxDiscover.CACHE_NAME); }

    private void showRoblox(List<Catalog.Item> items) {
        if (items == null || items.isEmpty()) return;
        robloxItems = items;
        String row = "kroblox";
        // Adult classic home still uses the "roblox" row id.
        if (!prefs.getBoolean("kidsMode", false) && prefs.getBoolean("classicHome", false)) row = "roblox";
        view.replaceRow(row, withRobloxApp(items));
    }

    private int robloxProfile() {
        return prefs.getBoolean("kidsMode", false) ? RobloxDiscover.Profile.kids(prefs.getString("kidsProfile", "kids")) : RobloxDiscover.Profile.ALL;
    }

    private List<Catalog.Item> applyRobloxFilter(List<RobloxDiscover.Game> games, boolean ratingsOk) {
        int p = robloxProfile();
        if (!RobloxDiscover.Profile.isKids(p)) return RobloxDiscover.filter(games, RobloxDiscover.Profile.ALL);
        if (!ratingsOk) return RobloxDiscover.fallback();
        return RobloxDiscover.filter(games, p);
    }

    private void refreshRoblox() {
        if (robloxLoading) return;
        boolean kids = prefs.getBoolean("kidsMode", false);
        if (robloxItems == null || robloxItems.isEmpty()) {
            List<RobloxDiscover.Game> cached = RobloxDiscover.loadGames(robloxCacheFile());
            boolean ok = !kids || RobloxDiscover.hasUsableRatings(cached);
            List<Catalog.Item> shown = applyRobloxFilter(cached, ok);
            if (!shown.isEmpty()) {
                robloxGames = cached;
                robloxRatingsOk = ok;
                showRoblox(shown);
            }
        }
        // 0.10.35: always try the public explore API. online() can be stale or wrong on some phones,
        // and skipping the fetch was leaving Roblox Mode on the official 12-game list.
        robloxSession = RobloxDiscover.newSession();
        robloxToken = "";
        robloxLoading = true;
        final String session = robloxSession;
        final boolean lookup = kids;
        new Thread(() -> {
            RobloxDiscover.Page page;
            try { page = RobloxDiscover.fetchPage(session, "", lookup); }
            catch (Exception e) { page = new RobloxDiscover.Page(new java.util.ArrayList<RobloxDiscover.Game>(), ""); }
            final RobloxDiscover.Page got = page;
            runOnUiThread(() -> {
                robloxLoading = false;
                boolean ok = !prefs.getBoolean("kidsMode", false) || RobloxDiscover.hasUsableRatings(got.games);
                if (!got.games.isEmpty() && ok) {
                    robloxRatingsOk = true;
                    robloxToken = got.nextToken;
                    robloxGames = got.games;
                    List<Catalog.Item> shown = applyRobloxFilter(got.games, true);
                    if (shown.isEmpty() && !got.nextToken.isEmpty()) { loadMoreRoblox(); return; }
                    showRoblox(shown.isEmpty() ? RobloxDiscover.fallback() : shown);
                    RobloxDiscover.saveGames(robloxCacheFile(), got.games);
                } else if (prefs.getBoolean("kidsMode", false)) {
                    robloxRatingsOk = false;
                    robloxToken = "";
                    robloxGames = null;
                    showRoblox(RobloxDiscover.fallback());  // never show an unfiltered Kids feed
                } else if (!got.items.isEmpty()) {
                    robloxRatingsOk = true;
                    robloxToken = got.nextToken;
                    robloxGames = got.games;
                    showRoblox(got.items);
                    RobloxDiscover.saveGames(robloxCacheFile(), got.games);
                } else if (robloxItems == null || robloxItems.isEmpty()) {
                    List<Catalog.Item> cached = RobloxDiscover.loadCache(robloxCacheFile());
                    showRoblox(cached.isEmpty() ? RobloxDiscover.fallback() : cached);
                }
            });
        }, "codeaque-roblox").start();
    }

    private void loadMoreRoblox() {
        if (robloxLoading || robloxToken == null || robloxToken.isEmpty()) return;
        if (prefs.getBoolean("kidsMode", false) && !robloxRatingsOk) return;
        long now = android.os.SystemClock.uptimeMillis();
        if (now - robloxMoreAt < 2500) return;   // onDraw can be near the end every frame
        robloxMoreAt = now;
        robloxLoading = true;
        final String session = robloxSession, token = robloxToken;
        final boolean lookup = prefs.getBoolean("kidsMode", false);
        new Thread(() -> {
            RobloxDiscover.Page page;
            try { page = RobloxDiscover.fetchPage(session, token, lookup); }
            catch (Exception e) { page = new RobloxDiscover.Page(new java.util.ArrayList<RobloxDiscover.Game>(), ""); }
            final RobloxDiscover.Page got = page;
            runOnUiThread(() -> {
                robloxLoading = false;
                boolean ok = !prefs.getBoolean("kidsMode", false) || RobloxDiscover.hasUsableRatings(got.games);
                if (!got.games.isEmpty() && ok) {
                    robloxToken = got.nextToken;
                    robloxGames = RobloxDiscover.mergeGames(robloxGames, got.games);
                    robloxRatingsOk = true;
                    List<Catalog.Item> shown = applyRobloxFilter(robloxGames, true);
                    showRoblox(shown);
                    RobloxDiscover.saveGames(robloxCacheFile(), robloxGames);
                } else if (!got.nextToken.isEmpty() && ok) {
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
        if ("ksearch".equals(catId) && searchStatus != null && !searchStatus.isEmpty()) return searchStatus;
        if (catId != null && browseRetry.contains(catId)) return "Couldn't load more games, tap to retry";
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
    @Override public void openWifi() {
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

    // ---------- 0.10.37 Always start in Kids mode (Settings, parent PIN; default on) ----------
    /** Cold start and Home-after-reboot (onCreate). Does not run on ordinary resume, so a parent who just left Kids mode is not bounced back. */
    private void applyAlwaysKids() {
        if (!prefs.getBoolean("alwaysKids", Catalog.ALWAYS_KIDS_DEFAULT)) return;
        if (!prefs.getBoolean("kidsMode", false))
            prefs.edit().putBoolean("kidsMode", true).remove("parentSettings").remove(ScreenTime.PARENT_STORE_UNTIL).remove(ScreenTime.PARENT_UPDATE_UNTIL).apply();   // last-used kidsProfile (kids / kids12) is left as it is
        else
            prefs.edit().remove("parentSettings").remove(ScreenTime.PARENT_STORE_UNTIL).remove(ScreenTime.PARENT_UPDATE_UNTIL).apply();
    }
    private void alwaysKidsToggle() {
        boolean on = !prefs.getBoolean("alwaysKids", Catalog.ALWAYS_KIDS_DEFAULT);
        prefs.edit().putBoolean("alwaysKids", on).apply();
        toast(on
                ? "Always start in Kids mode is on. After a restart (or Home after a reboot), CodeAque opens in " + (kids12() ? "Kids 12+" : "Kids mode") + ". Leaving still needs the parent PIN."
                : "Always start in Kids mode is off. CodeAque will stay in whichever mode you leave it in.");
    }

    // ---------- 0.10.37 Lock phone to CodeAque in Kids mode (screen pinning + bounce fallback) ----------
    private void kidsLockToggle() {
        boolean on = !prefs.getBoolean("kidsLock", false);
        prefs.edit().putBoolean("kidsLock", on).apply();
        if (on) {
            ScreenTimeService.sync(this);
            if (prefs.getBoolean("kidsMode", false)) syncKidsPinning();
            String extra = !ScreenTimeService.canUsage(this) ? " Usage access is not on, so the swipe-away fallback may not see which app is in front." : "";
            if (prefs.getBoolean("kidsMode", false) && !ScreenTimeService.canOverlay(this) && android.os.Build.VERSION.SDK_INT >= 29)
                extra += " Display over other apps helps bring CodeAque back on Android 10+.";
            toast("Lock phone to CodeAque in Kids mode is on. Android may ask you to confirm screen pinning. A child can usually unpin with Back and Recents unless CodeAque is the device owner (full silent lock)." + extra);
        } else {
            stopKidsPinning();
            ScreenTimeService.sync(this);
            toast("Lock phone to CodeAque in Kids mode is off.");
        }
    }

    /** Pin CodeAque when Kids mode + the Settings toggle are on. No-op if already pinned or we just handed off to a game. */
    private void syncKidsPinning() {
        if (!prefs.getBoolean("kidsMode", false) || !prefs.getBoolean("kidsLock", false)) return;
        if (parentStoreActive() || parentUpdateActive() || prefs.getString("kidsFgPkg", null) != null) return;   // Play / update window or an approved game
        view.post(() -> {
            if (!prefs.getBoolean("kidsMode", false) || !prefs.getBoolean("kidsLock", false)) return;
            if (parentStoreActive() || parentUpdateActive() || prefs.getString("kidsFgPkg", null) != null) return;
            if (inLockTask()) return;
            try { startLockTask(); }
            catch (Exception e) { /* Android may refuse if we are not focused; the bounce fallback still runs. */ }
        });
    }

    /** Called on a PIN exit from Kids mode, or when the parent turns the toggle off. */
    private void stopKidsPinning() {
        prefs.edit().remove("kidsFgPkg").apply();
        if (deviceOwner() && (locked() || prefs.getBoolean("kidsMode", false))) return;   // device-owner kiosk stays pinned
        if (!inLockTask()) return;
        try { stopLockTask(); } catch (Exception ignored) { }
    }

    private boolean inLockTask() {
        try {
            android.app.ActivityManager am = (android.app.ActivityManager) getSystemService(ACTIVITY_SERVICE);
            if (am == null) return false;
            if (android.os.Build.VERSION.SDK_INT >= 23)
                return am.getLockTaskModeState() != android.app.ActivityManager.LOCK_TASK_MODE_NONE;
            return am.isInLockTaskMode();
        } catch (Exception e) { return false; }
    }

    /** Remember the game we just launched, and unpin so it can come to the front (without device owner, only one task can be pinned). */
    private void allowKidsForeground(String pkg) {
        if (!prefs.getBoolean("kidsMode", false) || pkg == null || pkg.isEmpty() || pkg.equals(getPackageName())) return;
        if (ScreenTime.blockedKidPackage(pkg) || Catalog.emulatorPkg(pkg)) {
            boolean ok = (parentUpdateActive() && ScreenTime.parentUpdatePackage(pkg))
                    || (parentStoreActive() && ScreenTime.parentStorePackage(pkg));
            if (!ok) return;
        }
        prefs.edit().putString("kidsFgPkg", pkg).apply();
        // Device owner can keep lock-task and still open a whitelisted game. Without it, pinning must pause.
        if (deviceOwner() && prefs.getBoolean("kidsMode", false)) {
            try { syncDeviceOwnerKiosk(); } catch (Exception ignored) { }
            return;
        }
        if (prefs.getBoolean("kidsLock", false) && inLockTask() && !(deviceOwner() && locked())) {
            try { stopLockTask(); } catch (Exception ignored) { }
        }
    }

    private String launchPackage(Catalog.Item it) {
        if (it == null) return null;
        if (it.pkg != null) return it.pkg;
        if (it.storePackage() != null) return it.storePackage();
        if (it.action != null && (it.action.startsWith("roblox:") || "goto:roblox".equals(it.action) || it == Catalog.ROBLOX))
            return "com.roblox.client";
        return null;
    }

    /** If the child swipes to Recents or another app and pinning is off / was unpinned, try to come back (same path as Strict). */
    private void maybeBounceKidsLeave() {
        if (!prefs.getBoolean("kidsMode", false) || !prefs.getBoolean("kidsLock", false)) return;
        if (prefs.getBoolean("parentSettings", false)) return;   // 0.10.40: parent opened Settings after the PIN (adult mode should already have kids off)
        if (parentStoreActive()) return;   // 0.10.49: leaving for a PIN-approved Play Store install
        if (parentUpdateActive()) return;   // 0.10.49: leaving for a PIN-approved CodeAque APK update
        if (prefs.getString("kidsFgPkg", null) != null) return;   // leaving for an approved game
        if (pinDialogOpen) return;
        if (imeVisible()) return;   // 0.10.50: do not bounce the system keyboard
        final int gen = ++kidsBounceGen;
        view.postDelayed(() -> {
            if (gen != kidsBounceGen) return;
            if (!prefs.getBoolean("kidsMode", false) || !prefs.getBoolean("kidsLock", false)) return;
            if (prefs.getString("kidsFgPkg", null) != null) return;
            if (imeVisible()) return;
            if (hasWindowFocus() || inLockTask()) return;
            ScreenTimeService.bringKidsHome(this, false);
        }, 400);
    }
    private int kidsBounceGen;

    // ---------- 0.10.39 Exit CodeAque (adult Settings and the Power row, parent PIN) ----------
    /** PIN already asked. Stops lock-task if it is on, then opens Android's Home-app settings so a parent can pick the stock launcher.
     *  Does not change Always start in Kids mode — the next time CodeAque is opened it still starts in Kids mode. */
    private void exitCodeaque() {
        try {
            android.app.ActivityManager am = (android.app.ActivityManager) getSystemService(ACTIVITY_SERVICE);
            if (am != null && am.getLockTaskModeState() != android.app.ActivityManager.LOCK_TASK_MODE_NONE)
                stopLockTask();
        } catch (Exception ignored) { }
        if (deviceOwner()) {
            try {
                android.app.admin.DevicePolicyManager dpm = (android.app.admin.DevicePolicyManager) getSystemService(DEVICE_POLICY_SERVICE);
                dpm.clearPackagePersistentPreferredActivities(adminComponent(), getPackageName());
            } catch (Exception ignored) { }
        }
        toast("Pick another Home app and tap Always. The next time you open CodeAque it still starts in Kids mode.");
        if (!openHomeSettings()) toast("Could not open Home settings.");
    }
    private boolean openHomeSettings() {
        return openSystemSettings(new Intent(Settings.ACTION_HOME_SETTINGS), new Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS));
    }

    /** PIN already asked (or All Apps Settings in adult mode). Stops lock-task if it is on, allows the Settings package
     *  in the lock-task list for this session, then starts the system screen with FLAG_ACTIVITY_NEW_TASK.
     *  Does not bounce the parent back: Kids blocking of Settings only applies while Kids mode is on. */
    private boolean openSystemSettings(Intent primary, Intent fallback) {
        prefs.edit().putBoolean("parentSettings", true).apply();
        String pkg = resolveSettingsPackage(primary);
        if (pkg == null) pkg = resolveSettingsPackage(fallback);
        allowSettingsInLockTask(pkg);
        if (inLockTask()) {
            try { stopLockTask(); } catch (Exception ignored) { }
        }
        if (primary != null) {
            primary.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (tryStart(primary)) return true;
        }
        if (fallback != null) {
            fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (tryStart(fallback)) return true;
        }
        toast("Could not open Android settings.");
        return false;
    }
    private String resolveSettingsPackage(Intent i) {
        if (i == null) return null;
        try {
            android.content.pm.ResolveInfo r = getPackageManager().resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY);
            if (r != null && r.activityInfo != null) return r.activityInfo.packageName;
        } catch (Exception ignored) { }
        return null;
    }
    /** If we are the device owner, add the Settings package to the lock-task allow list for this adult session. Does not start lock-task. */
    private void allowSettingsInLockTask(String extraPkg) {
        if (!deviceOwner() || prefs.getBoolean("kidsMode", false)) return;
        try {
            android.app.admin.DevicePolicyManager dpm = (android.app.admin.DevicePolicyManager) getSystemService(DEVICE_POLICY_SERVICE);
            java.util.LinkedHashSet<String> allow = new java.util.LinkedHashSet<>();
            allow.add(getPackageName());
            allow.add("com.android.settings");
            allow.add("com.android.tv.settings");
            if (extraPkg != null && !extraPkg.isEmpty()) allow.add(extraPkg);
            for (ResolveInfo r : getPackageManager().queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)) {
                String p = r.activityInfo.packageName;
                if ((!LockPin.blocked(p) || Catalog.settingsPkg(p)) && !otherHome(p)) allow.add(p);
            }
            dpm.setLockTaskPackages(adminComponent(), allow.toArray(new String[0]));
        } catch (Exception ignored) { }
    }

    // ---------- 0.10.37 Lock phone to CodeAque (optional device owner, root or ADB) ----------
    /** PIN already asked. If su is present, try /system/bin/dpm set-device-owner (then cmd device_policy); otherwise show the ADB steps. */
    private void lockPhone() {
        if (deviceOwner()) {
            syncDeviceOwnerKiosk();
            toast(prefs.getBoolean("kidsMode", false)
                    ? "CodeAque is already the device owner. Kids mode is using the full kiosk lock."
                    : "CodeAque is already the device owner. Turn on Kids mode to lock the phone (no Recents swipe).");
            return;
        }
        if (hasSu()) {
            toast("Asking for root to set device owner. Allow the superuser prompt if it appears.");
            new Thread(() -> {
                SuRun.Batch b = suBatch(Catalog.DPM_SET_OWNER);
                if (!b.success() && !b.dpmFailed() && !deviceOwner()) {
                    SuRun.Batch b2 = suBatch(Catalog.DPM_SET_OWNER_FALLBACK);
                    for (SuRun.Attempt a : b2.attempts) b.add(a);
                }
                final SuRun.Batch batch = b;
                runOnUiThread(() -> {
                    if (deviceOwner() || batch.success()) {
                        syncDeviceOwnerKiosk();
                        toast("Device owner is on. In Kids mode the phone stays on CodeAque (no Recents swipe). Remove it later with Settings > Remove device owner.");
                    } else showDeviceOwnerFailed(batch);
                });
            }).start();
        } else showAdbKioskGuide("CodeAque needs a one-time setup to fully lock the phone. This handheld does not have root (su), so the steps use a computer and adb.");
    }

    private void showDeviceOwnerFailed(SuRun.Batch batch) {
        dlg().setTitle("Could not lock the phone")
                .setMessage(SuRun.dialogMessage(batch))
                .setPositiveButton("Show ADB steps", (d, w) -> showAdbKioskGuide(null))
                .setNegativeButton("OK", null).show();
    }

    /** Short on-phone copy of docs/android-kiosk-setup.md (USB debugging + adb from Linux Mint). */
    private void showAdbKioskGuide(String lead) {
        String steps = (lead == null ? "" : lead + "\n\n")
                + "This only works on a freshly reset phone with no Google account added yet.\n\n"
                + "1. On the phone: Settings > About, tap Build number seven times to open developer options. Turn on USB debugging.\n"
                + "2. On the Dell (Linux Mint), open a terminal and install adb if needed:\n"
                + "   sudo apt install adb\n"
                + "3. Plug the phone in with a data USB cable. If the phone asks, allow this computer.\n"
                + "4. In the same terminal run:\n"
                + "   adb devices\n"
                + "   (you should see the phone listed). Then:\n"
                + "   adb shell " + Catalog.DPM_SET_OWNER_ADB + "\n"
                + "5. You should see a success message. Open CodeAque, turn on Kids mode, and the phone stays on CodeAque (no Recents swipe).\n\n"
                + "To undo later: in CodeAque Settings (parent PIN) choose Remove device owner. Do not skip this if you will sell or give the phone away.";
        android.widget.TextView tv = new android.widget.TextView(this);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        tv.setPadding(pad, pad, pad, pad);
        tv.setTextIsSelectable(true);
        tv.setText(steps);
        tv.setTextSize(15);
        android.widget.ScrollView sc = new android.widget.ScrollView(this);
        sc.addView(tv);
        dlg().setTitle("Lock phone to CodeAque")
                .setView(sc)
                .setPositiveButton("OK", null)
                .show();
    }

    // ---------- 0.10.2 Kids mode ----------
    /** 0.10.54: enter Kids / Kids 12+ (last-used profile) from the home tile or Settings, with no PIN and no extra question. Leaving still needs the PIN. */
    private void enterKids() {
        leaveBoxSet();
        pinOkUntil = 0;   // a PIN from before Kids mode must not unlock Settings
        prefs.edit().putBoolean("kidsMode", true).remove("parentSettings").remove(ScreenTime.PARENT_STORE_UNTIL).remove(ScreenTime.PARENT_UPDATE_UNTIL).apply();
        timeUpScreen = false;
        parentStoreGen++;
        ScreenTimeService.sync(this); NovaOverlayService.retire(this); rescan();
        syncDeviceOwnerKiosk();
        syncKidsPinning();
        toast(kids12() ? "Kids 12+ is on. Leaving it needs the parent PIN." : "Kids mode is on. Leaving it needs the parent PIN.");
    }

    void aboutDialog() {
        String ver = "CodeAque " + myVersion() + (Catalog.updateAvailable ? " — an update is available." : ".");
        String licences = "Licences: Smarter Nova may use short extracts from Simple English Wikipedia (CC BY-SA). "
                + "Nova's optional on-device voice uses the Kokoro-82M model (Apache-2.0). "
                + "The optional on-device brain uses Google's Gemma 3 1B under the Gemma Terms of Use. "
                + "Those models are downloaded in Settings, not bundled in this app.";
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("About CodeAque")
                .setMessage(ver + "\n\n" + licences)
                .setPositiveButton("OK", null)
                .show();
    }

    // 0.10.54: Voice talk (RECORD_AUDIO). Friendly message first, then the system prompt.
    static final int REQ_MIC = 64;

    @Override public void onNovaExpandChanged(boolean expanded) { }

    @Override public boolean novaMicAllowed() {
        if (android.os.Build.VERSION.SDK_INT < 23) return true;
        return checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    @Override public void novaRequestMic() {
        if (novaMicAllowed()) {
            if (nova != null) nova.onMicPermission(true);
            return;
        }
        boolean asked = NovaMicPerm.asked(this);
        boolean rationale = false;
        if (android.os.Build.VERSION.SDK_INT >= 23) {
            rationale = shouldShowRequestPermissionRationale(android.Manifest.permission.RECORD_AUDIO);
        }
        android.app.AlertDialog.Builder b = new android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert);
        if (asked && !rationale) {
            b.setTitle(NovaSpeechAvail.PERMISSION_DENIED)
                    .setMessage("The microphone is off for CodeAque. Open Settings, tap Permissions, and allow the microphone, then tap Mic in Chat again.")
                    .setPositiveButton("Open Settings", new NovaMicOpenSettingsClick(this))
                    .setNegativeButton("Not now", new NovaMicNotNowClick(this))
                    .show();
            if (nova != null) nova.onPermissionDenied();
            return;
        }
        b.setTitle("Nova would like the mic")
                .setMessage("When Voice talk is on, Nova listens so you can ask out loud, without tapping a button each time. She pauses while she is speaking so she does not hear herself. The words stay on this phone.")
                .setPositiveButton("Allow", new NovaMicAllowClick(this))
                .setNegativeButton("Not now", new NovaMicNotNowClick(this))
                .show();
    }

    void onMicDialogDenied() {
        if (nova != null) nova.onMicPermission(false);
    }

    void onMicDialogOpenSettings() {
        if (nova != null) nova.onPermissionDenied();
    }

    @Override public boolean updateReady() { return Catalog.updateAvailable; }
    @Override public String updateReadyLabel() {
        return UpdateOffer.bannerLabel(prefs.getString(UpdateOffer.PREF_VER, ""));
    }
    @Override public void onUpdateInstall() {
        UpdateInfo u = UpdateOfferStore.load(prefs, myVersion());
        if (u == null) { checkUpdate(true); return; }
        new Thread(() -> install(u)).start();
    }

    static final int REQ_SPEECH = 65;

    @Override public void novaStartSpeechPrompt() {
        android.content.Intent i = NovaSpeech.recognizeIntent(false);
        try { startActivityForResult(i, REQ_SPEECH); }
        catch (Exception e) { novaOpenSpeechInstall(); }
    }

    @Override public void novaOpenSpeechInstall() {
        android.content.Intent i = new android.content.Intent(android.content.Intent.ACTION_VIEW,
                android.net.Uri.parse("market://details?id=" + NovaSpeechAvail.TTS_PACKAGE));
        try { startActivity(i); }
        catch (Exception e) {
            try {
                startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse("https://play.google.com/store/apps/details?id=" + NovaSpeechAvail.TTS_PACKAGE)));
            } catch (Exception e2) {
                toast(NovaSpeechAvail.NO_SERVICE);
            }
        }
    }

    private void kidsMode(boolean on) {
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(on ? "Turn on Kids mode?" : "Turn off Kids mode?")
                .setMessage(on ? "The home shows only Play, Roblox and Kids Games, with big tiles. Retro, game files, Ask and "
                        + "Settings are hidden. To get Settings back, use the Grown-ups row (parent PIN), or hold Select (or Y / Menu) for a second."
                        + (Rotation.suggest(android.os.Build.MANUFACTURER, android.os.Build.MODEL, android.os.Build.HARDWARE, android.os.Build.BOARD) ? " Recommended on this handheld." : "")
                        : "This brings back all rows and Settings.")
                .setPositiveButton(on ? "Turn on" : "Turn off", (d, w) -> {
                    android.content.SharedPreferences.Editor ed = prefs.edit().putBoolean("kidsMode", on);
                    if (on) ed.remove("parentSettings");
                    ed.remove(ScreenTime.PARENT_STORE_UNTIL).remove(ScreenTime.PARENT_UPDATE_UNTIL);
                    ed.apply();
                    parentStoreGen++;
                    if (!on) { timeUpScreen = false; stopKidsPinning(); }
                    if (deviceOwner()) { if (on || locked()) { syncDeviceOwnerKiosk(); } }
                    else if (!on) { /* not owner: screen pin already stopped */ }
                    ScreenTimeService.sync(this); NovaOverlayService.retire(this); rescan();
                    if (on) { syncDeviceOwnerKiosk(); syncKidsPinning(); toast(kids12() ? "Kids 12+ is on." : "Kids mode is on."); }
                    else syncDeviceOwnerKiosk();
                })
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

    // ---------- 0.10.1 / 0.10.42 CodeAque boot animation (Magisk or /data/local). UNTESTED ON A REAL DEVICE. ----------
    private boolean bootAnimAvailable() {
        boolean app;
        try { getPackageManager().getPackageInfo(BootAnim.MAGISK_PKG, 0); app = true; } catch (Exception e) { app = false; }
        return BootAnim.available(app, pth -> new java.io.File(pth).exists());
    }
    private boolean hasMagisk() {
        try { getPackageManager().getPackageInfo(BootAnim.MAGISK_PKG, 0); return true; } catch (Exception e) { }
        return BootAnim.magiskPresent(false, pth -> new java.io.File(pth).exists());
    }

    private void bootAnim() {
        if (!bootAnimAvailable()) { toast("The boot animation needs a rooted device. Nothing was changed."); return; }
        boolean magisk = hasMagisk();
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("CodeAque boot animation")
                .setMessage(magisk ? BootAnim.INFO_MAGISK : BootAnim.INFO_SAFE)
                .setPositiveButton("Download and install", (d, w) -> bootAnimDownload())
                .setNeutralButton("Restore original boot animation", (d, w) -> askPin("Restore original boot animation", () -> bootAnimRestore()))
                .setNegativeButton("Cancel", null).show();
    }

    /** Once per start: if CodeAque is not the Home app and the device is rooted, set it with su (Magisk asks once). */
    private boolean homeTried;
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
    /** PIN-gated: hide other Home apps. Device owner uses setApplicationHidden (no root).
     *  Otherwise /system/bin/pm disable-user via the su runner (any su, not only Magisk). */
    private void hideLaunchers() {
        askPin("Hide other launchers", () -> {
            java.util.Set<String> hidden = new java.util.HashSet<>(prefs.getStringSet("hiddenLaunchers", new java.util.HashSet<>()));
            java.util.List<String> others = otherLaunchers();
            StringBuilder m = new StringBuilder("Turns off the other Home apps so Android stops asking which one to use. Nothing is uninstalled.\n\n");
            m.append(others.isEmpty() ? "No other launchers are on right now." : "Will turn off:\n" + String.join("\n", others));
            if (!hidden.isEmpty()) m.append("\n\nHidden by CodeAque:\n").append(String.join("\n", hidden));
            if (!deviceOwner() && !hasSu())
                m.append("\n\nThis phone is not rooted and CodeAque is not the device owner, so Hide them will not work. Settings > Lock phone to CodeAque can set device owner (root or adb), or use a rooted phone.");
            AlertDialog.Builder b = dlg().setTitle("Hide other launchers").setMessage(m.toString()).setNegativeButton("Cancel", null);
            if (!others.isEmpty()) b.setPositiveButton("Hide them", (d, w) -> new Thread(() -> {
                java.util.Set<String> h = new java.util.HashSet<>(hidden); int n = 0;
                boolean owner = deviceOwner();
                for (String pkg : others) {
                    if (!safePkg(pkg)) continue;
                    if (owner && setLauncherHidden(pkg, true)) { h.add(pkg); n++; }
                    else if (su("/system/bin/pm disable-user --user 0 " + pkg, null) == 0) { h.add(pkg); n++; }
                }
                prefs.edit().putStringSet("hiddenLaunchers", h).apply();
                if (n > 0) toast("Hid " + n + " launcher(s). Undo is in the same menu.");
                else toast(hideLaunchersUnavailable());
                makeHome(false);
            }).start());
            if (!hidden.isEmpty()) b.setNeutralButton("Undo (show them again)", (d, w) -> new Thread(() -> {
                java.util.Set<String> left = new java.util.HashSet<>();
                boolean owner = deviceOwner();
                for (String pkg : hidden) {
                    if (!safePkg(pkg)) { left.add(pkg); continue; }
                    boolean ok = owner && setLauncherHidden(pkg, false);
                    if (!ok) ok = su("/system/bin/pm enable " + pkg, null) == 0;
                    if (!ok) left.add(pkg);
                }
                prefs.edit().putStringSet("hiddenLaunchers", left).apply();
                toast(left.isEmpty() ? "The other launchers are back." : "Could not turn some back on: " + String.join(", ", left));
            }).start());
            b.show();
        });
    }
    private static boolean safePkg(String pkg) {
        if (pkg == null || pkg.isEmpty()) return false;
        for (int i = 0; i < pkg.length(); i++) {
            char c = pkg.charAt(i);
            if (!(c >= 'a' && c <= 'z') && !(c >= 'A' && c <= 'Z') && !(c >= '0' && c <= '9') && c != '.' && c != '_') return false;
        }
        return true;
    }
    /** Device-owner hide (no root). False if we are not the owner or the call failed. */
    private boolean setLauncherHidden(String pkg, boolean hidden) {
        if (!deviceOwner()) return false;
        try {
            android.app.admin.DevicePolicyManager dpm = (android.app.admin.DevicePolicyManager) getSystemService(DEVICE_POLICY_SERVICE);
            dpm.setApplicationHidden(adminComponent(), pkg, hidden);
            return dpm.isApplicationHidden(adminComponent(), pkg) == hidden;
        } catch (Exception e) { return false; }
    }
    private String hideLaunchersUnavailable() {
        if (deviceOwner()) return "CodeAque is the device owner, but hiding the other launchers still failed. Nothing was changed.";
        if (hasSu() || su("id", null) == 0)
            return "Root (su) is present, but the hide command did not work. Nothing was changed. This does not need Magisk.";
        return "Hiding other launchers needs CodeAque as the device owner, or a rooted phone (the su program, in /system/xbin/su or similar). This phone has neither, so nothing was changed.";
    }

    /** Known su locations. Includes the K56 path (/system/xbin/su) and is not limited to Magisk. */
    static final String[] SU_PATHS = {
            "su", "/system/xbin/su", "/system/bin/su", "/sbin/su", "/su/bin/su", "/debug_ramdisk/su"
    };
    /** Quick check: an su binary exists on a known path or on PATH. Does not need to be Magisk, and does not require canExecute (often false for apps). File check only — no process — so it is safe on the UI thread. */
    private boolean hasSu() {
        for (String p : SU_PATHS) {
            if ("su".equals(p)) continue;
            if (new java.io.File(p).exists()) return true;
        }
        String path = System.getenv("PATH");
        if (path != null) {
            for (String dir : path.split(":")) {
                if (dir == null || dir.isEmpty()) continue;
                if (new java.io.File(dir, "su").exists()) return true;
            }
        }
        return false;
    }
    /** Every su we might run: known paths, PATH, and `command -v su` / `which su`. */
    private java.util.List<String> suBinaries() {
        java.util.LinkedHashSet<String> bins = new java.util.LinkedHashSet<>();
        for (String p : SU_PATHS) bins.add(p);
        String path = System.getenv("PATH");
        if (path != null) {
            for (String dir : path.split(":")) {
                if (dir == null || dir.isEmpty()) continue;
                java.io.File f = new java.io.File(dir, "su");
                if (f.exists()) bins.add(f.getAbsolutePath());
            }
        }
        try {
            Process w = new ProcessBuilder("sh", "-c", "command -v su || which su").redirectErrorStream(true).start();
            try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(w.getInputStream()))) {
                String l = r.readLine();
                if (l != null) {
                    l = l.trim();
                    if (!l.isEmpty() && new java.io.File(l).exists()) bins.add(l);
                }
            }
            w.waitFor();
        } catch (Exception ignored) { }
        return new java.util.ArrayList<>(bins);
    }
    /** Runs one command as root through every su binary and every SuRun form (stdin first).
     *  "failed to exec" / "No such file" / "not found" means try the next form, never a final success.
     *  Returns 0 only when the command actually succeeded ({@code __RC=0} or exit 0 with no exec miss). */
    private int su(String cmd, StringBuilder out) {
        SuRun.Batch b = suBatch(cmd);
        if (out != null) {
            if (b.decisive != null) out.append(b.decisive.output);
            else out.append(b.formList());
        }
        return b.success() ? 0 : b.rc;
    }
    /** Try stdin first, then the sh -c and split-args forms. Stops early on real success or a real dpm refusal. */
    private SuRun.Batch suBatch(String cmd) {
        SuRun.Batch batch = new SuRun.Batch();
        for (String bin : suBinaries()) {
            if (!"su".equals(bin) && !new java.io.File(bin).exists()) continue;
            for (String form : SuRun.FORM_IDS) {
                StringBuilder chunk = new StringBuilder();
                int wait = suRead(new ProcessBuilder(SuRun.argv(form, bin, cmd)),
                        SuRun.usesStdin(form) ? SuRun.stdinScript(cmd) : null, chunk);
                SuRun.Attempt a = new SuRun.Attempt(bin, form, chunk.toString(), wait);
                batch.add(a);
                if (!a.shouldRetry()) return batch;
            }
        }
        return batch;
    }
    /** Start argv, optionally write stdin (then close it), read stdout and stderr (merged) into out, return waitFor(). */
    private int suRead(ProcessBuilder pb, String stdin, StringBuilder out) {
        try {
            Process p = pb.redirectErrorStream(true).start();
            try (java.io.OutputStream w = p.getOutputStream()) {
                if (stdin != null) w.write(stdin.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream()))) {
                String l; while ((l = r.readLine()) != null) if (out != null && out.length() < 120000) out.append(l).append('\n');
            }
            return p.waitFor();
        } catch (Exception ignored) { return -1; }
    }

    private void bootAnimDownload() {
        final android.app.ProgressDialog prog = new android.app.ProgressDialog(this);
        prog.setMessage("Downloading …");
        prog.setProgressStyle(android.app.ProgressDialog.STYLE_HORIZONTAL);
        prog.setCancelable(true);
        final boolean[] cancelled = { false };
        prog.setOnCancelListener(d -> cancelled[0] = true);
        prog.show();
        new Thread(() -> {
            java.io.File file = null;
            String err = null;
            try {
                android.net.Uri uri = null;
                if (android.os.Build.VERSION.SDK_INT >= 29) {
                    try {
                        getContentResolver().delete(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                                android.provider.MediaStore.MediaColumns.DISPLAY_NAME + "=?", new String[]{BootAnim.FILE});
                    } catch (Exception ignored) { }
                    android.content.ContentValues v = new android.content.ContentValues();
                    v.put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, BootAnim.FILE);
                    v.put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "application/zip");
                    uri = getContentResolver().insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
                }
                java.io.OutputStream out;
                if (uri != null) out = getContentResolver().openOutputStream(uri);
                else {
                    java.io.File dir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS);
                    dir.mkdirs();
                    file = new java.io.File(dir, BootAnim.FILE);
                    out = new java.io.FileOutputStream(file);
                }
                java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(BootAnim.URL).openConnection();
                c.setConnectTimeout(15000); c.setReadTimeout(30000);
                java.io.InputStream in = c.getInputStream();
                java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
                byte[] buf = new byte[8192];
                int n, total = 0, cl = c.getContentLength();
                if (cl > 0) runOnUiThread(() -> prog.setMax(cl));
                while ((n = in.read(buf)) > 0) {
                    if (cancelled[0]) { in.close(); out.close(); return; }
                    total += n;
                    if (total > BootAnim.MAX_BYTES) throw new java.io.IOException("too big");
                    md.update(buf, 0, n);
                    out.write(buf, 0, n);
                    int soFar = total;
                    runOnUiThread(() -> prog.setProgress(soFar));
                }
                in.close(); out.close();
                if (!BootAnim.matches(md.digest())) {
                    err = "The download did not match the official file. Nothing was installed.";
                } else {
                    String path = file != null ? file.getAbsolutePath() : bootAnimQueryPath();
                    java.io.File anim = new java.io.File(getFilesDir(), BootAnim.ANIM_FILE);
                    String ex = null;
                    if (file != null) ex = BootAnim.extractAnimation(file, anim);
                    else if (path != null && BootAnim.safePath(path)) ex = BootAnim.extractAnimation(new java.io.File(path), anim);
                    else {
                        // MediaStore path may not be a plain file; write a temp copy from the module bytes already saved.
                        java.io.File tmp = new java.io.File(getCacheDir(), BootAnim.FILE);
                        if (uri != null) {
                            java.io.InputStream cin = getContentResolver().openInputStream(uri);
                            java.io.FileOutputStream fout = new java.io.FileOutputStream(tmp);
                            byte[] b2 = new byte[8192]; int k;
                            while (cin != null && (k = cin.read(b2)) > 0) fout.write(b2, 0, k);
                            if (cin != null) cin.close(); fout.close();
                            ex = BootAnim.extractAnimation(tmp, anim);
                        }
                    }
                    if (ex != null) err = ex;
                    else {
                        pendingAnimPath = anim.getAbsolutePath();
                        final String saved = path != null ? path : BootAnim.FILE;
                        runOnUiThread(() -> { prog.dismiss(); bootAnimReady(saved, pendingAnimPath); });
                        return;
                    }
                }
            } catch (Exception e) {
                err = "The download failed. Nothing was installed.";
            }
            final String fail = err;
            runOnUiThread(() -> { try { prog.dismiss(); } catch (Exception ignored) { } showBootAnimError(fail, fail); });
        }).start();
    }

    private String bootAnimQueryPath() {
        try {
            android.database.Cursor cur = getContentResolver().query(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    new String[]{android.provider.MediaStore.MediaColumns.DATA},
                    android.provider.MediaStore.MediaColumns.DISPLAY_NAME + "=?", new String[]{BootAnim.FILE}, null);
            if (cur != null && cur.moveToFirst()) { String pth = cur.getString(0); cur.close(); return pth; }
            if (cur != null) cur.close();
        } catch (Exception ignored) { }
        return null;
    }

    private void bootAnimReady(String modulePath, String animPath) {
        boolean magisk = hasMagisk();
        AlertDialog.Builder b = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Ready to install")
                .setMessage(magisk
                        ? "Checked and saved. Install now uses Magisk (systemless). Or use the safe /data/local copy. An advanced system write needs a separate confirm."
                        : "Checked and saved. The safe install copies CodeAque's own animation to /data/local (the system folder is not rewritten). An optional advanced step can write to the system folder after a verified backup.")
                .setPositiveButton(magisk ? "Install with Magisk" : "Install safely", (d, w) -> {
                    if (magisk && BootAnim.safePath(modulePath)) bootAnimInstallMagisk(modulePath);
                    else bootAnimInstallSafe(animPath);
                })
                .setNeutralButton("Advanced: write to system…", (d, w) -> bootAnimAdvanced(animPath))
                .setNegativeButton("Close", null);
        if (magisk) b.setMessage("Checked and saved to " + modulePath + ".\n\nInstall with Magisk (systemless), or install safely to /data/local. Advanced write to the system folder needs a separate confirm and can stop the phone booting.")
                .setPositiveButton("Install with Magisk", (d, w) -> bootAnimInstallMagisk(modulePath));
        b.show();
        // Offer the safe path on Magisk too via a follow-up isn't possible with 3 buttons; include it in Advanced? Add a second dialog button set by showing a choice if magisk.
        if (magisk) {
            // The positive is Magisk; add safe as a toast hint in the message (already). User can Restore or re-open. Extra: after Magisk dialog, we already mentioned /data/local.
        }
    }

    private void bootAnimAdvanced(String animPath) {
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Write to the system folder?")
                .setMessage(BootAnim.ADVANCED_WARN)
                .setPositiveButton("I understand — continue", (d, w) -> askPin("Write boot animation to system", () -> bootAnimInstallSystem(animPath)))
                .setNegativeButton("Cancel", null).show();
    }

    private void bootAnimInstallMagisk(String path) {
        if (!BootAnim.safePath(path)) { openMagisk(); return; }
        toast("Installing … allow the Magisk prompt if it appears.");
        new Thread(() -> {
            StringBuilder log = new StringBuilder();
            int r = su(BootAnim.installCmd(path), log);
            runOnUiThread(() -> {
                if (r == 0) restartPrompt("Boot animation installed. It shows from the next restart.");
                else showBootAnimError(BootAnim.shortError(log.toString(), r), log.toString());
            });
        }).start();
    }

    private void bootAnimInstallSafe(String animPath) {
        if (!BootAnim.safePath(animPath)) { showBootAnimError("The animation file is not in a usable place.", animPath); return; }
        toast("Installing the safe /data/local copy…");
        new Thread(() -> {
            StringBuilder log = new StringBuilder();
            int r = su(BootAnim.dataLocalInstallCmd(animPath), log);
            runOnUiThread(() -> {
                if (r == 0) restartPrompt("Boot animation installed to /data/local. It shows from the next restart if this phone reads that file.");
                else showBootAnimError(BootAnim.shortError(log.toString(), r), log.toString());
            });
        }).start();
    }

    private void bootAnimInstallSystem(String animPath) {
        if (!BootAnim.safePath(animPath)) { showBootAnimError("The animation file is not in a usable place.", animPath); return; }
        toast("Making a backup, then writing the animation…");
        new Thread(() -> {
            StringBuilder log = new StringBuilder();
            int r = su(BootAnim.systemInstallCmd(animPath), log);
            runOnUiThread(() -> {
                if (r == 0) restartPrompt("Boot animation written to the system folder. A backup is in Download/codeaque-bootanim-backup. It shows from the next restart.");
                else showBootAnimError(BootAnim.shortError(log.toString(), r), log.toString());
            });
        }).start();
    }

    private void bootAnimRestore() {
        new Thread(() -> {
            StringBuilder log = new StringBuilder();
            int r = su(BootAnim.restoreCmd(), log);
            runOnUiThread(() -> {
                if (r == 0) restartPrompt("The original boot animation will be restored at the next restart.");
                else showBootAnimError(BootAnim.shortError(log.toString(), r), log.toString());
            });
        }).start();
    }

    private void bootAnimRemove() { bootAnimRestore(); }

    private void showBootAnimError(String shortMsg, String details) {
        if (shortMsg == null || shortMsg.isEmpty()) shortMsg = "The boot animation could not be installed.";
        final String det = details == null ? "" : details.trim();
        AlertDialog.Builder b = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Not installed")
                .setMessage(shortMsg)
                .setNegativeButton("Close", null);
        if (det.length() > 0 && !det.equals(shortMsg))
            b.setNeutralButton("Details", (d, w) -> new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                    .setTitle("Details")
                    .setMessage(det)
                    .setNegativeButton("Close", null).show());
        if (hasMagisk()) b.setPositiveButton("Open Magisk", (d, w) -> openMagisk());
        b.show();
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
        // 0.10.59: Later / Back only closes the dialog. The Install bar stays until the
        // installed version is no longer older. No timeout and no snooze hide.
        popupShowing = true;
        String news = UpdateInfo.whatsNew(u.notes);
        AlertDialog d = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("New update ready (v" + u.version + ")")
                .setMessage((news.isEmpty() ? "" : news + "\n\n") + "You have " + mine + ".")
                .setPositiveButton("Update now", (x, w) -> new Thread(() -> install(u)).start())
                .setNegativeButton("Later", null)
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
        if (req == REQ_SPEECH) {
            String said = null;
            if (res == RESULT_OK && data != null) {
                java.util.ArrayList<String> list = data.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS);
                if (list != null && !list.isEmpty()) said = list.get(0);
            }
            if (nova != null) nova.onSpeechPromptResult(said);
            return;
        }
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

    // ---------- 0.10.37 Kids profile, screen time, Strict mode ----------
    private boolean kids12() { return "kids12".equals(prefs.getString("kidsProfile", "kids")); }

    private ScreenTime.State stLoad() { return ScreenTimeService.load(prefs, System.currentTimeMillis()); }
    private void stSave(ScreenTime.State s) { ScreenTimeService.save(prefs, s); }
    private ScreenTime.Hours stHours() { return ScreenTimeService.loadHours(prefs); }
    private void stSaveHours(ScreenTime.Hours h) { ScreenTimeService.saveHours(prefs, h); }
    private boolean playBlockedNow() {
        long now = System.currentTimeMillis();
        java.util.TimeZone tz = java.util.TimeZone.getDefault();
        return ScreenTime.playBlocked(stLoad(), stHours(), now, tz);
    }
    private boolean stTimeUp() { return playBlockedNow(); }

    @Override public String kidsBanner() {
        if (!prefs.getBoolean("kidsMode", false)) return "";
        String who = kids12() ? "Kids 12+" : "Kids";
        long now = System.currentTimeMillis();
        java.util.TimeZone tz = java.util.TimeZone.getDefault();
        ScreenTime.State s = stLoad();
        ScreenTime.Hours h = stHours();
        if (ScreenTime.outsideHours(h, s, now, tz) && !ScreenTime.timeUp(s, now, tz))
            return who + "  ·  back at " + ScreenTime.backAtLabel(h, now, tz);
        String time = ScreenTime.label(s, now, tz);
        if (h.enabled && ScreenTime.inHours(h, now, tz) && !s.unlocked)
            time = (time.isEmpty() ? "" : time + "  ·  ") + "until " + ScreenTime.formatClock(ScreenTime.windowEnd(h, now, tz));
        return time.isEmpty() ? who : who + "  ·  " + time;
    }
    @Override public boolean timeUpTile(Catalog.Item it) {
        return prefs.getBoolean("kidsMode", false) && ScreenTime.isPlayLaunch(it) && stTimeUp();
    }
    @Override public boolean timeUpScreen() { return prefs.getBoolean("kidsMode", false) && (timeUpScreen || playBlockedNow()); }
    @Override public void timeUpAskParent() { askPin("Parent PIN: time's up", () -> screenTimeExtend(null)); }
    @Override public String timeUpHeadline() {
        long now = System.currentTimeMillis();
        java.util.TimeZone tz = java.util.TimeZone.getDefault();
        ScreenTime.State s = stLoad();
        if (ScreenTime.outsideHours(stHours(), s, now, tz) && !ScreenTime.timeUp(s, now, tz)) return "PLAY TIME IS OVER";
        return "TIME'S UP";
    }
    @Override public String timeUpMessage() {
        long now = System.currentTimeMillis();
        java.util.TimeZone tz = java.util.TimeZone.getDefault();
        ScreenTime.State s = stLoad();
        ScreenTime.Hours h = stHours();
        if (ScreenTime.outsideHours(h, s, now, tz) && !ScreenTime.timeUp(s, now, tz))
            return "Play time is over – back at " + ScreenTime.backAtLabel(h, now, tz);
        return "Play time is used up for today.";
    }
    @Override public boolean pinDialogOpen() { return pinDialogOpen; }
    @Override public boolean browseLayout() {
        if (prefs.getBoolean("kidsMode", false)) return true;
        return !prefs.getBoolean("classicHome", false);   // 0.10.43: default is kids-style (classicHome false)
    }
    @Override public String itemBlurb(Catalog.Item it) { return KidsPlay.blurb(it); }
    @Override public void kidsBrowseMore(String catId) {
        String cat = KidsPlay.catFromId(catId);
        if (cat == null) return;
        boolean kids = prefs.getBoolean("kidsMode", false);
        int max = kids ? KidsPlay.maxPegi(kids12()) : KidsPlay.ADULT;
        int at = kidsBrowseAt.containsKey(catId) ? kidsBrowseAt.get(catId) : KidsPlay.PAGE;
        java.util.List<Catalog.Item> more = KidsPlay.items(cat, max, at, KidsPlay.PAGE);
        if (more.isEmpty()) return;
        kidsBrowseAt.put(catId, at + more.size());
        java.util.List<Catalog.Item> cur = new java.util.ArrayList<>();
        // Append by rebuilding the visible prefix plus more (replaceRow swaps the whole row).
        java.util.List<Catalog.Item> have = KidsPlay.items(cat, max, 0, at);
        have.addAll(more);
        view.replaceRow(catId, have);
    }
    @Override public void installPicked(java.util.List<String> pkgs) {
        if (pkgs == null || pkgs.isEmpty()) return;
        final java.util.ArrayList<String> copy = new java.util.ArrayList<>(pkgs);
        askPin("Parent PIN: install games", () -> {
            startStoreQueue(copy);
            if (view != null) view.clearPicks();
        });
    }

    @Override public String kidsSearchStatus() { return searchStatus == null ? "" : searchStatus; }

    @Override public void kidsSearchRetry() {
        searchPlayFailed = false;
        if (searchChip != null && !searchChip.isEmpty() && (searchQuery == null || searchQuery.trim().isEmpty()))
            runKidsSearchChip(searchChip);
        else runKidsSearch(searchQuery);
    }

    @Override public String kidsSearchChip() { return searchChip == null ? "" : searchChip; }

    /** 0.10.49: search Play (and the bundled catalogue) inside CodeAque; never opens Play search. */
    @Override public void runKidsSearch(String query) {
        if (!prefs.getBoolean("kidsMode", false)) return;
        searchChip = "";
        searchMoreOffset = 0;
        searchQuery = query == null ? "" : query;
        final String q = searchQuery;
        final int max = KidsPlay.maxPegi(kids12());
        final int gen = ++searchGen;
        final java.util.List<Catalog.Item> installed = kidsInstalledMatches(q);
        if (q.trim().isEmpty()) {
            lastSearchItems.clear();
            lastSearchItems.addAll(installed);
            searchStatus = "";
            searchPlayFailed = false;
            if (view != null) view.replaceRow("ksearch", lastSearchItems);
            return;
        }
        searchStatus = "Searching…";
        // Show installed + catalogue matches immediately, then fill in live Play hits.
        java.util.List<Catalog.Item> quick = PlaySearch.merge(installed, searchCache.get(PlaySearch.norm(q)), KidsPlay.searchItems(q, max));
        lastSearchItems.clear();
        lastSearchItems.addAll(quick);
        if (view != null) { view.replaceRow("ksearch", lastSearchItems); view.invalidate(); }
        new Thread(() -> {
            java.util.List<PlaySearch.Hit> play = null;
            boolean failed = false;
            try { play = PlaySearch.fetch(PlayArt.PLAY, q, max); }
            catch (Exception e) { failed = true; }
            if (play != null) {
                String key = PlaySearch.norm(q);
                searchCache.put(key, play);
                while (searchCache.size() > 20) searchCache.remove(searchCache.keySet().iterator().next());
            } else if (searchCache.containsKey(PlaySearch.norm(q))) {
                play = searchCache.get(PlaySearch.norm(q));
            }
            final java.util.List<PlaySearch.Hit> hits = play;
            final boolean netFail = failed && (hits == null || hits.isEmpty());
            final boolean playFail = failed;
            runOnUiThread(() -> {
                if (gen != searchGen) return;
                java.util.List<Catalog.Item> merged = PlaySearch.merge(kidsInstalledMatches(q), hits, KidsPlay.searchItems(q, max));
                lastSearchItems.clear();
                lastSearchItems.addAll(merged);
                searchPlayFailed = playFail;
                if (netFail && merged.isEmpty()) searchStatus = "Couldn't load more games, tap to retry";
                else if (playFail) searchStatus = "Couldn't load more games, tap to retry";
                else if (merged.isEmpty()) searchStatus = "No games found.";
                else searchStatus = "";
                if (view != null) { view.replaceRow("ksearch", lastSearchItems); view.invalidate(); }
            });
        }, "kids-search").start();
    }

    /** Installed games allowed in the current Kids / Kids 12+ profile, matching the query (empty query = all of them). */
    private java.util.List<Catalog.Item> kidsInstalledMatches(String query) {
        int max = KidsPlay.maxPegi(kids12());
        java.util.Set<String> approved = prefs.getStringSet("kidsApproved", java.util.Collections.<String>emptySet());
        java.util.Set<String> added = prefs.getStringSet("kidsAdded", java.util.Collections.<String>emptySet());
        java.util.ArrayList<Catalog.Item> out = new java.util.ArrayList<>();
        java.util.LinkedHashSet<String> seen = new java.util.LinkedHashSet<>();
        for (Catalog.Item a : installedLaunchers) {
            if (a == null) continue;
            String pkg = a.storePackage() != null ? a.storePackage() : a.pkg;
            if (pkg == null || !pkgInstalled(pkg)) continue;
            if (ScreenTime.blockedKidPackage(pkg) || Catalog.emulatorPkg(pkg)) continue;
            KidsPlay.Game g = KidsPlay.byPkg(pkg);
            boolean ok;
            if (g != null) ok = KidsPlay.allowed(g.pegi, max);
            else ok = approved.contains(pkg) || added.contains(pkg);
            if (!ok) continue;
            if (!PlaySearch.matches(query, a.label, pkg)) continue;
            if (!seen.add(pkg)) continue;
            out.add(g != null ? g.item() : a);
        }
        return out;
    }

    private java.util.List<Catalog.Item> kidsInstalledChipMatches(String label) {
        String cat = KidsPlay.chipCat(label);
        String q = KidsPlay.chipQuery(label);
        java.util.List<Catalog.Item> all = kidsInstalledMatches("");
        java.util.ArrayList<Catalog.Item> out = new java.util.ArrayList<>();
        for (Catalog.Item a : all) {
            if (a == null) continue;
            String pkg = a.storePackage() != null ? a.storePackage() : a.pkg;
            KidsPlay.Game g = pkg == null ? null : KidsPlay.byPkg(pkg);
            boolean ok = cat != null && g != null && cat.equals(g.cat);
            if (!ok) ok = PlaySearch.matches(q, a.label, pkg);
            if (ok) out.add(a);
        }
        return out;
    }

    @Override public void runKidsSearchChip(String chip) {
        if (!prefs.getBoolean("kidsMode", false)) return;
        searchChip = chip == null ? "" : chip;
        searchQuery = "";
        searchWatchLock = true;
        if (searchField != null) searchField.setText("");
        searchWatchLock = false;
        if (searchChip.isEmpty()) { runKidsSearch(""); return; }
        final String label = searchChip;
        final String q = KidsPlay.chipQuery(label);
        final int max = KidsPlay.maxPegi(kids12());
        final int gen = ++searchGen;
        final java.util.List<Catalog.Item> installed = kidsInstalledChipMatches(label);
        java.util.List<Catalog.Item> catalog = KidsPlay.chipItems(label, max, 0, KidsPlay.PAGE);
        searchMoreOffset = catalog.size();
        searchStatus = "Searching…";
        java.util.List<Catalog.Item> quick = PlaySearch.merge(installed, searchCache.get(PlaySearch.norm(q)), catalog);
        lastSearchItems.clear();
        lastSearchItems.addAll(quick);
        if (view != null) { view.replaceRow("ksearch", lastSearchItems); view.invalidate(); }
        new Thread(() -> {
            java.util.List<PlaySearch.Hit> play = null;
            boolean failed = false;
            try { play = PlaySearch.fetch(PlayArt.PLAY, q, max); }
            catch (Exception e) { failed = true; }
            if (play != null) {
                String key = PlaySearch.norm(q);
                searchCache.put(key, play);
                while (searchCache.size() > 20) searchCache.remove(searchCache.keySet().iterator().next());
            } else if (searchCache.containsKey(PlaySearch.norm(q))) {
                play = searchCache.get(PlaySearch.norm(q));
            }
            final java.util.List<PlaySearch.Hit> hits = play;
            final boolean playFail = failed;
            runOnUiThread(() -> {
                if (gen != searchGen) return;
                java.util.List<Catalog.Item> merged = PlaySearch.merge(kidsInstalledChipMatches(label), hits,
                        KidsPlay.chipItems(label, max, 0, KidsPlay.PAGE));
                if (merged.isEmpty() && !lastSearchItems.isEmpty()) merged = new java.util.ArrayList<>(lastSearchItems);
                lastSearchItems.clear();
                lastSearchItems.addAll(merged);
                searchPlayFailed = playFail;
                if (playFail) searchStatus = "Couldn't load more games, tap to retry";
                else if (merged.isEmpty()) searchStatus = "Couldn't load more games, tap to retry";
                else searchStatus = "";
                if (view != null) { view.replaceRow("ksearch", lastSearchItems); view.invalidate(); }
            });
        }, "kids-search-chip").start();
    }

    @Override public void kidsSearchMore() {
        if (!prefs.getBoolean("kidsMode", false)) return;
        if (searchChip == null || searchChip.isEmpty()) return;
        final int max = KidsPlay.maxPegi(kids12());
        java.util.List<Catalog.Item> more = KidsPlay.chipItems(searchChip, max, searchMoreOffset, KidsPlay.PAGE);
        if (more.isEmpty()) {
            if (searchPlayFailed) searchStatus = "Couldn't load more games, tap to retry";
            if (view != null) view.invalidate();
            return;
        }
        searchMoreOffset += more.size();
        java.util.LinkedHashSet<String> seen = new java.util.LinkedHashSet<>();
        for (Catalog.Item it : lastSearchItems) if (it != null && it.storePackage() != null) seen.add(it.storePackage());
        for (Catalog.Item it : more) {
            String pkg = it == null ? null : it.storePackage();
            if (pkg == null || !seen.add(pkg)) continue;
            lastSearchItems.add(it);
        }
        if (view != null) { view.replaceRow("ksearch", lastSearchItems); view.invalidate(); }
    }

    private android.widget.EditText buildKidsSearchField() {
        android.widget.EditText e = new android.widget.EditText(this);
        e.setVisibility(android.view.View.GONE);
        e.setBackgroundColor(0x00000000);
        e.setTextColor(0xFFFFFFFF);
        e.setHintTextColor(0x88FFFFFF);
        e.setSingleLine(true);
        e.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
                | android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI
                | android.view.inputmethod.EditorInfo.IME_FLAG_NO_FULLSCREEN);
        e.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_FILTER);
        e.setGravity(android.view.Gravity.CENTER_VERTICAL);
        e.setLayoutParams(new android.widget.FrameLayout.LayoutParams(1, 1));
        e.addTextChangedListener(new KidsSearchWatch(this));
        e.setOnEditorActionListener((v, actionId, ev) -> {
            boolean go = actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
                    || actionId == android.view.inputmethod.EditorInfo.IME_ACTION_GO
                    || actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE
                    || (ev != null && ev.getKeyCode() == android.view.KeyEvent.KEYCODE_ENTER
                        && ev.getAction() == android.view.KeyEvent.ACTION_DOWN);
            if (!go) return false;
            if (view != null) view.fireSearch();
            hideKidsIme();
            return true;
        });
        e.setOnKeyListener((v, keyCode, ev) -> {
            if (ev.getAction() != android.view.KeyEvent.ACTION_DOWN) return false;
            if (keyCode == android.view.KeyEvent.KEYCODE_BUTTON_A || keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER) {
                if (!imeVisible()) { requestKidsIme(); return true; }
                return false;
            }
            if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_UP || keyCode == android.view.KeyEvent.KEYCODE_DPAD_DOWN
                    || keyCode == android.view.KeyEvent.KEYCODE_DPAD_LEFT || keyCode == android.view.KeyEvent.KEYCODE_DPAD_RIGHT
                    || keyCode == android.view.KeyEvent.KEYCODE_BUTTON_B || keyCode == android.view.KeyEvent.KEYCODE_BACK) {
                return view != null && view.onKeyDown(keyCode, ev);
            }
            return false;
        });
        return e;
    }

    @Override public void placeKidsSearchField(float left, float top, float right, float bottom, String text, String hint, boolean focused, boolean showField, float textPx, float padH, float padV) {
        if (searchField == null) return;
        if (!showField) {
            if (searchField.getVisibility() != android.view.View.GONE) {
                final android.widget.EditText field = searchField;
                searchField.post(() -> {
                    field.setVisibility(android.view.View.GONE);
                    if (field.hasFocus() && view != null) view.requestFocus();
                });
            }
            return;
        }
        int l = Math.round(left), t = Math.round(top), r = Math.round(right), b = Math.round(bottom);
        int w = Math.max(1, r - l), h = Math.max(1, b - t);
        android.widget.FrameLayout.LayoutParams lp = (android.widget.FrameLayout.LayoutParams) searchField.getLayoutParams();
        if (lp == null) lp = new android.widget.FrameLayout.LayoutParams(w, h);
        final android.widget.EditText field = searchField;
        final android.widget.FrameLayout.LayoutParams flp = lp;
        final int fw = w, fh = h, fl = l, ft = t;
        final boolean needMove = lp.width != w || lp.height != h || lp.leftMargin != l || lp.topMargin != t;
        final boolean needShow = searchField.getVisibility() != android.view.View.VISIBLE;
        if (needMove || needShow) {
            searchField.post(() -> {
                if (field.getParent() == null) return;
                if (needMove) { flp.width = fw; flp.height = fh; flp.leftMargin = fl; flp.topMargin = ft; field.setLayoutParams(flp); }
                if (field.getVisibility() != android.view.View.VISIBLE) field.setVisibility(android.view.View.VISIBLE);
            });
        }
        if (hint != null && !hint.equals(String.valueOf(searchField.getHint()))) searchField.setHint(hint);
        String cur = searchField.getText() == null ? "" : searchField.getText().toString();
        if (text == null) text = "";
        if (!text.equals(cur) && !searchField.hasFocus()) {
            searchWatchLock = true;
            searchField.setText(text);
            searchField.setSelection(text.length());
            searchWatchLock = false;
        }
        searchField.setGravity(android.view.Gravity.CENTER_VERTICAL);
        searchField.setIncludeFontPadding(false);
        int ph = Math.max(4, Math.round(padH));
        int pv = Math.max(2, Math.round(padV));
        searchField.setPadding(ph, pv, ph, pv);
        float size = textPx > 0 ? textPx : Math.max(8, h * 0.42f);
        if (size * 1.25f > Math.max(1, h - 2 * pv)) size = Math.max(8, (h - 2 * pv) / 1.25f);
        searchField.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, size);
    }

    @Override public void requestKidsIme() {
        if (view == null || !view.onKidsSearch() || searchField == null) return;
        allowImeInLockTask();
        if (searchField.getVisibility() != android.view.View.VISIBLE) searchField.setVisibility(android.view.View.VISIBLE);
        searchField.requestFocus();
        android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.showSoftInput(searchField, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
        final int gen = ++imeFallbackGen;
        if (view != null) view.postDelayed(new KidsImeFallback(this, gen), 400);
    }

    @Override public void hideKidsIme() {
        imeFallbackGen++;
        ImeHide.hide(this);
        if (searchField != null) {
            ImeHide.hide(searchField);
            if (searchField.hasFocus() && view != null) view.requestFocus();
        }
    }

    String currentImePackage() {
        return ScreenTime.imePackage(Settings.Secure.getString(getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD));
    }

    private void allowImeInLockTask() {
        if (!deviceOwner() || !prefs.getBoolean("kidsMode", false)) return;
        try {
            android.app.admin.DevicePolicyManager dpm = (android.app.admin.DevicePolicyManager) getSystemService(DEVICE_POLICY_SERVICE);
            java.util.Set<String> allow = kidsLockTaskPackages();
            dpm.setLockTaskPackages(adminComponent(), allow.toArray(new String[0]));
        } catch (Exception ignored) { }
    }

    @Override public boolean imeVisible() {
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            android.view.WindowInsets insets = getWindow() != null && getWindow().getDecorView() != null
                    ? getWindow().getDecorView().getRootWindowInsets() : null;
            return insets != null && insets.isVisible(android.view.WindowInsets.Type.ime());
        }
        android.graphics.Rect r = new android.graphics.Rect();
        android.view.View decor = getWindow() != null ? getWindow().getDecorView() : null;
        if (decor == null) return false;
        decor.getWindowVisibleDisplayFrame(r);
        float th = 120 * getResources().getDisplayMetrics().density;
        return (decor.getHeight() - r.bottom) > th;
    }

    void onKidsImeFallback(int gen) {
        if (gen != imeFallbackGen) return;
        if (view == null || !view.onKidsSearch()) return;
        if (imeVisible()) view.setSearchImeFallback(false);
        else view.setSearchImeFallback(true);
    }

    void onKidsSearchField(String text) {
        if (searchWatchLock || view == null) return;
        view.setSearchQueryText(text, false);
    }

    private java.util.Set<String> allowedEmus() {
        String key = kids12() ? "kidsEmu12" : "kidsEmu";
        return new java.util.HashSet<>(prefs.getStringSet(key, java.util.Collections.<String>emptySet()));
    }
    private java.util.List<Catalog.Item> withRobloxApp(java.util.List<Catalog.Item> items) {
        java.util.ArrayList<Catalog.Item> out = new java.util.ArrayList<>();
        out.add(Catalog.ROBLOX);
        if (items != null) for (Catalog.Item it : items) if (it != Catalog.ROBLOX) out.add(it);
        return out;
    }

    private void handleKidsGuardIntent(Intent i) {
        if (i == null) return;
        if (i.getBooleanExtra("kidsTimeUp", false)) { timeUpScreen = true; i.removeExtra("kidsTimeUp"); }
        if (i.getBooleanExtra("kidsBounce", false)) i.removeExtra("kidsBounce");
    }

    private boolean kidsBlockedIntent(Intent i) {
        String pkg = i.getComponent() != null ? i.getComponent().getPackageName() : i.getPackage();
        if (pkg == null) {
            String a = i.getAction();
            if (a != null && (a.startsWith("android.settings.") || a.equals(Intent.ACTION_INSTALL_PACKAGE))) return true;
            try {
                ResolveInfo r = getPackageManager().resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY);
                if (r != null && r.activityInfo != null) pkg = r.activityInfo.packageName;
            } catch (Exception ignored) { }
        }
        return ScreenTime.blockedKidPackage(pkg) || Catalog.emulatorPkg(pkg);
    }

    private void onPlayLaunched(Catalog.Item it) {
        if (!prefs.getBoolean("kidsMode", false) || !ScreenTime.isPlayLaunch(it)) return;
        allowKidsForeground(launchPackage(it));
        ScreenTime.State s = ScreenTime.startPlay(stLoad(), System.currentTimeMillis(), java.util.TimeZone.getDefault());
        stSave(s);
        ScreenTimeService.sync(this);
    }

    private void pausePlayClock() {
        ScreenTime.State s = stLoad();
        if (s.startedAt <= 0) return;
        s = ScreenTime.pausePlay(s, System.currentTimeMillis(), java.util.TimeZone.getDefault());
        stSave(s);
        if (prefs.getBoolean("kidsMode", false) && s.strict && ScreenTime.timeUp(s, System.currentTimeMillis(), java.util.TimeZone.getDefault()))
            timeUpScreen = true;
        ScreenTimeService.sync(this);
    }

    private void screenTimeExtend(Catalog.Item openAfter) {
        timeUpScreen = false;
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Screen time")
                .setItems(new String[]{"Add 15 minutes", "Add 30 minutes", "Unlock for today", "Cancel"}, (d, w) -> {
                    long now = System.currentTimeMillis();
                    java.util.TimeZone tz = java.util.TimeZone.getDefault();
                    ScreenTime.State s = stLoad();
                    ScreenTime.Hours h = stHours();
                    if (w == 0) { s = ScreenTime.addMinutes(s, 15, now, tz); h = ScreenTime.overrideHours(h, now + 15 * 60_000L); }
                    else if (w == 1) { s = ScreenTime.addMinutes(s, 30, now, tz); h = ScreenTime.overrideHours(h, now + 30 * 60_000L); }
                    else if (w == 2) s = ScreenTime.unlockToday(s, now, tz);
                    else { view.invalidate(); return; }
                    stSave(s);
                    stSaveHours(h);
                    timeUpScreen = false;
                    ScreenTimeService.sync(this);
                    toast(w == 2 ? "Unlocked for today." : "Added play time.");
                    view.invalidate();
                    if (openAfter != null) open(openAfter);
                }).setOnCancelListener(d -> view.invalidate()).show();
    }

    private void kidsProfilePicker() {
        boolean twelve = kids12();
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Kids profile")
                .setSingleChoiceItems(new String[]{"Kids (all ages only)", "Kids 12+ (up to 12+ / 13+)"}, twelve ? 1 : 0, (d, w) -> {
                    String next = w == 1 ? "kids12" : "kids";
                    if (next.equals(prefs.getString("kidsProfile", "kids"))) { d.dismiss(); return; }
                    prefs.edit().putString("kidsProfile", next).apply();
                    // Re-filter any already fetched Roblox list; never show unfiltered games
                    if (robloxGames != null && !robloxGames.isEmpty()) {
                        robloxRatingsOk = !prefs.getBoolean("kidsMode", false) || RobloxDiscover.hasUsableRatings(robloxGames);
                        showRoblox(applyRobloxFilter(robloxGames, robloxRatingsOk));
                    } else if (prefs.getBoolean("kidsMode", false)) {
                        robloxItems = null; robloxOn = false;
                    }
                    toast(w == 1 ? "Kids 12+ will be used the next time Kids mode is on." : "Kids (all ages) will be used the next time Kids mode is on.");
                    rescan();
                    d.dismiss();
                })
                .setNegativeButton("Cancel", null).show();
    }

    private void screenTimePicker() {
        ScreenTime.State s = stLoad();
        java.util.List<String> labels = new java.util.ArrayList<>();
        ScreenTime.Hours hours = stHours();
        labels.add("Off");
        for (int m : new int[]{15, 30, 45, 60, 90, 120}) labels.add(m + " minutes");
        labels.add("Custom…");
        labels.add(ScreenTime.hoursSummary(hours));
        labels.add(!hours.enabled ? "Weekend hours: turn on allowed hours first"
                : hours.weekendSeparate ? "Weekend hours: " + ScreenTime.format24(hours.weekendStart) + "–" + ScreenTime.format24(hours.weekendEnd)
                : "Weekend hours: same as weekdays");
        labels.add(s.strict ? "Strict: on (tap to turn off)" : "Strict: off (tap to turn on)");
        labels.add(ScreenTimeService.canOverlay(this) ? "Display over other apps: allowed" : "Allow display over other apps");
        labels.add(ScreenTimeService.canUsage(this) ? "Usage access: allowed" : "Allow usage access (to bounce Settings / Play Store)");
        int pre = 0;
        if (s.limitMin > 0) {
            pre = 7; // custom unless it matches a preset
            int[] ps = {0, 15, 30, 45, 60, 90, 120};
            for (int i = 0; i < ps.length; i++) if (ps[i] == s.limitMin) pre = i;
        }
        String msg = "Daily play limit (resets at midnight on this device) and optional allowed hours in this device's local time. "
                + (s.strict ? "Strict mode tries to bring CodeAque back when time or hours run out, or when Settings, Play Store or the package installer opens."
                            : "Without Strict mode, time is counted when the child comes back, and the next game is blocked.")
                + " Android cannot force-close another app without device-owner rights.";
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Screen time")
                .setMessage(msg)
                .setSingleChoiceItems(labels.toArray(new String[0]), pre, (d, w) -> {
                    long now = System.currentTimeMillis();
                    java.util.TimeZone tz = java.util.TimeZone.getDefault();
                    if (w <= 6) {
                        int min = w == 0 ? 0 : new int[]{0, 15, 30, 45, 60, 90, 120}[w];
                        stSave(ScreenTime.setLimit(stLoad(), min, now, tz));
                        toast(min == 0 ? "Daily limit is off." : "Daily limit: " + min + " minutes.");
                        d.dismiss();
                    } else if (w == 7) {
                        d.dismiss();
                        screenTimeCustom();
                        return;
                    } else if (w == 8 || w == 9) {
                        d.dismiss();
                        screenTimeHours();
                        return;
                    } else if (w == 10) {
                        boolean on = !stLoad().strict;
                        stSave(ScreenTime.setStrict(stLoad(), on, now, tz));
                        if (on && !ScreenTimeService.canOverlay(this) && android.os.Build.VERSION.SDK_INT >= 29) {
                            new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                                    .setTitle("Display over other apps")
                                    .setMessage("On Android 10 and newer, CodeAque may need this to bring Kids mode back when time runs out. It is not required, but Strict mode works more reliably with it.")
                                    .setPositiveButton("Open settings", (a, b) -> ScreenTimeService.openOverlaySettings(this))
                                    .setNegativeButton("Not now", null).show();
                        }
                        toast(on ? "Strict mode is on." : "Strict mode is off.");
                        d.dismiss();
                    } else if (w == 11) {
                        ScreenTimeService.openOverlaySettings(this);
                        d.dismiss();
                    } else {
                        ScreenTimeService.openUsageSettings(this);
                        d.dismiss();
                    }
                    ScreenTimeService.sync(this);
                    view.invalidate();
                })
                .setNegativeButton("Close", null).show();
    }

    private void screenTimeCustom() {
        android.widget.EditText e = new android.widget.EditText(this);
        e.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        e.setHint("Minutes");
        int cur = stLoad().limitMin;
        if (cur > 0) e.setText(Integer.toString(cur));
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Custom daily limit")
                .setView(e)
                .setPositiveButton("Set", (d, w) -> {
                    int min = 0;
                    try { min = Integer.parseInt(e.getText().toString().trim()); } catch (Exception ignored) { }
                    if (min < 1 || min > 24 * 60) { toast("Enter a number of minutes between 1 and 1440."); return; }
                    stSave(ScreenTime.setLimit(stLoad(), min, System.currentTimeMillis(), java.util.TimeZone.getDefault()));
                    ScreenTimeService.sync(this);
                    toast("Daily limit: " + min + " minutes.");
                    view.invalidate();
                })
                .setNegativeButton("Cancel", null).show();
    }

    /** 0.10.49: PIN already required (Screen Time is a parent setting). Local-time windows, optional weekend. */
    private void screenTimeHours() {
        final ScreenTime.Hours cur = stHours();
        final boolean[] on = { cur.enabled };
        final boolean[] weekend = { cur.weekendSeparate };
        final int[] week = { cur.weekStart, cur.weekEnd };
        final int[] we = { cur.weekendStart, cur.weekendEnd };
        if (week[0] == 0 && week[1] == 0) { week[0] = ScreenTime.DEFAULT_WEEK_START; week[1] = ScreenTime.DEFAULT_WEEK_END; }
        if (we[0] == 0 && we[1] == 0) { we[0] = ScreenTime.DEFAULT_WEEKEND_START; we[1] = ScreenTime.DEFAULT_WEEKEND_END; }
        final android.widget.LinearLayout box = new android.widget.LinearLayout(this);
        box.setOrientation(android.widget.LinearLayout.VERTICAL);
        int pad = (int) (12 * getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad, pad, 0);
        final android.widget.TextView summary = new android.widget.TextView(this);
        android.widget.Button weekFrom = new android.widget.Button(this);
        android.widget.Button weekUntil = new android.widget.Button(this);
        android.widget.Button weFrom = new android.widget.Button(this);
        android.widget.Button weUntil = new android.widget.Button(this);
        android.widget.CheckBox useHours = new android.widget.CheckBox(this);
        android.widget.CheckBox useWeekend = new android.widget.CheckBox(this);
        Runnable relabel = () -> {
            useHours.setText("Allowed hours");
            useHours.setChecked(on[0]);
            weekFrom.setText("Weekdays from  " + ScreenTime.format24(week[0]));
            weekUntil.setText("Weekdays until  " + ScreenTime.format24(week[1]));
            useWeekend.setText("Different hours at the weekend");
            useWeekend.setChecked(weekend[0]);
            weFrom.setText("Weekend from  " + ScreenTime.format24(we[0]));
            weUntil.setText("Weekend until  " + ScreenTime.format24(we[1]));
            weekFrom.setEnabled(on[0]); weekUntil.setEnabled(on[0]);
            useWeekend.setEnabled(on[0]);
            weFrom.setEnabled(on[0] && weekend[0]); weUntil.setEnabled(on[0] && weekend[0]);
            ScreenTime.Hours preview = new ScreenTime.Hours(on[0], week[0], week[1], weekend[0], we[0], we[1], cur.overrideUntil);
            summary.setText(ScreenTime.hoursSummary(preview) + ". Times are this device's local clock. Outside the window, Kids mode shows a lock screen a parent PIN can override.");
        };
        useHours.setOnCheckedChangeListener((b, v) -> { on[0] = v; relabel.run(); });
        useWeekend.setOnCheckedChangeListener((b, v) -> { weekend[0] = v; relabel.run(); });
        weekFrom.setOnClickListener(v -> pickClock("Weekdays from", week[0], m -> { week[0] = m; relabel.run(); }));
        weekUntil.setOnClickListener(v -> pickClock("Weekdays until", week[1], m -> { week[1] = m; relabel.run(); }));
        weFrom.setOnClickListener(v -> pickClock("Weekend from", we[0], m -> { we[0] = m; relabel.run(); }));
        weUntil.setOnClickListener(v -> pickClock("Weekend until", we[1], m -> { we[1] = m; relabel.run(); }));
        box.addView(summary);
        box.addView(useHours);
        box.addView(weekFrom);
        box.addView(weekUntil);
        box.addView(useWeekend);
        box.addView(weFrom);
        box.addView(weUntil);
        relabel.run();
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Allowed hours")
                .setView(box)
                .setPositiveButton("Save", (d, w) -> {
                    ScreenTime.Hours next = new ScreenTime.Hours(on[0], week[0], week[1], weekend[0], we[0], we[1], cur.overrideUntil);
                    stSaveHours(next);
                    ScreenTimeService.sync(this);
                    toast(ScreenTime.hoursSummary(next) + ".");
                    view.invalidate();
                })
                .setNegativeButton("Cancel", null).show();
    }

    private void pickClock(String title, int current, MinutePick ok) {
        android.widget.TimePicker tp = new android.widget.TimePicker(this);
        tp.setIs24HourView(true);
        int h = ScreenTime.clampMin(current) / 60, m = ScreenTime.clampMin(current) % 60;
        if (android.os.Build.VERSION.SDK_INT >= 23) { tp.setHour(h); tp.setMinute(m); }
        else { tp.setCurrentHour(h); tp.setCurrentMinute(m); }
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(title)
                .setView(tp)
                .setPositiveButton("Set", (d, w) -> {
                    int hour, min;
                    if (android.os.Build.VERSION.SDK_INT >= 23) { hour = tp.getHour(); min = tp.getMinute(); }
                    else { hour = tp.getCurrentHour(); min = tp.getCurrentMinute(); }
                    ok.ok(hour * 60 + min);
                })
                .setNegativeButton("Cancel", null).show();
    }

    private boolean pkgInstalled(String pkg) {
        if (pkg == null || pkg.isEmpty()) return false;
        try { getPackageManager().getPackageInfo(pkg, 0); return true; } catch (Exception e) { return false; }
    }

    private void toggleClassicHome() {
        boolean next = !prefs.getBoolean("classicHome", false);
        prefs.edit().putBoolean("classicHome", next).apply();
        toast(next ? "Classic home layout is on." : "Kids-style home layout is on.");
        rescan();
    }

    private void resumeStoreQueue() {
        if (storeQueue.isEmpty()) return;
        while (storeQueueAt < storeQueue.size() && pkgInstalled(storeQueue.get(storeQueueAt))) storeQueueAt++;
        if (storeQueueAt >= storeQueue.size()) {
            storeQueue.clear(); storeQueueAt = 0;
            toast("Finished opening Play Store pages.");
            return;
        }
        openStoreQueueItem();
    }

    private void startStoreQueue(java.util.List<String> pkgs) {
        storeQueue.clear();
        storeQueue.addAll(KidsPlay.storeQueue(pkgs, pkg -> pkgInstalled(pkg)));
        storeQueueAt = 0;
        if (storeQueue.isEmpty()) { toast("Those games are already installed."); return; }
        openStoreQueueItem();
    }

    private void openStoreQueueItem() {
        while (storeQueueAt < storeQueue.size() && pkgInstalled(storeQueue.get(storeQueueAt))) storeQueueAt++;
        if (storeQueueAt >= storeQueue.size()) {
            storeQueue.clear(); storeQueueAt = 0;
            toast("Finished opening Play Store pages.");
            return;
        }
        String pkg = storeQueue.get(storeQueueAt);
        KidsPlay.Game g = KidsPlay.byPkg(pkg);
        String name = g != null ? g.title : pkg;
        int n = storeQueue.size(), i = storeQueueAt + 1;
        toast("Next game (" + i + " of " + n + "): " + name);
        storeQueueAt++;
        store(pkg);
    }


    private void fillKidsInstallList(android.widget.ListView list, java.util.ArrayList<KidsPlay.Game> shown,
                                     String[] cats, int tab, int max, java.util.LinkedHashSet<String> picked) {
        shown.clear();
        shown.addAll(KidsPlay.list(cats[tab], max));
        String[] names = new String[shown.size()];
        boolean[] on = new boolean[shown.size()];
        for (int i = 0; i < shown.size(); i++) {
            KidsPlay.Game g = shown.get(i);
            boolean have = pkgInstalled(g.pkg);
            names[i] = g.title + "  ·  PEGI " + g.pegi + (have ? "  (installed)" : "");
            on[i] = picked.contains(g.pkg);
        }
        list.setAdapter(new android.widget.ArrayAdapter<String>(this, android.R.layout.simple_list_item_multiple_choice, names));
        list.setChoiceMode(android.widget.AbsListView.CHOICE_MODE_MULTIPLE);
        for (int i = 0; i < on.length; i++) list.setItemChecked(i, on[i]);
    }

    private void installKidsGames() {
        final String[] cats = KidsPlay.CATS;
        final String[] labels = KidsPlay.CAT_LABELS;
        final int[] tab = { 0 };
        final int max = KidsPlay.ADULT;   // parent picker can tick any rated title; Kids home still filters
        final java.util.LinkedHashSet<String> picked = new java.util.LinkedHashSet<>();
        final java.util.ArrayList<KidsPlay.Game> shown = new java.util.ArrayList<>();
        final android.widget.LinearLayout root = new android.widget.LinearLayout(this);
        root.setOrientation(android.widget.LinearLayout.VERTICAL);
        int pad = (int) (8 * getResources().getDisplayMetrics().density);
        root.setPadding(pad, pad, pad, 0);
        android.widget.TextView intro = new android.widget.TextView(this);
        intro.setText("Tick games, then confirm with the parent PIN. Each selected game opens its Play Store page (already-installed games are skipped). No game files are copied.");
        intro.setPadding(0, 0, 0, pad);
        root.addView(intro);
        android.widget.LinearLayout tabs = new android.widget.LinearLayout(this);
        tabs.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        android.widget.HorizontalScrollView hsv = new android.widget.HorizontalScrollView(this);
        hsv.addView(tabs);
        root.addView(hsv);
        final android.widget.ListView list = new android.widget.ListView(this);
        root.addView(list, new android.widget.LinearLayout.LayoutParams(android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        list.setOnItemClickListener((parent, v, pos, id) -> {
            if (pos < 0 || pos >= shown.size()) return;
            String pkg = shown.get(pos).pkg;
            if (list.isItemChecked(pos)) picked.add(pkg); else picked.remove(pkg);
        });
        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            android.widget.Button b = new android.widget.Button(this);
            b.setText(labels[i]);
            b.setOnClickListener(v -> { tab[0] = idx; fillKidsInstallList(list, shown, cats, tab[0], max, picked); });
            tabs.addView(b);
        }
        fillKidsInstallList(list, shown, cats, tab[0], max, picked);
        AlertDialog dlg = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Install kids games")
                .setView(root)
                .setPositiveButton("Install selected", null)
                .setNeutralButton("Select all in this tab", null)
                .setNegativeButton("Cancel", null)
                .create();
        dlg.show();
        dlg.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
            for (KidsPlay.Game g : shown) if (!pkgInstalled(g.pkg)) picked.add(g.pkg);
            fillKidsInstallList(list, shown, cats, tab[0], max, picked);
        });
        dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (picked.isEmpty()) { toast("Tick at least one game."); return; }
            dlg.dismiss();
            askPin("Confirm install", () -> startStoreQueue(new java.util.ArrayList<>(picked)));
        });
    }

    private void kidsEmusSettings() {
        java.util.ArrayList<Catalog.Item> installed = new java.util.ArrayList<>();
        java.util.LinkedHashSet<String> seen = new java.util.LinkedHashSet<>();
        for (Catalog.Item e : Catalog.emulators()) {
            if (launchIntent(e) == null) continue;
            String pkg = e.storePackage();
            if (pkg != null && seen.add(pkg)) installed.add(e);
        }
        // Also any installed app that looks like an emulator.
        // `apps` from the last rescan isn't in scope; query launchers again via known packages only.
        if (installed.isEmpty()) { toast("No emulator apps are installed."); return; }
        android.widget.LinearLayout box = new android.widget.LinearLayout(this);
        box.setOrientation(android.widget.LinearLayout.VERTICAL);
        int p = (int) (8 * getResources().getDisplayMetrics().density);
        box.setPadding(p, p, p, 0);
        android.widget.TextView intro = new android.widget.TextView(this);
        intro.setText("Emulators stay hidden in Kids and Kids 12+ unless you tick them here. Each profile has its own list. This does not add ROM files.");
        intro.setPadding(0, 0, 0, p);
        box.addView(intro);
        java.util.HashSet<String> k = new java.util.HashSet<>(prefs.getStringSet("kidsEmu", java.util.Collections.<String>emptySet()));
        java.util.HashSet<String> k12 = new java.util.HashSet<>(prefs.getStringSet("kidsEmu12", java.util.Collections.<String>emptySet()));
        android.widget.TextView h1 = new android.widget.TextView(this); h1.setText("Kids"); h1.setPadding(0, p, 0, p / 2); box.addView(h1);
        final java.util.ArrayList<android.widget.CheckBox> boxesKids = new java.util.ArrayList<>();
        final java.util.ArrayList<android.widget.CheckBox> boxes12 = new java.util.ArrayList<>();
        for (Catalog.Item e : installed) {
            android.widget.CheckBox cb = new android.widget.CheckBox(this);
            cb.setText(e.label);
            cb.setChecked(k.contains(e.storePackage()));
            cb.setTag(e.storePackage());
            boxesKids.add(cb); box.addView(cb);
        }
        android.widget.TextView h2 = new android.widget.TextView(this); h2.setText("Kids 12+"); h2.setPadding(0, p, 0, p / 2); box.addView(h2);
        for (Catalog.Item e : installed) {
            android.widget.CheckBox cb = new android.widget.CheckBox(this);
            cb.setText(e.label);
            cb.setChecked(k12.contains(e.storePackage()));
            cb.setTag(e.storePackage());
            boxes12.add(cb); box.addView(cb);
        }
        android.widget.ScrollView sv = new android.widget.ScrollView(this);
        sv.addView(box);
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Emulators in Kids mode")
                .setView(sv)
                .setPositiveButton("Save", (d, w) -> {
                    java.util.HashSet<String> a = new java.util.HashSet<>();
                    java.util.HashSet<String> bset = new java.util.HashSet<>();
                    for (android.widget.CheckBox cb : boxesKids) if (cb.isChecked()) a.add((String) cb.getTag());
                    for (android.widget.CheckBox cb : boxes12) if (cb.isChecked()) bset.add((String) cb.getTag());
                    prefs.edit().putStringSet("kidsEmu", a).putStringSet("kidsEmu12", bset).apply();
                    toast("Saved. Ticked emulators will show in that Kids profile.");
                    rescan();
                })
                .setNegativeButton("Cancel", null).show();
    }


}

/** Package-level so the older D8 dexer can compile the Screen Time clock picker. */
interface MinutePick { void ok(int minutes); }


/** Package-level so the older D8 dexer can compile the IME fallback timer. */
final class KidsImeFallback implements Runnable {
    final MainActivity activity;
    final int gen;
    KidsImeFallback(MainActivity activity, int gen) { this.activity = activity; this.gen = gen; }
    @Override public void run() { activity.onKidsImeFallback(gen); }
}

final class KidsSearchWatch implements android.text.TextWatcher {
    final MainActivity activity;
    KidsSearchWatch(MainActivity activity) { this.activity = activity; }
    @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
    @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
    @Override public void afterTextChanged(android.text.Editable s) {
        activity.onKidsSearchField(s == null ? "" : s.toString());
    }
}

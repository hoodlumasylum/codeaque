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
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        view = new HomeView(this, this);
        setContentView(view);
        view.requestFocus();
        pool = new SoundPool.Builder().setMaxStreams(3).setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build();
        for (String n : new String[]{"hhmove", "hhcat", "hhselect", "hhback"}) {
            try { sounds.put(n, pool.load(getAssets().openFd(n + ".wav"), 1)); } catch (Exception ignored) { }
        }
        Intent i = getIntent();     // for screenshots/tests: am start -e cat cloud -e tile 1
        if (i != null && i.hasExtra("cat")) view.focus(i.getStringExtra("cat"), i.getIntExtra("tile", 0));
    }

    @Override protected void onResume() {
        super.onResume();
        installedCache.clear();       // apps may have been installed while we were away
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        view.invalidate();
    }

    @Override public void onBackPressed() { /* a home screen stays */ }
    @Override protected void onDestroy() { pool.release(); super.onDestroy(); }

    // ---------- Host ----------
    @Override public boolean installed(Catalog.Item it) {
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
            Intent i = launchIntent(it);
            if (i != null) { start(i); return; }
            if (it.webUrl != null) { start(new Intent(Intent.ACTION_VIEW, Uri.parse(it.webUrl))); return; }
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
            case "wifi": start(new Intent(Settings.ACTION_WIFI_SETTINGS)); break;
            case "bluetooth": start(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)); break;
            case "display": start(new Intent(Settings.ACTION_DISPLAY_SETTINGS)); break;
            case "settings": start(new Intent(Settings.ACTION_SETTINGS)); break;
            case "home": start(new Intent(Settings.ACTION_HOME_SETTINGS)); break;
            case "browser": start(new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com"))); break;
            case "effects": {
                String n = Catalog.nextEffects(effects());
                prefs.edit().putString("effects", n).apply();
                Toast.makeText(this, "Effects: " + n + (n.equals("auto") ? " (30 fps waves)" : n.equals("off") ? " (still background, saves battery)" : " (60 fps)"), Toast.LENGTH_SHORT).show();
                view.invalidate(); break;
            }
            case "allapps": allApps(); break;
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

    @Override public void sound(String n) {
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

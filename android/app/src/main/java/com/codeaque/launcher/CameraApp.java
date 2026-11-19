package com.codeaque.launcher;

/**
 * Adult-mode top-bar camera shortcut (0.10.76).
 * Opens B612 if it is installed, otherwise its Google Play page.
 * Play Store installs only: CodeAque never bundles or sideloads the B612 APK.
 * In Kids mode the icon is hidden (B612 has social / sharing features).
 */
public final class CameraApp {
    private CameraApp() {}

    public static final String PKG = "com.linecorp.b612.android";
    public static final String MARKET = "market://details?id=" + PKG;
    public static final String PLAY_HTTPS = "https://play.google.com/store/apps/details?id=" + PKG;
    /** Set once the adult first-run Play Store offer has been shown (or skipped because it was already installed). */
    public static final String PREF_OFFER = "cameraOfferShown";

    /** Same size and Y as the existing top-bar Wi-Fi glyph; do not change these or the tab-strip geometry. */
    public static final float ICON_SIZE = 22f;
    public static final float ICON_Y = 29f;
    /** Centre-to-centre gap, matching the existing Wi-Fi-to-clock spacing constant. */
    public static final float ICON_GAP = 30f;
    /** Half-size of the tap target; stays short of the Wi-Fi centre so the two hits do not overlap. */
    public static final float HIT_RADIUS = 14f;

    /** Simpler safe option: hide the icon in Kids mode rather than asking for the parent PIN. */
    public static boolean showIcon(boolean kidsMode) { return !kidsMode; }

    /** One-time adult-mode prompt that opens the Play Store page. Never shown in Kids mode. */
    public static boolean shouldOfferInstall(boolean kidsMode, boolean installed, boolean alreadyOffered) {
        return !kidsMode && !installed && !alreadyOffered;
    }

    /** Camera icon centre, immediately to the left of the unchanged Wi-Fi centre. */
    public static float cameraCx(float wifiCx, float s) {
        return wifiCx - ICON_GAP * s;
    }

    public static float cameraCy(float s) { return ICON_Y * s; }

    public static float iconSize(float s) { return ICON_SIZE * s; }

    public static float hitLeft(float wifiCx, float s) { return cameraCx(wifiCx, s) - HIT_RADIUS * s; }
    public static float hitTop(float s) { return cameraCy(s) - HIT_RADIUS * s; }
    public static float hitRight(float wifiCx, float s) { return cameraCx(wifiCx, s) + HIT_RADIUS * s; }
    public static float hitBottom(float s) { return cameraCy(s) + HIT_RADIUS * s; }

    /** True if a top-bar tap (view coordinates) is on the camera icon. */
    public static boolean hit(float x, float y, float wifiCx, float s) {
        float cx = cameraCx(wifiCx, s), cy = cameraCy(s), r = HIT_RADIUS * s;
        return x >= cx - r && x <= cx + r && y >= cy - r && y <= cy + r;
    }
}

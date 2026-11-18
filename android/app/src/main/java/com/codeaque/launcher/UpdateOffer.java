package com.codeaque.launcher;

/** 0.10.59: an update stays offered until the installed version is no longer older than the available one.
 *  Not a timeout, and not a "Later" snooze. Persistence is in UpdateOfferStore. */
public final class UpdateOffer {
    public static final String PREF_VER = "updAvailVer";
    public static final String PREF_NOTES = "updAvailNotes";
    public static final String PREF_APK = "updAvailApk";
    public static final String PREF_SUMS = "updAvailSums";
    public static final String PREF_DIGEST = "updAvailDigest";
    public static final String PREF_API = "updAvailApi";

    /** Visible label on the home / Kids install bar. */
    public static final String INSTALL = "Install";

    /** Hide only when the installed version is already at least the offered one.
     *  If an available versionCode is known (> 0), that is the authority; otherwise version names. */
    public static boolean shouldShow(String availableVer, String installedVer) {
        return shouldShow(availableVer, installedVer, 0, 0);
    }

    public static boolean shouldShow(String availableVer, String installedVer, int availableCode, int installedCode) {
        if (availableCode > 0) return installedCode < availableCode;
        return availableVer != null && !availableVer.isEmpty()
                && installedVer != null
                && UpdateInfo.newer(availableVer, installedVer);
    }

    public static String bannerLabel(String availableVer) {
        if (availableVer == null || availableVer.isEmpty()) return "Update ready";
        String v = availableVer.replaceFirst("^[vV]", "");
        return v.isEmpty() ? "Update ready" : "Update ready  v" + v;
    }

    private UpdateOffer() {}
}

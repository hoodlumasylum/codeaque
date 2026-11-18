package com.codeaque.launcher;

/** 0.10.59: persist an update offer in SharedPreferences. */
public final class UpdateOfferStore {
    private UpdateOfferStore() {}

    public static boolean apply(android.content.SharedPreferences prefs, String installedVer, int installedCode) {
        String v = prefs.getString(UpdateOffer.PREF_VER, "");
        int code = prefs.getInt("updAvailCode", 0);
        if (UpdateOffer.shouldShow(v, installedVer, code, installedCode)) {
            Catalog.updateAvailable = true;
            return true;
        }
        if ((v != null && !v.isEmpty()) || code > 0) clear(prefs);
        else Catalog.updateAvailable = false;
        return false;
    }

    public static boolean apply(android.content.SharedPreferences prefs, String installedVer) {
        return apply(prefs, installedVer, 0);
    }

    public static void remember(android.content.SharedPreferences prefs, UpdateInfo u) {
        if (u == null || u.version == null || u.version.isEmpty()) return;
        prefs.edit()
                .putString(UpdateOffer.PREF_VER, u.version)
                .putString(UpdateOffer.PREF_NOTES, u.notes == null ? "" : u.notes)
                .putString(UpdateOffer.PREF_APK, u.apkUrl == null ? "" : u.apkUrl)
                .putString(UpdateOffer.PREF_SUMS, u.sumsUrl == null ? "" : u.sumsUrl)
                .putString(UpdateOffer.PREF_DIGEST, u.apkDigest == null ? "" : u.apkDigest)
                .putString(UpdateOffer.PREF_API, u.apkApiUrl == null ? "" : u.apkApiUrl)
                .putString("updKnown", u.version)
                .remove("updSnoozeVer")
                .remove("updSnoozeUntil")
                .apply();
        Catalog.updateAvailable = true;
    }

    public static void clear(android.content.SharedPreferences prefs) {
        prefs.edit()
                .remove(UpdateOffer.PREF_VER).remove(UpdateOffer.PREF_NOTES).remove(UpdateOffer.PREF_APK)
                .remove(UpdateOffer.PREF_SUMS).remove(UpdateOffer.PREF_DIGEST).remove(UpdateOffer.PREF_API)
                .remove("updAvailCode")
                .apply();
        Catalog.updateAvailable = false;
    }

    public static UpdateInfo load(android.content.SharedPreferences prefs, String installedVer) {
        String v = prefs.getString(UpdateOffer.PREF_VER, "");
        if (!UpdateOffer.shouldShow(v, installedVer, prefs.getInt("updAvailCode", 0), 0)) return null;
        String apk = prefs.getString(UpdateOffer.PREF_APK, "");
        String sums = prefs.getString(UpdateOffer.PREF_SUMS, "");
        if (apk.isEmpty() || sums.isEmpty()) return null;
        UpdateInfo u = new UpdateInfo(v, apk, sums);
        u.notes = prefs.getString(UpdateOffer.PREF_NOTES, "");
        String dig = prefs.getString(UpdateOffer.PREF_DIGEST, "");
        u.apkDigest = dig.isEmpty() ? null : dig;
        String api = prefs.getString(UpdateOffer.PREF_API, "");
        u.apkApiUrl = api.isEmpty() ? null : api;
        return u;
    }
}

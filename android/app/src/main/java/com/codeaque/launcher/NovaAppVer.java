package com.codeaque.launcher;

/**
 * The shipping Android version, shown in Chat and Nova's tab so a screenshot
 * can confirm which build is installed. Keep in step with the manifest.
 */
public final class NovaAppVer {
    private NovaAppVer() {}

    public static final String NAME = "0.10.76";
    public static final int CODE = 176;

    public static String label() { return "CodeAque " + NAME; }

    /** Prefer the installed APK's versionName when a context is available. */
    public static String label(String installedName) {
        if (installedName == null || installedName.isEmpty() || "?".equals(installedName))
            return label();
        return "CodeAque " + installedName;
    }
}

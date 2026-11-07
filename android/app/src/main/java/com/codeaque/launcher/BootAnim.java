package com.codeaque.launcher;

/** 0.10.1 Settings > CodeAque boot animation: the published Magisk module, checked against its SHA-256 before anything runs.
 *  Plain Java (no Android classes) so the build-time checks can test it. UNTESTED ON A REAL DEVICE. */
public final class BootAnim {
    private BootAnim() { }

    public static final String FILE = "codeaque-bootanim-v1.0.zip";
    public static final String URL = "https://github.com/hoodlumasylum/codeaque/releases/download/bootanim-v1.0/" + FILE;
    /** SHA-256 of the asset published at the bootanim-v1.0 release. */
    public static final String SHA256 = "b1616fad964be479a5c125505defbd1b9b99fa3cef97cff3f09b5ea35b3600c1";
    public static final long MAX_BYTES = 20L << 20;
    public static final String MODULE_ID = "codeaque-bootanim";
    public static final String MAGISK_PKG = "com.topjohnwu.magisk";
    public static final String[] SU_PATHS = {"/system/bin/su", "/system/xbin/su", "/sbin/su", "/debug_ramdisk/su", "/data/adb/magisk/magisk"};

    public interface Exists { boolean exists(String path); }

    /** Show the Settings entry only where it can work: the Magisk app is installed or a su binary is there. */
    public static boolean available(boolean magiskAppInstalled, Exists fs) {
        if (magiskAppInstalled) return true;
        for (String p : SU_PATHS) if (fs.exists(p)) return true;
        return false;
    }

    /** Only plain storage paths (no quotes, spaces or shell characters) are ever passed to su. */
    public static boolean safePath(String p) {
        return p != null && p.startsWith("/") && p.endsWith(".zip") && p.matches("[A-Za-z0-9/._()-]+") && !p.contains("..");
    }

    public static String installCmd(String path) {
        if (!safePath(path)) throw new IllegalArgumentException("unsafe path");
        return "magisk --install-module '" + path + "'";
    }

    public static String removeCmd() {
        return "test -d /data/adb/modules/" + MODULE_ID + " && touch /data/adb/modules/" + MODULE_ID + "/remove";
    }

    public static final String REBOOT_CMD = "svc power reboot || reboot";

    public static String hex(byte[] d) {
        StringBuilder s = new StringBuilder();
        for (byte x : d) s.append(String.format("%02x", x));
        return s.toString();
    }

    public static boolean matches(byte[] digest) { return SHA256.equals(hex(digest)); }

    public static final String INFO = "Replaces the start-up animation with CodeAque's own animation, systemlessly with Magisk. "
            + "Your system files are not changed. UNTESTED on a real device: if it looks wrong, choose Remove boot animation "
            + "(or disable the module in the Magisk app) and restart.";
    public static final String NO_ROOT = "Root access was not available, so nothing was installed. The file is saved in Downloads: "
            + "open Magisk > Modules > Install from storage and pick " + FILE + ", then restart.";
}

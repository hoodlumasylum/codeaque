package com.codeaque.launcher;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.Enumeration;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** 0.10.1 / 0.10.42 Settings > CodeAque boot animation.
 *  The published Magisk module is downloaded and SHA-256 checked. On Magisk, the module is installed
 *  systemlessly. Otherwise the original CodeAque animation (the stored bootanimation.zip inside the
 *  module, never the module wrapper) is copied to /data/local/bootanimation.zip. An optional advanced
 *  path can write to /system or /product after a verified backup. Plain Java so the checks can run on a PC.
 *  UNTESTED ON A REAL DEVICE. */
public final class BootAnim {
    private BootAnim() { }

    public static final String FILE = "codeaque-bootanim-v1.0.zip";
    public static final String URL = "https://github.com/hoodlumasylum/codeaque/releases/download/bootanim-v1.0/" + FILE;
    /** SHA-256 of the Magisk module asset published at the bootanim-v1.0 release. */
    public static final String SHA256 = "b1616fad964be479a5c125505defbd1b9b99fa3cef97cff3f09b5ea35b3600c1";
    /** SHA-256 of the original stored animation inside the module (system/media/bootanimation.zip). */
    public static final String ANIM_SHA256 = "17914d88e1bb5315c2d86d0356dff74629c9dcd63f661bc6aab3b0853a1126fc";
    public static final String ANIM_FILE = "codeaque-bootanimation.zip";
    public static final long ANIM_BYTES = 1177051L;
    public static final long MAX_BYTES = 20L << 20;
    public static final String MODULE_ID = "codeaque-bootanim";
    public static final String MAGISK_PKG = "com.topjohnwu.magisk";
    public static final String[] SU_PATHS = {"/system/bin/su", "/system/xbin/su", "/sbin/su", "/debug_ramdisk/su", "/data/adb/magisk/magisk"};
    public static final String[] MAGISK_PATHS = {"/data/adb/magisk/magisk", "/debug_ramdisk/magisk", "/sbin/magisk"};
    public static final String DATA_LOCAL = "/data/local/bootanimation.zip";
    public static final String BACKUP_DIR = "/sdcard/Download/codeaque-bootanim-backup";
    public static final String[] SYSTEM_TARGETS = {
            "/product/media/bootanimation.zip",
            "/system/media/bootanimation.zip",
            "/system/product/media/bootanimation.zip"
    };
    public static final String[] ANIM_ENTRIES = {
            "system/media/bootanimation.zip",
            "system/product/media/bootanimation.zip"
    };

    public interface Exists { boolean exists(String path); }

    /** Show the Settings entry only where it can work: the Magisk app is installed or a su binary is there. */
    public static boolean available(boolean magiskAppInstalled, Exists fs) {
        if (magiskAppInstalled) return true;
        for (String p : SU_PATHS) if (fs.exists(p)) return true;
        return false;
    }

    /** Magisk itself is present (app or magisk binary). In that case the Magisk module path stays the default. */
    public static boolean magiskPresent(boolean magiskAppInstalled, Exists fs) {
        if (magiskAppInstalled) return true;
        if (fs == null) return false;
        for (String p : MAGISK_PATHS) if (fs.exists(p)) return true;
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

    public static boolean matchesAnim(byte[] digest) { return ANIM_SHA256.equals(hex(digest)); }

    public static byte[] sha256(File f) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] b = new byte[8192];
            InputStream in = new FileInputStream(f);
            try {
                int n;
                while ((n = in.read(b)) > 0) md.update(b, 0, n);
            } finally { in.close(); }
            return md.digest();
        } catch (Exception e) { return new byte[0]; }
    }

    /** Flatten a system path into a backup file name, e.g. /system/media/bootanimation.zip → system-media-bootanimation.zip. */
    public static String backupName(String path) {
        if (path == null || !path.startsWith("/")) return null;
        String n = path.substring(1).replace('/', '-');
        if (!n.endsWith(".zip")) n = n + ".zip";
        return n.matches("[A-Za-z0-9._-]+") ? n : null;
    }

    public static String targetFromBackupName(String name) {
        if (name == null || !name.endsWith(".zip")) return null;
        String t = "/" + name.substring(0, name.length() - 4).replace('-', '/');
        if (!t.endsWith(".zip")) t = t + ".zip";
        for (String s : SYSTEM_TARGETS) if (s.equals(t)) return s;
        return null;
    }

    public static boolean isStoredBootAnim(File zip) {
        if (zip == null || !zip.isFile()) return false;
        ZipFile z = null;
        try {
            z = new ZipFile(zip);
            boolean desc = false;
            Enumeration<? extends ZipEntry> en = z.entries();
            while (en.hasMoreElements()) {
                ZipEntry e = en.nextElement();
                if (e.getMethod() != ZipEntry.STORED) return false;
                String n = e.getName();
                if (n == null || n.contains("..") || n.startsWith("/")) return false;
                if ("desc.txt".equals(n)) desc = true;
                else if (!(n.startsWith("part") && (n.endsWith("/") || n.endsWith(".png")))) return false;
            }
            return desc;
        } catch (Exception e) { return false; }
        finally { if (z != null) try { z.close(); } catch (Exception ignored) { } }
    }

    public static boolean isOriginalAnimation(File zip) {
        if (zip == null || !zip.isFile() || zip.length() != ANIM_BYTES) return false;
        if (!isStoredBootAnim(zip)) return false;
        return matchesAnim(sha256(zip));
    }

    /**
     * Pull the original stored CodeAque {@code bootanimation.zip} out of the Magisk module.
     * Never copies the module wrapper. Returns null on success, or a short error.
     */
    public static String extractAnimation(File moduleZip, File dest) {
        if (moduleZip == null || !moduleZip.isFile()) return "The downloaded file is missing.";
        if (dest == null) return "No place to save the animation.";
        ZipFile z = null;
        try {
            z = new ZipFile(moduleZip);
            ZipEntry found = null;
            for (String name : ANIM_ENTRIES) {
                ZipEntry e = z.getEntry(name);
                if (e != null && !e.isDirectory()) { found = e; break; }
            }
            if (found == null) return "The CodeAque animation was not inside the downloaded file.";
            File parent = dest.getParentFile();
            if (parent != null) parent.mkdirs();
            InputStream in = z.getInputStream(found);
            FileOutputStream out = new FileOutputStream(dest);
            try {
                byte[] b = new byte[8192];
                long total = 0;
                int n;
                while ((n = in.read(b)) > 0) {
                    total += n;
                    if (total > MAX_BYTES) { dest.delete(); return "The animation file is too big."; }
                    out.write(b, 0, n);
                }
            } finally { try { in.close(); } catch (Exception ignored) { } try { out.close(); } catch (Exception ignored) { } }
            if (!isOriginalAnimation(dest)) {
                dest.delete();
                return "The animation file did not match the original CodeAque boot animation. Nothing was installed.";
            }
            return null;
        } catch (Exception e) {
            if (dest != null) dest.delete();
            return "The animation could not be unpacked.";
        } finally { if (z != null) try { z.close(); } catch (Exception ignored) { } }
    }

    public static String dataLocalInstallCmd(String src) {
        if (!safePath(src)) throw new IllegalArgumentException("unsafe path");
        String q = SuRun.singleQuote(src);
        return "cp " + q + " " + DATA_LOCAL
                + " && chmod 644 " + DATA_LOCAL
                + " && chown root:root " + DATA_LOCAL
                + " && (restorecon " + DATA_LOCAL + " >/dev/null 2>&1 || chcon u:object_r:system_file:s0 " + DATA_LOCAL + " >/dev/null 2>&1 || true)"
                + " && test -s " + DATA_LOCAL
                + " && test $(stat -c %s " + DATA_LOCAL + ") -eq $(stat -c %s " + q + ")";
    }

    /** Optional advanced path: remount, back up each existing system animation, verify the backup, then replace. */
    public static String systemInstallCmd(String src) {
        if (!safePath(src)) throw new IllegalArgumentException("unsafe path");
        String q = SuRun.singleQuote(src);
        StringBuilder s = new StringBuilder();
        s.append("if mount -o rw,remount / >/dev/null 2>&1 || mount -o rw,remount /system >/dev/null 2>&1 || mount -o rw,remount /product >/dev/null 2>&1; then echo __REMOUNT=ok; else echo __REMOUNT=fail; echo The system folder could not be unlocked for writing.; exit 2; fi; ");
        s.append("mkdir -p ").append(BACKUP_DIR).append("; found=0; ");
        for (String t : SYSTEM_TARGETS) {
            String bn = backupName(t);
            if (bn == null) continue;
            String b = BACKUP_DIR + "/" + bn;
            String qt = SuRun.singleQuote(t);
            String qb = SuRun.singleQuote(b);
            s.append("if [ -f ").append(qt).append(" ]; then found=1; ");
            s.append("MODE=$(stat -c %a ").append(qt).append("); OWN=$(stat -c %u:%g ").append(qt).append("); ");
            s.append("CTX=$(ls -Zd ").append(qt).append(" 2>/dev/null | awk '{print $1}'); ");
            s.append("cp ").append(qt).append(" ").append(qb).append(" || { echo The backup could not be written.; exit 3; }; ");
            s.append("if [ \"$(stat -c %s ").append(qt).append(")\" != \"$(stat -c %s ").append(qb).append(")\" ]; then echo The backup could not be verified.; exit 3; fi; ");
            s.append("if command -v sha256sum >/dev/null 2>&1; then ");
            s.append("H1=$(sha256sum ").append(qt).append(" | awk '{print $1}'); H2=$(sha256sum ").append(qb).append(" | awk '{print $1}'); ");
            s.append("if [ \"$H1\" != \"$H2\" ]; then echo The backup could not be verified.; exit 3; fi; ");
            s.append("else cmp -s ").append(qt).append(" ").append(qb).append(" || { echo The backup could not be verified.; exit 3; }; fi; ");
            s.append("cp ").append(q).append(" ").append(qt).append(" || { echo The animation could not be copied.; exit 4; }; ");
            s.append("chmod \"$MODE\" ").append(qt).append("; chown \"$OWN\" ").append(qt).append("; ");
            s.append("if [ -n \"$CTX\" ] && [ \"$CTX\" != \"?\" ]; then chcon \"$CTX\" ").append(qt).append(" >/dev/null 2>&1 || restorecon ").append(qt).append(" >/dev/null 2>&1 || true; fi; ");
            s.append("if [ \"$(stat -c %s ").append(qt).append(")\" != \"$(stat -c %s ").append(q).append(")\" ]; then echo The new animation could not be verified.; exit 4; fi; ");
            s.append("fi; ");
        }
        s.append("if [ \"$found\" = 0 ]; then echo No system boot animation was found to replace.; exit 5; fi");
        return s.toString();
    }

    public static String restoreCmd() {
        StringBuilder s = new StringBuilder();
        s.append("rm -f ").append(DATA_LOCAL).append("; rc=0; ");
        s.append("need=0; for b in ").append(BACKUP_DIR).append("/*.zip; do [ -f \"$b\" ] && need=1; done; ");
        s.append("if [ \"$need\" = 1 ]; then ");
        s.append("if mount -o rw,remount / >/dev/null 2>&1 || mount -o rw,remount /system >/dev/null 2>&1 || mount -o rw,remount /product >/dev/null 2>&1; then echo __REMOUNT=ok; ");
        for (String t : SYSTEM_TARGETS) {
            String bn = backupName(t);
            if (bn == null) continue;
            String b = BACKUP_DIR + "/" + bn;
            s.append("if [ -f ").append(SuRun.singleQuote(b)).append(" ]; then ");
            s.append("cp ").append(SuRun.singleQuote(b)).append(" ").append(SuRun.singleQuote(t)).append(" || rc=4; fi; ");
        }
        s.append("else echo __REMOUNT=fail; echo The system folder could not be unlocked for writing.; rc=2; fi; fi; ");
        s.append("if [ -d /data/adb/modules/").append(MODULE_ID).append(" ]; then touch /data/adb/modules/").append(MODULE_ID).append("/remove; fi; ");
        s.append("exit $rc");
        return s.toString();
    }

    /** Short plain-English reason from su output. Details stay in the raw log. */
    public static String shortError(String out, int rc) {
        String t = out == null ? "" : out;
        String low = t.toLowerCase(Locale.ROOT);
        if (low.contains("__remount=fail") || low.contains("could not be unlocked")
                || (low.contains("read-only") && low.contains("file system"))
                || (low.contains("remount") && (low.contains("failed") || low.contains("denied") || low.contains("read-only"))))
            return "The system folder could not be unlocked for writing. Nothing was changed.";
        if (low.contains("backup could not be verified") || low.contains("backup could not be written"))
            return "The backup could not be verified, so the system animation was not changed.";
        if (low.contains("no system boot animation was found"))
            return "No system boot animation was found to replace. Nothing was changed.";
        if (rc == 2) return "The system folder could not be unlocked for writing. Nothing was changed.";
        if (rc == 3) return "The backup could not be verified, so the system animation was not changed.";
        if (rc == 5) return "No system boot animation was found to replace. Nothing was changed.";
        String first = firstUseful(t);
        if (first != null && !first.isEmpty() && !SuRun.isExecMissLine(first)) return first;
        return "The boot animation could not be installed.";
    }

    static String firstUseful(String text) {
        if (text == null) return "";
        for (String raw : text.replace('\r', '\n').split("\n")) {
            String l = raw.trim();
            if (l.isEmpty() || l.startsWith("__RC=") || l.startsWith("__REMOUNT=")) continue;
            if (SuRun.isExecMissLine(l)) continue;
            return l;
        }
        return "";
    }

    public static final String INFO_MAGISK = "Replaces the start-up animation with CodeAque's own animation, systemlessly with Magisk. "
            + "Your system files are not changed. UNTESTED on a real device: if it looks wrong, choose Restore original boot animation "
            + "(or disable the module in the Magisk app) and restart.";
    public static final String INFO_SAFE = "Replaces the start-up animation with CodeAque's own animation by copying it to /data/local "
            + "(the phone's system folder is not rewritten). Many phones read the animation from there. UNTESTED on a real device: "
            + "if it looks wrong, choose Restore original boot animation and restart.";
    public static final String ADVANCED_WARN = "This optional step writes inside the phone's system or product folder. "
            + "If it goes wrong, the phone may not start until the original animation is restored (a backup is made first, and nothing is overwritten until that backup is checked). "
            + "Prefer the safe /data/local install when it works. Continue only if you understand this risk.";
    public static final String INFO = INFO_MAGISK;
    public static final String NO_ROOT = "Root access was not available, so nothing was installed. The file is saved in Downloads: "
            + "open Magisk > Modules > Install from storage and pick " + FILE + ", then restart.";
}

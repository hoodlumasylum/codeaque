package com.codeaque.launcher;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** 0.10.7: the player's own PSP game files on internal storage, SD cards and USB sticks (common folders only).
 *  Nothing is downloaded, no source is linked, no art. Plain Java for the build-time checks. */
public final class PspScan {
    private PspScan() { }

    /** Relative to each storage root, matched case-insensitively (ROMS/PSP = roms/psp). .chd only counts inside a PSP folder. */
    public static final String[] FOLDERS = {"roms/psp", "psp/game", "psp/iso", "psp", "download", "downloads"};
    static final int MAX_FILES = 300, MAX_DEPTH = 3;

    public static boolean pspFile(String name, boolean inPspFolder) {
        String n = name.toLowerCase(Locale.ROOT);
        if (n.startsWith(".")) return false;
        return n.endsWith(".iso") || n.endsWith(".cso") || n.endsWith(".pbp") || (inPspFolder && n.endsWith(".chd"));
    }

    /** True when a path (or a file: uri) sits in a PSP folder. */
    public static boolean inPspFolder(String path) {
        String p = path.toLowerCase(Locale.ROOT);
        return p.contains("/psp/") || p.endsWith("/psp");
    }

    public static List<File> scan(List<File> roots) { return scan(roots, null); }
    public static List<File> scan(List<File> roots, List<String> scanned) {
        List<File> out = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>(), files = new java.util.HashSet<>();
        for (File root : roots) {
            if (root == null) continue;
            for (String rel : FOLDERS) for (File d : DcScan.resolve(root, rel)) {
                if (!seen.add(DcScan.key(d))) continue;
                if (scanned != null) scanned.add(d.getAbsolutePath());
                walk(d, inPspFolder("/" + rel), 0, out, files);
                if (out.size() >= MAX_FILES) return out;
            }
        }
        return out;
    }

    private static void walk(File d, boolean psp, int depth, List<File> out, java.util.Set<String> files) {
        File[] fs = d.listFiles();
        if (fs == null) return;
        java.util.Arrays.sort(fs);
        for (File f : fs) {
            if (out.size() >= MAX_FILES) return;
            if (f.isDirectory()) { if (depth < MAX_DEPTH && !f.getName().startsWith(".") && !f.getName().equalsIgnoreCase("SAVEDATA")) walk(f, psp, depth + 1, out, files); }
            else if (pspFile(f.getName(), psp)) {
                String k; try { k = f.getCanonicalPath(); } catch (Exception e) { k = f.getAbsolutePath(); }
                if (files.add(k)) out.add(f);
            }
        }
    }

    /** Tile name: EBOOT.PBP games are named after their folder (PSP/GAME/<name>/EBOOT.PBP). */
    public static String fileName(File f) {
        if (f.getName().equalsIgnoreCase("EBOOT.PBP") && f.getParentFile() != null) return f.getParentFile().getName() + ".pbp";
        return f.getName();
    }
}

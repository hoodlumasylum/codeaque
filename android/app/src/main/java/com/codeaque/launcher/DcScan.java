package com.codeaque.launcher;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** 0.10.5: the player's own Dreamcast game files on internal storage and SD cards (common folders only).
 *  Nothing is downloaded and no source is linked. Plain Java for the build-time checks. */
public final class DcScan {
    private DcScan() { }

    /** Relative to each storage root, matched case-insensitively (ROMS/Dreamcast = roms/dreamcast = Roms/DC).
     *  "Download" is checked too; .chd only counts inside a Dreamcast folder. */
    public static final String[] FOLDERS = {"roms/dreamcast", "roms/dc", "dreamcast", "dc", "download", "downloads"};
    static final int MAX_FILES = 300, MAX_DEPTH = 3;   // ROMS/Dreamcast/<Game>/<disc>/<game>.gdi

    /** Every existing folder matching rel, ignoring case in each part (several on case-sensitive volumes). */
    public static List<File> resolve(File root, String rel) {
        List<File> cur = new ArrayList<>(); cur.add(root);
        if (rel.isEmpty()) return cur;
        for (String part : rel.split("/")) {
            List<File> next = new ArrayList<>();
            for (File c : cur) {
                File[] fs = c.listFiles();
                if (fs == null) { File f = new File(c, part); if (f.isDirectory()) next.add(f); continue; }   // unlistable parent: try the exact name
                java.util.Arrays.sort(fs);
                for (File f : fs) if (f.isDirectory() && f.getName().equalsIgnoreCase(part)) next.add(f);
            }
            cur = next;
            if (cur.isEmpty()) break;
        }
        return cur;
    }

    static String key(File f) { try { return f.getCanonicalPath(); } catch (Exception e) { return f.getAbsolutePath(); } }

    public static boolean dreamcastFile(String name, boolean inDcFolder) {
        String n = name.toLowerCase(Locale.ROOT);
        if (n.startsWith(".")) return false;
        return n.endsWith(".gdi") || n.endsWith(".cdi") || (inDcFolder && n.endsWith(".chd"));
    }

    static boolean dcFolder(String rel) {
        String r = "/" + rel.toLowerCase(Locale.ROOT);
        return r.endsWith("/dc") || r.endsWith("dreamcast");
    }

    public static List<File> scan(List<File> roots) { return scan(roots, null); }
    /** scanned (optional) receives every folder that was looked in, for the "No games found in" note. */
    public static List<File> scan(List<File> roots, List<String> scanned) {
        List<File> out = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (File root : roots) {
            if (root == null) continue;
            for (String rel : FOLDERS) for (File d : resolve(root, rel)) {
                if (!seen.add(key(d))) continue;   // ROMs/dc and roms/dc are the same folder on FAT/sdcardfs
                if (scanned != null) scanned.add(d.getAbsolutePath());
                walk(d, dcFolder(rel), 0, out);
                if (out.size() >= MAX_FILES) return out;
            }
        }
        return out;
    }

    /** Android's standard folders on a volume root: never game folders. */
    static final String[] STANDARD = {"alarms", "dcim", "music", "movies", "pictures", "podcasts", "recordings", "ringtones",
            "notifications", "audiobooks", "documents", "android", "lost.dir", "system volume information"};
    static boolean standard(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        for (String x : STANDARD) if (x.equals(n)) return true;
        return n.startsWith(".");
    }

    /** 0.10.10: game folders at the ROOT of a removable volume (SD card / USB stick), e.g. /storage/CA86-E82D/Crazy Taxi/x.gdi.
     *  .gdi/.cdi always; .chd only when the volume is labelled Dreamcast or the path says dreamcast. */
    public static void scanVolumeRoot(File root, String label, List<File> out, List<String> scanned) {
        File[] fs = root == null ? null : root.listFiles();
        if (fs == null) return;
        boolean dcVol = (label != null && label.toLowerCase(Locale.ROOT).contains("dreamcast")) || dcPath(root.getAbsolutePath());
        java.util.Arrays.sort(fs);
        if (scanned != null) scanned.add(root.getAbsolutePath() + (dcVol ? " (Dreamcast card)" : ""));
        for (File f : fs) {
            if (out.size() >= MAX_FILES) return;
            if (f.isDirectory()) { if (!standard(f.getName())) walk(f, dcVol || dcPath(f.getName()), 1, out); }
            else if (dreamcastFile(f.getName(), dcVol)) addOnce(f, out);
        }
    }
    static boolean dcPath(String p) { return p.toLowerCase(Locale.ROOT).contains("dreamcast"); }
    static void addOnce(File f, List<File> out) {
        String k = key(f);
        for (File o : out) if (key(o).equals(k)) return;
        out.add(f);
    }

    private static void walk(File d, boolean dc, int depth, List<File> out) {
        File[] fs = d.listFiles();
        if (fs == null) return;
        java.util.Arrays.sort(fs);
        for (File f : fs) {
            if (out.size() >= MAX_FILES) return;
            if (f.isDirectory()) { if (depth < MAX_DEPTH && !f.getName().startsWith(".")) walk(f, dc, depth + 1, out); }
            else if (dreamcastFile(f.getName(), dc)) {
                // a .gdi's own .bin/.raw tracks sit next to it; the .gdi is the one file to open
                boolean dup = false; for (File o : out) if (o.getAbsolutePath().equals(f.getAbsolutePath())) dup = true;
                if (!dup) out.add(f);
            }
        }
    }

    /** USB OTG sticks and SD cards: every volume under /storage (not the emulated one) and /mnt/media_rw (often root-only). */
    public static List<File> volumeRoots(File storage, File mediaRw) {
        List<File> out = new ArrayList<>();
        for (File base : new File[]{storage, mediaRw}) {
            File[] fs = base == null ? null : base.listFiles();
            if (fs == null) continue;
            java.util.Arrays.sort(fs);
            for (File f : fs) {
                String n = f.getName();
                if (n.equals("emulated") || n.equals("self") || n.startsWith(".") || !f.isDirectory()) continue;
                out.add(f);
            }
        }
        return out;
    }

    // ---------- the player's OWN Dreamcast BIOS (never bundled or downloaded) ----------
    public static final String[] BIOS_NAMES = {"dc_boot.bin", "dc_bios.bin", "dc_flash.bin"};
    public static final String[] BIOS_FOLDERS = {"", "bios", "bios/dc", "bios/dreamcast", "roms/bios", "roms/dreamcast/bios", "roms/dc", "roms/dreamcast",
            "dreamcast", "download", "downloads", "retroarch/system/dc", "system/dc"};
    /** Flycast's data folder (its default home on Android); BIOS files go in here. */
    public static final String FLYCAST_DATA = "Android/data/com.flycast.emulator/files/data";

    /** First copy of each BIOS name found, plausible size only (dc_boot 2 MB, dc_flash 128 KB). */
    public static List<File> findBios(List<File> roots) {
        List<File> out = new ArrayList<>(); java.util.Set<String> got = new java.util.HashSet<>();
        for (File root : roots) {
            if (root == null) continue;
            for (String rel : BIOS_FOLDERS) for (File dir : resolve(root, rel)) for (String n : BIOS_NAMES) {
                File f = new File(dir, n);
                String key = n.equals("dc_bios.bin") ? "dc_boot.bin" : n;
                if (got.contains(key) || !f.isFile()) continue;
                long len = f.length();
                boolean ok = key.equals("dc_boot.bin") ? len == 2097152 : len == 131072;
                if (ok) { got.add(key); out.add(f); }
            }
        }
        return out;
    }

    /** Flycast looks for dc_boot.bin (dc_bios.bin is an alias, copied under its own name). */
    public static String targetName(File bios) { return bios.getName(); }

    static boolean safe(String p) { return p != null && p.startsWith("/") && p.matches("[A-Za-z0-9/._()+-]+") && !p.contains(".."); }

    /** Root copy into Flycast's data folder, owned by Flycast (other apps can't write there on Android 11+). Null = unsafe path. */
    public static String rootCopyCmd(String src, String dataDir) {
        if (!safe(src) || !safe(dataDir)) return null;
        String dst = dataDir + "/" + new File(src).getName();
        return "mkdir -p '" + dataDir + "' && cp '" + src + "' '" + dst + "' && chown $(stat -c %u:%g '" + new File(dataDir).getParent() + "') '"
                + dataDir + "' '" + dst + "' && chmod 660 '" + dst + "'";
    }

    /** "Crazy Taxi (USA).gdi" -> "Crazy Taxi (USA)". */
    public static String title(File f) {
        String n = f.getName(); int i = n.lastIndexOf('.');
        return i > 0 ? n.substring(0, i) : n;
    }
}

package com.codeaque.launcher;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Plain-Java checks (run by android/build.sh test). */
public class CatalogTest {
    static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }

    public static void main(String[] a) {
        List<Catalog.Category> c = Catalog.categories();
        check(c.get(0).id.equals("games") && c.get(0).items.get(0).action.equals("goto:cloud"), "Games starts with Cloud Gaming");
        check(Catalog.indexOf(c, "cloud") == 1 && Catalog.indexOf(c, "retro") == 2, "Cloud then Retro");
        Set<String> want = new HashSet<>(List.of("Xbox Cloud Gaming", "Amazon Luna", "GeForce NOW", "Netflix", "YouTube",
                "TikTok", "Kodi", "RetroArch", "Minecraft", "Roblox"));
        for (Catalog.Category cat : c) for (Catalog.Item i : cat.items) {
            want.remove(i.label);
            check(i.isAction() || i.storePackage() != null, i.label + " has a package for the Play Store link");
            check(i.webUrl == null || i.webUrl.startsWith("https://"), i.label + " web link is https");
            check(!i.icon.contains("logo"), "no logos");
        }
        check(want.isEmpty(), "missing tiles " + want);
        for (Catalog.Item i : c.get(Catalog.indexOf(c, "cloud")).items) check(i.webUrl != null, i.label + " works in a browser");
        check(c.get(Catalog.indexOf(c, "retro")).items.get(0).label.equals("RetroArch"), "Retro opens RetroArch first");
        check(Catalog.nextEffects("auto").equals("on") && Catalog.nextEffects("off").equals("auto"), "effects cycle");
        // themes: default unchanged and first, Power Up available, picker cycles
        check(Palette.byId("nope") == Palette.NEON && Palette.ALL[0] == Palette.NEON, "default theme is Neon");
        check(Palette.NEON.gold == 0xFF3FD4FF && !Palette.NEON.aura && !Palette.NEON.burst, "Neon: cyan accent, calm background");
        check(Palette.POWERUP.name.equals("Power Up") && Palette.POWERUP.aura, "Power Up theme");
        check(Palette.next("neon").equals("powerup") && Palette.next("powerup").equals("catch") && Palette.next("catch").equals("neon"), "theme picker cycles");
        check(Palette.CATCH.name.equals("Catch") && Palette.CATCH.burst && "chime".equals(Palette.CATCH.selectSound), "Catch theme");
        check("powerup".equals(Palette.POWERUP.selectSound) && Palette.NEON.selectSound == null, "theme sounds");
        Set<String> acts = new HashSet<>();
        for (Catalog.Item i : c.get(Catalog.indexOf(c, "settings")).items) acts.add(i.action);
        check(acts.contains("theme") && acts.contains("wallpaper"), "Settings has Theme and Wallpaper");
        check(Catalog.sampleSize(4000, 640) == 4 && Catalog.sampleSize(600, 640) == 1, "wallpaper is decoded small");
        System.out.println("CatalogTest: all checks passed");
    }
}

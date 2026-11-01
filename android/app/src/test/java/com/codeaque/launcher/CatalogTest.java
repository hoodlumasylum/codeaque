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
        System.out.println("CatalogTest: all checks passed");
    }
}

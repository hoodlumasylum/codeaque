package com.codeaque.launcher;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Curated, Play Store-verified Android games for Kids / Kids 12+ browsing and the parent
 * "Install kids games" picker. Only packages confirmed on play.google.com (en_GB contentRating)
 * are listed. PEGI 12 appears only in Kids 12+. No bundled third-party logos.
 * Plain Java so the filter, paging and queue can be unit-tested on a PC.
 */
public final class KidsPlay {
    private KidsPlay() { }

    public static final int PAGE = 8;
    public static final String PUZZLE = "puzzle";
    public static final String RACING = "racing";
    public static final String ADVENTURE = "adventure";
    public static final String LEARNING = "learning";
    public static final String STYLE = "style";
    public static final String[] CATS = {PUZZLE, RACING, ADVENTURE, LEARNING, STYLE};
    public static final String[] CAT_LABELS = {"Puzzle", "Racing", "Adventure", "Learning", "Roblox-style"};
    public static final String[] CAT_IDS = {"kpuzzle", "kracing", "kadventure", "klearning", "kstyle"};

    public static final class Game {
        public final String pkg, title, blurb, cat;
        public final int pegi;
        public Game(String cat, int pegi, String pkg, String title, String blurb) {
            this.cat = cat; this.pegi = pegi; this.pkg = pkg; this.title = title; this.blurb = blurb;
        }
        public Catalog.Item item() { return Catalog.app(title, "cube", pkg); }
    }

    /** Official listings, each checked on play.google.com/store/apps/details?id=... (en_GB) on 16 Nov 2026. */
    public static final Game[] ALL = {
            // Puzzle (PEGI 3)
            g(PUZZLE, 3, "com.king.candycrushsaga", "Candy Crush Saga", "Match-three puzzle. PEGI 3."),
            g(PUZZLE, 3, "com.king.candycrushsodasaga", "Candy Crush Soda Saga", "Match-three soda puzzle. PEGI 3."),
            g(PUZZLE, 3, "com.peoplefun.wordcross", "Wordscapes", "Word-search crossword puzzles. PEGI 3."),
            g(PUZZLE, 3, "com.easybrain.sudoku.android", "Sudoku.com", "Classic sudoku puzzles. PEGI 3."),
            g(PUZZLE, 3, "com.zeptolab.ctr.ads", "Cut the Rope", "Feed Om Nom by cutting ropes. PEGI 3."),
            g(PUZZLE, 3, "com.bigduckgames.flow", "Flow Free", "Connect matching colours in a grid. PEGI 3."),
            g(PUZZLE, 3, "com.mobilityware.solitaire", "Solitaire", "Classic card solitaire. PEGI 3."),
            g(PUZZLE, 3, "com.easybrain.nonogram", "Nonogram.com", "Picture logic grid puzzles. PEGI 3."),
            g(PUZZLE, 3, "com.easybrain.jigsaw.puzzles", "Jigsaw Puzzles", "Photo jigsaw puzzles. PEGI 3."),
            g(PUZZLE, 3, "com.easybrain.art.puzzle", "Art Puzzle", "Assemble picture puzzles. PEGI 3."),
            g(PUZZLE, 3, "com.king.bubblewitch3", "Bubble Witch 3 Saga", "Bubble-shooting puzzle. PEGI 3."),
            g(PUZZLE, 3, "com.bitmango.go.wordcookies", "Word Cookies!", "Bake words from a set of letters. PEGI 3."),
            g(PUZZLE, 3, "com.block.juggle", "Block Blast!", "Clear lines with falling blocks. PEGI 3."),
            g(PUZZLE, 3, "com.vitastudio.mahjong", "Vita Mahjong", "Mahjong solitaire. PEGI 3."),
            g(PUZZLE, 3, "com.n3twork.tetris", "Tetris", "The classic block puzzle. PEGI 3."),
            g(PUZZLE, 3, "jp.pokemon.poketoru", "Pokémon Shuffle Mobile", "Match-three with Pokémon. PEGI 3."),
            // Racing / runners
            g(RACING, 3, "com.sega.sonicdash", "Sonic Dash", "Endless runner with Sonic. PEGI 3."),
            g(RACING, 3, "com.imangi.templerun2", "Temple Run 2", "Endless running adventure. PEGI 3."),
            g(RACING, 3, "com.imangi.templerun", "Temple Run", "The original endless runner. PEGI 3."),
            g(RACING, 3, "com.vectorunit.purple.googleplay", "Beach Buggy Racing", "Kart racing on beaches and tracks. PEGI 3."),
            g(RACING, 3, "com.creativemobile.DragRacing", "Drag Racing", "Build a car and race in a straight line. PEGI 3."),
            g(RACING, 3, "com.budgestudios.googleplay.HotWheelsUnlimited", "Hot Wheels Unlimited", "Build tracks and race Hot Wheels. PEGI 3."),
            g(RACING, 7, "com.kiloo.subwaysurf", "Subway Surfers", "Endless runner on the tracks. PEGI 7."),
            // Adventure
            g(ADVENTURE, 3, "com.ustwo.monumentvalley", "Monument Valley", "Quiet puzzle-adventure in impossible architecture. PEGI 3."),
            g(ADVENTURE, 3, "com.ustwo.monumentvalley2", "Monument Valley 2", "A second journey through monument paths. PEGI 3."),
            g(ADVENTURE, 3, "com.nintendo.zara", "Super Mario Run", "Mario side-scrolling run. PEGI 3."),
            g(ADVENTURE, 3, "com.noodlecake.altosodyssey", "Alto's Odyssey", "Desert sandboarding journey. PEGI 3."),
            g(ADVENTURE, 3, "com.noodlecake.altosadventure", "Alto's Adventure", "Alpine sandboarding journey. PEGI 3."),
            g(ADVENTURE, 3, "com.pokemon.pokemonplayhouse", "Pokémon Playhouse", "Playful rooms with Pokémon. PEGI 3."),
            g(ADVENTURE, 3, "jp.pokemon.pokemonquest", "Pokémon Quest", "Explore a voxel island with Pokémon. PEGI 3."),
            g(ADVENTURE, 3, "com.budgestudios.googleplay.PawPatrolRescueWorld", "PAW Patrol Rescue World", "Rescue missions with the PAW Patrol. PEGI 3."),
            g(ADVENTURE, 7, "com.tgc.sky.android", "Sky: Children of the Light", "A gentle social adventure in a kingdom of light. PEGI 7."),
            g(ADVENTURE, 7, "com.innersloth.spacemafia", "Among Us", "Team up or find the impostor. PEGI 7."),
            g(ADVENTURE, 12, "com.halfbrick.dantheman", "Dan the Man", "Action platformer. PEGI 12 (Kids 12+)."),
            // Learning
            g(LEARNING, 3, "org.khankids.android", "Khan Academy Kids", "Free learning activities for young children. PEGI 3."),
            g(LEARNING, 3, "org.khanacademy.android", "Khan Academy", "Lessons and practice in many subjects. PEGI 3."),
            g(LEARNING, 3, "com.duolingo", "Duolingo", "Learn languages in short lessons. PEGI 3."),
            g(LEARNING, 3, "com.duolingo.literacy", "Duolingo ABC", "Learn to read with short lessons. PEGI 3."),
            g(LEARNING, 3, "org.pbskids.gamesapp", "PBS KIDS Games", "Games from PBS KIDS shows. PEGI 3."),
            g(LEARNING, 3, "org.scratchjr.android", "ScratchJr", "Introduce coding with blocks and characters. PEGI 3."),
            g(LEARNING, 3, "com.google.android.apps.seekh", "Read Along by Google", "Reading practice with a helpful listener. PEGI 3."),
            g(LEARNING, 3, "com.getepic.Epic", "Epic: Kids' Books & Reading", "A children's digital library. PEGI 3."),
            g(LEARNING, 3, "com.prodigygame.prodigy", "Prodigy Math", "Math practice in a game world. PEGI 3."),
            g(LEARNING, 3, "jp.pokemon.pokemonsmile", "Pokémon Smile", "Brush-your-teeth game with Pokémon. PEGI 3."),
            // Roblox-style sandbox / social creation (Blockman GO PEGI 16 and Rec Room unavailable in GB are omitted)
            g(STYLE, 3, "com.tocaboca.tocalifeworld", "Toca Boca World", "Build a world and make up stories. PEGI 3. Single-player, no chat."),
            g(STYLE, 3, "com.tocaboca.tocakitchen2", "Toca Boca Jr", "Open-ended play for younger kids. PEGI 3."),
            g(STYLE, 3, "com.nimblebit.legotower", "LEGO Tower", "Build and run a LEGO tower of shops and homes. PEGI 3."),
            g(STYLE, 3, "com.movile.playkids.pkxd", "PK XD", "A social world to dress up, play and build. PEGI 3."),
            g(STYLE, 7, "com.mojang.minecraftpe", "Minecraft", "Build, explore and create in a block world. PEGI 7."),
            g(STYLE, 7, "com.rtsoft.growtopia", "Growtopia", "Build worlds and play with others in a pixel sandbox. PEGI 7."),
            g(STYLE, 7, "com.haegin.playtogether", "Play Together", "A social hangout world with friends. PEGI 7."),
            g(STYLE, 12, "com.and.games505.TerrariaPaid", "Terraria", "Dig, build and explore a 2D sandbox. PEGI 12 (Kids 12+)."),
            g(STYLE, 12, "com.chucklefish.stardewvalley", "Stardew Valley", "Run a farm, explore and make friends. PEGI 12 (Kids 12+)."),

            // Adult-only by rating (hidden in Kids / Kids 12+; shown unfiltered in adult mode)
            g(PUZZLE, 16, "com.playrix.gardenscapes", "Gardenscapes", "Match-three garden restoration. PEGI 16."),
            g(PUZZLE, 16, "com.playrix.homescapes", "Homescapes", "Match-three home makeover. PEGI 16."),
            g(PUZZLE, 16, "com.gramgames.mergedragons", "Merge Dragons!", "Merge creatures and heal the land. PEGI 16."),
            g(PUZZLE, 16, "com.rovio.baba", "Angry Birds 2", "Slingshot puzzle with birds and pigs. PEGI 16."),
            g(PUZZLE, 18, "com.moonactive.coinmaster", "Coin Master", "Village building with coin raids. PEGI 18."),
            g(RACING, 16, "com.fingersoft.hillclimb", "Hill Climb Racing", "Drive up hills in physics vehicles. PEGI 16."),
            g(RACING, 16, "com.gameloft.android.ANMP.GloftA9HM", "Asphalt Legends", "Arcade street racing. PEGI 16."),
            g(RACING, 16, "com.nintendo.zaka", "Mario Kart Tour", "Mario Kart on tour. PEGI 16."),
            g(RACING, 16, "com.naturalmotion.customstreetracer2", "CSR 2", "Drag racing with custom cars. PEGI 16."),
            g(ADVENTURE, 16, "com.nianticlabs.pokemongo", "Pokémon GO", "Find Pokémon in the real world. PEGI 16."),
            g(STYLE, 16, "com.sandboxol.blockymods", "Blockman GO", "Sandbox mini-games and building. PEGI 16."),
            g(STYLE, 16, "com.fungames.blockcraft", "Block Craft 3D", "Build in a block world. PEGI 16."),
            g(STYLE, 16, "com.ea.games.simsfreeplay_row", "The Sims FreePlay", "Life simulation in a town. PEGI 16."),
            g(PUZZLE, 16, "com.playrix.township", "Township", "Build a town and harvest crops. PEGI 16."),
    };

    private static Game g(String cat, int pegi, String pkg, String title, String blurb) {
        return new Game(cat, pegi, pkg, title, blurb);
    }

    /** Kids = 7, Kids 12+ = 12, adult (no age filter) = {@link #ADULT}. */
    public static final int ADULT = 18;
    public static int maxPegi(boolean kids12) { return kids12 ? 12 : 7; }

    /** Adult ({@code maxPegi >= ADULT} or {@code maxPegi < 0}): every rated title. Kids: 3 and 7. Kids 12+: also 12. */
    public static boolean allowed(int pegi, int maxPegi) {
        if (maxPegi < 0 || maxPegi >= ADULT) return pegi >= 3;
        if (pegi == 3 || pegi == 7) return true;
        return pegi == 12 && maxPegi >= 12;
    }

    public static String catId(String cat) {
        for (int i = 0; i < CATS.length; i++) if (CATS[i].equals(cat)) return CAT_IDS[i];
        return null;
    }

    public static String catFromId(String id) {
        for (int i = 0; i < CAT_IDS.length; i++) if (CAT_IDS[i].equals(id)) return CATS[i];
        return null;
    }

    /** Deduped, age-filtered games in a category, stable order. */
    public static List<Game> list(String cat, int maxPegi) {
        List<Game> out = new ArrayList<>();
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        if (cat == null) return out;
        for (Game g : ALL) {
            if (!cat.equals(g.cat) || !allowed(g.pegi, maxPegi)) continue;
            if (!seen.add(g.pkg)) continue;
            out.add(g);
        }
        return out;
    }

    public static List<Game> page(String cat, int maxPegi, int offset, int limit) {
        List<Game> all = list(cat, maxPegi);
        if (offset < 0) offset = 0;
        if (limit < 1) limit = PAGE;
        if (offset >= all.size()) return new ArrayList<>();
        int end = Math.min(all.size(), offset + limit);
        return new ArrayList<>(all.subList(offset, end));
    }

    public static List<Catalog.Item> items(String cat, int maxPegi, int offset, int limit) {
        List<Catalog.Item> out = new ArrayList<>();
        for (Game g : page(cat, maxPegi, offset, limit)) out.add(g.item());
        return out;
    }

    public static Game byPkg(String pkg) {
        if (pkg == null) return null;
        for (Game g : ALL) if (pkg.equals(g.pkg)) return g;
        return null;
    }

    public static String blurb(Catalog.Item it) {
        if (it == null) return "";
        Game g = it.storePackage() != null ? byPkg(it.storePackage()) : null;
        if (g != null) return g.blurb;
        if (it == Catalog.ROBLOX) return "Official Roblox app. Open a place from this list, or install Roblox from the Play Store.";
        if (it == Catalog.XBOX) return "Xbox Cloud Gaming — play in the app or at xbox.com/play.";
        if (it == Catalog.GFN || it == Catalog.GEFORCE_NOW) return "GeForce NOW — play in the app or in a browser.";
        if (it == Catalog.LUNA) return "Amazon Luna cloud gaming.";
        if (it == Catalog.BOOST) return "Boosteroid cloud gaming.";
        return it.isAction() ? "" : (it.webUrl != null ? "Opens in your browser, or the app once installed." : "");
    }

    /** Play Store queue: skip installed and duplicates, keep order. */
    public interface Installed { boolean installed(String pkg); }

    public static List<String> storeQueue(List<String> pkgs, Installed have) {
        List<String> out = new ArrayList<>();
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        if (pkgs == null) return out;
        for (String p : pkgs) {
            if (p == null || p.isEmpty() || !p.matches("[A-Za-z0-9_.]+")) continue;
            if (!seen.add(p)) continue;
            if (have != null && have.installed(p)) continue;
            out.add(p);
        }
        return out;
    }

    public static boolean knownPkg(String pkg) { return byPkg(pkg) != null; }

    public static int count(String cat, int maxPegi) { return list(cat, maxPegi).size(); }
}

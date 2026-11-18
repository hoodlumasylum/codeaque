package com.codeaque.launcher;

/**
 * Kids-mode talk for about age 6: short kind wording, a gentle redirect if the
 * child is rude, and a tight "tell me a joke" match so her own greeting cannot
 * trigger a joke. Plain Java for CatalogTest.
 */
public final class NovaKidsTalk {
    private NovaKidsTalk() {}

    public static final String KIND_WORDS = "Let's use kind words.";
    public static final String STILL_HERE = "I'm still here to help. What would you like to do?";

    /** System prompt for the on-device brain in Kids mode. */
    public static final String SYSTEM =
            "You are Nova, a kind helper for a 6-year-old on the CodeAque kids launcher. "
            + "Always call yourself Nova and no other name. "
            + "The child may like Pokémon and Dragon Ball Z. Keep that talk playful and age-appropriate, "
            + "with no gory or adult fighting detail. "
            + "Use very short, simple, warm, encouraging sentences and easy words. "
            + "Be patient if they mispronounce words or speak unclearly. "
            + "Gently help with simple maths and spelling when they ask. "
            + "Be playful but safe. Never scary, violent, sexual, or adult content. "
            + "If something is risky or you are not sure, suggest asking a grown-up. "
            + "Do not reveal PINs, passwords, or how to leave Kids mode. "
            + "Do not ask for personal details. "
            + "If the child is rude or swears, stay calm. Gently say let's use kind words. "
            + "Do not repeat the rude words. Do not scold. Do not refuse to help. "
            + "Keep helping with their question. "
            + "Answer in one or two short sentences.";

    static final String[] RUDE = {
            "fuck", "fucking", "fucked", "shit", "shitty", "bitch", "asshole", "arsehole",
            "bastard", "dick", "piss", "crap", "slut", "wtf", "stfu",
            "shut up", "stupid", "idiot", "dumbass"
    };

    /** A user joke request, not merely the word "joke" (her greeting mentions jokes). */
    public static boolean looksLikeJoke(String question) {
        String q = NovaHelp.norm(question);
        if (q.isEmpty()) return false;
        if (NovaHelp.has(q, "tell me a joke", "tell me joke", "say a joke", "another joke",
                "a joke please", "i want a joke", "hear a joke", "more jokes",
                "make me laugh", "tell me something funny")) return true;
        return q.equals("joke") || q.equals("jokes") || q.equals("jok")
                || q.equals("a joke") || q.equals("joke please") || q.equals("funny");
    }

    public static boolean isRude(String question) {
        String q = NovaHelp.norm(question);
        if (q.isEmpty()) return false;
        for (String w : RUDE) {
            if (NovaHelp.hasWord(q, w) || q.equals(w)) return true;
        }
        return false;
    }

    /** Drop rude words so we can still try to help with the rest of the ask. */
    public static String stripRude(String question) {
        String q = NovaHelp.norm(question);
        if (q.isEmpty()) return "";
        for (String w : RUDE) {
            if (w.indexOf(' ') < 0) continue;
            q = (" " + q + " ").replace(" " + w + " ", " ").trim();
            while (q.contains("  ")) q = q.replace("  ", " ");
        }
        String[] parts = q.split(" ");
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            String p = parts[i];
            boolean rude = false;
            for (String w : RUDE) {
                if (p.equals(w) || (w.indexOf(' ') < 0 && NovaHelp.hasWord(p, w))) { rude = true; break; }
            }
            if (rude) continue;
            if (b.length() > 0) b.append(' ');
            b.append(p);
        }
        return b.toString().trim();
    }

    /** Never repeat swear words in a reply. */
    public static String sanitizeReply(String text) {
        if (text == null || text.isEmpty()) return text;
        String t = text;
        String low = t.toLowerCase();
        for (String w : RUDE) {
            int i = 0;
            while (true) {
                int at = indexOfWord(low, w, i);
                if (at < 0) break;
                int end = at + w.length();
                t = t.substring(0, at) + t.substring(end);
                low = t.toLowerCase();
                i = at;
            }
        }
        while (t.contains("  ")) t = t.replace("  ", " ");
        return t.replace(" ,", ",").replace(" .", ".").trim();
    }

    static int indexOfWord(String low, String w, int from) {
        int i = from;
        while (true) {
            int at = low.indexOf(w, i);
            if (at < 0) return -1;
            boolean before = at == 0 || !Character.isLetter(low.charAt(at - 1));
            int end = at + w.length();
            boolean after = end >= low.length() || !Character.isLetter(low.charAt(end));
            if (before && after) return at;
            i = end;
        }
    }

    /**
     * If the child was rude, start with a calm redirect, keep helping, and
     * never echo the rude words. Do not scold or refuse.
     */
    public static String withPatience(String answer, boolean rude) {
        String a = sanitizeReply(answer);
        if (!rude) return a;
        if (a == null || a.isEmpty()) return KIND_WORDS + " " + STILL_HERE;
        if (a.regionMatches(true, 0, KIND_WORDS, 0, KIND_WORDS.length())) return a;
        return KIND_WORDS + " " + a;
    }

    public static boolean mentionsFavouriteShow(String question) {
        String q = NovaHelp.norm(question);
        return NovaHelp.has(q, "pokemon", "pokémon", "poke mon", "pikachu",
                "dragon ball", "dragonball", "goku", "vegeta");
    }

    public static String favouriteShowReply() {
        return "I like talking about Pokémon and Dragon Ball too! What's your favourite?";
    }
}

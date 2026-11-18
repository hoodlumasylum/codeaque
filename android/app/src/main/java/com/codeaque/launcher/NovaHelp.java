package com.codeaque.launcher;

/**
 * CodeAque helper answers. No accounts, no API keys, no data collection.
 * In Kids mode the local topics are games help, learning help, and clean jokes.
 * 0.10.54: an optional short online fact (Smarter Nova) may fill in a learning
 * question that is not in the local set. Kids answers never name the source.
 */
public final class NovaHelp {
    private NovaHelp() {}

    public static final String GREETING = "Hi, I'm Nova. I can help with CodeAque on this phone — updates, Kids mode, search, Screen Time, the parent PIN, and a few other Settings. If Smarter Nova is on I can look up a short fact when I'm not sure. I don't collect data.";

    public static final String LIMITS = "I know simple CodeAque help stored on this phone. If Smarter Nova is on I can look up a short fact when a question isn't already here. I don't use an account. Licence details are in About. I stay in CodeAque — I don't sit over other apps. Double-tap hides me until you turn Show Nova on in the Nova tab.";

    public static final String KIDS_GREETING = "Hi, I'm Nova! We can find a game. I can also help with words and numbers. There is a joke button if you want one.";

    public static final String KIDS_FOOT = "I can help with games, words, numbers, or a joke. For anything else, ask a grown-up.";

    public static final String KIDS_UNSURE = "I'm not sure. Let's ask a grown-up.";

    /** Clean, silly jokes for about age 6. No insults, toilet humour, scares or religion. */
    public static final String[] JOKES = {
            "Why did the banana go to the doctor? It wasn't peeling well.",
            "What do you call a bear with no teeth? A gummy bear.",
            "Why do bees have sticky hair? They use honeycombs.",
            "What do you call cheese that isn't yours? Nacho cheese.",
            "Why did the cookie go to the doctor? It felt crummy.",
            "What's a cat's favourite colour? Purr-ple.",
            "Why did the bicycle fall over? It was two-tired.",
            "What do you call a pig that does karate? A pork chop.",
            "What do you call a dinosaur that loves to sleep? A dino-snore.",
            "Why did the scarecrow win an award? He was outstanding in his field.",
            "Why did the tomato turn red? It saw the salad dressing.",
            "What's orange and sounds like a parrot? A carrot.",
            "Why did the cow look at the sky? To see the moo-n.",
            "What has ears but cannot hear? A field of corn.",
            "What do you call a fish with no eyes? A fsh.",
            "What kind of room has no doors or windows? A mushroom.",
            "Why did the frog take the bus? His car was toad away.",
            "Why did the stadium get so warm? Because of all the fans.",
            "What do you call an alligator in a vest? An investigator.",
            "Why was the broom late? It overswept.",
            "How does a scientist freshen her breath? With experi-mints.",
            "What do you call a dog magician? A labracadabrador.",
            "Why did the grape stop in the middle of the road? It ran out of juice.",
            "Why did the chicken cross the playground? To get to the other slide.",
            "What do cows play at parties? Moosical chairs.",
            "What's a snake's favourite subject? Hiss-tory.",
            "Why did the teddy bear say no to dessert? It was stuffed.",
            "What do you call a pony with a sore throat? A little hoarse.",
            "Why did the music teacher need a ladder? To reach the high notes.",
            "What kind of tree fits in your hand? A palm tree.",
            "Why did the calendar look so popular? It had lots of dates.",
            "What's a pirate's favourite letter? People think it's R, but it's the C.",
            "Why did the banana open a bakery? It had great a-peel."
    };

    public static String answer(String question) { return answer(question, false); }

    public static String answer(String question, boolean kids) {
        if (norm(question).isEmpty()) return kids ? KIDS_GREETING : GREETING;
        String a = kids ? kidsAnswer(question) : adultAnswer(question);
        if (a != null) return a;
        return kids ? KIDS_UNSURE : ("I'm not sure about that. Try asking about updates, Kids mode, search, Screen Time, the parent PIN, covers, Wi-Fi, themes, or Nova. " + LIMITS);
    }

    /** A confident canned reply, or null so the caller can send the question to the LLM. */
    public static String cannedOrNull(String question, boolean kids) {
        return kids ? kidsAnswer(question) : adultAnswer(question);
    }

    /** Instant local reply when the on-device brain is missing or too slow. */
    public static String bestEffort(String question, boolean kids) {
        String a = answer(question, kids);
        if (a == null || a.isEmpty() || NovaSmallTalk.isUnreadyPhrase(a)
                || (NovaSmallTalk.isBareGreetingReply(a) && !NovaSmallTalk.isBareGreeting(question))) {
            return NovaSmallTalk.brainFallback(question, kids);
        }
        if (kids && kidsUnsafe(a)) return NovaSmallTalk.brainFallback(question, kids);
        return a;
    }

    public static String[] chips() {
        return new String[] { "How do I update?", "Kids mode", "Search", "Screen Time", "Parent PIN", "Nova" };
    }

    /** Learning + games help only (jokes have their own button). */
    public static String[] kidsChips() {
        return new String[] { "Find a game", "How do I play?", "Learning" };
    }

    public static int jokeCount() { return JOKES.length; }

    public static String jokeAt(int i) {
        int n = JOKES.length;
        if (n == 0) return "";
        int k = i % n;
        if (k < 0) k += n;
        return JOKES[k];
    }

    public static boolean looksLikeJoke(String question) {
        return NovaKidsTalk.looksLikeJoke(question);
    }

    static String kidsAnswer(String question) {
        String q = norm(question);
        if (q.isEmpty()) return null;
        if (looksLikeJoke(question))
            return "Tap Tell me a joke and I'll tell you one.";
        if (NovaKidsTalk.mentionsFavouriteShow(question))
            return NovaKidsTalk.favouriteShowReply();
        if (has(q, "learn", "learning", "school", "puzzle", "read", "maths", "math", "spell", "spelling", "count", "write"))
            return "The Learning tab has games for words and numbers. Scroll the row and tap one to play. If it isn't on the phone yet, tap GET. You can also type a name in Search at the top. I can help with simple spelling or maths too — just ask.";
        if (has(q, "search", "find", "look up", "where is", "how do i play", "how to play", "start a game", "open a game", "play a game", "get a game", "install", "game"))
            return "Look along the rows, or use Search at the top. Tap a game to play it. If you see GET, tap it to put that game on the phone.";
        if (has(q, "theme", "colour", "color", "wallpaper", "looks"))
            return "The Themes tile can change how CodeAque looks. Pick one you like. If you want help with something else, ask a grown-up.";
        if (has(q, "wifi", "wi-fi", "internet", "network"))
            return "If the internet isn't working, ask a grown-up to help with Wi-Fi.";
        if (NovaIntent.confident(q, "times up", "time's up", "time is up", "play time",
                "playtime", "screen time", "time limit", "time is finished"))
            return "If play time is finished, ask a grown-up whether you can play more.";
        return null;
    }

    static String adultAnswer(String question) {
        String q = norm(question);
        if (q.isEmpty()) return null;
        if (has(q, "update", "upgrade", "new version", "install apk", "latest"))
            return "To update CodeAque: open Settings and tap Update, or use Wi-Fi & Update in Kids mode. From 0.10.54, CodeAque's own Update in Kids mode does not need the parent PIN. Leaving Kids mode, Settings, installing other apps, and a device still on 0.10.53 (which needs the PIN once to install 0.10.54) still follow their usual PIN rules. CodeAque checks for a new APK and Android will ask you to confirm the install.";
        if (has(q, "kids mode", "kidsmode", "turn off kids", "leave kids", "grown up", "adult mode", "kid mode"))
            return "Kids mode shows a simpler home with games you allow. A parent turns it on from the Kids Mode tile on the home screen, or from Settings, with no PIN. Turning it off always needs the parent PIN. If Kids and Kids 12+ both exist, entering uses the last-used profile. Changing the profile is a parent setting and still needs the PIN.";
        if (has(q, "search", "find a game", "find game", "look up"))
            return "In Kids mode, use the search box at the top of the home screen to find a game on the home rows or on Google Play (a parent may need to approve a store install). In Adult mode, browse the rows or use All Apps. Search never leaves your phone except when you open Play.";
        if (has(q, "screen time", "screentime", "time's up", "times up", "play time", "time limit", "hours"))
            return "Screen Time is a parent setting (PIN). You can set a daily play limit, Strict mode (CodeAque comes back when time is up), and optional allowed hours. When time is up, the parent PIN unlocks more time or opens Settings. It does not replace the phone's own Digital Wellbeing.";
        if (has(q, "install game", "add game", "play store", "sideload", "apk", "get game", "without the pin", "ask pin to install", "install games", "get in kids"))
            return "In Kids mode, GET on an age-filtered game opens only that game's Play page so you can install it. That does not need the parent PIN unless a grown-up turns on Settings > Ask PIN to install games. Kids 12+ still only shows games allowed for 12+. Leaving Kids mode, Settings, raw Play browsing, sideloading, and Screen Time still need the PIN.";
        if (has(q, "forgot pin", "forget pin", "reset pin", "lost pin", "default pin"))
            return "The parent PIN is set in CodeAque (default on a fresh install is 1234 until you change it). If it's forgotten, clearing CodeAque's data in Android Settings > Apps > CodeAque > Storage resets the PIN to 1234. That also clears CodeAque's other settings. There is no online account to recover it.";
        if (has(q, "change pin", "changepin", "new pin", "parent pin", "the pin", "pin"))
            return "The parent PIN protects Kids mode, Screen Time, and other grown-up Settings. Change it in Settings > Change PIN (you'll enter the current PIN first). If Kids or Lock mode is on, grown-up tiles ask for the PIN. Don't share it with the child.";
        if (has(q, "overlay", "over other", "over games", "display over", "bubble over", "float", "system_alert", "draw over"))
            return "Nova stays in CodeAque and does not sit over other apps. Double-tap hides her until you turn Show Nova on in the Nova tab (also in Kids mode). Keep listening while hidden is optional and off unless you turn it on — then she can hear you and answer by voice while hidden, even in a game, without drawing over it.";
        if (has(q, "hide nova", "turn off nova", "remove nova", "show nova", "where is nova"))
            return "Nova is the helper in the corner of CodeAque, including in Kids mode. Tap her to open Chat, tap again or Back to close. Drag her to move her. Double-tap hides her until you turn Show Nova on in the Nova tab. That does not need a PIN.";
        if (has(q, "cover", "box art", "boxart", "poster", "artwork", "screenshot"))
            return "Game tiles fill the cover edge-to-edge. Art comes from the game's icon, a saved screenshot, or downloaded box art for some retro files. If a cover looks wrong, try Rescan for games in Settings, or add a game folder for retro files.";
        if (has(q, "wifi", "wi-fi", "internet", "network"))
            return "Open Settings > Wi-Fi (this one does not need the parent PIN, even in Lock mode) and pick a network in Android's Wi-Fi screen.";
        if (has(q, "theme", "wallpaper", "christmas", "colour", "color"))
            return "Settings > Theme previews the built-in looks. Wallpaper lets you pick a photo, or turn it off to use the theme background. Kids mode has a Themes tile that does not need the PIN, and only changes the look.";
        if (has(q, "lock mode", "lockmode", "lock the"))
            return "Lock mode (parent Settings, PIN to turn on) hides a few sensitive tiles and asks for the PIN before other grown-up actions. It is separate from Kids mode and from Lock phone to CodeAque. Wi-Fi, Update, and Change PIN still follow their usual rules.";
        if (has(q, "home app", "make home", "default home", "launcher", "set as home"))
            return "To make CodeAque the home screen: Settings > Make CodeAque home, or Android's Home app settings. If the Home chooser keeps coming back, pick CodeAque and Always. Exit CodeAque (PIN) opens those system settings if you want a different home app.";
        if (has(q, "privacy", "data", "account", "api key", "online", "collect"))
            return "Nova has no account and no API key. Local answers are written into CodeAque. If Smarter Nova is on, a question that isn't already on the phone may look up a short fact; licence details are in About. I don't collect what you type. Speech, if you use it, uses the phone's own recognizer and voice.";
        if (has(q, "scroll", "smooth", "stutter", "lag"))
            return "The home rows should still scroll smoothly — Nova is drawn in her own corner view, not on the game-row scroller. You can hide her in Settings > Nova if you want the corner clear.";
        return null;
    }

    static String norm(String s) {
        if (s == null) return "";
        String t = s.trim().toLowerCase();
        StringBuilder b = new StringBuilder(t.length());
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            b.append((c == '?' || c == '!' || c == '.' || c == ',' || c == '\'' ) ? ' ' : c);
        }
        return b.toString().replaceAll(" +", " ").trim();
    }

    static boolean has(String q, String... keys) {
        for (String k : keys) if (NovaIntent.containsPhrase(q, k)) return true;
        return false;
    }

    /** True if the text looks unsafe to show a child (links, PIN, adult or violent wording). Word-boundary checks so "pee" does not match "peeling". */
    public static boolean kidsUnsafe(String text) {
        if (text == null) return false;
        String t = text.toLowerCase();
        if (t.contains("http://") || t.contains("https://") || t.contains("www.")) return true;
        if (t.contains("1234")) return true;
        if (t.contains("parent pin") || t.contains("the pin") || t.contains("enter the pin")) return true;
        if (t.contains("sideload") || t.contains("overlay") || t.contains("display over")) return true;
        if (t.contains("leave kids") || t.contains("turn off kids") || t.contains("exit codeaque")) return true;
        if (hasWord(t, "poo", "pee", "toilet", "kill", "dead", "hell", "damn", "stupid", "idiot",
                "god", "jesus", "christ", "blood", "sex", "porn", "naked", "murder", "suicide",
                "nazi", "conspiracy", "illuminati", "shoot", "weapon", "gun")) return true;
        return false;
    }

    /**
     * 0.10.54: a learning-style question that is not already in the local set may use Smarter Nova.
     * Jokes, game-tab help, Wi-Fi, time-up, Settings, PIN, and leave-Kids questions stay local.
     */
    public static boolean kidsMayAskOnline(String question) {
        String q = norm(question);
        if (q.isEmpty()) return false;
        if (looksLikeJoke(question)) return false;
        if (has(q, "pin", "password", "settings", "grown-up", "grown up", "adult mode",
                "leave kids", "turn off kids", "kidsoff", "overlay", "sideload", "apk",
                "parent", "update", "upgrade")) return false;
        if (has(q, "wifi", "wi-fi")) return false;
        if (has(q, "time's up", "times up", "screen time", "play time")) return false;
        // Already-answered local topics (games row, learning tab, themes) stay on the phone.
        String local = kidsAnswer(question);
        return local == null;
    }

    static boolean hasWord(String t, String... words) {
        if (t == null) return false;
        for (String w : words) {
            if (w == null || w.isEmpty()) continue;
            int i = 0;
            while ((i = t.indexOf(w, i)) >= 0) {
                boolean before = i == 0 || !Character.isLetter(t.charAt(i - 1));
                int end = i + w.length();
                boolean after = end >= t.length() || !Character.isLetter(t.charAt(end));
                if (before && after) return true;
                i = end;
            }
        }
        return false;
    }
}

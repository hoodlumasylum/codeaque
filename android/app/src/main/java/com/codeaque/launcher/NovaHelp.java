package com.codeaque.launcher;

/** Offline CodeAque helper answers. No network, no accounts, no data collection. */
public final class NovaHelp {
    private NovaHelp() {}

    public static final String GREETING = "Hi, I'm Nova. I can help with CodeAque on this phone — updates, Kids mode, search, Screen Time, the parent PIN, and a few other Settings. I don't go online and I don't collect data.";

    public static final String LIMITS = "I only know simple CodeAque help stored on this phone. I don't use an account or an internet assistant. Nova over other games needs a parent PIN and Android's Display over other apps permission — some phones, full-screen games, or Kids lock may still hide or block me.";

    public static String answer(String question) {
        String q = norm(question);
        if (q.isEmpty()) return GREETING;
        if (has(q, "who are you", "who is nova", "hello", "hi nova", "hey nova", "what can you", "help", "what do you"))
            return GREETING + " " + LIMITS;
        if (has(q, "update", "upgrade", "new version", "install apk", "latest"))
            return "To update CodeAque: open Settings (parent PIN if Kids mode is on) and tap Update. CodeAque checks for a new APK and Android will ask you to confirm the install. You can also sideload a CodeAque APK the usual way. I don't download anything myself.";
        if (has(q, "kids mode", "kidsmode", "turn off kids", "leave kids", "grown up", "adult mode", "kid mode"))
            return "Kids mode shows a simpler home with games you allow. A parent turns it on from Settings, or with the Kids tile. Turning it off always needs the parent PIN. There is also Kids 12+, Always start in Kids mode, and Lock phone to CodeAque in Kids mode — those are parent Settings, also behind the PIN.";
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
            return "Nova can sit in the CodeAque corner without any extra permission. Showing her over other games is optional and off by default. A parent turns it on in Settings > Nova (PIN), then grants Android's Display over other apps permission. It stays off while Kids mode is on. Some phones, full-screen or lock-task games, or OEM game modes may still cover or block the bubble. You can revoke the permission in Android Settings at any time.";
        if (has(q, "hide nova", "turn off nova", "remove nova", "show nova", "where is nova"))
            return "Nova is the circle in the corner of CodeAque. Tap her to open this help, tap again or Back to close. Show or hide her in Settings > Nova. That does not need a PIN. Showing her over other games does, and is off in Kids mode.";
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
            return "Nova has no account, no API key, and no chat server. Answers are written into CodeAque. I don't collect or send what you type here. System text-to-speech, if you use Hear it, stays on the phone.";
        if (has(q, "scroll", "smooth", "stutter", "lag"))
            return "The home rows should still scroll smoothly — Nova is drawn in her own corner view, not on the game-row scroller. You can hide her in Settings > Nova if you want the corner clear.";
        return "I'm not sure about that. Try asking about updates, Kids mode, search, Screen Time, the parent PIN, covers, Wi-Fi, themes, or Nova over games. " + LIMITS;
    }

    public static String[] chips() {
        return new String[] { "How do I update?", "Kids mode", "Search", "Screen Time", "Parent PIN", "Nova over games" };
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
        for (String k : keys) if (q.contains(k)) return true;
        return false;
    }
}

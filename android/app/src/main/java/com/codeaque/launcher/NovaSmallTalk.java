package com.codeaque.launcher;

/**
 * Kid-safe canned small talk, and a tight greeting match. Filler words are
 * stripped first so "uh, how are you?" is not treated as a hello.
 * Plain Java for CatalogTest.
 */
public final class NovaSmallTalk {
    private NovaSmallTalk() {}

    public static final String HOW_ARE_KIDS = "I'm really well, thank you! How are you?";
    public static final String HOW_ARE_ADULT = "I'm well, thanks. How are you?";
    public static final String NAME_KIDS = "I'm Nova. I can help you find a game, or with words and numbers.";
    public static final String NAME_ADULT = "I'm Nova, the helper in CodeAque.";
    public static final String CAN_DO_KIDS = "I can help you find a game, and I can help with words and numbers. What would you like to do?";
    public static final String CAN_DO_ADULT = "I can help with CodeAque: updates, Kids mode, search, Screen Time, and Settings. What would you like to do?";
    public static final String THANKS_KIDS = "You're welcome!";
    public static final String THANKS_ADULT = "You're welcome.";
    public static final String BYE_KIDS = "Bye! Come back whenever you like.";
    public static final String BYE_ADULT = "Goodbye. I'm here if you need me.";
    public static final String HELLO_KIDS = "Hi! I'm Nova. How are you?";
    public static final String HELLO_ADULT = "Hi! I'm Nova. How can I help?";

    /** Leading/trailing fillers only: uh, um, u, so, oh, and the like. */
    static final String[] FILLERS = {
            "uh", "um", "umm", "er", "erm", "ah", "oh", "o", "u", "ut",
            "hmm", "hm", "mmm", "mm", "like", "well", "so", "and"
    };

    public static String tidy(String raw) {
        return normalize(stripFillers(NovaAsrText.tidy(raw)));
    }

    public static String stripFillers(String s) {
        if (s == null) return "";
        String t = NovaSpeakGate.norm(s);
        if (t.isEmpty()) return "";
        String[] w = t.split(" ");
        int i = 0, j = w.length - 1;
        while (i <= j && isFiller(w[i])) i++;
        while (j >= i && isFiller(w[j])) j--;
        if (i > j) return "";
        StringBuilder b = new StringBuilder();
        for (int k = i; k <= j; k++) {
            if (b.length() > 0) b.append(' ');
            b.append(w[k]);
        }
        return b.toString();
    }

    static boolean isFiller(String w) {
        if (w == null) return true;
        for (int i = 0; i < FILLERS.length; i++) if (FILLERS[i].equals(w)) return true;
        return false;
    }

    /** Cheap ASR repairs used only for intent matching. */
    public static String normalize(String s) {
        if (s == null || s.isEmpty()) return "";
        String t = s;
        t = replaceWord(t, "hallo", "hello");
        t = replaceWord(t, "hullo", "hello");
        t = replaceWord(t, "halo", "hello");
        t = replaceWord(t, "helo", "hello");
        t = replaceWord(t, "hai", "hi");
        t = replaceWord(t, "heyya", "hey");
        t = replaceWord(t, "ya", "you");
        // Apostrophes become a space in norm ("what's" -> "what s").
        t = t.replace("what s ", "whats ");
        t = t.replace("how s ", "hows ");
        t = t.replace("that s ", "thats ");
        t = t.replace("it s ", "its ");
        t = t.replace("who s ", "whos ");
        t = t.replace("let s ", "lets ");
        t = t.replace("there s ", "theres ");
        t = t.replace(" i m ", " im ");
        if (t.startsWith("i m ")) t = "im " + t.substring(4);
        if (t.endsWith(" i m")) t = t.substring(0, t.length() - 4) + " im";
        if (t.equals("i m")) t = "im";
        // "how were you" is a very common mishearing of "how are you".
        if (t.equals("how were you") || t.equals("how was you")
                || t.equals("how r you") || t.equals("how re you")
                || t.equals("how you") || t.equals("how you doing")
                || t.equals("how were ya")) t = "how are you";
        return t;
    }

    static String replaceWord(String t, String from, String to) {
        if (t.equals(from)) return to;
        if (t.startsWith(from + " ")) t = to + t.substring(from.length());
        if (t.endsWith(" " + from)) t = t.substring(0, t.length() - from.length()) + to;
        t = t.replace(" " + from + " ", " " + to + " ");
        return t;
    }

    /** A greeting and nothing else. Must not fire on "hello how are you". */
    public static boolean isBareGreeting(String q) {
        String n = tidy(q);
        if (n.isEmpty()) return false;
        return n.equals("hello") || n.equals("hello hello") || n.equals("hellohello")
                || n.equals("hi") || n.equals("hi hi") || n.equals("hey")
                || n.equals("hello there") || n.equals("hi there") || n.equals("hey there")
                || n.equals("hey nova") || n.equals("hi nova") || n.equals("hello nova")
                || n.equals("good morning") || n.equals("good afternoon")
                || n.equals("good evening") || n.equals("yo") || n.equals("hiya")
                || n.equals("howdy") || n.equals("morning") || n.equals("evening");
    }

    public static String reply(String q, boolean kids) {
        String n = tidy(q);
        if (n.isEmpty()) return null;
        if (isHowAreYou(n)) return kids ? HOW_ARE_KIDS : HOW_ARE_ADULT;
        if (isName(n)) return kids ? NAME_KIDS : NAME_ADULT;
        if (isCanDo(n)) return kids ? CAN_DO_KIDS : CAN_DO_ADULT;
        if (isThanks(n)) return kids ? THANKS_KIDS : THANKS_ADULT;
        if (isBye(n)) return kids ? BYE_KIDS : BYE_ADULT;
        if (isBareGreeting(n)) return kids ? HELLO_KIDS : HELLO_ADULT;
        return null;
    }

    static boolean isHowAreYou(String n) {
        return n.equals("how are you") || n.equals("how are ya")
                || n.equals("how are you doing") || n.equals("how are you today")
                || n.equals("hows it going") || n.equals("how is it going")
                || n.equals("how you doing") || n.equals("you ok") || n.equals("you okay")
                || n.equals("are you ok") || n.equals("are you okay");
    }

    static boolean isName(String n) {
        return n.equals("what is your name") || n.equals("whats your name")
                || n.equals("what's your name".replace("'", ""))
                || n.equals("who are you") || n.equals("who is nova")
                || n.equals("whats your name") || n.equals("your name")
                || n.equals("what are you called") || n.equals("who re you");
    }

    static boolean isCanDo(String n) {
        return n.equals("what can you do") || n.equals("what do you do")
                || n.equals("what can you help with") || n.equals("what can you")
                || n.equals("what do you help with") || n.equals("what are you for")
                || n.equals("how can you help") || n.equals("what can i ask");
    }

    static boolean isThanks(String n) {
        return n.equals("thanks") || n.equals("thank you") || n.equals("thanks nova")
                || n.equals("thank you nova") || n.equals("cheers") || n.equals("ta")
                || n.equals("thanks a lot") || n.equals("thank you very much");
    }

    static boolean isBye(String n) {
        return n.equals("bye") || n.equals("goodbye") || n.equals("good bye")
                || n.equals("see you") || n.equals("see ya") || n.equals("good night")
                || n.equals("goodnight") || n.equals("bye bye") || n.equals("bye nova")
                || n.equals("speak soon");
    }

    /** True if a model reply is only a greeting-style intro, ignoring the question. */
    public static boolean isBareGreetingReply(String ans) {
        String n = tidy(ans);
        if (n.isEmpty()) return false;
        if (isBareGreeting(n)) return true;
        return n.equals("hello there i m nova") || n.equals("hello there im nova")
                || n.equals("hi i m nova") || n.equals("hi im nova")
                || n.equals("hello i m nova") || n.equals("hello im nova")
                || n.startsWith("hello there i m nova") || n.startsWith("hello there im nova")
                || n.equals("hi i m happy to help") || n.equals("hello there");
    }

    public static String brainFallback(String heard, boolean kids) {
        // Keep this short, with no quote marks: a long quoted line was
        // clipped in the bubble while Heart spoke a different span.
        if (kids) return "My thinking brain isn't ready yet. Try again in a moment.";
        return "My on-device brain isn't ready yet. Please try again in a moment.";
    }
}

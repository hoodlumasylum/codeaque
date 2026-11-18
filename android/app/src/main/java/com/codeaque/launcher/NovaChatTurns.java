package com.codeaque.launcher;

/**
 * Short mid-conversation memory: the last few user and Nova turns.
 * Plain Java for CatalogTest.
 */
public final class NovaChatTurns {
    public static final int KEEP = 6;

    final String[] role = new String[KEEP];
    final String[] text = new String[KEEP];
    int n;

    public void addUser(String s) { add("user", s); }
    public void addBot(String s) { add("bot", s); }

    void add(String r, String s) {
        if (s == null) s = "";
        s = s.trim();
        if (s.isEmpty()) return;
        if (n < KEEP) {
            role[n] = r;
            text[n] = s;
            n++;
            return;
        }
        for (int i = 1; i < KEEP; i++) {
            role[i - 1] = role[i];
            text[i - 1] = text[i];
        }
        role[KEEP - 1] = r;
        text[KEEP - 1] = s;
    }

    public boolean midConversation() { return n >= 2; }

    public String lastBot() {
        for (int i = n - 1; i >= 0; i--) if ("bot".equals(role[i])) return text[i];
        return "";
    }

    public String lastUser() {
        for (int i = n - 1; i >= 0; i--) if ("user".equals(role[i])) return text[i];
        return "";
    }

    public boolean sameAsLastBot(String s) {
        if (s == null || s.isEmpty()) return false;
        String a = NovaSpeakGate.norm(s);
        String b = NovaSpeakGate.norm(lastBot());
        return !a.isEmpty() && a.equals(b);
    }

    /** If this line was just said, use the alternate so we do not repeat ourselves. */
    public String avoidRepeat(String wanted, String alternate) {
        if (wanted == null || wanted.isEmpty()) return alternate;
        if (sameAsLastBot(wanted) && alternate != null && !alternate.isEmpty()) return alternate;
        return wanted;
    }

    public void clear() { n = 0; }
}

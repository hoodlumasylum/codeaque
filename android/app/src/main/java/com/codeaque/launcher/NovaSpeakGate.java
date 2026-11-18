package com.codeaque.launcher;

/**
 * 0.10.60: one in-flight utterance at a time, and ignore her own echo.
 * Plain Java so CatalogTest can check it.
 */
public final class NovaSpeakGate {
    private NovaSpeakGate() {}

    /** After audio ends, wait this long before opening the mic, so her last syllable is not heard. */
    public static final long HANGOVER_MS = 200L;

    public static int nextGen(int current) {
        int n = current + 1;
        return n == 0 ? 1 : n;
    }

    public static boolean isCurrent(int jobGen, int currentGen) {
        return jobGen != 0 && jobGen == currentGen;
    }

    /** A new speak must cancel any job that is still loading, synthesizing, or playing. */
    public static boolean stale(int jobGen, int currentGen) {
        return !isCurrent(jobGen, currentGen);
    }

    /**
     * The mic may only start once nothing is still speaking or about to:
     * mouth timer, a Kokoro job in flight, a pending system-TTS flush, or the hangover.
     */
    public static boolean canStartListen(boolean voiceLoop, boolean expanded, boolean talking,
                                         boolean pendingSpeak, boolean jobInFlight, long now, long silentUntil) {
        if (!voiceLoop || !expanded) return false;
        if (talking || pendingSpeak || jobInFlight) return false;
        if (now < silentUntil) return false;
        return true;
    }

    public static String norm(String s) {
        if (s == null) return "";
        String t = s.toLowerCase();
        StringBuilder b = new StringBuilder(t.length());
        boolean space = false;
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (c >= 'a' && c <= 'z' || c >= '0' && c <= '9') {
                b.append(c);
                space = false;
            } else if (!space && b.length() > 0) {
                b.append(' ');
                space = true;
            }
        }
        int n = b.length();
        if (n > 0 && b.charAt(n - 1) == ' ') b.setLength(n - 1);
        return b.toString();
    }

    /** True if the heard line is her last spoken line (or a long piece of it). */
    public static boolean sameLine(String heard, String spoken) {
        String a = norm(heard), b = norm(spoken);
        if (a.isEmpty() || b.isEmpty()) return false;
        if (a.equals(b)) return true;
        if (a.length() >= 8 && b.contains(a)) return true;
        if (b.length() >= 8 && a.contains(b)) return true;
        // first several words of a long spoken line
        if (b.length() >= 16) {
            String prefix = b.substring(0, Math.min(b.length(), Math.max(16, a.length())));
            if (a.startsWith(prefix) || prefix.startsWith(a) && a.length() >= 12) return true;
        }
        return false;
    }

    /**
     * True if the heard line is her last reply, even when ASR clips the start
     * or typo-alters a word ("s sure about that you can ask me…").
     */
    public static boolean isEcho(String heard, String spoken) {
        if (sameLine(heard, spoken)) return true;
        String a = NovaGameTalk.stripLeadingNoise(norm(heard));
        String b = norm(spoken);
        if (a.isEmpty() || b.isEmpty()) return false;
        if (a.equals(b)) return true;
        if (a.length() >= 6 && b.contains(a)) return true;
        if (b.length() >= 6 && a.contains(b)) return true;
        String[] aw = a.split(" ");
        String[] bw = b.split(" ");
        int meaningful = 0, hits = 0, j = 0;
        for (int i = 0; i < aw.length; i++) {
            String w = aw[i];
            if (w.length() <= 1) continue;
            meaningful++;
            for (int k = j; k < bw.length; k++) {
                if (bw[k].equals(w) || closeWord(bw[k], w)) {
                    hits++;
                    j = k + 1;
                    break;
                }
            }
        }
        if (meaningful >= 4 && hits >= meaningful - 1) return true;
        if (meaningful >= 6 && hits * 10 >= meaningful * 7) return true;
        // A leftover syllable of her last line ("is", "s is") is not a new question.
        if (meaningful > 0 && meaningful <= 2 && a.length() <= 10 && hits == meaningful) return true;
        if (meaningful <= 2 && a.length() <= 10 && heardIsTailOf(a, b)) return true;
        return false;
    }

    static boolean heardIsTailOf(String a, String b) {
        if (a == null || b == null || a.isEmpty()) return false;
        if (b.equals(a) || b.endsWith(" " + a)) return true;
        String[] aw = a.split(" ");
        String[] bw = b.split(" ");
        if (aw.length == 0 || aw.length > bw.length) return false;
        for (int i = 0; i < aw.length; i++) {
            if (!aw[i].equals(bw[bw.length - aw.length + i]) && !closeWord(aw[i], bw[bw.length - aw.length + i]))
                return false;
        }
        return true;
    }

    static boolean closeWord(String a, String b) {
        if (a == null || b == null) return false;
        if (a.equals(b)) return true;
        if (Math.abs(a.length() - b.length()) > 2) return false;
        if (a.length() < 4 || b.length() < 4) return false;
        int d = editDist(a, b);
        if (d <= 1) return true;
        // Longer typos such as "numbus" / "number".
        return d <= 2 && a.length() >= 6 && b.length() >= 6;
    }

    static int editDist(String a, String b) {
        int n = a.length(), m = b.length();
        int[] prev = new int[m + 1];
        int[] cur = new int[m + 1];
        for (int j = 0; j <= m; j++) prev[j] = j;
        for (int i = 1; i <= n; i++) {
            cur[0] = i;
            char ca = a.charAt(i - 1);
            for (int j = 1; j <= m; j++) {
                int cost = ca == b.charAt(j - 1) ? 0 : 1;
                int v = prev[j] + 1;
                if (cur[j - 1] + 1 < v) v = cur[j - 1] + 1;
                if (prev[j - 1] + cost < v) v = prev[j - 1] + cost;
                cur[j] = v;
            }
            int[] tmp = prev; prev = cur; cur = tmp;
        }
        return prev[m];
    }

    /**
     * Drop a result that arrived while she is still speaking, or that repeats
     * the line she just said (her echo). Do not drop a new question just
     * because it arrived in the short hangover — that clips his first words
     * and makes him repeat himself.
     */
    public static boolean shouldDropHeard(String heard, String lastSpoken, boolean speaking,
                                          boolean jobInFlight, long now, long silentUntil) {
        if (speaking || jobInFlight) return true;
        return isEcho(heard, lastSpoken);
    }

    public static final String TAG_HEART = "Heart";
    public static final String TAG_PHONE = "Phone voice";

    public static String speakTag(boolean usedHeart) {
        return usedHeart ? TAG_HEART : TAG_PHONE;
    }

    /**
     * How long to wait for Heart's first audio. After this, the phone voice
     * speaks the same line so a slow synth does not leave a multi-second gap.
     */
    public static final long HEART_FIRST_AUDIO_MS = 400L;
    public static final long HEART_WAIT_MS = HEART_FIRST_AUDIO_MS;

    /** True when Heart files are on the phone (prefer Heart when it starts quickly). */
    public static boolean keepHeart(boolean filesReady) {
        return filesReady;
    }

    /**
     * Do not extend a slow Heart wait. Prefer the phone voice after
     * {@link #HEART_FIRST_AUDIO_MS} if playback has not started.
     */
    public static boolean extendHeartWait(boolean keepHeart, boolean audioStarted,
            boolean jobInFlight, boolean alreadyExtended) {
        return false;
    }

    /**
     * The phone voice may speak a line when Heart is missing or too slow.
     * Once Heart audio has actually started, do not cut over.
     */
    public static boolean allowSystemTts(boolean keepHeart) {
        return true;
    }

    public static long speakWaitMs(boolean keepHeart) {
        return HEART_FIRST_AUDIO_MS;
    }

    /**
     * The canned opening greeting must start at once. If Heart is still loading,
     * the phone voice is allowed for that one line.
     */
    public static boolean allowInstantPhone(boolean heartFilesReady, boolean heartLoaded) {
        return !heartLoaded;
    }
}

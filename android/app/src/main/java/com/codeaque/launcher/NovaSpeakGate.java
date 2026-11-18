package com.codeaque.launcher;

/**
 * 0.10.60: one in-flight utterance at a time, and ignore her own echo.
 * Plain Java so CatalogTest can check it.
 */
public final class NovaSpeakGate {
    private NovaSpeakGate() {}

    /** After audio ends, keep the mic closed briefly so the last syllable is not heard as a question. */
    public static final long HANGOVER_MS = 800L;

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
     * Drop a recognizer result that arrived while she is speaking, during the
     * hangover, or that repeats the line she just said.
     */
    public static boolean shouldDropHeard(String heard, String lastSpoken, boolean speaking,
                                          boolean jobInFlight, long now, long silentUntil) {
        if (speaking || jobInFlight) return true;
        if (now < silentUntil) return true;
        return sameLine(heard, lastSpoken);
    }

    public static final String TAG_HEART = "Heart";
    public static final String TAG_PHONE = "Phone voice";

    public static String speakTag(boolean usedHeart) {
        return usedHeart ? TAG_HEART : TAG_PHONE;
    }

    /**
     * First Heart load/synth can take longer than a reply timeout. Wait this
     * long before considering a Heart line stuck. Never cut it over to the
     * phone's voice just because the 12 s reply timer fired.
     */
    public static final long HEART_WAIT_MS = 30000L;

    /** Once the Heart files are on the phone, every line must use Heart. */
    public static boolean keepHeart(boolean filesReady) {
        return filesReady;
    }

    /**
     * If Heart is still loading or synthesising, extend the wait once instead
     * of switching to the phone's voice.
     */
    public static boolean extendHeartWait(boolean keepHeart, boolean audioStarted,
            boolean jobInFlight, boolean alreadyExtended) {
        if (!keepHeart) return false;
        if (audioStarted) return false;
        return jobInFlight && !alreadyExtended;
    }

    /**
     * A timeout or a failed synth must not start system TTS while Heart is
     * the chosen voice.
     */
    public static boolean allowSystemTts(boolean keepHeart) {
        return !keepHeart;
    }

    public static long speakWaitMs(boolean keepHeart) {
        return keepHeart ? HEART_WAIT_MS : 12000L;
    }
}

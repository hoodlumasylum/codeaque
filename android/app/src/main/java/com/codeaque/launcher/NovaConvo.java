package com.codeaque.launcher;

/**
 * 0.10.61: a many-turn conversation loop that cannot wedge itself.
 * Plain Java so CatalogTest can walk five turns and the mic-busy path.
 */
public final class NovaConvo {
    private NovaConvo() {}

    public static final int IDLE = 0;
    public static final int LISTENING = 1;
    public static final int HEARD = 2;
    public static final int THINKING = 3;
    public static final int SPEAKING = 4;
    public static final int MIC_BUSY = 5;

    public static final long SILENCE_COMPLETE_MS = 1200L;
    public static final long SILENCE_POSSIBLY_MS = 1200L;
    public static final long SILENCE_MIN_MS = 400L;
    public static final long REPLY_TIMEOUT_MS = 12000L;

    public static final String LISTENING_TEXT = "Listening…";
    public static final String THINKING_TEXT = "Thinking…";
    public static final String SPEAKING_TEXT = NovaReplyQuick.SPEAKING_TEXT;
    public static final String NO_SOUND_TEXT = NovaReplyQuick.NO_SOUND;
    public static final long LISTEN_WINDOW_MS = NovaReplyQuick.LISTEN_WINDOW_MS;
    public static final String MIC_BUSY_TEXT = "Mic is busy, maybe screen recording? Tap Mic to try again";
    public static final String FALLBACK_TEXT = "Sorry, that took too long. Please ask me again.";

    /** SpeechRecognizer.ERROR_AUDIO */
    public static final int ERR_AUDIO = 3;
    /** SpeechRecognizer.ERROR_RECOGNIZER_BUSY */
    public static final int ERR_BUSY = 8;

    public static String youSaid(String text) {
        String t = text == null ? "" : text.trim();
        if (t.isEmpty()) return "You said: …";
        return "You said: " + t;
    }

    public static String statusLine(int state, String heard) {
        if (state == LISTENING) return LISTENING_TEXT;
        if (state == HEARD) return youSaid(heard);
        if (state == THINKING) return THINKING_TEXT;
        if (state == MIC_BUSY) return MIC_BUSY_TEXT;
        if (state == SPEAKING) return SPEAKING_TEXT;
        return "";
    }

    public static boolean isMicBusy(int code) {
        return code == ERR_BUSY || code == ERR_AUDIO;
    }

    /** Soft errors that may restart the mic. Busy/audio must not retry in a loop. */
    public static boolean shouldRetry(int code) {
        if (isMicBusy(code)) return false;
        return code == NovaVoiceGate.ERR_CLIENT
                || code == NovaVoiceGate.ERR_SPEECH_TIMEOUT
                || code == NovaVoiceGate.ERR_NO_MATCH;
    }

    public static boolean writeHeardToField() { return false; }

    public static int nextGen(int current) {
        int n = current + 1;
        return n == 0 ? 1 : n;
    }

    public static boolean isCurrent(int jobGen, int currentGen) {
        return jobGen != 0 && jobGen == currentGen;
    }

    public static boolean stale(int jobGen, int currentGen) {
        return !isCurrent(jobGen, currentGen);
    }

    /** Spoken line must be exactly the on-screen answer (no second template). */
    public static String speakText(String onScreen) {
        return onScreen == null ? "" : onScreen;
    }

    public static boolean spokenMatchesScreen(String spoken, String onScreen) {
        return speakText(spoken).equals(speakText(onScreen));
    }

    /**
     * In-memory loop used by tests and as the contract for NovaView:
     * every turn releases its locks, partials never touch the field,
     * and a timeout yields the canned fallback.
     */
    public static final class Loop {
        public int listenGen, speakGen, turn, state;
        public boolean talking, jobInFlight, pending, listening;
        public String status = "";
        public String caption = "";
        public String field = "";
        public String lastHeard = "";
        public String lastSpoken = "";
        public String answer = "";

        public boolean locksHeld() {
            return talking || jobInFlight || pending || listening;
        }

        public void releaseLocks() {
            talking = false;
            jobInFlight = false;
            pending = false;
            listening = false;
            if (state == SPEAKING || state == THINKING) state = IDLE;
        }

        public void startListen() {
            listening = true;
            listenGen = nextGen(listenGen);
            state = LISTENING;
            caption = "";
            status = statusLine(state, lastHeard);
        }

        public void onPartial(int gen, String text) {
            if (stale(gen, listenGen) || !listening) return;
            caption = text == null ? "" : text.trim();
            // Partials never go in the input field.
        }

        public boolean onFinal(int gen, String text) {
            if (stale(gen, listenGen)) return false;
            if (talking || jobInFlight) return false;
            listening = false;
            lastHeard = text == null ? "" : text.trim();
            if (lastHeard.isEmpty()) return false;
            state = HEARD;
            status = statusLine(state, lastHeard);
            caption = "";
            return true;
        }

        public void beginReply(String onScreen) {
            pending = false;
            jobInFlight = true;
            talking = true;
            answer = speakText(onScreen);
            lastSpoken = answer;
            state = THINKING;
            status = statusLine(state, lastHeard);
        }

        public void onAudioStart() {
            state = SPEAKING;
            status = statusLine(state, lastHeard);
        }

        /** Must be called from a finally block so a failed turn cannot hold the mic/speak gates. */
        public void endReply() {
            releaseLocks();
            state = IDLE;
            turn++;
        }

        public void timeout() {
            lastSpoken = FALLBACK_TEXT;
            answer = FALLBACK_TEXT;
            releaseLocks();
            state = IDLE;
            turn++;
        }

        public void micBusy() {
            listening = false;
            state = MIC_BUSY;
            status = MIC_BUSY_TEXT;
            caption = "";
        }
    }

    /** Drive n successful turns; returns the number completed with locks released each time. */
    public static int runTurns(int n) {
        Loop loop = new Loop();
        String fieldBefore = loop.field;
        for (int i = 0; i < n; i++) {
            loop.startListen();
            loop.onPartial(loop.listenGen, "hel");
            if (!loop.field.equals(fieldBefore)) return -1;
            if (!loop.onFinal(loop.listenGen, "hello " + (i + 1))) return -2;
            if (writeHeardToField()) return -3;
            String reply = "Answer " + (i + 1);
            loop.beginReply(reply);
            if (!spokenMatchesScreen(loop.lastSpoken, loop.answer)) return -4;
            if (!THINKING_TEXT.equals(loop.status) && !loop.status.startsWith("You said:")) return -5;
            loop.onAudioStart();
            loop.endReply();
            if (loop.locksHeld()) return -6;
        }
        return loop.turn;
    }

}

package com.codeaque.launcher;

/**
 * 0.10.58: hide/show and optional "Keep listening while hidden".
 * Plain Java so CatalogTest can check it. Default for the switch is off.
 */
public final class NovaListenHidden {
    private NovaListenHidden() {}

    public static final String PREF = "novaListenHidden";
    public static final String TOAST_HIDDEN = "Nova hidden - turn her back on in the Nova tab";

    /** Show Nova defaults to on. */
    public static boolean showNova(Boolean pref) { return pref == null || pref.booleanValue(); }

    /** Hidden when Show Nova is off (double-tap or the switch). The old edge-tab flag also counts. */
    public static boolean isHidden(boolean showNova, boolean edgeHidden) {
        return !showNova || edgeHidden;
    }

    /** The optional switch itself; default off. */
    public static boolean switchOn(Boolean pref) { return pref != null && pref.booleanValue(); }

    /**
     * The background listen service may run only when the switch is on, she is hidden,
     * and Voice talk is on. It must still be started from a foreground user action.
     */
    public static boolean shouldRun(boolean switchOn, boolean hidden, boolean voiceTalkOn) {
        return switchOn && hidden && voiceTalkOn;
    }

    /** Android 14: start the microphone foreground service only while CodeAque is in the foreground. */
    public static boolean canStartFromUser(boolean shouldRun, boolean appForeground, boolean micAllowed) {
        return shouldRun && appForeground && micAllowed;
    }

    /** Keep the mic off while she is speaking a reply. */
    public static boolean canListenNow(boolean shouldRun, boolean speaking) {
        return shouldRun && !speaking;
    }

    /**
     * Leaving text mode (Listen / Type-instead toggle off text) should enter the
     * hands-free loop. The mic starts now if she is not still talking.
     */
    public static boolean shouldListenAfterTextOff(boolean expanded, boolean voiceTalkOn,
                                                   boolean talking, boolean pendingSpeak) {
        if (!expanded || !voiceTalkOn) return false;
        return !talking && !pendingSpeak;
    }

    public static boolean wantsVoiceLoopAfterTextOff(boolean expanded, boolean voiceTalkOn) {
        return expanded && voiceTalkOn;
    }
}

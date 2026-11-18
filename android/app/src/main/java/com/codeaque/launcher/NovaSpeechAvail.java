package com.codeaque.launcher;

/**
 * When in-app SpeechRecognizer can be used, when to use the built-in
 * sherpa recognizer, when to fall back to the system speech prompt, and
 * the copy shown if this phone has no recognition service.
 * Plain Java so CatalogTest can check the wording and the decision table.
 */
public final class NovaSpeechAvail {
    private NovaSpeechAvail() {}

    public static final String NO_SERVICE =
            "No speech service on this phone - install Speech Recognition & Synthesis from Google from Play Store";
    public static final String NO_SERVICE_BUILTIN =
            "No speech service on this phone, using built-in";
    public static final String NO_SERVICE_NEED_BUILTIN =
            "No speech service on this phone. You can download built-in listening (about 45 MB).";
    public static final String SYSTEM_ERROR_BUILTIN =
            "The phone's speech service isn't working, using built-in";
    public static final String USING_BUILTIN = "Listening with built-in";
    public static final String PERMISSION_DENIED = "Mic permission denied";
    public static final String INSTALL_BTN = "Get speech service";
    public static final String TTS_PACKAGE = "com.google.android.tts";
    public static final String LANGUAGE = "en-GB";
    public static final String LANGUAGE_US = "en-US";

    public static final int USE_RECOGNIZER = 1;
    public static final int USE_PROMPT = 2;
    public static final int NEED_INSTALL = 3;
    public static final int USE_BUILTIN = 4;
    public static final int NEED_BUILTIN = 5;
    public static final int NEED_PERMISSION = 6;

    /**
     * recAvailable is SpeechRecognizer.isRecognitionAvailable.
     * promptAvailable is a resolvable ACTION_RECOGNIZE_SPEECH activity.
     * Kept for older tests: no on-device flag, no builtin pack, mic allowed.
     */
    public static int path(boolean recAvailable, boolean promptAvailable) {
        return path(recAvailable, false, promptAvailable, false, true);
    }

    /**
     * recAvailable is SpeechRecognizer.isRecognitionAvailable.
     * onDeviceAvailable is SpeechRecognizer.isOnDeviceRecognitionAvailable (API 31+).
     * promptAvailable is a resolvable ACTION_RECOGNIZE_SPEECH activity.
     * builtinReady is the on-demand sherpa pack on disk.
     * micAllowed is RECORD_AUDIO granted.
     */
    public static int path(boolean recAvailable, boolean onDeviceAvailable,
            boolean promptAvailable, boolean builtinReady, boolean micAllowed) {
        if (!micAllowed) return NEED_PERMISSION;
        if (recAvailable || onDeviceAvailable) return USE_RECOGNIZER;
        if (builtinReady) return USE_BUILTIN;
        if (promptAvailable) return USE_PROMPT;
        return NEED_BUILTIN;
    }

    public static String status(int path, boolean listening, String lastError) {
        if (path == NEED_PERMISSION) return PERMISSION_DENIED;
        if (listening && path == USE_BUILTIN) return USING_BUILTIN;
        if (listening) return "Listening…";
        if (lastError != null && !lastError.isEmpty()) return lastError;
        if (path == USE_BUILTIN) return NO_SERVICE_BUILTIN;
        if (path == NEED_BUILTIN) return NO_SERVICE_NEED_BUILTIN;
        if (path == NEED_INSTALL) return NO_SERVICE;
        if (path == USE_PROMPT) return "Tap Mic, then speak";
        return "";
    }

    public static boolean showInstallButton(int path) {
        return showInstallButton(path, false);
    }

    /**
     * Do not offer the Play Store page when Speech Recognition & Synthesis
     * (or any recognition service) is already on the phone.
     */
    public static boolean showInstallButton(int path, boolean serviceAlreadyInstalled) {
        if (serviceAlreadyInstalled) return false;
        return path == NEED_INSTALL || path == NEED_BUILTIN;
    }

    public static boolean serviceAlreadyInstalled(boolean recAvailable, boolean onDeviceAvailable,
            boolean ttsPackageInstalled) {
        return recAvailable || onDeviceAvailable || ttsPackageInstalled;
    }

    public static boolean showBuiltinButton(int path, boolean builtinReady) {
        if (builtinReady) return false;
        return path == NEED_BUILTIN || path == NEED_INSTALL || path == USE_PROMPT;
    }
}

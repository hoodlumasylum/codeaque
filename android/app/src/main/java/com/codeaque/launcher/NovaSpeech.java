package com.codeaque.launcher;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;

import java.util.ArrayList;

/**
 * 0.10.54: speech recognizer in Chat.
 * 0.10.63: do not start with EXTRA_PREFER_OFFLINE; try the on-device system
 * recognizer on API 31+; fall back en-GB then en-US; when the system service
 * is missing or keeps erroring, use a built-in sherpa-onnx recognizer.
 * Named listener (NovaSpeechListen) — d8 8.2 has NPEd on anonymous listeners.
 */
public final class NovaSpeech {
    public static final String PREF_VOICE_TALK = "novaVoiceTalk";

    public interface Callbacks {
        void onSpeechResult(String text);
        void onSpeechError(String message);
        void onSpeechState(boolean listening);
        /** Timeout / no-match: keep the hands-free loop going. Not used for a busy mic. */
        void onSpeechRetry(int code);
        /** No in-app recognizer; use ACTION_RECOGNIZE_SPEECH. */
        void onSpeechUsePrompt();
        /** No recognition service at all; show the Play Store message and button. */
        void onSpeechNeedService();
        /** Live caption only; must never be written into the input field. */
        void onSpeechPartial(String text);
        /** Mic is held by another app (for example a screen recorder). Do not retry in a loop. */
        void onSpeechMicBusy(String message);
        /** Mic level 0..1 for the on-screen meter. */
        default void onSpeechRms(float level) { }
        /** No system service (or it failed). Offer or start the built-in pack. */
        default void onSpeechNeedBuiltin(String message) { }
        /** Built-in pack is loading in the background. Do not treat as a give-up. */
        default void onSpeechAsrLoading(String message) { }
    }

    final Handler ui = new Handler(Looper.getMainLooper());
    SpeechRecognizer rec;
    Callbacks cb;
    boolean listening, offlineTried, destroyed, usedOnDevice, usedUsLang, usingBuiltin;
    boolean pausedHidden, pendingBuiltinLoad;
    String pendingBuiltinReason;
    Context app;
    /** 0.10.61: bumped on each start/stop so a late result cannot replay an old question. */
    int listenGen;
    final NovaListenBeep beep = new NovaListenBeep();
    String language = NovaSpeechAvail.LANGUAGE;
    NovaAsrMic asrMic;

    public static boolean voiceTalkOn(SharedPreferences p) {
        return p == null || p.getBoolean(PREF_VOICE_TALK, true);
    }

    public boolean isListening() { return listening; }

    public boolean usingBuiltin() { return usingBuiltin; }

    public boolean start(Context ctx, Callbacks callbacks) {
        return start(ctx, callbacks, false);
    }

    public boolean start(Context ctx, Callbacks callbacks, boolean forceBuiltin) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            ui.post(new NovaSpeechStart(this, ctx, callbacks, forceBuiltin));
            return true;
        }
        stop();
        destroyed = false;
        cb = callbacks;
        usingBuiltin = false;
        usedOnDevice = false;
        usedUsLang = false;
        language = NovaListenPick.firstLanguage();
        if (ctx == null) {
            fail("Voice talk isn't available on this phone. You can type your question instead.");
            return false;
        }
        app = ctx.getApplicationContext();
        NovaListenService.pauseForChat(app);
        pausedHidden = true;

        if (Build.VERSION.SDK_INT >= 23
                && app.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            NovaListenDiag.setPermissionDenied(true);
            NovaListenDiag.setReason(NovaSpeechAvail.PERMISSION_DENIED);
            fail(NovaSpeechAvail.PERMISSION_DENIED);
            return false;
        }
        NovaListenDiag.setPermissionDenied(false);

        boolean recOk = false, onDeviceOk = false;
        try { recOk = SpeechRecognizer.isRecognitionAvailable(ctx); } catch (Exception ignored) { }
        if (Build.VERSION.SDK_INT >= 31) {
            try { onDeviceOk = SpeechRecognizer.isOnDeviceRecognitionAvailable(ctx); }
            catch (Throwable ignored) { }
        }
        boolean promptOk = promptAvailable(ctx);
        boolean builtinReady = NovaAsr.filesReady(ctx);
        boolean builtinLoaded = NovaAsr.isLoaded();
        int path = NovaSpeechAvail.path(recOk, onDeviceOk, promptOk, builtinReady, true);

        // Kick a background load on a Mic tap; never init native on this thread.
        if (builtinReady && !builtinLoaded) NovaAsr.requestLoad(ctx);

        if (NovaListenPick.useBuiltinNow(forceBuiltin, builtinLoaded, recOk, onDeviceOk)) {
            startBuiltin(forceBuiltin && (recOk || onDeviceOk)
                    ? NovaSpeechAvail.SYSTEM_ERROR_BUILTIN
                    : NovaSpeechAvail.NO_SERVICE_BUILTIN);
            return listening || pendingBuiltinLoad;
        }
        // Pack is on disk but not yet loaded, and there is no system recognizer:
        // load in the background and start the mic when it is ready.
        if (builtinReady && !builtinLoaded && !recOk && !onDeviceOk) {
            startBuiltin(NovaSpeechAvail.NO_SERVICE_BUILTIN);
            return listening || pendingBuiltinLoad;
        }
        if (path == NovaSpeechAvail.NEED_PERMISSION) {
            NovaListenDiag.setPermissionDenied(true);
            NovaListenDiag.setReason(NovaSpeechAvail.PERMISSION_DENIED);
            fail(NovaSpeechAvail.PERMISSION_DENIED);
            return false;
        }
        if (path == NovaSpeechAvail.NEED_BUILTIN || path == NovaSpeechAvail.NEED_INSTALL) {
            String msg = NovaSpeechAvail.NO_SERVICE_NEED_BUILTIN;
            NovaListenDiag.setReason(msg);
            fail(msg);
            if (cb != null) ui.post(new NovaSpeechPost(this, NovaSpeechPost.NEED_BUILTIN, msg, false));
            return false;
        }
        if (path == NovaSpeechAvail.USE_PROMPT) {
            if (cb != null) ui.post(new NovaSpeechPost(this, NovaSpeechPost.USE_PROMPT, null, false));
            return false;
        }
        try {
            rec = createBest(ctx);
            if (rec == null) throw new IllegalStateException("no recognizer");
            rec.setRecognitionListener(new NovaSpeechListen(this));
        } catch (Exception e) {
            rec = null;
            if (builtinReady) {
                startBuiltin(NovaSpeechAvail.SYSTEM_ERROR_BUILTIN);
                return listening;
            }
            if (promptOk) {
                if (cb != null) ui.post(new NovaSpeechPost(this, NovaSpeechPost.USE_PROMPT, null, false));
                return false;
            }
            String msg = NovaSpeechAvail.NO_SERVICE_NEED_BUILTIN;
            NovaListenDiag.setReason(msg);
            fail(msg);
            if (cb != null) ui.post(new NovaSpeechPost(this, NovaSpeechPost.NEED_BUILTIN, msg, false));
            return false;
        }
        listen(NovaListenPick.preferOfflineFirst(), language);
        return listening;
    }

    SpeechRecognizer createBest(Context ctx) {
        if (ctx == null) return null;
        // Default recognizer first: Speech Recognition & Synthesis can report
        // an on-device engine as available even with no English pack, which
        // then "listens" and returns nothing.
        try {
            if (SpeechRecognizer.isRecognitionAvailable(ctx)) {
                usedOnDevice = false;
                return SpeechRecognizer.createSpeechRecognizer(ctx);
            }
        } catch (Exception ignored) { }
        if (Build.VERSION.SDK_INT >= 31) {
            try {
                SpeechRecognizer r = SpeechRecognizer.createOnDeviceSpeechRecognizer(ctx);
                usedOnDevice = true;
                return r;
            } catch (Throwable ignored) { }
        }
        return null;
    }

    void startBuiltin(String reason) {
        destroyRecognizerOnly();
        if (NovaAsr.isLoaded()) {
            startBuiltinMic(reason);
            return;
        }
        if (app != null) NovaAsr.requestLoad(app, new NovaSpeechAsrLoadCb(this, reason));
        pendingBuiltinLoad = true;
        pendingBuiltinReason = reason;
        listenGen = NovaConvo.nextGen(listenGen);
        NovaListenDiag.setReason(reason == null ? "Loading built-in listening…" : reason);
        if (cb != null) ui.post(new NovaSpeechPost(this, NovaSpeechPost.ASR_LOADING,
                "Loading built-in listening…", false));
    }

    void startBuiltinMic(String reason) {
        usingBuiltin = true;
        pendingBuiltinLoad = false;
        NovaListenDiag.setUsingBuiltin(true);
        NovaListenDiag.setReason(reason);
        if (asrMic == null) asrMic = new NovaAsrMic(new NovaSpeechAsrCb(this));
        String err = asrMic.start(app);
        if (err != null) {
            usingBuiltin = false;
            NovaListenDiag.setUsingBuiltin(false);
            if (err.contains("busy") || err.contains("Mic is busy")) {
                if (cb != null) ui.post(new NovaSpeechPost(this, NovaSpeechPost.MIC_BUSY, NovaConvo.MIC_BUSY_TEXT, false));
                return;
            }
            fail(err);
            if (cb != null) ui.post(new NovaSpeechPost(this, NovaSpeechPost.NEED_BUILTIN, err, false));
            return;
        }
        listenGen = NovaConvo.nextGen(listenGen);
        listening = true;
        notifyState(true);
    }

    void onAsrLoadDone(boolean ok, String error, String reason, int gen) {
        if (!pendingBuiltinLoad) return;
        if (NovaConvo.stale(gen, listenGen) && gen != 0) return;
        pendingBuiltinLoad = false;
        if (destroyed || listening) return;
        if (ok && NovaAsr.isLoaded()) {
            startBuiltinMic(reason != null ? reason : pendingBuiltinReason);
            return;
        }
        String msg = error == null || error.isEmpty()
                ? "Built-in listening didn't start. Using the phone's listener if it is there."
                : error;
        NovaListenDiag.setReason(msg);
        // Fall back to the system recognizer when it exists.
        try {
            if (app != null) {
                rec = createBest(app);
                if (rec != null) {
                    rec.setRecognitionListener(new NovaSpeechListen(this));
                    listen(NovaListenPick.preferOfflineFirst(), language);
                    return;
                }
            }
        } catch (Exception e) {
            rec = null;
        }
        fail(msg);
        if (cb != null) ui.post(new NovaSpeechPost(this, NovaSpeechPost.NEED_BUILTIN, msg, false));
    }

    public void toggle(Context ctx, Callbacks callbacks) {
        if (listening) stop();
        else start(ctx, callbacks);
    }

    public void stop() {
        listening = false;
        offlineTried = false;
        pendingBuiltinLoad = false;
        listenGen = NovaConvo.nextGen(listenGen);
        beep.restoreNow();
        usingBuiltin = false;
        NovaListenDiag.setUsingBuiltin(false);
        NovaListenDiag.setLevel(0f);
        if (asrMic != null) {
            try { asrMic.stop(); } catch (Exception ignored) { }
        }
        destroyRecognizerOnly();
        notifyState(false);
    }

    void destroyRecognizerOnly() {
        SpeechRecognizer r = rec;
        rec = null;
        if (r != null) {
            try { r.stopListening(); } catch (Exception ignored) { }
            try { r.cancel(); } catch (Exception ignored) { }
            try { r.destroy(); } catch (Exception ignored) { }
        }
    }

    public void destroy() {
        destroyed = true;
        stop();
        cb = null;
        app = null;
    }

    void listen(boolean preferOffline) {
        listen(preferOffline, language);
    }

    void listen(boolean preferOffline, String lang) {
        if (destroyed || rec == null) return;
        if (lang == null || lang.isEmpty()) lang = NovaSpeechAvail.LANGUAGE;
        language = lang;
        Intent i = recognizeIntent(preferOffline, lang);
        offlineTried = preferOffline;
        listenGen = NovaConvo.nextGen(listenGen);
        listening = true;
        notifyState(true);
        if (app != null) beep.duck(app);
        try { rec.startListening(i); }
        catch (Exception e) {
            beep.restoreNow();
            if (preferOffline) { listen(false, lang); return; }
            if (tryLanguageOrBuiltin(NovaVoiceGate.ERR_CLIENT)) return;
            listening = false;
            notifyState(false);
            fail("I couldn't start the mic. You can type your question instead.");
        }
    }

    void onReady() { beep.restoreNow(); }

    void onRms(float rmsdB) {
        if (destroyed || !listening) return;
        float level = NovaListenDiag.meterFromRmsDb(rmsdB);
        NovaListenDiag.setLevel(level);
        if (cb != null) ui.post(new NovaSpeechPost(this, NovaSpeechPost.RMS, null, false, listenGen, 0, level));
    }

    void onResults(Bundle b) {
        int gen = listenGen;
        if (destroyed || !listening) return;
        listening = false;
        beep.restoreNow();
        notifyState(false);
        String text = firstResult(b);
        if (text == null || text.trim().isEmpty()) {
            fail("I didn't catch that. Try again, or type it.");
            return;
        }
        final String said = NovaAsrText.tidy(text);
        if (said.isEmpty()) {
            fail("I didn't catch that. Try again, or type it.");
            return;
        }
        if (cb != null) ui.post(new NovaSpeechPost(this, NovaSpeechPost.RESULT, said, false, gen));
    }

    void onPartial(Bundle b) {
        if (destroyed || !listening) return;
        String text = firstResult(b);
        if (text == null || text.trim().isEmpty()) return;
        String said = NovaAsrText.tidy(text);
        if (said.isEmpty()) return;
        if (cb != null) ui.post(new NovaSpeechPost(this, NovaSpeechPost.PARTIAL, said, false, listenGen));
    }

    /** Soft errors that should restart listening in a hands-free loop (not a hard failure). */
    public static boolean softListenError(int code) {
        return NovaConvo.shouldRetry(code);
    }

    void onError(int code) {
        if (destroyed || !listening) return;
        int gen = listenGen;
        if (offlineTried && (code == SpeechRecognizer.ERROR_CLIENT
                || code == SpeechRecognizer.ERROR_SERVER
                || code == SpeechRecognizer.ERROR_NETWORK
                || code == SpeechRecognizer.ERROR_NETWORK_TIMEOUT)) {
            listen(false, language);
            return;
        }
        if (tryLanguageOrBuiltin(code)) return;
        listening = false;
        beep.restoreNow();
        notifyState(false);
        if (NovaConvo.isMicBusy(code)) {
            if (cb != null) ui.post(new NovaSpeechPost(this, NovaSpeechPost.MIC_BUSY, NovaConvo.MIC_BUSY_TEXT, false, gen));
            return;
        }
        if (softListenError(code)) {
            if (cb != null) ui.post(new NovaSpeechPost(this, NovaSpeechPost.RETRY, null, false, gen, code));
            return;
        }
        fail(friendlyError(code));
    }

    /**
     * Retry en-US after en-GB, drop the on-device recognizer for the default
     * one, then fall through to the built-in pack when it is ready.
     * @return true if a retry was started (caller must not treat this as a stop).
     */
    boolean tryLanguageOrBuiltin(int code) {
        if (usingBuiltin) return false;
        String next = NovaListenPick.nextLanguage(language, NovaListenPick.languageWorthyError(code));
        if (next != null && rec != null) {
            usedUsLang = true;
            listen(false, next);
            return true;
        }
        if (usedOnDevice && app != null
                && NovaListenPick.networkOrServer(code)) {
            destroyRecognizerOnly();
            usedOnDevice = false;
            try {
                rec = SpeechRecognizer.createSpeechRecognizer(app);
                rec.setRecognitionListener(new NovaSpeechListen(this));
                listen(false, language);
                return true;
            } catch (Exception e) {
                rec = null;
            }
        }
        if (!usedOnDevice && app != null && Build.VERSION.SDK_INT >= 31
                && NovaListenPick.networkOrServer(code)) {
            destroyRecognizerOnly();
            try {
                rec = SpeechRecognizer.createOnDeviceSpeechRecognizer(app);
                rec.setRecognitionListener(new NovaSpeechListen(this));
                usedOnDevice = true;
                listen(false, language);
                return true;
            } catch (Throwable e) {
                rec = null;
                usedOnDevice = false;
            }
        }
        if (app != null && NovaAsr.filesReady(app)
                && (NovaListenPick.networkOrServer(code) || code == SpeechRecognizer.ERROR_SERVER
                || code == SpeechRecognizer.ERROR_CLIENT)) {
            // Hard system failure (not a simple no-match). Switch to built-in.
            if (!softListenError(code) || code == SpeechRecognizer.ERROR_CLIENT) {
                startBuiltin(NovaSpeechAvail.SYSTEM_ERROR_BUILTIN);
                return true;
            }
        }
        return false;
    }

    static String firstResult(Bundle b) {
        if (b == null) return null;
        ArrayList<String> list = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (list == null || list.isEmpty()) return null;
        return list.get(0);
    }

    static String friendlyError(int code) {
        if (code == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS)
            return NovaSpeechAvail.PERMISSION_DENIED;
        if (code == SpeechRecognizer.ERROR_SPEECH_TIMEOUT || code == SpeechRecognizer.ERROR_NO_MATCH)
            return "I didn't catch that. Try again, or type it.";
        if (NovaConvo.isMicBusy(code))
            return NovaConvo.MIC_BUSY_TEXT;
        return "I couldn't hear that. You can type your question instead.";
    }

    public static Intent recognizeIntent(boolean preferOffline) {
        return recognizeIntent(preferOffline, NovaSpeechAvail.LANGUAGE);
    }

    public static Intent recognizeIntent(boolean preferOffline, String language) {
        if (language == null || language.isEmpty()) language = NovaSpeechAvail.LANGUAGE;
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, language);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, language);
        i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        i.putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to Nova");
        i.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, (int) NovaConvo.SILENCE_COMPLETE_MS);
        i.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, (int) NovaConvo.SILENCE_POSSIBLY_MS);
        i.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, (int) NovaConvo.SILENCE_MIN_MS);
        if (preferOffline) i.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true);
        return i;
    }

    public static boolean promptAvailable(Context ctx) {
        if (ctx == null) return false;
        try {
            android.content.pm.ResolveInfo ri = ctx.getPackageManager().resolveActivity(
                    new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH), 0);
            return ri != null && ri.activityInfo != null;
        } catch (Exception e) { return false; }
    }

    void fail(String message) {
        if (cb != null) ui.post(new NovaSpeechPost(this, NovaSpeechPost.ERROR, message, false));
    }

    void notifyState(boolean on) {
        if (cb != null) ui.post(new NovaSpeechPost(this, NovaSpeechPost.STATE, null, on));
    }
}

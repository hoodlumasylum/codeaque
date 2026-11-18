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
    boolean listening, offlineTried, offlineUnavailable, destroyed, usedOnDevice, usedUsLang, usingBuiltin;
    boolean pausedHidden, pendingBuiltinLoad, isStarting;
    long listenStartedAt, lastStopAt;
    NovaSpeechListenGo startSoon;
    String pendingBuiltinReason;
    Context app;
    /** 0.10.61: bumped on each start/stop so a late result cannot replay an old question. */
    int listenGen;
    final NovaListenBeep beep = new NovaListenBeep();
    String language = NovaSpeechAvail.LANGUAGE;
    NovaAsrMic asrMic;
    final NovaEndpoint endpoint = new NovaEndpoint();
    NovaSpeechEndWatch endWatch;
    /** Only one SpeechRecognizer or AudioRecord may hold the mic at a time. */
    static volatile NovaSpeech activeMic;
    NovaSpeechResultsWait resultsWait;

    public static boolean voiceTalkOn(SharedPreferences p) {
        return p == null || p.getBoolean(PREF_VOICE_TALK, true);
    }

    public boolean isListening() { return listening; }

    public boolean isStarting() { return isStarting; }

    public boolean usingBuiltin() { return usingBuiltin; }

    public boolean start(Context ctx, Callbacks callbacks) {
        return start(ctx, callbacks, false);
    }

    public boolean start(Context ctx, Callbacks callbacks, boolean forceBuiltin) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            ui.post(new NovaSpeechStart(this, ctx, callbacks, forceBuiltin));
            return true;
        }
        destroyed = false;
        if (callbacks != null) cb = callbacks;
        // Already holding the mic, or a startListening is in flight: do not
        // cancel and restart. That race was the 0.10.74 Listening/Mic loop.
        if (listening && !forceBuiltin && rec != null && !usingBuiltin) return true;
        if (listening && usingBuiltin && asrMic != null) return true;
        if (isStarting && !forceBuiltin) return true;
        cancelStartSoon();
        // Only cancel when something actually holds the mic. Cancelling an
        // idle recognizer and starting it in the same call is a busy/client loop.
        if (listening || isStarting || (forceBuiltin && rec != null) || (usingBuiltin && !forceBuiltin && asrMic != null))
            pauseListening();
        cb = callbacks;
        usedUsLang = false;
        language = NovaListenPick.firstLanguage();
        // Switching engines: drop the other recognizer. Otherwise keep it warm.
        if (forceBuiltin) {
            destroyRecognizerOnly();
            usingBuiltin = false;
        } else if (usingBuiltin) {
            if (asrMic != null) {
                try { asrMic.stop(); } catch (Exception ignored) { }
            }
            usingBuiltin = false;
            NovaListenDiag.setUsingBuiltin(false);
        }
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
            if (rec == null) {
                rec = createBest(ctx);
                if (rec == null) throw new IllegalStateException("no recognizer");
                rec.setRecognitionListener(new NovaSpeechListen(this));
            }
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
        claimMic();
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

    /** Stop this listen session but keep the SpeechRecognizer for the next turn. */
    public void pauseListening() {
        cancelStartSoon();
        isStarting = false;
        listening = false;
        pendingBuiltinLoad = false;
        lastStopAt = android.os.SystemClock.uptimeMillis();
        listenGen = NovaConvo.nextGen(listenGen);
        cancelEndpointWatches();
        beep.restoreNow();
        NovaListenDiag.setLevel(0f);
        if (asrMic != null) {
            try { asrMic.stop(); } catch (Exception ignored) { }
        }
        if (rec != null) {
            try { rec.stopListening(); } catch (Exception ignored) { }
            try { rec.cancel(); } catch (Exception ignored) { }
        }
        releaseMic();
        notifyState(false);
    }

    public long listenElapsedMs() {
        if (listenStartedAt == 0L) return 0L;
        long n = android.os.SystemClock.uptimeMillis() - listenStartedAt;
        return n < 0L ? 0L : n;
    }

    void cancelStartSoon() {
        if (startSoon != null) {
            ui.removeCallbacks(startSoon);
            startSoon = null;
        }
    }

    void scheduleListenGo(boolean preferOffline, String lang, long delayMs) {
        cancelStartSoon();
        startSoon = new NovaSpeechListenGo(this, preferOffline, lang, listenGen);
        ui.postDelayed(startSoon, delayMs < 1L ? 1L : delayMs);
    }

    void claimMic() {
        NovaSpeech other = activeMic;
        if (other != null && other != this) {
            try { other.pauseListening(); } catch (Exception ignored) { }
        }
        activeMic = this;
    }

    void releaseMic() {
        if (activeMic == this) activeMic = null;
    }

    public void stop() {
        pauseListening();
        offlineTried = false;
        usingBuiltin = false;
        NovaListenDiag.setUsingBuiltin(false);
        destroyRecognizerOnly();
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
        if (isStarting || listening) return;
        if (lang == null || lang.isEmpty()) lang = NovaSpeechAvail.LANGUAGE;
        language = lang;
        if (offlineUnavailable) preferOffline = false;
        long now = android.os.SystemClock.uptimeMillis();
        long gap = lastStopAt == 0L ? NovaListenGate.START_GAP_MS : now - lastStopAt;
        if (gap < NovaListenGate.START_GAP_MS) {
            scheduleListenGo(preferOffline, lang, NovaListenGate.START_GAP_MS - gap);
            return;
        }
        listenNow(preferOffline, lang);
    }

    void listenNow(boolean preferOffline, String lang) {
        if (destroyed || rec == null) return;
        if (isStarting || listening) return;
        if (lang == null || lang.isEmpty()) lang = NovaSpeechAvail.LANGUAGE;
        language = lang;
        if (offlineUnavailable) preferOffline = false;
        Intent i = recognizeIntent(preferOffline, lang);
        offlineTried = preferOffline;
        listenGen = NovaConvo.nextGen(listenGen);
        claimMic();
        isStarting = true;
        listening = true;
        listenStartedAt = android.os.SystemClock.uptimeMillis();
        endpoint.start(listenStartedAt);
        scheduleEndpointWatch();
        notifyState(true);
        if (app != null) beep.duck(app);
        try { rec.startListening(i); }
        catch (Exception e) {
            isStarting = false;
            listening = false;
            lastStopAt = android.os.SystemClock.uptimeMillis();
            beep.restoreNow();
            notifyState(false);
            long elapsed = listenElapsedMs();
            NovaListenDiag.setError(NovaVoiceGate.ERR_CLIENT, elapsed, preferOffline ? "offline-start" : "start");
            android.util.Log.i("NovaListen", NovaListenDiag.lastErrorLine());
            if (preferOffline && !offlineUnavailable) {
                offlineUnavailable = true;
                scheduleListenGo(false, lang, NovaListenGate.START_GAP_MS);
                return;
            }
            if (cb != null) ui.post(new NovaSpeechPost(this, NovaSpeechPost.RETRY, null, false, listenGen, NovaVoiceGate.ERR_CLIENT));
        }
    }

    void onReady() {
        isStarting = false;
        beep.restoreNow();
    }

    void onRms(float rmsdB) {
        if (destroyed || !listening) return;
        long now = android.os.SystemClock.uptimeMillis();
        float raw = NovaListenDiag.meterFromRmsDb(rmsdB);
        endpoint.onLevel(raw, now);
        float level = endpoint.meter(raw);
        NovaListenDiag.setLevel(level);
        if (cb != null) ui.post(new NovaSpeechPost(this, NovaSpeechPost.RMS, null, false, listenGen, 0, level));
        applyEndpointDecision(endpoint.decision(now));
    }

    void onResults(Bundle b) {
        int gen = listenGen;
        if (destroyed || !listening) return;
        cancelEndpointWatches();
        isStarting = false;
        listening = false;
        lastStopAt = android.os.SystemClock.uptimeMillis();
        beep.restoreNow();
        notifyState(false);
        String text = firstResult(b);
        if (text == null || text.trim().isEmpty()) text = endpoint.lastPartial();
        if (text == null || text.trim().isEmpty()) {
            if (cb != null) ui.post(new NovaSpeechPost(this, NovaSpeechPost.RETRY, null, false, gen, NovaVoiceGate.ERR_NO_MATCH));
            return;
        }
        final String said = NovaAsrText.tidy(text);
        if (said.isEmpty()) {
            if (cb != null) ui.post(new NovaSpeechPost(this, NovaSpeechPost.RETRY, null, false, gen, NovaVoiceGate.ERR_NO_MATCH));
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
        endpoint.onPartial(said, android.os.SystemClock.uptimeMillis());
        if (cb != null) ui.post(new NovaSpeechPost(this, NovaSpeechPost.PARTIAL, said, false, listenGen));
    }

    void onEndOfSpeech() {
        if (destroyed || !listening) return;
        cancelResultsWait();
        resultsWait = new NovaSpeechResultsWait(this, listenGen);
        ui.postDelayed(resultsWait, NovaEndpoint.RESULTS_WAIT_MS);
    }

    void onResultsWait(int gen) {
        resultsWait = null;
        if (destroyed || !listening || gen != listenGen) return;
        // System end-of-speech already fired; if finals are late, use the last partial.
        finishFromEndpoint(false);
    }

    void onEndpointWatch(int gen) {
        endWatch = null;
        if (destroyed || !listening || gen != listenGen) return;
        applyEndpointDecision(endpoint.decision(android.os.SystemClock.uptimeMillis()));
        if (listening) scheduleEndpointWatch();
    }

    void applyEndpointDecision(int decision) {
        if (decision == NovaEndpoint.CONTINUE) return;
        finishFromEndpoint(decision == NovaEndpoint.END_EMPTY);
    }

    void finishFromEndpoint(boolean emptyWindow) {
        if (!listening) return;
        int gen = listenGen;
        String said = NovaAsrText.tidy(endpoint.lastPartial());
        cancelEndpointWatches();
        listening = false;
        beep.restoreNow();
        notifyState(false);
        // Keep the recognizer instance warm for the next startListening.
        if (said != null && !said.isEmpty()) {
            if (cb != null) ui.post(new NovaSpeechPost(this, NovaSpeechPost.RESULT, said, false, gen));
            return;
        }
        int code = emptyWindow ? NovaVoiceGate.ERR_NO_MATCH : NovaVoiceGate.ERR_SPEECH_TIMEOUT;
        if (cb != null) ui.post(new NovaSpeechPost(this, NovaSpeechPost.RETRY, null, false, gen, code));
    }

    void scheduleEndpointWatch() {
        cancelEndWatch();
        endWatch = new NovaSpeechEndWatch(this, listenGen);
        ui.postDelayed(endWatch, NovaEndpoint.WATCH_MS);
    }

    void cancelEndWatch() {
        if (endWatch != null) {
            ui.removeCallbacks(endWatch);
            endWatch = null;
        }
    }

    void cancelResultsWait() {
        if (resultsWait != null) {
            ui.removeCallbacks(resultsWait);
            resultsWait = null;
        }
    }

    void cancelEndpointWatches() {
        cancelEndWatch();
        cancelResultsWait();
    }

    /** Soft errors that should restart listening in a hands-free loop (not a hard failure). */
    public static boolean softListenError(int code) {
        return NovaConvo.shouldRetry(code);
    }

    void onError(int code) {
        long elapsed = listenElapsedMs();
        isStarting = false;
        lastStopAt = android.os.SystemClock.uptimeMillis();
        String src = usingBuiltin ? "builtin" : (offlineTried ? "offline" : "system");
        NovaListenDiag.setError(code, elapsed, src);
        android.util.Log.i("NovaListen", NovaListenDiag.lastErrorLine());
        if (destroyed) return;
        int gen = listenGen;
        // Instant offline failure: switch to the default recognizer once and
        // stick with it for this session. Do it after a short gap, never in
        // the same call as the error (that was a tight start/cancel loop).
        if (offlineTried && !offlineUnavailable && NovaListenGate.isInstantFailure(elapsed)) {
            offlineUnavailable = true;
            listening = false;
            beep.restoreNow();
            notifyState(false);
            scheduleListenGo(false, language, NovaListenGate.START_GAP_MS);
            return;
        }
        listening = false;
        beep.restoreNow();
        notifyState(false);
        if (NovaConvo.isMicBusy(code)) {
            String partial = NovaAsrText.tidy(endpoint.lastPartial());
            if (partial != null && !partial.isEmpty()) {
                if (cb != null) ui.post(new NovaSpeechPost(this, NovaSpeechPost.RESULT, partial, false, gen));
                return;
            }
        }
        // Instant errors and busy/audio go to the view so it can back off and
        // stop. Do not recreate the recognizer or switch engines here.
        if (NovaListenGate.isInstantFailure(elapsed) || NovaListenGate.isEmptyCycle(code)
                || NovaConvo.isMicBusy(code) || softListenError(code)) {
            if (cb != null) ui.post(new NovaSpeechPost(this, NovaSpeechPost.RETRY, null, false, gen, code));
            return;
        }
        if (tryLanguageOrBuiltin(code)) return;
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

package com.codeaque.launcher;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;

import java.util.ArrayList;

/**
 * 0.10.54: speech recognizer in Chat. Prefers the offline recognizer, no API keys.
 * 0.10.55: hands-free loop while voice mode is on — soft errors (timeout, no match)
 * resume listening; late errors after stop are ignored so TTS is not interrupted.
 * Named listener (NovaSpeechListen) — d8 8.2 has NPEd on anonymous listeners.
 */
public final class NovaSpeech {
    public static final String PREF_VOICE_TALK = "novaVoiceTalk";

    public interface Callbacks {
        void onSpeechResult(String text);
        void onSpeechError(String message);
        void onSpeechState(boolean listening);
        /** Timeout / no-match / busy: keep the hands-free loop going. */
        void onSpeechRetry();
        /** No in-app recognizer; use ACTION_RECOGNIZE_SPEECH. */
        void onSpeechUsePrompt();
        /** No recognition service at all; show the Play Store message and button. */
        void onSpeechNeedService();
    }

    final Handler ui = new Handler(Looper.getMainLooper());
    SpeechRecognizer rec;
    Callbacks cb;
    boolean listening, offlineTried, destroyed;
    Context app;

    public static boolean voiceTalkOn(SharedPreferences p) {
        return p == null || p.getBoolean(PREF_VOICE_TALK, true);
    }

    public boolean isListening() { return listening; }

    public void start(Context ctx, Callbacks callbacks) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            ui.post(new NovaSpeechStart(this, ctx, callbacks));
            return;
        }
        stop();
        destroyed = false;
        cb = callbacks;
        if (ctx == null) {
            fail("Voice talk isn't available on this phone. You can type your question instead.");
            return;
        }
        app = ctx.getApplicationContext();
        boolean recOk = false;
        try { recOk = SpeechRecognizer.isRecognitionAvailable(ctx); } catch (Exception ignored) { }
        boolean promptOk = promptAvailable(ctx);
        int path = NovaSpeechAvail.path(recOk, promptOk);
        if (path == NovaSpeechAvail.NEED_INSTALL) {
            fail(NovaSpeechAvail.NO_SERVICE);
            if (cb != null) ui.post(new NovaSpeechPost(this, NovaSpeechPost.NEED_SERVICE, NovaSpeechAvail.NO_SERVICE, false));
            return;
        }
        if (path == NovaSpeechAvail.USE_PROMPT) {
            if (cb != null) ui.post(new NovaSpeechPost(this, NovaSpeechPost.USE_PROMPT, null, false));
            return;
        }
        try {
            rec = SpeechRecognizer.createSpeechRecognizer(ctx);
            rec.setRecognitionListener(new NovaSpeechListen(this));
        } catch (Exception e) {
            rec = null;
            if (promptOk) {
                if (cb != null) ui.post(new NovaSpeechPost(this, NovaSpeechPost.USE_PROMPT, null, false));
                return;
            }
            fail(NovaSpeechAvail.NO_SERVICE);
            if (cb != null) ui.post(new NovaSpeechPost(this, NovaSpeechPost.NEED_SERVICE, NovaSpeechAvail.NO_SERVICE, false));
            return;
        }
        listen(true);
    }

    public void toggle(Context ctx, Callbacks callbacks) {
        if (listening) stop();
        else start(ctx, callbacks);
    }

    public void stop() {
        listening = false;
        offlineTried = false;
        SpeechRecognizer r = rec;
        rec = null;
        if (r != null) {
            try { r.stopListening(); } catch (Exception ignored) { }
            try { r.cancel(); } catch (Exception ignored) { }
            try { r.destroy(); } catch (Exception ignored) { }
        }
        notifyState(false);
    }

    public void destroy() {
        destroyed = true;
        stop();
        cb = null;
        app = null;
    }

    void listen(boolean preferOffline) {
        if (destroyed || rec == null) return;
        Intent i = recognizeIntent(preferOffline);
        offlineTried = preferOffline;
        listening = true;
        notifyState(true);
        try { rec.startListening(i); }
        catch (Exception e) {
            if (preferOffline) { listen(false); return; }
            listening = false;
            notifyState(false);
            fail("I couldn't start the mic. You can type your question instead.");
        }
    }

    void onReady() { }

    void onResults(Bundle b) {
        if (destroyed || !listening) return;
        listening = false;
        notifyState(false);
        String text = firstResult(b);
        if (text == null || text.trim().isEmpty()) {
            fail("I didn't catch that. Try again, or type it.");
            return;
        }
        final String said = text.trim();
        if (cb != null) ui.post(new NovaSpeechPost(this, NovaSpeechPost.RESULT, said, false));
    }

    /** Soft errors that should restart listening in a hands-free loop (not a hard failure). */
    public static boolean softListenError(int code) {
        return NovaVoiceGate.shouldRecycleRecognizer(code);
    }

    void onError(int code) {
        if (destroyed || !listening) return;
        // Offline packs are missing on some phones: retry once without EXTRA_PREFER_OFFLINE.
        if (offlineTried && (code == SpeechRecognizer.ERROR_CLIENT
                || code == SpeechRecognizer.ERROR_SERVER
                || code == SpeechRecognizer.ERROR_NETWORK
                || code == SpeechRecognizer.ERROR_NETWORK_TIMEOUT)) {
            listen(false);
            return;
        }
        listening = false;
        notifyState(false);
        if (softListenError(code)) {
            if (cb != null) ui.post(new NovaSpeechPost(this, NovaSpeechPost.RETRY, null, false));
            return;
        }
        fail(friendlyError(code));
    }

    static String firstResult(Bundle b) {
        if (b == null) return null;
        ArrayList<String> list = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (list == null || list.isEmpty()) return null;
        return list.get(0);
    }

    static String friendlyError(int code) {
        if (code == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS)
            return "The mic wasn't allowed, so you can type your question instead.";
        if (code == SpeechRecognizer.ERROR_SPEECH_TIMEOUT || code == SpeechRecognizer.ERROR_NO_MATCH)
            return "I didn't catch that. Try again, or type it.";
        if (code == SpeechRecognizer.ERROR_RECOGNIZER_BUSY)
            return "The mic is busy. Try again in a moment, or type it.";
        return "I couldn't hear that. You can type your question instead.";
    }

    public static Intent recognizeIntent(boolean preferOffline) {
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, NovaSpeechAvail.LANGUAGE);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, NovaSpeechAvail.LANGUAGE);
        i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false);
        i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        i.putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to Nova");
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

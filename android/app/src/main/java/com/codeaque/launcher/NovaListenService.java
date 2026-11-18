package com.codeaque.launcher;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import java.util.Locale;

/**
 * 0.10.58: optional hands-free listen while Nova is hidden. Voice replies only —
 * never draws over other apps. Start only from a user action while CodeAque is
 * in the foreground (Android 14 microphone foreground-service rule).
 */
public class NovaListenService extends Service implements TextToSpeech.OnInitListener {
    public static final String ACTION_START = "com.codeaque.launcher.NOVA_LISTEN_START";
    public static final String ACTION_STOP = "com.codeaque.launcher.NOVA_LISTEN_STOP";
    static final String CH = "nova-listen";
    static final int NOTE_ID = 58;

    /** Set by MainActivity. Starting the service is only allowed while this is true. */
    public static volatile boolean appInForeground;

    final Handler ui = new Handler(Looper.getMainLooper());
    final NovaListenSpeechCb speechCb = new NovaListenSpeechCb(this);
    final NovaListenTtsProg ttsProg = new NovaListenTtsProg(this);
    NovaSpeech speech;
    TextToSpeech tts;
    boolean ttsReady, speaking, running, kids;
    String pendingSpeak;
    int speakSeq;

    public static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("codeaque", MODE_PRIVATE);
    }

    public static boolean micAllowed(Context c) {
        if (Build.VERSION.SDK_INT < 23) return true;
        return c.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean shouldRun(Context c) {
        SharedPreferences p = prefs(c);
        boolean hidden = NovaListenHidden.isHidden(
                p.getBoolean(NovaView.PREF_BUBBLE, true),
                p.getBoolean(NovaView.PREF_HIDDEN, false));
        return NovaListenHidden.shouldRun(
                p.getBoolean(NovaListenHidden.PREF, false),
                hidden,
                NovaSpeech.voiceTalkOn(p));
    }

    /** Start only from a user action while the activity is in the foreground. */
    public static void tryStartFromUser(Context c) {
        if (c == null) return;
        if (!NovaListenHidden.canStartFromUser(shouldRun(c), appInForeground, micAllowed(c))) return;
        Intent i = new Intent(c, NovaListenService.class).setAction(ACTION_START);
        try {
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i);
            else c.startService(i);
        } catch (Exception ignored) { }
    }

    public static void stopAndClear(Context c) {
        if (c == null) return;
        try {
            prefs(c).edit().putBoolean(NovaListenHidden.PREF, false).apply();
        } catch (Exception ignored) { }
        stopOnly(c);
    }

    public static void stopOnly(Context c) {
        if (c == null) return;
        try { c.stopService(new Intent(c, NovaListenService.class)); } catch (Exception ignored) { }
    }

    /** Chat is about to use the mic. Release this service without clearing the hidden-listen pref. */
    public static void pauseForChat(Context c) {
        stopOnly(c);
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public void onCreate() {
        super.onCreate();
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            NovaNotifyQuiet.install(nm, CH, "Nova listening", "Nova is listening while hidden");
        }
        startFg(note());
        tts = new TextToSpeech(this, this);
        kids = prefs(this).getBoolean("kidsMode", false);
    }

    void startFg(Notification n) {
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTE_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
        } else {
            startForeground(NOTE_ID, n);
        }
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        startFg(note());
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopAndClear(this);
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!shouldRun(this) || !micAllowed(this)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        running = true;
        kids = prefs(this).getBoolean("kidsMode", false);
        maybeListen();
        return START_NOT_STICKY;
    }

    @Override public void onDestroy() {
        running = false;
        stopMic();
        if (tts != null) {
            try { tts.stop(); tts.shutdown(); } catch (Exception ignored) { }
            tts = null;
        }
        super.onDestroy();
    }

    @Override public void onInit(int status) {
        ttsReady = status == TextToSpeech.SUCCESS && tts != null;
        if (ttsReady) {
            try {
                if (tts.isLanguageAvailable(Locale.UK) >= TextToSpeech.LANG_AVAILABLE) tts.setLanguage(Locale.UK);
                else if (tts.isLanguageAvailable(Locale.US) >= TextToSpeech.LANG_AVAILABLE) tts.setLanguage(Locale.US);
                else tts.setLanguage(Locale.UK);
                tts.setPitch(NovaTtsVoice.PITCH);
                tts.setSpeechRate(NovaKokoro.systemRateFrom(prefs(this)));
                tts.setOnUtteranceProgressListener(ttsProg);
            } catch (Exception ignored) { }
            if (pendingSpeak != null) flushSpeak();
        }
        maybeListen();
    }

    void maybeListen() {
        if (!running || !shouldRun(this)) return;
        if (!NovaListenHidden.canListenNow(true, speaking || pendingSpeak != null)) return;
        if (speech != null && speech.isListening()) return;
        if (speech == null) speech = new NovaSpeech();
        speech.start(this, speechCb);
    }

    void stopMic() {
        if (speech != null) {
            speech.stop();
            speech.destroy();
            speech = null;
        }
    }

    void onHeard(String text) {
        if (text == null || text.trim().isEmpty()) { ui.postDelayed(new NovaListenSoonSvc(this), 400); return; }
        stopMic();
        String q = text.trim();
        kids = prefs(this).getBoolean("kidsMode", false);
        if (kids && NovaHelp.looksLikeJoke(q)) {
            int i = prefs(this).getInt(NovaView.PREF_JOKE, 0);
            prefs(this).edit().putInt(NovaView.PREF_JOKE, i + 1).apply();
            speak(NovaHelp.jokeAt(i));
            return;
        }
        boolean smarter = prefs(this).getBoolean(NovaView.PREF_SMARTER, true);
        if (kids && smarter && NovaHelp.kidsMayAskOnline(q)) {
            speaking = true;
            new Thread(new NovaListenWiki(this, q), "nova-listen-wiki").start();
            return;
        }
        String a = NovaHelp.answer(q, kids);
        if (kids && NovaHelp.kidsUnsafe(a)) a = NovaHelp.KIDS_UNSURE;
        speak(a);
    }

    void onWikiDone(String ans) {
        speaking = false;
        if (ans == null || ans.isEmpty()) ans = NovaHelp.KIDS_UNSURE;
        speak(ans);
    }

    void speak(String s) {
        if (s == null || s.isEmpty()) { ui.postDelayed(new NovaListenSoonSvc(this), 400); return; }
        speaking = true;
        stopMic();
        pendingSpeak = s;
        if (ttsReady) flushSpeak();
    }

    void flushSpeak() {
        if (tts == null || pendingSpeak == null) return;
        String s = pendingSpeak;
        pendingSpeak = null;
        speakSeq++;
        String id = "nova-listen-" + speakSeq;
        try {
            tts.speak(s, TextToSpeech.QUEUE_FLUSH, null, id);
        } catch (Exception e) {
            speaking = false;
            ui.postDelayed(new NovaListenSoonSvc(this), 400);
        }
    }

    void onSpeakEnd() {
        speaking = false;
        pendingSpeak = null;
        if (running) ui.postDelayed(new NovaListenSoonSvc(this), 400);
    }

    void onListenRetry() {
        ui.postDelayed(new NovaListenSoonSvc(this), 350);
    }

    Notification note() {
        Intent open = new Intent(this, MainActivity.class).setAction(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        int fl = Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_UPDATE_CURRENT | 0x02000000 : PendingIntent.FLAG_UPDATE_CURRENT;
        PendingIntent pi = PendingIntent.getActivity(this, 58, open, fl);
        Intent stop = new Intent(this, NovaListenService.class).setAction(ACTION_STOP);
        PendingIntent stopPi;
        if (Build.VERSION.SDK_INT >= 26)
            stopPi = PendingIntent.getForegroundService(this, 59, stop, fl);
        else
            stopPi = PendingIntent.getService(this, 59, stop, fl);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CH) : new Notification.Builder(this);
        b.setContentTitle("Nova is listening")
                .setContentText("She will answer by voice. She is not drawn over other apps.")
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentIntent(pi).setOngoing(true).setOnlyAlertOnce(true)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPi);
        if (Build.VERSION.SDK_INT < 26) b.setPriority(Notification.PRIORITY_LOW);
        return b.build();
    }
}

final class NovaListenSpeechCb implements NovaSpeech.Callbacks {
    final NovaListenService svc;
    NovaListenSpeechCb(NovaListenService svc) { this.svc = svc; }
    @Override public void onSpeechResult(String text) { svc.onHeard(text); }
    @Override public void onSpeechPartial(String text) { }
    @Override public void onSpeechMicBusy(String message) { /* do not retry against a busy mic */ }
    @Override public void onSpeechError(String message) {
        if (message != null && (message.toLowerCase().contains("wasn't allowed")
                || (message != null && message.contains("Mic is busy")))) {
            NovaListenService.stopAndClear(svc);
            return;
        }
        svc.onListenRetry();
    }
    @Override public void onSpeechState(boolean listening) { }
    @Override public void onSpeechRetry(int code) { svc.onListenRetry(); }
    @Override public void onSpeechUsePrompt() { svc.onListenRetry(); }
    @Override public void onSpeechNeedService() { NovaListenService.stopAndClear(svc); }
}

final class NovaListenTtsProg extends UtteranceProgressListener {
    final NovaListenService svc;
    NovaListenTtsProg(NovaListenService svc) { this.svc = svc; }
    @Override public void onStart(String id) { }
    @Override public void onDone(String id) { svc.ui.post(new NovaListenSpeakEnd(svc)); }
    @Override public void onError(String id) { svc.ui.post(new NovaListenSpeakEnd(svc)); }
}

final class NovaListenSpeakEnd implements Runnable {
    final NovaListenService svc;
    NovaListenSpeakEnd(NovaListenService svc) { this.svc = svc; }
    @Override public void run() { svc.onSpeakEnd(); }
}

final class NovaListenSoonSvc implements Runnable {
    final NovaListenService svc;
    NovaListenSoonSvc(NovaListenService svc) { this.svc = svc; }
    @Override public void run() { if (svc.running) svc.maybeListen(); }
}

final class NovaListenWiki implements Runnable {
    final NovaListenService svc;
    final String question;
    NovaListenWiki(NovaListenService svc, String question) {
        this.svc = svc;
        this.question = question;
    }
    @Override public void run() {
        String local = NovaHelp.answer(question, true);
        String wiki = null;
        try { wiki = NovaWiki.fetchSummary(question); } catch (Exception ignored) { }
        String a = local;
        if (wiki != null && !wiki.isEmpty() && !NovaHelp.kidsUnsafe(wiki)) a = wiki;
        else if (a == null || NovaHelp.kidsUnsafe(a) || a.equals(NovaHelp.KIDS_FOOT)) a = NovaHelp.KIDS_UNSURE;
        final String ans = a;
        svc.ui.post(new NovaListenWikiDone(svc, ans));
    }
}

final class NovaListenWikiDone implements Runnable {
    final NovaListenService svc;
    final String ans;
    NovaListenWikiDone(NovaListenService svc, String ans) { this.svc = svc; this.ans = ans; }
    @Override public void run() { svc.onWikiDone(ans); }
}

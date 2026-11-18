package com.codeaque.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.graphics.Typeface;
import android.os.Build;
import android.speech.tts.TextToSpeech;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.view.WindowInsets;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Locale;

/**
 * Corner bubble for Nova, plus an expandable offline help panel.
 * Lives as a sibling of HomeView so blink/breath never runs on the row scroller.
 * In Kids mode: games help, learning help and jokes only; speech is on by default.
 */
public class NovaView extends FrameLayout implements android.view.Choreographer.FrameCallback {
    public static final String PREF_BUBBLE = "novaBubble";
    public static final String PREF_OVERLAY = "novaOverlay";
    /** 0.10.53: Kids-mode speech. Default off means she speaks (mute is opt-in). */
    public static final String PREF_KIDS_MUTE = "novaKidsMute";
    public static final String PREF_JOKE = "novaJokeAt";
    public static final String PREF_LEFT = "novaBubbleLeft";
    public static final String PREF_Y = "novaBubbleY";
    public static final String PREF_HIDDEN = "novaHidden";
    public static final String PREF_BIG = "novaBig";
    /** 0.10.54: double-tap tucks her into a corner (still visible). Full hide is separate. */
    public static final String PREF_TUCKED = "novaTucked";
    /** 0.10.54: grown-ups can turn Voice talk off. Default on. */
    public static final String PREF_VOICE_TALK = NovaSpeech.PREF_VOICE_TALK;
    /** 0.10.54: optional short online fact. Default on. Branded "Smarter Nova". */
    public static final String PREF_SMARTER = "novaSmarter";

    final NovaFace face = new NovaFace();
    final NovaAnim.State st = new NovaAnim.State();
    final RectF bubble = new RectF(), muteHit = new RectF();
    final Paint mutePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    final android.view.Choreographer choreo = android.view.Choreographer.getInstance();
    final SharedPreferences prefs;
    final java.util.Random rng = new java.util.Random();
    LinearLayout panel;
    TextView answerView, muteLabel, voiceOfferStatus, engineLabel, liveCaption;
    Button voiceOfferBtn;
    EditText askField;
    Button micBtn, typeBtn, hideBtn, speechInstallBtn, asrOfferBtn, asrOfferSmallBtn;
    TextView listenStatus, asrOfferStatus;
    NovaMicMeter micMeter;
    TextToSpeech tts;
    NovaSpeech speech;
    String pendingSpeak, lastAnswer = NovaHelp.GREETING, lastSpoken, voiceTag;
    /** 0.10.60: single-flight speak id, shared with NovaVoiceSpeakJob. */
    int speakGen;
    boolean speakJobInFlight;
    long silentUntil;
    int convoState;
    String lastHeard;
    NovaReplyTimeout replyWait;
    NovaListenSoon listenSoon;
    int emptyListenCycles;
    boolean listenGiveUp, preferBuiltin, asrDlBusy, busyRetried, heartWaitExtended, heartFallbackRetried;
    boolean listenStarted, pendingRude;
    boolean heardThisListen;
    long lastRmsAt, thinkStarted;
    NovaThinkTick thinkTick;
    NovaListenWatch listenWatch;
    NovaMeterDecay meterDecay;
    String pendingRest;
    long listenHideAfter;
    boolean expanded, running, overlayMode, faceLoaded, kids, talking, ttsReady, deviceVoice;
    int ttsInitTries;
    final RectF tabHit = new RectF();
    boolean dragging, snapping, pinching, hidden, big, tucked, downOnMute, downOnBubble, downOnTab, placed;
    boolean voiceLoop, awaitingAnswer, micAsked;
    TextView listenLabel;
    String talkId;
    int speakSeq;
    boolean ignoreUntilUp, tapArmedMute;
    float downX, downY, startLeft, startTop, slop, snapX0, snapY0, snapX1, snapY1;
    float pinchStartSpan, pinchScale;
    long lastFrameNs, talkUntil, downAt, snapStart, tapArmedAt;
    int bubbleSize, tuckSize, tuckMargin, margin, longPressMs;
    Host host;

    public interface Host {
        void onNovaExpandChanged(boolean expanded);
        default boolean novaMicAllowed() { return false; }
        default void novaRequestMic() { }
        /** 0.10.59: system speak-now screen when SpeechRecognizer is missing. */
        default void novaStartSpeechPrompt() { }
        /** 0.10.59: open Play Store for Google Speech Recognition & Synthesis. */
        default void novaOpenSpeechInstall() { }
        /** 0.10.58: double-tap or Hide turned Show Nova off. May start hidden-listen if opted in. */
        default void onNovaHiddenByUser() { }
    }

    public NovaView(Context ctx) { this(ctx, ctx.getSharedPreferences("codeaque", Context.MODE_PRIVATE), false); }

    public NovaView(Context ctx, SharedPreferences p, boolean overlayMode) {
        super(ctx);
        this.prefs = p;
        this.overlayMode = overlayMode;
        setWillNotDraw(false);
        setClipToPadding(false);
        setClipChildren(false);
        float d = getResources().getDisplayMetrics().density;
        bubbleSize = Math.round((overlayMode ? 72 : 96) * d);
        tuckSize = Math.round((overlayMode ? 48 : 58) * d);
        tuckMargin = Math.round(6 * d);
        margin = Math.round(14 * d);
        slop = ViewConfiguration.get(ctx).getScaledTouchSlop();
        longPressMs = ViewConfiguration.getLongPressTimeout();
        NovaAnim.reset(st, android.os.SystemClock.uptimeMillis());
        buildPanel(d);
        setPanelShown(false);
        applyPref();
    }

    public void setHost(Host h) { host = h; }

    /** 0.10.53: Kids home uses the same bubble, with a kid-safe panel and spoken replies. */
    public void setKids(boolean on) {
        if (kids == on) return;
        kids = on;
        lastAnswer = on ? NovaHelp.KIDS_GREETING : NovaHelp.GREETING;
        talking = false;
        stopSpeak();
        rebuildPanel();
        if (on) ensureTts();
    }

    public boolean kids() { return kids; }

    public void applyPref() {
        boolean parent = prefs == null || prefs.getBoolean(PREF_BUBBLE, true);
        hidden = prefs != null && prefs.getBoolean(PREF_HIDDEN, false);
        big = prefs != null && prefs.getBoolean(PREF_BIG, false);
        tucked = prefs != null && prefs.getBoolean(PREF_TUCKED, false);
        if (hidden) tucked = false;
        if (!parent) {
            setVisibility(GONE);
            hidden = false;
            big = false;
            collapse();
            stopAnim();
            return;
        }
        setVisibility(VISIBLE);
        if (hidden) {
            big = false;
            collapse();
            stopSpeak();
            stopAnim();
        } else {
            startAnim();
        }
        requestLayout();
        invalidate();
    }

    public boolean isExpanded() { return expanded; }

    /** Collapse Chat, or shrink from the large view first. Returns true if it consumed Back. */
    public boolean collapse() {
        if (big) { setBig(false); return true; }
        if (!expanded) return false;
        setExpanded(false);
        return true;
    }

    public void toggle() { setExpanded(!expanded); }

    public RectF bubbleRect() { return bubble; }

    public int bubbleSize() { return bubbleSize; }

    void setExpanded(boolean on) {
        if (expanded == on) return;
        expanded = on;
        setPanelShown(on);
        if (host != null) host.onNovaExpandChanged(on);
        if (on) {
            if (askField != null) askField.clearFocus();
            if (getContext() != null) NovaListenService.pauseForChat(getContext());
        } else {
            hideSoftIme();
            exitVoiceMode();
            stopSpeak();
            stopListen();
        }
        invalidate();
    }

    void greet() {
        pendingRest = null;
        pendingRude = false;
        if (getContext() != null) NovaListenService.pauseForChat(getContext());
        lastAnswer = NovaConvo.speakText(kids ? NovaHelp.KIDS_GREETING : NovaHelp.GREETING);
        if (answerView != null) answerView.setText(lastAnswer);
        cancelThinkTick();
        showListenStatus(NovaConvo.SPEAKING_TEXT);
        convoState = NovaConvo.SPEAKING;
        if (!muted()) speak(lastAnswer, true, true);
        // Safety net: if TTS onDone never arrives, still start listening after the greeting.
        if (voiceLoop) {
            long wait = NovaTtsVoice.estimateMs(lastAnswer) + NovaSpeakGate.HANGOVER_MS + 400;
            postDelayed(new NovaListenSoon(this), wait);
        }
    }

    void setPanelShown(boolean on) {
        if (panel == null) return;
        panel.setVisibility(on ? VISIBLE : GONE);
        if (on) {
            panel.setAlpha(0f);
            panel.setTranslationY(dp(12));
            panel.animate().alpha(1f).translationY(0).setDuration(180)
                    .setInterpolator(new DecelerateInterpolator()).start();
        }
    }

    void rebuildPanel() {
        if (panel != null) removeView(panel);
        panel = null;
        askField = null;
        answerView = null;
        muteLabel = null;
        engineLabel = null;
        voiceOfferBtn = null;
        voiceOfferStatus = null;
        micBtn = null;
        typeBtn = null;
        hideBtn = null;
        listenLabel = null;
        listenStatus = null;
        liveCaption = null;
        speechInstallBtn = null;
        asrOfferBtn = null;
        asrOfferSmallBtn = null;
        asrOfferStatus = null;
        micMeter = null;
        stopListen();
        buildPanel(getResources().getDisplayMetrics().density);
        setPanelShown(expanded);
        requestLayout();
        invalidate();
    }

    void buildPanel(float d) {
        int pad = Math.round(10 * d);
        panel = new LinearLayout(getContext());
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(pad, pad, pad, pad);
        styleGlass(panel, 0xB8141A22, 0x66E8C872, 22 * d, Math.max(1f, 1.25f * d));
        if (Build.VERSION.SDK_INT >= 21) {
            panel.setElevation(3 * d);
            panel.setClipToOutline(true);
        }

        LinearLayout head = new LinearLayout(getContext());
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(android.view.Gravity.CENTER_VERTICAL);
        TextView title = new TextView(getContext());
        title.setText("Chat");
        title.setTextColor(0xFFF2F6FA);
        title.setTextSize(18);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        tlp.setMargins(0, 0, Math.round(8 * d), 0);
        head.addView(title, tlp);
        // Version stays in the footer only. Putting it in this row made "Chat"
        // wrap to one letter per line on a short landscape card.
        android.view.View spacer = new android.view.View(getContext());
        head.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1));
        listenLabel = new TextView(getContext());
        listenLabel.setText("Listening…");
        listenLabel.setTextColor(0xFF1C1A18);
        listenLabel.setTextSize(13);
        listenLabel.setTypeface(Typeface.DEFAULT_BOLD);
        listenLabel.setPadding(Math.round(10 * d), Math.round(5 * d), Math.round(10 * d), Math.round(5 * d));
        styleGlass(listenLabel, 0xFFE8C872, 0x00E8C872, 14 * d, 0);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        llp.setMargins(0, 0, Math.round(8 * d), 0);
        head.addView(listenLabel, llp);
        muteLabel = btn(muted() ? "Muted" : "Sound on", v -> toggleMute());
        head.addView(muteLabel);
        engineLabel = new TextView(getContext());
        engineLabel.setTextColor(0xFFE8C872);
        engineLabel.setTextSize(12);
        engineLabel.setPadding(Math.round(8 * d), Math.round(6 * d), Math.round(8 * d), Math.round(6 * d));
        engineLabel.setVisibility(INVISIBLE);
        engineLabel.setMinWidth(Math.round(72 * d));
        if (voiceTag != null && !voiceTag.isEmpty() && (talking || speakJobInFlight)) {
            engineLabel.setText(voiceTag);
            engineLabel.setVisibility(VISIBLE);
        }
        head.addView(engineLabel);
        panel.addView(head);
        refreshMicLabel();

        HorizontalScrollView chips = new HorizontalScrollView(getContext());
        chips.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (String c : (kids ? NovaHelp.kidsChips() : NovaHelp.chips())) {
            TextView chip = new TextView(getContext());
            chip.setText(c);
            chip.setTextColor(0xFFF2F6FA);
            chip.setTextSize(13);
            chip.setPadding(Math.round(12 * d), Math.round(7 * d), Math.round(12 * d), Math.round(7 * d));
            styleGlass(chip, 0x55202830, 0x44E8C872, 16 * d, 1f);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, pad / 2, Math.round(6 * d), 0);
            final String q = c;
            chip.setOnClickListener(v -> {
                if (talking || speakJobInFlight) return;
                ask(q);
            });
            row.addView(chip, lp);
        }
        chips.addView(row);

        // 0.10.67: chips, the joke button, and offers scroll. The pinned reply,
        // status, input, Mic, meter and version stay in reserved space so a
        // short landscape card cannot draw the reply on top of the Mic.
        LinearLayout mid = new LinearLayout(getContext());
        mid.setOrientation(LinearLayout.VERTICAL);
        mid.addView(chips, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        if (kids) {
            Button joke = btn("Tell me a joke", v -> tellJoke());
            joke.setTextSize(16);
            styleGlass(joke, 0x55202830, 0x66E8C872, 18 * d, 1.25f);
            LinearLayout.LayoutParams jlp = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            jlp.topMargin = pad;
            mid.addView(joke, jlp);
        }

        TextView foot = new TextView(getContext());
        foot.setText(kids ? NovaHelp.KIDS_FOOT : NovaHelp.LIMITS);
        foot.setTextColor(0xFF8A98A4);
        foot.setTextSize(11);
        mid.addView(foot);

        voiceOfferStatus = new TextView(getContext());
        voiceOfferStatus.setTextColor(0xFFE8C872);
        voiceOfferStatus.setTextSize(12);
        voiceOfferStatus.setPadding(0, pad / 2, 0, 0);
        mid.addView(voiceOfferStatus);
        voiceOfferBtn = btn("Get Nova's voice", new NovaVoiceOfferClick(this));
        mid.addView(voiceOfferBtn);
        refreshVoiceOffer();

        speechInstallBtn = btn(NovaSpeechAvail.INSTALL_BTN, new NovaSpeechInstallClick(this));
        speechInstallBtn.setVisibility(GONE);
        mid.addView(speechInstallBtn);

        asrOfferStatus = new TextView(getContext());
        asrOfferStatus.setTextColor(0xFFE8C872);
        asrOfferStatus.setTextSize(12);
        asrOfferStatus.setPadding(0, pad / 2, 0, 0);
        asrOfferStatus.setVisibility(GONE);
        mid.addView(asrOfferStatus);
        asrOfferBtn = btn(NovaAsrFiles.DOWNLOAD_BTN, new NovaAsrOfferClick(this));
        asrOfferBtn.setMinHeight(dp(36));
        asrOfferBtn.setMinimumHeight(dp(36));
        asrOfferBtn.setVisibility(GONE);
        mid.addView(asrOfferBtn);
        asrOfferSmallBtn = btn(NovaAsrFiles.DOWNLOAD_BTN_SMALL, new NovaAsrSmallOfferClick(this));
        asrOfferSmallBtn.setMinHeight(dp(36));
        asrOfferSmallBtn.setMinimumHeight(dp(36));
        asrOfferSmallBtn.setVisibility(GONE);
        mid.addView(asrOfferSmallBtn);

        ScrollView sv = new ScrollView(getContext());
        sv.addView(mid);
        LinearLayout.LayoutParams svLp = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1);
        panel.addView(sv, svLp);

        // Pinned reply in its own space ABOVE the input row. Never intercepts taps.
        // Do not call panelBox() here: during construction the view has no size,
        // and panelBox used to be a startup hazard if anything else blocked.
        int viewH = getHeight();
        int panelDp = viewH > 0 ? Math.max(1, Math.round(viewH / Math.max(0.5f, d))) : 200;
        answerView = new TextView(getContext());
        answerView.setText(lastAnswer);
        answerView.setTextColor(0xFFE8E0D4);
        answerView.setTextSize(kids ? 16 : 14);
        answerView.setPadding(0, pad / 2, 0, pad / 2);
        answerView.setMaxLines(NovaChatLayout.replyMaxLines(panelDp));
        answerView.setMinHeight(dp(28));
        answerView.setClickable(false);
        answerView.setFocusable(false);
        answerView.setLongClickable(false);
        answerView.setMovementMethod(null);
        panel.addView(answerView);

        listenStatus = new TextView(getContext());
        listenStatus.setTextColor(0xFFE8C872);
        listenStatus.setTextSize(12);
        listenStatus.setPadding(0, pad / 2, 0, 0);
        listenStatus.setText("");
        listenStatus.setVisibility(VISIBLE);
        listenStatus.setClickable(false);
        listenStatus.setFocusable(false);
        panel.addView(listenStatus);

        liveCaption = new TextView(getContext());
        liveCaption.setTextColor(0xFFB8A88A);
        liveCaption.setTextSize(12);
        liveCaption.setPadding(0, pad / 2, 0, 0);
        liveCaption.setText("");
        liveCaption.setVisibility(VISIBLE);
        liveCaption.setClickable(false);
        liveCaption.setFocusable(false);
        panel.addView(liveCaption);

        LinearLayout askRow = new LinearLayout(getContext());
        askRow.setOrientation(LinearLayout.HORIZONTAL);
        askRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        askField = new EditText(getContext());
        askField.setHint(kids ? "Ask me about games, words, or numbers…" : "Ask about CodeAque…");
        askField.setHintTextColor(0x88B8A88A);
        askField.setTextColor(0xFFF5F0E8);
        askField.setTextSize(14);
        askField.setSingleLine(true);
        askField.setInputType(InputType.TYPE_CLASS_TEXT);
        askField.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE
                | android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI
                | android.view.inputmethod.EditorInfo.IME_FLAG_NO_FULLSCREEN);
        styleGlass(askField, 0x66101820, 0x33E8C872, 14 * d, 1f);
        askField.setPadding(pad, pad, pad, pad);
        askField.setOnEditorActionListener((v, a, e) -> {
            boolean go = a == android.view.inputmethod.EditorInfo.IME_ACTION_DONE
                    || a == android.view.inputmethod.EditorInfo.IME_ACTION_GO
                    || a == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
                    || (e != null && e.getKeyCode() == KeyEvent.KEYCODE_ENTER
                        && e.getAction() == KeyEvent.ACTION_DOWN);
            if (!go) return false;
            ask(askField.getText().toString());
            hideSoftIme();
            return true;
        });
        LinearLayout.LayoutParams askLp = new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1);
        askRow.addView(askField, askLp);
        // 0.10.59: Mic is always visible in Chat as tap-to-talk, even if hands-free is off.
        // 0.10.67: named listener (d8 8.2) and a wider min width so "Listening" does not shrink the field.
        micBtn = btn("Mic", new NovaMicTapClick(this));
        micBtn.setMinWidth(dp(Math.max(88, NovaChatLayout.MIC_MIN_DP)));
        micBtn.setMinHeight(dp(NovaChatLayout.MIC_MIN_DP));
        micBtn.setMinimumHeight(dp(NovaChatLayout.MIC_MIN_DP));
        micBtn.setClickable(true);
        micBtn.setFocusable(true);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, dp(NovaChatLayout.MIC_MIN_DP));
        mlp.leftMargin = pad / 2;
        askRow.addView(micBtn, mlp);
        refreshMicLabel();
        LinearLayout.LayoutParams askRowLp = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        askRowLp.topMargin = pad;
        panel.addView(askRow, askRowLp);

        LinearLayout meterRow = new LinearLayout(getContext());
        meterRow.setOrientation(LinearLayout.HORIZONTAL);
        meterRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        TextView meterLab = new TextView(getContext());
        meterLab.setText("Mic");
        meterLab.setTextColor(0xFFB8A88A);
        meterLab.setTextSize(11);
        meterLab.setPadding(0, 0, pad / 2, 0);
        meterRow.addView(meterLab);
        micMeter = new NovaMicMeter(getContext());
        LinearLayout.LayoutParams meterLp = new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1);
        meterRow.addView(micMeter, meterLp);
        TextView verFoot = new TextView(getContext());
        verFoot.setText(NovaAppVer.label(installedVersionName()));
        verFoot.setTextColor(0xFF8A98A4);
        verFoot.setTextSize(11);
        verFoot.setPadding(pad / 2, 0, 0, 0);
        verFoot.setSingleLine(true);
        meterRow.addView(verFoot, new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams meterRowLp = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        meterRowLp.topMargin = pad / 2;
        panel.addView(meterRow, meterRowLp);
        refreshAsrOffer();

        LinearLayout actions = new LinearLayout(getContext());
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setPadding(0, pad / 2, 0, 0);
        actions.addView(btn("Ask", v -> { ask(askField.getText().toString()); hideSoftIme(); }), lpBtn());
        if (!kids) actions.addView(btn("Hear it", v -> speak(lastAnswer)), lpBtn());
        typeBtn = btn("Type instead", new NovaTypeListenClick(this));
        typeBtn.setVisibility(voiceLoop ? VISIBLE : GONE);
        actions.addView(typeBtn, lpBtn());
        hideBtn = btn("Hide", v -> hideAway());
        actions.addView(hideBtn, lpBtn());
        actions.addView(btn("Close", v -> collapse()), lpBtn());
        panel.addView(actions);

        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        addView(panel, plp);
    }

    Button btn(String label, OnClickListener l) {
        Button b = new Button(getContext(), null, android.R.attr.borderlessButtonStyle);
        b.setText(label);
        b.setTextColor(0xFFE8C872);
        b.setTextSize(13);
        b.setAllCaps(false);
        b.setOnClickListener(l);
        b.setMinHeight(dp(48));
        b.setMinimumHeight(dp(48));
        b.setMinWidth(0);
        b.setPadding(dp(10), dp(8), dp(10), dp(8));
        styleGlass(b, 0x33202830, 0x33E8C872, dp(14), 1f);
        return b;
    }

    LinearLayout.LayoutParams lpBtn() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1);
        lp.height = Math.max(dp(48), LayoutParams.WRAP_CONTENT);
        return lp;
    }

    void ask(String q) { ask(q, false); }

    void pinAnswer(String text) {
        String t = text == null ? "" : text;
        if (kids) t = NovaKidsTalk.withPatience(t, pendingRude);
        lastAnswer = t;
        if (answerView != null) answerView.setText(lastAnswer);
    }

    void ask(String q, boolean fromVoice) {
        stopListen();
        cancelThinkTick();
        pendingRest = null;
        pendingRude = kids && NovaKidsTalk.isRude(q);
        String heard = pendingRude ? NovaKidsTalk.sanitizeReply(q) : q;
        String match = pendingRude ? NovaKidsTalk.stripRude(q) : q;
        if (match == null || match.isEmpty()) match = "";
        if (fromVoice) showListenStatus(NovaConvo.youSaid(heard));
        q = match;
        if (kids && NovaHelp.looksLikeJoke(q)) { tellJoke(); return; }
        if (NovaReplyQuick.isSimpleGreeting(q) || (pendingRude && (q == null || q.isEmpty()))) {
            if (pendingRude && (q == null || q.isEmpty())) {
                pinAnswer(NovaKidsTalk.STILL_HERE);
            } else {
                pinAnswer(NovaConvo.speakText(NovaReplyQuick.cannedHello(kids)));
            }
            if (!fromVoice && askField != null && heard != null) askField.setText(heard);
            showListenStatus(fromVoice ? NovaConvo.youSaid(heard) : NovaConvo.SPEAKING_TEXT);
            convoState = NovaConvo.SPEAKING;
            if (kids || !muted()) speakAuto(lastAnswer);
            invalidate();
            return;
        }
        String arith = NovaArith.answer(q);
        if (arith != null) {
            pinAnswer(NovaConvo.speakText(arith));
            if (!fromVoice && askField != null && heard != null) askField.setText(heard);
            showListenStatus(fromVoice ? NovaConvo.youSaid(heard) : NovaConvo.SPEAKING_TEXT);
            convoState = NovaConvo.SPEAKING;
            if (kids || !muted()) speakAuto(lastAnswer);
            invalidate();
            return;
        }
        String canned = NovaHelp.cannedOrNull(q, kids);
        if (canned != null) {
            if (kids && NovaHelp.kidsUnsafe(canned)) canned = NovaHelp.KIDS_UNSURE;
            pinAnswer(NovaConvo.speakText(canned));
            if (!fromVoice && !NovaConvo.writeHeardToField() && askField != null && heard != null) askField.setText(heard);
            if (fromVoice) showListenStatus(NovaConvo.youSaid(heard));
            else showListenStatus(NovaConvo.SPEAKING_TEXT);
            convoState = NovaConvo.SPEAKING;
            if (kids) speakAuto(lastAnswer);
            invalidate();
            return;
        }
        if (smarterOn() && (NovaLlm.smarterUsesModel(getContext(), prefs)
                || (kids && NovaLlm.phoneStrongEnough(getContext())))) {
            awaitingAnswer = true;
            if (!fromVoice && askField != null && heard != null) askField.setText(heard);
            convoState = NovaConvo.THINKING;
            pinAnswer("Let me think…");
            startThinkTick();
            final String question = q;
            if (NovaLlm.smarterUsesModel(getContext(), prefs)
                    && NovaLlm.phoneStrongEnough(getContext())) {
                new Thread(new NovaLlmJob(this, question), "nova-llm").start();
            } else {
                new Thread(new NovaWikiJob(this, question), "nova-wiki").start();
            }
            invalidate();
            return;
        }
        String fallback = NovaHelp.answer(q, kids);
        if (kids && NovaHelp.kidsUnsafe(fallback)) fallback = NovaHelp.KIDS_UNSURE;
        pinAnswer(NovaConvo.speakText(fallback));
        if (!fromVoice && !NovaConvo.writeHeardToField() && askField != null && heard != null) askField.setText(heard);
        if (fromVoice) showListenStatus(NovaConvo.youSaid(heard));
        else showListenStatus(NovaConvo.SPEAKING_TEXT);
        convoState = NovaConvo.SPEAKING;
        if (kids) speakAuto(lastAnswer);
        invalidate();
    }

    void finishOnlineAsk(String question) {
        String local = NovaHelp.answer(question, true);
        String wiki = null;
        try { wiki = NovaWiki.fetchSummary(question); } catch (Exception ignored) { }
        String a = local;
        if (wiki != null && !wiki.isEmpty() && !NovaHelp.kidsUnsafe(wiki)) a = wiki;
        else if (a == null || NovaHelp.kidsUnsafe(a) || a.equals(NovaHelp.KIDS_FOOT)) a = NovaHelp.KIDS_UNSURE;
        awaitingAnswer = false;
        final String ans = a;
        post(new NovaWikiDone(this, ans));
    }

    boolean smarterOn() { return prefs == null || prefs.getBoolean(PREF_SMARTER, true); }

    boolean voiceTalkOn() { return NovaSpeech.voiceTalkOn(prefs); }

    void onMicTap() {
        // Toggle off if she is already listening.
        if (speech != null && speech.isListening()) {
            stopListen();
            cancelListenSoon();
            listenGiveUp = false;
            showListenStatus(NovaListenGate.TAP_MIC);
            refreshMicLabel();
            return;
        }
        hideSoftIme();
        if (host != null && !host.novaMicAllowed()) { host.novaRequestMic(); return; }
        // A user tap must not be swallowed by the greeting, a Heart job, or the
        // hangover that stopSpeak() itself plants on silentUntil.
        if (NovaMicTap.cancelSpeechFirst(talking, thinking(), speakJobInFlight,
                pendingSpeak != null, android.os.SystemClock.uptimeMillis() < silentUntil)) {
            stopSpeak();
        }
        silentUntil = 0;
        awaitingAnswer = false;
        voiceLoop = true;
        listenGiveUp = false;
        emptyListenCycles = 0;
        busyRetried = false;
        cancelListenSoon();
        convoState = NovaConvo.LISTENING;
        showListenStatus(NovaMicTap.startedStatus());
        refreshVoiceButtons();
        if (!startListen(true)) {
            if (NovaMicTap.fallbackBuiltin(false, NovaAsr.filesReady(getContext()))) {
                preferBuiltin = true;
                showListenStatus(NovaMicTap.FAIL_FALLBACK);
                if (!startListen(true)) showListenStatus(NovaMicTap.FAIL_RETRY);
            } else {
                showListenStatus(NovaMicTap.FAIL_RETRY);
            }
        }
        refreshMicLabel();
    }

    /** Grown-ups granted or denied RECORD_AUDIO. */
    public void onMicPermission(boolean ok) {
        if (ok) {
            voiceLoop = true;
            refreshVoiceButtons();
            if (NovaSpeakGate.canStartListen(true, expanded, talking, pendingSpeak != null, speakJobInFlight, android.os.SystemClock.uptimeMillis(), silentUntil))
                startListen();
            else
                postDelayed(new NovaListenSoon(this), 200);
            if (host != null) host.onNovaHiddenByUser();
        } else {
            exitVoiceMode();
            onPermissionDenied();
        }
    }

    void startListen() { startListen(false); }

    /**
     * @param fromUser true when the Mic button was tapped. Automatic restarts
     * still wait until she has finished speaking; a user tap already cancelled
     * speech and must start listening even during a hangover.
     */
    boolean startListen(boolean fromUser) {
        long now = android.os.SystemClock.uptimeMillis();
        if (!NovaMicTap.allowStart(fromUser, expanded, voiceLoop, listenGiveUp, talking, thinking(),
                speakJobInFlight, pendingSpeak != null, now < silentUntil)) return false;
        try { NovaAsr.requestLoad(getContext()); } catch (Throwable ignored) { }
        preferBuiltin = NovaListenPick.preferBuiltinWhenReady(NovaAsr.isLoaded()) || preferBuiltin;
        heardThisListen = false;
        lastRmsAt = 0;
        if (micMeter != null) micMeter.setLevel(0f);
        if (speech == null) speech = new NovaSpeech();
        boolean ok = speech.start(getContext(), new NovaTalkCb(this), preferBuiltin);
        if (ok || (speech != null && speech.isListening())) listenStarted = true;
        scheduleListenWatch();
        startMeterDecay();
        refreshMicLabel();
        return ok || (speech != null && speech.isListening());
    }

    void stopListen() {
        cancelListenWatch();
        cancelMeterDecay();
        if (speech != null) speech.stop();
        if (micMeter != null && !isListening()) micMeter.setLevel(0f);
        refreshMicLabel();
    }

    void refreshMicLabel() {
        if (micBtn != null) {
            boolean on = isListening();
            micBtn.setText(NovaMicTap.buttonLabel(on));
            styleGlass(micBtn, on ? 0xCCE8C872 : 0x33202830, 0xFFE8C872, dp(14), on ? 2f : 1f);
            micBtn.setTextColor(on ? 0xFF1A1408 : 0xFFE8C872);
        }
        boolean speakingOrThinking = talking || speakJobInFlight || thinking();
        boolean between = voiceLoop && listenStarted && !listenGiveUp && !speakingOrThinking;
        boolean want = NovaListenGate.showListeningChip(voiceLoop, expanded, listenGiveUp,
                speakingOrThinking, isListening(), between);
        long now = android.os.SystemClock.uptimeMillis();
        listenHideAfter = NovaListenGate.hideAfter(want, now, listenHideAfter);
        boolean show = NovaListenGate.chipVisible(want, speakingOrThinking || listenGiveUp, now, listenHideAfter);
        if (listenLabel != null) listenLabel.setVisibility(show ? VISIBLE : INVISIBLE);
        if (listenGiveUp && convoState != NovaConvo.MIC_BUSY) {
            showListenStatus(NovaListenGate.TAP_MIC);
        } else if ((want || isListening())
                && convoState != NovaConvo.HEARD
                && convoState != NovaConvo.THINKING
                && convoState != NovaConvo.SPEAKING
                && convoState != NovaConvo.MIC_BUSY) {
            showListenStatus(NovaConvo.LISTENING_TEXT);
        }
        invalidate();
    }

    boolean thinking() {
        return awaitingAnswer || convoState == NovaConvo.THINKING;
    }

    void cancelListenSoon() {
        if (listenSoon != null) {
            removeCallbacks(listenSoon);
            listenSoon = null;
        }
    }

    void startThinkTick() {
        cancelThinkTick();
        thinkStarted = android.os.SystemClock.uptimeMillis();
        showListenStatus(NovaReplyQuick.thinkingText(0));
        thinkTick = new NovaThinkTick(this, thinkStarted);
        postDelayed(thinkTick, 1000);
    }

    void cancelThinkTick() {
        if (thinkTick != null) {
            removeCallbacks(thinkTick);
            thinkTick = null;
        }
    }

    public void onThinkTick(long started) {
        if (!thinking()) {
            thinkTick = null;
            return;
        }
        long now = android.os.SystemClock.uptimeMillis();
        showListenStatus(NovaReplyQuick.thinkingText(now - started));
        thinkTick = new NovaThinkTick(this, started);
        postDelayed(thinkTick, 1000);
    }

    void scheduleListenWatch() {
        cancelListenWatch();
        int gen = speech == null ? 0 : speech.listenGen;
        listenWatch = new NovaListenWatch(this, gen);
        postDelayed(listenWatch, NovaReplyQuick.LISTEN_WINDOW_MS);
    }

    void cancelListenWatch() {
        if (listenWatch != null) {
            removeCallbacks(listenWatch);
            listenWatch = null;
        }
    }

    public void onListenWatch(int gen) {
        listenWatch = null;
        if (speech == null || gen != speech.listenGen) return;
        if (!isListening()) return;
        if (heardThisListen) return;
        onEmptyListen(NovaVoiceGate.ERR_NO_MATCH);
    }

    void scheduleListen(long delayMs) {
        cancelListenSoon();
        listenSoon = new NovaListenSoon(this);
        postDelayed(listenSoon, delayMs);
    }

    /** A silent / no-match / timeout cycle. Keep the chip on, back off, then give up. */
    public void onEmptyListen(int code) {
        if (NovaConvo.isMicBusy(code)) {
            onMicBusy(NovaConvo.MIC_BUSY_TEXT);
            return;
        }
        long delay = NovaListenGate.retryDelayMs(emptyListenCycles);
        emptyListenCycles = NovaListenGate.nextEmptyCycles(emptyListenCycles, true);
        setLiveCaption("");
        boolean ready = NovaAsr.filesReady(getContext());
        if (NovaListenPick.shouldFallToBuiltin(ready, preferBuiltin, emptyListenCycles, false, false)) {
            preferBuiltin = true;
            listenGiveUp = false;
            emptyListenCycles = 0;
            cancelListenSoon();
            showListenStatus(NovaSpeechAvail.SYSTEM_ERROR_BUILTIN);
            NovaListenDiag.setReason(NovaSpeechAvail.SYSTEM_ERROR_BUILTIN);
            refreshMicLabel();
            startListen();
            return;
        }
        boolean can = NovaListenGate.canRestart(voiceLoop, expanded, listenGiveUp,
                talking, thinking(), speakJobInFlight, pendingSpeak != null);
        if (!can || !NovaListenGate.shouldRetryEmpty(emptyListenCycles)) {
            listenGiveUp = true;
            cancelListenSoon();
            if (NovaListenPick.shouldOfferBuiltin(ready, false, false, emptyListenCycles)) {
                showNeedBuiltin(NovaSpeechAvail.NO_SERVICE_NEED_BUILTIN);
            } else {
                showListenStatus(NovaListenGate.TAP_MIC);
                NovaListenDiag.setReason(NovaListenGate.TAP_MIC);
            }
            refreshMicLabel();
            return;
        }
        convoState = NovaConvo.LISTENING;
        showListenStatus(NovaConvo.LISTENING_TEXT);
        refreshMicLabel();
        scheduleListen(delay);
    }

    String installedVersionName() {
        try {
            return getContext().getPackageManager().getPackageInfo(getContext().getPackageName(), 0).versionName;
        } catch (Exception e) {
            return NovaAppVer.NAME;
        }
    }

    void showListenStatus(String msg) {
        if (listenStatus == null) return;
        listenStatus.setVisibility(VISIBLE);
        listenStatus.setText(msg == null ? "" : msg);
        if (msg != null && !msg.isEmpty()) NovaListenDiag.setReason(msg);
    }

    void setLiveCaption(String text) {
        if (liveCaption == null) return;
        liveCaption.setVisibility(VISIBLE);
        liveCaption.setText(text == null ? "" : text);
    }

    public void onPartialHeard(String text) {
        text = NovaAsrText.tidy(text);
        if (text != null && !text.isEmpty()) heardThisListen = true;
        setLiveCaption(text);
    }

    public void onMicNoSound(String message) {
        listenGiveUp = false;
        emptyListenCycles++;
        String msg = message == null || message.isEmpty() ? NovaReplyQuick.NO_SOUND : message;
        NovaListenDiag.setReason(msg);
        lastAnswer = msg;
        if (answerView != null) answerView.setText(msg);
        showListenStatus(msg);
        boolean ready = NovaAsr.filesReady(getContext());
        if (!ready) showNeedBuiltin(msg);
        else if (emptyListenCycles >= 2) {
            listenGiveUp = true;
            showListenStatus(NovaListenGate.TAP_MIC);
        } else {
            scheduleListen(400);
        }
        refreshMicLabel();
    }

    public void onMicBusy(String message) {

        boolean ready = NovaAsr.filesReady(getContext());
        boolean alreadyBuiltin = preferBuiltin || (speech != null && speech.usingBuiltin());
        // The hidden listen service may have been holding the mic. Pause it once, then retry.
        if (NovaListenPick.retryBusyAfterPause(busyRetried)) {
            busyRetried = true;
            NovaListenService.pauseForChat(getContext());
            listenGiveUp = false;
            showListenStatus("Mic was busy. Trying again…");
            scheduleListen(400);
            return;
        }
        if (NovaListenPick.switchBusyToBuiltin(ready, alreadyBuiltin, busyRetried)) {
            preferBuiltin = true;
            listenGiveUp = false;
            cancelListenSoon();
            showListenStatus("Mic was busy, using built-in");
            NovaListenDiag.setReason("Mic was busy, using built-in");
            startListen();
            return;
        }
        listenGiveUp = true;
        cancelListenSoon();
        convoState = NovaConvo.MIC_BUSY;
        setLiveCaption("");
        String msg = message == null || message.isEmpty() ? NovaConvo.MIC_BUSY_TEXT : message;
        showListenStatus(msg);
        NovaListenDiag.setReason(msg);
        refreshAsrOffer();
        refreshMicLabel();
        // Do not keep retrying the same engine — another app may still hold the mic.
    }

    /** 0.10.59/0.10.63: no system recognizer — offer built-in listening, then Play Store. */
    void showSpeechServiceMissing() {
        if (NovaAsr.filesReady(getContext())) {
            preferBuiltin = true;
            listenGiveUp = false;
            showListenStatus(NovaSpeechAvail.NO_SERVICE_BUILTIN);
            NovaListenDiag.setReason(NovaSpeechAvail.NO_SERVICE_BUILTIN);
            startListen();
            return;
        }
        showNeedBuiltin(NovaSpeechAvail.NO_SERVICE_NEED_BUILTIN);
    }

    boolean speechServicePresent() {
        Context c = getContext();
        if (c == null) return false;
        boolean rec = false, od = false, tts = false;
        try { rec = android.speech.SpeechRecognizer.isRecognitionAvailable(c); } catch (Exception ignored) { }
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            try { od = android.speech.SpeechRecognizer.isOnDeviceRecognitionAvailable(c); } catch (Throwable ignored) { }
        }
        try { c.getPackageManager().getPackageInfo(NovaSpeechAvail.TTS_PACKAGE, 0); tts = true; } catch (Exception ignored) { }
        return NovaSpeechAvail.serviceAlreadyInstalled(rec, od, tts);
    }

    void refreshSpeechInstallBtn() {
        if (speechInstallBtn == null) return;
        boolean show = NovaSpeechAvail.showInstallButton(NovaSpeechAvail.NEED_BUILTIN, speechServicePresent());
        speechInstallBtn.setVisibility(show ? VISIBLE : GONE);
    }

    void showNeedBuiltin(String message) {
        String msg = message == null || message.isEmpty()
                ? NovaSpeechAvail.NO_SERVICE_NEED_BUILTIN : message;
        lastAnswer = msg;
        if (answerView != null) answerView.setText(msg);
        showListenStatus(msg);
        NovaListenDiag.setReason(msg);
        listenGiveUp = true;
        cancelListenSoon();
        refreshAsrOffer();
        if (!NovaAsr.filesReady(getContext())) {
            if (asrOfferBtn != null) asrOfferBtn.setVisibility(VISIBLE);
            if (asrOfferSmallBtn != null) asrOfferSmallBtn.setVisibility(VISIBLE);
        }
        refreshSpeechInstallBtn();
        refreshMicLabel();
    }

    public void onPermissionDenied() {
        listenGiveUp = true;
        cancelListenSoon();
        voiceLoop = false;
        NovaListenDiag.setPermissionDenied(true);
        NovaListenDiag.setReason(NovaSpeechAvail.PERMISSION_DENIED);
        lastAnswer = NovaSpeechAvail.PERMISSION_DENIED;
        if (answerView != null) answerView.setText(lastAnswer);
        showListenStatus(NovaSpeechAvail.PERMISSION_DENIED);
        refreshMicLabel();
    }

    public void onSpeechRms(float level) {
        lastRmsAt = android.os.SystemClock.uptimeMillis();
        if (level >= 0.15f) heardThisListen = true;
        if (micMeter != null) micMeter.setLevel(level);
        startMeterDecay();
    }

    void startMeterDecay() {
        if (meterDecay != null) return;
        meterDecay = new NovaMeterDecay(this);
        post(meterDecay);
    }

    void cancelMeterDecay() {
        if (meterDecay != null) {
            removeCallbacks(meterDecay);
            meterDecay = null;
        }
    }

    public void onMeterDecay() {
        if (!isListening()) {
            cancelMeterDecay();
            if (micMeter != null) micMeter.setLevel(0f);
            return;
        }
        long now = android.os.SystemClock.uptimeMillis();
        if (lastRmsAt > 0 && now - lastRmsAt > 180 && micMeter != null) {
            float v = micMeter.level * 0.6f;
            if (v < 0.03f) v = 0f;
            micMeter.setLevel(v);
        }
        if (meterDecay != null) postDelayed(meterDecay, 80);
    }

    void refreshAsrOffer() {
        boolean ready = NovaAsr.filesReady(getContext());
        boolean bad = NovaAsr.packMarkedBad(getContext());
        boolean show = NovaListenPick.showBuiltinButton(ready, asrDlBusy);
        if (asrOfferBtn != null) {
            asrOfferBtn.setEnabled(!asrDlBusy);
            asrOfferBtn.setText(bad ? "Download again" : NovaAsrFiles.DOWNLOAD_BTN);
            asrOfferBtn.setVisibility(show ? VISIBLE : GONE);
        }
        if (asrOfferSmallBtn != null) {
            asrOfferSmallBtn.setEnabled(!asrDlBusy);
            asrOfferSmallBtn.setVisibility(show ? VISIBLE : GONE);
        }
        if (asrOfferStatus != null && !asrDlBusy) {
            if (ready) {
                asrOfferStatus.setVisibility(GONE);
            } else if (bad) {
                asrOfferStatus.setVisibility(VISIBLE);
                asrOfferStatus.setText("Built-in listening didn't start safely. You can download again, or get the smaller pack.");
            }
        }
    }

    void startAsrDownload() { startAsrDownload(false); }

    void startAsrDownload(boolean small) {
        if (asrDlBusy) return;
        if (NovaAsr.filesReady(getContext())) {
            listenGiveUp = false;
            return;
        }
        asrDlBusy = true;
        if (asrOfferBtn != null) asrOfferBtn.setEnabled(false);
        if (asrOfferSmallBtn != null) asrOfferSmallBtn.setEnabled(false);
        String start = small
                ? "Downloading the smaller listening pack… 0%"
                : "Downloading built-in listening… 0%";
        if (asrOfferStatus != null) {
            asrOfferStatus.setVisibility(VISIBLE);
            asrOfferStatus.setText(start);
        }
        showListenStatus(start);
        new Thread(new NovaAsrPack(getContext(), new NovaViewAsrDl(this), small), "nova-asr-dl").start();
    }

    void onAsrLoading(String message) {
        String msg = message == null || message.isEmpty() ? "Loading built-in listening…" : message;
        showListenStatus(msg);
        if (asrOfferStatus != null) {
            asrOfferStatus.setVisibility(VISIBLE);
            asrOfferStatus.setText(msg);
        }
        NovaListenDiag.setReason(msg);
    }

    void onAsrDlProgress(int percent) {
        String msg = "Downloading built-in listening… " + percent + "%";
        if (asrOfferStatus != null) {
            asrOfferStatus.setVisibility(VISIBLE);
            asrOfferStatus.setText(msg);
        }
        showListenStatus(msg);
        NovaListenDiag.setReason(msg);
    }

    void onAsrDlDone(boolean ok, String error) {
        asrDlBusy = false;
        if (ok) {
            if (asrOfferBtn != null) asrOfferBtn.setVisibility(GONE);
            if (asrOfferSmallBtn != null) asrOfferSmallBtn.setVisibility(GONE);
            String ready = "Built-in listening is downloaded. Tap Mic to talk.";
            if (asrOfferStatus != null) {
                asrOfferStatus.setVisibility(VISIBLE);
                asrOfferStatus.setText(ready);
            }
            showListenStatus(ready);
            NovaListenDiag.setReason(ready);
            listenGiveUp = false;
            // Do not start listening or load the model here. A Mic tap loads it
            // in the background; auto-starting after download was crashing the app.
        } else {
            String msg = error != null ? error : "The download didn't finish. You can try again, or get the smaller pack.";
            if (asrOfferBtn != null) {
                asrOfferBtn.setVisibility(VISIBLE);
                asrOfferBtn.setEnabled(true);
            }
            if (asrOfferSmallBtn != null) {
                asrOfferSmallBtn.setVisibility(VISIBLE);
                asrOfferSmallBtn.setEnabled(true);
            }
            if (asrOfferStatus != null) {
                asrOfferStatus.setVisibility(VISIBLE);
                asrOfferStatus.setText(msg);
            }
            showListenStatus(msg);
            NovaListenDiag.setReason(msg);
        }
        refreshAsrOffer();
    }

    public void onSpeechPromptResult(String text) {
        if (text != null && !text.trim().isEmpty()) ask(text.trim(), true);
        else if (voiceLoop) postDelayed(new NovaListenSoon(this), 400);
    }

    public boolean isListening() {
        return speech != null && speech.isListening();
    }

    /** 0.10.55: dismiss the system IME in Chat (Done / Back / B / tap outside). */
    void hideSoftIme() {
        if (askField != null) ImeHide.hide(askField);
        ImeHide.hide(this);
        if (getRootView() != null) ImeHide.hide(getRootView());
    }

    boolean imeUp() {
        if (Build.VERSION.SDK_INT >= 30) {
            android.view.WindowInsets in = getRootWindowInsets();
            if (in != null && in.isVisible(android.view.WindowInsets.Type.ime())) return true;
        }
        return askField != null && askField.hasFocus();
    }

    void tellJoke() {
        // Only the joke button or a clear "tell me a joke" ask should get here.
        // Stop the mic first so a joke never speaks over the child.
        stopListen();
        int i = prefs == null ? 0 : prefs.getInt(PREF_JOKE, 0);
        String joke = NovaHelp.jokeAt(i);
        if (prefs != null) prefs.edit().putInt(PREF_JOKE, i + 1).apply();
        pinAnswer(joke);
        showListenStatus(NovaConvo.SPEAKING_TEXT);
        convoState = NovaConvo.SPEAKING;
        speakAuto(lastAnswer);
        invalidate();
    }

    void refreshVoiceOffer() {
        if (voiceOfferBtn == null || voiceOfferStatus == null) return;
        if (NovaKokoro.filesReady(getContext())) {
            voiceOfferBtn.setVisibility(GONE);
            String err = NovaKokoro.lastError();
            if (err != null && !NovaKokoro.isLoaded()) {
                voiceOfferStatus.setVisibility(VISIBLE);
                voiceOfferStatus.setText("Nova's voice is on this phone but didn't start, so she is using the phone's voice.");
            } else {
                voiceOfferStatus.setVisibility(GONE);
            }
            return;
        }
        voiceOfferBtn.setVisibility(VISIBLE);
        voiceOfferBtn.setEnabled(true);
        voiceOfferStatus.setVisibility(VISIBLE);
        voiceOfferStatus.setText("Nova is using the phone's voice for now. You can download her own voice (about 360 MB) when you're on Wi-Fi.");
    }

    void onVoiceOfferProgress(int percent) {
        if (voiceOfferStatus != null) {
            voiceOfferStatus.setVisibility(VISIBLE);
            voiceOfferStatus.setText("Downloading Nova's voice… " + percent + "%");
        }
        if (voiceOfferBtn != null) voiceOfferBtn.setEnabled(false);
    }

    void onVoiceOfferDone(boolean ok, String error) {
        if (ok) {
            if (voiceOfferStatus != null) {
                voiceOfferStatus.setVisibility(VISIBLE);
                voiceOfferStatus.setText("Nova's voice is ready. She will use it the next time she speaks.");
            }
            if (voiceOfferBtn != null) voiceOfferBtn.setVisibility(GONE);
            // Load in the background so the next line can use it; she still speaks if this fails.
            new Thread(new NovaVoiceWarmup(getContext()), "nova-voice-load").start();
        } else {
            if (voiceOfferStatus != null) {
                voiceOfferStatus.setVisibility(VISIBLE);
                voiceOfferStatus.setText(error != null ? error : "The download didn't finish. You can try again.");
            }
            if (voiceOfferBtn != null) {
                voiceOfferBtn.setVisibility(VISIBLE);
                voiceOfferBtn.setEnabled(true);
            }
        }
    }

    boolean muted() { return prefs != null && prefs.getBoolean(PREF_KIDS_MUTE, false); }

    void setMuted(boolean on) {
        if (prefs != null) prefs.edit().putBoolean(PREF_KIDS_MUTE, on).apply();
        if (on) stopSpeak();
        if (muteLabel != null) muteLabel.setText(on ? "Muted" : "Sound on");
        invalidate();
    }

    void toggleMute() { setMuted(!muted()); }

    /** Bubble single-tap: open Chat in voice mode (if Voice talk is on), or close it. A tucked bubble first untucks. */
    void onSingleTap(boolean muteBadge) {
        if (muteBadge) { toggleMute(); return; }
        if (tucked) { setTucked(false); return; }
        if (muted() || !expanded) {
            setMuted(false);
            setExpanded(true);
            if (voiceTalkOn()) enterVoiceMode();
            else greet();
        } else {
            exitVoiceMode();
            setMuted(true);
            if (!big) setExpanded(false);
        }
    }

    void enterVoiceMode() {
        voiceLoop = true;
        listenGiveUp = false;
        emptyListenCycles = 0;
        busyRetried = false;
        listenStarted = false;
        if (getContext() != null) NovaListenService.pauseForChat(getContext());
        greet();
        refreshVoiceButtons();
        // 0.10.57: if she is muted, listen straight away. Otherwise wait until
        // speech ends (maybeResumeListen). Starting the mic mid-utterance ducks
        // or stops TTS on many phones and freezes the mouth.
        if (muted()) postDelayed(new NovaListenSoon(this), 400);
    }

    void exitVoiceMode() {
        boolean was = voiceLoop;
        voiceLoop = false;
        awaitingAnswer = false;
        listenGiveUp = false;
        emptyListenCycles = 0;
        listenStarted = false;
        cancelListenSoon();
        stopListen();
        refreshVoiceButtons();
        if (was && answerView != null && lastAnswer != null) {
            // Leave Chat open so they can type. No extra toast on the overlay thread.
        }
    }

    void refreshVoiceButtons() {
        if (typeBtn != null) {
            boolean talk = voiceTalkOn();
            typeBtn.setVisibility(talk ? VISIBLE : GONE);
            typeBtn.setText(voiceLoop ? "Type instead" : "Listen");
        }
        refreshMicLabel();
    }

    /** Type instead / Listen toggle. Leaving text mode starts hands-free listen. */
    void toggleTextMode() {
        if (!voiceTalkOn()) return;
        if (voiceLoop) {
            exitVoiceMode();
            if (askField != null) askField.requestFocus();
            return;
        }
        enterListenFromText();
    }

    void enterListenFromText() {
        hideSoftIme();
        voiceLoop = true;
        refreshVoiceButtons();
        if (host != null && !host.novaMicAllowed()) {
            micAsked = true;
            host.novaRequestMic();
            return;
        }
        if (NovaListenHidden.shouldListenAfterTextOff(expanded, true, talking || speakJobInFlight, pendingSpeak != null))
            startListen();
        else
            postDelayed(new NovaListenSoon(this), 200);
    }

    /** After she finishes speaking, listen again if the conversation loop is still on. */
    public void maybeResumeListen() {
        if (!voiceLoop || !expanded || !voiceTalkOn() || listenGiveUp) return;
        if (!NovaListenGate.canRestart(true, true, listenGiveUp, talking, thinking(), speakJobInFlight, pendingSpeak != null)) return;
        long now = android.os.SystemClock.uptimeMillis();
        if (!NovaSpeakGate.canStartListen(true, true, talking, pendingSpeak != null, speakJobInFlight, now, silentUntil)) return;
        if (speech != null && speech.isListening()) return;
        if (host != null && !host.novaMicAllowed()) {
            if (!micAsked) { micAsked = true; host.novaRequestMic(); }
            return;
        }
        startListen();
    }

    void speakAuto(String s) {
        if (muted()) {
            if (voiceLoop) postDelayed(new NovaListenSoon(this), 400);
            return;
        }
        speak(s);
    }

    void speak(String s) { speak(s, true, false); }

    void speak(String s, boolean freshLine) { speak(s, freshLine, false); }

    void speakStreamed(String full) {
        if (full == null || full.isEmpty()) return;
        pinAnswer(full);
        cancelThinkTick();
        String first = NovaReplyQuick.firstSentence(full);
        pendingRest = NovaReplyQuick.afterFirst(full);
        speakAuto(first);
    }

    /** If a streamed rest remains, speak it and return true (do not start listening yet). */
    boolean continueRest() {
        if (pendingRest == null || pendingRest.isEmpty()) return false;
        String rest = pendingRest;
        pendingRest = null;
        speak(rest, false);
        return true;
    }

    void speak(String s, boolean freshLine, boolean instantOk) {
        if (s == null || s.isEmpty()) return;
        if (muted()) return;
        if (freshLine) heartFallbackRetried = false;
        cancelThinkTick();
        cancelListenSoon();
        stopListen();   // 0.10.55: do not hear herself while speaking
        // 0.10.60: cancel any other engine / leftover job first so only one voice plays.
        if (tts != null) { try { tts.stop(); } catch (Exception ignored) { } }
        pendingSpeak = null;
        talkId = null;
        NovaVoicePlay.stop();
        speakGen = NovaVoicePlay.newSession();
        speakJobInFlight = true;
        lastSpoken = s;
        silentUntil = 0;
        heartWaitExtended = false;
        beginTalk(s);
        boolean heartReady = NovaKokoro.isLoaded();
        // Greeting must start at once. If Heart is still warming, use the phone voice for this line only.
        if (instantOk && NovaSpeakGate.allowInstantPhone(NovaKokoro.filesReady(getContext()), heartReady)) {
            setVoiceTag(NovaSpeakGate.TAG_PHONE);
            speakJobInFlight = false;
            speakSystem(s, true);
            return;
        }
        startReplyTimeout(speakGen);
        if (NovaKokoro.usesOnDevice(getContext(), prefs)) {
            setVoiceTag(NovaSpeakGate.TAG_HEART);
            new Thread(new NovaVoiceSpeakJob(this, s, NovaKokoro.speakerId(prefs), speakGen), "nova-voice").start();
            return;
        }
        setVoiceTag(NovaSpeakGate.TAG_PHONE);
        speakJobInFlight = false;
        speakSystem(s);
    }

    void startReplyTimeout(int gen) {
        cancelReplyTimeout();
        replyWait = new NovaReplyTimeout(this, gen);
        boolean heart = NovaSpeakGate.keepHeart(NovaKokoro.filesReady(getContext()));
        postDelayed(replyWait, NovaSpeakGate.speakWaitMs(heart));
    }

    void cancelReplyTimeout() {
        if (replyWait != null) {
            removeCallbacks(replyWait);
            replyWait = null;
        }
    }

    /** Heart (or a wiki job) took too long: drop the locks and speak a canned fallback. */
    public void onReplyTimeout(int gen) {
        replyWait = null;
        if (NovaSpeakGate.stale(gen, speakGen) && gen != 0) return;
        if (deviceVoice && talking) return;   // Heart audio already started — never cut over to the phone.
        boolean keep = NovaSpeakGate.keepHeart(NovaKokoro.filesReady(getContext()));
        if (NovaSpeakGate.extendHeartWait(keep, deviceVoice, speakJobInFlight, heartWaitExtended)) {
            heartWaitExtended = true;
            startReplyTimeout(gen);
            return;
        }
        String fallback = NovaConvo.FALLBACK_TEXT;
        lastAnswer = fallback;
        if (answerView != null) answerView.setText(fallback);
        showListenStatus(fallback);
        if (keep) {
            // Keep Heart for the fallback line. Do not start the phone's voice.
            speakJobInFlight = false;
            pendingSpeak = null;
            speak(fallback);
            return;
        }
        speakJobInFlight = false;
        pendingSpeak = null;
        speakGen = NovaSpeakGate.nextGen(speakGen);
        NovaVoicePlay.stop();
        setVoiceTag(NovaSpeakGate.TAG_PHONE);
        speakSystem(fallback);
    }

    void releaseSpeakLocks() {
        cancelReplyTimeout();
        speakJobInFlight = false;
        pendingSpeak = null;
        talking = false;
        deviceVoice = false;
        talkUntil = 0;
        st.talking = false;
        setVoiceTag(null);
    }

    /** Mark her as talking so the mouth animates from a timer even if audio is late. */
    void beginTalk(String s) {
        talking = true;
        deviceVoice = false;
        convoState = NovaConvo.SPEAKING;
        showListenStatus(NovaConvo.SPEAKING_TEXT);
        talkUntil = android.os.SystemClock.uptimeMillis() + NovaTtsVoice.estimateMs(s);
        st.talking = true;
        startAnim();
        invalidate();
    }

    /** System TTS path. Never used once Heart's files are on the phone. */
    void speakSystem(String s) { speakSystem(s, false); }

    void speakSystem(String s, boolean forcePhone) {
        if (s == null || s.isEmpty()) return;
        if (muted()) { speakJobInFlight = false; return; }
        if (!forcePhone && NovaSpeakGate.keepHeart(NovaKokoro.filesReady(getContext()))) {
            if (!heartFallbackRetried) {
                heartFallbackRetried = true;
                speak(s, false);
                return;
            }
            onHeartFailed(NovaKokoro.lastError());
            releaseSpeakLocks();
            if (voiceLoop) postDelayed(new NovaListenSoon(this), 400);
            return;
        }
        deviceVoice = false;
        speakJobInFlight = false;
        setVoiceTag(NovaSpeakGate.TAG_PHONE);
        pendingSpeak = s;
        beginTalk(s);
        ensureTts();
        if (ttsReady) flushSpeak();
        else android.util.Log.i("NovaVoice", "waiting for system TTS to start");
    }

    /** Heart could not start; show the real error and use the phone's voice. */
    void onHeartFailed(String error) {
        String reason = error;
        if (reason == null || reason.trim().isEmpty()) reason = NovaKokoro.lastError();
        String msg = NovaVoiceStatus.failed(reason);
        setVoiceTag(NovaSpeakGate.TAG_PHONE);
        if (voiceOfferStatus != null) {
            voiceOfferStatus.setVisibility(VISIBLE);
            voiceOfferStatus.setText(msg);
        }
        if (listenStatus != null) {
            listenStatus.setText(msg);
            listenStatus.setVisibility(VISIBLE);
        }
        android.util.Log.w("NovaVoice", msg);
        invalidate();
    }

    void setVoiceTag(String tag) {
        voiceTag = tag;
        if (engineLabel != null) {
            if (tag == null || tag.isEmpty()) engineLabel.setVisibility(INVISIBLE);
            else {
                engineLabel.setText(tag);
                engineLabel.setVisibility(VISIBLE);
            }
        }
        invalidate();
    }

    /** Drop recognizer results that are her own echo or that arrived while she is speaking. */
    public void onHeard(String text) {
        text = NovaAsrText.tidy(text);
        long now = android.os.SystemClock.uptimeMillis();
        if (NovaSpeakGate.shouldDropHeard(text, lastSpoken, talking, speakJobInFlight, now, silentUntil)) {
            setLiveCaption("");
            refreshMicLabel();
            if (voiceLoop) postDelayed(new NovaListenSoon(this), 400);
            return;
        }
        lastHeard = text;
        emptyListenCycles = 0;
        listenGiveUp = false;
        convoState = NovaConvo.HEARD;
        showListenStatus(NovaConvo.youSaid(text));
        setLiveCaption(NovaConvo.youSaid(text));
        ask(text, true);
    }

    public void onDeviceVoiceStart() {
        stopListen();
        cancelReplyTimeout();
        talking = true;
        deviceVoice = true;
        st.talking = true;
        convoState = NovaConvo.SPEAKING;
        showListenStatus(NovaConvo.SPEAKING_TEXT);
        long extra = android.os.SystemClock.uptimeMillis() + 8000;
        if (extra > talkUntil) talkUntil = extra;
        invalidate();
    }

    public void onDeviceVoiceAmp(float amp) {
        st.talkAmp = amp;
        st.talking = true;
        talking = true;
        deviceVoice = true;
        invalidate();
    }

    public void onDeviceVoiceEnd() {
        releaseSpeakLocks();
        if (continueRest()) return;
        silentUntil = android.os.SystemClock.uptimeMillis() + NovaSpeakGate.HANGOVER_MS;
        st.mouth = 0;
        invalidate();
        if (voiceLoop) postDelayed(new NovaListenSoon(this), NovaSpeakGate.HANGOVER_MS);
    }

    void ensureTts() {
        if (tts != null) return;
        tts = new TextToSpeech(getContext(), new NovaTtsInit(this));
    }

    public void onTtsInit(int status) {
        if (status == TextToSpeech.SUCCESS && tts != null) {
            pickVoice();
            try {
                tts.setPitch(NovaTtsVoice.PITCH);
                tts.setSpeechRate(NovaKokoro.systemRateFrom(prefs));
                tts.setOnUtteranceProgressListener(new NovaTtsListen(this));
            } catch (Exception ignored) { }
            ttsReady = true;
            ttsInitTries = 0;
            flushSpeak();
        } else {
            ttsReady = false;
            android.util.Log.w("NovaVoice", "system TTS init failed, status=" + status + " try=" + ttsInitTries);
            if (tts != null) {
                try { tts.shutdown(); } catch (Exception ignored) { }
                tts = null;
            }
            ttsInitTries++;
            if (pendingSpeak != null && ttsInitTries < 2) {
                tts = new TextToSpeech(getContext(), new NovaTtsInit(this));
            } else {
                pendingSpeak = null;
                talking = false;
                talkUntil = 0;
                st.talking = false;
                if (voiceLoop) postDelayed(new NovaListenSoon(this), 400);
            }
        }
    }

    void pickVoice() {
        if (tts == null) return;
        try {
            java.util.Set<android.speech.tts.Voice> set = tts.getVoices();
            android.speech.tts.Voice best = null;
            int bestScore = -1;
            if (set != null) {
                for (android.speech.tts.Voice v : set) {
                    if (v == null) continue;
                    java.util.Locale loc = v.getLocale();
                    String lang = loc == null ? null : loc.getLanguage();
                    String country = loc == null ? null : loc.getCountry();
                    int sc = NovaTtsVoice.score(lang, country, v.getName(), v.getQuality(), v.isNetworkConnectionRequired());
                    if (sc > bestScore) { bestScore = sc; best = v; }
                }
            }
            if (best != null && bestScore >= 0) {
                tts.setVoice(best);
                return;
            }
        } catch (Exception ignored) { }
        try {
            if (tts.isLanguageAvailable(Locale.UK) >= TextToSpeech.LANG_AVAILABLE) tts.setLanguage(Locale.UK);
            else if (tts.isLanguageAvailable(Locale.US) >= TextToSpeech.LANG_AVAILABLE) tts.setLanguage(Locale.US);
            else tts.setLanguage(Locale.UK);
        } catch (Exception ignored) { }
    }

    void flushSpeak() {
        if (tts == null || pendingSpeak == null) return;
        try { tts.setSpeechRate(NovaKokoro.systemRateFrom(prefs)); } catch (Exception ignored) { }
        String s = pendingSpeak;
        pendingSpeak = null;
        stopListen();
        speakSeq++;
        String[] parts = NovaTtsVoice.sentences(s);
        if (parts.length == 0) return;
        talking = true;
        talkUntil = android.os.SystemClock.uptimeMillis() + NovaTtsVoice.estimateMs(s);
        talkId = null;
        for (int i = 0; i < parts.length; i++) {
            String id = "nova-" + speakSeq + "-" + i;
            int mode = (i == 0) ? TextToSpeech.QUEUE_FLUSH : TextToSpeech.QUEUE_ADD;
            if (i > 0) {
                try { tts.playSilentUtterance(NovaTtsVoice.PAUSE_MS, TextToSpeech.QUEUE_ADD, "nova-" + speakSeq + "-s" + i); }
                catch (Exception ignored) { }
            }
            int ok = tts.speak(parts[i], mode, null, id);
            if (ok == TextToSpeech.ERROR) {
                android.util.Log.w("NovaVoice", "tts.speak failed, retrying with an English voice");
                try {
                    if (tts.isLanguageAvailable(Locale.US) >= TextToSpeech.LANG_AVAILABLE) tts.setLanguage(Locale.US);
                    else tts.setLanguage(Locale.UK);
                    ok = tts.speak(parts[i], mode, null, id);
                } catch (Exception ignored) { }
            }
            if (ok == TextToSpeech.ERROR) {
                android.util.Log.w("NovaVoice", "system TTS could not speak this line");
                releaseSpeakLocks();
                silentUntil = android.os.SystemClock.uptimeMillis() + NovaSpeakGate.HANGOVER_MS;
                if (voiceLoop) postDelayed(new NovaListenSoon(this), 400);
            }
            talkId = id;   // last sentence; onDone of this one ends the mouth + may resume listen
        }
        talking = true;
        startAnim();
    }

    public void onTtsStart(String id) {
        if (id == null || talkId == null) return;
        if (!id.startsWith("nova-" + speakSeq + "-")) return;
        stopListen();
        cancelReplyTimeout();
        talking = true;
        convoState = NovaConvo.SPEAKING;
        // Callbacks can be late: keep the duration fallback as a safety net.
        long extra = android.os.SystemClock.uptimeMillis() + 8000;
        if (extra > talkUntil) talkUntil = extra;
        invalidate();
    }

    public void onTtsEnd(String id) {
        if (id == null || talkId == null) return;
        if (!id.equals(talkId)) return;   // ignore pauses and earlier sentences
        talkId = null;
        releaseSpeakLocks();
        if (continueRest()) return;
        silentUntil = android.os.SystemClock.uptimeMillis() + NovaSpeakGate.HANGOVER_MS;
        invalidate();
        if (voiceLoop) postDelayed(new NovaListenSoon(this), NovaSpeakGate.HANGOVER_MS);
    }

    void stopSpeak() {
        cancelReplyTimeout();
        cancelThinkTick();
        pendingRest = null;
        pendingSpeak = null;
        talking = false;
        deviceVoice = false;
        talkUntil = 0;
        speakJobInFlight = false;
        speakGen = NovaSpeakGate.nextGen(speakGen);
        silentUntil = android.os.SystemClock.uptimeMillis() + NovaSpeakGate.HANGOVER_MS;
        st.talking = false;
        st.mouth = 0;
        NovaVoicePlay.stop();
        setVoiceTag(null);
        if (tts != null) {
            try { tts.stop(); } catch (Exception ignored) { }
        }
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (!faceLoaded) faceLoaded = face.load(getContext().getAssets());
        if (kids) ensureTts();
        if (NovaKokoro.filesReady(getContext()) && !NovaKokoro.isLoaded())
            new Thread(new NovaVoiceWarmup(getContext()), "nova-voice-load").start();
        startAnim();
    }

    @Override protected void onDetachedFromWindow() {
        snapping = false;
        dragging = false;
        pinching = false;
        downOnBubble = false;
        downOnTab = false;
        ignoreUntilUp = false;
        tapArmedAt = 0;
        stopListen();
        if (speech != null) { speech.destroy(); speech = null; }
        stopAnim();
        shutdownTts();
        super.onDetachedFromWindow();
    }

    public void release() {
        stopListen();
        if (speech != null) { speech.destroy(); speech = null; }
        stopAnim();
        shutdownTts();
        face.recycle();
        faceLoaded = false;
    }

    void shutdownTts() {
        stopSpeak();
        ttsReady = false;
        if (tts != null) {
            try { tts.shutdown(); } catch (Exception ignored) { }
            tts = null;
        }
    }

    void startAnim() {
        if (running || getVisibility() != VISIBLE || hidden) return;
        running = true;
        lastFrameNs = 0;
        choreo.postFrameCallback(this);
    }

    void stopAnim() {
        running = false;
        choreo.removeFrameCallback(this);
    }

    @Override public void doFrame(long frameTimeNs) {
        if (!running || getVisibility() != VISIBLE) { running = false; return; }
        long now = android.os.SystemClock.uptimeMillis();
        long dt = 16;
        if (lastFrameNs != 0) {
            dt = Math.max(8, Math.min(48, (frameTimeNs - lastFrameNs) / 1_000_000L));
        }
        lastFrameNs = frameTimeNs;
        if (talkUntil > 0 && now >= talkUntil) {
            boolean still = speakJobInFlight;
            if (!still) {
                try { still = tts != null && tts.isSpeaking(); } catch (Exception ignored) { }
            }
            if (still) talkUntil = now + 800;
            else if (talkId != null) onTtsEnd(talkId);
            else {
                // TTS/on-device end callback never arrived: drop the gate so listen can restart.
                talking = false;
                talkUntil = 0;
                st.talking = false;
                silentUntil = now + NovaSpeakGate.HANGOVER_MS;
                if (voiceLoop) postDelayed(new NovaListenSoon(this), NovaSpeakGate.HANGOVER_MS);
            }
        }
        // Cap at ~30 fps: blink and breath don't need 60, and we stay off the scroll thread.
        if (downOnBubble && !dragging && !pinching && !hidden && !ignoreUntilUp && now - downAt >= longPressMs) startDrag();
        if (snapping) tickSnap(now);
        NovaAnim.tick(st, now, dt, rng.nextInt());
        boolean lips = talking && !(kids && muted());
        // 0.10.57: always drive the mouth from a talking timer so it moves for
        // system TTS as well as the on-device voice. Amplitude, if present, is
        // already in st.mouth and tickTalk will ease toward a flapping target.
        NovaAnim.tickTalk(st, now, lips);
        invalidate();
        choreo.postFrameCallbackDelayed(this, 33);
    }

    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        layoutParts(w, h);
    }

    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        layoutParts(r - l, b - t);
        if (!hidden && panel != null && panel.getVisibility() == VISIBLE) layoutPanel();
    }

    RectF panelBox() {
        int w = getWidth(), h = getHeight();
        float gap = dp(8);
        float top = margin + insetT();
        float bottom = h - margin - insetB();
        if (big) {
            float face = bigFaceSize(w, h);
            boolean left = bubble.centerX() < w * 0.5f;
            if (w >= h) {
                if (left) return new RectF(margin + insetL() + face + gap, top, w - margin - insetR(), bottom);
                return new RectF(margin + insetL(), top, w - margin - insetR() - face - gap, bottom);
            }
            return new RectF(margin + insetL(), top + face + gap, w - margin - insetR(), bottom);
        }
        float pw = Math.min(w - bubbleSize - margin * 2 - gap, Math.max(dp(280), w * 0.62f));
        if (pw < dp(240)) pw = Math.max(dp(220), w - margin * 2);
        boolean left = bubble.centerX() < w * 0.5f;
        float leftX, rightX;
        if (left) {
            leftX = bubble.right + gap;
            rightX = Math.min(w - margin - insetR(), leftX + pw);
            if (rightX - leftX < dp(200)) { leftX = margin + insetL(); rightX = Math.min(w - margin - insetR(), leftX + pw); }
        } else {
            rightX = bubble.left - gap;
            leftX = Math.max(margin + insetL(), rightX - pw);
            if (rightX - leftX < dp(200)) { rightX = w - margin - insetR(); leftX = Math.max(margin + insetL(), rightX - pw); }
        }
        return new RectF(leftX, top, rightX, bottom);
    }

    void layoutPanel() {
        if (panel == null || panel.getVisibility() != VISIBLE) return;
        RectF b = panelBox();
        int pw = Math.max(1, Math.round(b.width()));
        int ph = Math.max(1, Math.round(b.height()));
        int ws = MeasureSpec.makeMeasureSpec(pw, MeasureSpec.EXACTLY);
        int hs = MeasureSpec.makeMeasureSpec(ph, MeasureSpec.EXACTLY);
        panel.measure(ws, hs);
        panel.layout(Math.round(b.left), Math.round(b.top), Math.round(b.right), Math.round(b.bottom));
    }

    float bigFaceSize(int w, int h) {
        float availW = Math.max(1, w - insetL() - insetR() - margin * 2);
        float availH = Math.max(1, h - insetT() - insetB() - margin * 2);
        if (w >= h) return Math.min(availH, availW * 0.40f);
        return Math.min(availW, availH * 0.36f);
    }

    void layoutParts(int w, int h) {
        if (w <= 0 || h <= 0) return;
        layoutTab(w, h);
        if (hidden) {
            bubble.setEmpty();
            muteHit.setEmpty();
            return;
        }
        if (big && !dragging && !snapping) {
            layoutBig(w, h);
        } else if (dragging || snapping) {
            // bubble already follows the finger or the snap
        } else {
            boolean left = prefs != null && prefs.getBoolean(PREF_LEFT, false);
            float yFrac = prefs != null && prefs.contains(PREF_Y) ? prefs.getFloat(PREF_Y, 1f) : 1f;
            float size = tucked ? tuckSize : bubbleSize;
            float m = tucked ? tuckMargin : margin;
            if (pinching) size = livePinchSize();
            float x = NovaPlace.xForSide(left, size, w, insetL(), insetR(), m);
            float y = NovaPlace.yFromFrac(tucked ? (yFrac <= 0.5f ? 0f : 1f) : yFrac, size, h, insetT(), insetB(), m);
            bubble.set(x, y, x + size, y + size);
            placed = true;
        }
        float s = Math.max(dp(28), bubble.width() * 0.32f);
        muteHit.set(bubble.right - s, bubble.bottom - s, bubble.right + dp(4), bubble.bottom + dp(4));
    }

    void layoutBig(int w, int h) {
        float size = pinching ? livePinchSize() : bigFaceSize(w, h);
        boolean left = prefs != null && prefs.getBoolean(PREF_LEFT, false);
        float availH = h - insetT() - insetB() - margin * 2;
        if (w >= h) {
            float x = left ? margin + insetL() : w - insetR() - margin - size;
            float y = margin + insetT() + Math.max(0, (availH - size) / 2f);
            bubble.set(x, y, x + size, y + size);
        } else {
            float x = (w - size) / 2f;
            float y = margin + insetT();
            bubble.set(x, y, x + size, y + size);
        }
    }

    void layoutTab(int w, int h) {
        float tw = dp(22), th = dp(56);
        boolean left = prefs != null && prefs.getBoolean(PREF_LEFT, false);
        float yFrac = prefs != null && prefs.contains(PREF_Y) ? prefs.getFloat(PREF_Y, 1f) : 1f;
        float y = NovaPlace.yFromFrac(yFrac, th, h, insetT(), insetB(), margin);
        if (left) tabHit.set(insetL(), y, insetL() + tw, y + th);
        else tabHit.set(w - insetR() - tw, y, w - insetR(), y + th);
    }

    float liveSize() { return tucked ? tuckSize : bubbleSize; }
    float liveMargin() { return tucked ? tuckMargin : margin; }

    float livePinchSize() {
        float small = liveSize();
        float large = bigFaceSize(Math.max(1, getWidth()), Math.max(1, getHeight()));
        float from = big ? large : small;
        float to = from * pinchScale;
        return NovaPlace.clamp(to, small * 0.7f, large * 1.05f);
    }

    int insetL() { return inset(true, false, false, false); }
    int insetT() { return inset(false, true, false, false); }
    int insetR() { return inset(false, false, true, false); }
    int insetB() { return inset(false, false, false, true); }

    int inset(boolean l, boolean t, boolean r, boolean b) {
        if (Build.VERSION.SDK_INT < 23) return 0;
        WindowInsets in = getRootWindowInsets();
        if (in == null) return 0;
        if (l) return in.getSystemWindowInsetLeft();
        if (t) return in.getSystemWindowInsetTop();
        if (r) return in.getSystemWindowInsetRight();
        return in.getSystemWindowInsetBottom();
    }

    @Override protected void dispatchDraw(Canvas c) {
        super.dispatchDraw(c);
        if (getVisibility() != VISIBLE) return;
        if (hidden) { drawTab(c); return; }
        if (!bubble.isEmpty()) face.draw(c, bubble, st, 0, false);
        if (isListening()) drawListening(c);
        drawMuteBadge(c);
        drawVoiceTag(c);
    }

    void drawTab(Canvas c) {
        if (tabHit.isEmpty()) return;
        float r = Math.min(tabHit.width(), tabHit.height()) * 0.5f;
        mutePaint.setStyle(Paint.Style.FILL);
        mutePaint.setColor(0xCC141A22);
        c.drawRoundRect(tabHit, r, r, mutePaint);
        mutePaint.setStyle(Paint.Style.STROKE);
        mutePaint.setStrokeWidth(Math.max(2, getResources().getDisplayMetrics().density * 1.5f));
        mutePaint.setColor(0xFFE8C872);
        c.drawRoundRect(tabHit, r, r, mutePaint);
        // A peek of her face in the tab, if loaded.
        if (!tabHit.isEmpty()) {
            float inset = dp(3);
            RectF peek = new RectF(tabHit.left + inset, tabHit.top + inset, tabHit.right - inset, tabHit.bottom - inset);
            face.draw(c, peek, st, 0, false);
        }
    }

    void drawVoiceTag(Canvas c) {
        if (hidden || bubble.isEmpty()) return;
        if (voiceTag == null || voiceTag.isEmpty()) return;
        if (!talking && !speakJobInFlight) return;
        float den = getResources().getDisplayMetrics().density;
        mutePaint.setStyle(Paint.Style.FILL);
        mutePaint.setAntiAlias(true);
        mutePaint.setTextSize(11 * den);
        mutePaint.setFakeBoldText(true);
        mutePaint.setTextAlign(Paint.Align.CENTER);
        float tw = mutePaint.measureText(voiceTag) + 14 * den;
        float th = 18 * den;
        float cx = bubble.centerX();
        float top = bubble.bottom + 4 * den;
        if (top + th > getHeight() - 4 * den) top = bubble.top - th - 4 * den;
        android.graphics.RectF r = new android.graphics.RectF(cx - tw / 2, top, cx + tw / 2, top + th);
        mutePaint.setColor(0xF2161A22);
        c.drawRoundRect(r, th / 2, th / 2, mutePaint);
        mutePaint.setStyle(Paint.Style.STROKE);
        mutePaint.setStrokeWidth(Math.max(1.5f, den));
        mutePaint.setColor(0xFFE8C872);
        c.drawRoundRect(r, th / 2, th / 2, mutePaint);
        mutePaint.setStyle(Paint.Style.FILL);
        mutePaint.setColor(0xFFE8C872);
        c.drawText(voiceTag, cx, r.centerY() + 4 * den, mutePaint);
        mutePaint.setFakeBoldText(false);
        mutePaint.setTextAlign(Paint.Align.LEFT);
    }

    void drawListening(Canvas c) {
        if (bubble.isEmpty()) return;
        float cx = bubble.centerX(), cy = bubble.centerY();
        float r = Math.min(bubble.width(), bubble.height()) * 0.52f;
        long now = android.os.SystemClock.uptimeMillis();
        float pulse = 0.65f + 0.35f * (float) Math.sin(now / 280.0);
        mutePaint.setStyle(Paint.Style.STROKE);
        mutePaint.setStrokeWidth(Math.max(3, getResources().getDisplayMetrics().density * 2.5f));
        mutePaint.setColor(((int) (220 * pulse) << 24) | (0x00E8C872));
        c.drawCircle(cx, cy, r, mutePaint);
        mutePaint.setStyle(Paint.Style.FILL);
        mutePaint.setColor(0xF2E8C872);
        float br = Math.max(5, getResources().getDisplayMetrics().density * 5f);
        c.drawCircle(bubble.centerX(), bubble.bottom - br * 0.2f, br * pulse, mutePaint);
    }

    void drawMuteBadge(Canvas c) {
        float cx = muteHit.centerX(), cy = muteHit.centerY(), r = muteHit.width() * 0.38f;
        mutePaint.setStyle(Paint.Style.FILL);
        mutePaint.setColor(0xEE1C1A18);
        c.drawCircle(cx, cy, r, mutePaint);
        mutePaint.setStyle(Paint.Style.STROKE);
        mutePaint.setStrokeWidth(Math.max(2, r * 0.12f));
        mutePaint.setColor(0xFFE8C872);
        c.drawCircle(cx, cy, r, mutePaint);
        mutePaint.setStyle(Paint.Style.FILL);
        mutePaint.setColor(0xFFF4E4C1);
        // A little speaker; a slash when muted.
        float s = r * 0.45f;
        c.drawRect(cx - s * 0.7f, cy - s * 0.35f, cx - s * 0.15f, cy + s * 0.35f, mutePaint);
        android.graphics.Path p = new android.graphics.Path();
        p.moveTo(cx - s * 0.15f, cy - s * 0.35f);
        p.lineTo(cx + s * 0.55f, cy - s * 0.85f);
        p.lineTo(cx + s * 0.55f, cy + s * 0.85f);
        p.lineTo(cx - s * 0.15f, cy + s * 0.35f);
        p.close();
        c.drawPath(p, mutePaint);
        if (muted()) {
            mutePaint.setStyle(Paint.Style.STROKE);
            mutePaint.setStrokeWidth(Math.max(2, r * 0.16f));
            mutePaint.setColor(0xFFE07A6A);
            c.drawLine(cx - s * 0.9f, cy + s * 0.9f, cx + s * 0.9f, cy - s * 0.9f, mutePaint);
        }
    }

    @Override public boolean dispatchTouchEvent(MotionEvent ev) {
        if (getVisibility() != VISIBLE) return false;
        float x = ev.getX(), y = ev.getY();
        if (ev.getActionMasked() == MotionEvent.ACTION_DOWN && askField != null && askField.hasFocus()
                && !ImeHide.eventInside(askField, ev)) {
            hideSoftIme();
        }
        // Panel controls (Ask, chips, field, mic) must get the tap. Don't let a leftover
        // pinch ignoreUntilUp eat the first tap on Chat.
        if (hitsPanel(x, y) && !dragging && !pinching && !downOnBubble && !downOnTab) {
            if (ignoreUntilUp && ev.getActionMasked() == MotionEvent.ACTION_DOWN) ignoreUntilUp = false;
            return super.dispatchTouchEvent(ev);
        }
        if (dragging || pinching || downOnBubble || downOnTab || ignoreUntilUp) return super.dispatchTouchEvent(ev);
        if (!hitsInteractive(x, y)) return false;
        return super.dispatchTouchEvent(ev);
    }

    @Override public boolean onInterceptTouchEvent(MotionEvent ev) {
        // Take a two-finger scale that starts on the bubble or the open Chat panel.
        if (hidden) return false;
        if (ev.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN && ev.getPointerCount() >= 2) {
            if (downOnBubble || hitsInteractive(ev.getX(0), ev.getY(0)) || hitsInteractive(ev.getX(1), ev.getY(1))) {
                return true;
            }
        }
        return false;
    }

    @Override public boolean onTouchEvent(MotionEvent ev) {
        int action = ev.getActionMasked();
        if (ignoreUntilUp) {
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) ignoreUntilUp = false;
            return true;
        }

        if (hidden) return onTabTouch(ev);

        if (action == MotionEvent.ACTION_POINTER_DOWN && ev.getPointerCount() >= 2) {
            beginPinch(ev);
            return true;
        }

        if (pinching) {
            if (action == MotionEvent.ACTION_MOVE && ev.getPointerCount() >= 2) {
                pinchScale = NovaPlace.pinchScale(pinchStartSpan, eventSpan(ev));
                layoutParts(getWidth(), getHeight());
                requestLayout();
                invalidate();
                return true;
            }
            if (action == MotionEvent.ACTION_POINTER_UP || action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                finishPinch(action == MotionEvent.ACTION_CANCEL);
                return true;
            }
            return true;
        }

        if (action == MotionEvent.ACTION_DOWN) {
            downOnBubble = hitsBubble(ev.getX(), ev.getY()) || hitsMute(ev.getX(), ev.getY());
            downOnMute = hitsMute(ev.getX(), ev.getY());
            if (!downOnBubble) return false;
            downX = ev.getX();
            downY = ev.getY();
            downAt = ev.getEventTime();
            startLeft = bubble.left;
            startTop = bubble.top;
            dragging = false;
            snapping = false;
            pinching = false;
            // A second down inside the double-tap window is the start of hide, not a new single-tap.
            return true;
        }
        if (!downOnBubble && !dragging) return false;
        if (action == MotionEvent.ACTION_MOVE) {
            float dx = ev.getX() - downX, dy = ev.getY() - downY;
            // 0.10.54: movement past slop on the bubble starts a drag (0.10.53 cancelled it,
            // so a real finger often never moved her). Tap / double-tap / pinch are unchanged.
            if (NovaPlace.dragStartsOnMove(dragging, big, pinching, NovaPlace.movedPast(dx, dy, slop))) {
                startDrag();
            }
            if (dragging) {
                moveBubble(startLeft + dx, startTop + dy);
                return true;
            }
            return true;
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            boolean wasDrag = dragging;
            boolean mute = downOnMute;
            long upAt = ev.getEventTime();
            downOnBubble = false;
            downOnMute = false;
            dragging = false;
            if (action == MotionEvent.ACTION_CANCEL) {
                cancelArmedTap();
                return true;
            }
            if (wasDrag) {
                cancelArmedTap();
                snapGently();
                return true;
            }
            if (NovaPlace.movedPast(ev.getX() - downX, ev.getY() - downY, slop)) {
                cancelArmedTap();
                return true;
            }
            // 0.10.58: first tap opens her immediately; a second tap in the
            // window hides her (no wait before the single-tap action).
            if (NovaPlace.isImmediateDoubleTap(tapArmedAt, upAt, NovaPlace.DOUBLE_MS)) {
                tapArmedAt = 0;
                hideCompletely();
                return true;
            }
            tapArmedAt = upAt;
            tapArmedMute = mute;
            onSingleTap(mute);
            return true;
        }
        return true;
    }

    boolean onTabTouch(MotionEvent ev) {
        int action = ev.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            downOnTab = hitsTab(ev.getX(), ev.getY());
            return downOnTab;
        }
        if (!downOnTab) return false;
        if (action == MotionEvent.ACTION_UP) {
            downOnTab = false;
            if (hitsTab(ev.getX(), ev.getY())) reveal();
            return true;
        }
        if (action == MotionEvent.ACTION_CANCEL) { downOnTab = false; return true; }
        return true;
    }

    void beginPinch(MotionEvent ev) {
        pinching = true;
        dragging = false;
        snapping = false;
        ignoreUntilUp = false;
        cancelArmedTap();
        downOnMute = false;
        pinchStartSpan = eventSpan(ev);
        if (pinchStartSpan < 1f) pinchStartSpan = 1f;
        pinchScale = 1f;
    }

    void finishPinch(boolean cancelled) {
        float scale = pinchScale;
        pinching = false;
        pinchScale = 1f;
        downOnBubble = false;
        downOnMute = false;
        ignoreUntilUp = true;
        if (cancelled) {
            requestLayout();
            invalidate();
            return;
        }
        setBig(NovaPlace.goBig(big, scale));
    }

    float eventSpan(MotionEvent ev) {
        if (ev.getPointerCount() < 2) return pinchStartSpan;
        return NovaPlace.span(ev.getX(0), ev.getY(0), ev.getX(1), ev.getY(1));
    }

    void startDrag() {
        if (dragging || pinching || big || hidden) return;
        dragging = true;
        snapping = false;
        cancelArmedTap();
    }

    void cancelArmedTap() { tapArmedAt = 0; }

    void cancelPress(boolean snapIfDrag) {
        downOnBubble = false;
        downOnMute = false;
        cancelArmedTap();
        if (dragging) {
            dragging = false;
            if (snapIfDrag) snapGently();
            else placeClamped();
        }
    }

    void setBig(boolean on) {
        if (big == on) {
            requestLayout();
            invalidate();
            return;
        }
        if (prefs != null) prefs.edit().putBoolean(PREF_BIG, on).apply();
        boolean was = big;
        big = on;
        if (on) {
            setExpanded(true);
            if (!muted() && !was) greet();
        } else {
            setExpanded(false);
        }
        requestLayout();
        invalidate();
    }

    void setTucked(boolean on) {
        if (prefs != null) prefs.edit().putBoolean(PREF_TUCKED, on).putBoolean(PREF_HIDDEN, false).putBoolean(PREF_BIG, false).apply();
        tucked = on;
        hidden = false;
        big = false;
        if (on) {
            exitVoiceMode();
            if (expanded) setExpanded(false);
        }
        startAnim();
        requestLayout();
        invalidate();
    }

    void hideAway() { hideCompletely(); }

    /**
     * 0.10.58: hide her completely (Show Nova off) until the Nova tab turns her back on.
     * Persists across restarts. May start optional hidden listening from this user action.
     */
    void hideCompletely() {
        cancelArmedTap();
        if (prefs != null) prefs.edit()
                .putBoolean(PREF_BUBBLE, false)
                .putBoolean(PREF_HIDDEN, false)
                .putBoolean(PREF_BIG, false)
                .putBoolean(PREF_TUCKED, false)
                .apply();
        hidden = false;
        big = false;
        tucked = false;
        exitVoiceMode();
        if (expanded) setExpanded(false);
        stopSpeak();
        stopAnim();
        setVisibility(GONE);
        try { android.widget.Toast.makeText(getContext(), NovaListenHidden.TOAST_HIDDEN, android.widget.Toast.LENGTH_SHORT).show(); }
        catch (Exception ignored) { }
        if (host != null) host.onNovaHiddenByUser();
        requestLayout();
        invalidate();
    }

    void reveal() {
        if (prefs != null) prefs.edit().putBoolean(PREF_HIDDEN, false).apply();
        hidden = false;
        startAnim();
        requestLayout();
        invalidate();
    }

    void moveBubble(float left, float top) {
        int w = getWidth(), h = getHeight();
        float size = liveSize(), m = liveMargin();
        float minX = insetL() + m, minY = insetT() + m;
        float maxX = w - insetR() - m - size, maxY = h - insetB() - m - size;
        left = NovaPlace.clamp(left, minX, Math.max(minX, maxX));
        top = NovaPlace.clamp(top, minY, Math.max(minY, maxY));
        bubble.set(left, top, left + size, top + size);
        layoutParts(w, h);
        requestLayout();
        invalidate();
    }

    void snapGently() {
        int w = getWidth(), h = getHeight();
        float size = liveSize(), m = liveMargin();
        float[] to = tucked
                ? NovaPlace.tuckSnap(bubble.left, bubble.top, size, w, h, insetL(), insetT(), insetR(), insetB(), m)
                : NovaPlace.snap(bubble.left, bubble.top, size, w, h, insetL(), insetT(), insetR(), insetB(), m);
        snapX0 = bubble.left;
        snapY0 = bubble.top;
        snapX1 = to[0];
        snapY1 = to[1];
        snapStart = android.os.SystemClock.uptimeMillis();
        snapping = true;
        savePlace(snapX1, snapY1);
        startAnim();
        tickSnap(snapStart);
    }

    void tickSnap(long now) {
        if (!snapping) return;
        float u = (now - snapStart) / 200f;
        if (u >= 1f) { u = 1f; snapping = false; }
        // DecelerateInterpolator-style: 1 - (1-u)^2
        u = 1f - (1f - u) * (1f - u);
        moveBubble(snapX0 + (snapX1 - snapX0) * u, snapY0 + (snapY1 - snapY0) * u);
    }

    void placeClamped() {
        int w = getWidth(), h = getHeight();
        float size = liveSize(), m = liveMargin();
        float[] to = tucked
                ? NovaPlace.tuckSnap(bubble.left, bubble.top, size, w, h, insetL(), insetT(), insetR(), insetB(), m)
                : NovaPlace.snap(bubble.left, bubble.top, size, w, h, insetL(), insetT(), insetR(), insetB(), m);
        bubble.set(to[0], to[1], to[0] + size, to[1] + size);
        savePlace(to[0], to[1]);
        layoutParts(w, h);
        requestLayout();
        invalidate();
    }

    void savePlace(float x, float y) {
        if (prefs == null) return;
        int w = getWidth(), h = getHeight();
        float size = liveSize(), m = liveMargin();
        boolean left = NovaPlace.leftSide(x, size, w, insetL(), insetR(), m);
        float frac = NovaPlace.yFrac(y, size, h, insetT(), insetB(), m);
        prefs.edit().putBoolean(PREF_LEFT, left).putFloat(PREF_Y, frac).apply();
    }

    void styleGlass(android.view.View v, int fill, int stroke, float radius, float strokeW) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(radius);
        d.setStroke(Math.max(1, Math.round(strokeW)), stroke);
        v.setBackground(d);
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        int code = event.getKeyCode();
        boolean back = code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_BUTTON_B
                || code == KeyEvent.KEYCODE_ESCAPE;
        if (expanded && back) {
            if (imeUp() || (askField != null && askField.hasFocus())) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) hideSoftIme();
                return true;
            }
            if (event.getAction() == KeyEvent.ACTION_UP) {
                if (voiceLoop) { exitVoiceMode(); return true; }
                collapse();
            }
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    boolean hitsInteractive(float x, float y) {
        if (hidden) return hitsTab(x, y);
        if (hitsMute(x, y)) return true;
        if (hitsBubble(x, y)) return true;
        return hitsPanel(x, y);
    }

    boolean hitsPanel(float x, float y) {
        if (!expanded && !big) return false;
        if (panel == null || panel.getVisibility() != VISIBLE) return false;
        // Use the laid-out bounds so Ask / chips / field / mic match where they were drawn.
        return x >= panel.getLeft() && x <= panel.getRight() && y >= panel.getTop() && y <= panel.getBottom();
    }

    boolean hitsMute(float x, float y) { return !muteHit.isEmpty() && muteHit.contains(x, y); }

    boolean hitsTab(float x, float y) {
        if (tabHit.isEmpty()) return false;
        float pad = dp(8);
        return x >= tabHit.left - pad && x <= tabHit.right + pad && y >= tabHit.top - pad && y <= tabHit.bottom + pad;
    }

    boolean hitsBubble(float x, float y) {
        if (bubble.isEmpty()) return false;
        float pad = dp(4);
        return x >= bubble.left - pad && x <= bubble.right + pad && y >= bubble.top - pad && y <= bubble.bottom + pad;
    }

    int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}

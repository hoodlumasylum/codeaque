package com.codeaque.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Build;
import android.speech.tts.TextToSpeech;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.MotionEvent;
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

    final NovaFace face = new NovaFace();
    final NovaAnim.State st = new NovaAnim.State();
    final RectF bubble = new RectF(), muteHit = new RectF();
    final Paint mutePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    final android.view.Choreographer choreo = android.view.Choreographer.getInstance();
    final SharedPreferences prefs;
    final java.util.Random rng = new java.util.Random();
    LinearLayout panel;
    TextView answerView, subView, muteLabel;
    EditText askField;
    TextToSpeech tts;
    String pendingSpeak, lastAnswer = NovaHelp.GREETING;
    boolean expanded, running, overlayMode, faceLoaded, kids, talking, ttsReady;
    long lastFrameNs, talkUntil;
    int bubbleSize, margin;
    Host host;

    public interface Host {
        void onNovaExpandChanged(boolean expanded);
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
        margin = Math.round(14 * d);
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
        boolean show = prefs == null || prefs.getBoolean(PREF_BUBBLE, true);
        setVisibility(show ? VISIBLE : GONE);
        if (!show) collapse();
        if (show) startAnim(); else stopAnim();
    }

    public boolean isExpanded() { return expanded; }

    /** Collapse if open. Returns true if it consumed Back. */
    public boolean collapse() {
        if (!expanded) return false;
        setExpanded(false);
        return true;
    }

    public void toggle() { setExpanded(!expanded); }

    public RectF bubbleRect() { return bubble; }

    public int bubbleSize() { return bubbleSize; }

    void setExpanded(boolean on) {
        if (expanded == on) {
            if (on && kids) greet();
            return;
        }
        expanded = on;
        setPanelShown(on);
        if (host != null) host.onNovaExpandChanged(on);
        if (on) {
            if (askField != null) askField.clearFocus();
            if (kids) greet();
        } else {
            stopSpeak();
        }
        invalidate();
    }

    void greet() {
        lastAnswer = NovaHelp.KIDS_GREETING;
        if (answerView != null) answerView.setText(lastAnswer);
        speakAuto(lastAnswer);
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
        subView = null;
        muteLabel = null;
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
        panel.setBackgroundColor(0xF214161C);
        if (Build.VERSION.SDK_INT >= 21) panel.setElevation(8 * d);

        LinearLayout head = new LinearLayout(getContext());
        head.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout titles = new LinearLayout(getContext());
        titles.setOrientation(LinearLayout.VERTICAL);
        TextView title = new TextView(getContext());
        title.setText("Nova");
        title.setTextColor(0xFFF4E4C1);
        title.setTextSize(20);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        subView = new TextView(getContext());
        subView.setText(kids ? "Games, learning and jokes" : "CodeAque helper · offline");
        subView.setTextColor(0xFFB8A88A);
        subView.setTextSize(12);
        titles.addView(title);
        titles.addView(subView);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1);
        head.addView(titles, tlp);
        if (kids) {
            muteLabel = btn(muted() ? "Muted" : "Sound on", v -> toggleMute());
            head.addView(muteLabel);
        }
        panel.addView(head);

        HorizontalScrollView chips = new HorizontalScrollView(getContext());
        chips.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (String c : (kids ? NovaHelp.kidsChips() : NovaHelp.chips())) {
            TextView chip = new TextView(getContext());
            chip.setText(c);
            chip.setTextColor(0xFFF4E4C1);
            chip.setTextSize(13);
            chip.setPadding(Math.round(10 * d), Math.round(6 * d), Math.round(10 * d), Math.round(6 * d));
            chip.setBackgroundColor(0xFF2A2420);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, pad / 2, Math.round(6 * d), 0);
            final String q = c;
            chip.setOnClickListener(v -> ask(q));
            row.addView(chip, lp);
        }
        chips.addView(row);
        panel.addView(chips);

        if (kids) {
            Button joke = btn("Tell me a joke", v -> tellJoke());
            joke.setTextSize(16);
            LinearLayout.LayoutParams jlp = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            jlp.topMargin = pad;
            panel.addView(joke, jlp);
        }

        askField = new EditText(getContext());
        askField.setHint(kids ? "Ask about games or learning…" : "Ask about CodeAque…");
        askField.setHintTextColor(0x88B8A88A);
        askField.setTextColor(0xFFF5F0E8);
        askField.setTextSize(14);
        askField.setSingleLine(true);
        askField.setInputType(InputType.TYPE_CLASS_TEXT);
        askField.setBackgroundColor(0xFF1C1A18);
        askField.setPadding(pad, pad, pad, pad);
        askField.setOnEditorActionListener((v, a, e) -> { ask(askField.getText().toString()); return true; });
        LinearLayout.LayoutParams askLp = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        askLp.topMargin = pad;
        panel.addView(askField, askLp);

        LinearLayout actions = new LinearLayout(getContext());
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.addView(btn("Ask", v -> ask(askField.getText().toString())), lpBtn());
        if (!kids) actions.addView(btn("Hear it", v -> speak(lastAnswer)), lpBtn());
        actions.addView(btn("Close", v -> collapse()), lpBtn());
        panel.addView(actions);

        answerView = new TextView(getContext());
        answerView.setText(lastAnswer);
        answerView.setTextColor(0xFFE8E0D4);
        answerView.setTextSize(kids ? 16 : 14);
        answerView.setPadding(0, pad, 0, pad / 2);
        ScrollView sv = new ScrollView(getContext());
        sv.addView(answerView);
        LinearLayout.LayoutParams svLp = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1);
        panel.addView(sv, svLp);

        TextView foot = new TextView(getContext());
        foot.setText(kids ? NovaHelp.KIDS_FOOT : NovaHelp.LIMITS);
        foot.setTextColor(0xFF8A8070);
        foot.setTextSize(11);
        panel.addView(foot);

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
        b.setMinHeight(0);
        b.setMinWidth(0);
        b.setPadding(dp(8), dp(4), dp(8), dp(4));
        return b;
    }

    LinearLayout.LayoutParams lpBtn() {
        return new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1);
    }

    void ask(String q) {
        if (kids && NovaHelp.looksLikeJoke(q)) { tellJoke(); return; }
        lastAnswer = NovaHelp.answer(q, kids);
        if (kids && NovaHelp.kidsUnsafe(lastAnswer)) lastAnswer = NovaHelp.KIDS_FOOT;
        if (answerView != null) answerView.setText(lastAnswer);
        if (askField != null && q != null) askField.setText(q);
        if (kids) speakAuto(lastAnswer);
        invalidate();
    }

    void tellJoke() {
        int i = prefs == null ? 0 : prefs.getInt(PREF_JOKE, 0);
        lastAnswer = NovaHelp.jokeAt(i);
        if (prefs != null) prefs.edit().putInt(PREF_JOKE, i + 1).apply();
        if (answerView != null) answerView.setText(lastAnswer);
        speakAuto(lastAnswer);
        invalidate();
    }

    boolean muted() { return prefs != null && prefs.getBoolean(PREF_KIDS_MUTE, false); }

    void toggleMute() {
        boolean next = !muted();
        if (prefs != null) prefs.edit().putBoolean(PREF_KIDS_MUTE, next).apply();
        if (next) stopSpeak();
        if (muteLabel != null) muteLabel.setText(next ? "Muted" : "Sound on");
        invalidate();
    }

    void speakAuto(String s) {
        if (!kids || muted()) return;
        speak(s);
    }

    void speak(String s) {
        if (s == null || s.isEmpty()) return;
        if (kids && muted()) return;
        pendingSpeak = s;
        ensureTts();
        if (ttsReady) flushSpeak();
    }

    void ensureTts() {
        if (tts != null) return;
        tts = new TextToSpeech(getContext(), status -> {
            if (status == TextToSpeech.SUCCESS && tts != null) {
                try {
                    if (tts.isLanguageAvailable(Locale.UK) >= TextToSpeech.LANG_AVAILABLE) tts.setLanguage(Locale.UK);
                    else tts.setLanguage(Locale.getDefault());
                } catch (Exception ignored) { }
                ttsReady = true;
                flushSpeak();
            } else {
                ttsReady = false;
                pendingSpeak = null;
            }
        });
    }

    void flushSpeak() {
        if (tts == null || pendingSpeak == null) return;
        String s = pendingSpeak;
        pendingSpeak = null;
        talking = true;
        talkUntil = android.os.SystemClock.uptimeMillis() + estimateMs(s);
        if (Build.VERSION.SDK_INT >= 21) tts.speak(s, TextToSpeech.QUEUE_FLUSH, null, "nova");
        else tts.speak(s, TextToSpeech.QUEUE_FLUSH, null);
    }

    static int estimateMs(String s) {
        int words = 1;
        if (s != null) {
            String t = s.trim();
            if (!t.isEmpty()) {
                words = 1;
                for (int i = 0; i < t.length(); i++) if (t.charAt(i) == ' ') words++;
            }
        }
        return 400 + words * 380;
    }

    void stopSpeak() {
        pendingSpeak = null;
        talking = false;
        talkUntil = 0;
        st.talking = false;
        st.mouth = 0;
        if (tts != null) {
            try { tts.stop(); } catch (Exception ignored) { }
        }
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (!faceLoaded) faceLoaded = face.load(getContext().getAssets());
        if (kids) ensureTts();
        startAnim();
    }

    @Override protected void onDetachedFromWindow() {
        stopAnim();
        shutdownTts();
        super.onDetachedFromWindow();
    }

    public void release() {
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
        if (running || getVisibility() != VISIBLE) return;
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
            boolean still = false;
            try { still = tts != null && tts.isSpeaking(); } catch (Exception ignored) { }
            if (!still) { talking = false; talkUntil = 0; }
        }
        // Cap at ~30 fps: blink and breath don't need 60, and we stay off the scroll thread.
        NovaAnim.tick(st, now, dt, rng.nextInt());
        NovaAnim.tickTalk(st, now, talking && !(kids && muted()));
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
        if (panel != null && panel.getVisibility() == VISIBLE) {
            panel.layout(Math.round(panelBox().left), Math.round(panelBox().top),
                    Math.round(panelBox().right), Math.round(panelBox().bottom));
        }
    }

    RectF panelBox() {
        int w = getWidth(), h = getHeight();
        float pw = Math.min(w - bubbleSize - margin * 2 - dp(8), Math.max(dp(280), w * 0.62f));
        if (pw < dp(240)) pw = Math.max(dp(220), w - margin * 2);
        float left = margin + insetL();
        float top = margin + insetT();
        float right = left + pw;
        float bottom = h - margin - insetB();
        if (right > bubble.left - dp(8) && w > bubbleSize * 2) right = bubble.left - dp(8);
        return new RectF(left, top, right, bottom);
    }

    void layoutParts(int w, int h) {
        int r = margin + insetR();
        int btm = margin + insetB();
        bubble.set(w - r - bubbleSize, h - btm - bubbleSize, w - r, h - btm);
        float s = Math.max(dp(28), bubble.width() * 0.32f);
        muteHit.set(bubble.right - s, bubble.bottom - s, bubble.right + dp(4), bubble.bottom + dp(4));
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
        float ring = Math.max(2, getResources().getDisplayMetrics().density * 2.2f);
        face.draw(c, bubble, st, ring, true);
        if (kids) drawMuteBadge(c);
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
        if (!hitsInteractive(ev.getX(), ev.getY())) return false;
        return super.dispatchTouchEvent(ev);
    }

    @Override public boolean onTouchEvent(MotionEvent ev) {
        if (kids && hitsMute(ev.getX(), ev.getY())) {
            if (ev.getAction() == MotionEvent.ACTION_UP) toggleMute();
            return true;
        }
        if (hitsBubble(ev.getX(), ev.getY())) {
            if (ev.getAction() == MotionEvent.ACTION_UP) toggle();
            return true;
        }
        return super.onTouchEvent(ev);
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (expanded && event.getAction() == KeyEvent.ACTION_UP
                && (event.getKeyCode() == KeyEvent.KEYCODE_BACK || event.getKeyCode() == KeyEvent.KEYCODE_BUTTON_B
                || event.getKeyCode() == KeyEvent.KEYCODE_ESCAPE)) {
            collapse();
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    boolean hitsInteractive(float x, float y) {
        if (kids && hitsMute(x, y)) return true;
        if (hitsBubble(x, y)) return true;
        if (expanded && panel != null && panel.getVisibility() == VISIBLE) {
            RectF p = panelBox();
            return p.contains(x, y);
        }
        return false;
    }

    boolean hitsMute(float x, float y) { return muteHit.contains(x, y); }

    boolean hitsBubble(float x, float y) {
        float cx = bubble.centerX(), cy = bubble.centerY(), r = bubble.width() * 0.5f + dp(4);
        float dx = x - cx, dy = y - cy;
        return dx * dx + dy * dy <= r * r;
    }

    int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}

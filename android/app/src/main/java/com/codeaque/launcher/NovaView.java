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
import android.view.HapticFeedbackConstants;
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

    final NovaFace face = new NovaFace();
    final NovaAnim.State st = new NovaAnim.State();
    final RectF bubble = new RectF(), muteHit = new RectF();
    final Paint mutePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    final android.view.Choreographer choreo = android.view.Choreographer.getInstance();
    final SharedPreferences prefs;
    final java.util.Random rng = new java.util.Random();
    LinearLayout panel;
    TextView answerView, muteLabel;
    EditText askField;
    TextToSpeech tts;
    String pendingSpeak, lastAnswer = NovaHelp.GREETING;
    boolean expanded, running, overlayMode, faceLoaded, kids, talking, ttsReady;
    boolean dragging, snapping, downOnMute, downOnBubble, placed;
    float downX, downY, startLeft, startTop, slop, snapX0, snapY0, snapX1, snapY1;
    long lastFrameNs, talkUntil, downAt, snapStart;
    int bubbleSize, margin, longPressMs;
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
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1);
        head.addView(title, tlp);
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
            chip.setTextColor(0xFFF2F6FA);
            chip.setTextSize(13);
            chip.setPadding(Math.round(12 * d), Math.round(7 * d), Math.round(12 * d), Math.round(7 * d));
            styleGlass(chip, 0x55202830, 0x44E8C872, 16 * d, 1f);
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
            styleGlass(joke, 0x55202830, 0x66E8C872, 18 * d, 1.25f);
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
        styleGlass(askField, 0x66101820, 0x33E8C872, 14 * d, 1f);
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
        foot.setTextColor(0xFF8A98A4);
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
        b.setPadding(dp(10), dp(6), dp(10), dp(6));
        styleGlass(b, 0x33202830, 0x33E8C872, dp(14), 1f);
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
        snapping = false;
        dragging = false;
        downOnBubble = false;
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
        if (downOnBubble && !dragging && now - downAt >= longPressMs) startDrag();
        if (snapping) tickSnap(now);
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
        float gap = dp(8);
        float pw = Math.min(w - bubbleSize - margin * 2 - gap, Math.max(dp(280), w * 0.62f));
        if (pw < dp(240)) pw = Math.max(dp(220), w - margin * 2);
        float top = margin + insetT();
        float bottom = h - margin - insetB();
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

    void layoutParts(int w, int h) {
        if (w <= 0 || h <= 0) return;
        if (dragging || snapping) {
            // bubble already follows the finger or the snap
        } else {
            boolean left = prefs != null && prefs.getBoolean(PREF_LEFT, false);
            float yFrac = prefs != null && prefs.contains(PREF_Y) ? prefs.getFloat(PREF_Y, 1f) : 1f;
            float x = NovaPlace.xForSide(left, bubbleSize, w, insetL(), insetR(), margin);
            float y = NovaPlace.yFromFrac(yFrac, bubbleSize, h, insetT(), insetB(), margin);
            bubble.set(x, y, x + bubbleSize, y + bubbleSize);
            placed = true;
        }
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
        // Keep the gesture while a long-press or drag is in progress, even if the finger leaves the bubble.
        if (dragging || downOnBubble) return super.dispatchTouchEvent(ev);
        if (!hitsInteractive(ev.getX(), ev.getY())) return false;
        return super.dispatchTouchEvent(ev);
    }

    @Override public boolean onTouchEvent(MotionEvent ev) {
        int action = ev.getActionMasked();
        if (action == MotionEvent.ACTION_POINTER_DOWN) {
            // A second finger is not a drag (and must not steal a pinch on the home).
            cancelPress(false);
            return true;
        }
        if (action == MotionEvent.ACTION_DOWN) {
            downOnBubble = hitsBubble(ev.getX(), ev.getY()) || (kids && hitsMute(ev.getX(), ev.getY()));
            downOnMute = kids && hitsMute(ev.getX(), ev.getY());
            if (!downOnBubble) return false;
            downX = ev.getX();
            downY = ev.getY();
            downAt = ev.getEventTime();
            startLeft = bubble.left;
            startTop = bubble.top;
            dragging = false;
            snapping = false;
            return true;
        }
        if (!downOnBubble && !dragging) return false;
        if (action == MotionEvent.ACTION_MOVE) {
            float dx = ev.getX() - downX, dy = ev.getY() - downY;
            if (!dragging && NovaPlace.movedPast(dx, dy, slop)) {
                // Moved before the long-press: not a drag and not a tap. Start a row scroll off the bubble.
                downOnBubble = false;
                downOnMute = false;
                return false;
            }
            if (!dragging && android.os.SystemClock.uptimeMillis() - downAt >= longPressMs) {
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
            downOnBubble = false;
            downOnMute = false;
            dragging = false;
            if (action == MotionEvent.ACTION_UP && wasDrag) {
                snapGently();
                return true;
            }
            if (action == MotionEvent.ACTION_UP && !wasDrag && !NovaPlace.movedPast(ev.getX() - downX, ev.getY() - downY, slop)) {
                if (mute) toggleMute();
                else toggle();
            }
            return true;
        }
        return true;
    }

    void startDrag() {
        if (dragging) return;
        dragging = true;
        snapping = false;
        try { performHapticFeedback(HapticFeedbackConstants.LONG_PRESS); } catch (Exception ignored) { }
    }

    void cancelPress(boolean snapIfDrag) {
        downOnBubble = false;
        downOnMute = false;
        if (dragging) {
            dragging = false;
            if (snapIfDrag) snapGently();
            else placeClamped();
        }
    }

    void moveBubble(float left, float top) {
        int w = getWidth(), h = getHeight();
        float minX = insetL() + margin, minY = insetT() + margin;
        float maxX = w - insetR() - margin - bubbleSize, maxY = h - insetB() - margin - bubbleSize;
        left = NovaPlace.clamp(left, minX, Math.max(minX, maxX));
        top = NovaPlace.clamp(top, minY, Math.max(minY, maxY));
        bubble.set(left, top, left + bubbleSize, top + bubbleSize);
        layoutParts(w, h);
        requestLayout();
        invalidate();
    }

    void snapGently() {
        int w = getWidth(), h = getHeight();
        float[] to = NovaPlace.snap(bubble.left, bubble.top, bubbleSize, w, h, insetL(), insetT(), insetR(), insetB(), margin);
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
        float[] to = NovaPlace.snap(bubble.left, bubble.top, bubbleSize, w, h, insetL(), insetT(), insetR(), insetB(), margin);
        bubble.set(to[0], to[1], to[0] + bubbleSize, to[1] + bubbleSize);
        savePlace(to[0], to[1]);
        layoutParts(w, h);
        requestLayout();
        invalidate();
    }

    void savePlace(float x, float y) {
        if (prefs == null) return;
        int w = getWidth(), h = getHeight();
        boolean left = NovaPlace.leftSide(x, bubbleSize, w, insetL(), insetR(), margin);
        float frac = NovaPlace.yFrac(y, bubbleSize, h, insetT(), insetB(), margin);
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

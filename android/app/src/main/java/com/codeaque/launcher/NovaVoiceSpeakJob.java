package com.codeaque.launcher;

/** Named worker that synthesizes Nova's on-device voice and plays it. */
public final class NovaVoiceSpeakJob implements Runnable, NovaVoicePlay.Listener {
    final NovaView view;
    final String text;
    final int sid;
    final int gen;
    public NovaVoiceSpeakJob(NovaView view, String text, int sid) {
        this(view, text, sid, 0);
    }
    public NovaVoiceSpeakJob(NovaView view, String text, int sid, int gen) {
        this.view = view;
        this.text = text;
        this.sid = sid;
        this.gen = gen;
    }
    @Override public void run() {
        if (stale()) return;
        if (!NovaKokoro.isLoaded()) {
            if (!NovaKokoro.load(view.getContext())) {
                fallback();
                return;
            }
        }
        if (stale()) return;
        float speed = NovaKokoro.speedFrom(view.prefs);
        float[] samples = NovaKokoro.synthesize(text, sid, speed);
        if (stale()) return;
        if (samples == null || samples.length < 16) {
            fallback();
            return;
        }
        NovaVoicePlay.play(samples, NovaKokoro.sampleRate(), this, gen);
    }
    boolean stale() {
        return view == null || !NovaSpeakGate.isCurrent(gen, view.speakGen);
    }
    void fallback() {
        if (stale()) return;
        String err = NovaKokoro.lastError();
        view.post(new NovaVoiceFallback(view, text, gen, err));
    }
    @Override public void onVoiceStart() {
        if (!stale()) view.onDeviceVoiceStart();
    }
    @Override public void onVoiceAmp(float amp) {
        if (!stale()) view.onDeviceVoiceAmp(amp);
    }
    @Override public void onVoiceEnd() {
        if (!stale()) view.onDeviceVoiceEnd();
    }
}

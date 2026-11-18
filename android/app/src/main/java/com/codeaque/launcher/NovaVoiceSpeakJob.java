package com.codeaque.launcher;

/** Named worker that synthesizes Nova's on-device voice and plays it. */
public final class NovaVoiceSpeakJob implements Runnable, NovaVoicePlay.Listener {
    final NovaView view;
    final String text;
    final int sid;
    public NovaVoiceSpeakJob(NovaView view, String text, int sid) {
        this.view = view;
        this.text = text;
        this.sid = sid;
    }
    @Override public void run() {
        if (!NovaKokoro.isLoaded()) {
            if (!NovaKokoro.load(view.getContext())) {
                view.post(new NovaVoiceFallback(view, text));
                return;
            }
        }
        float[] samples = NovaKokoro.synthesize(text, sid);
        if (samples == null || samples.length < 16) {
            view.post(new NovaVoiceFallback(view, text));
            return;
        }
        NovaVoicePlay.play(samples, NovaKokoro.sampleRate(), this);
    }
    @Override public void onVoiceStart() { view.onDeviceVoiceStart(); }
    @Override public void onVoiceAmp(float amp) { view.onDeviceVoiceAmp(amp); }
    @Override public void onVoiceEnd() { view.onDeviceVoiceEnd(); }
}

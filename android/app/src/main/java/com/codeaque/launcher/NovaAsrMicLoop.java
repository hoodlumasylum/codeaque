package com.codeaque.launcher;

import android.media.AudioRecord;

/** AudioRecord read loop for {@link NovaAsrMic}. Named for d8 8.2. */
public final class NovaAsrMicLoop implements Runnable {
    final NovaAsrMic mic;

    public NovaAsrMicLoop(NovaAsrMic mic) { this.mic = mic; }

    @Override public void run() {
        if (mic == null) return;
        short[] buf = new short[1600];
        float[] fbuf = new float[1600];
        NovaEndpoint ep = new NovaEndpoint();
        long started = android.os.SystemClock.uptimeMillis();
        ep.start(started);
        int peakAbs = 0;
        long frames = 0;
        while (mic.running) {
            AudioRecord r = mic.record;
            long stream = mic.stream;
            if (r == null || stream == 0) break;
            int n;
            try { n = r.read(buf, 0, buf.length); }
            catch (Throwable t) { n = -1; }
            if (!mic.running) break;
            if (n == AudioRecord.ERROR_INVALID_OPERATION || n == AudioRecord.ERROR_BAD_VALUE || n < 0) {
                mic.running = false;
                mic.postError("Couldn't read the mic.");
                break;
            }
            if (n == 0) continue;
            double acc = 0;
            int count = n;
            if (count > fbuf.length) count = fbuf.length;
            int chunkPeak = 0;
            for (int i = 0; i < count; i++) {
                int a = buf[i] >= 0 ? buf[i] : -buf[i];
                if (a > chunkPeak) chunkPeak = a;
                if (a > peakAbs) peakAbs = a;
                float v = buf[i] / 32768f;
                fbuf[i] = v;
                acc += (double) v * (double) v;
            }
            frames += count;
            float rms = count <= 0 ? 0f : (float) Math.sqrt(acc / count);
            long now = android.os.SystemClock.uptimeMillis();
            ep.onLevel(NovaListenDiag.meterFromPcmRms(rms), now);
            mic.postRms(ep.meter(NovaListenDiag.meterFromPcmRms(rms)));
            NovaAsr.accept(stream, fbuf, count, NovaAsrMic.SAMPLE_RATE);
            String text = NovaAsr.result(stream);
            if (text == null) text = "";
            text = NovaAsrText.tidy(text);
            if (!text.isEmpty() && !text.equals(ep.lastPartial())) {
                ep.onPartial(text, now);
                mic.postPartial(text);
            }
            int decision = ep.decision(now);
            if (decision != NovaEndpoint.CONTINUE) {
                NovaAsr.inputFinished(stream);
                text = NovaAsr.result(stream);
                if (text == null) text = "";
                text = NovaAsrText.tidy(text);
                if (text.isEmpty()) text = NovaAsrText.tidy(ep.lastPartial());
                mic.running = false;
                if (text.isEmpty() && ep.reallySilent(now)
                        && NovaReplyQuick.allZeros(peakAbs, frames, ep.heardSpeech()))
                    mic.postError(NovaReplyQuick.NO_SOUND);
                else if (text.isEmpty()) mic.postError("I didn't catch that. Try again, or type it.");
                else mic.postResult(text);
                break;
            }
        }
    }
}

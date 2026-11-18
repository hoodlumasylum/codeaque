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
        String lastPartial = "";
        long started = android.os.SystemClock.uptimeMillis();
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
            for (int i = 0; i < count; i++) {
                float v = buf[i] / 32768f;
                fbuf[i] = v;
                acc += (double) v * (double) v;
            }
            float rms = count <= 0 ? 0f : (float) Math.sqrt(acc / count);
            mic.postRms(NovaListenDiag.meterFromPcmRms(rms));
            NovaAsr.accept(stream, fbuf, count, NovaAsrMic.SAMPLE_RATE);
            boolean end = NovaAsr.isEndpoint(stream);
            String text = NovaAsr.result(stream);
            if (text == null) text = "";
            text = text.trim();
            if (!text.isEmpty() && !text.equals(lastPartial)) {
                lastPartial = text;
                mic.postPartial(text);
            }
            long now = android.os.SystemClock.uptimeMillis();
            boolean tooLong = now - started > 22_000L;
            if (end || tooLong) {
                if (!end) NovaAsr.inputFinished(stream);
                text = NovaAsr.result(stream);
                if (text == null) text = "";
                text = text.trim();
                mic.running = false;
                if (text.isEmpty()) mic.postError("I didn't catch that. Try again, or type it.");
                else mic.postResult(text);
                break;
            }
        }
    }
}

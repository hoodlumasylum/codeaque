package com.codeaque.launcher;

/**
 * 0.10.61: jaw opening from the RMS of the PCM that is actually leaving
 * the AudioTrack (playback head), not the chunk that was just queued.
 * Plain Java so CatalogTest can check the mapping and the ring lookup.
 */
public final class NovaMouth {
    private NovaMouth() {}

    /** Chunks of mono PCM held until the playback head reaches them. */
    public static final class Ring {
        final int[] pos;
        final float[] rms;
        final int cap;
        int size, start;

        public Ring(int cap) {
            this.cap = Math.max(8, cap);
            this.pos = new int[this.cap];
            this.rms = new float[this.cap];
        }

        public void clear() { size = 0; start = 0; }

        public void add(int samplePos, float rmsValue) {
            int i = (start + size) % cap;
            if (size == cap) {
                start = (start + 1) % cap;
                size--;
            }
            pos[i] = samplePos;
            rms[i] = rmsValue < 0f ? 0f : rmsValue;
            size++;
        }

        /** RMS of the chunk the playback head is currently in. */
        public float atHead(int playHeadSample) {
            if (size == 0) return 0f;
            float last = 0f;
            for (int k = 0; k < size; k++) {
                int i = (start + k) % cap;
                if (pos[i] > playHeadSample && k > 0) return last;
                last = rms[i];
            }
            return last;
        }
    }

    /** RMS of a float sample chunk in [-1, 1]. */
    public static float rms(float[] samples, int off, int n) {
        if (samples == null || n <= 0) return 0f;
        int end = Math.min(samples.length, off + n);
        if (off < 0 || off >= end) return 0f;
        double sum = 0;
        int c = 0;
        for (int i = off; i < end; i++) {
            float s = samples[i];
            sum += (double) s * s;
            c++;
        }
        if (c == 0) return 0f;
        return (float) Math.sqrt(sum / c);
    }

    public static float rmsShort(short[] pcm, int n) {
        if (pcm == null || n <= 0) return 0f;
        int end = Math.min(pcm.length, n);
        double sum = 0;
        for (int i = 0; i < end; i++) {
            float s = pcm[i] / 32768f;
            sum += (double) s * s;
        }
        return (float) Math.sqrt(sum / end);
    }

    /**
     * Map RMS to a jaw opening that is obviously open on speech and closed
     * on quiet. A raw RMS of ~0.05 already opens clearly.
     */
    public static float mouthFromRms(float rms) {
        if (rms <= 0.012f) return 0f;
        float m = (rms - 0.012f) * 8.5f;
        if (m < 0.22f) m = 0.22f;
        if (m > 1f) m = 1f;
        return m;
    }

    public static int latencySamples(int sampleRate, int bufferFrames) {
        if (sampleRate <= 0) return 0;
        int buf = Math.max(0, bufferFrames);
        return buf;
    }

    public static boolean drained(int playHead, int totalSamples, boolean stopped) {
        if (stopped) return true;
        return totalSamples > 0 && playHead >= totalSamples;
    }
}

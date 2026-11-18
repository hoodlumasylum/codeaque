package com.codeaque.launcher;

import java.io.File;

/**
 * On-demand English streaming ASR pack (sherpa-onnx zipformer int8, 2023-06-26).
 * About 75 MB, a step up from the older 20M pack. Downloaded like Heart, never in the APK.
 */
public final class NovaAsrFiles {
    private NovaAsrFiles() {}

    public static final String DIR = "nova/asr-en-20230626";
    public static final String BASE =
            "https://huggingface.co/csukuangfj/sherpa-onnx-streaming-zipformer-en-2023-06-26/resolve/main/";
    public static final String ENCODER = "encoder-epoch-99-avg-1-chunk-16-left-128.int8.onnx";
    public static final String DECODER = "decoder-epoch-99-avg-1-chunk-16-left-128.int8.onnx";
    public static final String JOINER = "joiner-epoch-99-avg-1-chunk-16-left-128.int8.onnx";
    public static final String TOKENS = "tokens.txt";

    public static final long ENCODER_MIN = 60_000_000L;
    public static final long DECODER_MIN = 100_000L;
    public static final long JOINER_MIN = 100_000L;
    public static final long TOKENS_MIN = 10L;

    public static final String SIZE_LABEL = "about 75 MB";
    public static final String DOWNLOAD_BTN = "Get built-in listening";

    public static final String[] NAMES = { ENCODER, DECODER, JOINER, TOKENS };

    public static boolean sizesOk(long encoder, long decoder, long joiner, long tokens) {
        return encoder >= ENCODER_MIN && decoder >= DECODER_MIN
                && joiner >= JOINER_MIN && tokens >= TOKENS_MIN;
    }

    public static boolean filesReadyAt(File dir) {
        if (dir == null) return false;
        return sizesOkAt(dir, ENCODER, DECODER, JOINER, TOKENS, ENCODER_MIN, DECODER_MIN, JOINER_MIN, TOKENS_MIN);
    }

    /** Older, lighter English pack (~45 MB). Offered if the 75 MB pack crashes or is declined. */
    public static final String DIR_SMALL = "nova/asr";
    public static final String BASE_SMALL =
            "https://huggingface.co/csukuangfj/sherpa-onnx-streaming-zipformer-en-20M-2023-02-17/resolve/main/";
    public static final String ENCODER_SMALL = "encoder-epoch-99-avg-1.int8.onnx";
    public static final String DECODER_SMALL = "decoder-epoch-99-avg-1.int8.onnx";
    public static final String JOINER_SMALL = "joiner-epoch-99-avg-1.int8.onnx";
    public static final long ENCODER_MIN_SMALL = 20_000_000L;
    public static final String SIZE_LABEL_SMALL = "about 45 MB";
    public static final String DOWNLOAD_BTN_SMALL = "Get smaller pack (45 MB)";
    public static final String[] NAMES_SMALL = { ENCODER_SMALL, DECODER_SMALL, JOINER_SMALL, TOKENS };

    public static boolean filesReadySmallAt(File dir) {
        if (dir == null) return false;
        return sizesOkAt(dir, ENCODER_SMALL, DECODER_SMALL, JOINER_SMALL, TOKENS,
                ENCODER_MIN_SMALL, DECODER_MIN, JOINER_MIN, TOKENS_MIN);
    }

    public static boolean sizesOkAt(File dir, String enc, String dec, String join, String tok,
            long encMin, long decMin, long joinMin, long tokMin) {
        if (dir == null) return false;
        return new File(dir, enc).length() >= encMin
                && new File(dir, dec).length() >= decMin
                && new File(dir, join).length() >= joinMin
                && new File(dir, tok).length() >= tokMin;
    }

    public static long minLength(String name) {
        if (name == null) return Long.MAX_VALUE;
        if (ENCODER.equals(name)) return ENCODER_MIN;
        if (ENCODER_SMALL.equals(name)) return ENCODER_MIN_SMALL;
        if (DECODER.equals(name) || DECODER_SMALL.equals(name)) return DECODER_MIN;
        if (JOINER.equals(name) || JOINER_SMALL.equals(name)) return JOINER_MIN;
        if (TOKENS.equals(name)) return TOKENS_MIN;
        return 8L;
    }

    public static boolean alreadyDownloaded(File dest, String name) {
        return dest != null && dest.isFile() && dest.length() >= minLength(name);
    }

    public static String[] namesFor(File dir) {
        return usingSmall(dir) ? NAMES_SMALL : NAMES;
    }

    public static boolean packMarkedBad(File filesDir) {
        if (filesDir == null) return false;
        return NovaAsrGuard.isMarkedBad(new File(filesDir, DIR))
                || NovaAsrGuard.isMarkedBad(new File(filesDir, DIR_SMALL));
    }

    /** Which on-disk pack to use, if either is complete and not marked bad. */
    public static File usableDir(File filesDir) {
        if (filesDir == null) return null;
        File large = new File(filesDir, DIR);
        if (filesReadyAt(large) && !NovaAsrGuard.isMarkedBad(large) && !NovaAsrGuard.diedMidLoad(large)
                && NovaAsrGuard.namesMatch(ENCODER, DECODER, JOINER, TOKENS)) return large;
        File small = new File(filesDir, DIR_SMALL);
        if (filesReadySmallAt(small) && !NovaAsrGuard.isMarkedBad(small) && !NovaAsrGuard.diedMidLoad(small)
                && NovaAsrGuard.namesMatch(ENCODER_SMALL, DECODER_SMALL, JOINER_SMALL, TOKENS)) return small;
        return null;
    }

    public static boolean usingSmall(File dir) {
        if (dir == null) return false;
        String p = dir.getPath().replace('\\', '/');
        return p.endsWith("/" + DIR_SMALL);
    }
}

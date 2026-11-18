package com.codeaque.launcher;

import java.io.File;

/**
 * On-demand English streaming ASR pack (sherpa-onnx zipformer 20M int8).
 * Plain-Java size checks so CatalogTest can cover filesReady without JNI.
 * The model is downloaded like Heart, never bundled in the APK.
 */
public final class NovaAsrFiles {
    private NovaAsrFiles() {}

    public static final String DIR = "nova/asr";
    public static final String BASE =
            "https://huggingface.co/csukuangfj/sherpa-onnx-streaming-zipformer-en-20M-2023-02-17/resolve/main/";
    public static final String ENCODER = "encoder-epoch-99-avg-1.int8.onnx";
    public static final String DECODER = "decoder-epoch-99-avg-1.int8.onnx";
    public static final String JOINER = "joiner-epoch-99-avg-1.int8.onnx";
    public static final String TOKENS = "tokens.txt";

    public static final long ENCODER_MIN = 20_000_000L;
    public static final long DECODER_MIN = 100_000L;
    public static final long JOINER_MIN = 100_000L;
    public static final long TOKENS_MIN = 10L;

    /** Total download, for on-screen copy. */
    public static final String SIZE_LABEL = "about 45 MB";
    public static final String DOWNLOAD_BTN = "Get built-in listening";

    public static final String[] NAMES = { ENCODER, DECODER, JOINER, TOKENS };

    public static boolean sizesOk(long encoder, long decoder, long joiner, long tokens) {
        return encoder >= ENCODER_MIN && decoder >= DECODER_MIN
                && joiner >= JOINER_MIN && tokens >= TOKENS_MIN;
    }

    public static boolean filesReadyAt(File dir) {
        if (dir == null) return false;
        return sizesOk(
                new File(dir, ENCODER).length(),
                new File(dir, DECODER).length(),
                new File(dir, JOINER).length(),
                new File(dir, TOKENS).length());
    }
}

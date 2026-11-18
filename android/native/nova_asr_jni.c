/* Thin JNI over sherpa-onnx C API for streaming English ASR. Models stay on disk, not in the APK. */
#include <jni.h>
#include <stdlib.h>
#include <stdint.h>
#include <string.h>
#include <pthread.h>
#include "sherpa-onnx/c-api/c-api.h"

static pthread_mutex_t g_lock = PTHREAD_MUTEX_INITIALIZER;

static const char *chars_or_empty(JNIEnv *env, jstring js, const char **held) {
    if (!js) { *held = NULL; return ""; }
    const char *s = (*env)->GetStringUTFChars(env, js, NULL);
    *held = s;
    return s ? s : "";
}

static void release_held(JNIEnv *env, jstring js, const char *held) {
    if (held && js) (*env)->ReleaseStringUTFChars(env, js, held);
}

JNIEXPORT jlong JNICALL
Java_com_codeaque_launcher_NovaAsr_nativeLoad(JNIEnv *env, jclass cls,
        jstring jenc, jstring jdec, jstring jjoin, jstring jtokens) {
    (void)cls;
    if (!jenc || !jdec || !jjoin || !jtokens) return 0;
    const char *henc = NULL, *hdec = NULL, *hjoin = NULL, *htok = NULL;
    const char *enc = chars_or_empty(env, jenc, &henc);
    const char *dec = chars_or_empty(env, jdec, &hdec);
    const char *join = chars_or_empty(env, jjoin, &hjoin);
    const char *tok = chars_or_empty(env, jtokens, &htok);
    if (!henc || !hdec || !hjoin || !htok) {
        release_held(env, jenc, henc);
        release_held(env, jdec, hdec);
        release_held(env, jjoin, hjoin);
        release_held(env, jtokens, htok);
        return 0;
    }

    SherpaOnnxOnlineRecognizerConfig config;
    memset(&config, 0, sizeof(config));
    config.feat_config.sample_rate = 16000;
    config.feat_config.feature_dim = 80;
    config.model_config.transducer.encoder = enc;
    config.model_config.transducer.decoder = dec;
    config.model_config.transducer.joiner = join;
    config.model_config.tokens = tok;
    config.model_config.num_threads = 2;
    config.model_config.provider = "cpu";
    config.model_config.debug = 0;
    config.model_config.model_type = "zipformer";
    config.decoding_method = "greedy_search";
    config.max_active_paths = 4;
    config.enable_endpoint = 1;
    config.rule1_min_trailing_silence = 2.0f;
    config.rule2_min_trailing_silence = 1.8f;
    config.rule3_min_utterance_length = 20.0f;

    pthread_mutex_lock(&g_lock);
    const SherpaOnnxOnlineRecognizer *rec = SherpaOnnxCreateOnlineRecognizer(&config);
    pthread_mutex_unlock(&g_lock);

    release_held(env, jenc, henc);
    release_held(env, jdec, hdec);
    release_held(env, jjoin, hjoin);
    release_held(env, jtokens, htok);

    return (jlong)(intptr_t)rec;
}

JNIEXPORT void JNICALL
Java_com_codeaque_launcher_NovaAsr_nativeDestroy(JNIEnv *env, jclass cls, jlong handle) {
    (void)env; (void)cls;
    if (!handle) return;
    pthread_mutex_lock(&g_lock);
    SherpaOnnxDestroyOnlineRecognizer((const SherpaOnnxOnlineRecognizer *)(intptr_t)handle);
    pthread_mutex_unlock(&g_lock);
}

JNIEXPORT jlong JNICALL
Java_com_codeaque_launcher_NovaAsr_nativeCreateStream(JNIEnv *env, jclass cls, jlong handle) {
    (void)env; (void)cls;
    if (!handle) return 0;
    const SherpaOnnxOnlineStream *s = SherpaOnnxCreateOnlineStream(
            (const SherpaOnnxOnlineRecognizer *)(intptr_t)handle);
    return (jlong)(intptr_t)s;
}

JNIEXPORT void JNICALL
Java_com_codeaque_launcher_NovaAsr_nativeDestroyStream(JNIEnv *env, jclass cls, jlong stream) {
    (void)env; (void)cls;
    if (!stream) return;
    SherpaOnnxDestroyOnlineStream((const SherpaOnnxOnlineStream *)(intptr_t)stream);
}

JNIEXPORT void JNICALL
Java_com_codeaque_launcher_NovaAsr_nativeAccept(JNIEnv *env, jclass cls,
        jlong handle, jlong stream, jfloatArray jsamples, jint n, jint rate) {
    (void)cls;
    if (!handle || !stream || !jsamples || n <= 0) return;
    jsize len = (*env)->GetArrayLength(env, jsamples);
    if (n > len) n = (jint)len;
    jfloat *p = (*env)->GetFloatArrayElements(env, jsamples, NULL);
    if (!p) return;
    const SherpaOnnxOnlineRecognizer *r = (const SherpaOnnxOnlineRecognizer *)(intptr_t)handle;
    const SherpaOnnxOnlineStream *s = (const SherpaOnnxOnlineStream *)(intptr_t)stream;
    SherpaOnnxOnlineStreamAcceptWaveform(s, rate, p, n);
    (*env)->ReleaseFloatArrayElements(env, jsamples, p, JNI_ABORT);
    while (SherpaOnnxIsOnlineStreamReady(r, s)) {
        SherpaOnnxDecodeOnlineStream(r, s);
    }
}

JNIEXPORT jstring JNICALL
Java_com_codeaque_launcher_NovaAsr_nativeResult(JNIEnv *env, jclass cls, jlong handle, jlong stream) {
    (void)cls;
    if (!handle || !stream) return (*env)->NewStringUTF(env, "");
    const SherpaOnnxOnlineRecognizerResult *r = SherpaOnnxGetOnlineStreamResult(
            (const SherpaOnnxOnlineRecognizer *)(intptr_t)handle,
            (const SherpaOnnxOnlineStream *)(intptr_t)stream);
    const char *text = (r && r->text) ? r->text : "";
    jstring js = (*env)->NewStringUTF(env, text);
    if (r) SherpaOnnxDestroyOnlineRecognizerResult(r);
    return js;
}

JNIEXPORT jboolean JNICALL
Java_com_codeaque_launcher_NovaAsr_nativeIsEndpoint(JNIEnv *env, jclass cls, jlong handle, jlong stream) {
    (void)env; (void)cls;
    if (!handle || !stream) return JNI_FALSE;
    int32_t ep = SherpaOnnxOnlineStreamIsEndpoint(
            (const SherpaOnnxOnlineRecognizer *)(intptr_t)handle,
            (const SherpaOnnxOnlineStream *)(intptr_t)stream);
    return ep ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_codeaque_launcher_NovaAsr_nativeInputFinished(JNIEnv *env, jclass cls, jlong handle, jlong stream) {
    (void)env; (void)cls;
    if (!stream) return;
    SherpaOnnxOnlineStreamInputFinished((const SherpaOnnxOnlineStream *)(intptr_t)stream);
    if (!handle) return;
    const SherpaOnnxOnlineRecognizer *r = (const SherpaOnnxOnlineRecognizer *)(intptr_t)handle;
    const SherpaOnnxOnlineStream *s = (const SherpaOnnxOnlineStream *)(intptr_t)stream;
    while (SherpaOnnxIsOnlineStreamReady(r, s)) {
        SherpaOnnxDecodeOnlineStream(r, s);
    }
}

JNIEXPORT void JNICALL
Java_com_codeaque_launcher_NovaAsr_nativeReset(JNIEnv *env, jclass cls, jlong handle, jlong stream) {
    (void)env; (void)cls;
    if (!handle || !stream) return;
    SherpaOnnxOnlineStreamReset(
            (const SherpaOnnxOnlineRecognizer *)(intptr_t)handle,
            (const SherpaOnnxOnlineStream *)(intptr_t)stream);
}

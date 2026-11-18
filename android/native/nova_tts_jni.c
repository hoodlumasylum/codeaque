/* Thin JNI over sherpa-onnx C API for Kokoro TTS. Models are loaded from disk, not the APK. */
#include <jni.h>
#include <stdlib.h>
#include <string.h>
#include <pthread.h>
#include "sherpa-onnx/c-api/c-api.h"

static pthread_mutex_t g_lock = PTHREAD_MUTEX_INITIALIZER;

struct NovaTts {
    const SherpaOnnxOfflineTts *tts;
};

JNIEXPORT jlong JNICALL
Java_com_codeaque_launcher_NovaKokoro_nativeLoad(JNIEnv *env, jclass cls,
        jstring jmodel, jstring jvoices, jstring jtokens, jstring jlexicon) {
    (void)cls;
    if (!jmodel || !jvoices || !jtokens) return 0;
    const char *model = (*env)->GetStringUTFChars(env, jmodel, NULL);
    const char *voices = (*env)->GetStringUTFChars(env, jvoices, NULL);
    const char *tokens = (*env)->GetStringUTFChars(env, jtokens, NULL);
    const char *lexicon = jlexicon ? (*env)->GetStringUTFChars(env, jlexicon, NULL) : NULL;

    SherpaOnnxOfflineTtsConfig config;
    memset(&config, 0, sizeof(config));
    config.model.kokoro.model = model;
    config.model.kokoro.voices = voices;
    config.model.kokoro.tokens = tokens;
    config.model.kokoro.lexicon = lexicon ? lexicon : "";
    config.model.kokoro.length_scale = 1.0f;
    config.model.num_threads = 2;
    config.model.debug = 0;
    config.model.provider = "cpu";
    config.max_num_sentences = 1;
    config.silence_scale = 0.2f;

    pthread_mutex_lock(&g_lock);
    const SherpaOnnxOfflineTts *tts = SherpaOnnxCreateOfflineTts(&config);
    pthread_mutex_unlock(&g_lock);

    if (model) (*env)->ReleaseStringUTFChars(env, jmodel, model);
    if (voices) (*env)->ReleaseStringUTFChars(env, jvoices, voices);
    if (tokens) (*env)->ReleaseStringUTFChars(env, jtokens, tokens);
    if (lexicon && jlexicon) (*env)->ReleaseStringUTFChars(env, jlexicon, lexicon);

    if (!tts) return 0;
    struct NovaTts *h = (struct NovaTts *)calloc(1, sizeof(*h));
    if (!h) {
        SherpaOnnxDestroyOfflineTts(tts);
        return 0;
    }
    h->tts = tts;
    return (jlong)(intptr_t)h;
}

JNIEXPORT void JNICALL
Java_com_codeaque_launcher_NovaKokoro_nativeUnload(JNIEnv *env, jclass cls, jlong handle) {
    (void)env; (void)cls;
    struct NovaTts *h = (struct NovaTts *)(intptr_t)handle;
    if (!h) return;
    pthread_mutex_lock(&g_lock);
    if (h->tts) SherpaOnnxDestroyOfflineTts(h->tts);
    h->tts = NULL;
    pthread_mutex_unlock(&g_lock);
    free(h);
}

JNIEXPORT jint JNICALL
Java_com_codeaque_launcher_NovaKokoro_nativeSampleRate(JNIEnv *env, jclass cls, jlong handle) {
    (void)env; (void)cls;
    struct NovaTts *h = (struct NovaTts *)(intptr_t)handle;
    if (!h || !h->tts) return 0;
    return SherpaOnnxOfflineTtsSampleRate(h->tts);
}

JNIEXPORT jint JNICALL
Java_com_codeaque_launcher_NovaKokoro_nativeNumSpeakers(JNIEnv *env, jclass cls, jlong handle) {
    (void)env; (void)cls;
    struct NovaTts *h = (struct NovaTts *)(intptr_t)handle;
    if (!h || !h->tts) return 0;
    return SherpaOnnxOfflineTtsNumSpeakers(h->tts);
}

JNIEXPORT jfloatArray JNICALL
Java_com_codeaque_launcher_NovaKokoro_nativeSynthesize(JNIEnv *env, jclass cls,
        jlong handle, jstring jtext, jint sid, jfloat speed) {
    (void)cls;
    struct NovaTts *h = (struct NovaTts *)(intptr_t)handle;
    if (!h || !h->tts || !jtext) return NULL;
    const char *text = (*env)->GetStringUTFChars(env, jtext, NULL);
    if (!text) return NULL;

    SherpaOnnxGenerationConfig cfg;
    memset(&cfg, 0, sizeof(cfg));
    cfg.silence_scale = 0.2f;
    cfg.speed = speed > 0.1f ? speed : 1.0f;
    cfg.sid = sid;

    pthread_mutex_lock(&g_lock);
    const SherpaOnnxGeneratedAudio *audio =
            SherpaOnnxOfflineTtsGenerateWithConfig(h->tts, text, &cfg, NULL, NULL);
    pthread_mutex_unlock(&g_lock);
    (*env)->ReleaseStringUTFChars(env, jtext, text);
    if (!audio || !audio->samples || audio->n <= 0) {
        if (audio) SherpaOnnxDestroyOfflineTtsGeneratedAudio(audio);
        return NULL;
    }
    jfloatArray out = (*env)->NewFloatArray(env, audio->n);
    if (out) (*env)->SetFloatArrayRegion(env, out, 0, audio->n, audio->samples);
    SherpaOnnxDestroyOfflineTtsGeneratedAudio(audio);
    return out;
}

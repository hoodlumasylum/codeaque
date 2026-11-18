/* Thin JNI over sherpa-onnx C API for Kokoro TTS. Models are loaded from disk, not the APK. */
#include <jni.h>
#include <stdlib.h>
#include <string.h>
#include <pthread.h>
#include "sherpa-onnx/c-api/c-api.h"

static pthread_mutex_t g_lock = PTHREAD_MUTEX_INITIALIZER;

struct NovaTts {
    const SherpaOnnxOfflineTts *tts;
    int last_rate;
};

static const char *chars_or_empty(JNIEnv *env, jstring js, const char **held) {
    if (!js) { *held = NULL; return ""; }
    const char *s = (*env)->GetStringUTFChars(env, js, NULL);
    *held = s;
    return s ? s : "";
}

JNIEXPORT jlong JNICALL
Java_com_codeaque_launcher_NovaKokoro_nativeLoad(JNIEnv *env, jclass cls,
        jstring jmodel, jstring jvoices, jstring jtokens, jstring jlexicon,
        jstring jdataDir, jstring jlang) {
    (void)cls;
    if (!jmodel || !jvoices || !jtokens) return 0;
    const char *model, *voices, *tokens, *lexicon, *dataDir, *lang;
    const char *hmodel = NULL, *hvoices = NULL, *htokens = NULL, *hlex = NULL, *hdata = NULL, *hlang = NULL;
    model = chars_or_empty(env, jmodel, &hmodel);
    voices = chars_or_empty(env, jvoices, &hvoices);
    tokens = chars_or_empty(env, jtokens, &htokens);
    lexicon = chars_or_empty(env, jlexicon, &hlex);
    dataDir = chars_or_empty(env, jdataDir, &hdata);
    lang = chars_or_empty(env, jlang, &hlang);
    if (!hmodel || !hvoices || !htokens) {
        if (hmodel) (*env)->ReleaseStringUTFChars(env, jmodel, hmodel);
        if (hvoices) (*env)->ReleaseStringUTFChars(env, jvoices, hvoices);
        if (htokens) (*env)->ReleaseStringUTFChars(env, jtokens, htokens);
        if (hlex && jlexicon) (*env)->ReleaseStringUTFChars(env, jlexicon, hlex);
        if (hdata && jdataDir) (*env)->ReleaseStringUTFChars(env, jdataDir, hdata);
        if (hlang && jlang) (*env)->ReleaseStringUTFChars(env, jlang, hlang);
        return 0;
    }

    SherpaOnnxOfflineTtsConfig config;
    memset(&config, 0, sizeof(config));
    config.model.kokoro.model = model;
    config.model.kokoro.voices = voices;
    config.model.kokoro.tokens = tokens;
    config.model.kokoro.lexicon = lexicon;
    /* v2.0 ignores data_dir / lang (espeak removed). Still set them so an
     * older library would phonemize English; leave dict_dir empty so jieba/zh
     * is never forced. Language then comes from lexicon-us-en.txt. */
    config.model.kokoro.data_dir = dataDir;
    config.model.kokoro.dict_dir = "";
    config.model.kokoro.lang = (lang && lang[0]) ? lang : "en-us";
    config.model.kokoro.length_scale = 1.0f;
    config.model.num_threads = 2;
    config.model.debug = 0;
    config.model.provider = "cpu";
    config.max_num_sentences = 1;
    config.silence_scale = 0.2f;

    pthread_mutex_lock(&g_lock);
    const SherpaOnnxOfflineTts *tts = SherpaOnnxCreateOfflineTts(&config);
    pthread_mutex_unlock(&g_lock);

    if (hmodel) (*env)->ReleaseStringUTFChars(env, jmodel, hmodel);
    if (hvoices) (*env)->ReleaseStringUTFChars(env, jvoices, hvoices);
    if (htokens) (*env)->ReleaseStringUTFChars(env, jtokens, htokens);
    if (hlex && jlexicon) (*env)->ReleaseStringUTFChars(env, jlexicon, hlex);
    if (hdata && jdataDir) (*env)->ReleaseStringUTFChars(env, jdataDir, hdata);
    if (hlang && jlang) (*env)->ReleaseStringUTFChars(env, jlang, hlang);

    if (!tts) return 0;
    struct NovaTts *h = (struct NovaTts *)calloc(1, sizeof(*h));
    if (!h) {
        SherpaOnnxDestroyOfflineTts(tts);
        return 0;
    }
    h->tts = tts;
    h->last_rate = SherpaOnnxOfflineTtsSampleRate(tts);
    if (h->last_rate <= 0) h->last_rate = 24000;
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
    if (!h) return 0;
    if (h->last_rate > 0) return h->last_rate;
    if (!h->tts) return 0;
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
    if (audio->sample_rate > 0) h->last_rate = audio->sample_rate;
    jfloatArray out = (*env)->NewFloatArray(env, audio->n);
    if (out) (*env)->SetFloatArrayRegion(env, out, 0, audio->n, audio->samples);
    SherpaOnnxDestroyOfflineTtsGeneratedAudio(audio);
    return out;
}

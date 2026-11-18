/* Thin JNI over llama.cpp for the optional on-device Gemma model. The GGUF is not in the APK. */
#include <jni.h>
#include <string>
#include <vector>
#include <mutex>
#include <cstring>
#include "llama.h"

static std::mutex g_mu;

struct NovaLlm {
    llama_model *model = nullptr;
    llama_context *ctx = nullptr;
    const llama_vocab *vocab = nullptr;
};

extern "C" JNIEXPORT jlong JNICALL
Java_com_codeaque_launcher_NovaLlm_nativeLoad(JNIEnv *env, jclass, jstring jpath, jint n_ctx, jint n_threads) {
    if (!jpath) return 0;
    const char *path = env->GetStringUTFChars(jpath, nullptr);
    if (!path) return 0;

    std::lock_guard<std::mutex> lock(g_mu);
    llama_backend_init();

    llama_model_params mp = llama_model_default_params();
    mp.n_gpu_layers = 0;
    llama_model *model = llama_model_load_from_file(path, mp);
    env->ReleaseStringUTFChars(jpath, path);
    if (!model) return 0;

    llama_context_params cp = llama_context_default_params();
    int ctx = n_ctx > 256 ? n_ctx : 1024;
    if (ctx > 4096) ctx = 4096;
    cp.n_ctx = ctx;
    cp.n_batch = 256;
    cp.n_ubatch = 256;
    if (n_threads > 0) cp.n_threads = n_threads;
    llama_context *lctx = llama_init_from_model(model, cp);
    if (!lctx) {
        llama_model_free(model);
        return 0;
    }
    NovaLlm *h = new NovaLlm();
    h->model = model;
    h->ctx = lctx;
    h->vocab = llama_model_get_vocab(model);
    return reinterpret_cast<jlong>(h);
}

extern "C" JNIEXPORT void JNICALL
Java_com_codeaque_launcher_NovaLlm_nativeUnload(JNIEnv *, jclass, jlong handle) {
    std::lock_guard<std::mutex> lock(g_mu);
    NovaLlm *h = reinterpret_cast<NovaLlm *>(handle);
    if (!h) return;
    if (h->ctx) llama_free(h->ctx);
    if (h->model) llama_model_free(h->model);
    delete h;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_codeaque_launcher_NovaLlm_nativeComplete(JNIEnv *env, jclass, jlong handle, jstring jprompt, jint max_tokens) {
    NovaLlm *h = reinterpret_cast<NovaLlm *>(handle);
    if (!h || !h->ctx || !h->model || !h->vocab || !jprompt) return nullptr;
    const char *prompt = env->GetStringUTFChars(jprompt, nullptr);
    if (!prompt) return nullptr;

    std::lock_guard<std::mutex> lock(g_mu);
    llama_memory_clear(llama_get_memory(h->ctx), true);

    int n_prompt = -llama_tokenize(h->vocab, prompt, (int)strlen(prompt), nullptr, 0, true, true);
    if (n_prompt <= 0 || n_prompt > 2048) {
        env->ReleaseStringUTFChars(jprompt, prompt);
        return nullptr;
    }
    std::vector<llama_token> toks(n_prompt);
    if (llama_tokenize(h->vocab, prompt, (int)strlen(prompt), toks.data(), (int)toks.size(), true, true) < 0) {
        env->ReleaseStringUTFChars(jprompt, prompt);
        return nullptr;
    }
    env->ReleaseStringUTFChars(jprompt, prompt);

    auto sparams = llama_sampler_chain_default_params();
    llama_sampler *smpl = llama_sampler_chain_init(sparams);
    llama_sampler_chain_add(smpl, llama_sampler_init_temp(0.7f));
    llama_sampler_chain_add(smpl, llama_sampler_init_dist(12345));

    llama_batch_ext *batch = llama_batch_ext_init(h->ctx);
    auto set_tokens = [&](const llama_token *ids, int32_t n, llama_pos pos0) {
        llama_batch_ext_clear(batch);
        for (int32_t i = 0; i < n; ++i) {
            int32_t idx = llama_batch_ext_add_token(batch, 0, ids[i]);
            llama_pos pos = pos0 + i;
            llama_batch_ext_set_pos(batch, idx, &pos);
        }
        llama_batch_ext_set_output_logits(batch, n - 1, true);
    };
    set_tokens(toks.data(), n_prompt, 0);
    if (llama_process(h->ctx, LLAMA_PROCESS_TYPE_DECODE, batch)) {
        llama_batch_ext_free(batch);
        llama_sampler_free(smpl);
        return nullptr;
    }

    int limit = max_tokens > 0 ? max_tokens : 80;
    if (limit > 160) limit = 160;
    std::string out;
    llama_pos pos = n_prompt;
    for (int i = 0; i < limit; i++) {
        llama_token id = llama_sampler_sample(smpl, h->ctx, -1);
        if (llama_vocab_is_eog(h->vocab, id)) break;
        char buf[256];
        int n = llama_token_to_piece(h->vocab, id, buf, sizeof(buf), 0, true);
        if (n > 0) out.append(buf, n);
        set_tokens(&id, 1, pos);
        if (llama_process(h->ctx, LLAMA_PROCESS_TYPE_DECODE, batch)) break;
        pos += 1;
    }
    llama_batch_ext_free(batch);
    llama_sampler_free(smpl);
    return env->NewStringUTF(out.c_str());
}

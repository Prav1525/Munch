#include <jni.h>
#include <string>
#include <vector>
#include <mutex>
#include "llama.h"

namespace {
std::mutex g_mutex;
llama_model *g_model = nullptr;
llama_context *g_context = nullptr;
llama_sampler *g_sampler = nullptr;

void release_locked() {
    if (g_sampler) { llama_sampler_free(g_sampler); g_sampler = nullptr; }
    if (g_context) { llama_free(g_context); g_context = nullptr; }
    if (g_model) { llama_model_free(g_model); g_model = nullptr; }
}
std::string from_jstring(JNIEnv *env, jstring value) {
    if (!value) return {};
    const char *chars = env->GetStringUTFChars(value, nullptr);
    if (!chars) return {};
    std::string result(chars);
    env->ReleaseStringUTFChars(value, chars);
    return result;
}
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_prav1525_munch_LocalLlmNative_loadModel(JNIEnv *env, jobject, jstring pathValue) {
    std::lock_guard<std::mutex> lock(g_mutex);
    release_locked();
    const std::string path = from_jstring(env, pathValue);
    if (path.empty()) return JNI_FALSE;

    llama_model_params model_params = llama_model_default_params();
    g_model = llama_model_load_from_file(path.c_str(), model_params);
    if (!g_model) return JNI_FALSE;

    llama_context_params context_params = llama_context_default_params();
    context_params.n_ctx = 2048;
    context_params.n_batch = 256;
    context_params.n_threads = 4;
    context_params.n_threads_batch = 4;
    g_context = llama_init_from_model(g_model, context_params);
    if (!g_context) { release_locked(); return JNI_FALSE; }
    g_sampler = llama_sampler_init_greedy();
    return g_sampler ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_prav1525_munch_LocalLlmNative_generate(JNIEnv *env, jobject, jstring promptValue, jint maxTokens) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (!g_model || !g_context || !g_sampler) return env->NewStringUTF("");
    const std::string prompt = from_jstring(env, promptValue);
    const llama_vocab *vocab = llama_model_get_vocab(g_model);
    const int required = -llama_tokenize(vocab, prompt.c_str(), (int)prompt.size(), nullptr, 0, true, true);
    if (required <= 0 || required > 8192) return env->NewStringUTF("");
    std::vector<llama_token> tokens((size_t)required);
    const int token_count = llama_tokenize(vocab, prompt.c_str(), (int)prompt.size(), tokens.data(), (int)tokens.size(), true, true);
    if (token_count <= 0) return env->NewStringUTF("");
    tokens.resize((size_t)token_count);

    llama_memory_clear(llama_get_memory(g_context), true);
    llama_sampler_reset(g_sampler);
    llama_batch batch = llama_batch_get_one(tokens.data(), (int)tokens.size());
    if (llama_decode(g_context, batch) != 0) return env->NewStringUTF("");

    std::string output;
    const int limit = maxTokens > 0 ? (maxTokens > 128 ? 128 : maxTokens) : 96;
    for (int i = 0; i < limit; ++i) {
        const llama_token token = llama_sampler_sample(g_sampler, g_context, -1);
        if (llama_vocab_is_eog(vocab, token)) break;
        char piece[512];
        const int n = llama_token_to_piece(vocab, token, piece, sizeof(piece), 0, true);
        if (n > 0) output.append(piece, (size_t)n);
        llama_sampler_accept(g_sampler, token);
        batch = llama_batch_get_one(const_cast<llama_token *>(&token), 1);
        if (llama_decode(g_context, batch) != 0) break;
    }
    return env->NewStringUTF(output.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_prav1525_munch_LocalLlmNative_unload(JNIEnv *, jobject) {
    std::lock_guard<std::mutex> lock(g_mutex);
    release_locked();
}

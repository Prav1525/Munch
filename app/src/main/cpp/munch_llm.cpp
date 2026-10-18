#include <jni.h>
#include <algorithm>
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
    // Keep inference memory and latency more manageable on 8 GB Android devices.
    context_params.n_ctx = 1024;
    context_params.n_batch = 128;
    context_params.n_threads = 4;
    context_params.n_threads_batch = 4;
    g_context = llama_init_from_model(g_model, context_params);
    if (!g_context) { release_locked(); return JNI_FALSE; }
    g_sampler = llama_sampler_init_greedy();
    if (!g_sampler) { release_locked(); return JNI_FALSE; }
    return JNI_TRUE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_prav1525_munch_LocalLlmNative_generate(JNIEnv *env, jobject, jstring promptValue, jint maxTokens) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (!g_model || !g_context || !g_sampler) return env->NewStringUTF("");
    const std::string prompt = from_jstring(env, promptValue);
    const llama_vocab *vocab = llama_model_get_vocab(g_model);
    const int required = -llama_tokenize(vocab, prompt.c_str(), (int)prompt.size(), nullptr, 0, false, true);
    if (required <= 0 || required > 8192) return env->NewStringUTF("");
    std::vector<llama_token> tokens((size_t)required);
    const int token_count = llama_tokenize(vocab, prompt.c_str(), (int)prompt.size(), tokens.data(), (int)tokens.size(), false, true);
    if (token_count <= 0) return env->NewStringUTF("");
    tokens.resize((size_t)token_count);

    const int context_size = (int)llama_n_ctx(g_context);
    // Reserve room for generated output and keep only the most recent prompt tokens if needed.
    const int output_limit = maxTokens > 0 ? (maxTokens > 48 ? 48 : maxTokens) : 48;
    const int prompt_limit = context_size - output_limit - 1;
    if (prompt_limit <= 0) return env->NewStringUTF("");
    if ((int)tokens.size() > prompt_limit) {
        tokens.erase(tokens.begin(), tokens.end() - prompt_limit);
    }

    llama_memory_clear(llama_get_memory(g_context), true);
    llama_sampler_reset(g_sampler);

    // llama_decode batches are limited by n_batch. Decode long prompts in chunks
    // rather than submitting the entire prompt as one oversized batch.
    constexpr int kPromptBatchSize = 128;
    llama_batch batch{};
    for (size_t offset = 0; offset < tokens.size();) {
        const int count = (int)std::min((size_t)kPromptBatchSize, tokens.size() - offset);
        batch = llama_batch_get_one(tokens.data() + offset, count);
        if (llama_decode(g_context, batch) != 0) return env->NewStringUTF("");
        offset += (size_t)count;
    }

    std::string output;
    for (int i = 0; i < output_limit; ++i) {
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

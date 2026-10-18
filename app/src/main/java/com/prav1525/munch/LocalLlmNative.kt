package com.prav1525.munch

/**
 * JNI bridge to llama.cpp. The model is downloaded separately and stored in app-private storage.
 * No prompt or model data is sent to a server by this class.
 */
object LocalLlmNative {
    init {
        System.loadLibrary("munchllm")
    }

    external fun loadModel(path: String): Boolean
    external fun generate(prompt: String, maxTokens: Int = 96): String
    external fun unload()
}

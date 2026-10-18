package com.prav1525.munch

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import android.os.Handler
import android.os.Looper
import java.io.File
import java.util.concurrent.Executors

/**
 * Serializes native model load/inference away from the UI thread. If the model is unavailable,
 * callers retain the existing deterministic offline assistant instead of failing or using cloud AI.
 */
object LocalLlmService {
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var loaded = false
    @Volatile private var loading = false

    fun isLoaded(): Boolean = loaded
    fun isLoading(): Boolean = loading

    fun load(context: Context, callback: (Result<Boolean>) -> Unit = {}) {
        executor.execute {
            if (loaded) {
                main.post { callback(Result.success(true)) }
                return@execute
            }
            loading = true
            try {
                val file = LocalModelManager.modelFile(context.applicationContext)
                val ok = file.exists() && file.length() > 1_000_000_000L && LocalLlmNative.loadModel(file.absolutePath)
                loaded = ok
                main.post { callback(Result.success(ok)) }
            } catch (e: Throwable) {
                loaded = false
                main.post { callback(Result.failure(e)) }
            } finally {
                loading = false
            }
        }
    }

    fun downloadAndLoad(context: Context, onProgress: (String) -> Unit, onComplete: (Result<File>) -> Unit) {
        val appContext = context.applicationContext
        ContextCompat.startForegroundService(appContext, Intent(appContext, ModelDownloadService::class.java))
        LocalModelManager.download(appContext, { message -> main.post { onProgress(message) } }) { result ->
            result.fold(
                onSuccess = { file ->
                    main.post { onProgress("Loading model into memory…") }
                    load(context.applicationContext) { loadedResult ->
                        if (loadedResult.getOrDefault(false)) {
                            appContext.stopService(Intent(appContext, ModelDownloadService::class.java))
                            onComplete(Result.success(file))
                        } else {
                            appContext.stopService(Intent(appContext, ModelDownloadService::class.java))
                            onComplete(Result.failure(loadedResult.exceptionOrNull() ?: IllegalStateException("The model could not be loaded on this device.")))
                        }
                    }
                },
                onFailure = { error -> main.post { appContext.stopService(Intent(appContext, ModelDownloadService::class.java)); onComplete(Result.failure(error)) } }
            )
        }
    }

    fun generate(prompt: String, callback: (String) -> Unit) {
        executor.execute {
            val answer = if (loaded) runCatching {
                // Qwen3 Instruct expects ChatML markers; plain "User:/Assistant:" text
                // bypasses its instruction formatting and can lead to poor/repetitive replies.
                val safePrompt = prompt.trim().take(1200)
                val chatPrompt = "<|im_start|>system\\nYou are Munch, a concise and friendly Android voice assistant. Answer directly in one short sentence unless more detail is necessary. Do not claim to perform phone actions; Munch handles navigation, music, and reminders separately. /no_think<|im_end|>\\n" +
                    "<|im_start|>user\\n" + safePrompt + "<|im_end|>\\n<|im_start|>assistant\\n"
                LocalLlmNative.generate(chatPrompt, 40).trim().takeIf { it.isNotBlank() }
            }.getOrNull() else null
            main.post { callback(answer.orEmpty()) }
        }
    }
}

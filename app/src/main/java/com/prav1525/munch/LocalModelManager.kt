package com.prav1525.munch

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Downloads the free Qwen3 4B Q4_K_M GGUF to app-private storage.
 * The download is explicit and only starts when the user taps Download.
 */
object LocalModelManager {
    const val MODEL_NAME = "Qwen3 4B (Q4_K_M)"
    private const val MODEL_FILE = "qwen3-4b-q4_k_m.gguf"
    private const val MODEL_URL = "https://huggingface.co/Qwen/Qwen3-4B-GGUF/resolve/main/Qwen3-4B-Q4_K_M.gguf?download=true"
    private const val MIN_FREE_BYTES = 3_500_000_000L
    private val executor = Executors.newSingleThreadExecutor()

    fun modelFile(context: Context): File = File(context.filesDir, MODEL_FILE)
    fun isDownloaded(context: Context): Boolean =
        modelFile(context).let { it.exists() && it.length() > 1_000_000_000L }

    fun download(context: Context, onProgress: (String) -> Unit, onComplete: (Result<File>) -> Unit) {
        executor.execute {
            val target = modelFile(context)
            val partial = File(context.filesDir, "$MODEL_FILE.part")
            try {
                if (target.exists() && target.length() > 1_000_000_000L) {
                    onComplete(Result.success(target))
                    return@execute
                }
                if (context.filesDir.usableSpace < MIN_FREE_BYTES) {
                    throw IllegalStateException("Free at least 3.5 GB of app storage before downloading the model.")
                }
                onProgress("Connecting to Hugging Face…")
                val connection = (URL(MODEL_URL).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 20_000
                    readTimeout = 60_000
                    instanceFollowRedirects = true
                    requestMethod = "GET"
                }
                try {
                    val responseCode = connection.responseCode
                    if (responseCode !in 200..299) {
                        throw IllegalStateException("Model download failed (HTTP $responseCode).")
                    }
                    val total = connection.contentLengthLong
                    FileOutputStream(partial).use { output ->
                        connection.inputStream.use { input ->
                            val buffer = ByteArray(64 * 1024)
                            var received = 0L
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                output.write(buffer, 0, count)
                                received += count
                                if (total > 0) onProgress("Downloading model: ${(received * 100 / total).toInt()}%")
                                else onProgress("Downloaded ${received / (1024 * 1024)} MB…")
                            }
                        }
                    }
                } finally {
                    connection.disconnect()
                }
                if (partial.length() < 1_000_000_000L) {
                    throw IllegalStateException("Downloaded model file is unexpectedly small; please retry.")
                }
                if (target.exists()) target.delete()
                if (!partial.renameTo(target)) throw IllegalStateException("Could not save the model file.")
                onComplete(Result.success(target))
            } catch (error: Exception) {
                partial.delete()
                onComplete(Result.failure(error))
            }
        }
    }
}

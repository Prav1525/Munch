package com.prav1525.munch

import android.content.Context
import android.net.Uri
import android.os.PowerManager
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Downloads Qwen3 to app-private storage. Partial downloads are preserved and
 * resumed where HTTP Range is supported. A partial wake lock keeps the CPU awake.
 */
object LocalModelManager {
    const val MODEL_NAME = "Qwen3 1.7B (Q4_K_M) — faster replies"
    private const val MODEL_FILE = "qwen3-1.7b-q4_k_m.gguf"
    private const val MODEL_URL = "https://huggingface.co/ggml-org/Qwen3-1.7B-GGUF/resolve/main/Qwen3-1.7B-Q4_K_M.gguf?download=true"
    private const val MIN_FREE_BYTES = 2_000_000_000L
    private val executor = Executors.newSingleThreadExecutor()

    fun modelFile(context: Context): File = File(context.filesDir, MODEL_FILE)
    fun isDownloaded(context: Context): Boolean =
        modelFile(context).let { it.exists() && it.length() > 700_000_000L }

    /** Copies a user-selected GGUF into app-private storage so it remains available after restart. */
    fun importModel(context: Context, uri: Uri, onProgress: (String) -> Unit, onComplete: (Result<File>) -> Unit) {
        executor.execute {
            val target = modelFile(context)
            val temporary = File(context.filesDir, "${MODEL_FILE}.importing")
            try {
                onProgress("Copying model from device storage…")
                context.contentResolver.openInputStream(uri).use { input ->
                    requireNotNull(input) { "Could not open the selected file." }
                    FileOutputStream(temporary, false).use { output ->
                        val buffer = ByteArray(128 * 1024)
                        var copied = 0L
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                            copied += count
                            if (copied > 0 && copied % (256L * 1024 * 1024) < buffer.size) {
                                onProgress("Copying model: ${copied / (1024 * 1024)} MB")
                            }
                        }
                        output.fd.sync()
                    }
                }
                require(temporary.length() > 1_000_000_000L) {
                    "That file is too small to be a complete Qwen3 1.7B GGUF model. Choose the Q4_K_M .gguf file."
                }
                if (target.exists() && !target.delete()) throw IllegalStateException("Could not replace the existing model.")
                if (!temporary.renameTo(target)) throw IllegalStateException("Could not save the imported model.")
                onComplete(Result.success(target))
            } catch (error: Exception) {
                temporary.delete()
                onComplete(Result.failure(error))
            }
        }
    }

    fun download(context: Context, onProgress: (String) -> Unit, onComplete: (Result<File>) -> Unit) {
        executor.execute {
            val target = modelFile(context)
            val partial = File(context.filesDir, "${MODEL_FILE}.part")
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            val wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Munch:ModelDownload")
            try {
                if (target.exists() && target.length() > 1_000_000_000L) {
                    onComplete(Result.success(target))
                    return@execute
                }
                if (context.filesDir.usableSpace < MIN_FREE_BYTES) {
                    throw IllegalStateException("Free at least 2 GB of app storage before downloading the model.")
                }
                wakeLock.acquire()
                onProgress("Connecting to Hugging Face…")
                var offset = if (partial.exists()) partial.length() else 0L
                var connection = (URL(MODEL_URL).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 30_000
                    readTimeout = 120_000
                    instanceFollowRedirects = true
                    if (offset > 0) setRequestProperty("Range", "bytes=" + offset + "-")
                    requestMethod = "GET"
                }
                try {
                    var responseCode = connection.responseCode
                    if (offset > 0 && responseCode == HttpURLConnection.HTTP_OK) {
                        connection.disconnect()
                        partial.delete()
                        offset = 0L
                        connection = (URL(MODEL_URL).openConnection() as HttpURLConnection).apply {
                            connectTimeout = 30_000
                            readTimeout = 120_000
                            instanceFollowRedirects = true
                            requestMethod = "GET"
                        }
                        responseCode = connection.responseCode
                    }
                    if (responseCode != HttpURLConnection.HTTP_OK &&
                        responseCode != HttpURLConnection.HTTP_PARTIAL) {
                        throw IllegalStateException("Model download failed (HTTP " + responseCode + ").")
                    }
                    if (offset > 0 && responseCode != HttpURLConnection.HTTP_PARTIAL) {
                        throw IllegalStateException("Server could not resume the model download; please retry.")
                    }
                    val segmentLength = connection.contentLengthLong
                    val expectedTotal = if (segmentLength > 0) offset + segmentLength else -1L
                    FileOutputStream(partial, offset > 0).use { output ->
                        connection.inputStream.use { input ->
                            val buffer = ByteArray(128 * 1024)
                            var received = offset
                            var lastReported = -1
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                output.write(buffer, 0, count)
                                received += count
                                val percent = if (expectedTotal > 0) (received * 100 / expectedTotal).toInt() else -1
                                if (percent != lastReported) {
                                    lastReported = percent
                                    if (percent >= 0) onProgress("Downloading Qwen3 1.7B: " + percent + "% (" + received / (1024 * 1024) + " MB)")
                                    else onProgress("Downloading Qwen3 1.7B: " + received / (1024 * 1024) + " MB")
                                }
                            }
                            output.fd.sync()
                        }
                    }
                } finally {
                    connection.disconnect()
                }
                if (partial.length() < 700_000_000L) {
                    throw IllegalStateException("Download is incomplete (" + partial.length() / (1024 * 1024) + " MB). Retry to resume it.")
                }
                if (target.exists()) target.delete()
                if (!partial.renameTo(target)) throw IllegalStateException("Could not save the model file.")
                onComplete(Result.success(target))
            } catch (error: Exception) {
                // Keep the partial file so a retry can resume instead of downloading from zero.
                onComplete(Result.failure(error))
            } finally {
                if (wakeLock.isHeld) wakeLock.release()
            }
        }
    }
}

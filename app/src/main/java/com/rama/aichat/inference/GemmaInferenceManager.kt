package com.rama.aichat.inference

import android.content.Context
import android.graphics.Bitmap
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.genai.llminference.GraphOptions
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import com.rama.aichat.data.model.ChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class GemmaInferenceManager(
    private val context: Context
) {
    companion object {
        private const val MODEL_FILE_NAME = "gemma-4-E4B-it.litertlm"
        private const val MODEL_URL =
            "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/resolve/main/gemma-4-E4B-it.litertlm"
        private const val MIN_MODEL_BYTES = 3_000_000_000L
        private const val MAX_TOKENS = 1024
        private const val TOP_K = 40
        private const val TEMPERATURE = 0.8f
        private const val MAX_NUM_IMAGES = 1
    }

    sealed interface LoadState {
        data object Idle : LoadState
        data class Downloading(val progress: Float?) : LoadState
        data object Initializing : LoadState
        data object Ready : LoadState
    }

    private val _loadState = MutableStateFlow<LoadState>(LoadState.Idle)
    val loadState: StateFlow<LoadState> = _loadState.asStateFlow()

    @Volatile
    private var llmInference: LlmInference? = null

    @Volatile
    private var session: LlmInferenceSession? = null

    @Volatile
    private var conversationHistory: List<ChatMessage> = emptyList()

    suspend fun initialize() = withContext(Dispatchers.IO) {
        if (llmInference != null) return@withContext

        val modelFile = ensureModelFile()
        _loadState.value = LoadState.Initializing

        val options = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(modelFile.absolutePath)
            .setMaxTokens(MAX_TOKENS)
            .setMaxTopK(TOP_K)
            .setMaxNumImages(MAX_NUM_IMAGES)
            .build()
        llmInference = LlmInference.createFromOptions(context, options)
        createSession()
        _loadState.value = LoadState.Ready
    }

    fun resetConversation(history: List<ChatMessage>) {
        conversationHistory = history
        createSession()
    }

    fun generateResponse(prompt: String, image: Bitmap? = null): Flow<String> = callbackFlow {
        val activeSession = checkNotNull(session) {
            "Conversation is not ready. Call resetConversation() first."
        }
        val formattedPrompt = buildPrompt(conversationHistory, prompt)
        activeSession.addQueryChunk(formattedPrompt)
        if (image != null) {
            activeSession.addImage(BitmapImageBuilder(image).build())
        }
        activeSession.generateResponseAsync { partialResult, done ->
            partialResult?.let { trySend(it) }
            if (done) close()
        }
        awaitClose()
    }

    fun close() {
        session?.close()
        session = null
        llmInference?.close()
        llmInference = null
        conversationHistory = emptyList()
        _loadState.value = LoadState.Idle
    }

    private fun createSession() {
        val inference = llmInference ?: return
        session?.close()
        session = LlmInferenceSession.createFromOptions(
            inference,
            LlmInferenceSession.LlmInferenceSessionOptions.builder()
                .setTopK(TOP_K)
                .setTemperature(TEMPERATURE)
                .setGraphOptions(
                    GraphOptions.builder()
                        .setEnableVisionModality(true)
                        .build()
                )
                .build()
        )
    }

    private suspend fun ensureModelFile(): File {
        val modelFile = File(context.filesDir, MODEL_FILE_NAME)
        if (modelFile.exists() && modelFile.length() >= MIN_MODEL_BYTES) {
            return modelFile
        }

        modelFile.delete()
        downloadModel(modelFile)
        return modelFile
    }

    private suspend fun downloadModel(dest: File) {
        _loadState.value = LoadState.Downloading(progress = 0f)

        val connection = openConnectionFollowingRedirects(MODEL_URL)

        try {
            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                throw IllegalStateException(
                    "Model download failed (HTTP $responseCode). " +
                        "You can sideload the file with: adb push $MODEL_FILE_NAME " +
                        "/data/data/${context.packageName}/files/$MODEL_FILE_NAME"
                )
            }

            val totalBytes = connection.contentLengthLong.takeIf { it > 0 }
            connection.inputStream.use { input ->
                FileOutputStream(dest).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var downloaded = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        _loadState.value = LoadState.Downloading(
                            progress = totalBytes?.let { downloaded.toFloat() / it.toFloat() }
                        )
                    }
                }
            }

            if (dest.length() < MIN_MODEL_BYTES) {
                dest.delete()
                throw IllegalStateException("Downloaded model file is incomplete.")
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun openConnectionFollowingRedirects(url: String): HttpURLConnection {
        var currentUrl = url
        repeat(10) {
            val connection = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                requestMethod = "GET"
                connectTimeout = 30_000
                readTimeout = 60_000
            }
            when (connection.responseCode) {
                HttpURLConnection.HTTP_MOVED_PERM,
                HttpURLConnection.HTTP_MOVED_TEMP,
                HttpURLConnection.HTTP_SEE_OTHER,
                307, 308 -> {
                    val location = connection.getHeaderField("Location")
                        ?: throw IllegalStateException("Redirect without Location header.")
                    connection.disconnect()
                    currentUrl = if (location.startsWith("http")) {
                        location
                    } else {
                        URL(URL(currentUrl), location).toString()
                    }
                }
                else -> return connection
            }
        }
        throw IllegalStateException("Too many redirects while downloading model.")
    }

    private fun buildPrompt(history: List<ChatMessage>, userMessage: String): String {
        return buildString {
            history.forEach { message ->
                appendTurn(message.role, message.content)
            }
            appendTurn("user", userMessage)
            append("<|turn|>model\n")
        }
    }

    private fun StringBuilder.appendTurn(role: String, content: String) {
        val turnRole = if (role == "user") "user" else "model"
        append("<|turn|>")
        append(turnRole)
        append('\n')
        append(content)
        append('\n')
        append("<|turn|>\n")
    }
}

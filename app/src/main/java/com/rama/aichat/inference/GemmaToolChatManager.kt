package com.rama.aichat.inference

import android.graphics.Bitmap
import com.rama.aichat.appfunctions.SkillFunctionCatalog
import com.rama.aichat.data.model.ChatMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GemmaToolChatManager @Inject constructor(
    private val gemmaInferenceManager: GemmaInferenceManager,
    private val skillFunctionCatalog: SkillFunctionCatalog,
    private val gemmaInferenceLock: GemmaInferenceLock
) {
    companion object {
        private const val MAX_TOOL_CALLS_PER_TURN = 5
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = false
    }

    val loadState: StateFlow<GemmaInferenceManager.LoadState> = gemmaInferenceManager.loadState

    suspend fun initialize() {
        gemmaInferenceManager.initialize()
    }

    fun generateResponse(
        history: List<ChatMessage>,
        userMessage: String,
        image: Bitmap? = null
    ): Flow<String> = flow {
        val lockResult = gemmaInferenceLock.tryWithLock(InferenceOwner.Chat) {
            val runtimeHistory = history.toMutableList()
            var prompt = buildInitialPrompt(userMessage)

            repeat(MAX_TOOL_CALLS_PER_TURN + 1) { iteration ->
                val rawResponse = generateSingleResponse(
                    history = runtimeHistory,
                    prompt = prompt,
                    image = if (iteration == 0) image else null
                ).trim()
                if (rawResponse.isBlank()) {
                    if (iteration == MAX_TOOL_CALLS_PER_TURN) {
                        emit("I could not complete your request right now.")
                    }
                    return@tryWithLock
                }

                val toolCall = parseToolCall(rawResponse)
                if (toolCall == null) {
                    emit(rawResponse)
                    return@tryWithLock
                }

                val toolResult = runCatching {
                    skillFunctionCatalog.execute(toolCall.function, toolCall.args)
                }.fold(
                    onSuccess = { data ->
                        buildJsonObject {
                            put("function", toolCall.function)
                            put("ok", true)
                            put("data", data)
                        }
                    },
                    onFailure = { error ->
                        buildJsonObject {
                            put("function", toolCall.function)
                            put("ok", false)
                            put("error", error.message ?: "Unknown tool error")
                        }
                    }
                )

                runtimeHistory += ChatMessage().apply {
                    role = "model"
                    content = rawResponse
                }
                runtimeHistory += ChatMessage().apply {
                    role = "user"
                    content = """{"tool_result":$toolResult}"""
                }

                if (iteration == MAX_TOOL_CALLS_PER_TURN) {
                    emit("I reached the maximum number of tool calls for this message. Please refine your request.")
                    return@tryWithLock
                }

                prompt = buildFollowUpPrompt()
            }
        }

        if (lockResult.isFailure) {
            val error = lockResult.exceptionOrNull()
            if (error is InferenceBusyException) {
                emit("The model is busy with live camera analysis. Please try again shortly.")
            } else {
                throw error ?: IllegalStateException("Inference lock failed.")
            }
        }
    }

    private suspend fun generateSingleResponse(
        history: List<ChatMessage>,
        prompt: String,
        image: Bitmap?
    ): String {
        gemmaInferenceManager.resetConversation(history)
        val full = StringBuilder()
        gemmaInferenceManager.generateResponse(prompt, image).collect { chunk ->
            full.append(chunk)
        }
        return full.toString()
    }

    private fun buildInitialPrompt(userMessage: String): String {
        val availableFunctionsJson = skillFunctionCatalog.buildSpecsJson()
        return buildString {
            appendLine("You are AiChat assistant with callable app functions.")
            appendLine("When a function is needed, return STRICT JSON only with this exact schema:")
            appendLine("""{"function":"functionName","args":{"param":"value"}}""")
            appendLine("Do not include markdown, code fences, or extra keys when calling a function.")
            appendLine("If no function is needed, reply with normal helpful text.")
            appendLine("Available functions:")
            appendLine(availableFunctionsJson)
            appendLine()
            appendLine("User request:")
            append(userMessage)
        }
    }

    private fun buildFollowUpPrompt(): String = buildString {
        appendLine("A tool_result message was provided in conversation history.")
        appendLine("If another function call is needed, return STRICT JSON only:")
        appendLine("""{"function":"functionName","args":{"param":"value"}}""")
        appendLine("Otherwise provide the final user-facing answer in plain text.")
    }

    private fun parseToolCall(rawResponse: String): ToolCall? {
        val parsed = runCatching {
            json.parseToJsonElement(rawResponse).jsonObject
        }.getOrNull() ?: return null

        val functionName = parsed["function"]?.jsonPrimitive?.content?.trim()
        val args = parsed["args"]?.jsonObject
        if (functionName.isNullOrEmpty() || args == null) return null
        return ToolCall(function = functionName, args = args)
    }

    private data class ToolCall(
        val function: String,
        val args: JsonObject
    )
}

package com.rama.aichat.inference

import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.FunctionCallPart
import com.google.firebase.ai.type.FunctionDeclaration
import com.google.firebase.ai.type.FunctionResponsePart
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.Tool
import com.google.firebase.ai.type.content
import com.rama.aichat.data.model.ChatMessage
import com.rama.aichat.data.repository.SkillRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeminiChatManager @Inject constructor(
    private val skillRepository: SkillRepository
) {
    private val skillTools = listOf(
        Tool.functionDeclarations(
            listOf(
                FunctionDeclaration(
                    name = "listSkills",
                    description = "Lists all skill files available in the app. Returns file names, display names, and short previews.",
                    parameters = emptyMap()
                ),
                FunctionDeclaration(
                    name = "getSkillContent",
                    description = "Gets the full markdown content of a skill file. Call listSkills first to get valid file names.",
                    parameters = mapOf(
                        "fileName" to Schema.string(
                            "The skill file name including the .md extension, e.g. my-skill.md"
                        )
                    )
                ),
                FunctionDeclaration(
                    name = "createSkill",
                    description = "Creates a new skill file with the given name and markdown content.",
                    parameters = mapOf(
                        "name" to Schema.string(
                            "Skill name using only letters, numbers, hyphens, or underscores — no spaces or .md extension"
                        ),
                        "content" to Schema.string("The full markdown content of the skill")
                    )
                ),
                FunctionDeclaration(
                    name = "updateSkill",
                    description = "Replaces the content of an existing skill file. Call listSkills first to get valid file names.",
                    parameters = mapOf(
                        "fileName" to Schema.string(
                            "The skill file name including the .md extension"
                        ),
                        "content" to Schema.string("The new markdown content to replace the existing content")
                    )
                ),
                FunctionDeclaration(
                    name = "deleteSkill",
                    description = "Permanently deletes a skill file. This is irreversible. Always confirm with the user before calling this.",
                    parameters = mapOf(
                        "fileName" to Schema.string(
                            "The skill file name including the .md extension"
                        )
                    )
                )
            )
        )
    )

    private val model by lazy {
        Firebase.ai(backend = GenerativeBackend.googleAI())
            .generativeModel(
                modelName = "gemini-2.0-flash",
                systemInstruction = content {
                    text(
                        """You are a helpful AI assistant inside the AiChat app.
The app lets users create and manage skill files — markdown documents that define instructions or context for the AI.
You have tools to list, read, create, update, and delete skill files on the user's behalf.
Always confirm with the user before deleting a skill.
When creating or updating skills, write clear, well-structured markdown."""
                    )
                },
                tools = skillTools
            )
    }

    /**
     * Sends [userMessage] to Gemini, resolves any skill tool calls in a loop,
     * then emits the final natural-language response text.
     */
    fun generateResponse(history: List<ChatMessage>, userMessage: String): Flow<String> = flow {
        val chat = model.startChat(buildGeminiHistory(history))
        var response = chat.sendMessage(userMessage)

        // Resolve all function calls before producing a final answer
        while (response.functionCalls.isNotEmpty()) {
            val responseParts = response.functionCalls.map { fc ->
                FunctionResponsePart(fc.name, executeFunction(fc), fc.id)
            }
            response = chat.sendMessage(content("function") {
                responseParts.forEach { part(it) }
            })
        }

        val text = response.text
        if (!text.isNullOrBlank()) emit(text)
    }

    private fun buildGeminiHistory(messages: List<ChatMessage>): List<Content> =
        messages.map { msg ->
            content(role = if (msg.role == "user") "user" else "model") {
                text(msg.content)
            }
        }

    private suspend fun executeFunction(fc: FunctionCallPart): JsonObject =
        withContext(Dispatchers.IO) {
            try {
                when (fc.name) {
                    "listSkills" -> {
                        val skills = skillRepository.getAllSkills()
                        buildJsonObject {
                            put(
                                "skills",
                                JsonArray(
                                    skills.map { skill ->
                                        buildJsonObject {
                                            put("fileName", skill.fileName)
                                            put("displayName", skill.displayName)
                                            put("preview", skill.preview)
                                        }
                                    }
                                )
                            )
                        }
                    }

                    "getSkillContent" -> {
                        val fileName = fc.args["fileName"]?.jsonPrimitive?.content
                            ?: return@withContext errorJson("fileName parameter is required")
                        val fileContent = skillRepository.getSkillContent(fileName)
                        buildJsonObject {
                            put("fileName", fileName)
                            put("content", fileContent)
                        }
                    }

                    "createSkill" -> {
                        val name = fc.args["name"]?.jsonPrimitive?.content
                            ?: return@withContext errorJson("name parameter is required")
                        val fileContent = fc.args["content"]?.jsonPrimitive?.content
                            ?: return@withContext errorJson("content parameter is required")
                        val fileName = skillRepository.createSkill(name, fileContent)
                        buildJsonObject {
                            put("success", true)
                            put("fileName", fileName)
                            put("message", "Skill '$name' created successfully")
                        }
                    }

                    "updateSkill" -> {
                        val fileName = fc.args["fileName"]?.jsonPrimitive?.content
                            ?: return@withContext errorJson("fileName parameter is required")
                        val fileContent = fc.args["content"]?.jsonPrimitive?.content
                            ?: return@withContext errorJson("content parameter is required")
                        skillRepository.updateSkill(fileName, fileContent)
                        buildJsonObject {
                            put("success", true)
                            put("fileName", fileName)
                            put("message", "Skill '$fileName' updated successfully")
                        }
                    }

                    "deleteSkill" -> {
                        val fileName = fc.args["fileName"]?.jsonPrimitive?.content
                            ?: return@withContext errorJson("fileName parameter is required")
                        skillRepository.deleteSkill(fileName)
                        buildJsonObject {
                            put("success", true)
                            put("message", "Skill '$fileName' deleted successfully")
                        }
                    }

                    else -> errorJson("Unknown function: ${fc.name}")
                }
            } catch (e: Exception) {
                errorJson(e.message ?: "Unknown error")
            }
        }

    private fun errorJson(message: String): JsonObject =
        buildJsonObject { put("error", message) }
}

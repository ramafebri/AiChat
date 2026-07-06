package com.rama.aichat.appfunctions

import com.rama.aichat.data.repository.SkillRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

data class AppFunctionParameterSpec(
    val name: String,
    val description: String
)

data class AppFunctionSpec(
    val name: String,
    val description: String,
    val parameters: List<AppFunctionParameterSpec>
)

/**
 * SkillFunctions-focused inventory + dispatcher used by Gemma function-calling.
 */
@Singleton
class SkillFunctionCatalog @Inject constructor(
    private val skillRepository: SkillRepository
) {
    fun listAllFunctions(): List<AppFunctionSpec> = listOf(
        AppFunctionSpec(
            name = "listSkills",
            description = "Lists all skill files in the app.",
            parameters = emptyList()
        ),
        AppFunctionSpec(
            name = "getSkillContent",
            description = "Gets full markdown content for a skill file.",
            parameters = listOf(
                AppFunctionParameterSpec(
                    name = "fileName",
                    description = "Skill file name including .md extension."
                )
            )
        ),
        AppFunctionSpec(
            name = "createSkill",
            description = "Creates a new skill file.",
            parameters = listOf(
                AppFunctionParameterSpec(
                    name = "name",
                    description = "Skill name without extension."
                ),
                AppFunctionParameterSpec(
                    name = "content",
                    description = "Markdown content for the skill."
                )
            )
        ),
        AppFunctionSpec(
            name = "updateSkill",
            description = "Updates markdown content of an existing skill file.",
            parameters = listOf(
                AppFunctionParameterSpec(
                    name = "fileName",
                    description = "Skill file name including .md extension."
                ),
                AppFunctionParameterSpec(
                    name = "content",
                    description = "New markdown content."
                )
            )
        ),
        AppFunctionSpec(
            name = "deleteSkill",
            description = "Deletes an existing skill file. Must be confirmed by user.",
            parameters = listOf(
                AppFunctionParameterSpec(
                    name = "fileName",
                    description = "Skill file name including .md extension."
                )
            )
        )
    )

    suspend fun execute(functionName: String, args: JsonObject): JsonObject = withContext(Dispatchers.IO) {
        when (functionName) {
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
                                    put("lastModified", JsonPrimitive(skill.lastModified))
                                    put("preview", skill.preview)
                                }
                            }
                        )
                    )
                }
            }

            "getSkillContent" -> {
                val fileName = args.requiredString("fileName")
                val content = skillRepository.getSkillContent(fileName)
                buildJsonObject {
                    put("fileName", fileName)
                    put("content", content)
                }
            }

            "createSkill" -> {
                val name = args.requiredString("name")
                val content = args.requiredString("content")
                val fileName = skillRepository.createSkill(name, content)
                buildJsonObject {
                    put("success", true)
                    put("fileName", fileName)
                    put("message", "Skill '$name' created successfully")
                }
            }

            "updateSkill" -> {
                val fileName = args.requiredString("fileName")
                val content = args.requiredString("content")
                skillRepository.updateSkill(fileName, content)
                buildJsonObject {
                    put("success", true)
                    put("fileName", fileName)
                    put("message", "Skill '$fileName' updated successfully")
                }
            }

            "deleteSkill" -> {
                val fileName = args.requiredString("fileName")
                skillRepository.deleteSkill(fileName)
                buildJsonObject {
                    put("success", true)
                    put("message", "Skill '$fileName' deleted successfully")
                }
            }

            else -> throw IllegalArgumentException("Unknown function: $functionName")
        }
    }

    fun buildSpecsJson(): String = buildJsonObject {
        put(
            "functions",
            JsonArray(
                listAllFunctions().map { function ->
                    buildJsonObject {
                        put("name", function.name)
                        put("description", function.description)
                        put(
                            "parameters",
                            JsonArray(
                                function.parameters.map { param ->
                                    buildJsonObject {
                                        put("name", param.name)
                                        put("description", param.description)
                                    }
                                }
                            )
                        )
                    }
                }
            )
        )
    }.toString()

    private fun JsonObject.requiredString(key: String): String {
        val value = this[key]?.jsonPrimitive?.content?.trim()
        if (value.isNullOrEmpty()) {
            throw IllegalArgumentException("$key parameter is required")
        }
        return value
    }
}

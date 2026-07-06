package com.rama.aichat.appfunctions

import androidx.appfunctions.AppFunctionContext
import androidx.appfunctions.AppFunctionElementNotFoundException
import androidx.appfunctions.AppFunctionInvalidArgumentException
import androidx.appfunctions.service.AppFunction
import com.rama.aichat.data.model.SkillFile
import com.rama.aichat.data.repository.SkillException
import com.rama.aichat.data.repository.SkillRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Exposes [SkillRepository] operations as AppFunctions so that system agents
 * and authorized assistants (e.g. Google Gemini) can manage skills on behalf
 * of the user using natural language.
 */
class SkillFunctions @Inject constructor(
    private val skillRepository: SkillRepository
) {

    /**
     * Lists all skills available in the app.
     *
     * @param appFunctionContext The execution context provided by the system.
     * @return A list of [SkillSummary] objects, or null if no skills exist yet.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun listSkills(appFunctionContext: AppFunctionContext): List<SkillSummary>? =
        withContext(Dispatchers.IO) {
            val skills = skillRepository.getAllSkills()
            skills.ifEmpty { null }?.map { it.toSummary() }
        }

    /**
     * Reads the full markdown content of a skill.
     * Required workflow: call [listSkills] first to obtain a valid [fileName].
     *
     * @param appFunctionContext The execution context provided by the system.
     * @param fileName The file name of the skill including the .md extension (e.g. "my-skill.md").
     * @return A [SkillContent] containing the file name and complete content.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun getSkillContent(
        appFunctionContext: AppFunctionContext,
        fileName: String
    ): SkillContent = withContext(Dispatchers.IO) {
        try {
            val content = skillRepository.getSkillContent(fileName)
            SkillContent(fileName = fileName, content = content)
        } catch (e: SkillException) {
            throw e.toAppFunctionException()
        }
    }

    /**
     * Creates a new skill with the given name and markdown content.
     *
     * @param appFunctionContext The execution context provided by the system.
     * @param name The skill name (letters, numbers, hyphens, and underscores only; no extension needed).
     * @param content The markdown content of the skill.
     * @return A [SkillSummary] for the newly created skill.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun createSkill(
        appFunctionContext: AppFunctionContext,
        name: String,
        content: String
    ): SkillSummary = withContext(Dispatchers.IO) {
        try {
            val fileName = skillRepository.createSkill(name, content)
            skillRepository.getAllSkills()
                .firstOrNull { it.fileName == fileName }
                ?.toSummary()
                ?: SkillSummary(
                    fileName = fileName,
                    displayName = fileName.removeSuffix(".md"),
                    lastModified = System.currentTimeMillis(),
                    preview = content.take(100).replace('\n', ' ')
                )
        } catch (e: SkillException) {
            throw e.toAppFunctionException()
        }
    }

    /**
     * Updates the content of an existing skill.
     * Required workflow: call [listSkills] first to obtain a valid [fileName].
     *
     * @param appFunctionContext The execution context provided by the system.
     * @param fileName The file name of the skill to update, including the .md extension.
     * @param content The new markdown content to replace the existing content.
     * @return A [SkillSummary] reflecting the updated skill.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun updateSkill(
        appFunctionContext: AppFunctionContext,
        fileName: String,
        content: String
    ): SkillSummary = withContext(Dispatchers.IO) {
        try {
            skillRepository.updateSkill(fileName, content)
            skillRepository.getAllSkills()
                .firstOrNull { it.fileName == fileName }
                ?.toSummary()
                ?: SkillSummary(
                    fileName = fileName,
                    displayName = fileName.removeSuffix(".md"),
                    lastModified = System.currentTimeMillis(),
                    preview = content.take(100).replace('\n', ' ')
                )
        } catch (e: SkillException) {
            throw e.toAppFunctionException()
        }
    }

    /**
     * Permanently deletes a skill. This action is irreversible.
     * IMPORTANT: Always confirm with the user before invoking this function.
     * Required workflow: call [listSkills] first to obtain a valid [fileName].
     *
     * @param appFunctionContext The execution context provided by the system.
     * @param fileName The file name of the skill to delete, including the .md extension.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun deleteSkill(
        appFunctionContext: AppFunctionContext,
        fileName: String
    ): Unit = withContext(Dispatchers.IO) {
        try {
            skillRepository.deleteSkill(fileName)
        } catch (e: SkillException) {
            throw e.toAppFunctionException()
        }
    }

    private fun SkillFile.toSummary() = SkillSummary(
        fileName = fileName,
        displayName = displayName,
        lastModified = lastModified,
        preview = preview
    )

    private fun SkillException.toAppFunctionException() =
        if (message == "Skill not found") {
            AppFunctionElementNotFoundException(message ?: "Skill not found")
        } else {
            AppFunctionInvalidArgumentException(message ?: "Invalid argument")
        }
}

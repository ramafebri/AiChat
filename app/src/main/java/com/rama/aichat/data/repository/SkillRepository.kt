package com.rama.aichat.data.repository

import android.content.Context
import com.rama.aichat.data.model.SkillFile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

class SkillException(message: String) : Exception(message)

@Singleton
class SkillRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val skillDir: File
        get() = File(context.filesDir, SKILL_DIR).also { it.mkdirs() }

    suspend fun getAllSkills(): List<SkillFile> = withContext(Dispatchers.IO) {
        skillDir.listFiles()
            ?.filter { it.isFile && it.extension.equals("md", ignoreCase = true) }
            ?.map { file ->
                val content = file.readText()
                SkillFile(
                    fileName = file.name,
                    displayName = file.nameWithoutExtension,
                    lastModified = file.lastModified(),
                    preview = content.take(PREVIEW_LENGTH).replace('\n', ' ')
                )
            }
            ?.sortedByDescending { it.lastModified }
            ?: emptyList()
    }

    suspend fun getSkillContent(fileName: String): String = withContext(Dispatchers.IO) {
        val file = resolveExistingFile(fileName)
        file.readText()
    }

    suspend fun createSkill(name: String, content: String): String = withContext(Dispatchers.IO) {
        val fileName = normalizeFileName(name)
        val file = File(skillDir, fileName)
        if (file.exists()) {
            throw SkillException("A skill with this name already exists")
        }
        file.writeText(content)
        fileName
    }

    suspend fun updateSkill(fileName: String, content: String) = withContext(Dispatchers.IO) {
        val file = resolveExistingFile(fileName)
        file.writeText(content)
    }

    suspend fun deleteSkill(fileName: String) = withContext(Dispatchers.IO) {
        val file = resolveExistingFile(fileName)
        if (!file.delete()) {
            throw SkillException("Failed to delete skill")
        }
    }

    private fun resolveExistingFile(fileName: String): File {
        val safeName = normalizeFileName(fileName)
        val file = File(skillDir, safeName)
        if (!file.exists()) {
            throw SkillException("Skill not found")
        }
        return file
    }

    private fun normalizeFileName(name: String): String {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            throw SkillException("Skill name cannot be empty")
        }
        val baseName = trimmed
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .removeSuffix(".md")
            .removeSuffix(".MD")
        if (baseName.isEmpty() || !VALID_NAME_REGEX.matches(baseName)) {
            throw SkillException("Skill name may only contain letters, numbers, hyphens, and underscores")
        }
        return "$baseName.md"
    }

    companion object {
        private const val SKILL_DIR = "skill"
        private const val PREVIEW_LENGTH = 100
        private val VALID_NAME_REGEX = Regex("^[a-zA-Z0-9_-]+$")
    }
}

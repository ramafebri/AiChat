package com.rama.aichat.appfunctions

import androidx.appfunctions.AppFunctionSerializable

/**
 * Lightweight summary of a skill file returned when listing skills.
 */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class SkillSummary(
    /** The file name of the skill including the .md extension (e.g. "my-skill.md"). */
    val fileName: String,
    /** The human-readable display name without the file extension. */
    val displayName: String,
    /** The Unix epoch timestamp (milliseconds) when the skill was last modified. */
    val lastModified: Long,
    /** A short preview of the skill content, truncated to 100 characters. */
    val preview: String
)

/**
 * Full content of a skill file.
 */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class SkillContent(
    /** The file name of the skill including the .md extension (e.g. "my-skill.md"). */
    val fileName: String,
    /** The complete markdown content of the skill. */
    val content: String
)

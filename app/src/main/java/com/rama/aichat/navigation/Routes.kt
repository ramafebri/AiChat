package com.rama.aichat.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
data object HomeRoute : NavKey

@Serializable
data object SkillListRoute : NavKey

@Serializable
data class SkillEditRoute(val fileName: String? = null) : NavKey

@Serializable
data object GemmaChatRoute : NavKey

@Serializable
data object LiveCameraAnalyzerRoute : NavKey

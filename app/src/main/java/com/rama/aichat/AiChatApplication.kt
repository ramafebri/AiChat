package com.rama.aichat

import android.app.Application
import androidx.appfunctions.service.AppFunctionConfiguration
import com.rama.aichat.appfunctions.SkillFunctions
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class AiChatApplication : Application(), AppFunctionConfiguration.Provider {

    @Inject lateinit var skillFunctions: SkillFunctions

    override val appFunctionConfiguration: AppFunctionConfiguration
        get() = AppFunctionConfiguration.Builder()
            .addEnclosingClassFactory(SkillFunctions::class.java) { skillFunctions }
            .build()
}

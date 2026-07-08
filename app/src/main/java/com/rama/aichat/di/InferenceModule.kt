package com.rama.aichat.di

import android.content.Context
import com.rama.aichat.appfunctions.SkillFunctionCatalog
import com.rama.aichat.inference.GemmaInferenceLock
import com.rama.aichat.inference.GemmaInferenceManager
import com.rama.aichat.inference.GemmaToolChatManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object InferenceModule {

    @Provides
    @Singleton
    fun provideGemmaInferenceLock(): GemmaInferenceLock = GemmaInferenceLock()

    @Provides
    @Singleton
    fun provideGemmaInferenceManager(
        @ApplicationContext context: Context
    ): GemmaInferenceManager = GemmaInferenceManager(context)

    @Provides
    @Singleton
    fun provideGemmaToolChatManager(
        gemmaInferenceManager: GemmaInferenceManager,
        skillFunctionCatalog: SkillFunctionCatalog,
        gemmaInferenceLock: GemmaInferenceLock
    ): GemmaToolChatManager = GemmaToolChatManager(
        gemmaInferenceManager,
        skillFunctionCatalog,
        gemmaInferenceLock
    )
}

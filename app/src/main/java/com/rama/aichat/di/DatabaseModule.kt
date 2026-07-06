package com.rama.aichat.di

import com.rama.aichat.data.model.ChatMessage
import com.rama.aichat.data.model.ChatSession
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration
import kotlinx.coroutines.runBlocking
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideRealm(): Realm = runBlocking {
        Realm.open(
            RealmConfiguration.create(
                schema = setOf(ChatSession::class, ChatMessage::class)
            )
        )
    }
}

package com.rama.aichat.data.repository

import com.rama.aichat.data.model.ChatMessage
import com.rama.aichat.data.model.ChatSession
import io.realm.kotlin.Realm
import io.realm.kotlin.ext.query
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.mongodb.kbson.ObjectId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepository @Inject constructor(
    private val realm: Realm
) {
    fun getAllSessions(): Flow<List<ChatSession>> =
        realm.query<ChatSession>()
            .asFlow()
            .map { results ->
                results.list.sortedByDescending { it.createdAt }
            }

    fun getSessionMessages(sessionId: String): Flow<List<ChatMessage>> =
        realm.query<ChatSession>("id == $0", sessionId)
            .asFlow()
            .map { results ->
                results.list.firstOrNull()?.messages?.toList() ?: emptyList()
            }

    suspend fun createSession(title: String): String {
        val session = ChatSession().apply { this.title = title }
        realm.write { copyToRealm(session) }
        return session.id
    }

    suspend fun addMessage(sessionId: String, content: String, role: String) {
        realm.write {
            val session = query<ChatSession>("id == $0", sessionId).first().find()
            session?.messages?.add(
                ChatMessage().apply {
                    this.id = ObjectId().toHexString()
                    this.content = content
                    this.role = role
                    this.timestamp = System.currentTimeMillis()
                }
            )
            session?.updatedAt = System.currentTimeMillis()
        }
    }

    suspend fun updateSessionTitle(sessionId: String, title: String) {
        realm.write {
            val session = query<ChatSession>("id == $0", sessionId).first().find()
            session?.title = title
            session?.updatedAt = System.currentTimeMillis()
        }
    }

    suspend fun deleteSession(sessionId: String) {
        realm.write {
            val session = query<ChatSession>("id == $0", sessionId).first().find()
            session?.let { delete(it) }
        }
    }
}

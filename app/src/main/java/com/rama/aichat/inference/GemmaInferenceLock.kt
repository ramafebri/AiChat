package com.rama.aichat.inference

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

enum class InferenceOwner {
    Chat,
    LiveAnalyzer
}

class InferenceBusyException(
    val holder: InferenceOwner
) : Exception("Gemma inference is in use by $holder.")

@Singleton
class GemmaInferenceLock @Inject constructor() {
    private val mutex = Mutex()

    private val _currentOwner = MutableStateFlow<InferenceOwner?>(null)
    val currentOwner: StateFlow<InferenceOwner?> = _currentOwner.asStateFlow()

    fun isIdle(): Boolean = !mutex.isLocked

    suspend fun <T> withLock(owner: InferenceOwner, block: suspend () -> T): T {
        return mutex.withLock {
            _currentOwner.value = owner
            try {
                block()
            } finally {
                _currentOwner.value = null
            }
        }
    }

    suspend fun <T> tryWithLock(owner: InferenceOwner, block: suspend () -> T): Result<T> {
        if (!mutex.tryLock()) {
            val holder = _currentOwner.value ?: InferenceOwner.Chat
            return Result.failure(InferenceBusyException(holder))
        }
        return try {
            _currentOwner.value = owner
            Result.success(block())
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            _currentOwner.value = null
            mutex.unlock()
        }
    }
}

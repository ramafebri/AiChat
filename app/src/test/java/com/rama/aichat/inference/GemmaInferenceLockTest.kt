package com.rama.aichat.inference

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GemmaInferenceLockTest {

    @Test
    fun tryWithLock_rejectsSecondOwnerWhileHeld() = runTest {
        val lock = GemmaInferenceLock()
        val holdStarted = CompletableDeferred<Unit>()

        val firstJob = launch {
            lock.tryWithLock(InferenceOwner.Chat) {
                holdStarted.complete(Unit)
                delay(Long.MAX_VALUE)
            }
        }

        holdStarted.await()
        val second = lock.tryWithLock(InferenceOwner.LiveAnalyzer) { "blocked" }

        assertTrue(second.isFailure)
        assertTrue(second.exceptionOrNull() is InferenceBusyException)
        firstJob.cancel()
    }

    @Test
    fun withLock_releasesOwnerAfterCompletion() = runTest {
        val lock = GemmaInferenceLock()

        lock.withLock(InferenceOwner.Chat) {
            assertEquals(InferenceOwner.Chat, lock.currentOwner.value)
        }

        assertEquals(null, lock.currentOwner.value)
    }
}

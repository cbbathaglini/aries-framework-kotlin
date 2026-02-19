package org.hyperledger.ariesframework.util

import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

object Session {
    private val sessionIdRef = AtomicReference<String?>(null)

    fun startNewSession() {
        sessionIdRef.set(UUID.randomUUID().toString())
    }

    fun getId(): String {
        return sessionIdRef.get() ?: run {
            val id = UUID.randomUUID().toString()
            sessionIdRef.set(id)
            id
        }
    }
}

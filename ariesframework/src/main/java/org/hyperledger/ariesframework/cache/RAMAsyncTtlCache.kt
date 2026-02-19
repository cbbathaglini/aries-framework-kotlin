package org.hyperledger.ariesframework.cache

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

class RAMAsyncTtlCache<K, V>(
    private val ttlMillis: Long,
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
) {
    private data class Entry<V>(val value: V, val expiresAt: Long)

    private val store = ConcurrentHashMap<K, Entry<V>>()

    private val inFlight = ConcurrentHashMap<K, CompletableDeferred<V>>()
    private val lock = Mutex()

    fun getIfFresh(key: K): V? {
        val e = store[key] ?: return null
        return if (nowMillis() < e.expiresAt) {
            e.value
        } else {
            store.remove(key, e)
            null
        }
    }

    suspend fun getOrLoad(key: K, loader: suspend () -> V): V {
        getIfFresh(key)?.let { return it }
        val deferred = lock.withLock {
            getIfFresh(key)?.let { return it }

            inFlight[key]?.let { return@withLock it }

            val created = CompletableDeferred<V>()
            inFlight[key] = created
            created
        }

        if (!deferred.isCompleted) {
            try {
                val value = loader()
                store[key] = Entry(value, nowMillis() + ttlMillis)
                deferred.complete(value)
            } catch (t: Throwable) {
                deferred.completeExceptionally(t)
            } finally {
                inFlight.remove(key, deferred)
            }
        }

        return deferred.await()
    }

    fun invalidate(key: K) {
        store.remove(key)
    }

    fun clear() {
        store.clear()
    }
}

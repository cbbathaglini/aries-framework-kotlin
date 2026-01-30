package org.hyperledger.ariesframework.cache

import android.content.Context
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

class DiskOnlyAsyncTtlCache<K, V>(
    private val context: Context,
    private val cacheName: String,
    private val ttlMillis: Long,
    private val keyToString: (K) -> String,
    private val valueSerializer: KSerializer<V>,
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
) {
    @Serializable
    private data class DiskEntry(val valueJson: String, val expiresAt: Long)

    private val inFlight = ConcurrentHashMap<K, CompletableDeferred<V>>()
    private val lock = Mutex()

    private val dir: File by lazy {
        File(context.filesDir, "ttl_cache/$cacheName").apply { mkdirs() }
    }

    suspend fun getOrLoad(key: K, loader: suspend () -> V): V {
        readFromDiskIfFresh(key)?.let { return it }

        val deferred = lock.withLock {
            val disk = runCatching { readFromDiskIfFresh(key) }.getOrNull()
            if (disk != null) return@withLock CompletableDeferred<V>().apply { complete(disk) }

            inFlight[key]?.let { return@withLock it }

            val created = CompletableDeferred<V>()
            inFlight[key] = created
            created
        }

        if (!deferred.isCompleted) {
            try {
                val value = loader()
                val expiresAt = nowMillis() + ttlMillis

                writeToDisk(key, value, expiresAt)
                deferred.complete(value)
            } catch (t: Throwable) {
                deferred.completeExceptionally(t)
            } finally {
                inFlight.remove(key, deferred)
            }
        }

        return deferred.await()
    }

    suspend fun getIfFresh(key: K): V? = readFromDiskIfFresh(key)

    fun invalidate(key: K) {
        fileForKey(key).delete()
    }

    fun clear() {
        dir.listFiles()?.forEach { it.delete() }
    }

    suspend fun pruneExpired() = withContext(Dispatchers.IO) {
        dir.listFiles()?.forEach { f ->
            runCatching {
                val entry = json.decodeFromString(DiskEntry.serializer(), f.readText())
                if (nowMillis() >= entry.expiresAt) f.delete()
            }.onFailure {
                // corrompido
                f.delete()
            }
        }
    }

    private fun fileForKey(key: K): File {
        val raw = keyToString(key)
        val safe = sha256(raw)
        return File(dir, "$safe.json")
    }

    private suspend fun readFromDiskIfFresh(key: K): V? = withContext(Dispatchers.IO) {
        val f = fileForKey(key)
        if (!f.exists()) return@withContext null

        try {
            val entry = json.decodeFromString(DiskEntry.serializer(), f.readText())
            if (nowMillis() >= entry.expiresAt) {
                f.delete()
                return@withContext null
            }
            json.decodeFromString(valueSerializer, entry.valueJson)
        } catch (_: Throwable) {
            f.delete()
            null
        }
    }

    private suspend fun writeToDisk(key: K, value: V, expiresAt: Long) = withContext(Dispatchers.IO) {
        val f = fileForKey(key)
        val valueJson = json.encodeToString(valueSerializer, value)
        val payload = json.encodeToString(DiskEntry.serializer(), DiskEntry(valueJson, expiresAt))
        f.writeText(payload)
    }

    private fun sha256(input: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
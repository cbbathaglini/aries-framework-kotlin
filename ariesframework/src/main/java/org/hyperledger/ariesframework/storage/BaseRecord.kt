package org.hyperledger.ariesframework.storage

import kotlinx.datetime.Instant
import kotlinx.serialization.json.JsonElement
import org.hyperledger.ariesframework.Tags
import java.util.UUID

abstract class BaseRecord {
    abstract var id: String
    protected abstract var _tags: Tags?
    abstract val createdAt: Instant
    abstract var updatedAt: Instant?
    var metadata: MutableMap<String, Any> = mutableMapOf()

    abstract fun getTags(): Tags

    fun setTags(tags: Tags) {
        _tags = tags
    }

    fun addMetadata(key: String, value: Any) {
        metadata[key] = value
    }

    companion object {
        fun generateId(): String {
            return UUID.randomUUID().toString()
        }
    }
}

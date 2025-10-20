package org.hyperledger.ariesframework.storage

import kotlinx.datetime.Instant
import kotlinx.serialization.json.JsonElement
import org.hyperledger.ariesframework.Tags
import org.slf4j.LoggerFactory
import java.util.UUID

// @Serializable
abstract class BaseRecord {
    @Transient
    private val logger = LoggerFactory.getLogger(BaseRecord::class.java)

    abstract var id: String
    protected abstract var _tags: Tags?
    abstract val createdAt: Instant
    abstract var updatedAt: Instant?
    // var metadata: MutableMap<String, Any> = mutableMapOf()

    var metadata: MutableMap<String, JsonElement> = mutableMapOf()

    abstract fun getTags(): Tags

    fun setTags(tags: Tags) {
        _tags = tags
    }

//    fun addMetadata(key: String, value: Any) {
//        metadata[key] = value
//    }

    fun addMetadata(key: String, value: JsonElement) {
        metadata[key] = value
    }

    companion object {
        fun generateId(): String {
            return UUID.randomUUID().toString()
        }
    }
}

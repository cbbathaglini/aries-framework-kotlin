package org.hyperledger.ariesframework.storage

import kotlinx.datetime.Instant
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import org.hyperledger.ariesframework.Tags
import org.slf4j.LoggerFactory
import java.util.UUID

@Serializable
abstract class BaseRecord {
    abstract var id: String
    protected abstract var _tags: Tags?
    abstract val createdAt: Instant
    abstract var updatedAt: Instant?
    // var metadata: MutableMap<String, Any> = mutableMapOf()

    @EncodeDefault
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

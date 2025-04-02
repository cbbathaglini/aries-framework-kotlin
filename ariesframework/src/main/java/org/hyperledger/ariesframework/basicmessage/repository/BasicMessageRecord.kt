package org.hyperledger.ariesframework.basicmessage.repository

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.Serializable
import org.hyperledger.ariesframework.Tags
import org.hyperledger.ariesframework.connection.repository.ConnectionRecord
import org.hyperledger.ariesframework.storage.BaseRecord

@Serializable
data class BasicMessageRecord(
    @EncodeDefault
    override var id: String = generateId(),
    override var _tags: Tags? = null,
    @EncodeDefault
    override val createdAt: Instant = Clock.System.now(),
    override var updatedAt: Instant? = null,
    var content: String,
    var connectionRecord: ConnectionRecord?,
) : BaseRecord() {
    override fun getTags(): Tags {
        val tags = (_tags ?: mutableMapOf()).toMutableMap()

        connectionRecord?.let { tags["connectionRecordId"] = connectionRecord!!.id }

        return tags
    }

    override fun toString(): String {
        return "BasicMessageRecord(id=\"$id\", createdAt=$createdAt, content=\"$content\" connectionRecord.id=\"${connectionRecord?.id}\"))"
    }
}

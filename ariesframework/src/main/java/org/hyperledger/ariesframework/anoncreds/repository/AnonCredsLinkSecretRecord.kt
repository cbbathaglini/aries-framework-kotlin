package org.hyperledger.ariesframework.anoncreds.repository

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.hyperledger.ariesframework.Tags
import org.hyperledger.ariesframework.storage.BaseRecord

@Serializable
class AnonCredsLinkSecretRecord (
    override var id: String,
    override var _tags: Tags? = null,
    override val createdAt: Instant,
    override var updatedAt: Instant?,
    val linkSecretId : String,
    val value : String? = null
): BaseRecord() {

    companion object {
        const val type = "AnonCredsLinkSecretRecord"
    }

    constructor(
        tags: Tags? = null,
        linkSecretId: String,
        value: String? = null
    ) : this(
        id = BaseRecord.generateId(),
        _tags = tags,
        createdAt = Clock.System.now(),
        updatedAt = null,
        value = value,
        linkSecretId = linkSecretId,
    ) {
        val tagMap = (tags ?: mutableMapOf()).toMutableMap()
        _tags = tagMap
    }


    override fun getTags(): Tags {
        val tags = (_tags ?: mutableMapOf()).toMutableMap()
        tags["linkSecretId"] = linkSecretId
        return tags
    }
}
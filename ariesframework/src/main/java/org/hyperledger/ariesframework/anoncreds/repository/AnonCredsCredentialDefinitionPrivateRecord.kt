package org.hyperledger.ariesframework.anoncreds.repository

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.hyperledger.ariesframework.Tags
import org.hyperledger.ariesframework.storage.BaseRecord

@Serializable
class AnonCredsCredentialDefinitionPrivateRecord(
    override var id: String,
    override var _tags: Tags?,
    override val createdAt: Instant,
    override var updatedAt: Instant?,
    val credentialDefinitionId : String,
    val value : Map<String, @Contextual Any>
) : BaseRecord() {

    companion object {
        const val type = "AnonCredsCredentialDefinitionPrivateRecord"
    }

    constructor(
        tags: Tags? = null,
        credentialDefinitionId: String,
        value: Map<String, Any>
    ) : this(
        id = BaseRecord.generateId(),
        _tags = tags,
        createdAt = Clock.System.now(),
        updatedAt = null,
        value = value,
        credentialDefinitionId = credentialDefinitionId,
    ) {
        val tagMap = (tags ?: mutableMapOf()).toMutableMap()
        _tags = tagMap
    }


    override fun getTags(): Tags {
        val tags = (_tags ?: mutableMapOf()).toMutableMap()
        tags["credentialDefinitionId"] = credentialDefinitionId
        return tags
    }
}
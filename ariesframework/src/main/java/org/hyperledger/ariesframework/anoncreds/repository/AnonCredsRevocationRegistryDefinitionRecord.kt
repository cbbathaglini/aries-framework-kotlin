package org.hyperledger.ariesframework.anoncreds.repository

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import org.hyperledger.ariesframework.Tags
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsRevocationRegistryDefinition
import org.hyperledger.ariesframework.storage.BaseRecord

@Serializable
class AnonCredsRevocationRegistryDefinitionRecord (
    override var id: String,
    override var _tags: Tags?,
    override val createdAt: Instant,
    override var updatedAt: Instant?,
    val revocationRegistryDefinitionId: String,
    val revocationRegistryDefinition: AnonCredsRevocationRegistryDefinition
) : BaseRecord(){
    companion object {
        const val type = "AnonCredsRevocationRegistryDefinitionRecord"
    }

    constructor(
        tags: Tags? = null,
        revocationRegistryDefinitionId: String,
        revocationRegistryDefinition: AnonCredsRevocationRegistryDefinition
    ) : this(
        id = BaseRecord.generateId(),
        _tags = tags,
        createdAt = Clock.System.now(),
        updatedAt = null,
        revocationRegistryDefinitionId = revocationRegistryDefinitionId,
        revocationRegistryDefinition = revocationRegistryDefinition,
    ) {
        val tagMap = (tags ?: mutableMapOf()).toMutableMap()
        _tags = tagMap
    }


    override fun getTags(): Tags {
        val tags = (_tags ?: mutableMapOf()).toMutableMap()
        tags["credentialDefinitionId"] = revocationRegistryDefinition.credDefId
        tags["revocationRegistryDefinitionId"] = revocationRegistryDefinitionId
        return tags
    }
}
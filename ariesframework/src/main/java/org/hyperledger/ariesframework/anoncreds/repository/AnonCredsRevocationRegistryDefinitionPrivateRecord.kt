package org.hyperledger.ariesframework.anoncreds.repository

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.hyperledger.ariesframework.Tags
import org.hyperledger.ariesframework.storage.BaseRecord

@Serializable
class AnonCredsRevocationRegistryDefinitionPrivateRecord(
    override var id: String,
    override var _tags: Tags?,
    override val createdAt: Instant,
    override var updatedAt: Instant?,
    val revocationRegistryDefinitionId: String,
    val credentialDefinitionId: String,
    val value: Map<String, @Contextual Any>,
    var state: AnonCredsRevocationRegistryState,
) : BaseRecord() {

    companion object {
        const val type = "AnonCredsRevocationRegistryDefinitionPrivateRecord"
    }

    constructor(
        tags: Tags? = null,
        credentialDefinitionId: String,
        revocationRegistryDefinitionId: String,
        state: AnonCredsRevocationRegistryState,
        value: Map<String, Any>,
    ) : this(
        id = BaseRecord.generateId(),
        _tags = tags,
        createdAt = Clock.System.now(),
        updatedAt = null,
        revocationRegistryDefinitionId = revocationRegistryDefinitionId,
        credentialDefinitionId = credentialDefinitionId,
        value = value,
        state = state,
    ) {
        val tagMap = (tags ?: mutableMapOf()).toMutableMap()
        _tags = tagMap
    }

    override fun getTags(): Tags {
        val tags = (_tags ?: mutableMapOf()).toMutableMap()

        tags["revocationRegistryDefinitionId"] = revocationRegistryDefinitionId
        tags["credentialDefinitionId"] = credentialDefinitionId
        tags["state"] = state.name
        return tags
    }
}

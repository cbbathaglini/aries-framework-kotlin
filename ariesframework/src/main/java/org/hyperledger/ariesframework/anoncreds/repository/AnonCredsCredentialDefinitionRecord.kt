package org.hyperledger.ariesframework.anoncreds.repository

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import org.hyperledger.ariesframework.Tags
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsCredentialDefinition
import org.hyperledger.ariesframework.anoncreds.utils.Indyidentifiers
import org.hyperledger.ariesframework.storage.BaseRecord


@Serializable
class AnonCredsCredentialDefinitionRecord(
    override var id: String,
    override var _tags: Tags? = null,
    override val createdAt: Instant,
    override var updatedAt: Instant? = null,
    var credentialDefinitionId: String,
    var credentialDefinition: AnonCredsCredentialDefinition,
    var methodName: String,
) : BaseRecord() {

    companion object {
        const val type = "AnonCredsCredentialDefinitionRecord"
    }

    constructor(
        tags: Tags? = null,
        credentialDefinitionId: String,
        credentialDefinition: AnonCredsCredentialDefinition,
        methodName: String
    ) : this(
        id = BaseRecord.generateId(),
        _tags = tags,
        createdAt = Clock.System.now(),
        updatedAt = null,
        credentialDefinition = credentialDefinition,
        credentialDefinitionId = credentialDefinitionId,
        methodName = methodName
    ) {
        val tagMap = (tags ?: mutableMapOf()).toMutableMap()
        _tags = tagMap
    }

    override fun getTags(): Tags {
        val tags = (_tags ?: mutableMapOf()).toMutableMap()

        var unqualifiedCredentialDefinitionId : String?  = null
        if(Indyidentifiers.isDidIndyCredentialDefinitionId(credentialDefinitionId)){
            val (namespaceIdentifier, schemaSeqNo, tag ) = Indyidentifiers.parseIndyCredentialDefinitionId(credentialDefinitionId)
            unqualifiedCredentialDefinitionId = Indyidentifiers.getUnqualifiedCredentialDefinitionId(namespaceIdentifier, schemaSeqNo, tag)
        }

        tags["credentialDefinitionId"] = credentialDefinitionId
        tags["schemaId"] = credentialDefinition.schemaId
        tags["issuerId"] = credentialDefinition.issuerId
        tags["tag"] = credentialDefinition.tag
        tags["methodName"] = methodName
        tags["credentialDefinitionId"] = credentialDefinitionId
        tags["unqualifiedCredentialDefinitionId"] = unqualifiedCredentialDefinitionId ?: ""
        return tags
    }


}
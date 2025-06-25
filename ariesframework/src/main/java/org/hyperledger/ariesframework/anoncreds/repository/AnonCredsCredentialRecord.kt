package org.hyperledger.ariesframework.anoncreds.repository

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import org.hyperledger.ariesframework.Tags
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsCredential
import org.hyperledger.ariesframework.storage.BaseRecord

class AnonCredsCredentialRecord (
    override var id: String,
    override var _tags: Tags?,
    override val createdAt: Instant,
    override var updatedAt: Instant?,
    val credentialId : String,
    val credentialRevocationId: String? = null,
    val linkSecretId: String,
    val credencial: AnonCredsCredential,
    val methodName: String
): BaseRecord() {

    companion object {
        const val type = "AnonCredsCredentialRecord"
    }

    constructor(
        tags: Tags? = null,
        credentialId : String,
        credentialRevocationId : String?,
        linkSecretId: String,
        credencial : AnonCredsCredential,
        methodName: String
    ) : this(
        id = BaseRecord.generateId(),
        _tags = tags,
        createdAt = Clock.System.now(),
        updatedAt = null,
        credentialId = credentialId,
        credentialRevocationId = credentialRevocationId,
        linkSecretId = linkSecretId,
        credencial = credencial,
        methodName = methodName
    ) {
        val tagMap = (tags ?: mutableMapOf()).toMutableMap()
        _tags = tagMap
    }

    override fun getTags(): Tags {
        val tags = (_tags ?: mutableMapOf()).toMutableMap()
        tags["credentialDefinitionId"] = this.credencial.credDefId
        tags["schemaId"] = this.credencial.schemaId
        tags["credentialId"] = this.credentialId
        tags["credentialRevocationId"] = this.credentialRevocationId.toString()
        tags["revocationRegistryId"] = this.credencial.revRegId.toString()
        tags["linkSecretId"] = this.linkSecretId
        tags["methodName"] = this.methodName
        return tags
    }
}
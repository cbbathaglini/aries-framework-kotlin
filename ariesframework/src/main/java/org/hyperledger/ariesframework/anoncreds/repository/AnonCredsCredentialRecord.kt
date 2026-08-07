package org.hyperledger.ariesframework.anoncreds.repository

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.hyperledger.ariesframework.Tags
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsCredential
import org.hyperledger.ariesframework.storage.BaseRecord

class AnonCredsCredentialRecord(
    override var id: String,
    override var _tags: Tags?,
    override val createdAt: Instant,
    override var updatedAt: Instant?,
    val credentialId: String,
    val credentialRevocationId: String? = null,
    val linkSecretId: String,
    val credential: AnonCredsCredential,
    val methodName: String,
) : BaseRecord() {

    companion object {
        const val type = "AnonCredsCredentialRecord"
    }

    constructor(
        tags: Tags? = null,
        credentialId: String,
        credentialRevocationId: String?,
        linkSecretId: String,
        credential: AnonCredsCredential,
        methodName: String,
    ) : this(
        id = BaseRecord.generateId(),
        _tags = tags,
        createdAt = Clock.System.now(),
        updatedAt = null,
        credentialId = credentialId,
        credentialRevocationId = credentialRevocationId,
        linkSecretId = linkSecretId,
        credential = credential,
        methodName = methodName,
    ) {
        val tagMap = (tags ?: mutableMapOf()).toMutableMap()
        _tags = tagMap
    }

    override fun getTags(): Tags {
        val tags = (_tags ?: mutableMapOf()).toMutableMap()
        tags["credentialDefinitionId"] = this.credential.credDefId
        tags["schemaId"] = this.credential.schemaId
        tags["credentialId"] = this.credentialId
        tags["credentialRevocationId"] = this.credentialRevocationId.toString()
        tags["revocationRegistryId"] = this.credential.revRegId.toString()
        tags["linkSecretId"] = this.linkSecretId
        tags["methodName"] = this.methodName
        return tags
    }
}

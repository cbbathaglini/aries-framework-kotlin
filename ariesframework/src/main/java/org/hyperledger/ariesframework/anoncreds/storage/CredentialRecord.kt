package org.hyperledger.ariesframework.anoncreds.storage

import anoncreds_uniffi.Credential
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.Serializable
import org.hyperledger.ariesframework.Tags
import org.hyperledger.ariesframework.credentials.models.CredentialRole
import org.hyperledger.ariesframework.credentials.models.CredentialState
import org.hyperledger.ariesframework.credentials.repository.CredentialExchangeRecord
import org.hyperledger.ariesframework.credentials.repository.CredentialRecordBinding
import org.hyperledger.ariesframework.revocationnotification.model.RevocationNotification
import org.hyperledger.ariesframework.storage.BaseRecord

@Serializable
class CredentialRecord(
    @EncodeDefault
    override var id: String,
    override var _tags: Tags? = null,
    @EncodeDefault
    override val createdAt: Instant,
    override var updatedAt: Instant? = null,

    var credentialId: String,
    var credentialRevocationId: String? = null,
    var revocationRegistryId: String? = null,
    var linkSecretId: String,
    var credential: String,
    var schemaId: String,
    var schemaName: String,
    var schemaVersion: String,
    var schemaIssuerId: String,
    var issuerId: String,
    var credentialDefinitionId: String,
    var revocationNotification: RevocationNotification? = null,
) : BaseRecord() {
    constructor(
        tags: Tags? = null,
        credentialId: String,
        credentialRevocationId: String? = null,
        revocationRegistryId: String? = null,
        linkSecretId: String,
        credentialObject: Credential,
        schemaId: String,
        schemaName: String,
        schemaVersion: String,
        schemaIssuerId: String,
        issuerId: String,
        credentialDefinitionId: String,
        revocationNotification: RevocationNotification,
    ) : this(
        BaseRecord.generateId(),
        tags,
        Clock.System.now(),
        null,
        credentialId,
        credentialRevocationId,
        revocationRegistryId,
        linkSecretId,
        credentialObject.toJson(),
        schemaId,
        schemaName,
        schemaVersion,
        schemaIssuerId,
        issuerId,
        credentialDefinitionId,
        revocationNotification,
    ) {
        val tagMap = (tags ?: mutableMapOf()).toMutableMap()
        for ((key, value) in credentialObject.values()) {
            tagMap["attr::$key::value"] = value
            tagMap["attr::$key::marker"] = "1"
        }
        _tags = tagMap
    }

    override fun getTags(): Tags {
        val tags = (_tags ?: mutableMapOf()).toMutableMap()
        tags["credentialId"] = credentialId
        credentialRevocationId?.let { tags["credentialRevocationId"] = it }
        revocationRegistryId?.let { tags["revocationRegistryId"] = it }
        tags["linkSecretId"] = linkSecretId
        tags["schemaId"] = schemaId
        tags["schemaName"] = schemaName
        tags["schemaVersion"] = schemaVersion
        tags["schemaIssuerId"] = schemaIssuerId
        tags["issuerId"] = issuerId
        tags["credentialDefinitionId"] = credentialDefinitionId
        return tags
    }

    fun toCredentialExchangeRecord(
        connectionId: String,
        threadId: String,
        state: CredentialState,
        protocolVersion: String,
        role: CredentialRole? = null,
    ): CredentialExchangeRecord {
        return CredentialExchangeRecord(
            id = this.id,
            _tags = this._tags,
            createdAt = this.createdAt,
            updatedAt = this.updatedAt,
            connectionId = connectionId,
            threadId = threadId,
            state = state,
            protocolVersion = protocolVersion,
            credentialDefinitionId = this.credentialDefinitionId,
            revocationNotification = this.revocationNotification,
            credentials = mutableListOf(CredentialRecordBinding(credentialRecordType = "indy", credentialRecordId = this.id)),
            role = role,
        )
    }

    override fun toString(): String {
        return "CredentialRecord(id='$id', _tags=$_tags, createdAt=$createdAt, updatedAt=$updatedAt, credentialId='$credentialId', credentialRevocationId=$credentialRevocationId, revocationRegistryId=$revocationRegistryId, linkSecretId='$linkSecretId', credential='$credential', schemaId='$schemaId', schemaName='$schemaName', schemaVersion='$schemaVersion', schemaIssuerId='$schemaIssuerId', issuerId='$issuerId', credentialDefinitionId='$credentialDefinitionId', revocationNotification=$revocationNotification)"
    }

    fun parseCredential(credentialJson: String): Map<String, String> {
        if (credentialJson.isNotBlank()) {
            val jsonObject = JsonParser.parseString(credentialJson).asJsonObject

            val valuesNode: JsonObject? = jsonObject.getAsJsonObject("values")
            val result = mutableMapOf<String, String>()

            valuesNode?.entrySet()?.forEach { (key, valueElement) ->
                val rawValue = valueElement.asJsonObject.get("raw")?.asString ?: "N/A"
                result[key] = rawValue
            }

            return result
        }
        return emptyMap()
    }
}

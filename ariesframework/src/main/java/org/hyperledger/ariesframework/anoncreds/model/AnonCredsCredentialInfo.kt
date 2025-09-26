package org.hyperledger.ariesframework.anoncreds.model

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

typealias AnonCredsClaimRecord = Map<String, String>

@Serializable
class AnonCredsCredentialInfo() {
    var credentialId: String = ""
    var attributes: AnonCredsClaimRecord = emptyMap()
    var schemaId: String = ""
    var credentialDefinitionId: String = ""
    var revocationRegistryId: String? = null
    var credentialRevocationId: String? = null
    var methodName: String = ""
    var createdAt: Instant = Clock.System.now()
    var updatedAt: Instant = Clock.System.now()
    var linkSecretId: String = ""

    constructor(
        credentialId: String,
        attributes: AnonCredsClaimRecord,
        schemaId: String,
        credentialDefinitionId: String,
        revocationRegistryId: String? = null,
        credentialRevocationId: String? = null,
        methodName: String,
        createdAt: Instant = Clock.System.now(),
        updatedAt: Instant = Clock.System.now(),
        linkSecretId: String,
    ) : this() {
        this.credentialId = credentialId
        this.attributes = attributes
        this.schemaId = schemaId
        this.credentialDefinitionId = credentialDefinitionId
        this.revocationRegistryId = revocationRegistryId
        this.credentialRevocationId = credentialRevocationId
        this.methodName = methodName
        this.createdAt = createdAt
        this.updatedAt = updatedAt
        this.linkSecretId = linkSecretId
    }

    // private val logger = LoggerFactory.getLogger(AnonCredsCredentialInfo::class.java)

    fun toJsonElement(): JsonElement =
        buildJsonObject {
            put("credentialId", JsonPrimitive(credentialId))
            put("attributes", attributes.toJsonElementKotlinx() ?: JsonNull)
            put("schemaId", JsonPrimitive(schemaId))
            put("credentialDefinitionId", JsonPrimitive(credentialDefinitionId))
            put("revocationRegistryId", revocationRegistryId?.let { JsonPrimitive(it) } ?: JsonNull)
            put("credentialRevocationId", credentialRevocationId?.let { JsonPrimitive(it) } ?: JsonNull)
            put("methodName", JsonPrimitive(methodName))
            put("createdAt", JsonPrimitive(createdAt.toString()))
            put("updatedAt", JsonPrimitive(updatedAt.toString()))
            put("linkSecretId", JsonPrimitive(linkSecretId))
        }

    fun Map<String, *>.toJsonElementKotlinx(): JsonElement =
        buildJsonObject {
            for ((k, v) in this@toJsonElementKotlinx) {
                put(k, JsonPrimitive(v?.toString() ?: ""))
            }
        }

    override fun toString(): String {
        return "AnonCredsCredentialInfo(credentialId='$credentialId', attributes=$attributes, schemaId='$schemaId', credentialDefinitionId='$credentialDefinitionId', revocationRegistryId=$revocationRegistryId, credentialRevocationId=$credentialRevocationId, methodName='$methodName', createdAt=$createdAt, updatedAt=$updatedAt, linkSecretId='$linkSecretId')"
    }
}

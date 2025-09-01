package org.hyperledger.ariesframework.anoncreds.model

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.hyperledger.ariesframework.proofs.v2.ProofCommandV2
import org.slf4j.LoggerFactory

typealias AnonCredsClaimRecord = Map<String, String>

@Serializable
data class AnonCredsCredentialInfo(
    val credentialId: String,
    val attributes: AnonCredsClaimRecord,
    val schemaId: String,
    val credentialDefinitionId: String,
    val revocationRegistryId: String? = null,
    val credentialRevocationId: String? = null,
    val methodName: String,
    val createdAt: Instant = Clock.System.now(),
    val updatedAt: Instant,
    val linkSecretId: String
){

    private val logger = LoggerFactory.getLogger(AnonCredsCredentialInfo::class.java)

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

//    fun AnonCredsClaimRecord.toJsonElement(): JsonElement? {
//        try {
//            return JsonObject(
//                this.map { (k, v) -> k to JsonPrimitive(v) }.toMap()
//            )
//        }catch (e:Exception){
//            logger.error("execptionssss : ${e.message} ${e.cause}")
//        }
//
//        return null
//    }
}
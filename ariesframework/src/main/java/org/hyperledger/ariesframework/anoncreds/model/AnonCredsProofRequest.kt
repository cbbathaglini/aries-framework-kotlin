package org.hyperledger.ariesframework.anoncreds.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.hyperledger.ariesframework.anoncreds.model.holder.AnonCredsNonRevokedInterval

@Serializable
data class AnonCredsProofRequest(
    val name: String,
    val version: String,
    val nonce: String,
    @SerialName("requested_attributes")
    val requestedAttributes: Map<String, AnonCredsRequestedAttribute>,
    @SerialName("requested_predicates")
    val requestedPredicates: Map<String, AnonCredsRequestedPredicate>,
    @SerialName("non_revoked")
    val nonRevoked: AnonCredsNonRevokedInterval? = null,
    val ver: String? = null, // can be "1.0" or "2.0"
) {

    @OptIn(ExperimentalSerializationApi::class)
    fun toJson(): String {
        val json = Json {
            prettyPrint = true
            encodeDefaults = true
        }
        return json.encodeToString(kotlinx.serialization.serializer<AnonCredsProofRequest>(), this)
    }
}

@Serializable
data class AnonCredsRequestedAttribute(
    val name: String? = null,
    val names: List<String>? = null,
    val restrictions: List<AnonCredsProofRequestRestriction>? = null,
    @SerialName("non_revoked")
    val nonRevoked: AnonCredsNonRevokedInterval? = null,
)

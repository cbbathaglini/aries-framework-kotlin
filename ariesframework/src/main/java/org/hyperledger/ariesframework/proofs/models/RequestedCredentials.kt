package org.hyperledger.ariesframework.proofs.models

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
data class RequestedCredentials(
    @EncodeDefault
    @SerialName("requested_attributes")
    var requestedAttributes: MutableMap<String, RequestedAttribute> = mutableMapOf(),
    @EncodeDefault
    @SerialName("requested_predicates")
    var requestedPredicates: MutableMap<String, RequestedPredicate> = mutableMapOf(),
    @EncodeDefault
    @SerialName("self_attested_attributes")
    val selfAttestedAttributes: MutableMap<String, String> = mutableMapOf(),
) {
    fun getCredentialIdentifiers(): List<String> {
        val credIds = mutableSetOf<String>()
        for ((_, attr) in requestedAttributes) {
            credIds.add(attr.credentialId)
        }
        for ((_, pred) in requestedPredicates) {
            credIds.add(pred.credentialId)
        }
        return credIds.toList()
    }

    fun toJsonString(): String = Json.encodeToString(this)

    fun toMap(): MutableMap<String, JsonElement> {
        val root: JsonElement = Json.encodeToJsonElement(RequestedCredentials.serializer(), this)
        val obj = root as? JsonObject
            ?: error("RequestedCredentials não serializou para um objeto JSON")

        // Converte JsonObject -> MutableMap<String, JsonElement>
        val map = mutableMapOf<String, JsonElement>()
        for ((k, v) in obj) map[k] = v
        return map
    }
}

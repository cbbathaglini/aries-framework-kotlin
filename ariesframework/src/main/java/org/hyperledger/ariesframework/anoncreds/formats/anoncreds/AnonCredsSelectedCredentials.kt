package org.hyperledger.ariesframework.anoncreds.formats.anoncreds

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement

@Serializable
data class AnonCredsSelectedCredentials(
    @SerialName("requested_attributes")
    val attributes: Map<String, AnonCredsRequestedAttributeMatch>,

    @SerialName("requested_predicates")
    val predicates: Map<String, AnonCredsRequestedPredicateMatch>,

    @SerialName("self_attested_attributes")
    val selfAttestedAttributes: Map<String, String>
){
    companion object {
        fun convert(proofFormats: Map<String, JsonElement>?): AnonCredsSelectedCredentials {
            val json = Json { ignoreUnknownKeys = true } // ignora campos extras no JSON

            val attributes = proofFormats?.get("requested_attributes")?.let {

                json.decodeFromJsonElement<Map<String, AnonCredsRequestedAttributeMatch>>(it)
            } ?: emptyMap()

            val predicates = proofFormats?.get("requested_predicates")?.let {
                json.decodeFromJsonElement<Map<String, AnonCredsRequestedPredicateMatch>>(it)
            } ?: emptyMap()

            val selfAttested = proofFormats?.get("self_attested_attributes")?.let {
                json.decodeFromJsonElement<Map<String, String>>(it)
            } ?: emptyMap()

            return AnonCredsSelectedCredentials(
                attributes = attributes,
                predicates = predicates,
                selfAttestedAttributes = selfAttested
            )
        }
    }
}
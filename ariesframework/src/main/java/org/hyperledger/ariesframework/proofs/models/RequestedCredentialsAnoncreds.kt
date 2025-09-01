package org.hyperledger.ariesframework.proofs.models


import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsSchema

@Serializable
data class RequestedCredentialsAnoncreds(
    @EncodeDefault
    @SerialName("requested_attributes")
    var requestedAttributes: MutableMap<String, RequestedAttributeAnonCreds> = mutableMapOf(),

    @EncodeDefault
    @SerialName("requested_predicates")
    var requestedPredicates: MutableMap<String, RequestedPredicateAnonCreds> = mutableMapOf(),

    @EncodeDefault
    @SerialName("self_attested_attributes")
    val selfAttestedAttributes: MutableMap<String, String> = mutableMapOf(),
) {

    companion object {

        fun mapToRequestedCredentialsWithKotlinx(
            root: Map<String, JsonElement>?
        ): RequestedCredentialsAnoncreds {
            if (root == null) return RequestedCredentialsAnoncreds()

            val json = Json {
                ignoreUnknownKeys = true
                coerceInputValues = true
                encodeDefaults = true
                isLenient = true
            }

            val obj = JsonObject(root)
            return json.decodeFromJsonElement(RequestedCredentialsAnoncreds.serializer(), obj)
        }
    }

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


    fun toJson(): String = Json {
        prettyPrint = true
        encodeDefaults = true
    }.encodeToString(RequestedCredentialsAnoncreds.serializer(), this)

    fun toMap(): Map<String, JsonElement> {
        return mapOf(
            "requested_attributes" to JsonObject(
                requestedAttributes.mapValues { (_, v) ->
                    buildJsonObject {
                        put("credentialId", JsonPrimitive(v.credentialId))
                        put("timestamp", v.timestamp?.let { JsonPrimitive(it) } ?: JsonNull)
                        put("credentialInfo", v.credentialInfo?.toJsonElement() ?: JsonNull)
                        put("revoked", v.revoked?.let { JsonPrimitive(it) } ?: JsonNull)
                        put("revealed", JsonPrimitive(v.revealed))
                    }
                }
            ),
            "requested_predicates" to JsonObject(
                requestedPredicates.mapValues { (_, v) ->
                    buildJsonObject {
                        put("credentialId", JsonPrimitive(v.credentialId))
                        put("timestamp", v.timestamp?.let { JsonPrimitive(it) } ?: JsonNull)
                        put("credentialInfo", v.credentialInfo?.toJsonElement() ?: JsonNull)
                        put("revoked", v.revoked?.let { JsonPrimitive(it) } ?: JsonNull)
                    }
                }
            ),
            "self_attested_attributes" to JsonObject(
                selfAttestedAttributes.mapValues { (_, v) ->
                    JsonPrimitive(v) // assumindo que é String, se não ajusta
                }
            )
        )
    }

}
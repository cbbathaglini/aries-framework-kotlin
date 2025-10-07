package org.hyperledger.ariesframework.proofs.models

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.serializer
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsCredentialInfo

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

    fun toJsonString(): String {
        val json = Json { encodeDefaults = true }

        val attrEl = buildJsonObject {
            for ((key, value) in requestedAttributes) {
                put(key, value.toJsonElement())
            }
        }

        val predsEl = buildJsonObject {
            for ((key, value) in requestedPredicates) {
                put(key, value.toJsonElement())
            }
        }

        val selfObj = buildJsonObject {
            for ((key, value) in selfAttestedAttributes) {
                put(key, JsonPrimitive(value))
            }
        }

        val root = buildJsonObject {
            put("requested_attributes", attrEl)
            put("requested_predicates", predsEl)
            put("self_attested_attributes", selfObj)
        }
        return json.encodeToString(root)
    }

    fun normalizeAllAttributes() {
        this.requestedAttributes.values.forEach { elem ->
            val attrs = elem.credentialInfo?.attributes as? Map<String, JsonElement> ?: return@forEach
            val normalized: Map<String, String> = attrs.mapValues { (_, v) ->
                v.jsonPrimitive.content
            }
            elem.credentialInfo?.attributes = normalized
        }
        this.requestedPredicates.values.forEach { elem ->
            val attrs = elem.credentialInfo?.attributes as? Map<String, JsonElement> ?: return@forEach
            val normalized: Map<String, String> = attrs.mapValues { (_, v) ->
                v.jsonPrimitive.content
            }
            elem.credentialInfo?.attributes = normalized
        }
    }
    companion object {

        fun mapToRequestedCredentialsWithKotlinx(
            root: Map<String, JsonElement>?,
        ): RequestedCredentialsAnoncreds {
            if (root == null) return RequestedCredentialsAnoncreds()

            val json = Json {
                ignoreUnknownKeys = true
                coerceInputValues = true
                encodeDefaults = true
                isLenient = true
            }

            val obj = JsonObject(root)
            return json.decodeFromJsonElement<RequestedCredentialsAnoncreds>(obj)
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
    }.encodeToString(serializer<RequestedCredentialsAnoncreds>(), this)

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
                },
            ),
            "requested_predicates" to JsonObject(
                requestedPredicates.mapValues { (_, v) ->
                    buildJsonObject {
                        put("credentialId", JsonPrimitive(v.credentialId))
                        put("timestamp", v.timestamp?.let { JsonPrimitive(it) } ?: JsonNull)
                        put("credentialInfo", v.credentialInfo?.toJsonElement() ?: JsonNull)
                        put("revoked", v.revoked?.let { JsonPrimitive(it) } ?: JsonNull)
                    }
                },
            ),
            "self_attested_attributes" to JsonObject(
                selfAttestedAttributes.mapValues { (_, v) ->
                    JsonPrimitive(v) // assumindo que é String, se não ajusta
                },
            ),
        )
    }

    /**
     * Valida serialização campo a campo e retorna uma lista de problemas encontrados.
     * Use antes de chamar encodeToString(...) para descobrir o "path" que quebra.
     */
    fun validateSerializationPaths(json: Json = Json): List<String> {
        val issues = mutableListOf<String>()

        // requested_attributes: testa item a item
        for ((key, value) in requestedAttributes) {
            val oldval = value.copy()
            value.credentialInfo = AnonCredsCredentialInfo()
            value.credentialInfo!!.credentialId = oldval.credentialInfo!!.credentialId
            value.credentialInfo!!.schemaId = oldval.credentialInfo!!.schemaId
            value.credentialInfo!!.credentialRevocationId = oldval.credentialInfo!!.credentialRevocationId
            value.credentialInfo!!.credentialDefinitionId = oldval.credentialInfo!!.credentialDefinitionId
            value.credentialInfo!!.linkSecretId = oldval.credentialInfo!!.linkSecretId
            value.credentialInfo!!.revocationRegistryId = oldval.credentialInfo!!.revocationRegistryId
            value.credentialInfo!!.createdAt = oldval.credentialInfo!!.createdAt
            value.credentialInfo!!.updatedAt = oldval.credentialInfo!!.updatedAt
            value.credentialInfo!!.methodName = oldval.credentialInfo!!.methodName
            value.credentialInfo!!.attributes = oldval.credentialInfo!!.attributes

            val attrs: Map<String, JsonElement> = value.credentialInfo!!.attributes as Map<String, JsonElement>
            val normalized: Map<String, String> = attrs.mapValues { (_, v) ->
                v.jsonPrimitive.content // extrai o valor do JsonLiteral como String
            }
            value.credentialInfo!!.attributes = normalized

//            val normalized: Map<String, String> = value.credentialInfo!!.attributes.mapValues { (_, v) ->
//                v.jsonPrimitive.content
//            }
            // value.credentialInfo!!.attributes = normalized
            runCatching {
                json.encodeToString(RequestedAttributeAnonCreds.serializer(), value)
            }.onFailure { t ->
                issues += "requested_attributes[$key] falhou: ${t.rootCauseMessage()}"
                // Checagens adicionais úteis:
                if (value.credentialId.isBlank()) {
                    issues += "  └─ credentialId em requested_attributes[$key] está em branco"
                }
            }
        }

        // requested_predicates: testa item a item
        for ((key, value) in requestedPredicates) {
            runCatching {
                json.encodeToString(RequestedPredicateAnonCreds.serializer(), value)
            }.onFailure { t ->
                issues += "requested_predicates[$key] falhou: ${t.rootCauseMessage()}"
                if (value.credentialId.isBlank()) {
                    issues += "  └─ credentialId em requested_predicates[$key] está em branco"
                }
            }
        }

        // self_attested_attributes: testa o mapa inteiro e, se falhar, testa chave a chave
        val mapSer = MapSerializer(String.serializer(), String.serializer())
        runCatching {
            json.encodeToString(mapSer, selfAttestedAttributes)
        }.onFailure { t ->
            issues += "self_attested_attributes falhou (mapa): ${t.rootCauseMessage()}"
            // Diagnóstico fino: item por item
            for ((k, v) in selfAttestedAttributes) {
                runCatching {
                    json.encodeToString(String.serializer(), v)
                }.onFailure { it2 ->
                    issues += "  └─ self_attested_attributes[$k] inválido: ${it2.rootCauseMessage()} (valor='$v')"
                }
                if (v.isBlank()) {
                    issues += "  └─ self_attested_attributes[$k] está em branco"
                }
            }
        }

        return issues
    }

    // Helpers

    private fun Throwable.rootCause(): Throwable {
        var c: Throwable = this
        while (c.cause != null && c.cause !== c) c = c.cause!!
        return c
    }

    private fun Throwable.rootCauseMessage(): String =
        (this as? SerializationException)?.rootCause()?.message ?: this.rootCause().message ?: this.message ?: toString()
}
